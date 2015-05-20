package pt.ist.fenixedu.integration.task;

import java.util.HashSet;
import java.util.Set;

import org.fenixedu.academic.domain.Degree;
import org.fenixedu.academic.domain.ExecutionDegree;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Lesson;
import org.fenixedu.academic.domain.OccupationPeriod;
import org.fenixedu.academic.domain.OccupationPeriodReference;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.YearMonthDay;

public class CorrectLessonPeriods extends CustomTask {

    protected final String[] degreesSiglasToExclude = new String[] { "MEIC-A", "MEIC-T" };
    protected Set<Degree> degreesToExclude = new HashSet<Degree>();

    @Override
    public void runTask() throws Exception {
        for (String degreeSigla : degreesSiglasToExclude) {
            degreesToExclude.add(Degree.readBySigla(degreeSigla));
            taskLog("%s excluded from the initialization!\n", degreeSigla);
        }

        Set<OccupationPeriod> periods = new HashSet<OccupationPeriod>();
        int count = 0;
        YearMonthDay endDate = new YearMonthDay(2016, 03, 25);
        ExecutionSemester executionSemester = ExecutionSemester.readActualExecutionSemester().getNextExecutionPeriod();
        taskLog("" + Bennu.getInstance().getLessonsSet().size());
        int numberOfLessons = 0;
        for (Lesson lesson : Bennu.getInstance().getLessonsSet()) {
            numberOfLessons++;
            if (lesson.getExternalId().equals("563976450635792")) {
                taskLog("Let's seee!");
            }
            if (lesson.getShift().getExecutionPeriod() == executionSemester) {
                if (lesson.getPeriod().getNextPeriod() == null && lesson.getPeriod().getEndYearMonthDay().isBefore(endDate)) {
                    if (isToExcludeDegree(lesson)) {
                        continue;
                    }
                    Set<OccupationPeriod> periodsList = new HashSet<OccupationPeriod>();
                    for (ExecutionDegree executionDegree : lesson.getExecutionCourse().getExecutionDegrees()) {
                        for (OccupationPeriodReference periodReference : executionDegree.getOccupationPeriodReferencesSet()) {
                            if (periodReference.getSemester() != null && periodReference.getSemester() == 2) {
                                if (periodReference.getOccupationPeriod().getNextPeriod() != null) {
                                    periodsList.add(periodReference.getOccupationPeriod());
                                }
                            }
                        }
                    }

                    if (periodsList.isEmpty()) {
                        continue;
                    }
                    if (periodsList.size() > 1) {
                        taskLog("Oh well...");
                    }
                    OccupationPeriod occupationPeriod = periodsList.iterator().next();
                    OccupationPeriod periodForLesson =
                            OccupationPeriod.createOccupationPeriodForLesson(lesson.getShift().getExecutionCourse(),
                                    occupationPeriod.getNextPeriod().getStartYearMonthDay(), occupationPeriod.getNextPeriod()
                                            .getEndYearMonthDayWithNextPeriods());
                    lesson.getPeriod().setNextPeriodWithoutChecks(periodForLesson);
                    periods.add(lesson.getPeriod());
                    count++;
                }
            }
        }
        taskLog("Number of Lessons %s\n", numberOfLessons);
        /*int differentPeriods = 0;
        for (OccupationPeriod period : periods) {
            if (isEqual(period, periods)) {
                break;
            } else {
                differentPeriods++;
            }
        }*/

        taskLog("Lessons whithout 2nd period: %s\n", count);
        taskLog("Different periods: %s\n", periods.size());
        //taskLog("REAL different periods: %s\n", differentPeriods);
        //throw new Exception();
    }

    private boolean isToExcludeDegree(Lesson lesson) {
        for (ExecutionDegree executionDegree : lesson.getExecutionCourse().getExecutionDegrees()) {
            if (degreesToExclude.contains(executionDegree.getDegree())) {
                return true;
            }
        }
        return false;
    }

    private boolean isEqual(OccupationPeriod period, Set<OccupationPeriod> periods) {
        boolean result = true;
        for (OccupationPeriod occupationPeriod : periods) {
            if (period != occupationPeriod) {
                if (!period.isEqualTo(occupationPeriod)) {
                    taskLog("Inicio: %s - Fim: %s\n", period.getStartYearMonthDay(), period.getEndYearMonthDay());
                }
                result &= period.isEqualTo(occupationPeriod);
            }
        }
        return result;
    }
}
