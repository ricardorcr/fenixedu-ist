package pt.ist.fenixedu.integration.task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.fenixedu.academic.domain.Department;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;

import pt.ist.fenixedu.quc.domain.InquiryResult;
import pt.ist.fenixedu.quc.domain.InquiryResultComment;
import pt.ist.fenixedu.quc.domain.TeacherInquiryTemplate;

public class TeacherQucReport extends CustomTask {

    @Override
    public void runTask() throws Exception {
        final TeacherInquiryTemplate teacherInquiryTemplate =
                TeacherInquiryTemplate.getTemplateByExecutionPeriod(ExecutionSemester.readActualExecutionSemester()
                        .getPreviousExecutionPeriod());

        final ExecutionSemester executionPeriod = teacherInquiryTemplate.getExecutionPeriod();

        final List<TeacherBean> teachersList = new ArrayList<TeacherBean>();
        for (Professorship professorship : Bennu.getInstance().getProfessorshipsSet()) {
            if (professorship.getExecutionCourse().getExecutionPeriod() == executionPeriod) {
                Person person = professorship.getPerson();
                boolean isToAnswer = TeacherInquiryTemplate.hasToAnswerTeacherInquiry(person, professorship);
                if (isToAnswer) {
                    boolean hasMandatoryCommentsToMake = InquiryResultComment.hasMandatoryCommentsToMake(professorship);
                    Department department = null;
                    if (person.getEmployee() != null) {
                        department =
                                person.getEmployee().getLastDepartmentWorkingPlace(
                                        teacherInquiryTemplate.getExecutionPeriod().getBeginDateYearMonthDay(),
                                        teacherInquiryTemplate.getExecutionPeriod().getEndDateYearMonthDay());
                    }
                    TeacherBean teacherBean = new TeacherBean(department, person, professorship);
                    teacherBean.setCommentsToMake(hasMandatoryCommentsToMake);
                    int questionsToAnswer =
                            professorship.getInquiryTeacherAnswer() != null ? teacherInquiryTemplate.getNumberOfQuestions()
                                    - professorship.getInquiryTeacherAnswer().getNumberOfAnsweredQuestions() : teacherInquiryTemplate
                                    .getNumberOfQuestions();
                    int mandatoryQuestionsToAnswer =
                            professorship.getInquiryTeacherAnswer() != null ? teacherInquiryTemplate
                                    .getNumberOfRequiredQuestions()
                                    - professorship.getInquiryTeacherAnswer().getNumberOfAnsweredRequiredQuestions() : teacherInquiryTemplate
                                    .getNumberOfRequiredQuestions();
                    teacherBean.setMandatoryQuestionsToAnswer(mandatoryQuestionsToAnswer);
                    teacherBean.setQuestionsToAnswer(questionsToAnswer);
                    teachersList.add(teacherBean);
                }
            }
        }

        Spreadsheet spreadsheet = createReport(teachersList);

        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        spreadsheet.exportToXLSSheet(byteArrayOS);

        output("relatorio_docentes_quc.xls", byteArrayOS.toByteArray());
    }

    private Spreadsheet createReport(List<TeacherBean> teachersList) throws IOException {
        Spreadsheet spreadsheet = new Spreadsheet("Relatório Docentes QUC");
        spreadsheet.setHeader("Departamento");
        spreadsheet.setHeader("Docente");
        spreadsheet.setHeader("Nº Mec");
        spreadsheet.setHeader("Telefone");
        spreadsheet.setHeader("Email");
        spreadsheet.setHeader("Comentários obrigatórios por fazer");
        spreadsheet.setHeader("Perguntas obrigatórias por responder");
        spreadsheet.setHeader("Perguntas por responder");
        spreadsheet.setHeader("Disciplina");
        spreadsheet.setHeader("Disciplina sujeita auditoria?");

        for (TeacherBean teacherBean : teachersList) {
            Row row = spreadsheet.addRow();
            row.setCell(teacherBean.getDepartment() != null ? teacherBean.getDepartment().getName() : "-");
            row.setCell(teacherBean.getTeacher().getName());
            row.setCell(teacherBean.getTeacher().getUsername());
            row.setCell(teacherBean.getTeacher().getDefaultMobilePhoneNumber());
            row.setCell(teacherBean.getTeacher().getDefaultEmailAddressValue());
            row.setCell(teacherBean.isCommentsToMake() ? "Sim" : "Não");
            row.setCell(teacherBean.getMandatoryQuestionsToAnswer());
            row.setCell(teacherBean.getQuestionsToAnswer());
            row.setCell(teacherBean.getProfessorship().getExecutionCourse().getName());
            row.setCell(InquiryResult.canBeSubjectToQucAudit(teacherBean.getProfessorship().getExecutionCourse()) ? "Sim" : "Não");
        }

        return spreadsheet;
    }

    class TeacherBean {
        private Department department;
        private Person teacher;
        private Professorship professorship;
        private boolean commentsToMake;
        private int questionsToAnswer;
        private int mandatoryQuestionsToAnswer;

        public TeacherBean(Department department, Person teacher, Professorship professorship) {
            setDepartment(department);
            setTeacher(teacher);
            setProfessorship(professorship);
        }

        public void setDepartment(Department department) {
            this.department = department;
        }

        public Department getDepartment() {
            return department;
        }

        public void setTeacher(Person teacher) {
            this.teacher = teacher;
        }

        public Person getTeacher() {
            return teacher;
        }

        public Professorship getProfessorship() {
            return professorship;
        }

        public void setProfessorship(Professorship professorship) {
            this.professorship = professorship;
        }

        public void setCommentsToMake(boolean commentsToMake) {
            this.commentsToMake = commentsToMake;
        }

        public boolean isCommentsToMake() {
            return commentsToMake;
        }

        public int getQuestionsToAnswer() {
            return questionsToAnswer;
        }

        public void setQuestionsToAnswer(int questionsToAnswer) {
            this.questionsToAnswer = questionsToAnswer;
        }

        public int getMandatoryQuestionsToAnswer() {
            return mandatoryQuestionsToAnswer;
        }

        public void setMandatoryQuestionsToAnswer(int mandatoryQuestionsToAnswer) {
            this.mandatoryQuestionsToAnswer = mandatoryQuestionsToAnswer;
        }
    }

}
