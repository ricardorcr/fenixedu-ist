package pt.ist.fenixedu.integration.task.exportData;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Set;

import org.fenixedu.academic.domain.Degree;
import org.fenixedu.academic.domain.DegreeCurricularPlan;
import org.fenixedu.academic.domain.Enrolment;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Grade;
import org.fenixedu.academic.domain.IEnrolment;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.StudentCurricularPlan;
import org.fenixedu.academic.domain.degree.DegreeType;
import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.academic.domain.student.Student;
import org.fenixedu.academic.domain.studentCurriculum.Credits;
import org.fenixedu.academic.domain.studentCurriculum.CurriculumGroup;
import org.fenixedu.academic.domain.studentCurriculum.CurriculumLine;
import org.fenixedu.academic.domain.studentCurriculum.CurriculumModule;
import org.fenixedu.academic.domain.studentCurriculum.Dismissal;
import org.fenixedu.academic.domain.studentCurriculum.EnrolmentWrapper;
import org.fenixedu.academic.domain.studentCurriculum.RootCurriculumGroup;
import org.fenixedu.academic.domain.studentCurriculum.Substitution;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;

public class StudentsMeritReport extends CustomTask {

    private static final String EXECUTION_YEAR_STRING = "2013/2014";
    private static final String OUTPUT_FILENAME = "MeritStudents.xls";
    private static final MathContext MATH_CONTEXT = new MathContext(3, RoundingMode.HALF_EVEN);
    private static final DegreeType[] DEGREE_TYPES = null;/*new DegreeType[] { DegreeType.BOLONHA_DEGREE,
                                                          DegreeType.BOLONHA_MASTER_DEGREE, DegreeType.BOLONHA_INTEGRATED_MASTER_DEGREE };*///TODO change to the new model

    @Override
    public void runTask() throws Exception {

        for (int iter = 0; iter < DEGREE_TYPES.length; iter++) {
            final DegreeType degreeType = DEGREE_TYPES[iter];

            final Spreadsheet spreadsheet = createHeader();
            for (final Degree degree : Bennu.getInstance().getDegreesSet()) {
                if (degreeType != degree.getDegreeType()) {
                    continue;
                }

                final ExecutionYear executionYearForReport = readExecutionYear(EXECUTION_YEAR_STRING);
                for (final DegreeCurricularPlan degreeCurricularPlan : degree.getDegreeCurricularPlansSet()) {
                    taskLog(degree.getPresentationName() + " - " + degreeCurricularPlan.getName());

                    for (final StudentCurricularPlan studentCurricularPlan : degreeCurricularPlan.getStudentCurricularPlansSet()) {
                        final Registration registration = studentCurricularPlan.getRegistration();
                        final Student student = registration.getStudent();
                        if (registration.hasAnyActiveState(executionYearForReport)) {
                            final double approvedCredits =
                                    getCredits(executionYearForReport, studentCurricularPlan.getRegistration().getStudent(), true);
                            final double enrolledCredits =
                                    getCredits(executionYearForReport, studentCurricularPlan.getRegistration().getStudent(),
                                            false);
                            final Person person = student.getPerson();
                            final int curricularYear = registration.getCurricularYear(executionYearForReport);

                            final Row row = spreadsheet.addRow();
                            row.setCell(degree.getSigla());
                            row.setCell(student.getNumber());
                            row.setCell(person.getName());
                            row.setCell(enrolledCredits);
                            row.setCell(approvedCredits);
                            row.setCell(curricularYear);

                            final BigDecimal average = calculateAverage(registration, executionYearForReport);
                            row.setCell(average.toPlainString());
                        }
                    }
                }
            }
            ByteArrayOutputStream reportFileOS = new ByteArrayOutputStream();
            spreadsheet.exportToXLSSheet(reportFileOS);
            output(degreeType.getName() + "_" + OUTPUT_FILENAME, reportFileOS.toByteArray());
        }
    }

    private Spreadsheet createHeader() {
        Spreadsheet spreadsheet = new Spreadsheet("MeritStudents");

        spreadsheet.setHeader("Degree");
        spreadsheet.setHeader("Number");
        spreadsheet.setHeader("Name");
        spreadsheet.setHeader("Credits Enroled in " + EXECUTION_YEAR_STRING);
        spreadsheet.setHeader("Credits Approved Durring Year " + EXECUTION_YEAR_STRING);
        spreadsheet.setHeader("Curricular Year During " + EXECUTION_YEAR_STRING);
        spreadsheet.setHeader("Average in " + EXECUTION_YEAR_STRING);

        return spreadsheet;
    }

    private double getCredits(final ExecutionYear executionYear, final Student student, final boolean approvedCredits) {
        double creditsCount = 0.0;
        for (final Registration registration : student.getRegistrationsSet()) {
            for (final StudentCurricularPlan studentCurricularPlan : registration.getStudentCurricularPlansSet()) {
                final RootCurriculumGroup root = studentCurricularPlan.getRoot();
                final Set<CurriculumModule> modules =
                        root == null ? (Set) studentCurricularPlan.getEnrolmentsSet() : root.getCurriculumModulesSet();
                creditsCount += countCredits(executionYear, modules, approvedCredits);
            }
        }
        return creditsCount;
    }

    private double countCredits(final ExecutionYear executionYear, final Set<CurriculumModule> modules,
            final boolean approvedCredits) {
        double creditsCount = 0.0;
        for (final CurriculumModule module : modules) {
            if (module instanceof CurriculumGroup) {
                final CurriculumGroup courseGroup = (CurriculumGroup) module;
                creditsCount += countCredits(executionYear, courseGroup.getCurriculumModulesSet(), approvedCredits);
            } else if (module instanceof CurriculumLine) {
                final CurriculumLine curriculumLine = (CurriculumLine) module;
                if (curriculumLine.getExecutionYear() == executionYear) {
                    if (approvedCredits) {
                        creditsCount += curriculumLine.getAprovedEctsCredits().doubleValue();
                    } else {
                        creditsCount += curriculumLine.getEctsCredits().doubleValue();
                    }
                }
            }
        }
        return creditsCount;
    }

    private BigDecimal calculateAverage(final Registration registration, final ExecutionYear executionYear) {
        BigDecimal[] result = new BigDecimal[] { new BigDecimal(0.000, MATH_CONTEXT), new BigDecimal(0.000, MATH_CONTEXT) };
        for (final StudentCurricularPlan studentCurricularPlan : registration.getStudentCurricularPlansSet()) {
            final RootCurriculumGroup root = studentCurricularPlan.getRoot();
            final Set<CurriculumModule> modules =
                    root == null ? (Set) studentCurricularPlan.getEnrolmentsSet() : root.getCurriculumModulesSet();
            calculateAverage(result, modules, executionYear);
        }
        return result[1].equals(BigDecimal.ZERO) ? result[1] : result[0].divide(result[1], MATH_CONTEXT);
    }

    private void calculateAverage(final BigDecimal[] result, final Set<CurriculumModule> modules,
            final ExecutionYear executionYear) {
        for (final CurriculumModule module : modules) {
            if (module instanceof CurriculumGroup) {
                final CurriculumGroup courseGroup = (CurriculumGroup) module;
                calculateAverage(result, courseGroup.getCurriculumModulesSet(), executionYear);
            } else if (module instanceof Enrolment) {
                final Enrolment enrolment = (Enrolment) module;
                if (enrolment.isApproved()) {
                    if (enrolment.getExecutionYear() == executionYear) {
                        final Grade grade = enrolment.getGrade();
                        if (grade.isNumeric()) {
                            final BigDecimal ectsCredits = new BigDecimal(enrolment.getEctsCredits());
                            final BigDecimal value = grade.getNumericValue().multiply(ectsCredits);
                            result[0] = result[0].add(value);
                            result[1] = result[1].add(ectsCredits);
                        }
                    }
                }
            } else if (module instanceof Dismissal) {
                final Dismissal dismissal = (Dismissal) module;
                if (dismissal.getExecutionYear() == executionYear && dismissal.getCurricularCourse() != null
                        && !dismissal.getCurricularCourse().isOptionalCurricularCourse()) {

                    final Credits credits = dismissal.getCredits();
                    if (credits instanceof Substitution) {
                        final Substitution substitution = (Substitution) credits;
                        for (final EnrolmentWrapper enrolmentWrapper : substitution.getEnrolmentsSet()) {
                            final IEnrolment iEnrolment = enrolmentWrapper.getIEnrolment();

                            final Grade grade = iEnrolment.getGrade();
                            if (grade.isNumeric()) {
                                final BigDecimal ectsCredits = new BigDecimal(iEnrolment.getEctsCredits());
                                final BigDecimal value = grade.getNumericValue().multiply(ectsCredits);
                                result[0] = result[0].add(value);
                                result[1] = result[1].add(ectsCredits);
                            }
                        }
                    } else {
                        final Grade grade = dismissal.getGrade();
                        if (grade.isNumeric()) {
                            final BigDecimal ectsCredits = new BigDecimal(dismissal.getEctsCredits());
                            final BigDecimal value = grade.getNumericValue().multiply(ectsCredits);
                            result[0] = result[0].add(value);
                            result[1] = result[1].add(ectsCredits);
                        }
                    }
                }
            }
        }
    }

    private ExecutionYear readExecutionYear(final String executionYearString) {
        for (final ExecutionYear executionYear : Bennu.getInstance().getExecutionYearsSet()) {
            if (executionYear.getYear().equals(executionYearString)) {
                return executionYear;
            }
        }
        return null;
    }
}
