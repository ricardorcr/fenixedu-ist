package pt.ist.fenixedu.integration.task;

import java.io.File;
import java.util.stream.Stream;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.accounting.AccountingTransaction;
import org.fenixedu.academic.domain.accounting.AccountingTransactionDetail;
import org.fenixedu.academic.domain.accounting.CreditNoteEntry;
import org.fenixedu.academic.domain.accounting.Entry;
import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.domain.accounting.EventType;
import org.fenixedu.academic.domain.accounting.Exemption;
import org.fenixedu.academic.domain.accounting.PostingRule;
import org.fenixedu.academic.domain.accounting.events.AcademicEventExemption;
import org.fenixedu.academic.domain.accounting.events.AdministrativeOfficeFeeAndInsuranceExemption;
import org.fenixedu.academic.domain.accounting.events.AdministrativeOfficeFeeAndInsurancePenaltyExemption;
import org.fenixedu.academic.domain.accounting.events.AdministrativeOfficeFeeExemption;
import org.fenixedu.academic.domain.accounting.events.AnnualEvent;
import org.fenixedu.academic.domain.accounting.events.ImprovementOfApprovedEnrolmentPenaltyExemption;
import org.fenixedu.academic.domain.accounting.events.InsuranceExemption;
import org.fenixedu.academic.domain.accounting.events.PenaltyExemption;
import org.fenixedu.academic.domain.accounting.events.candidacy.SecondCycleIndividualCandidacyExemption;
import org.fenixedu.academic.domain.accounting.events.gratuity.GratuityEventWithPaymentPlan;
import org.fenixedu.academic.domain.accounting.events.gratuity.PercentageGratuityExemption;
import org.fenixedu.academic.domain.accounting.events.gratuity.ValueGratuityExemption;
import org.fenixedu.academic.domain.accounting.events.gratuity.exemption.penalty.InstallmentPenaltyExemption;
import org.fenixedu.academic.domain.accounting.postingRules.AdministrativeOfficeFeeAndInsurancePR;
import org.fenixedu.academic.domain.accounting.postingRules.InsurancePR;
import org.fenixedu.academic.domain.phd.debts.PhdEventExemption;
import org.fenixedu.academic.domain.phd.debts.PhdGratuityEvent;
import org.fenixedu.academic.domain.phd.debts.PhdGratuityExternalScholarshipExemption;
import org.fenixedu.academic.domain.phd.debts.PhdGratuityFineExemption;
import org.fenixedu.academic.domain.phd.debts.PhdRegistrationFeePenaltyExemption;
import org.fenixedu.academic.util.Money;
import org.fenixedu.bennu.GiafInvoiceConfiguration;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.DateTime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import pt.ist.fenixedu.giaf.invoices.ClientMap;
import pt.ist.fenixedu.giaf.invoices.Utils;
import pt.ist.fenixframework.Atomic.TxMode;

public class InitializeSapData extends CustomTask {

    private static ExecutionYear startYear = null;
    private static ExecutionYear SAP_3RD_CYCLE_THRESHOLD = null;
    private static DateTime FIRST_DAY = new DateTime(2018, 01, 01, 00, 00);
    private static final String REGISTER_DATE = new DateTime().toString("yyyy-MM-dd HH:mm:ss");
    private static Money payments = Money.ZERO;
    private static Money exemptions = Money.ZERO;

    @Override
    public TxMode getTxMode() {
        return TxMode.READ;
    }

    private static boolean needsProcessing(final Event event) {
        try {
            final ExecutionYear executionYear = Utils.executionYearOf(event);
            return !event.isCancelled()
                    && (!executionYear.isBefore(startYear)
                            || event instanceof PhdGratuityEvent && !executionYear.isBefore(SAP_3RD_CYCLE_THRESHOLD))
                    && (!event.getAccountingTransactionsSet().isEmpty() || !event.getExemptionsSet().isEmpty());
        } catch (Exception e) {
            System.out.println("O evento " + event.getExternalId() + " deu o seguinte erro: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void runTask() throws Exception {
        startYear = ExecutionYear.readExecutionYearByName("2015/2016");
        SAP_3RD_CYCLE_THRESHOLD = ExecutionYear.readExecutionYearByName("2014/2015");
        Stream<Event> eventStream =
                Bennu.getInstance().getAccountingEventsSet().stream().filter(InitializeSapData::needsProcessing);

        eventStream.forEach(InitializeSapData::process);
        taskLog("O valor dos pagamentos foi de %s\n", payments.toPlainString());
        taskLog("O valor das insenções foi de %s\n", exemptions.toPlainString());
    }

    public static void process(Event event) {
        JsonArray array = new JsonArray();
        Money amountPayed = processPayments(event, array);
        processExemptions(event, array, amountPayed);
        processReimbursements(event, array);
    }

    private static Money processPayments(Event event, JsonArray jArray) {
        Money amountToRegister = event.getAccountingTransactionsSet().stream().filter(InitializeSapData::needsProcessing)
                .map(t -> t.getAmountWithAdjustment()).reduce(Money.ZERO, Money::add);
        if (amountToRegister.isPositive()) {
            payments = payments.add(amountToRegister);
            final File sapFile = sapEventFile(event);
            final File dir = sapFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String clientId = null;
            if (event.getParty().isPerson()) {
                clientId = ClientMap.uVATNumberFor(event.getPerson());
            } else {
                clientId = event.getParty().getExternalId();
                System.out.println("Dívida de empresa!! - " + clientId);
                return Money.ZERO;
            }

            persistLocalChange(clientId, "ND0", "", "invoice", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
            if (event.isGratuity()) {
                persistLocalChange(clientId, "NG0", "", "debt", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
            }
            persistLocalChange(clientId, "NP0", "", "payment", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);

            return isPartialRegime(event) ? amountToRegister : Money.ZERO;
        }
        return Money.ZERO;
    }

    private static boolean isPartialRegime(Event event) {
        if (event instanceof GratuityEventWithPaymentPlan) {
            if (((GratuityEventWithPaymentPlan) event).getGratuityPaymentPlan().isForPartialRegime()) {
                return true;
            }
        }
        return false;
    }

    private static void processExemptions(Event event, JsonArray jArray, Money amountPayed) {
        Money amountToRegister = discountsAmount(event).add(exemptionsAmount(event));
        if (amountToRegister.isPositive()) {
            //when the events are referring to partial regime
            if (amountPayed.isPositive() && event.getExemptionsSet().size() == 1
                    && event.getExemptionsSet().iterator().next() instanceof PercentageGratuityExemption) {
                Money originalAmount = Utils.calculateTotalDebtValue(event);
                if (amountToRegister.add(amountPayed).greaterThan(originalAmount)) {
                    amountToRegister = originalAmount.subtract(amountPayed);
                }
            }
            exemptions = exemptions.add(amountToRegister);
            final File sapFile = sapEventFile(event);
            final File dir = sapFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String clientId = null;
            if (event.getParty().isPerson()) {
                clientId = ClientMap.uVATNumberFor(event.getPerson());
            } else {
                clientId = event.getParty().getExternalId();
                System.out.println("Dívida de empresa!! - " + clientId);
                return;
            }
            persistLocalChange(clientId, "NA0", "", "credit", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
            if (event.isGratuity()) {
                persistLocalChange(clientId, "NG0", "", "debt", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
                persistLocalChange(clientId, "NJ0", "", "debt", amountToRegister.negate(), REGISTER_DATE, "", Money.ZERO, sapFile,
                        jArray);
            }
            persistLocalChange(clientId, "ND0", "", "invoice", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile, jArray);
        }
    }

    private static void processReimbursements(Event event, JsonArray jArray) {
        Money amountToRegister = event.getAccountingTransactionsSet().stream().flatMap(at -> at.getEntriesSet().stream())
                .flatMap(e -> e.getReceiptsSet().stream()).flatMap(r -> r.getCreditNotesSet().stream())
                .filter(cn -> !cn.isAnnulled()).flatMap(cn -> cn.getCreditNoteEntriesSet().stream()).map(cne -> cne.getAmount())
                .reduce(Money.ZERO, Money::add);
        if (amountToRegister.isPositive()) {
            System.out.println("O evento: " + event.getExternalId() + " - tem nota de crédito associada, no valor de: "
                    + amountToRegister.toPlainString());
            final File sapFile = sapEventFile(event);
            final File dir = sapFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String clientId = null;
            if (event.getParty().isPerson()) {
                clientId = ClientMap.uVATNumberFor(event.getPerson());
            } else {
                clientId = event.getParty().getExternalId();
                System.out.println("Dívida de empresa!! - " + clientId);
                return;
            }
            persistLocalChange(clientId, "NR0", "", "reimbursement", amountToRegister, REGISTER_DATE, "", Money.ZERO, sapFile,
                    jArray);
        }
    }

    private static boolean needsProcessing(AccountingTransaction transaction) {
        return transaction.getWhenRegistered().isBefore(FIRST_DAY) && !isCreditNote(transaction.getTransactionDetail());
    }

    private static boolean isCreditNote(AccountingTransactionDetail detail) {
        final Entry entry = detail.getTransaction().getToAccountEntry();
        final CreditNoteEntry creditNoteEntry = entry.getAdjustmentCreditNoteEntry();
        return creditNoteEntry != null || detail.getTransaction().getAdjustedTransaction() != null;
    }

    private static void persistLocalChange(final String clientId, final String documentNumber, final String sapDocumentNumber,
            final String type, final Money value, final String date, String paymentId, Money advancement, File file,
            JsonArray jArray) {
        final JsonObject json = new JsonObject();
        json.addProperty("clientId", clientId);
        json.addProperty("documentNumber", documentNumber);
        json.addProperty("sapDocumentNumber", sapDocumentNumber);
        json.addProperty("type", type);
        json.addProperty("value", value.toPlainString());
        json.addProperty("date", date);
        json.addProperty("paymentId", paymentId);
        json.addProperty("advancement", advancement.toPlainString());

        jArray.add(json);
        persistLocalChanges(jArray, file);
    }

    private static void persistLocalChanges(JsonArray array, File file) {
        Utils.writeFileWithoutFailuer(file.toPath(), array.toString().getBytes(), false);
    }

    private static File sapEventFile(final Event event) {
        final File dir = dirFor(event);
        return new File(dir, event.getExternalId() + ".json");
    }

    private static File dirFor(final Event event) {
        final String id = event.getExternalId();
        final String dirPath =
                GiafInvoiceConfiguration.getConfiguration().sapInvoiceDir() + Utils.splitPath(id) + File.separator + id;
        final File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static Money discountsAmount(final Event event) {
        return event.getDiscountsSet().stream().filter(d -> d.getWhenCreated().isBefore(FIRST_DAY)).map(d -> d.getAmount())
                .reduce(Money.ZERO, Money::add);
    }

    private static Money exemptionsAmount(final Event event) {
        return event.getExemptionsSet().stream().filter(e -> e.getWhenCreated().isBefore(FIRST_DAY)).map(e -> amountFor(e))
                .reduce(Money.ZERO, Money::add);
    }

    private static Money amountFor(final Exemption exemption) {
        Event event = exemption.getEvent();
        final Money amount = Utils.calculateTotalDebtValue(event);
        if (exemption instanceof AcademicEventExemption) {
            final AcademicEventExemption o = (AcademicEventExemption) exemption;
            return o.getValue();
        } else if (exemption instanceof AdministrativeOfficeFeeAndInsuranceExemption) {
            final AdministrativeOfficeFeeAndInsuranceExemption o = (AdministrativeOfficeFeeAndInsuranceExemption) exemption;
        } else if (exemption instanceof AdministrativeOfficeFeeExemption) {
            final AdministrativeOfficeFeeExemption o = (AdministrativeOfficeFeeExemption) exemption;
            final DateTime when = event.getWhenOccured().plusSeconds(1);
            final PostingRule postingRule = event.getPostingRule();
            final Money originalAmount = postingRule.calculateTotalAmountToPay(event, when, false);
            final Money amountToPay = postingRule.calculateTotalAmountToPay(event, when, true);
            return originalAmount.subtract(amountToPay);
        } else if (exemption instanceof InsuranceExemption) {
            PostingRule pr = event.getPostingRule();
            InsurancePR insurancePR = null;
            if (pr instanceof AdministrativeOfficeFeeAndInsurancePR) {
                AnnualEvent annualEvent = (AnnualEvent) event;
                AdministrativeOfficeFeeAndInsurancePR ofiPr = (AdministrativeOfficeFeeAndInsurancePR) pr;
                insurancePR = (InsurancePR) ofiPr.getServiceAgreementTemplateForInsurance().findPostingRuleBy(EventType.INSURANCE,
                        annualEvent.getStartDate(), annualEvent.getEndDate());
            } else {
                insurancePR = (InsurancePR) pr;
            }
            return insurancePR.getFixedAmount();
        } else if (exemption instanceof SecondCycleIndividualCandidacyExemption) {
            final SecondCycleIndividualCandidacyExemption o = (SecondCycleIndividualCandidacyExemption) exemption;
        } else if (exemption instanceof PercentageGratuityExemption) {
            final PercentageGratuityExemption o = (PercentageGratuityExemption) exemption;
            return amount.multiply(o.getPercentage());
        } else if (exemption instanceof ValueGratuityExemption) {
            final ValueGratuityExemption o = (ValueGratuityExemption) exemption;
            return o.getValue();
        } else if (exemption instanceof PhdGratuityExternalScholarshipExemption) {
            final PhdGratuityExternalScholarshipExemption o = (PhdGratuityExternalScholarshipExemption) exemption;
            return o.getValue();
        } else if (exemption instanceof PhdGratuityFineExemption) {
            final PhdGratuityFineExemption o = (PhdGratuityFineExemption) exemption;
            return o.getValue();
        } else if (exemption instanceof PhdEventExemption) {
            final PhdEventExemption o = (PhdEventExemption) exemption;
            return o.getValue();
        } else if (exemption instanceof AdministrativeOfficeFeeAndInsurancePenaltyExemption) {
            final AdministrativeOfficeFeeAndInsurancePenaltyExemption o =
                    (AdministrativeOfficeFeeAndInsurancePenaltyExemption) exemption;
            return Money.ZERO;
        } else if (exemption instanceof ImprovementOfApprovedEnrolmentPenaltyExemption) {
            final ImprovementOfApprovedEnrolmentPenaltyExemption o = (ImprovementOfApprovedEnrolmentPenaltyExemption) exemption;
            return Money.ZERO;
        } else if (exemption instanceof InstallmentPenaltyExemption) {
            final InstallmentPenaltyExemption o = (InstallmentPenaltyExemption) exemption;
            return Money.ZERO;
        } else if (exemption instanceof PhdRegistrationFeePenaltyExemption) {
            final PhdRegistrationFeePenaltyExemption o = (PhdRegistrationFeePenaltyExemption) exemption;
            return Money.ZERO;
        } else if (exemption instanceof PenaltyExemption) {
            final PenaltyExemption o = (PenaltyExemption) exemption;
            return Money.ZERO;
        }
        return amount;
    }

}
