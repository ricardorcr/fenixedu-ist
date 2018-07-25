package pt.ist.fenixedu.sap.servlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.fenixedu.academic.domain.accounting.AccountingTransaction;
import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.domain.accounting.EventState;
import org.fenixedu.academic.domain.accounting.EventState.ChangeStateEvent;
import org.fenixedu.bennu.core.signals.DomainObjectEvent;
import org.fenixedu.bennu.core.signals.Signal;

import pt.ist.fenixedu.giaf.invoices.ui.SapInvoiceController;

@WebListener
public class FenixEduSapInvoiceContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        Signal.register(AccountingTransaction.SIGNAL_ANNUL, this::handlerAccountingTransactionAnnulment);
        Signal.register(EventState.EVENT_STATE_CHANGED, this::handlerEventStateChange);
    }
    
    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
    }

    private void handlerAccountingTransactionAnnulment(final DomainObjectEvent<AccountingTransaction> domainEvent) {
        final AccountingTransaction transaction = domainEvent.getInstance();
        final Event event = transaction.getEvent();
        syncEvent(event);
        throw new Error("This transaction must be first canceled / undone in SAP"); // TODO
    }

    private void handlerEventStateChange(final ChangeStateEvent eventStateChange) {
        final Event event = eventStateChange.getEvent();
        final EventState oldtState = event.getEventState();
        final EventState newState = eventStateChange.getNewState();
        if (oldtState == null) {
            // Then it is a new event and nothing needs to be done.
        } else if (oldtState == newState) {
            // Not really a state change... nothing to be done.
        } else if ((oldtState == EventState.CLOSED || oldtState == EventState.CANCELLED)
                && (newState == EventState.CLOSED || newState == EventState.CANCELLED)) {
            throw new Error("You should not cancel closed events or close cancelled events. Why do this? What is the point?");
        } else if ((oldtState == EventState.CLOSED || oldtState == EventState.CANCELLED)) {
            throw new Error("SAP does not allow reopening closed or cancelledd events. Consider creating a new event instead.");
        } else if (oldtState == EventState.OPEN) {
            syncEvent(event);
            throw new Error("Event state change must first be canceled in SAP"); // TODO
        } else {
            throw new Error("New event state that must be handled: " + oldtState.name());
        }
    }

    private void syncEvent(final Event event) {
        final String errors = SapInvoiceController.syncEvent(event);
        if (!errors.isEmpty()) {
            throw new Error("Unable to sync event: " + errors);
        }
    }

}
