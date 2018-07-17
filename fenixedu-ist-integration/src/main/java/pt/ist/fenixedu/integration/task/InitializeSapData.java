package pt.ist.fenixedu.integration.task;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.domain.accounting.calculator.DebtInterestCalculator;
import org.fenixedu.academic.domain.accounting.events.gratuity.GratuityEventWithPaymentPlan;
import org.fenixedu.academic.domain.phd.debts.PhdGratuityEvent;
import org.fenixedu.academic.util.Money;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.DateTime;

import com.google.gson.JsonObject;

import pt.ist.fenixedu.domain.SapRequest;
import pt.ist.fenixedu.domain.SapRequestType;
import pt.ist.fenixedu.giaf.invoices.ClientMap;
import pt.ist.fenixedu.giaf.invoices.Utils;

public class InitializeSapData extends CustomTask {

    private static ExecutionYear startYear = null;
    private static ExecutionYear SAP_3RD_CYCLE_THRESHOLD = null;
    private static DateTime FIRST_DAY = new DateTime(2018, 01, 01, 00, 00);
    private static DateTime LAST_DAY = new DateTime(2017, 12, 31, 23, 59, 59);
    private Money payments = Money.ZERO;
    private Money exemptions = Money.ZERO;

    private static boolean needsProcessing(final Event event) {
        try {
            final ExecutionYear executionYear = Utils.executionYearOf(event);
            return !event.isCancelled()
                    && (!executionYear.isBefore(startYear)
                            || (event instanceof PhdGratuityEvent && !executionYear.isBefore(SAP_3RD_CYCLE_THRESHOLD)))
                    && (!event.getAccountingTransactionsSet().isEmpty() || !event.getExemptionsSet().isEmpty());
        } catch (Exception e) {
            System.out.println("O evento " + event.getExternalId() + " deu o seguinte erro: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void runTask() throws Exception {
        payments = Money.ZERO;
        exemptions = Money.ZERO;

        startYear = ExecutionYear.readExecutionYearByName("2015/2016");
        SAP_3RD_CYCLE_THRESHOLD = ExecutionYear.readExecutionYearByName("2014/2015");
        Bennu.getInstance().getAccountingEventsSet().stream().filter(InitializeSapData::needsProcessing).forEach(event -> {
            try {
                process(event);
            } catch (Exception e) {
                taskLog("Erro no evento %s %s\n", event.getExternalId(), e.getMessage());
                e.printStackTrace();
            }
        });
        taskLog("O valor dos pagamentos foi de %s\n", payments.toPlainString());
        taskLog("O valor das insenções foi de %s\n", exemptions.toPlainString());
    }

    public void process(Event event) {
        DebtInterestCalculator debtInterestCalculator = event.getDebtInterestCalculator(LAST_DAY);
        Money amountPayed = processPayments(event, debtInterestCalculator);
        processExemptions(event, amountPayed, debtInterestCalculator);
        processReimbursements(event);
    }

    private Money processPayments(Event event, DebtInterestCalculator debtInterestCalculator) {

        Money paidAmount = new Money(debtInterestCalculator.getPaidDebtAmount());

        String clientId = null;
        if (event.getParty().isPerson()) {
            clientId = ClientMap.uVATNumberFor(event.getPerson());
        } else {
            clientId = event.getParty().getExternalId();
            //taskLog("Dívida de empresa!! - %s\n", clientId);
            return Money.ZERO;
        }

        if (paidAmount.isPositive()) {
            payments = payments.add(paidAmount);

            SapRequest sapInvoiceRequest =
                    new SapRequest(event, clientId, paidAmount, "ND0", SapRequestType.INVOICE, Money.ZERO, new JsonObject());
            sapInvoiceRequest.setWhenSent(FIRST_DAY);
            sapInvoiceRequest.setSent(true);
            sapInvoiceRequest.setIntegrated(true);
//            persistLocalChange(clientId, "ND0", "", "invoice", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
            if (event.isGratuity()) {
                SapRequest sapDebtRequest =
                        new SapRequest(event, clientId, paidAmount, "NG0", SapRequestType.DEBT, Money.ZERO, new JsonObject());
                sapDebtRequest.setWhenSent(FIRST_DAY);
                sapDebtRequest.setSent(true);
                sapDebtRequest.setIntegrated(true);
//                persistLocalChange(clientId, "NG0", "", "debt", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
            }

            SapRequest sapPaymentRequest =
                    new SapRequest(event, clientId, paidAmount, "NP0", SapRequestType.PAYMENT, Money.ZERO, new JsonObject());
            sapPaymentRequest.setWhenSent(FIRST_DAY);
            sapPaymentRequest.setSent(true);
            sapPaymentRequest.setIntegrated(true);

            //persistLocalChange(clientId, "NP0", "", "payment", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);

//            return isPartialRegime(event) ? paidDebtAmount : Money.ZERO;
        }

        Money interestAndfineAmount =
                new Money(debtInterestCalculator.getPaidInterestAmount().add(debtInterestCalculator.getPaidInterestAmount()));
        if (interestAndfineAmount.isPositive()) {
            payments = payments.add(interestAndfineAmount);

            SapRequest sapInvoiceRequest = new SapRequest(event, clientId, interestAndfineAmount, "ND0",
                    SapRequestType.INVOICE_INTEREST, Money.ZERO, new JsonObject());
            sapInvoiceRequest.setWhenSent(FIRST_DAY);
            sapInvoiceRequest.setSent(true);
            sapInvoiceRequest.setIntegrated(true);
//            persistLocalChange(clientId, "ND0", "", "invoice", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);

            SapRequest sapPaymentRequest = new SapRequest(event, clientId, interestAndfineAmount, "NP0",
                    SapRequestType.PAYMENT_INTEREST, Money.ZERO, new JsonObject());
            sapPaymentRequest.setWhenSent(FIRST_DAY);
            sapPaymentRequest.setSent(true);
            sapPaymentRequest.setIntegrated(true);
        }
        return Money.ZERO;
    }

    private boolean isPartialRegime(Event event) {
        if (event instanceof GratuityEventWithPaymentPlan) {
            if (((GratuityEventWithPaymentPlan) event).getGratuityPaymentPlan().isForPartialRegime()) {
                return true;
            }
        }
        return false;
    }

    private void processExemptions(Event event, Money amountPayed, DebtInterestCalculator debtInterestCalculator) {

        Money amountToRegister = new Money(debtInterestCalculator.getDebtExemptionAmount()
                .add(debtInterestCalculator.getInterestExemptionAmount()).add(debtInterestCalculator.getFineExemptionAmount()));
        if (amountToRegister.isPositive()) {
            //TODO remove when fully tested, if we register a different value for the exemption
            //when the sync script runs it will detect a different amount for the exemptions and it will try to rectify

            //when the events are referring to partial regime
            if (amountPayed.isPositive() && isPartialRegime(event)) {
                Money originalAmount = event.getOriginalAmountToPay();
                if (amountToRegister.add(amountPayed).greaterThan(originalAmount)) {
                    taskLog("Evento: %s # Montante original: %s # montante pago: %s # montante a registar: %s\n",
                            event.getExternalId(), originalAmount, amountPayed, amountToRegister);
//                    amountToRegister = originalAmount.subtract(amountPayed);
                }
            }

            exemptions = exemptions.add(amountToRegister);

            String clientId = null;
            if (event.getParty().isPerson()) {
                clientId = ClientMap.uVATNumberFor(event.getPerson());
            } else {
                clientId = event.getParty().getExternalId();
                taskLog("Dívida de empresa!! - %s\n", clientId);
                return;
            }

            //an exemption should be considered as a payment for the initialization
            SapRequest sapInvoiceRequest = new SapRequest(event, clientId, amountToRegister, "ND0", SapRequestType.INVOICE,
                    Money.ZERO, new JsonObject());
            sapInvoiceRequest.setWhenSent(FIRST_DAY);
            sapInvoiceRequest.setSent(true);
            sapInvoiceRequest.setIntegrated(true);

            SapRequest sapCreditRequest =
                    new SapRequest(event, clientId, amountToRegister, "NA0", SapRequestType.CREDIT, Money.ZERO, new JsonObject());
            sapCreditRequest.setWhenSent(FIRST_DAY);
            sapCreditRequest.setSent(true);
            sapCreditRequest.setIntegrated(true);
//            persistLocalChange(clientId, "NA0", "", "credit", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
            if (event.isGratuity()) {
                SapRequest sapDebtRequest = new SapRequest(event, clientId, amountToRegister, "NG0", SapRequestType.DEBT,
                        Money.ZERO, new JsonObject());
                sapDebtRequest.setWhenSent(FIRST_DAY);
                sapDebtRequest.setSent(true);
                sapDebtRequest.setIntegrated(true);
//                persistLocalChange(clientId, "NG0", "", "debt", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);

                SapRequest sapDebtCreditRequest = new SapRequest(event, clientId, amountToRegister.negate(), "NJ0",
                        SapRequestType.DEBT, Money.ZERO, new JsonObject());
                sapDebtCreditRequest.setWhenSent(FIRST_DAY);
                sapDebtCreditRequest.setSent(true);
                sapDebtCreditRequest.setIntegrated(true);
//                persistLocalChange(clientId, "NJ0", "", "debt", amountToRegister.negate(), REGISTER_DATE, "", Money.ZERO, sapFile,
//                        jArray);
            }
        }
    }

    private void processReimbursements(Event event) {
        Money amountToRegister = event.getAccountingTransactionsSet().stream().flatMap(at -> at.getEntriesSet().stream())
                .flatMap(e -> e.getReceiptsSet().stream()).flatMap(r -> r.getCreditNotesSet().stream())
                .filter(cn -> !cn.isAnnulled()).flatMap(cn -> cn.getCreditNoteEntriesSet().stream()).map(cne -> cne.getAmount())
                .reduce(Money.ZERO, Money::add);
        if (amountToRegister.isPositive()) {
            taskLog("O evento: %s - tem nota de crédito associada, no valor de: %s\n", event.getExternalId(),
                    amountToRegister.toPlainString());

            String clientId = null;
            if (event.getParty().isPerson()) {
                clientId = ClientMap.uVATNumberFor(event.getPerson());
            } else {
                clientId = event.getParty().getExternalId();
                taskLog("Dívida de empresa!! - %s\n", clientId);
                return;
            }
            SapRequest sapRequest = new SapRequest(event, clientId, amountToRegister, "NR0", SapRequestType.REIMBURSEMENT,
                    Money.ZERO, new JsonObject());
            sapRequest.setWhenSent(FIRST_DAY);
            sapRequest.setSent(true);
            sapRequest.setIntegrated(true);
//            persistLocalChange(clientId, "NR0", "", "reimbursement", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile,
//                    jArray);
        }
    }
}