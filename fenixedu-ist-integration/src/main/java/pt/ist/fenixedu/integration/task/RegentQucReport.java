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
import pt.ist.fenixedu.quc.domain.RegentInquiryTemplate;

public class RegentQucReport extends CustomTask {

    @Override
    public void runTask() throws Exception {
        final RegentInquiryTemplate regentInquiryTemplate =
                RegentInquiryTemplate.getTemplateByExecutionPeriod(ExecutionSemester.readActualExecutionSemester()
                        .getPreviousExecutionPeriod());

        final ExecutionSemester executionPeriod = regentInquiryTemplate.getExecutionPeriod();

        final List<RegentBean> regentsList = new ArrayList<RegentBean>();
        for (Professorship professorship : Bennu.getInstance().getProfessorshipsSet()) {
            if (professorship.getExecutionCourse().getExecutionPeriod() == executionPeriod) {
                Person person = professorship.getPerson();
                boolean isToAnswer = RegentInquiryTemplate.hasToAnswerRegentInquiry(professorship);
                if (isToAnswer) {
                    boolean hasMandatoryCommentsToMake =
                            InquiryResultComment.hasMandatoryCommentsToMakeAsResponsible(professorship);
                    Department department = null;
                    if (person.getEmployee() != null) {
                        department =
                                person.getEmployee().getLastDepartmentWorkingPlace(
                                        regentInquiryTemplate.getExecutionPeriod().getBeginDateYearMonthDay(),
                                        regentInquiryTemplate.getExecutionPeriod().getEndDateYearMonthDay());
                    }
                    RegentBean regentBean = new RegentBean(department, person, professorship);
                    regentBean.setCommentsToMake(hasMandatoryCommentsToMake);
                    int questionsToAnswer =
                            professorship.getInquiryRegentAnswer() != null ? regentInquiryTemplate.getNumberOfQuestions()
                                    - professorship.getInquiryRegentAnswer().getNumberOfAnsweredQuestions() : regentInquiryTemplate
                                    .getNumberOfQuestions();
                    int mandatoryQuestionsToAnswer =
                            professorship.getInquiryRegentAnswer() != null ? regentInquiryTemplate.getNumberOfRequiredQuestions()
                                    - professorship.getInquiryRegentAnswer().getNumberOfAnsweredRequiredQuestions() : regentInquiryTemplate
                                    .getNumberOfRequiredQuestions();
                    regentBean.setMandatoryQuestionsToAnswer(mandatoryQuestionsToAnswer);
                    regentBean.setQuestionsToAnswer(questionsToAnswer);
                    regentsList.add(regentBean);
                }
            }
        }

        Spreadsheet spreadsheet = createReport(regentsList);

        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        spreadsheet.exportToXLSSheet(byteArrayOS);

        output("relatorio_regentes_quc.xls", byteArrayOS.toByteArray());
    }

    private Spreadsheet createReport(List<RegentBean> regentsList) throws IOException {
        Spreadsheet spreadsheet = new Spreadsheet("Relatório Regentes QUC");
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

        for (RegentBean regentBean : regentsList) {
            Row row = spreadsheet.addRow();
            row.setCell(regentBean.getDepartment() != null ? regentBean.getDepartment().getName() : "-");
            row.setCell(regentBean.getRegent().getName());
            row.setCell(regentBean.getRegent().getUsername());
            row.setCell(regentBean.getRegent().getDefaultMobilePhoneNumber());
            row.setCell(regentBean.getRegent().getDefaultEmailAddressValue());
            row.setCell(regentBean.isCommentsToMake() ? "Sim" : "Não");
            row.setCell(regentBean.getMandatoryQuestionsToAnswer());
            row.setCell(regentBean.getQuestionsToAnswer());
            row.setCell(regentBean.getProfessorship().getExecutionCourse().getName());
            row.setCell(InquiryResult.canBeSubjectToQucAudit(regentBean.getProfessorship().getExecutionCourse()) ? "Sim" : "Não");
        }

        return spreadsheet;
    }

    class RegentBean {
        private Department department;
        private Person regent;
        private Professorship professorship;
        private boolean commentsToMake;
        private int questionsToAnswer;
        private int mandatoryQuestionsToAnswer;

        public RegentBean(Department department, Person regent, Professorship professorship) {
            setDepartment(department);
            setRegent(regent);
            setProfessorship(professorship);
        }

        public void setDepartment(Department department) {
            this.department = department;
        }

        public Department getDepartment() {
            return department;
        }

        public void setRegent(Person regent) {
            this.regent = regent;
        }

        public Person getRegent() {
            return regent;
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
