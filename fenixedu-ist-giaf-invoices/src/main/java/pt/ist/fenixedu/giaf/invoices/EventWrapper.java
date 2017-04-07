package pt.ist.fenixedu.giaf.invoices;

import java.time.Year;
import java.util.stream.Stream;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.accounting.AccountingTransaction;
import org.fenixedu.academic.domain.accounting.AccountingTransactionDetail;
import org.fenixedu.academic.domain.accounting.CreditNoteEntry;
import org.fenixedu.academic.domain.accounting.CreditNoteState;
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
import org.joda.time.DateTime;

import pt.ist.fenixframework.FenixFramework;

public class EventWrapper {

    public final static DateTime THRESHOLD = new DateTime(2015, 12, 1, 0, 0, 0, 0);
    public final static ExecutionYear SAP_THRESHOLD = ExecutionYear.readExecutionYearByName("2015/2016");
    public final static ExecutionYear SAP_3RD_CYCLE_THRESHOLD = ExecutionYear.readExecutionYearByName("2014/2015");
    public final static DateTime SAP_TRANSACTIONS_THRESHOLD = new DateTime(2017, 12, 31, 0, 0, 0, 0);

    public final static DateTime LIMIT = new DateTime(2017, 12, 31, 23, 59, 59, 999);

    public static Stream<Event> eventsToProcess(final ErrorLogConsumer consumer, final Stream<Event> eventStream,
            final Stream<AccountingTransactionDetail> txStream) {
        final Stream<Event> currentEvents = eventStream.filter(EventWrapper::needsProcessing)
                .filter(e -> Utils.validate(consumer, e)).filter(e -> e.getWhenOccured().isBefore(LIMIT));

        final int currentYear = 2017;
        final Stream<Event> pastEvents = txStream.filter(d -> d.getWhenRegistered().getYear() == currentYear)
                .map(d -> d.getEvent()).filter(e -> !needsProcessing(e)).filter(e -> Utils.validate(consumer, e));

        return Stream.concat(currentEvents, pastEvents).distinct().filter(e -> okToProcessPayments(e));
    }

    private static boolean okToProcessPayments(final Event e) {
        for (final AccountingTransaction tx : e.getAccountingTransactionsSet()) {
            if (tx.getWhenRegistered().isAfter(LIMIT)) {
                return false;
            }
        }
        return true;
    }

    public static boolean needsProcessing(final Event event) {
        final ExecutionYear executionYear = Utils.executionYearOf(event);
        final int year = executionYear.getBeginLocalDate().getYear();
        return year >= THRESHOLD.getYear() || event.getWhenOccured().isAfter(THRESHOLD);
    }

    public static Stream<Event> eventsToProcessSap(final ErrorLogConsumer consumer, final Stream<Event> eventStream,
            final Stream<AccountingTransactionDetail> txStream) {
        final Stream<Event> currentEvents =
                Stream.of(FenixFramework.getDomainObject("1688875630069920"));
        /*, FenixFramework.getDomainObject("1974082933358646"),
        FenixFramework.getDomainObject("1973339904016761"), FenixFramework.getDomainObject("1973339904016733"));*/
        //eventStream.filter(EventWrapper::needsProcessingSap).filter(e -> Utils.validate(consumer, e));

        final int currentYear = Year.now().getValue();
        final Stream<Event> pastEvents = txStream.filter(d -> d.getWhenRegistered().getYear() == currentYear)
                .map(d -> d.getEvent()).filter(e -> !needsProcessingSap(e)).filter(e -> Utils.validate(consumer, e));

        return Stream.concat(currentEvents, pastEvents).distinct();
    }

    public static boolean needsProcessingSap(final Event event) {
        final ExecutionYear executionYear = Utils.executionYearOf(event);
        return !executionYear.isBefore(SAP_THRESHOLD)
                || (event instanceof PhdGratuityEvent && !executionYear.isBefore(SAP_3RD_CYCLE_THRESHOLD));
    }

    public final Event event;
    public final Money debt;
    public final Money exempt;
    public final Money payed;
    public final Money fines;
    public final Money reimbursements;

    public EventWrapper(final Event event, final pt.ist.fenixedu.giaf.invoices.ErrorLogConsumer errorLogConsumer, boolean sap) {
        this.event = event;

        final Money payedTotal = calculateAmountPayed(null, errorLogConsumer);
        //only events >= SAP_THRESHOLD are analyzed and there are no payments referring those events made before
        final Money payedBeforThreshold = sap ? Money.ZERO : calculateAmountPayed(THRESHOLD, errorLogConsumer);
        final Money payedAfterThreshhold = payedTotal.subtract(payedBeforThreshold);

        // calculate debt
        {
            final Money value = event.isCancelled() ? Money.ZERO : Utils.calculateTotalDebtValue(event);
            final Money diff = value.subtract(payedBeforThreshold);
            debt = diff.isPositive() ? diff : Money.ZERO;
        }

        // calculate exemptions and discounts
        {
            final Money discounts = discounts();
            final Money excemptions = exemptionsValue();
            exempt = discounts.add(excemptions);
        }

        // calculate payed amount
        payed = debt.lessThan(payedAfterThreshhold) ? debt : payedAfterThreshhold;

        // calculate fines
        {
            final Money exessPayment = payedAfterThreshhold.subtract(debt);
            fines = exessPayment.isPositive() ? exessPayment : Money.ZERO;
        }

        // calculate reimbursements
        {
            reimbursements = event.getAccountingTransactionsSet().stream().flatMap(at -> at.getEntriesSet().stream())
                    .flatMap(e -> e.getReceiptsSet().stream()).flatMap(r -> r.getCreditNotesSet().stream())
                    .filter(cn -> CreditNoteState.PAYED.equals(cn.getState())) //TODO rever este filtro
                    .flatMap(c -> c.getCreditNoteEntriesSet().stream()).map(cne -> cne.getAmount())
                    .reduce(Money.ZERO, Money::add);

//            reimbursements = event.getAccountingTransactionsSet().stream().map(at -> at.getToAccountEntry())
//                    .map(e -> e.getAdjustmentCreditNoteEntry()).filter(Objects::nonNull).map(cne -> cne.getAmount())
//                    .reduce(Money.ZERO, Money::add);
        }
    }

    public Money amountStillInDebt() {
        return debt.subtract(exempt).subtract(payed);
    }

    private Money calculateAmountPayed(final DateTime threshold,
            final pt.ist.fenixedu.giaf.invoices.ErrorLogConsumer errorLogConsumer) {
        return event.getAccountingTransactionsSet().stream()
                .filter(t -> threshold == null || t.getWhenRegistered().isBefore(threshold))
                .filter(t -> Utils.validate(errorLogConsumer, t.getTransactionDetail())).map(t -> t.getAmountWithAdjustment())
                .reduce(Money.ZERO, Money::add);
    }

    private Money discounts() {
        return event.getDiscountsSet().stream().map(d -> d.getAmount()).reduce(Money.ZERO, Money::add);
    }

    private Money exemptionsValue() {
        return event.getExemptionsSet().stream().map(e -> amountFor(e)).reduce(Money.ZERO, Money::add);
    }

    private Money amountFor(final Exemption e) {
        final Money amount = Utils.calculateTotalDebtValue(event);
        if (e instanceof AcademicEventExemption) {
            final AcademicEventExemption o = (AcademicEventExemption) e;
            return o.getValue();
        } else if (e instanceof AdministrativeOfficeFeeAndInsuranceExemption) {
            final AdministrativeOfficeFeeAndInsuranceExemption o = (AdministrativeOfficeFeeAndInsuranceExemption) e;
        } else if (e instanceof AdministrativeOfficeFeeExemption) {
            final AdministrativeOfficeFeeExemption o = (AdministrativeOfficeFeeExemption) e;
            final DateTime when = event.getWhenOccured().plusSeconds(1);
            final PostingRule postingRule = event.getPostingRule();
            final Money originalAmount = postingRule.calculateTotalAmountToPay(event, when, false);
            final Money amountToPay = postingRule.calculateTotalAmountToPay(event, when, true);
            return originalAmount.subtract(amountToPay);
        } else if (e instanceof InsuranceExemption) {
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
        } else if (e instanceof SecondCycleIndividualCandidacyExemption) {
            final SecondCycleIndividualCandidacyExemption o = (SecondCycleIndividualCandidacyExemption) e;
        } else if (e instanceof PercentageGratuityExemption) {
            final PercentageGratuityExemption o = (PercentageGratuityExemption) e;
            return amount.multiply(o.getPercentage());
        } else if (e instanceof ValueGratuityExemption) {
            final ValueGratuityExemption o = (ValueGratuityExemption) e;
            return o.getValue();
        } else if (e instanceof PhdGratuityExternalScholarshipExemption) {
            final PhdGratuityExternalScholarshipExemption o = (PhdGratuityExternalScholarshipExemption) e;
            return o.getValue();
        } else if (e instanceof PhdGratuityFineExemption) {
            final PhdGratuityFineExemption o = (PhdGratuityFineExemption) e;
            return o.getValue();
        } else if (e instanceof PhdEventExemption) {
            final PhdEventExemption o = (PhdEventExemption) e;
            return o.getValue();
        } else if (e instanceof AdministrativeOfficeFeeAndInsurancePenaltyExemption) {
            final AdministrativeOfficeFeeAndInsurancePenaltyExemption o = (AdministrativeOfficeFeeAndInsurancePenaltyExemption) e;
            return Money.ZERO;
        } else if (e instanceof ImprovementOfApprovedEnrolmentPenaltyExemption) {
            final ImprovementOfApprovedEnrolmentPenaltyExemption o = (ImprovementOfApprovedEnrolmentPenaltyExemption) e;
            return Money.ZERO;
        } else if (e instanceof InstallmentPenaltyExemption) {
            final InstallmentPenaltyExemption o = (InstallmentPenaltyExemption) e;
            return Money.ZERO;
        } else if (e instanceof PhdRegistrationFeePenaltyExemption) {
            final PhdRegistrationFeePenaltyExemption o = (PhdRegistrationFeePenaltyExemption) e;
            return Money.ZERO;
        } else if (e instanceof PenaltyExemption) {
            final PenaltyExemption o = (PenaltyExemption) e;
            return Money.ZERO;
        }
        return amount;
    }

    public Stream<AccountingTransactionDetail> payments() {
        final Stream<AccountingTransactionDetail> stream =
                event.getAccountingTransactionsSet().stream().map(at -> at.getTransactionDetail());
        return stream
                //.filter(d -> d.getWhenRegistered().getYear() >= START_YEAR_TO_CONSIDER_TRANSACTIONS)
                .filter(d -> d.getWhenRegistered().isAfter(THRESHOLD)).filter(d -> !isCreditNote(d))
                .filter(d -> Utils.validate(null, d));
    }

    public Stream<AccountingTransactionDetail> paymentsSap() {
        final Stream<AccountingTransactionDetail> stream =
                event.getAccountingTransactionsSet().stream().map(at -> at.getTransactionDetail());
        return stream.filter(d -> d.getWhenRegistered().isAfter(SAP_TRANSACTIONS_THRESHOLD)).filter(d -> !isCreditNote(d))
                .filter(d -> Utils.validate(null, d));
    }

    private boolean isCreditNote(AccountingTransactionDetail detail) {
        final Entry entry = detail.getTransaction().getToAccountEntry();
        final CreditNoteEntry creditNoteEntry = entry.getAdjustmentCreditNoteEntry();
        return creditNoteEntry != null;
    }

    public boolean isGratuity() {
        return this.event.isGratuity();
    }
}
