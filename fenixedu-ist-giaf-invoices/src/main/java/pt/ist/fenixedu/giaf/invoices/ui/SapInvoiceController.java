/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST GIAF Invoices.
 *
 * FenixEdu IST GIAF Invoices is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST GIAF Invoices is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST GIAF Invoices.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.giaf.invoices.ui;

import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;

import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.domain.accounting.EventState;
import org.fenixedu.academic.domain.accounting.calculator.DebtInterestCalculator;
import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.spring.portal.SpringFunctionality;
import org.joda.time.DateTime;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import pt.ist.fenixedu.domain.SapRequest;

@SpringFunctionality(app = InvoiceController.class, title = "title.sap.invoice.viewer")
@RequestMapping("/sap-invoice-viewer")
public class SapInvoiceController {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @RequestMapping(method = RequestMethod.GET)
    public String home(@RequestParam(required = false) String username, final Model model) {
        final User user = InvoiceController.getUser(username);
        if (InvoiceController.isAllowedToAccess(user)) {
            final Person person = user.getPerson();
            final DateTime now = new DateTime();
            final JsonArray events = person.getEventsSet().stream()
                    .sorted(Event.COMPARATOR_BY_DATE)
                    .map(e -> toJsonObject(e, now))
                    .collect(toJsonArray());
            model.addAttribute("events", events);
        }
        return "sap-invoice-viewer/home";
    }

    private JsonObject toJsonObject(final Event event, final DateTime when) {
        final JsonObject result = new JsonObject();
        result.addProperty("eventId", event.getExternalId());
        result.addProperty("eventDescription", event.getDescription().toString());
        result.addProperty("isCanceled", event.isInState(EventState.CANCELLED));

        final DebtInterestCalculator calculator = event.getDebtInterestCalculator(when);

        result.addProperty("debtAmount", calculator.getDebtAmount());
        result.addProperty("debtExemptionAmount", calculator.getDebtExemptionAmount());

        result.addProperty("dueAmount", calculator.getDueAmount());
        result.addProperty("dueFineAmount", calculator.getDueFineAmount());
        result.addProperty("dueInterestAmount", calculator.getDueInterestAmount());

        result.addProperty("fineAmount", calculator.getFineAmount());
        result.addProperty("fineExemptionAmount", calculator.getFineExemptionAmount());

        result.addProperty("interestAmount", calculator.getInterestAmount());
        result.addProperty("interestExemptionAmount", calculator.getInterestExemptionAmount());

        result.addProperty("paidDebtAmount", calculator.getPaidDebtAmount());
        result.addProperty("paidFineAmount", calculator.getPaidFineAmount());
        result.addProperty("paidInterestAmount", calculator.getPaidInterestAmount());

        result.addProperty("totalAmount", calculator.getTotalAmount());
        result.addProperty("totalDueAmount", calculator.getTotalDueAmount());
        result.addProperty("totalPaidAmount", calculator.getTotalPaidAmount());

        final JsonArray sapRequests = event.getSapRequestSet().stream()
            .sorted(SapRequest.COMPARATOR_BY_DATE)
            .map(sr -> toJsonObject(sr))
            .collect(toJsonArray());
        result.add("sapRequests", sapRequests);

        return result;
    }

    private JsonObject toJsonObject(final SapRequest sapRequest) {
        final JsonObject result = new JsonObject();
        result.addProperty("id", sapRequest.getExternalId());
        result.addProperty("advancement", sapRequest.getAdvancement() == null ? null : sapRequest.getAdvancement().toPlainString());
        result.addProperty("answer", sapRequest.getAnswer());
        //result.addProperty("anulledRequest", sapRequest.getAnulledRequest());
        result.addProperty("clientId", sapRequest.getClientId());
        result.addProperty("documentNumber", sapRequest.getDocumentNumber());
        result.addProperty("integrated", sapRequest.getIntegrated());
        result.addProperty("integrationMessage", sapRequest.getIntegrationMessage());
        //result.addProperty("originalRequest", sapRequest.getOriginalRequest());
        //result.addProperty("payment", sapRequest.getPayment());
        result.addProperty("request", sapRequest.getRequest());
        result.addProperty("requestType", sapRequest.getRequestType() == null ? null : sapRequest.getRequestType().name());
        result.addProperty("sapDocumentNumber", sapRequest.getSapDocumentNumber());
        result.addProperty("sent", sapRequest.getSent());
        result.addProperty("value", sapRequest.getValue() == null ? null : sapRequest.getValue().toPlainString());
        result.addProperty("whenCreated", sapRequest.getWhenCreated() == null ? null : sapRequest.getWhenCreated().toString(DATE_TIME_FORMAT));
        result.addProperty("whenSent", sapRequest.getWhenSent() == null ? null : sapRequest.getWhenSent().toString(DATE_TIME_FORMAT));
        return result;
    }

    public static <T extends JsonElement> Collector<T, JsonArray, JsonArray> toJsonArray() {
        return Collector.of(JsonArray::new, (array, element) -> array.add(element), (one, other) -> {
            one.addAll(other);
            return one;
        } , Characteristics.IDENTITY_FINISH);
    }

}