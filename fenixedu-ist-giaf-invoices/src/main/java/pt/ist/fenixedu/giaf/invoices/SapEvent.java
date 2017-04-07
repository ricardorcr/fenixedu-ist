package pt.ist.fenixedu.giaf.invoices;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fenixedu.academic.domain.Degree;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.StudentCurricularPlan;
import org.fenixedu.academic.domain.accounting.AccountingTransactionDetail;
import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.academic.domain.accounting.PaymentMode;
import org.fenixedu.academic.domain.accounting.accountingTransactions.detail.SibsTransactionDetail;
import org.fenixedu.academic.domain.accounting.events.AdministrativeOfficeFeeAndInsuranceEvent;
import org.fenixedu.academic.domain.accounting.events.ImprovementOfApprovedEnrolmentEvent;
import org.fenixedu.academic.domain.accounting.events.SpecialSeasonEnrolmentEvent;
import org.fenixedu.academic.domain.accounting.events.dfa.DFACandidacyEvent;
import org.fenixedu.academic.domain.accounting.events.gratuity.GratuityEvent;
import org.fenixedu.academic.domain.accounting.events.insurance.InsuranceEvent;
import org.fenixedu.academic.domain.phd.debts.PhdGratuityEvent;
import org.fenixedu.academic.util.Money;
import org.fenixedu.bennu.GiafInvoiceConfiguration;
import org.fenixedu.generated.sources.saft.sap.SAFTPTPaymentType;
import org.fenixedu.generated.sources.saft.sap.SAFTPTSettlementType;
import org.fenixedu.generated.sources.saft.sap.SAFTPTSourceBilling;
import org.fenixedu.generated.sources.saft.sap.SAFTPTSourcePayment;
import org.joda.time.DateTime;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import pt.ist.fenixedu.domain.SapRoot;
import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.Atomic.TxMode;
import pt.ist.sap.client.SapFinantialClient;

public class SapEvent {

    private static final String DT_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String MORADA_DESCONHECIDO = "Desconhecido";
    private static final String EMPTY_JSON = "{}";

    private final Comparator<SapEventEntry> DOCUMENT_NUMBER_COMPARATOR = new Comparator<SapEventEntry>() {
        @Override
        public int compare(final SapEventEntry e1, final SapEventEntry e2) {
            final Integer i1 = e1.documentNumber != null ? Integer.valueOf(e1.documentNumber.substring(2)) : Integer.valueOf(-1);
            final Integer i2 = e2.documentNumber != null ? Integer.valueOf(e2.documentNumber.substring(2)) : Integer.valueOf(-1);;
            return i1.compareTo(i2);
        }
    };

    public class SapEventEntry {
        public final String clientId;
        public final String documentNumber;
        public final String sapDocumentNumber;
        public final String paymentId;
        public Money debt = Money.ZERO;
        public Money invoice = Money.ZERO;
        public Money credit = Money.ZERO;
        public Money reimbursement = Money.ZERO;
        public Money advancement = Money.ZERO;
        public Money payed = Money.ZERO;
        public Money fines = Money.ZERO;

        private SapEventEntry(final String clientId, final String documentNumber, final String sapDocumentNumber,
                final String paymentId) {
            this.clientId = clientId;
            this.documentNumber = documentNumber;
            this.sapDocumentNumber = sapDocumentNumber;
            this.paymentId = paymentId;
        }

        public Money amountStillInDebt() {
            return debt.subtract(credit).subtract(payed).subtract(fines);
        }
    }

    private final File file;
    private final JsonArray array;
    public final Set<SapEventEntry> entries = new HashSet<>();

    public SapEvent(final Event event) {
        file = sapEventFile(event);
        array = readEventFile(event);

        for (final JsonElement je : array) {
            final JsonObject jObject = je.getAsJsonObject();
            final String clientId = jObject.get("clientId").getAsString();

            final String documentNumber = jObject.get("documentNumber").getAsString();
            final String sapDocumentNumber = jObject.get("sapDocumentNumber").getAsString();
            final String paymentId = jObject.get("paymentId").getAsString();

            final String type = jObject.get("type").getAsString();
            final Money value = new Money(jObject.get("value").getAsString());
            final Money advancement = new Money(jObject.get("advancement").getAsString());

            final SapEventEntry entry = new SapEventEntry(clientId, documentNumber, sapDocumentNumber, paymentId);
            entries.add(entry);

            if (type.equals("invoice")) {
                entry.invoice = value;
            } else if (type.equals("debt")) {
                entry.debt = value;
            } else if (type.equals("credit")) {
                entry.credit = value;
            } else if (type.equals("reimbursement")) {
                entry.reimbursement = value;
            } else if (type.equals("advancement")) {
                entry.payed = value;
                entry.advancement = advancement;
            } else if (type.equals("payment")) {
                entry.payed = value;
            } else if (type.equals("fine")) { //TODO
                entry.fines = value;
            }
        }
    }

    public void registerInvoice(ClientMap clientMap, Money debtFenix, Event event, boolean isGratuity, boolean isNewDate,
            ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {
        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (isGratuity) {
            //if debt is greater than invoice, then there was a debt registered and the correspondent invoice failed, don't register the debt again
            if (!getDebtAmount().greaterThan(getInvoiceAmount())) {
                boolean result = registerDebt(clientMap, debtFenix, event, isNewDate, errorLog, elogger);
                if (!result) { //if the debt register didn't went ok we wont register the invoice
                    return;
                }
            }
        }

        String clientId = clientMap.getClientId(event.getPerson());

        JsonObject result = null;
        JsonObject data = getPendingRequest(event, "ND");
        if (data != null) {
            result = sendDataToSap(dir, data, "ND", false);
        } else {
            data = toJsonInvoice(event, debtFenix, new DateTime(), clientId, false, isNewDate);
            result = sendDataToSap(dir, data, "ND", true);
        }
        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, event, errorLog, elogger, "invoice");
            checkClientStatus(result, event, errorLog, elogger, "invoice", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "ND");
                JsonObject docResult = SapFinantialClient.getDocument(documentNumbers.getValue(),
                        data.get("taxRegistrationNumber").getAsString());
                if (docResult.get("status").getAsString().equalsIgnoreCase("S")) {
                    final File documentFile = new File(dir, sanitize(documentNumbers.getValue()) + ".pdf");
                    Utils.writeFileWithoutFailuer(documentFile.toPath(),
                            Base64.getDecoder().decode(docResult.get("documentBase64").getAsString()), false);
                }

                SapEventEntry sapEventEntry = newSapEventEntry(clientId, debtFenix, documentNumbers.getKey(),
                        documentNumbers.getValue(), "invoice", "", Money.ZERO);
                sapEventEntry.invoice = debtFenix;

                // if there are amounts in advancement we need to register them in the new invoice
                Money advancementAmount = getAdvancementAmount();
                if (advancementAmount.isPositive()) {
                    registerPaymentFromAdvancement(event, clientId, advancementAmount, sapEventEntry, errorLog, elogger);
                }
            }
        } else {
            logError(event, errorLog, elogger, result.get("exception").getAsString(),
                    result.get("workingDocument").getAsJsonObject().get("workingDocumentNumber").getAsString(), "invoice");
        }
    }

    private JsonObject getPendingRequest(Event event, String docType) {
        final File dir = dirFor(event);
        final File file = new File(dir, "pendingRequests" + docType + ".json");
        if (file.exists()) {
            try {
                String fileContent = new String(Files.readAllBytes(file.toPath()));
                return fileContent.isEmpty() ? null : new JsonParser().parse(fileContent).getAsJsonObject();
            } catch (JsonSyntaxException | IOException e) {
                throw new Error(e);
            }
        }
        return null;
    }

    private void registerPaymentFromAdvancement(Event event, String clientId, Money advancementAmount, SapEventEntry invoiceEntry,
            ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {

        checkValidDocumentNumber(invoiceEntry.documentNumber, event);

        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // ver se o valor da divida é superior ou igual ao advancement, se for tudo ok, caso contrário é registar o pagamento no valor da factura
        // e abater esse valor ao advancement
        Money amountToRegister = advancementAmount;
        if (advancementAmount.greaterThan(invoiceEntry.invoice)) {
            amountToRegister = invoiceEntry.invoice;
        }

        JsonObject result = null;
        JsonObject data = getPendingRequest(event, "NP");
        if (data != null) {
            result = sendDataToSap(dir, data, "NP", false);
        } else {
            data = toJsonPaymentFromAdvancement(event, invoiceEntry.documentNumber, clientId, amountToRegister);
            result = sendDataToSap(dir, data, "NP", true);
        }

        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, event, errorLog, elogger, "paymentFromAdvancement");
            checkClientStatus(result, event, errorLog, elogger, "paymentFromAdvancement", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NP");
                JsonObject docResult = SapFinantialClient.getDocument(documentNumbers.getValue(),
                        data.get("taxRegistrationNumber").getAsString());
                if (docResult.get("status").getAsString().equalsIgnoreCase("S")) {
                    final File documentFile = new File(dir, sanitize(documentNumbers.getValue()) + ".pdf");
                    Utils.writeFileWithoutFailuer(documentFile.toPath(),
                            Base64.getDecoder().decode(docResult.get("documentBase64").getAsString()), false);
                }

                SapEventEntry sapEventEntry = newSapEventEntry(clientId, amountToRegister, documentNumbers.getKey(),
                        documentNumbers.getValue(), "payment", "", amountToRegister.negate());
                sapEventEntry.payed = amountToRegister;
                sapEventEntry.advancement = amountToRegister.negate();
            }
        } else {
            logError(event, errorLog, elogger, result.get("exception").getAsString(),
                    result.get("paymentDocument").getAsJsonObject().get("paymentDocumentNumber").getAsString(),
                    "paymentFromAdvancement");
        }

    }

    private JsonObject toJsonPaymentFromAdvancement(Event event, String invoiceNumber, String clientId, Money amount)
            throws Exception {
        JsonObject data = toJson(event, clientId, new DateTime(), false, false);
        JsonObject paymentDocument = toJsonPaymentDocument(amount, "NP", invoiceNumber, new DateTime(), "OU", "",
                SAFTPTSettlementType.NN.toString(), true);
        paymentDocument.addProperty("excessPayment", amount.negate().toPlainString());//the payment amount must be zero

        data.add("paymentDocument", paymentDocument);
        return data;
    }

    private boolean registerDebt(ClientMap clientMap, Money debtFenix, Event event, boolean isNewDate, ErrorLogConsumer errorLog,
            EventLogger elogger) throws Exception {
        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String clientId = clientMap.getClientId(event.getPerson());

        JsonObject result = null;
        JsonObject data = getPendingRequest(event, "NG");
        if (data != null) {
            result = sendDataToSap(dir, data, "NG", false);
        } else {
            data = toJsonDebt(event, debtFenix, clientId, new DateTime(), true, "NG", true, isNewDate);
            result = sendDataToSap(dir, data, "NG", true);
        }

        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, event, errorLog, elogger, "debt");
            checkClientStatus(result, event, errorLog, elogger, "debt", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NG");
                SapEventEntry sapEventEntry = newSapEventEntry(clientId, debtFenix, documentNumbers.getKey(),
                        documentNumbers.getValue(), "debt", "", Money.ZERO);
                sapEventEntry.debt = debtFenix;
                return true;
            } else {
                return false;
            }
        } else {
            logError(event, errorLog, elogger, result.get("exception").getAsString(),
                    result.get("workingDocument").getAsJsonObject().get("workingDocumentNumber").getAsString(), "debt");
            return false;
        }
    }

    private boolean registerDebtCredit(ClientMap clientMap, Money creditAmount, Event event, boolean isNewDate,
            ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {
        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String clientId = clientMap.getClientId(event.getPerson());
        SimpleImmutableEntry<List<SapEventEntry>, Money> openDebtsAndRemainingValue = getOpenDebtsAndRemainingValue();
        List<SapEventEntry> openDebts = openDebtsAndRemainingValue.getKey();
        Money remainingAmount = openDebtsAndRemainingValue.getValue();
        if (creditAmount.greaterThan(remainingAmount)) {
            if (openDebts.size() > 1) {
                // dividir o valor da isenção pelas várias dívidas
                return registerDebtCreditList(event, openDebts, creditAmount, remainingAmount, clientId, isNewDate, dir, errorLog,
                        elogger);
            } else {
                // o valor da isenção é superior ao valor em dívida
                String debtNumber = "";
                if (openDebts.size() == 1) { // mas só existe uma dívida abertura
                    debtNumber = openDebts.get(0).documentNumber;
                } else { // não existe nenhuma dívida aberta, ir buscar a última
                    debtNumber = getLastDebtNumber();
                }
                return registerDebtCredit(clientId, event, creditAmount, dir, debtNumber, isNewDate, errorLog, elogger);
            }
        } else {
            //tudo normal
            return registerDebtCredit(clientId, event, creditAmount, dir, openDebts.get(0).documentNumber, isNewDate, errorLog,
                    elogger);
        }
    }

    private boolean registerDebtCreditList(Event event, List<SapEventEntry> openDebts, Money amountToRegister,
            Money remainingAmount, String clientId, boolean isNewDate, File dir, ErrorLogConsumer errorLog, EventLogger elogger)
            throws Exception {
        if (amountToRegister.greaterThan(remainingAmount)) {
            if (openDebts.size() > 1) {
                boolean result = registerDebtCredit(clientId, event, remainingAmount, dir, openDebts.get(0).documentNumber,
                        isNewDate, errorLog, elogger);
                if (!result) { //if the first debt register didn't went ok, we abort the next ones
                    return result;
                }
                return registerDebtCreditList(event, openDebts.subList(1, openDebts.size()),
                        amountToRegister.subtract(remainingAmount), openDebts.get(1).debt, clientId, isNewDate, dir, errorLog,
                        elogger);
            } else {
                return registerDebtCredit(clientId, event, amountToRegister, dir, openDebts.get(0).documentNumber, isNewDate,
                        errorLog, elogger);
            }
        } else {
            return registerDebtCredit(clientId, event, amountToRegister, dir, openDebts.get(0).documentNumber, isNewDate,
                    errorLog, elogger);
        }
    }

    private boolean registerDebtCredit(String clientId, Event event, Money creditAmount, File dir, String debtCreditDocNumber,
            boolean isNewDate, ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {
        checkValidDocumentNumber(debtCreditDocNumber, event);

        JsonObject result = null;
        JsonObject data = getPendingRequest(event, "NJ");
        if (data != null) {
            result = sendDataToSap(dir, data, "NJ", false);
        } else {
            data = toJsonDebtCredit(event, creditAmount, clientId, new DateTime(), true, "NJ", false, isNewDate,
                    debtCreditDocNumber);
            result = sendDataToSap(dir, data, "NJ", true);
        }

        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, event, errorLog, elogger, "debtCredit");
            checkClientStatus(result, event, errorLog, elogger, "debtCredit", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NJ");
                SapEventEntry sapEventEntry = newSapEventEntry(clientId, creditAmount.negate(), documentNumbers.getKey(),
                        documentNumbers.getValue(), "debt", "", Money.ZERO);
                sapEventEntry.debt = creditAmount.negate();
                return true;
            } else {
                return false;
            }
        } else {
            logError(event, errorLog, elogger, result.get("exception").getAsString(),
                    result.get("workingDocument").getAsJsonObject().get("workingDocumentNumber").getAsString(), "debtCredit");
            return false;
        }
    }

    /**
     * Sends the data to SAP and keeps a log of the xml request that was sent as well of the json data in case
     * it is necessary to resend it. If every things go well the json data is deleted
     * 
     * @param dir - the directory where the log is going to be written
     * @param data - the necessary data to invoke the service for the specified operation
     * @param docType - what kind of document is being sent
     * @param logJsonRequest - if it is a resend this should be false, otherwise we should log the json data
     * @return The result of the SAP service invocation, with the status of the documents and clients and also the xml request
     *         sent. In case of an unexpected exception returns the exception message
     */
    private JsonObject sendDataToSap(final File dir, JsonObject data, String docType, boolean logJsonRequest) {
        JsonObject result = null;
        final File requestsFile = new File(dir, "pendingRequests" + docType + ".json");
        try {
            if (logJsonRequest) {
                Utils.writeFileWithoutFailuer(requestsFile.toPath(), (data.toString() + "\n").getBytes(), true);
            }
            result = SapFinantialClient.comunicate(data);
        } catch (Exception e) {
            e.printStackTrace();
            result = new JsonObject();
            result.addProperty("exception", e.getMessage());
            return result;
        }

        final File logFile = new File(dir, "log.json");
        Utils.writeFileWithoutFailuer(logFile.toPath(), (result.get("xmlRequest").toString() + "\n").getBytes(), true);
        //Utils.writeFileWithoutFailuer(requestsFile.toPath(), "".getBytes(), false);

        return result;
    }

    private SimpleImmutableEntry<String, String> getSapDocumentNumber(JsonObject result, String documentType) {
        JsonArray jsonArray = result.getAsJsonArray("documents");
        for (int iter = 0; iter < jsonArray.size(); iter++) {
            JsonObject json = jsonArray.get(iter).getAsJsonObject();
            if (json.get("documentNumber").getAsString().startsWith(documentType)
                    && "S".equals(json.get("status").getAsString())) {
                return new SimpleImmutableEntry<String, String>(json.get("documentNumber").getAsString(),
                        json.get("sapDocumentNumber").getAsString());
            }
        }
        return null;
    }

    private void registerCreditList(Event event, List<SapEventEntry> openInvoices, Money amountToRegister, Money remainingAmount,
            String clientId, File dir, ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {
        if (amountToRegister.greaterThan(remainingAmount)) {
            if (openInvoices.size() > 1) {
                boolean result = registerCredit(clientId, event, remainingAmount, dir, openInvoices.get(0).documentNumber,
                        errorLog, elogger);
                if (!result) { //if the first credit register didn't went ok, we abort the next ones
                    return;
                }
                registerCreditList(event, openInvoices.subList(1, openInvoices.size()),
                        amountToRegister.subtract(remainingAmount), openInvoices.get(1).invoice, clientId, dir, errorLog,
                        elogger);
            } else {
                registerCredit(clientId, event, amountToRegister, dir, openInvoices.get(0).documentNumber, errorLog, elogger);
            }
        } else {
            registerCredit(clientId, event, amountToRegister, dir, openInvoices.get(0).documentNumber, errorLog, elogger);
        }
    }

    public void registerCredit(ClientMap clientMap, Event event, Money creditAmount, boolean isGratuity,
            ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {
        // diminuir divida no sap (se for propina diminuir dívida) e credit note na última factura existente
        // se o valor pago nesta factura for superior à nova dívida, o que fazer? terá que existir nota crédito no fenix -> sim

        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (isGratuity) {
            //if the debt credit amount is greater than the credit amount it means that a credit debt was registered but the correspondent invoice credit failed
            //we don't register the credit debt again
            if (!getDebtCreditAmount().greaterThan(getCreditAmount())) {
                boolean result = registerDebtCredit(clientMap, creditAmount, event, true, errorLog, elogger);
                if (!result) { //if the debt credit didn't went ok, we abort the credit register
                    return;
                }
            }
        }

        String clientId = clientMap.getClientId(event.getPerson());

        SimpleImmutableEntry<List<SapEventEntry>, Money> openInvoicesAndRemainingValue = getOpenInvoicesAndRemainingValue();
        List<SapEventEntry> openInvoices = openInvoicesAndRemainingValue.getKey();
        Money remainingAmount = openInvoicesAndRemainingValue.getValue();
        if (creditAmount.greaterThan(remainingAmount)) {
            if (openInvoices.size() > 1) {
                // dividir o valor da isenção pelas várias facturas....
                registerCreditList(event, openInvoices, creditAmount, remainingAmount, clientId, dir, errorLog, elogger);
            } else {
                // o valor da isenção é superior ao valor em dívida
                String invoiceNumber = "";
                if (openInvoices.size() == 1) { // mas só existe uma factura abertura
                    invoiceNumber = openInvoices.get(0).documentNumber;
                } else { // não existe nenhuma factura aberta, ir buscar a última
                    invoiceNumber = getLastInvoiceNumber();
                }
                registerCredit(clientId, event, creditAmount, dir, invoiceNumber, errorLog, elogger);
            }
        } else {
            //tudo normal
            registerCredit(clientId, event, creditAmount, dir, openInvoices.get(0).documentNumber, errorLog, elogger);
        }
    }

    private void checkValidDocumentNumber(String documentNumber, Event event) throws Exception {
        if ("0".equals(documentNumber.charAt(2))) {
            throw new Exception("Houve uma tentativa de efectuar uma operação sobre o documento: " + documentNumber
                    + " - evento: " + event.getExternalId());
        }
    }

    public void registerReimbursement(ClientMap clientMap, Event event, Money amount, ErrorLogConsumer errorLog,
            EventLogger elogger) throws Exception {
        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String invoiceNumber = getLastInvoiceNumber();
        checkValidDocumentNumber(invoiceNumber, event);
        String clientId = clientMap.getClientId(event.getPerson());

        JsonObject result = null;
        JsonObject data = getPendingRequest(event, "NR");
        if (data != null) {
            result = sendDataToSap(dir, data, "NR", false);
        } else {
            data = toJsonReimbursement(event, amount, clientId, invoiceNumber, false, true);
            result = sendDataToSap(dir, data, "NR", true);
        }

        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, event, errorLog, elogger, "reimbursement");
            checkClientStatus(result, event, errorLog, elogger, "reimbursement", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NR");
                JsonObject docResult = SapFinantialClient.getDocument(documentNumbers.getValue(),
                        data.get("taxRegistrationNumber").getAsString());
                if (docResult.get("status").getAsString().equalsIgnoreCase("S")) {
                    final File documentFile = new File(dir, sanitize(documentNumbers.getValue()) + ".pdf");
                    Utils.writeFileWithoutFailuer(documentFile.toPath(),
                            Base64.getDecoder().decode(docResult.get("documentBase64").getAsString()), false);
                }

                SapEventEntry sapEventEntry = newSapEventEntry(clientId, amount, documentNumbers.getKey(),
                        documentNumbers.getValue(), "reimbursement", "", Money.ZERO);
                sapEventEntry.reimbursement = amount;
            }
        } else {
            logError(event, errorLog, elogger, result.get("exception").getAsString(),
                    result.get("paymentDocument").getAsJsonObject().get("paymentDocumentNumber").getAsString(), "reimbursement");
        }
    }

    private boolean registerCredit(String clientId, Event event, Money creditAmount, final File dir, String documentNumber,
            ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {
        checkValidDocumentNumber(documentNumber, event);

        JsonObject result = null;
        JsonObject data = getPendingRequest(event, "NA");
        if (data != null) {
            result = sendDataToSap(dir, data, "NA", false);
        } else {
            data = toJsonCredit(event, creditAmount, clientId, documentNumber, false, true);
            result = sendDataToSap(dir, data, "NA", true);
        }

        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, event, errorLog, elogger, "credit");
            checkClientStatus(result, event, errorLog, elogger, "credit", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NA");
                JsonObject docResult = SapFinantialClient.getDocument(documentNumbers.getValue(),
                        data.get("taxRegistrationNumber").getAsString());
                if (docResult.get("status").getAsString().equalsIgnoreCase("S")) {
                    final File documentFile = new File(dir, sanitize(documentNumbers.getValue()) + ".pdf");
                    Utils.writeFileWithoutFailuer(documentFile.toPath(),
                            Base64.getDecoder().decode(docResult.get("documentBase64").getAsString()), false);
                }

                SapEventEntry sapEventEntry = newSapEventEntry(clientId, creditAmount, documentNumbers.getKey(),
                        documentNumbers.getValue(), "credit", "", Money.ZERO);
                sapEventEntry.credit = creditAmount;
                return true;
            } else {
                return false;
            }
        } else {
            logError(event, errorLog, elogger, result.get("exception").getAsString(),
                    result.get("paymentDocument").getAsJsonObject().get("paymentDocumentNumber").getAsString(), "credit");
            return false;
        }
    }

    public void registerPayment(ClientMap clientMap, AccountingTransactionDetail transactionDetail, ErrorLogConsumer errorLog,
            EventLogger elogger) throws Exception {

        final File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String clientId = clientMap.getClientId(transactionDetail.getEvent().getPerson());

        // ir buscar a ultima factura aberta e verificar se o pagamento ultrapassa o valor da factura
        // e associar o restante à(s) factura(s) seguinte(s)
        SimpleImmutableEntry<List<SapEventEntry>, Money> openInvoicesAndRemainingAmount = getOpenInvoicesAndRemainingValue();

        Money payedAmount = transactionDetail.getTransaction().getAmountWithAdjustment();
        Money firstRemainingAmount = openInvoicesAndRemainingAmount.getValue();
        List<SapEventEntry> openInvoices = openInvoicesAndRemainingAmount.getKey();

        if (firstRemainingAmount.isZero()) {
            // não há facturas abertas, fazer adiantamento, sobre a última factura!!
            registerAdvancement(Money.ZERO, payedAmount, getLastInvoiceNumber(), clientId, transactionDetail, dir, errorLog,
                    elogger);
            return;
        }

        if (firstRemainingAmount.lessThan(payedAmount)) {
            // quer dizer que ou há outra factura aberta ou é um pagamento em excesso
            // dividir o valor pago pela facturas e registar n pagamentos ou registar um pagamento adiamento

            if (openInvoices.size() == 1) {
                // só há uma factura aberta -> fazer adiantamento
                String invoiceNumber = openInvoices.get(0).documentNumber;
                registerAdvancement(firstRemainingAmount, payedAmount.subtract(firstRemainingAmount), invoiceNumber, clientId,
                        transactionDetail, dir, errorLog, elogger);
            } else {
                // vai distribuir o pagamento pelas restantes facturas abertas
                registerPaymentList(openInvoices, payedAmount, firstRemainingAmount, clientId, transactionDetail, dir, errorLog,
                        elogger);
            }
        } else {
            // tudo ok, é só registar o pagamento
            registerPayment(transactionDetail, dir, payedAmount, openInvoices.get(0), clientId, errorLog, elogger);
        }
    }

    private void registerPaymentList(List<SapEventEntry> openInvoices, Money amountToRegister, Money remainingAmount,
            String clientId, AccountingTransactionDetail transactionDetail, File dir, ErrorLogConsumer errorLog,
            EventLogger elogger) throws Exception {
        if (amountToRegister.greaterThan(remainingAmount)) {
            if (openInvoices.size() > 1) {
                boolean successful = registerPayment(transactionDetail, dir, remainingAmount, openInvoices.get(0), clientId,
                        errorLog, elogger);
                if (successful) {
                    registerPaymentList(openInvoices.subList(1, openInvoices.size()), amountToRegister.subtract(remainingAmount),
                            openInvoices.get(1).invoice, clientId, transactionDetail, dir, errorLog, elogger);
                } else {
                    return;
                }
            } else if (openInvoices.size() == 1) {
                // registar adiantamento
                String invoiceNumber = openInvoices.get(0).documentNumber;
                registerAdvancement(remainingAmount, amountToRegister.subtract(remainingAmount), invoiceNumber, clientId,
                        transactionDetail, dir, errorLog, elogger);
            }
        } else {
            registerPayment(transactionDetail, dir, amountToRegister, openInvoices.get(0), clientId, errorLog, elogger);
        }
    }

    private boolean registerPayment(AccountingTransactionDetail transactionDetail, final File dir, Money payedAmount,
            SapEventEntry sapEntry, String clientId, ErrorLogConsumer errorLog, EventLogger elogger) throws Exception {

        checkValidDocumentNumber(sapEntry.documentNumber, transactionDetail.getEvent());

        JsonObject result = null;
        JsonObject data = getPendingRequest(transactionDetail.getEvent(), "NP");
        if (data != null) {
            result = sendDataToSap(dir, data, "NP", false);
        } else {
            data = toJsonPayment(transactionDetail, sapEntry, clientId);
            result = sendDataToSap(dir, data, "NP", true);
        }
        boolean successful = true;

        if (result.get("exception") == null) {
            boolean docIsIntregrated = checkDocumentsStatus(result, transactionDetail.getEvent(), errorLog, elogger, "payment");
            checkClientStatus(result, transactionDetail.getEvent(), errorLog, elogger, "payment", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NP");
                JsonObject docResult = SapFinantialClient.getDocument(documentNumbers.getValue(),
                        data.get("taxRegistrationNumber").getAsString());
                if (docResult.get("status").getAsString().equalsIgnoreCase("S")) {
                    final File documentFile = new File(dir, sanitize(documentNumbers.getValue()) + ".pdf");
                    Utils.writeFileWithoutFailuer(documentFile.toPath(),
                            Base64.getDecoder().decode(docResult.get("documentBase64").getAsString()), false);
                }

                SapEventEntry sapEventEntry = newSapEventEntry(clientId, payedAmount, documentNumbers.getKey(),
                        documentNumbers.getValue(), "payment", transactionDetail.getExternalId(), Money.ZERO);
                sapEventEntry.payed = payedAmount;
            } else {
                if (hasPayment(transactionDetail)) {
                    //TODO já existe um pagamento feito com este id o que quer dizer que isto é um pagamento que se dividiu por n facturas e um deles deu erro
                    // este pagamento não vai voltar a ser processado e é preciso dar uma mensagem de erro diferente para isto ser corrigido manualmente
                    logError(transactionDetail.getEvent(), errorLog, elogger, "WARNING: one of multiple payments has failed!!!",
                            result.get("paymentDocument").getAsJsonObject().get("paymentDocumentNumber").getAsString(),
                            "payment");
                }
                successful = false;
            }
        } else {
            logError(transactionDetail.getEvent(), errorLog, elogger, result.get("exception").getAsString(),
                    result.get("paymentDocument").getAsJsonObject().get("paymentDocumentNumber").getAsString(), "payment");
            successful = false;
        }
        return successful;
    }

    private void registerAdvancement(Money amount, Money advancement, String invoiceNumber, String clientId,
            AccountingTransactionDetail transactionDetail, File dir, ErrorLogConsumer errorLog, EventLogger elogger)
            throws Exception {
        checkValidDocumentNumber(invoiceNumber, transactionDetail.getEvent());

        JsonObject result = null;
        JsonObject data = getPendingRequest(transactionDetail.getEvent(), "NP");
        if (data != null) {
            result = sendDataToSap(dir, data, "NP", false);
        } else {
            data = toJsonAdvancement(amount, advancement, invoiceNumber, clientId, transactionDetail);
            result = sendDataToSap(dir, data, "NP", true);
        }

        if (result.get("exception") == null) {
            boolean docIsIntregrated =
                    checkDocumentsStatus(result, transactionDetail.getEvent(), errorLog, elogger, "advancement");
            checkClientStatus(result, transactionDetail.getEvent(), errorLog, elogger, "advancement", data);

            if (docIsIntregrated) {
                SimpleImmutableEntry<String, String> documentNumbers = getSapDocumentNumber(result, "NP");
                JsonObject docResult = SapFinantialClient.getDocument(documentNumbers.getValue(),
                        data.get("taxRegistrationNumber").getAsString());
                if (docResult.get("status").getAsString().equalsIgnoreCase("S")) {
                    final File documentFile = new File(dir, sanitize(documentNumbers.getValue()) + ".pdf");
                    Utils.writeFileWithoutFailuer(documentFile.toPath(),
                            Base64.getDecoder().decode(docResult.get("documentBase64").getAsString()), false);
                }

                SapEventEntry sapEventEntry = newSapEventEntry(clientId, amount, documentNumbers.getKey(),
                        documentNumbers.getValue(), "advancement", transactionDetail.getExternalId(), advancement);
                sapEventEntry.payed = amount;
                sapEventEntry.advancement = advancement;
            }
        } else {
            logError(transactionDetail.getEvent(), errorLog, elogger, result.get("exception").getAsString(),
                    result.get("paymentDocument").getAsJsonObject().get("paymentDocumentNumber").getAsString(), "advancement");
        }
    }

    private JsonObject toJsonPayment(AccountingTransactionDetail transactionDetail, SapEventEntry sapEntry, String clientId)
            throws Exception {
        JsonObject data = toJson(transactionDetail.getEvent(), clientId, transactionDetail.getWhenRegistered(), false, false);
        JsonObject paymentDocument = toJsonPaymentDocument(transactionDetail.getTransaction().getAmountWithAdjustment(), "NP",
                sapEntry.documentNumber, new DateTime(), getPaymentMechanism(transactionDetail),
                getPaymentMethodReference(transactionDetail), SAFTPTSettlementType.NL.toString(), true);

        data.add("paymentDocument", paymentDocument);
        return data;
    }

    private JsonObject toJsonAdvancement(Money amount, Money excess, String invoiceNumber, String clientId,
            AccountingTransactionDetail transactionDetail) throws Exception {
        JsonObject data = toJson(transactionDetail.getEvent(), clientId, transactionDetail.getWhenRegistered(), false, false);
        JsonObject paymentDocument =
                toJsonPaymentDocument(amount, "NP", invoiceNumber, new DateTime(), getPaymentMechanism(transactionDetail),
                        getPaymentMethodReference(transactionDetail), SAFTPTSettlementType.NL.toString(), true);
        paymentDocument.addProperty("excessPayment", excess.toPlainString());
        paymentDocument.addProperty("isAdvancedPayment", true);

        JsonObject workingDocument = toJsonWorkDocument(transactionDetail.getWhenRegistered(), excess, "NA", false, false,
                transactionDetail.getWhenRegistered());
        workingDocument.addProperty("isAdvancedPayment", true);
        workingDocument.addProperty("paymentDocumentNumber", paymentDocument.get("paymentDocumentNumber").getAsString());

        paymentDocument.addProperty("originatingOnDocumentNumber", workingDocument.get("workingDocumentNumber").getAsString());

        data.add("workingDocument", workingDocument);
        data.add("paymentDocument", paymentDocument);
        return data;
    }

    private JsonObject toJsonCredit(Event event, Money creditAmount, String clientId, String invoiceNumber,
            boolean isDebtRegistration, boolean isNewDate) throws Exception {
        JsonObject json = toJson(event, clientId, new DateTime(), isDebtRegistration, isNewDate);
        JsonObject workDocument = toJsonWorkDocument(null, creditAmount, "NA", false, isNewDate, new DateTime());
        workDocument.addProperty("workOriginDocNumber", invoiceNumber);
        json.add("workingDocument", workDocument);

        String workingDocumentNumber = workDocument.get("workingDocumentNumber").getAsString();
        JsonObject paymentDocument = toJsonPaymentDocument(creditAmount, "NP", workingDocumentNumber, new DateTime(), "OU", "",
                SAFTPTSettlementType.NN.toString(), false);
        paymentDocument.addProperty("isCreditNote", true);
        paymentDocument.addProperty("paymentOriginDocNumber", invoiceNumber);
        paymentDocument.addProperty("excessPayment", creditAmount.negate().toPlainString());//the payment amount must be zero
        json.add("paymentDocument", paymentDocument);

        return json;
    }

    private JsonObject toJsonReimbursement(Event event, Money amount, String clientId, String invoiceNumber,
            boolean isDebtRegistration, boolean isNewDate) throws Exception {
        JsonObject json = toJson(event, clientId, new DateTime(), isDebtRegistration, isNewDate);
        JsonObject workDocument = toJsonWorkDocument(null, amount, "NA", false, isNewDate, new DateTime());
        workDocument.addProperty("workOriginDocNumber", invoiceNumber);
        json.add("workingDocument", workDocument);

        String workingDocumentNumber = workDocument.get("workingDocumentNumber").getAsString();
        JsonObject paymentDocument = toJsonPaymentDocument(amount, "NR", workingDocumentNumber, new DateTime(), "OU", "",
                SAFTPTSettlementType.NR.toString(), false);
        paymentDocument.addProperty("isReimbursment", true);
        paymentDocument.addProperty("reimbursementStatus", "PENDING");
        paymentDocument.addProperty("excessPayment", amount.negate().toPlainString());//the payment amount must be zero
        json.add("paymentDocument", paymentDocument);

        return json;
    }

    private JsonObject toJsonPaymentDocument(Money amount, String documentType, String workingDocumentNumber,
            DateTime paymentDate, String paymentMechanism, String paymentMethodReference, String settlementType, boolean isDebit)
            throws Exception {
        JsonObject paymentDocument = new JsonObject();
        paymentDocument.addProperty("paymentDocumentNumber", documentType + getDocumentNumber());
        paymentDocument.addProperty("paymentDate", paymentDate.toString(DT_FORMAT));
        paymentDocument.addProperty("paymentType", SAFTPTPaymentType.RG.toString());
        paymentDocument.addProperty("paymentStatus", "N");
        paymentDocument.addProperty("sourcePayment", SAFTPTSourcePayment.P.toString());
        paymentDocument.addProperty("paymentAmount", amount.getAmountAsString());
        paymentDocument.addProperty("paymentMechanism", paymentMechanism);
        paymentDocument.addProperty("paymentMethodReference", paymentMethodReference);
        paymentDocument.addProperty("settlementType", settlementType);

        paymentDocument.addProperty("isToDebit", isDebit);
        paymentDocument.addProperty("workingDocumentNumber", workingDocumentNumber);

        paymentDocument.addProperty("paymentGrossTotal", BigDecimal.ZERO);
        paymentDocument.addProperty("paymentNetTotal", BigDecimal.ZERO);
        paymentDocument.addProperty("paymentTaxPayable", BigDecimal.ZERO);
        return paymentDocument;
    }

    private Long getDocumentNumber() throws Exception {
        final List<Long> docNumber = new ArrayList<>();

        Thread thread = new Thread() {
            @Override
            @Atomic(mode = TxMode.WRITE)
            public void run() {
                docNumber.add(SapRoot.getInstance().getAndSetNextDocumentNumber());
            }
        };

        thread.start();
        thread.join();

        return docNumber.get(0);
    }

    private JsonObject toJsonInvoice(Event event, Money debtFenix, DateTime documentDate, String clientId,
            boolean isDebtRegistration, boolean isNewDate) throws Exception {
        JsonObject json = toJson(event, clientId, documentDate, isDebtRegistration, isNewDate);
        JsonObject workDocument =
                toJsonWorkDocument(documentDate, debtFenix, "ND", true, isNewDate, new DateTime(Utils.getDueDate(event)));

        json.add("workingDocument", workDocument);
        return json;
    }

    private JsonObject toJsonDebt(Event event, Money debtFenix, String clientId, DateTime documentDate,
            boolean isDebtRegistration, String docType, boolean isToDebit, boolean isNewDate) throws Exception {
        JsonObject json = toJson(event, clientId, documentDate, isDebtRegistration, isNewDate);
        JsonObject workDocument =
                toJsonWorkDocument(documentDate, debtFenix, docType, isToDebit, isNewDate, new DateTime(Utils.getDueDate(event)));

        ExecutionYear executionYear = Utils.executionYearOf(event);
        String metadata = String.format("{\"ANO_LECTIVO\":\"%s\", \"CURSO\":\"%s\", \"START_DATE\":\"%s\", \"END_DATE\":\"%s\"}",
                executionYear.getName(), Utils.getDegreeAcronym(event),
                isNewDate ? new DateTime().toString("yyyy-MM-dd") : event.getWhenOccured().toString("yyyy-MM-dd"),
                executionYear.getEndDateYearMonthDay().toString("yyyy-MM-dd"));
        workDocument.addProperty("debtMetadata", metadata);

        json.add("workingDocument", workDocument);
        return json;
    }

    private JsonObject toJsonDebtCredit(Event event, Money debtFenix, String clientId, DateTime documentDate,
            boolean isDebtRegistration, String docType, boolean isToDebit, boolean isNewDate, String debtDocNumber)
            throws Exception {
        JsonObject json = toJsonDebt(event, debtFenix, clientId, documentDate, isDebtRegistration, docType, isToDebit, isNewDate);
        JsonObject workingDocument = json.get("workingDocument").getAsJsonObject();
        workingDocument.addProperty("workOriginDocNumber", debtDocNumber);
        return json;
    }

    private JsonObject toJsonWorkDocument(DateTime eventDate, Money amount, String documentType, boolean isToDebit,
            boolean isNewDate, DateTime dueDate) throws Exception {
        JsonObject workDocument = new JsonObject();
        String documentDate = isNewDate ? new DateTime().toString(DT_FORMAT) : eventDate.toString(DT_FORMAT);
        workDocument.addProperty("documentDate", documentDate);
        workDocument.addProperty("dueDate", dueDate.toString(DT_FORMAT));
        workDocument.addProperty("workingDocumentNumber", documentType + getDocumentNumber());
        workDocument.addProperty("sourceBilling", SAFTPTSourceBilling.P.toString());
        workDocument.addProperty("workingAmount", amount.getAmountAsString());
        workDocument.addProperty("taxPayable", BigDecimal.ZERO);
        workDocument.addProperty("workType", "DC");
        workDocument.addProperty("workStatus", "N");

        workDocument.addProperty("isToDebit", isToDebit);
        workDocument.addProperty("isToCredit", !isToDebit);

//        workDocument.addProperty("compromiseMetadata", "");

        workDocument.addProperty("taxExemptionReason", "M99");
        workDocument.addProperty("unitOfMeasure", "UNID");

        return workDocument;
    }

    public JsonObject toJson(final Event event, final String clientId, DateTime documentDate, boolean isDebtRegistration,
            boolean isNewDate) {
        final JsonObject json = new JsonObject();

        json.addProperty("finantialInstitution", "IST");
        json.addProperty("taxType", "IVA");
        json.addProperty("taxCode", "ISE");
        json.addProperty("taxCountry", "PT");
        json.addProperty("taxPercentage", "0");
        json.addProperty("auditFileVersion", "1.0.3");
        json.addProperty("processId", "006");
        json.addProperty("businessName", "Técnico Lisboa");
        json.addProperty("companyName", "Instituto Superior Técnico");
        json.addProperty("companyId", "256241256");
        json.addProperty("currencyCode", "EUR");
        json.addProperty("country", "PT");
        json.addProperty("addressDetail", "Avenida Rovisco Pais, 1");
        json.addProperty("city", "Lisboa");
        json.addProperty("postalCode", "1049-001");
        json.addProperty("region", "Lisboa");
        json.addProperty("street", "Avenida Rovisco Pais, 1");
        json.addProperty("fromDate", isNewDate ? new DateTime().toString(DT_FORMAT) : documentDate.toString(DT_FORMAT));
        json.addProperty("toDate", new DateTime().toString(DT_FORMAT)); //tem impacto no ano fiscal!!!
        json.addProperty("productCompanyTaxId", "999999999");
        json.addProperty("productId", "FenixEdu/FenixEdu");
        json.addProperty("productVersion", "5.0.0.0");
        json.addProperty("softwareCertificateNumber", 0);
        json.addProperty("taxAccountingBasis", "P");
        json.addProperty("taxEntity", "Global");
        json.addProperty("taxRegistrationNumber", "501507930");
        SimpleImmutableEntry<String, String> product = mapToProduct(event, event.getDescription().toString(), isDebtRegistration);
        json.addProperty("productDescription", product.getValue());
        json.addProperty("productCode", product.getKey());

        final JsonObject clientData = toJsonClient(event.getPerson(), clientId);
        json.add("clientData", clientData);

        return json;
    }

    private JsonObject toJsonClient(final Person person, final String clientId) {
        final JsonObject clientData = new JsonObject();
        clientData.addProperty("accountId", "STUDENT");
        clientData.addProperty("companyName", person.getName());
        clientData.addProperty("clientId", clientId);
        clientData.addProperty("country",
                person.getCountryOfResidence() != null ? person.getCountryOfResidence().getCode() : "PT");
        clientData.addProperty("street", !Strings.isNullOrEmpty(person.getAddress()) ? person.getAddress() : MORADA_DESCONHECIDO);
        clientData.addProperty("city", !Strings.isNullOrEmpty(person.getDistrictSubdivisionOfResidence()) ? person
                .getDistrictSubdivisionOfResidence() : "Lisboa" /*MORADA_DESCONHECIDO*/);
        clientData.addProperty("postalCode", !Strings.isNullOrEmpty(person.getAreaCode()) ? person.getAreaCode() : "0000-000");
        clientData.addProperty("region", !Strings.isNullOrEmpty(person.getDistrictOfResidence()) ? person
                .getDistrictOfResidence() : "Lisboa" /*MORADA_DESCONHECIDO*/);
        clientData.addProperty("vatNumber", clientId);
        clientData.addProperty("fiscalCountry", clientId.substring(0, 2));
        clientData.addProperty("nationality", person.getCountry().getCode());
        clientData.addProperty("billingIndicator", 0);

        return clientData;
    }

    private String getPaymentMethodReference(AccountingTransactionDetail transactionDetail) {
        if (transactionDetail.getPaymentMode().equals(PaymentMode.ATM)) {
            return ((SibsTransactionDetail) transactionDetail).getSibsCode();
        }
        return "";
    }

    private String getPaymentMechanism(AccountingTransactionDetail transactionDetail) {
//            "NU" - numerário
//            "SI" - sibs
//            "OU" - outros        
        switch (transactionDetail.getPaymentMode()) {
        case CASH:
            return "NU";
        case ATM:
            return "SI";
        default:
            throw new Error();
        }
    }

    /**
     * Returns the open invoices and the remaining value of the first open invoice
     * The list is ordered, the first open invoice is the first of the list
     * 
     * @return
     */
    private SimpleImmutableEntry<List<SapEventEntry>, Money> getOpenInvoicesAndRemainingValue() {
        List<SapEventEntry> invoiceEntries = getInvoiceEntries().sorted(DOCUMENT_NUMBER_COMPARATOR).collect(Collectors.toList());
        Money invoiceAmount = Money.ZERO;
        Money firstRemainingValue = Money.ZERO;
        Money totalAmount = getPayedAmount().add(getCreditAmount());
        List<SapEventEntry> openInvoiceEntries = new ArrayList<SapEventEntry>();
        for (SapEventEntry invoiceEntry : invoiceEntries) {
            invoiceAmount = invoiceAmount.add(invoiceEntry.invoice);
            if (invoiceAmount.greaterThan(totalAmount)) {
                if (firstRemainingValue.isZero()) {
                    firstRemainingValue = invoiceAmount.subtract(totalAmount);
                }
                openInvoiceEntries.add(0, invoiceEntry);
            }
        }
        return new SimpleImmutableEntry<List<SapEventEntry>, Money>(openInvoiceEntries, firstRemainingValue);
    }

    private Stream<SapEventEntry> getInvoiceEntries() {
        return entries.stream().filter(e -> e.invoice.isPositive());
    }

    private String getLastInvoiceNumber() {
        Optional<SapEventEntry> findFirst = getInvoiceEntries().sorted(DOCUMENT_NUMBER_COMPARATOR.reversed()).findFirst();
        return findFirst.isPresent() ? findFirst.get().documentNumber : "";
    }

    /**
     * Returns the open debts and the remaining value of the first open debt
     * The list is ordered, the first open debt is the first of the list
     * 
     * @return
     */
    private SimpleImmutableEntry<List<SapEventEntry>, Money> getOpenDebtsAndRemainingValue() {
        List<SapEventEntry> debtEntries = getDebtEntries().sorted(DOCUMENT_NUMBER_COMPARATOR).collect(Collectors.toList());
        Money debtAmount = Money.ZERO;
        Money firstRemainingValue = Money.ZERO;
        Money totalAmount = getDebtCreditAmount();
        List<SapEventEntry> openDebtEntries = new ArrayList<SapEventEntry>();
        for (SapEventEntry debtEntry : debtEntries) {
            debtAmount = debtAmount.add(debtEntry.debt);
            if (debtAmount.greaterThan(totalAmount)) {
                if (firstRemainingValue.isZero()) {
                    firstRemainingValue = debtAmount.subtract(totalAmount);
                }
                openDebtEntries.add(0, debtEntry);
            }
        }
        return new SimpleImmutableEntry<List<SapEventEntry>, Money>(openDebtEntries, firstRemainingValue);
    }

    private Stream<SapEventEntry> getDebtEntries() {
        return entries.stream().filter(e -> e.debt.isPositive());
    }

    private String getLastDebtNumber() {
        Optional<SapEventEntry> findFirst = getDebtEntries().sorted(DOCUMENT_NUMBER_COMPARATOR.reversed()).findFirst();
        return findFirst.isPresent() ? findFirst.get().documentNumber : "";
    }

    private void checkClientStatus(JsonObject result, Event event, ErrorLogConsumer errorLog, EventLogger elogger, String action,
            JsonObject sentData) {
        JsonArray jsonArray = result.getAsJsonArray("customers");
        for (int iter = 0; iter < jsonArray.size(); iter++) {
            JsonObject json = jsonArray.get(iter).getAsJsonObject();
            if (!"S".equals(json.get("status").getAsString())) {
                logError(event, json.get("customerId").getAsString(), errorLog, elogger, json.get("returnMessage").getAsString(),
                        action, sentData);
            }
        }
    }

    private boolean checkDocumentsStatus(JsonObject result, Event event, ErrorLogConsumer errorLog, EventLogger elogger,
            String action) {
        JsonArray jsonArray = result.getAsJsonArray("documents");
        boolean checkStatus = true;
        for (int iter = 0; iter < jsonArray.size(); iter++) {
            JsonObject json = jsonArray.get(iter).getAsJsonObject();
            if (!"S".equals(json.get("status").getAsString())) {
                checkStatus = false;
                logError(event, errorLog, elogger, json.get("errorDescription").getAsString(),
                        json.get("documentNumber").getAsString(), action);
            }
        }
        return checkStatus;
    }

    private Money addAll(final Function<SapEventEntry, Money> f) {
        return entries.stream().map(e -> f.apply(e)).reduce(Money.ZERO, Money::add);
    }

    public Money getDebtAmount() {
        return addAll((e) -> e.debt);
    }

    public Money getDebtCreditAmount() {
        return entries.stream().filter(e -> e.debt.isNegative()).map(e -> e.debt.abs()).reduce(Money.ZERO, Money::add);
    }

    public Money getInvoiceAmount() {
        return addAll((e) -> e.invoice);
    }

    public Money getPayedAmount() {
        return addAll((e) -> e.payed);
    }

    public Money getCreditAmount() {
        return addAll((e) -> e.credit);
    }

    public Money getAdvancementAmount() {
        return addAll((e) -> e.advancement);
    }

    public Money getFinesAmount() {
        return addAll((e) -> e.fines);
    }

    public Money getReimbursementsAmount() {
        return addAll((e) -> e.reimbursement);
    }

    public boolean hasPayment(final AccountingTransactionDetail transactionDetail) {
        return entries.stream().filter(e -> e.paymentId.equals(transactionDetail.getExternalId())).findAny().isPresent();
    }

    public JsonArray readEventFile(final Event event) {
        final File file = sapEventFile(event);
        if (file.exists()) {
            try {
                return new JsonParser().parse(new String(Files.readAllBytes(file.toPath()))).getAsJsonArray();
            } catch (JsonSyntaxException | IOException e) {
                throw new Error(e);
            }
        }
        return new JsonArray();
    }

    private void persistLocalChange(final String clientId, final String documentNumber, final String sapDocumentNumber,
            final String type, final Money value, final String date, String paymentId, Money advancement) {
        final JsonObject json = new JsonObject();
        json.addProperty("clientId", clientId);
        json.addProperty("documentNumber", documentNumber);
        json.addProperty("sapDocumentNumber", sapDocumentNumber);
        json.addProperty("type", type);
        json.addProperty("value", value.toPlainString());
        json.addProperty("date", date);
        json.addProperty("paymentId", paymentId);
        json.addProperty("advancement", advancement.toPlainString());

        array.add(json);
        persistLocalChanges();
    }

    private void persistLocalChanges() {
        Utils.writeFileWithoutFailuer(file.toPath(), array.toString().getBytes(), false);
    }

    private File sapEventFile(final Event event) {
        final File dir = dirFor(event);
        return new File(dir, event.getExternalId() + ".json");
    }

    public SapEventEntry newSapEventEntry(final String clientId, final Money amount, final String documentNumber,
            final String sapDocumentNumber, final String type, final String paymentId, Money advancement) {
        final String now = new DateTime().toString(DT_FORMAT);

        persistLocalChange(clientId, documentNumber, sapDocumentNumber, type, amount, now, paymentId, advancement);

        final SapEventEntry entry = new SapEventEntry(clientId, documentNumber, sapDocumentNumber, paymentId);
        entries.add(entry);
        return entry;
    }

    private void logPastPayments(final AccountingTransactionDetail d, final String clientId, final Money txAmount,
            final String receiptNumber, final String now, final String registryDate) {
        final Event event = d.getEvent();
        final ExecutionYear debtYear = Utils.executionYearOf(event);

        final StringBuilder builder = new StringBuilder();
        builder.append(event.getExternalId());
        builder.append("\t");
        builder.append(d.getExternalId());
        builder.append("\t");
        builder.append(clientId == null ? " " : clientId);
        builder.append("\t");
        builder.append(txAmount.toPlainString());
        builder.append("\t");
        builder.append(receiptNumber);
        builder.append("\t");
        builder.append(now);
        builder.append("\t");
        builder.append(registryDate);
        builder.append("\t");
        builder.append(debtYear.getName());
        builder.append("\n");
        appendPastLog(builder.toString());
    }

    private void appendPastLog(final String line) {
        final File file = new File("/afs/ist.utl.pt/ciist/fenix/fenix015/ist/sap_past_event_log.csv");
        if (!file.exists()) {
            final StringBuilder builder = new StringBuilder();
            builder.append("Event");
            builder.append("\t");
            builder.append("Transaction");
            builder.append("\t");
            builder.append("ClientId");
            builder.append("\t");
            builder.append("TX Amount");
            builder.append("\t");
            builder.append("Receipt Number");
            builder.append("\t");
            builder.append("Sent To Accounting");
            builder.append("\t");
            builder.append("Registry Date");
            builder.append("\t");
            builder.append("Debt Year");
            builder.append("\n");
            append(file, builder.toString());
        }
        append(file, line);
    }

    private void append(final File file, final String line) {
        try {
            Files.write(file.toPath(), line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
        } catch (final IOException e) {
            throw new Error(e);
        }
    }

    private SimpleImmutableEntry<String, String> mapToProduct(Event event, String eventDescription, boolean isDebtRegistration) {
        if (event.isGratuity()) {
            final GratuityEvent gratuityEvent = (GratuityEvent) event;
            final StudentCurricularPlan scp = gratuityEvent.getStudentCurricularPlan();
            final Degree degree = scp.getDegree();
            if (scp.getRegistration().getRegistrationProtocol().isAlien()) {
                if (isDebtRegistration) {
                    return new SimpleImmutableEntry<String, String>("E0075", "ESP PROPINAS INTERNACIONAL");
                } else {
                    return new SimpleImmutableEntry<String, String>("0075", "PROPINAS INTERNACIONAL");
                }
            }
            if (degree.isFirstCycle() && degree.isSecondCycle()) {
                if (isDebtRegistration) {
                    return new SimpleImmutableEntry<String, String>("E0030", "ESP PROPINAS MESTRADO INTEGRADO");
                } else {
                    return new SimpleImmutableEntry<String, String>("0030", "PROPINAS MESTRADO INTEGRADO");
                }
            }
            if (degree.isFirstCycle()) {
                if (isDebtRegistration) {
                    return new SimpleImmutableEntry<String, String>("E0027", "ESP PROPINAS 1 CICLO");
                } else {
                    return new SimpleImmutableEntry<String, String>("0027", "PROPINAS 1 CICLO");
                }
            }
            if (degree.isSecondCycle()) {
                if (isDebtRegistration) {
                    return new SimpleImmutableEntry<String, String>("E0028", "ESP PROPINAS 2 CICLO");
                } else {
                    return new SimpleImmutableEntry<String, String>("0028", "PROPINAS 2 CICLO");
                }
            }
            if (degree.isThirdCycle()) {
                if (isDebtRegistration) {
                    return new SimpleImmutableEntry<String, String>("E0029", "ESP PROPINAS 3 CICLO");
                } else {
                    return new SimpleImmutableEntry<String, String>("0029", "PROPINAS 3 CICLO");
                }
            }
            if (isDebtRegistration) {
                return new SimpleImmutableEntry<String, String>("E0076", "ESP PROPINAS OUTROS");
            } else {
                return new SimpleImmutableEntry<String, String>("0076", "PROPINAS OUTROS");
            }
        }
        if (event instanceof PhdGratuityEvent) {
            if (isDebtRegistration) {
                return new SimpleImmutableEntry<String, String>("E0029", "ESP PROPINAS 3 CICLO");
            } else {
                return new SimpleImmutableEntry<String, String>("0029", "PROPINAS 3 CICLO");
            }
        }
        if (event.isResidenceEvent()) {
            return null;
        }
        if (event.isFctScholarshipPhdGratuityContribuitionEvent()) {
            return null;
        }
        if (event.isAcademicServiceRequestEvent()) {
            if (eventDescription.indexOf(" Reingresso") >= 0) {
                return new SimpleImmutableEntry<String, String>("0035", "OUTRAS TAXAS");
            }
            return new SimpleImmutableEntry<String, String>("0037", "EMOLUMENTOS");
        }
        if (event.isDfaRegistrationEvent()) {
            return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
        }
        if (event.isIndividualCandidacyEvent()) {
            return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
        }
        if (event.isEnrolmentOutOfPeriod()) {
            return new SimpleImmutableEntry<String, String>("0035", "OUTRAS TAXAS");
        }
        if (event instanceof AdministrativeOfficeFeeAndInsuranceEvent) {
            return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
        }
        if (event instanceof InsuranceEvent) {
            return new SimpleImmutableEntry<String, String>("0034", "SEGURO ESCOLAR");
        }
        if (event.isSpecializationDegreeRegistrationEvent()) {
            return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
        }
        if (event instanceof ImprovementOfApprovedEnrolmentEvent) {
            return new SimpleImmutableEntry<String, String>("0033", "TAXAS DE MELHORIAS DE NOTAS");
        }
        if (event instanceof DFACandidacyEvent) {
            return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
        }
        if (event instanceof SpecialSeasonEnrolmentEvent) {
            return new SimpleImmutableEntry<String, String>("0032", "TAXAS  DE EXAMES");
        }
        if (event.isPhdEvent()) {
            if (eventDescription.indexOf("Taxa de Inscri") >= 0) {
                return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
            }
            if (eventDescription.indexOf("Requerimento de provas") >= 0) {
                return new SimpleImmutableEntry<String, String>("0032", "TAXAS  DE EXAMES");
            }
            return new SimpleImmutableEntry<String, String>("0031", "TAXAS DE MATRICULA");
        }
        throw new Error("not.supported: " + event.getExternalId());
    }

    private String sanitize(final String s) {
        return s.replace('/', '_').replace('\\', '_');
    }

    private File dirFor(final Event event) {
        final String id = event.getExternalId();
        final String dirPath =
                GiafInvoiceConfiguration.getConfiguration().sapInvoiceDir() + Utils.splitPath(id) + File.separator + id;
        final File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void logError(Event event, String clientId, ErrorLogConsumer errorLog, EventLogger elogger, String returnMessage,
            String action, JsonObject sentData) {
        errorLog.accept(event.getExternalId(), clientId, event.getPerson().getName(), "", "", returnMessage, "", "",
                sentData.get("clientData").getAsJsonObject().get("fiscalCountry").getAsString(), clientId,
                sentData.get("clientData").getAsJsonObject().get("street").getAsString(), "",
                sentData.get("clientData").getAsJsonObject().get("postalCode").getAsString(), "", "", "", action);
        elogger.log("Pessoa %s: evento: %s %s %s %s %n", event.getPerson().getExternalId(), event.getExternalId(), clientId,
                returnMessage, action);
    }

    private void logError(Event event, ErrorLogConsumer errorLog, EventLogger elogger, String errorMessage, String documentNumber,
            String action) {
        BigDecimal amount;
        DebtCycleType cycleType;
//        try {
//            amount = Utils.calculateTotalDebtValue(event).getAmount();
//            cycleType = Utils.cycleType(event);
//        } catch (Exception ex) {
        amount = null;
        cycleType = null;
//        }

        errorLog.accept(event.getExternalId(), event.getPerson().getUsername(), event.getPerson().getName(),
                amount == null ? "" : amount.toPlainString(), cycleType == null ? "" : cycleType.getDescription(), errorMessage,
                "", "", "", "", "", "", "", "", "", documentNumber, action);
        elogger.log("%s: %s %s %s %n", event.getExternalId(), errorMessage, documentNumber, action);
    }
}
