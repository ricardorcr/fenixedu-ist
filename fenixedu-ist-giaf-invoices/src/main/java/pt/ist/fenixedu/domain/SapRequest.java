package pt.ist.fenixedu.domain;

import java.util.Comparator;

import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.util.Money;
import org.joda.time.DateTime;

import com.google.gson.JsonObject;

public class SapRequest extends SapRequest_Base {
    
    public static final Comparator<SapRequest> COMPARATOR_BY_DATE = new Comparator<SapRequest>() {
        @Override
        public int compare(SapRequest r1, SapRequest r2) {
            final int i = r1.getWhenCreated().compareTo(r2.getWhenCreated());
            return i == 0 ? r1.getExternalId().compareTo(r2.getExternalId()) : i;
        }
    };

    public SapRequest(Event event, String clientId, Money amount, String documentNumber, SapRequestType requestType,
            Money advancement,
            JsonObject request) {
        setEvent(event);
        setClientId(clientId);
        setValue(amount);
        setDocumentNumber(documentNumber);
        setRequestType(requestType);
        setAdvancement(advancement);
        setRequest(request.toString());
        setSent(false);
        setWhenCreated(new DateTime());
    }
    
}
