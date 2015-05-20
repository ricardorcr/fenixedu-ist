package pt.ist.fenixedu.integration.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.validator.EmailValidator;
import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.util.email.Message;
import org.fenixedu.academic.domain.util.email.Sender;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixedu.quc.domain.InquiryResultComment;
import pt.ist.fenixedu.quc.domain.TeacherInquiryTemplate;
import pt.ist.fenixframework.FenixFramework;

public class SendTeachersQUCWarningEmail extends CustomTask {

    @Override
    public void runTask() throws Exception {

        final Map<Person, List<ExecutionCourse>> teachers = getTeachers();
        Sender sender = getCPSender();
        int count = 0;
        for (Person teacher : teachers.keySet()) {
            if (teacher.getDefaultEmailAddress() == null) {
                continue;
            }
            String emailAddress = teacher.getDefaultEmailAddress().getValue();
            if (!EmailValidator.getInstance().isValid(emailAddress)) {
                continue;
            }
            sendEmail(teacher, teachers.get(teacher), sender);
            count++;
        }
        taskLog("%s mails have been sent.", count);
    }

    private void sendEmail(Person teacher, List<ExecutionCourse> inquiriesCoursesToRespond, Sender sender) {
        Locale locale = new Locale("pt", "PT");
        final ResourceBundle resourceBundle = ResourceBundle.getBundle("resources.ApplicationResources", locale);

        final StringBuilder message = new StringBuilder("Caro(a) colega ");
        message.append(teacher.getName()).append(",\n\n");
        message.append("Como deve ser do seu conhecimento, o prazo de preenchimento do relatório de docência relativo à(s) UC(s) ");
        int count = 1;
        for (ExecutionCourse executionCourse : inquiriesCoursesToRespond) {
            if (count > 1) {
                message.append(", ");
            }
            message.append(executionCourse.getName());
            count++;
        }
        message.append(" termina hoje dia 26 de outubro de 2015.\n\n");
        message.append("De acordo com o sistema informático, o seu relatório, de preenchimento obrigatório, ainda não está completamente preenchido.");
        message.append("Dada a importância que esta recolha de informação apresenta para a monitorização da qualidade do ensino, solicita-se a sua colaboração.\n\n");
        message.append("Para preencher o relatório, por favor faça login no sistema Fénix, e se não lhe aparecer o aviso com o link para preenchimento do respetivo relatório, por favor aceda ao separador"
                + " \"Docência\" onde pode escolher o 2º sem de 2014/15 e cada uma das UC lecionadas; terá então acesso, na coluna do lado esquerdo em baixo,"
                + " à seção QUC - Relatórios e Resultados onde poderá proceder ao preenchimento do relatório.\n\n");
        message.append("Caso exista algum problema com o seu preenchimento por favor comunique-nos o mesmo com a máxima urgência para o endereço cp@ist.utl.pt para que o mesmo possa ser corrigido.\n\n");
        message.append("Relembro que o preenchimento do relatório de docência é obrigatório.\n\n");
        message.append("Com os melhores cumprimentos,\n\n");
        message.append("Luís Santos Castro\nVice - Presidente do Conselho Pedagógico");

        message.append("\n\n---\n");
        message.append(resourceBundle.getString("message.email.footer.prefix"));
        message.append(sender.getFromName());
        message.append(resourceBundle.getString("message.email.footer.prefix.suffix"));
        message.append("\n\t");
        message.append(teacher.getName());
        message.append("\n");

        String subject = "Preenchimento QUC - Relatório de Docência";

        final Set<String> bccs = new HashSet<String>();
        bccs.add(teacher.getDefaultEmailAddress().getValue());

        new Message(sender, sender.getReplyTosSet(), null, subject, message.toString(), bccs);
    }

    private Map<Person, List<ExecutionCourse>> getTeachers() {
        final Map<Person, List<ExecutionCourse>> teachersMap = new HashMap<Person, List<ExecutionCourse>>();
        final TeacherInquiryTemplate teacherInquiryTemplate = TeacherInquiryTemplate.getCurrentTemplate();
        if (teacherInquiryTemplate != null) {
            final ExecutionSemester executionPeriod = teacherInquiryTemplate.getExecutionPeriod();
            for (Professorship professorship : Bennu.getInstance().getProfessorshipsSet()) {
                if (professorship.getExecutionCourse().getExecutionPeriod() == executionPeriod) {
                    boolean isToAnswer =
                            TeacherInquiryTemplate.hasToAnswerTeacherInquiry(professorship.getPerson(), professorship);
                    if (isToAnswer
                            && ((professorship.getInquiryTeacherAnswer() == null || professorship.getInquiryTeacherAnswer()
                                    .hasRequiredQuestionsToAnswer(teacherInquiryTemplate)) || InquiryResultComment
                                    .hasMandatoryCommentsToMake(professorship))) {
                        if (teachersMap.get(professorship.getPerson()) == null) {
                            teachersMap.put(professorship.getPerson(), new ArrayList<ExecutionCourse>());
                        }
                        List<ExecutionCourse> executionCourseList = teachersMap.get(professorship.getPerson());
                        executionCourseList.add(professorship.getExecutionCourse());
                    }
                }
            }
        }
        return teachersMap;
    }

    private Sender getCPSender() {
        return FenixFramework.getDomainObject("4264902526932");
    }
}
