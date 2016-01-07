/**
 * Copyright Â© 2013 Instituto Superior TÃ©cnico
 *
 * This file is part of FenixEdu IST Integration.
 *
 * FenixEdu IST Integration is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Integration is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Integration.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.integration.task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.fenixedu.academic.domain.CompetenceCourse;
import org.fenixedu.academic.domain.CourseLoad;
import org.fenixedu.academic.domain.CurricularCourse;
import org.fenixedu.academic.domain.Degree;
import org.fenixedu.academic.domain.DegreeCurricularPlan;
import org.fenixedu.academic.domain.EntryPhase;
import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionDegree;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.FrequencyType;
import org.fenixedu.academic.domain.Holiday;
import org.fenixedu.academic.domain.Lesson;
import org.fenixedu.academic.domain.LessonInstance;
import org.fenixedu.academic.domain.OccupationPeriod;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.SchoolClass;
import org.fenixedu.academic.domain.Shift;
import org.fenixedu.academic.domain.ShiftType;
import org.fenixedu.academic.domain.degree.DegreeType;
import org.fenixedu.academic.domain.exceptions.DomainException;
import org.fenixedu.academic.dto.GenericPair;
import org.fenixedu.academic.util.DiaSemana;
import org.fenixedu.academic.util.HourMinuteSecond;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.spaces.domain.Space;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Minutes;
import org.joda.time.Period;
import org.joda.time.TimeOfDay;
import org.joda.time.Weeks;
import org.joda.time.YearMonthDay;

public class CreateAndInitializeExecutionCourses extends CustomTask {

    protected ExecutionSemester originExecutionSemester;
    protected ExecutionSemester destinationExecutionSemester;

    protected Set<CurricularCourse> processedCurricularCourses = new HashSet<CurricularCourse>();
    protected Set<Degree> degreesToExclude = new HashSet<Degree>();
    protected final String[] degreesSiglasToExclude = new String[] { "MEIC-A", "MEIC-T" };

    protected Map<SchoolClass, SchoolClass> schoolClassTranslation = new HashMap<SchoolClass, SchoolClass>();

    @Override
    public void runTask() throws Exception {
        processedCurricularCourses.clear();
        schoolClassTranslation.clear();

        originExecutionSemester = ExecutionSemester.readActualExecutionSemester().getPreviousExecutionPeriod();
        destinationExecutionSemester = ExecutionSemester.readActualExecutionSemester().getNextExecutionPeriod();
        for (String degreeSigla : degreesSiglasToExclude) {
            degreesToExclude.add(Degree.readBySigla(degreeSigla));
            taskLog("%s excluded from the initialization!\n", degreeSigla);
        }

        printExecutionSemesterInfo();

        createSchoolClasses();

        loadExistingCodes();

        for (final ExecutionCourse executionCourse : originExecutionSemester.getAssociatedExecutionCoursesSet()) {
            final EntryPhase entryPhase = executionCourse.getEntryPhase();
            if (entryPhase == null || EntryPhase.FIRST_PHASE.equals(entryPhase)) {
                createFromPreviouse(executionCourse);
            }
        }

        final ExecutionYear executionYear = destinationExecutionSemester.getExecutionYear();
        for (final ExecutionDegree executionDegree : executionYear.getExecutionDegreesSet()) {
            final DegreeCurricularPlan degreeCurricularPlan = executionDegree.getDegreeCurricularPlan();
            for (final CurricularCourse curricularCourse : degreeCurricularPlan.getCurricularCoursesSet()) {
                if (shouldCreateFor(curricularCourse)) {
                    createFor(curricularCourse);
                }
            }
        }

        printReport();
    }

    private void createSchoolClasses() {
        final ExecutionYear executionYear = originExecutionSemester.getExecutionYear();
        for (final ExecutionDegree originalExecutionDegree : executionYear.getExecutionDegreesSet()) {
            final ExecutionDegree destinationExecutionDegree = getDestinationExecutionDegree(originalExecutionDegree);
            if (destinationExecutionDegree != null) {
                final Degree degree = destinationExecutionDegree.getDegree();
                for (final SchoolClass schoolClass : originalExecutionDegree.getSchoolClassesSet()) {
                    SchoolClass newSchoolClass =
                            destinationExecutionDegree.findSchoolClassesByExecutionPeriodAndName(destinationExecutionSemester,
                                    schoolClass.getNome());
                    if (newSchoolClass == null) {
                        final String namePrefix = degree.constructSchoolClassPrefix(schoolClass.getAnoCurricular());
                        final String name = schoolClass.getNome().substring(namePrefix.length());
                        newSchoolClass =
                                new SchoolClass(destinationExecutionDegree, destinationExecutionSemester, name,
                                        schoolClass.getAnoCurricular());
                    }
                    schoolClassTranslation.put(schoolClass, newSchoolClass);
                }
            }
        }
    }

    private ExecutionDegree getDestinationExecutionDegree(final ExecutionDegree originalExecutionDegree) {
        final ExecutionYear executionYear = destinationExecutionSemester.getExecutionYear();
        final DegreeCurricularPlan degreeCurricularPlan = originalExecutionDegree.getDegreeCurricularPlan();
        for (final ExecutionDegree executionDegree : degreeCurricularPlan.getExecutionDegreesSet()) {
            if (executionYear == executionDegree.getExecutionYear()) {
                return executionDegree;
            }
        }
        return null;
    }

    int copiedCourseLoads = 0;
    int notCopiedCourseLoads = 0;

    protected void createFromPreviouse(final ExecutionCourse executionCourse) {
        final Set<CurricularCourse> curricularCoursesToAssociate = getActiveCurricularCourses(executionCourse);

        if (!curricularCoursesToAssociate.isEmpty()) {
            final ExecutionCourse newExecutionCourse =
                    createExecutionCourse(executionCourse.getNome(), executionCourse.getSigla());
            for (final CurricularCourse curricularCourse : curricularCoursesToAssociate) {
                newExecutionCourse.addAssociatedCurricularCourses(curricularCourse);
            }

            final Map<CourseLoad, CourseLoad> courseLoadMap = new HashMap<CourseLoad, CourseLoad>();
            final Set<Shift> shifts = new HashSet<Shift>();
            for (final CourseLoad oldCourseLoad : executionCourse.getCourseLoadsSet()) {
                final ShiftType shiftType = oldCourseLoad.getType();
                final CourseLoad newCourseLoad = newExecutionCourse.getCourseLoadByShiftType(shiftType);
                if (newCourseLoad != null) {
                    courseLoadMap.put(oldCourseLoad, newCourseLoad);
                    for (final Shift shift : oldCourseLoad.getShiftsSet()) {
                        shifts.add(shift);
                    }
                    copiedCourseLoads++;
                } else {
                    notCopiedCourseLoads++;
                }
            }

            for (final Shift oldShift : shifts) {
                copyShift(newExecutionCourse, courseLoadMap, oldShift);
            }

            //if (executionCourse.getExecutionPeriod().getSemester().intValue() == 2) {
            final ExecutionCourse firstSemesterExecutionCourse = findFirstSemesterExecutionCourse(executionCourse);
            if (firstSemesterExecutionCourse != null) {
                for (final Professorship professorship : firstSemesterExecutionCourse.getProfessorshipsSet()) {
                    if (professorship.getTeacher().hasTeacherAuthorization(
                            newExecutionCourse.getExecutionPeriod().getAcademicInterval())) {
                        Professorship.create(professorship.getResponsibleFor(), newExecutionCourse, professorship.getPerson());
                    }
                }
            }
            //}

            processedCurricularCourses.addAll(curricularCoursesToAssociate);
        }
    }

    private ExecutionCourse findFirstSemesterExecutionCourse(final ExecutionCourse executionCourse) {
        final ExecutionSemester executionSemester = executionCourse.getExecutionPeriod().getPreviousExecutionPeriod();
        ExecutionCourse result = null;
        for (final CurricularCourse curricularCourse : executionCourse.getAssociatedCurricularCoursesSet()) {
            if (curricularCourse.isAnual()) {
                final ExecutionCourse previous = findPrevious(curricularCourse, executionSemester);
                if (result == null) {
                    result = previous;
                } else if (result != previous) {
                    return null;
                }
            }
        }
        return result;
    }

    private ExecutionCourse findPrevious(CurricularCourse curricularCourse, ExecutionSemester executionSemester) {
        final ExecutionSemester previousExecutionPeriod = executionSemester.getPreviousExecutionPeriod();
        for (final ExecutionCourse executionCourse : curricularCourse.getAssociatedExecutionCoursesSet()) {
            if (executionCourse.getExecutionPeriod() == previousExecutionPeriod) {
                return executionCourse;
            }
        }
        return null;
    }

    int countCopiedLessons = 0;
    int countInconsistentLessons = 0;
    int countSkippedLessons = 0;
    int countSkippedLessons1 = 0;
    int countSkippedLessons2 = 0;
    int countSkippedLessons3 = 0;
    int countSkippedLessons4 = 0;

    private YearMonthDay getValidBeginDate(YearMonthDay startDate, DiaSemana diaSemana) {
        YearMonthDay lessonBegin =
                startDate.toDateTimeAtMidnight().withDayOfWeek(diaSemana.getDiaSemanaInDayOfWeekJodaFormat()).toYearMonthDay();
        if (lessonBegin.isBefore(startDate)) {
            lessonBegin = lessonBegin.plusDays(7);
        }
        return lessonBegin;
    }

    private OccupationPeriod readOccupationPeriod(YearMonthDay start, YearMonthDay end) {
        for (final OccupationPeriod occupationPeriod : Bennu.getInstance().getOccupationPeriodsSet()) {
            if (occupationPeriod.getNextPeriod() == null && occupationPeriod.getPreviousPeriod() == null
                    && occupationPeriod.getStartYearMonthDay().equals(start) && occupationPeriod.getEndYearMonthDay().equals(end)) {
                return occupationPeriod;
            }
        }
        return null;
    }

    private void copyShift(final ExecutionCourse newExecutionCourse, final Map<CourseLoad, CourseLoad> courseLoadMap,
            final Shift oldShift) {
        final Set<ShiftType> shiftTypes = new HashSet<ShiftType>(oldShift.getTypes());
        for (final Iterator<ShiftType> iterator = shiftTypes.iterator(); iterator.hasNext();) {
            final ShiftType shiftType = iterator.next();
            if (!hasCourseLoadForShiftType(courseLoadMap, shiftType)) {
                iterator.remove();
            }
        }
        final Shift newShift = new Shift(newExecutionCourse, shiftTypes, oldShift.getLotacao());

        for (final SchoolClass oldSchoolClass : oldShift.getAssociatedClassesSet()) {
            final SchoolClass newSchoolClass = schoolClassTranslation.get(oldSchoolClass);
            if (newSchoolClass == null) {
                taskLog("No corresponding school class for: " + oldSchoolClass.getNome() + " "
                        + oldSchoolClass.getExecutionDegree().getDegree().getSigla() + "\n");
            } else {
                newShift.addAssociatedClasses(newSchoolClass);
            }
        }

        for (final Lesson oldLesson : oldShift.getAssociatedLessonsSet()) {
            final GenericPair<YearMonthDay, YearMonthDay> maxLessonsPeriod = newShift.getExecutionCourse().getMaxLessonsPeriod();
            final OccupationPeriod period = readOccupationPeriod(maxLessonsPeriod.getLeft(), maxLessonsPeriod.getRight());
            final OccupationPeriod occupationPeriod =
                    findOccupationPeriod(newExecutionCourse, maxLessonsPeriod.getLeft(), maxLessonsPeriod.getRight());

            if (!isConsistent(oldLesson.getDiaSemana(), oldLesson.getInicio(), oldLesson.getFim(), newShift,
                    oldLesson.getFrequency(), destinationExecutionSemester, maxLessonsPeriod.getLeft(),
                    maxLessonsPeriod.getRight(), oldLesson.getLessonCampus(), period, occupationPeriod)) {
                countSkippedLessons++;
                continue;
            }
            //oldLesson.getOccurrenceWeeksAsString()
            final Space allocatableSpace = oldLesson.getSala();
            final Space allocatableSpaceToSet;
            //final int offset;
            final int offset = findOffset(oldLesson);
            if (allocatableSpace != null
                    && allocatableSpace.isFree(generateEventSpaceOccupationIntervals(maxLessonsPeriod.getLeft(), maxLessonsPeriod
                            .getRight(), new HourMinuteSecond(oldLesson.getInicio()), new HourMinuteSecond(oldLesson.getFim()),
                            oldLesson.getDiaSemana(), oldLesson.getFrequency()))) {
                allocatableSpaceToSet = allocatableSpace;
                //offset = findOffset(oldLesson);
            } else {
                allocatableSpaceToSet = null;
                //offset = 0;
            }
            taskLog(oldShift.getExecutionCourse().getSigla() + " - " + oldShift.getNome() + " vv ");
            taskLog("  op: " + occupationPeriod.asString());
            taskLog("  off: " + offset);
            taskLog("  dia semana: " + oldLesson.getDiaSemana());
            taskLog(" inic: " + oldLesson.getInicioString());
            taskLog(" fim: " + oldLesson.getFimString());
            taskLog(" freq: " + oldLesson.getFrequency());
            if (allocatableSpaceToSet != null) {
                taskLog(" space: " + allocatableSpaceToSet.getName());
            } else {
                taskLog(" space: no space");
            }

            try {
                String lessonWeeks = oldLesson.getOccurrenceWeeksAsString();
                if (oldLesson.getShift().getExternalId().equals("2748779657784")) {
                    taskLog("lol");
                }
                Integer startingWeek = Integer.parseInt(lessonWeeks.split("[, ]")[0].trim());
                Lesson newLesson = null;
                if (occupationPeriod == null) {
                    newLesson =
                            new Lesson(oldLesson.getDiaSemana(), oldLesson.getInicio(), oldLesson.getFim(), newShift,
                                    oldLesson.getFrequency(), destinationExecutionSemester, maxLessonsPeriod.getLeft().plusDays(
                                            7 * offset), maxLessonsPeriod.getRight(), allocatableSpaceToSet);
                    taskLog("  diaSemananoOcup: "
                            + getValidBeginDate(maxLessonsPeriod.getLeft().plusDays(7 * offset), oldLesson.getDiaSemana())
                                    .toString());
                } else {
                    // if (offset == 0) {
                    newLesson =
                            new Lesson(oldLesson.getDiaSemana(), oldLesson.getInicio(), oldLesson.getFim(), newShift,
                                    oldLesson.getFrequency(), destinationExecutionSemester, occupationPeriod
                                            .getStartYearMonthDay().plusDays(7 * offset), occupationPeriod.getEndYearMonthDay(),
                                    allocatableSpaceToSet);
                    taskLog("  diaSemanawtOcup: "
                            + getValidBeginDate(occupationPeriod.getStartYearMonthDay().plusDays(7 * offset),
                                    oldLesson.getDiaSemana()).toString());
//                    } else {
//                        final OccupationPeriod lessonOP =
//                                new OccupationPeriod(getWeeks(oldLesson).sorted().map(w -> map(occupationPeriod, w))
//                                        .filter(i -> i != null).collect(Collectors.toList()).iterator());
//                        print("   op: ", lessonOP);
//                        taskLog("  lessonOP:" + getWeeks(oldLesson).sorted().collect(Collectors.toList()));
//                        newLesson =
//                                new Lesson(oldLesson.getDiaSemana(), oldLesson.getInicio(), oldLesson.getFim(), newShift,
//                                        oldLesson.getFrequency(), destinationExecutionSemester,
//                                        lessonOP == null ? occupationPeriod : lessonOP, allocatableSpaceToSet);
//                    }
                }
                taskLog(newLesson != null ? newLesson.getPeriod().asString() : "---");
                taskLog(" weeks: " + newLesson.getOccurrenceWeeksAsString());
                taskLog(" execMax start: " + newLesson.getExecutionCourse().getMaxLessonsPeriod().getLeft());
                taskLog(" lessonStartDate: "
                        + getValidBeginDate(newLesson.getPeriod().getStartYearMonthDay(), newLesson.getDiaSemana()).toString());
                countCopiedLessons++;
            } catch (DomainException de) {
                countInconsistentLessons++;
                final ExecutionDegree executionDegree =
                        newExecutionCourse.getAssociatedCurricularCoursesSet().iterator().next()
                                .getExecutionDegreeFor(destinationExecutionSemester.getExecutionYear());

                taskLog("\n");
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(de.getMessage());
                for (final String arg : de.getArgs()) {
                    stringBuilder.append(" ");
                    stringBuilder.append(arg);
                }
                taskLog(stringBuilder.toString() + "\n");
                taskLog("ExecutionDegree: " + executionDegree.getDegreeCurricularPlan().getName() + "\n");
                taskLog("ExecutionCourse: " + newExecutionCourse.getNome() + "\n");
                taskLog("Shift: " + oldShift.getNome() + "\n");
                taskLog("Previous offset: " + offset + "\n");

                OccupationPeriod occupationPeriodX =
                        destinationExecutionSemester.getSemester().intValue() == 1 ? executionDegree
                                .getPeriodLessonsFirstSemester() : executionDegree.getPeriodLessonsSecondSemester();
                taskLog("ED Period ");
                while (occupationPeriodX != null) {
                    taskLog(" : ");
                    taskLog(occupationPeriodX.getStartYearMonthDay().toString("yyyy-MM-dd"));
                    taskLog(" - ");
                    taskLog(occupationPeriodX.getEndYearMonthDay().toString("yyyy-MM-dd"));
                    occupationPeriodX = occupationPeriodX.getNextPeriod();
                }
                taskLog("\n");

                taskLog("Period: " + maxLessonsPeriod.getLeft() + " - " + maxLessonsPeriod.getRight() + "\n");
                if (allocatableSpaceToSet != null) {
                    taskLog("Room: " + oldLesson.getSala() + " : " + oldLesson.getSala().getName() + "\n");
                }

                for (final String arg : de.getArgs()) {
                    taskLog("   arg: " + arg + "\n");
                }

                throw de;
            }
        }
    }

    private void print(final String prefix, final OccupationPeriod occupationPeriod) {
        taskLog("%s %s", prefix, occupationPeriod.getPeriodInterval().toString());
        final OccupationPeriod next = occupationPeriod.getNextPeriod();
        if (next != null) {
            print(" + ", next);
        } else {
            taskLog();
        }
    }

    private Interval map(final OccupationPeriod occupationPeriod, final Integer w) {
        final Interval startInterval = occupationPeriod.getPeriodInterval();
        final DateTime s = startInterval.getStart().plusWeeks(w - 1);
        final Interval i = new Interval(s, s.plusWeeks(1));
        return intersect(occupationPeriod, i);
    }

    private Interval intersect(final OccupationPeriod occupationPeriod, final Interval i) {
        final Interval pi = occupationPeriod.getPeriodInterval();
        final Interval overlap = pi.overlap(i);
        return overlap == null && occupationPeriod.getNextPeriod() != null ? intersect(occupationPeriod.getNextPeriod(), i) : overlap;
    }

    public Stream<Integer> getWeeks(final Lesson lesson) {
        final SortedSet<Integer> weeks = new TreeSet<Integer>();
        final ExecutionCourse executionCourse = lesson.getExecutionCourse();
        final YearMonthDay firstPossibleLessonDay = executionCourse.getMaxLessonsPeriod().getLeft();
        for (final Interval interval : lesson.getAllLessonIntervals()) {
            final Integer week = Weeks.weeksBetween(firstPossibleLessonDay, interval.getStart().toLocalDate()).getWeeks() + 1;
            weeks.add(week);
        }
        return weeks.stream();
    }

    public SortedSet<Integer> getOccurrenceWeeksAsString(final Lesson lesson) {
        final SortedSet<Integer> weeks = new TreeSet<Integer>();

        final ExecutionCourse executionCourse = lesson.getExecutionCourse();
        final YearMonthDay firstPossibleLessonDay = executionCourse.getMaxLessonsPeriod().getLeft();
        final YearMonthDay lastPossibleLessonDay = executionCourse.getMaxLessonsPeriod().getRight();
        for (final Interval interval : lesson.getAllLessonIntervals()) {
            final Integer week = Weeks.weeksBetween(firstPossibleLessonDay, interval.getStart().toLocalDate()).getWeeks() + 1;
            weeks.add(week);
        }
        return weeks;
    }

    private static int SATURDAY_IN_JODA_TIME = 6, SUNDAY_IN_JODA_TIME = 7;

    protected List<Interval> generateEventSpaceOccupationIntervals(YearMonthDay begin, final YearMonthDay end,
            final HourMinuteSecond beginTime, final HourMinuteSecond endTime, final DiaSemana diaSemana,
            final FrequencyType frequency) {

        final YearMonthDay startDateToSearch = begin;
        final YearMonthDay endDateToSearch = end;

        List<Interval> result = new ArrayList<Interval>();
        begin = getBeginDateInSpecificWeekDay(diaSemana, begin);

        if (frequency == null) {
            if (!begin.isAfter(end)
                    && (startDateToSearch == null || (!end.isBefore(startDateToSearch) && !begin.isAfter(endDateToSearch)))) {
                result.add(createNewInterval(begin, end, beginTime, endTime));
                return result;
            }
        } else {
            int numberOfDaysToSum = frequency.getNumberOfDays();
            while (true) {
                if (begin.isAfter(end)) {
                    break;
                }
                if (startDateToSearch == null || (!begin.isBefore(startDateToSearch) && !begin.isAfter(endDateToSearch))) {

                    Interval interval = createNewInterval(begin, begin, beginTime, endTime);

                    if (!frequency.equals(FrequencyType.DAILY)
                            || ((false || interval.getStart().getDayOfWeek() != SATURDAY_IN_JODA_TIME) && (false || interval
                                    .getStart().getDayOfWeek() != SUNDAY_IN_JODA_TIME))) {

                        result.add(interval);
                    }
                }
                begin = begin.plusDays(numberOfDaysToSum);
            }
        }
        return result;
    }

    protected static Interval createNewInterval(YearMonthDay begin, YearMonthDay end, HourMinuteSecond beginTime,
            HourMinuteSecond endTime) {
        return new Interval(begin.toDateTime(new TimeOfDay(beginTime.getHour(), beginTime.getMinuteOfHour(), 0, 0)),
                end.toDateTime(new TimeOfDay(endTime.getHour(), endTime.getMinuteOfHour(), 0, 0)));
    }

    private YearMonthDay getBeginDateInSpecificWeekDay(DiaSemana diaSemana, YearMonthDay begin) {
        if (diaSemana != null) {
            YearMonthDay newBegin =
                    begin.toDateTimeAtMidnight().withDayOfWeek(diaSemana.getDiaSemanaInDayOfWeekJodaFormat()).toYearMonthDay();
            if (newBegin.isBefore(begin)) {
                begin = newBegin.plusDays(Lesson.NUMBER_OF_DAYS_IN_WEEK);
            } else {
                begin = newBegin;
            }
        }
        return begin;
    }

    private OccupationPeriod findOccupationPeriod(final ExecutionCourse executionCourse, final YearMonthDay left,
            final YearMonthDay right) {
        for (final CurricularCourse curricularCourse : executionCourse.getAssociatedCurricularCoursesSet()) {
            final DegreeCurricularPlan degreeCurricularPlan = curricularCourse.getDegreeCurricularPlan();
            for (final ExecutionDegree executionDegree : degreeCurricularPlan.getExecutionDegreesSet()) {
                if (executionDegree.getExecutionYear() == executionCourse.getExecutionYear()) {
                    final OccupationPeriod occupationPeriod =
                            executionDegree.getPeriodLessons(executionCourse.getExecutionPeriod());
                    if (left.equals(occupationPeriod.getStartYearMonthDay())
                            && right.equals(occupationPeriod.getEndYearMonthDayWithNextPeriods())) {
                        return occupationPeriod;
                    }
                }
            }
        }
        return null;
    }

    public int getFinalNumberOfLessonInstances(final DiaSemana diaSemana, final YearMonthDay start, final YearMonthDay end,
            final Space campus, final OccupationPeriod period, final FrequencyType frequencyType) {
        int count = 0;
        YearMonthDay startDateToSearch = getValidBeginDate(diaSemana, start);
        YearMonthDay endDateToSearch = getValidEndDate(diaSemana, end);
        count +=
                getAllValidLessonDatesWithoutInstancesDates(diaSemana, startDateToSearch, endDateToSearch, campus, period,
                        frequencyType).size();
        return count;
    }

    private YearMonthDay getValidBeginDate(final DiaSemana diaSemana, YearMonthDay startDate) {
        YearMonthDay lessonBegin =
                startDate.toDateTimeAtMidnight().withDayOfWeek(diaSemana.getDiaSemanaInDayOfWeekJodaFormat()).toYearMonthDay();
        if (lessonBegin.isBefore(startDate)) {
            lessonBegin = lessonBegin.plusDays(Lesson.NUMBER_OF_DAYS_IN_WEEK);
        }
        return lessonBegin;
    }

    private YearMonthDay getValidEndDate(final DiaSemana diaSemana, YearMonthDay endDate) {
        YearMonthDay lessonEnd =
                endDate.toDateTimeAtMidnight().withDayOfWeek(diaSemana.getDiaSemanaInDayOfWeekJodaFormat()).toYearMonthDay();
        if (lessonEnd.isAfter(endDate)) {
            lessonEnd = lessonEnd.minusDays(Lesson.NUMBER_OF_DAYS_IN_WEEK);
        }
        return lessonEnd;
    }

    private SortedSet<YearMonthDay> getAllValidLessonDatesWithoutInstancesDates(DiaSemana diaSemana,
            YearMonthDay startDateToSearch, YearMonthDay endDateToSearch, final Space campus, final OccupationPeriod period,
            final FrequencyType frequencyType) {

        SortedSet<YearMonthDay> result = new TreeSet<YearMonthDay>();
        startDateToSearch = startDateToSearch != null ? getValidBeginDate(diaSemana, startDateToSearch) : null;

        if (startDateToSearch != null && endDateToSearch != null && !startDateToSearch.isAfter(endDateToSearch)) {

            Space lessonCampus = campus;
            while (true) {
                if (isDayValid(startDateToSearch, lessonCampus, period)) {
                    result.add(startDateToSearch);
                }
                startDateToSearch = startDateToSearch.plusDays(frequencyType.getNumberOfDays());
                if (startDateToSearch.isAfter(endDateToSearch)) {
                    break;
                }
            }
        }

        return result;
    }

    private boolean isDayValid(YearMonthDay day, Space lessonCampus, final OccupationPeriod period) {
        return !Holiday.isHoliday(day.toLocalDate(), lessonCampus) && period.nestedOccupationPeriodsContainsDay(day);
    }

    private boolean isConsistent(final DiaSemana diaSemana, final Calendar inicio, final Calendar fim, final Shift newShift,
            final FrequencyType frequency, final ExecutionSemester executionSemester, final YearMonthDay start,
            final YearMonthDay end, final Space campus, final OccupationPeriod occupationPeriod,
            OccupationPeriod actualOccupationPeriod) {

        final BigDecimal lessonHours =
                BigDecimal.valueOf(Minutes.minutesBetween(new HourMinuteSecond(inicio), new HourMinuteSecond(fim)).getMinutes())
                        .divide(BigDecimal.valueOf(Lesson.NUMBER_OF_MINUTES_IN_HOUR), 2, RoundingMode.HALF_UP);
        final int finalNumberOfLessonInstances =
                getFinalNumberOfLessonInstances(diaSemana, start, end, campus,
                        actualOccupationPeriod == null ? occupationPeriod : actualOccupationPeriod, frequency);
        BigDecimal totalHours =
                newShift.getTotalHours().add(lessonHours.multiply(BigDecimal.valueOf(finalNumberOfLessonInstances)));

        if (newShift.getCourseLoadsSet().size() == 1) {

            final CourseLoad courseLoad = newShift.getCourseLoadsSet().iterator().next();

            if (courseLoad.getUnitQuantity() != null && lessonHours.compareTo(courseLoad.getUnitQuantity()) != 0) {
                countSkippedLessons1++;
                return false;
            }

            if (totalHours.compareTo(courseLoad.getTotalQuantity()) == 1) {
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("\n");
                stringBuilder.append(totalHours);
                stringBuilder.append(" : ");
                stringBuilder.append(courseLoad.getTotalQuantity());
                stringBuilder.append(" ... ");
                stringBuilder.append(finalNumberOfLessonInstances);
                stringBuilder.append(" : ");
                stringBuilder.append(lessonHours);
                stringBuilder.append(" : ");
                stringBuilder.append(newShift.getTotalHours());
                stringBuilder.append(" : ");
                stringBuilder.append(newShift.getExecutionCourse().getName());
                stringBuilder.append(" : ");
                stringBuilder.append(newShift.getExecutionCourse().getDegreePresentationString());
                taskLog(stringBuilder.toString() + "\n");
                countSkippedLessons2++;
                return false;
            }
        } else {

            boolean unitValid = false, totalValid = false;

            for (CourseLoad courseLoad : newShift.getCourseLoadsSet()) {

                unitValid = false;
                totalValid = false;

                if (courseLoad.getUnitQuantity() == null || lessonHours.compareTo(courseLoad.getUnitQuantity()) == 0) {
                    unitValid = true;
                }
                if (totalHours.compareTo(courseLoad.getTotalQuantity()) != 1) {
                    totalValid = true;
                    if (unitValid) {
                        break;
                    }
                }
            }

            if (!totalValid) {
                countSkippedLessons3++;
                return false;
            }
            if (!unitValid) {
                countSkippedLessons4++;
                return false;
            }

        }

        return true;
    }

    private int findOffset(final Lesson oldLesson) {
        final GenericPair<YearMonthDay, YearMonthDay> maxLessonsPeriod = oldLesson.getExecutionCourse().getMaxLessonsPeriod();
        final LessonInstance lessonInstance = oldLesson.getFirstLessonInstance();
        final Period period;
        if (lessonInstance != null) {
            period = new Period(maxLessonsPeriod.getLeft(), lessonInstance.getDay());
        } else if (oldLesson.getPeriod() != null) {
            final YearMonthDay start = oldLesson.getPeriod().getStartYearMonthDay();
            period = new Period(maxLessonsPeriod.getLeft(), start);
        } else {
            period = null;
        }
        return period == null ? 0 : period.getMonths() * 4 + period.getWeeks() + (period.getDays() / 7);
    }

    private boolean hasCourseLoadForShiftType(final Map<CourseLoad, CourseLoad> courseLoadMap, final ShiftType shiftType) {
        for (final CourseLoad courseLoad : courseLoadMap.values()) {
            if (courseLoad.getType() == shiftType) {
                return true;
            }
        }
        return false;
    }

    private void createFor(final CurricularCourse curricularCourse) {
        String acronym = curricularCourse.getAcronym(destinationExecutionSemester);
        if (acronym == null) {
            acronym = curricularCourse.getAcronym();
        }
        if (acronym == null) {
            acronym = curricularCourse.getName().substring(0, 1);
        }

        final ExecutionCourse newExecutionCourse = createExecutionCourse(curricularCourse.getName(), acronym);
        newExecutionCourse.addAssociatedCurricularCourses(curricularCourse);
        processedCurricularCourses.add(curricularCourse);
    }

    protected ExecutionCourse createExecutionCourse(final String name, final String acronym) {
        final String code = findUniqueCode(acronym);
        return new ExecutionCourse(name, code, destinationExecutionSemester, null);
    }

    private final Map<String, int[]> codeMap = new HashMap<String, int[]>();

    protected String findUniqueCode(final String acronym) {
        final String key = getKey(acronym);
        int[] count = codeMap.get(key);
        if (count == null) {
            count = new int[] { 1 };
            codeMap.put(key, count);
            return acronym;
        }
        return key + ++count[0];
    }

    protected String getKey(final String acronym) {
        return acronym.indexOf('-') > 0 ? acronym.substring(0, acronym.indexOf('-')) : acronym;
    }

    protected void loadExistingCodes() {
        for (final ExecutionCourse executionCourse : destinationExecutionSemester.getAssociatedExecutionCoursesSet()) {
            findUniqueCode(executionCourse.getSigla());
        }
    }

    protected Set<CurricularCourse> getActiveCurricularCourses(final ExecutionCourse executionCourse) {
        final Set<CurricularCourse> curricularCourses = new HashSet<CurricularCourse>();
        for (final CurricularCourse curricularCourse : executionCourse.getAssociatedCurricularCoursesSet()) {
            if (shouldCreateFor(curricularCourse)) {
                curricularCourses.add(curricularCourse);
            }
        }
        return curricularCourses;
    }

    protected boolean shouldCreateFor(final CurricularCourse curricularCourse) {
        return !processedCurricularCourses.contains(curricularCourse) && isActive(curricularCourse)
                && !hasExecutionCourse(curricularCourse) && isDegreeTypeToBeProcessed(curricularCourse)
                && isDegreeToBeProcessed(curricularCourse);
    }

    private boolean isDegreeToBeProcessed(CurricularCourse curricularCourse) {
        return !degreesToExclude.contains(curricularCourse.getDegree());
    }

    private boolean isDegreeTypeToBeProcessed(final CurricularCourse curricularCourse) {
        final DegreeType degreeType = curricularCourse.getDegreeType();
        return degreeType.isBolonhaType() || degreeType.isBolonhaMasterDegree() || degreeType.isIntegratedMasterDegree();
    }

    protected boolean hasExecutionCourse(final CurricularCourse curricularCourse) {
        for (final ExecutionCourse executionCourse : curricularCourse.getAssociatedExecutionCoursesSet()) {
            if (destinationExecutionSemester == executionCourse.getExecutionPeriod()) {
                return true;
            }
        }
        return false;
    }

    protected boolean isActive(final CurricularCourse curricularCourse) {
        final DegreeCurricularPlan degreeCurricularPlan = curricularCourse.getDegreeCurricularPlan();
        return curricularCourse.isActive(destinationExecutionSemester) && hasActiveExecutionDegree(degreeCurricularPlan)
                && hasAprovedCompetenceCourse(curricularCourse);
    }

    private boolean hasAprovedCompetenceCourse(final CurricularCourse curricularCourse) {
        final CompetenceCourse competenceCourse = curricularCourse.getCompetenceCourse();
        return competenceCourse == null || competenceCourse.isApproved();
    }

    private boolean hasActiveExecutionDegree(final DegreeCurricularPlan degreeCurricularPlan) {
        final ExecutionYear executionYear = destinationExecutionSemester.getExecutionYear();
        for (final ExecutionDegree executionDegree : degreeCurricularPlan.getExecutionDegreesSet()) {
            if (executionYear == executionDegree.getExecutionYear()) {
                return true;
            }
        }
        return false;
    }

    protected void printExecutionSemesterInfo() {
        taskLog("Initializing next execution semester: ");
        print(destinationExecutionSemester);
        taskLog(" from ");
        print(originExecutionSemester);
    }

    protected void print(final ExecutionSemester executionSemester) {
        taskLog(executionSemester.getSemester().toString());
        taskLog(" ");
        taskLog(executionSemester.getExecutionYear().getYear());
        taskLog("\n");
    }

    protected void printReport() {
        taskLog("Processed " + processedCurricularCourses.size() + " curricular courses." + "\n");
        taskLog("Processed " + schoolClassTranslation.size() + " school classes." + "\n");
        Map<DegreeType, Integer> degreeTypeCounter = new HashMap<DegreeType, Integer>();
        Map<Degree, int[]> degreeCounter = new TreeMap<Degree, int[]>(Degree.COMPARATOR_BY_NAME_AND_ID);
        for (final CurricularCourse curricularCourse : processedCurricularCourses) {
            final DegreeCurricularPlan degreeCurricularPlan = curricularCourse.getDegreeCurricularPlan();
            final Degree degree = degreeCurricularPlan.getDegree();
            final DegreeType degreeType = degree.getDegreeType();

            increaseDegreeTypeCount(degreeTypeCounter, degreeType);

            int[] dc = degreeCounter.get(degree);
            if (dc == null) {
                dc = new int[1];
                degreeCounter.put(degree, dc);
            }
            dc[0]++;

            if (!degreeType.isBolonhaType()) {
                taskLog("Non bolonha curricular course: %s - %s\n", degreeCurricularPlan.getName(), curricularCourse.getName());
            }
        }
        for (final DegreeType degreeType : degreeTypeCounter.keySet()) {
            taskLog("   %s: %s\n", degreeType.getName().getContent(), degreeTypeCounter.get(degreeType));

            for (final Entry<Degree, int[]> entry : degreeCounter.entrySet()) {
                final Degree degree = entry.getKey();
                if (degree.getDegreeType() == degreeType) {
                    taskLog("      %s: %s\n", degree.getSigla(), Integer.toString(entry.getValue()[0]));
                }
            }
        }

        taskLog("Copied " + copiedCourseLoads + " course loads.\n");
        taskLog("Did not copy " + notCopiedCourseLoads + " course loads.\n");
        taskLog("Copied " + countCopiedLessons + " lessons.\n");
        taskLog("Skipped " + countSkippedLessons + " lessons.\n");
        taskLog("Skipped1 " + countSkippedLessons1 + " lessons.\n");
        taskLog("Skipped2 " + countSkippedLessons2 + " lessons.\n");
        taskLog("Skipped3 " + countSkippedLessons3 + " lessons.\n");
        taskLog("Skipped4 " + countSkippedLessons4 + " lessons.\n");
        taskLog("Did not copy " + countInconsistentLessons + " inconsistent lessons.\n");
    }

    private void increaseDegreeTypeCount(Map<DegreeType, Integer> degreeTypeCounter, final DegreeType degreeType) {
        Integer counter = degreeTypeCounter.get(degreeType);
        if (counter == null) {
            counter = new Integer(0);
        }
        degreeTypeCounter.put(degreeType, ++counter);
    }
}