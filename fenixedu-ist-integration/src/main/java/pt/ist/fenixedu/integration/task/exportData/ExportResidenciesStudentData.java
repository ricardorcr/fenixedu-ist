package pt.ist.fenixedu.integration.task.exportData;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.fenixedu.academic.domain.Enrolment;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.GradeScale;
import org.fenixedu.academic.domain.StudentCurricularPlan;
import org.fenixedu.academic.domain.degreeStructure.CycleType;
import org.fenixedu.academic.domain.exceptions.DomainException;
import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.academic.domain.student.Student;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;
import org.joda.time.DateTime;

public class ExportResidenciesStudentData extends CustomTask {

    private static final Integer[] RDP_STUDENTS = new Integer[] { 72697, 77169, 81476, 80878, 81876, 80872, 81483, 78229, 81451,
            81170, 80842, 81425, 70157, 82316, 70150, 82905, 81782, 81842, 78662, 82905, 82905, 79156, 78239, 73257, 73509,
            81743, 67993, 78847, 80869, 69501, 81141, 69797, 75974, 81285, 81545, 73590, 76980, 78655, 81814, 81821, 69282,
            80925, 82573, 78638, 75387, 75878, 64919, 78502, 73749, 81659, 62886, 81013, 81195, 81714, 81430, 78240, 81114,
            81602, 78907, 78194, 81040, 81168, 81486, 81720, 82577, 76340, 80751, 81225, 76055, 78628, 76015, 81806, 79017,
            79667, 73107, 81172, 81821, 81564, 78867, 78975, 72726, 78460, 78156, 81085, 81546, 81538, 81226, 82577, 73511,
            76151, 81722, 67740, 81709, 82034, 82381, 75898, 81292, 80813, 81183, 81009, 81266, 81602, 81197, 70052, 81775,
            78731, 79266, 77011, 75912, 81370, 81024, 76079, 78446, 79673, 72718, 68075, 81424, 81692, 67911, 81285, 80821,
            81400, 81870, 76303, 76068, 67185, 78934, 80896 };

    private static final Integer[] RRR_STUDENTS = new Integer[] { 55629, 58207, 66358, 66367, 68142, 68247, 69515, 70648, 70650,
            72745, 72829, 73830, 73871, 73930, 73932, 73938, 76377, 76394, 76496, 76522, 76832, 77939, 77964, 77998, 78038,
            79532, 79808, 79873, 79873, 81159, 81914, 81917, 81933, 81962, 81996, 82002, 82011, 82025, 82033, 82055, 82079,
            82094, 82097, 82143, 82381, 82529, 82595 };

    private static ExecutionSemester PREVIOUS_SEMESTER = null;

    @Override
    public void runTask() throws Exception {
        PREVIOUS_SEMESTER = ExecutionSemester.readActualExecutionSemester().getPreviousExecutionPeriod();

        ByteArrayOutputStream rdpFileOS = new ByteArrayOutputStream();
        Spreadsheet rdpSpreadsheet = createSpreadsheet();
        for (int iter = 0; iter < RDP_STUDENTS.length; iter++) {
            Student student = Student.readStudentByNumber(RDP_STUDENTS[iter]);
            addInformation(rdpSpreadsheet, student);
        }
        rdpSpreadsheet.exportToXLSSheet(rdpFileOS);
        output(getFileName("Alunos_RDP_"), rdpFileOS.toByteArray());

        ByteArrayOutputStream rrrFileOS = new ByteArrayOutputStream();
        Spreadsheet rrrSpreadsheet = createSpreadsheet();
        for (int iter = 0; iter < RRR_STUDENTS.length; iter++) {
            Student student = Student.readStudentByNumber(RRR_STUDENTS[iter]);
            addInformation(rrrSpreadsheet, student);
        }
        rrrSpreadsheet.exportToXLSSheet(rrrFileOS);
        output(getFileName("Alunos_RRR_"), rrrFileOS.toByteArray());
    }

    private String getFileName(String startString) {
        String fileNameString =
                startString + PREVIOUS_SEMESTER.getName() + "_" + PREVIOUS_SEMESTER.getExecutionYear().getName() + "_"
                        + new DateTime().toString("dd_MM_yyyy") + ".xls";
        return fileNameString.replace("/", "-");
    }

    private Set<Enrolment> getEnrolments(final Student student) {
        StudentCurricularPlan scp = getStudentCurricularPlan(student, PREVIOUS_SEMESTER);

        return scp.getAllCurriculumLines().stream().filter(line -> !line.getExecutionPeriod().isAfter(PREVIOUS_SEMESTER))
                .filter(line -> line.isEnrolment()).filter(line -> !line.isDismissal()).map(line -> (Enrolment) line)
                .collect(Collectors.toSet());
    }

    private double getApprovedECTS(final Set<Enrolment> enrolments) {
        return enrolments.stream().filter(e -> e.isApproved()).mapToDouble(e -> e.getEctsCreditsForCurriculum().doubleValue())
                .sum();
    }

    private double getEnrolledECTS(final Set<Enrolment> enrolments) {
        return enrolments.stream().mapToDouble(e -> e.getEctsCreditsForCurriculum().doubleValue()).sum();
    }

    private double getApprovedGradeValuesSum(final Set<Enrolment> enrolments) {
        return enrolments.stream().filter(e -> e.getGrade().isNumeric())
                .filter(e -> GradeScale.TYPE20.equals(e.getGrade().getGradeScale()))
                .mapToDouble(e -> e.getGrade().getNumericValue().doubleValue()).sum();
    }

    private int getNumberOfApprovedCourses(final Set<Enrolment> enrolments) {
        return enrolments.stream().filter(e -> e.isApproved()).collect(Collectors.toList()).size() * 20;
    }

    private StudentCurricularPlan getStudentCurricularPlan(final Student student, final ExecutionSemester semester) {
        List<Registration> registrations = student.getActiveRegistrationsIn(semester);
        if (registrations.isEmpty()) {
            return null;
        }

        if (registrations.size() != 1) {
            //throw new DomainException("student.has.more.than.one.active.registration", null);
        }

        Registration registration = registrations.iterator().next();
        final StudentCurricularPlan studentCurricularPlan = registration.getLastStudentCurricularPlan();
        if (!studentCurricularPlan.isBolonhaDegree()) {
            throw new DomainException("student.curricular.plan.is.not.bolonha", null);
        }

        return studentCurricularPlan;
    }

    private Spreadsheet createSpreadsheet() {
        final Spreadsheet spreadsheet = new Spreadsheet("students");

        spreadsheet.setHeaders(new String[] { "Num Aluno", "Nome", "Tipo Curso", "Curso", "Ciclo", "Ects Aprovados",
                "Ects Total", "Soma Classificações", "Num Aprovadas * 20" });

        return spreadsheet;
    }

    private void addInformation(final Spreadsheet spreadsheet, final Student student) {
        StudentCurricularPlan studentCurricularPlan = getStudentCurricularPlan(student, PREVIOUS_SEMESTER);
        if (studentCurricularPlan == null) {
            return;
        }
        Set<Enrolment> enrolments = getEnrolments(student);

        final Row row = spreadsheet.addRow();
        row.setCell(student.getNumber());
        row.setCell(student.getPerson().getName());
        row.setCell(studentCurricularPlan.getDegreeType().getName().getContent());
        row.setCell(studentCurricularPlan.getName());
        CycleType cycleType = studentCurricularPlan.getRegistration().getCycleType(PREVIOUS_SEMESTER.getExecutionYear());
        if (cycleType != null) {
            row.setCell(cycleType.getDescription());
        } else {
            row.setCell("");
        }
        row.setCell(getApprovedECTS(enrolments));
        row.setCell(getEnrolledECTS(enrolments));
        row.setCell(getApprovedGradeValuesSum(enrolments));
        row.setCell(getNumberOfApprovedCourses(enrolments));
    }
}
