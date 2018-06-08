package pt.ist.fenixedu.domain;

import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.util.Money;
import org.joda.time.DateTime;

import com.google.gson.JsonObject;

public class SapRequest extends SapRequest_Base {
    
    public SapRequest() {
        super();
    }

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
