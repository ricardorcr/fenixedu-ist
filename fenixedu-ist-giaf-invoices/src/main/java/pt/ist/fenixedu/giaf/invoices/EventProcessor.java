package pt.ist.fenixedu.giaf.invoices;

import java.math.BigDecimal;

import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.util.Money;

import pt.ist.fenixedu.giaf.invoices.GiafEvent.GiafEventEntry;

public class EventProcessor {

    public static void syncEventWithGiaf(final ClientMap clientMap, final ErrorLogConsumer consumer, final EventLogger elogger,
            final Event event) {
        final EventWrapper wrapper = new EventWrapper(event, consumer, false);
        process(clientMap, consumer, elogger, wrapper);
    }

    private static void process(final ClientMap clientMap, final ErrorLogConsumer consumer, final EventLogger elogger,
            final EventWrapper eventWrapper) {
        try {
            final GiafEvent giafEvent = new GiafEvent(eventWrapper.event);
            if (EventWrapper.needsProcessing(eventWrapper.event)) {

                final Money debtFenix = eventWrapper.debt;
                final Money debtGiaf = giafEvent.debt();

                if (debtFenix.isPositive() && !debtFenix.equals(debtGiaf)) {
                    for (final GiafEventEntry entry : giafEvent.entries) {
                        final Money amountStillInDebt = entry.amountStillInDebt();
                        if (amountStillInDebt.isPositive()) {
                            giafEvent.exempt(entry, eventWrapper.event, amountStillInDebt);
                        }
                    }

                    if (debtFenix.isPositive()) { // THIS TEST IS REDUNDANT !  WHY IS IT HERE ?
                        final String clientId = clientMap.getClientId(eventWrapper.event.getPerson());
                        final GiafEventEntry entry = giafEvent.newGiafEventEntry(eventWrapper.event, clientId, debtFenix);
                        final Money exempt = eventWrapper.exempt.add(giafEvent.payed());
                        if (exempt.isPositive()) {
                            giafEvent.exempt(entry, eventWrapper.event, exempt.greaterThan(debtFenix) ? debtFenix : exempt);
                        }
                    }
                }

                final GiafEventEntry openEntry = giafEvent.openEntry();
                if (openEntry != null) {
                    final Money giafActualExempt = giafEvent.entries.stream().filter(e -> e != openEntry).map(e -> e.payed)
                            .reduce(openEntry.exempt, Money::subtract);
                    if (eventWrapper.exempt.greaterThan(giafActualExempt)) {
                        final Money exemptDiff = eventWrapper.exempt.subtract(giafActualExempt);
                        if (exemptDiff.isPositive()) {
                            final Money amountStillInDebt = openEntry.amountStillInDebt();
                            if (exemptDiff.greaterThan(amountStillInDebt)) {
                                giafEvent.exempt(openEntry, eventWrapper.event, amountStillInDebt);
                            } else {
                                giafEvent.exempt(openEntry, eventWrapper.event, exemptDiff);
                            }
                        }
                    }
                }

                final Money totalPayed = eventWrapper.payed.add(eventWrapper.fines);
                eventWrapper.payments().filter(d -> !giafEvent.hasPayment(d)).peek(
                        d -> elogger.log("Processing payment %s : %s%n", eventWrapper.event.getExternalId(), d.getExternalId()))
                        .forEach(d -> giafEvent.pay(clientMap, d, totalPayed));
            }

            eventWrapper.payments().filter(d -> !giafEvent.hasPayment(d)).peek(
                    d -> elogger.log("Processing past payment %s : %s%n", eventWrapper.event.getExternalId(), d.getExternalId()))
                    .forEach(d -> giafEvent.payWithoutDebtRegistration(clientMap, d));
        } catch (final Error e) {
            final String m = e.getMessage();

            BigDecimal amount;
            DebtCycleType cycleType;

            try {
                amount = Utils.calculateTotalDebtValue(eventWrapper.event).getAmount();
                cycleType = Utils.cycleType(eventWrapper.event);
            } catch (Exception ex) {
                amount = null;
                cycleType = null;
            }

            consumer.accept(eventWrapper.event.getExternalId(), eventWrapper.event.getPerson().getUsername(),
                    eventWrapper.event.getPerson().getName(), amount == null ? "" : amount.toPlainString(),
                    cycleType == null ? "" : cycleType.getDescription(), m, "", "", "", "", "", "", "", "", "", "", "");
            elogger.log("%s: %s%n", eventWrapper.event.getExternalId(), m);
            if (m.indexOf("digo de Entidade ") > 0 && m.indexOf(" invlido/inexistente!") > 0) {
                return;
            } else if (m.indexOf("O valor da factura") >= 0 && m.indexOf("inferior") >= 0 && m.indexOf("nota de cr") >= 0
                    && m.indexOf("encontrar a factura") >= 0) {
                elogger.log("%s: %s%n", eventWrapper.event.getExternalId(), m);
                return;
            }
            elogger.log("Unhandled giaf error for event " + eventWrapper.event.getExternalId() + " : " + m);
            e.printStackTrace();
//        throw e;
        }

    }

    public static Money syncEventWithSap(final ClientMap clientMap, final ErrorLogConsumer consumer, final EventLogger elogger,
            final Event event) {
        final EventWrapper wrapper = new EventWrapper(event, consumer, true);
        return processSap(clientMap, consumer, elogger, wrapper);
    }

    private static Money processSap(final ClientMap clientMap, final ErrorLogConsumer errorLog, final EventLogger elogger,
            final EventWrapper eventWrapper) {
        try {
            final SapEvent sapEvent = new SapEvent(eventWrapper.event);
            if (EventWrapper.needsProcessingSap(eventWrapper.event)) {

                //System.out.println(eventWrapper.event.getExternalId());
                final Money debtFenix = eventWrapper.debt; //este valor é sem isenções certo?
                final Money invoiceSap = sapEvent.getInvoiceAmount();

                if (debtFenix.isPositive()) {
                    if (invoiceSap.isZero()) {
                        //System.out.println("divida sap igual a zero");
                        sapEvent.registerInvoice(clientMap, debtFenix, eventWrapper.event, eventWrapper.isGratuity(), false,
                                errorLog, elogger);
//                        return debtFenix;
                    } else if (invoiceSap.isNegative()) {
                        //TODO something is wronggggggg
                        // loggggg
                        System.out
                                .println("O evento " + eventWrapper.event.getExternalId() + " tem uma dívida negativa no SAP!!!");
                    } else if (!debtFenix.equals(invoiceSap)) {
                        if (debtFenix.greaterThan(invoiceSap)) {
                            //System.out.println("divida fenix maior que sap$$$");
                            sapEvent.registerInvoice(clientMap, debtFenix.subtract(invoiceSap), eventWrapper.event,
                                    eventWrapper.isGratuity(), true, errorLog, elogger);
//                            return debtFenix.subtract(invoiceSap);
                            // criar invoice com a diferença entre debtFenix e invoiceDebtSap (se for propina aumentar a dívida no sap)
                            //passar data actual (o valor do evento mudou, não dá para saber quando, vamos assumir que mudou qd foi detectada essa diferença)
                        } else {
//                             diminuir divida no sap e credit note da diferença na última factura existente
//                            se o valor pago nesta factura for superior à nova dívida, o que fazer? terá que existir nota crédito no fenix -> sim
                            //System.out.println("divida sap igual ou menor a zero");
                            sapEvent.registerCredit(clientMap, eventWrapper.event, invoiceSap.subtract(debtFenix),
                                    eventWrapper.isGratuity(), errorLog, elogger);
                            System.out.println("Ia registar abate à dívida?? " + eventWrapper.event.getExternalId()
                                    + " Valor dívida no Fénix: " + debtFenix + " - Valor dívida SAP: " + invoiceSap);
                        }
                    }
                }

                // there could have been an error comunicating a debt, we can not comunicate payments and such, since there is nothing registered in SAP
                if (sapEvent.getInvoiceAmount().isPositive()) {
                    //System.out.println("pagamentos");
                    //Payments!!
                    eventWrapper.paymentsSap().filter(transactionDetail -> !sapEvent.hasPayment(transactionDetail))
                            .forEach(transactionDetail -> {
                                try {
                                    sapEvent.registerPayment(clientMap, transactionDetail, errorLog, elogger);
                                } catch (Exception e) {
                                    logError(errorLog, elogger, eventWrapper, e);
                                    return;
                                }
                            });

                    //Exemptions!!
                    //System.out.println("está a validar isenções");
                    if (eventWrapper.exempt.greaterThan(sapEvent.getCreditAmount())) {
                        sapEvent.registerCredit(clientMap, eventWrapper.event,
                                eventWrapper.exempt.subtract(sapEvent.getCreditAmount()), eventWrapper.isGratuity(), errorLog,
                                elogger);
                    } else if (eventWrapper.exempt.lessThan(sapEvent.getCreditAmount())) {

                        //(valor da divida no fenix - isenção fenix) tem de ser igual ao (valor das facturas sap - valor dos creditos)
                        Money finalFenixDebt = eventWrapper.debt.subtract(eventWrapper.exempt);
                        Money finalSapDebt = sapEvent.getInvoiceAmount().subtract(sapEvent.getCreditAmount());

                        // se o valor da diferença for positivo que dizer que é preciso lançar dívida, caso contrário está tudo certo
                        Money invoiceValue = finalFenixDebt.subtract(finalSapDebt);
                        if (invoiceValue.isPositive()) {
                            sapEvent.registerInvoice(clientMap, invoiceValue, eventWrapper.event, eventWrapper.isGratuity(), true,
                                    errorLog, elogger);
                        }
                    }

                    //System.out.println("Reimbursements");
                    //TODO confirmar a estrutura das notas de credito no fenix
                    Money sapReimbursements = sapEvent.getReimbursementsAmount();
                    if (eventWrapper.reimbursements.greaterThan(sapReimbursements)) {
                        sapEvent.registerReimbursement(clientMap, eventWrapper.event,
                                eventWrapper.reimbursements.subtract(sapReimbursements), errorLog, elogger);
                    }

                    final Money totalPayed = eventWrapper.payed.add(eventWrapper.fines); //TODO isto é o que??
                    //TODO multas só podem ser comunicadas depois de a divida no fenix estar fechada e houver um pagamento
                }
            } else {
                //processing payments of past events
//                eventWrapper.paymentsSap().filter(d -> !sapEvent.hasPayment(d)).peek(
//                    d -> elogger.log("Processing past payment %s : %s%n", eventWrapper.event.getExternalId(), d.getExternalId()))
//                    .forEach(d -> sapEvent.registerInvoiceAndPayment(clientMap, d, errorLog, elogger));
            }
        } catch (final Exception e) {
            logError(errorLog, elogger, eventWrapper, e);
//        throw e;
        }
        return Money.ZERO;
    }

    private static void logError(final ErrorLogConsumer errorLog, final EventLogger elogger, final EventWrapper eventWrapper,
            final Exception e) {
        final String errorMessage = e.getMessage();

        BigDecimal amount;
        DebtCycleType cycleType;

        try {
            amount = Utils.calculateTotalDebtValue(eventWrapper.event).getAmount();
            cycleType = Utils.cycleType(eventWrapper.event);
        } catch (Exception ex) {
            amount = null;
            cycleType = null;
        }

        errorLog.accept(eventWrapper.event.getExternalId(), eventWrapper.event.getPerson().getUsername(),
                eventWrapper.event.getPerson().getName(), amount == null ? "" : amount.toPlainString(),
                cycleType == null ? "" : cycleType.getDescription(), errorMessage, "", "", "", "", "", "", "", "", "", "", "");
        elogger.log("%s: %s%n", eventWrapper.event.getExternalId(), errorMessage);
//            if (errorMessage.indexOf("digo de Entidade ") > 0 && errorMessage.indexOf(" invlido/inexistente!") > 0) {
//                return;
//            } else if (errorMessage.indexOf("O valor da factura") >= 0 && errorMessage.indexOf("inferior") >= 0 && errorMessage.indexOf("nota de cr") >= 0
//                    && errorMessage.indexOf("encontrar a factura") >= 0) {
//                elogger.log("%s: %s%n", eventWrapper.event.getExternalId(), errorMessage);
//                return;
//            }
        elogger.log("Unhandled SAP error for event " + eventWrapper.event.getExternalId() + " : " + e.getClass().getName() + " - "
                + errorMessage);
        e.printStackTrace();
    }
}
