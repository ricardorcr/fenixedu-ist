package pt.ist.fenixedu.integration.task;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixedu.delegates.domain.student.YearDelegate;
import pt.ist.fenixedu.quc.domain.InquiryDelegateAnswer;

public class DeleteInquiryAnswers extends CustomTask {

    @Override
    public void runTask() throws Exception {
        //deleteInquiryDelegateAnswers();
        deleteInquiryTeacherAnswers();
        deleteInquiryRegentAnswers();
    }

    private void deleteInquiryTeacherAnswers() {
        ExecutionSemester executionSemester = ExecutionSemester.readActualExecutionSemester().getPreviousExecutionPeriod();
        Bennu.getInstance()
                .getProfessorshipsSet()
                .stream()
                .filter(p -> p.getExecutionCourse().getExecutionPeriod() == executionSemester
                        && p.getInquiryTeacherAnswer() != null).forEach(p -> {
                    p.getInquiryTeacherAnswer().getQuestionAnswersSet().stream().forEach(qa -> qa.delete());
                    p.getInquiryTeacherAnswer().delete();
                });
    }

    private void deleteInquiryRegentAnswers() {
        ExecutionSemester executionSemester = ExecutionSemester.readActualExecutionSemester().getPreviousExecutionPeriod();
        Bennu.getInstance()
                .getProfessorshipsSet()
                .stream()
                .filter(p -> p.getExecutionCourse().getExecutionPeriod() == executionSemester
                        && p.getInquiryRegentAnswer() != null).forEach(p -> {
                    p.getInquiryRegentAnswer().getQuestionAnswersSet().stream().forEach(qa -> qa.delete());
                    p.getInquiryRegentAnswer().delete();
                });
    }

    private void deleteInquiryDelegateAnswers() throws Exception {
        final List<InquiryDelegateAnswer> inquiryAnswers = new ArrayList<InquiryDelegateAnswer>();
        Bennu.getInstance().getDelegatesSet().stream().filter(delegate -> delegate.isYearDelegate())
                .forEach(delegate -> inquiryAnswers.addAll(((YearDelegate) delegate).getInquiryDelegateAnswersSet()));

        for (InquiryDelegateAnswer inquiryDelegateAnswer : inquiryAnswers) {
            inquiryDelegateAnswer.setDelegate(null);
            inquiryDelegateAnswer.setExecutionCourse(null);
            inquiryDelegateAnswer.setRootDomainObject(null);

            final Method method = inquiryDelegateAnswer.getClass().getMethod("deleteDomainObject");
            method.setAccessible(true);
            method.invoke(inquiryDelegateAnswer);
        }
    }
}
