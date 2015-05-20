package pt.ist.fenixedu.integration.task;

import java.io.ByteArrayOutputStream;

import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.reports.GepReportFile;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;

import pt.ist.fenixedu.quc.domain.QuestionAnswer;
import pt.ist.fenixedu.quc.domain.TeacherInquiryTemplate;

public class TeacherQucAnswersReport extends CustomTask {

    @Override
    public void runTask() throws Exception {
        final TeacherInquiryTemplate teacherInquiryTemplate =
                TeacherInquiryTemplate.getTemplateByExecutionPeriod(ExecutionSemester.readActualExecutionSemester()
                        .getPreviousExecutionPeriod());

        final ExecutionSemester executionSemester = teacherInquiryTemplate.getExecutionPeriod();
        Spreadsheet spreadsheet = new Spreadsheet("Respostas Docentes QUC");
        spreadsheet.setHeader("Período Execução");
        spreadsheet.setHeader("Disciplina Execução");
        spreadsheet.setHeader("Docente");
        spreadsheet.setHeader("Pergunta");
        spreadsheet.setHeader("Resposta");

        for (Professorship professorship : Bennu.getInstance().getProfessorshipsSet()) {
            if (professorship.getExecutionCourse().getExecutionPeriod() == executionSemester) {
                Person person = professorship.getPerson();
                boolean isToAnswer = TeacherInquiryTemplate.hasToAnswerTeacherInquiry(person, professorship);
                if (isToAnswer) {
                    for (QuestionAnswer questionAnswer : professorship.getInquiryTeacherAnswer().getQuestionAnswersSet()) {
                        Row row = spreadsheet.addRow();
                        row.setCell(GepReportFile.getExecutionSemesterCode(executionSemester));
                        row.setCell(GepReportFile.getExecutionCourseCode(professorship.getExecutionCourse()));
                        row.setCell(professorship.getPerson().getUsername());
                        row.setCell(questionAnswer.getInquiryAnswer().getCode().toString());
                        row.setCell(questionAnswer.getAnswer());
                    }
                }
            }
        }

        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        spreadsheet.exportToXLSSheet(byteArrayOS);

        output("respostas_docentes_quc.xls", byteArrayOS.toByteArray());
    }
}
