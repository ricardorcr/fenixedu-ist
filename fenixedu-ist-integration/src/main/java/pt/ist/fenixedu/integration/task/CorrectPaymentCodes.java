package pt.ist.fenixedu.integration.task;

import org.fenixedu.academic.domain.accounting.PaymentCode;
import org.fenixedu.academic.domain.accounting.PaymentCodeType;
import org.fenixedu.academic.domain.accounting.paymentCodes.IndividualCandidacyPaymentCode;
import org.fenixedu.academic.util.Money;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.LocalDate;

public class CorrectPaymentCodes extends CustomTask {

    @Override
    public void runTask() throws Exception {
        LocalDate today = new LocalDate(2015, 05, 07);
        Money amount = new Money(100.0);

        //delete
        for (PaymentCode paymentCode : Bennu.getInstance().getPaymentCodesSet()) {
            if (paymentCode.getWhenCreated().toLocalDate().equals(today)) {
                taskLog("Código %s apagad\no", paymentCode.getCode());
                paymentCode.delete();
            }
        }

        //create the correct ones
        IndividualCandidacyPaymentCode.createPaymentCodes(PaymentCodeType.OVER_23_INDIVIDUAL_CANDIDACY_PROCESS, new LocalDate(
                2015, 05, 8), new LocalDate(2015, 05, 25), amount, amount, 80);
    }
}
