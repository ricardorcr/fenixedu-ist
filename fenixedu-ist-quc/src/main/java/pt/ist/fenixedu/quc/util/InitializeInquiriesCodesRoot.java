package pt.ist.fenixedu.quc.util;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixedu.quc.domain.InquiriesRoot;

public class InitializeInquiriesCodesRoot extends CustomTask {

    @Override
    public void runTask() throws Exception {
        Long inquiryQuestionLastCode =
                Bennu.getInstance().getInquiryQuestionsSet().stream().max((q1, q2) -> q1.getCode().compareTo(q2.getCode())).get()
                        .getCode();
        InquiriesRoot.getInstance().setLastInquiryQuestionCode(inquiryQuestionLastCode);
        taskLog("O úlitmo código na InquiryQuestion é %s\n", inquiryQuestionLastCode);
        Long inquiryAnswerLastCode =
                Bennu.getInstance().getInquiriesAnswersSet().stream().max((a1, a2) -> a1.getCode().compareTo(a2.getCode())).get()
                        .getCode();
        taskLog("O último código na InquiryAnswer é %s\n", inquiryAnswerLastCode);
        InquiriesRoot.getInstance().setLastInquiryAnswerCode(inquiryAnswerLastCode);
    }
}
