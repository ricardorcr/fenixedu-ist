package pt.ist.fenixedu.integration.task.exportData;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.transport.http.HTTPConduit;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.accounting.Event;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.generated.sources.saft.sap.AddressStructure;
import org.fenixedu.generated.sources.saft.sap.AddressStructurePT;
import org.fenixedu.generated.sources.saft.sap.AuditFile;
import org.fenixedu.generated.sources.saft.sap.AuditFile.MasterFiles;
import org.fenixedu.generated.sources.saft.sap.Customer;
import org.fenixedu.generated.sources.saft.sap.Header;
import org.fenixedu.generated.sources.saft.sap.OrderReferences;
import org.fenixedu.generated.sources.saft.sap.PaymentMethod;
import org.fenixedu.generated.sources.saft.sap.Product;
import org.fenixedu.generated.sources.saft.sap.ReimbursementStatusType;
import org.fenixedu.generated.sources.saft.sap.SAFTPTPaymentType;
import org.fenixedu.generated.sources.saft.sap.SAFTPTSettlementType;
import org.fenixedu.generated.sources.saft.sap.SAFTPTSourceBilling;
import org.fenixedu.generated.sources.saft.sap.SAFTPTSourcePayment;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.Payments;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.Payments.Payment;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.Payments.Payment.AdvancedPaymentCredit;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.Payments.Payment.DocumentTotals;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.Payments.Payment.Line.SourceDocumentID;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.Payments.Payment.ReimbursementProcess;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.WorkingDocuments.WorkDocument;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.WorkingDocuments.WorkDocument.AdvancedPayment;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.WorkingDocuments.WorkDocument.Line;
import org.fenixedu.generated.sources.saft.sap.SourceDocuments.WorkingDocuments.WorkDocument.Line.Metadata;
import org.fenixedu.generated.sources.saft.sap.Tax;
import org.fenixedu.generated.sources.saft.sap.TaxTable;
import org.fenixedu.generated.sources.saft.sap.TaxTableEntry;
import org.joda.time.DateTime;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.sap.document.sap.soap.functions.mc_style.ZULWSFATURACAOCLIENTESBLK;
import com.sap.document.sap.soap.functions.mc_style.ZULWSFATURACAOCLIENTESBLK_Service;
import com.sap.document.sap.soap.functions.mc_style.ZulfwscustomersReturn1S;
import com.sap.document.sap.soap.functions.mc_style.ZulwsDocumentosInput;
import com.sap.document.sap.soap.functions.mc_style.ZulwsDocumentosOutput;
import com.sap.document.sap.soap.functions.mc_style.ZulwsdocumentStatusWs1;
import com.sap.document.sap.soap.functions.mc_style.ZulwsfaturacaoClientesIn;
import com.sap.document.sap.soap.functions.mc_style.ZulwsfaturacaoClientesOut;
import com.sun.xml.ws.client.BindingProviderProperties;

import pt.ist.fenixedu.giaf.invoices.ClientMap;
import pt.ist.fenixframework.FenixFramework;

public class SapExporter extends CustomTask {

    private static final String SAFT_PT_ENCODING = "UTF-8";
    private static final String ERP_HEADER_VERSION_1_00_00 = "1.0.3";
    private static final String MORADA_DESCONHECIDO = "Desconhecido";
    private static final String PRODUCT_ID = "FenixEdu/FenixEdu";
    private static final String PRODUCT_VERSION = "5.0.0.0";
    private static final int SOFTWARE_CERTIFICATE_NUMBER = 0;
    private static final String PRODUCT_COMPANY_TAX_ID = "999999999";
    private static final int MAX_STREET_NAME = 90;
    private static final int MAX_REASON = 50;
    private static final String VAT_TAX_CODE = "ISE";
    private static final BigDecimal VAT_TAX = BigDecimal.ZERO;
    private static final String DEBT = "ND190000";
    private static final String PAYMENT = "NP190000";
    private static final String DEBT_REGISTER = "NG2";
    private static final String DEBT_EXEMPTION = "NJ3";
    private static final String CREDIT_NOTE_NA = "NA43";
    private static final String REIMBURSEMENT = "NR18";
    private static final String WORKING_ORIGINATING_ON = "NP43";
    private static final String PAYMENT_ORIGINATING_ON = "ND190000";
    private static final String PAYMENT_ND_ORIGINATING_ON = "ND17";

    @Override
    public void runTask() throws Exception {
//        JsonObject result = getDocument("2210000224/20182018-20-20");//NA -> 3230000002/2017 # 2220000001/20172017-20-20 -> NP
//        if (result.get("documentBase64") != null) {
//            output("recibo_sap.pdf", Base64.getDecoder().decode(result.get("documentBase64").getAsString()));
//        }

        final boolean debtRegistration = false;
        final boolean debtExemption = false;
        final boolean debt = true;
        final boolean payment = false;
        final boolean creditNote = false;
        final boolean reimbursement = false;
        final boolean advancedPayment = false;
        final boolean isDuplicate = false;
        Event eventToProcess = getEventToProcess();
        String sapFileXml = generateSapFile("IST", new DateTime().minusDays(5), new DateTime(), eventToProcess, debtRegistration,
                debtExemption, debt, payment, creditNote, reimbursement, advancedPayment, isDuplicate);
        byte[] bytes = null;
        try {
            bytes = sapFileXml.getBytes(SAFT_PT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        sendInfoOnline(bytes);
    }

    private Event getEventToProcess() {

        return FenixFramework.getDomainObject("281500746542574");

//        return ExecutionYear.readCurrentExecutionYear().getAnnualEventsSet().stream()
//                .filter(e -> e.getWhenOccured().getYear() == 2017 && e.getPostingRule() != null
//                        && !(e.getPostingRule() instanceof StandaloneEnrolmentGratuityPR) && e.getAmountToPay().isPositive())
//                .limit(1).collect(Collectors.toList());
    }

    private String generateSapFile(final String institution, final DateTime fromDate, final DateTime toDate, final Event event,
            final boolean debtRegistration, final boolean debtExemption, final boolean debt, final boolean payment,
            final boolean creditNote, final boolean reimbursement, final boolean advancedPayment, final boolean isDuplicate)
            throws Exception {

        // Build SAFT-AuditFile

        AuditFile auditFile = new AuditFile();

        // Build SAFT-HEADER (Chapter 1 in AuditFile)
        Header header = createSAFTHeader(fromDate, toDate, /*institution,*/ ERP_HEADER_VERSION_1_00_00);
        // SetHeader
        auditFile.setHeader(header);

        // Build Master-Files
        MasterFiles masterFiles = new MasterFiles();

        // SetMasterFiles
        auditFile.setMasterFiles(masterFiles);

        // ClientsTable (Chapter 2.2 in AuditFile)
        List<Customer> customerList = masterFiles.getCustomer();
        final Map<String, Person> customerMap = new HashMap<String, Person>();

        taskLog("Generating Customer");
        Person payer = null;
        if (payment) {
//            payer = FenixFramework.getDomainObject("846731327574186");
//            customerMap.put(payer.getExternalId(), payer);
        } else {
            customerMap.put(event.getPerson().getExternalId(), event.getPerson());
        }

        // TaxTable (Chapter 2.5 in AuditFile)
        TaxTable taxTable = new TaxTable();
        masterFiles.setTaxTable(taxTable);

        //TODO ver o código de iva
        TaxTableEntry taxEntry = new TaxTableEntry();
        taxEntry.setTaxType("IVA");
        taxEntry.setTaxCode(VAT_TAX_CODE/*vat.getVatType().getCode()*/);
        taxEntry.setTaxCountryRegion("PT");
        taxEntry.setDescription("");
        taxEntry.setTaxPercentage(VAT_TAX);

        taxTable.getTaxTableEntry().add(taxEntry);

        // Set MovementOfGoods in SourceDocuments(AuditFile)
        SourceDocuments sourceDocuments = new SourceDocuments();
        auditFile.setSourceDocuments(sourceDocuments);

        SourceDocuments.WorkingDocuments workingDocuments = new SourceDocuments.WorkingDocuments();
        BigDecimal amount = new BigDecimal(150);
        if (debtRegistration) {
            amount = event.getTotalAmountToPay().getAmount();
        } else if (debtExemption) {
            amount = new BigDecimal(100);
        } else if (advancedPayment) {
            amount = new BigDecimal(50);
        } else if (isDuplicate) {
            amount = new BigDecimal(150);
        }

        BigInteger numberOfWorkingDocuments = BigInteger.ONE;
        BigDecimal totalCreditOfWorkingDocuments = BigDecimal.ZERO;
        BigDecimal totalDebitOfWorkingDocuments = BigDecimal.ZERO;

        if (debtRegistration || debt) {
            totalDebitOfWorkingDocuments = amount;
        }
        if (debtExemption || advancedPayment || creditNote || reimbursement) {
            totalCreditOfWorkingDocuments = amount;
        }

        if (!payment) {
            try {
                WorkDocument workDocument = convertToSAFTWorkDocument(event, amount, debtRegistration, debtExemption, debt,
                        payment, creditNote, reimbursement, advancedPayment, payer);
                workingDocuments.getWorkDocument().add(workDocument);
            } catch (Exception ex) {
                taskLog("Error processing document " + event.getExternalId() + ": " + ex.getLocalizedMessage());
                throw ex;
            }
        }

        // Update Totals of Workingdocuments
        workingDocuments.setNumberOfEntries(numberOfWorkingDocuments);
        workingDocuments.setTotalCredit(totalCreditOfWorkingDocuments.setScale(2, RoundingMode.HALF_EVEN));
        workingDocuments.setTotalDebit(totalDebitOfWorkingDocuments.setScale(2, RoundingMode.HALF_EVEN));
        sourceDocuments.setWorkingDocuments(workingDocuments);

        //PROCESSING PAYMENTS TABLE
        BigInteger numberOfPaymentsDocuments = BigInteger.ZERO;
        BigDecimal totalCreditOfPaymentsDocuments = BigDecimal.ZERO;
        BigDecimal totalDebitOfPaymentsDocuments = BigDecimal.ZERO;

        Payments paymentsDocuments = new Payments();
        if (payment || creditNote || reimbursement || advancedPayment) {
            numberOfPaymentsDocuments = BigInteger.ONE;

            if (payment || advancedPayment) {
                amount = new BigDecimal(50);
                totalDebitOfPaymentsDocuments = amount;
            }
            if (debtExemption || creditNote || reimbursement) {
                totalCreditOfPaymentsDocuments = amount;
            }
            if (advancedPayment && isDuplicate) {
                totalCreditOfPaymentsDocuments = BigDecimal.ZERO;
                totalDebitOfPaymentsDocuments = BigDecimal.ZERO;
            }
            try {
                Payment paymentDocument = convertToSAFTPaymentDocument(event, amount, debtRegistration, debtExemption, debt,
                        payment, creditNote, reimbursement, advancedPayment, isDuplicate, payer);
                paymentsDocuments.getPayment().add(paymentDocument);
            } catch (Exception ex) {
                taskLog("Error processing document " + event.getExternalId() + ": " + ex.getLocalizedMessage());
                throw ex;
            }
        }

        // Update Totals of Payment Documents
        paymentsDocuments.setNumberOfEntries(numberOfPaymentsDocuments);
        paymentsDocuments.setTotalCredit(totalCreditOfPaymentsDocuments.setScale(2, RoundingMode.HALF_EVEN));
        paymentsDocuments.setTotalDebit(totalDebitOfPaymentsDocuments.setScale(2, RoundingMode.HALF_EVEN));
        sourceDocuments.setPayments(paymentsDocuments);

        // Update the Customer Table in SAFT
        for (final Person customer : customerMap.values()) {
            final Customer customerSaft = convertCustomerToSAFTCustomer(customer);
            customerList.add(customerSaft);
        }

        String xml = exportAuditFileToXML(auditFile);

        taskLog("SAFT File export concluded with success.");
        output("sap.xml", xml.getBytes());
        return xml;
    }

    private Header createSAFTHeader(DateTime startDate, DateTime endDate, String auditVersion) {

        Header header = new Header();
        DatatypeFactory dataTypeFactory;
        try {

            dataTypeFactory = DatatypeFactory.newInstance();

            // AuditFileVersion
            header.setAuditFileVersion(auditVersion);
            header.setIdProcesso("006"/*finantialInstitution.getErpIntegrationConfiguration().getErpIdProcess()*/);

            // BusinessName - Nome da Empresa
            header.setBusinessName("Técnico Lisboa" /*finantialInstitution.getCompanyName()*/);
            header.setCompanyName("Instituto Superior Técnico"/*finantialInstitution.getName()*/);

            // CompanyAddress
            AddressStructurePT companyAddress = null;
            //TODOJN Locale por resolver
            companyAddress =
                    convertFinantialInstitutionAddressToAddressPT("Avenida Rovisco Pais, 1"/*finantialInstitution.getAddress()*/,
                            "1049-001", "Lisboa", "Avenida Rovisco Pais, 1");
            header.setCompanyAddress(companyAddress);

            // CompanyID
            /*
             * Obtem -se pela concatenação da conservatória do registo comercial
             * com o número do registo comercial, separados pelo caracter
             * espaço. Nos casos em que não existe o registo comercial, deve ser
             * indicado o NIF.
             */
            header.setCompanyID("256241256"/*finantialInstitution.getFiscalNumber()*/);

            // CurrencyCode
            /*
             * 1.11 * Código de moeda (CurrencyCode) . . . . . . . Preencher com 'EUR'
             */
            header.setCurrencyCode("EUR"/*finantialInstitution.getCurrency().getCode()*/);

            // DateCreated
            DateTime now = new DateTime();

            /* ANIL: 2015/10/20 converted from dateTime to Date */
            header.setDateCreated(convertToXMLDate(dataTypeFactory, now));

            // Email
            // header.setEmail(StringUtils.EMPTY);

            // EndDate

            // StartDate
            header.setStartDate(convertToXMLDate(dataTypeFactory, startDate));
            /* ANIL: 2015/10/20 converted from dateTime to Date */
            header.setEndDate(convertToXMLDate(dataTypeFactory, endDate));

            // Fax
            // header.setFax(StringUtils.EMPTY);

            // FiscalYear
            /*
             * Utilizar as regras do código do IRC, no caso de períodos
             * contabilísticos não coincidentes com o ano civil. (Ex: período de
             * tributação de 01 -10 -2008 a 30 -09 -2009 corresponde FiscalYear
             * 2008). Inteiro 4
             */
            header.setFiscalYear(endDate.getYear());

            // Ir obter a data do ?ltimo
            // documento(por causa de submeter em janeiro, documentos de
            // dezembro)

            // HeaderComment
            // header.setHeaderComment(org.apache.commons.lang.StringUtils.EMPTY);

            // ProductCompanyTaxID
            // Preencher com o NIF da entidade produtora do software
            header.setProductCompanyTaxID(PRODUCT_COMPANY_TAX_ID);

            // ProductID
            /*
             * 1.16 * Nome do produto (ProductID). . . . . . . . . . . Nome do
             * produto que gera o SAF -T (PT) . . . . . . . . . . . Deve ser
             * indicado o nome comercial do software e o da empresa produtora no
             * formato 'Nome produto/nome empresa'.
             */
            header.setProductID(PRODUCT_ID);

            // Product Version
            header.setProductVersion(PRODUCT_VERSION);

            // SoftwareCertificateNumber
            /* Changed to 0 instead of -1 decribed in SaftConfig.SOFTWARE_CERTIFICATE_NUMBER() */
            header.setSoftwareCertificateNumber(BigInteger.valueOf(SOFTWARE_CERTIFICATE_NUMBER));

            // TaxAccountingBasis
            /*
             * Deve ser preenchido com: contabilidade; facturação; 'I' ? dados
             * integrados de facturação e contabilidade; 'S' ? autofacturação;
             * 'P' ? dados parciais de facturação
             */
            header.setTaxAccountingBasis("P");

            // TaxEntity
            /*
             * Identificação do estabelecimento (TaxEntity) No caso do ficheiro
             * de facturação deverá ser especificado a que estabelecimento diz
             * respeito o ficheiro produzido, se aplicável, caso contrário,
             * deverá ser preenchido com a especificação 'Global'. No caso do
             * ficheiro de contabilidade ou integrado, este campo deverá ser
             * preenchido com a especificação 'Sede'. Texto 20
             */
            header.setTaxEntity("Global");

            // TaxRegistrationNumber
            /*
             * Número de identificação fiscal da empresa
             * (TaxRegistrationNumber). Preencher com o NIF português sem
             * espaços e sem qualquer prefixo do país. Inteiro 9
             */
            try {
                header.setTaxRegistrationNumber(501507930/*finantialInstitution.getFiscalNumber()*/);
            } catch (Exception ex) {
                throw new RuntimeException("Invalid Fiscal Number.");
            }
            // header.setTelephone(finantialInstitution.get);
            // header.setWebsite(finantialInstitution.getEmailContact());

            return header;
        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
            return null;
        }
    }

    public AddressStructurePT convertFinantialInstitutionAddressToAddressPT(final String addressDetail, final String zipCode,
            final String zipCodeRegion, final String street) {
        final AddressStructurePT companyAddress = new AddressStructurePT();

        companyAddress.setCountry("PT");
        companyAddress.setAddressDetail(!Strings.isNullOrEmpty(addressDetail) ? addressDetail : MORADA_DESCONHECIDO);
        companyAddress.setCity(!Strings.isNullOrEmpty(zipCodeRegion) ? zipCodeRegion : MORADA_DESCONHECIDO);
        companyAddress.setPostalCode(!Strings.isNullOrEmpty(zipCode) ? zipCode : MORADA_DESCONHECIDO);
        companyAddress.setRegion(!Strings.isNullOrEmpty(zipCodeRegion) ? zipCodeRegion : MORADA_DESCONHECIDO);
        companyAddress.setStreetName(Splitter.fixedLength(MAX_STREET_NAME).splitToList(street).get(0));

        return companyAddress;
    }

    private String exportAuditFileToXML(AuditFile auditFile) {
        try {
            final String cleanXMLAnotations = "xsi:type=\"xs:string\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";
            final String cleanXMLAnotations2 = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
            final String cleanXMLAnotations3 = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:string\"";
            final String cleanDateTimeMiliseconds = ".000<";
            final String cleanStandaloneAnnotation = "standalone=\"yes\"";

            final JAXBContext jaxbContext = JAXBContext.newInstance(AuditFile.class);
            Marshaller marshaller = jaxbContext.createMarshaller();

            ByteArrayOutputStream writer = new ByteArrayOutputStream();

            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, SAFT_PT_ENCODING);
            marshaller.marshal(auditFile, writer);

            String xml = new String(writer.toByteArray(), SAFT_PT_ENCODING);
            xml = xml.replace(cleanXMLAnotations, "");
            xml = xml.replace(cleanXMLAnotations2, "");
            xml = xml.replace(cleanXMLAnotations3, "");
            xml = xml.replace(cleanDateTimeMiliseconds, "<");
            xml = xml.replace(cleanStandaloneAnnotation, "");

            try {
                MessageDigest md = MessageDigest.getInstance("SHA1");
                md.update(("SALTING WITH QUB:" + xml).getBytes(SAFT_PT_ENCODING));
                byte[] output = md.digest();
                String digestAscii = bytesToHex(output);
                xml = xml + "<!-- QUB-IT (remove this line,add the qubSALT, save with UTF-8 encode): " + digestAscii + " -->\n";
            } catch (Exception ex) {
                taskLog("Exception during digest");
                ex.printStackTrace();
            }
            return xml;
        } catch (JAXBException e) {
            return org.apache.commons.lang.StringUtils.EMPTY;
        } catch (UnsupportedEncodingException jex) {
            return org.apache.commons.lang.StringUtils.EMPTY;
        }
    }

    private String bytesToHex(byte[] b) {
        char hexDigit[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        StringBuffer buf = new StringBuffer();
        for (byte element : b) {
            buf.append(hexDigit[element >> 4 & 0x0f]);
            buf.append(hexDigit[element & 0x0f]);
        }
        return buf.toString();
    }

    private enum StatusType {
        PENDING, ERROR, SUCCESS;
    }

    private JsonObject getDocument(String documentNumber) {
        final ZULWSFATURACAOCLIENTESBLK client = getClient();

        //Set Timeout for the client
        Map<String, Object> requestContext = ((BindingProvider) client).getRequestContext();
        requestContext.put(BindingProviderProperties.REQUEST_TIMEOUT, 15000); // Timeout in millis
        requestContext.put(BindingProviderProperties.CONNECT_TIMEOUT, 2000); // Timeout in millis

        ZulwsDocumentosInput input = new ZulwsDocumentosInput();

        input.setTaxRegistrationNumber("501507930"); //IST
        input.setFinantialDocumentNumber(documentNumber);
        input.setIdProcesso("");

        final ZulwsDocumentosOutput zulwsDocumentos = client.zulwsDocumentos(input);

        final JsonObject output = new JsonObject();
        output.addProperty("documentBase64", Base64.getEncoder().encodeToString(zulwsDocumentos.getBinary()));
        output.addProperty("status", zulwsDocumentos.getStatus());
        output.addProperty("errorDescription", zulwsDocumentos.getErrorDescription());
        final StatusType status = "S".equals(zulwsDocumentos.getStatus()) ? StatusType.SUCCESS : StatusType.ERROR;

        taskLog("Status: %s - %s %n", zulwsDocumentos.getStatus(), zulwsDocumentos.getErrorDescription());
        if (status != StatusType.SUCCESS) {
            taskLog("Documento não está ok %s %n", input.getFinantialDocumentNumber());

        }
        return output;
    }

    public void sendInfoOnline(byte[] content/*final FinantialInstitution finantialInstitution,
                                             DocumentsInformationInput documentsInformation*/) {

//        DocumentsInformationOutput output = new DocumentsInformationOutput();
//        output.setDocumentStatus(new ArrayList<DocumentStatusWS>());
        final ZULWSFATURACAOCLIENTESBLK client = getClient();
//
//        final SOAPLoggingHandler loggingHandler = SOAPLoggingHandler.createLoggingHandler((BindingProvider) client);
//
        //Set Timeout for the client
        Map<String, Object> requestContext = ((BindingProvider) client).getRequestContext();
        requestContext.put(BindingProviderProperties.REQUEST_TIMEOUT, 15000); // Timeout in millis
        requestContext.put(BindingProviderProperties.CONNECT_TIMEOUT, 2000); // Timeout in millis

        ZulwsfaturacaoClientesIn auditFile = new ZulwsfaturacaoClientesIn();
        auditFile.setFinantialInstitution("IST");
        auditFile.setData(content);

        ZulwsfaturacaoClientesOut zulwsfaturacaoClientesOut = client.zulfmwsFaturacaoClientes(auditFile);

//        output.setRequestId(zulwsfaturacaoClientesOut.getRequestId());
//
//        boolean hasSettlementFailed = hasSettlementFailed(finantialInstitution, zulwsfaturacaoClientesOut);
//        boolean isSomeDocAssociatedWithReimbursementFailed =
//                isSomeDocAssociatedWithReimbursementFailed(finantialInstitution, zulwsfaturacaoClientesOut);
//
        taskLog("-- Documentos --");
        for (ZulwsdocumentStatusWs1 item : zulwsfaturacaoClientesOut.getDocumentStatus().getItem()) {
//            final DocumentStatusWrapper itemWrapper = new DocumentStatusWrapper(finantialInstitution, item);

//            DocumentStatusWS status = new DocumentStatusWS();
//            status.setDocumentNumber(itemWrapper.getDocumentNumber());
//            status.setErrorDescription(
//                    String.format("[STATUS: %s] - %s", itemWrapper.getIntegrationStatus(), itemWrapper.getErrorDescription()));
//            status.setIntegrationStatus(
//                    convertToStatusType(itemWrapper, hasSettlementFailed, isSomeDocAssociatedWithReimbursementFailed));
//            status.setSapDocumentNumber(itemWrapper.getSapDocumentNumber());
            taskLog("#####################");
            taskLog("Document number: " + item.getDocumentNumber());
            taskLog("Description: [STATUS: %s] - %s\n", item.getIntegrationStatus(), item.getErrorDescription());
            taskLog("Sap document number: " + item.getSapDocumentNumber());
            taskLog("Certified document url: " + item.getCertifiedDocumentUrl());

//            output.getDocumentStatus().add(status);
        }

        taskLog("-- Clientes --");
        for (final ZulfwscustomersReturn1S item : zulwsfaturacaoClientesOut.getCustomers().getItem()) {
//            final String otherMessage = String.format("%s (SAP nº %s): [%s] %s",
//                    Constants.bundle("label.SAPExternalService.customer.integration.result"),
//                    !Strings.isNullOrEmpty(item.getCustomerIdSap()) ? item.getCustomerIdSap() : "", item.getIntegrationStatus(),
//                    item.getReturnMsg());
//
//            output.getOtherMessages().add(otherMessage);
            taskLog("#####################");
            taskLog("Customer ID: " + item.getCustomerId());
            taskLog("Customer ID Sap: " + item.getCustomerIdSap());
            taskLog("Integration satus: " + item.getIntegrationStatus());
            taskLog("Message: " + item.getReturnMsg());
        }
//
//        output.setSoapInboundMessage(loggingHandler.getInboundMessage());
//        output.setSoapOutboundMessage(loggingHandler.getOutboundMessage());        
    }

    private ZULWSFATURACAOCLIENTESBLK getClient() {
        BindingProvider port = getService();
        setupClient(port);
        return (ZULWSFATURACAOCLIENTESBLK) port;
    }

    protected BindingProvider getService() {
        BindingProvider prov = (BindingProvider) new ZULWSFATURACAOCLIENTESBLK_Service().getZULWSFATURACAOCLIENTESBLK();
        return prov;
    }

    protected void setupClient(BindingProvider bindingProvider) {
//        WebServiceClientConfiguration webServiceClientConfiguration = getWebServiceClientConfiguration();
        String url =
                "https://sapq1.sap.tecnico.ulisboa.pt:8443/sap/bc/srt/rfc/sap/zulws_faturacaoclientes_blk/010/zulws_faturacaoclientes_blk/zulws_faturacaoclientes_blk";

        //TODO only for development version!! in production the certificate must match
        Client client = ClientProxy.getClient(bindingProvider);
        client.getInInterceptors().add(new LoggingInInterceptor());
        client.getOutInterceptors().add(new LoggingOutInterceptor());
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        TLSClientParameters tlsCP = new TLSClientParameters();
        // other TLS/SSL configuration like setting up TrustManagers
        tlsCP.setDisableCNCheck(true);
        httpConduit.setTlsClientParameters(tlsCP);

        if (url != null && url.length() > 0) {
            bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
        }
        if (false/*webServiceClientConfiguration.isSSLActive()*/) {
            // setSSLConnection(bindingProvider);
        }

        if (true /*webServiceClientConfiguration.isSecured()*/) {
            if (false /*webServiceClientConfiguration.isUsingWSSecurity()*/) {
                if (false /*webServiceClientConfiguration.getDomainKeyStore() != null*/) {
                    List<Handler> handlerList = bindingProvider.getBinding().getHandlerChain();
                    if (handlerList == null) {
                        handlerList = new ArrayList<Handler>();
                    }
//                    handlerList.add(new WebServiceClientHandler(webServiceClientConfiguration, this.username, this.password));
//                    bindingProvider.getBinding().setHandlerChain(handlerList);
                } else {
                    throw new IllegalStateException(
                            "Security was activated to webservice client but no keystore was defined! Fix that in the configuration interface");
                }
            } else {
                bindingProvider.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, "WSFENIX"/*this.username*/);
                bindingProvider.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY,
                        "pu@p+a2bzvJ4PhbAfP497TJ3l#WJgPKmt>tSlDB}"/*this.password*/);
            }

        }
    }

//    private void setSSLConnection(BindingProvider bp) {
//        WebServiceClientConfiguration webServiceClientConfiguration = getWebServiceClientConfiguration();
//        try {
//            SSLContext sslContext = SSLContext.getInstance(getSSLVersion());
//            KeyStoreWorker helper = webServiceClientConfiguration.getDomainKeyStore().getHelper();
//            KeyManagerFactory kmf =
//                    helper.getKeyManagerFactoryNeededForSSL(webServiceClientConfiguration.getAliasForSSLCertificate());
//            TrustManagerFactory tmf = helper.getTrustManagerFactoryNeededForSSL();
//            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
//
//            bp.getRequestContext().put(JAXWSProperties.SSL_SOCKET_FACTORY, sslContext.getSocketFactory());
//        } catch (Exception e) {
//            throw new RuntimeException("Problems creating sslContext", e);
//        }
//    }
//
//    protected String getSSLVersion() {
//        return "TLSv1.2";
//    }

//    private String exportFinantialDocumentToXML(final FinantialInstitution finantialInstitution,
//            List<FinantialDocument> documents, final UnaryOperator<AuditFile> preProcessFunctionBeforeSerialize) {
//
//        if (documents.isEmpty()) {
//            //throw new TreasuryDomainException("error.ERPExporter.no.document.to.export");
//        }
//
//        checkForUnsetDocumentSeriesNumberInDocumentsToExport(documents);
//
//        documents = processCreditNoteSettlementsInclusion(documents);
//
//        DateTime beginDate =
//                documents.stream().min((x, y) -> x.getDocumentDate().compareTo(y.getDocumentDate())).get().getDocumentDate();
//        DateTime endDate =
//                documents.stream().max((x, y) -> x.getDocumentDate().compareTo(y.getDocumentDate())).get().getDocumentDate();
//        return generateERPFile(finantialInstitution, beginDate, endDate, documents, false, false,
//                preProcessFunctionBeforeSerialize);
//    }

    private WorkDocument convertToSAFTWorkDocument(final Event document, BigDecimal amount, final boolean debtRegistration,
            final boolean debtExemption, final boolean debt, final boolean payment, final boolean creditNote,
            final boolean reimbursement, final boolean isAdvancedPayment, Person payer) throws Exception {

        WorkDocument workDocument = new WorkDocument();

        // MovementDate
        DatatypeFactory dataTypeFactory;

        dataTypeFactory = DatatypeFactory.newInstance();
        DateTime documentDate = document.getWhenOccured();
        if (!debtRegistration) {
            documentDate = new DateTime(2018, 01, 11, 0, 0);
        }
        //TODO meter a data em que é suposto pagar document.getDocumentDueDate()
        if (debtRegistration) {
            workDocument.setDueDate(convertToXMLDate(dataTypeFactory, document.getEventStateDate()));
        } else {
            workDocument.setDueDate(convertToXMLDate(dataTypeFactory, documentDate));
        }
        /* Anil: 14/06/2016: Fill with 0's the Hash element */
        workDocument.setHash(Strings.repeat("0", 172));
        // SystemEntryDate
        workDocument.setSystemEntryDate(convertToXMLDateTime(dataTypeFactory, documentDate));
        /* ANIL: 2015/10/20 converted from dateTime to Date */
        workDocument.setWorkDate(convertToXMLDate(dataTypeFactory, documentDate));
        // DocumentNumber
        String documentNumber = null;
        if (debtRegistration) {
            documentNumber = DEBT_REGISTER;
        } else if (debt) {
            documentNumber = DEBT;
        } else if (creditNote || isAdvancedPayment || reimbursement) {
            documentNumber = CREDIT_NOTE_NA;
        } else if (debtExemption) {
            documentNumber = DEBT_EXEMPTION;
        } else {
            throw new Exception("Tipo documento desconhecido");
        }

        workDocument.setDocumentNumber(documentNumber);
        // CustomerID
        workDocument.setCustomerID(ClientMap.uVATNumberFor(payer != null ? payer : document.getPerson()));

        //TODO check CloseDate()
        XMLGregorianCalendar certificationDate = null;
        if (debtRegistration || debtExemption) {
            certificationDate = convertToXMLDate(dataTypeFactory, documentDate);
        } else {
            if (document.getLastPaymentDate() != null) {
                certificationDate = convertToXMLDate(dataTypeFactory, document.getLastPaymentDate());
            }
        }
        workDocument.setCertificationDate(certificationDate);

        if (isAdvancedPayment) {
            AdvancedPayment advancedPayment = new AdvancedPayment();
            advancedPayment.setDescription("");
            advancedPayment.setOriginatingON(PAYMENT);
            workDocument.setAdvancedPayment(advancedPayment);
        }
        //PayorID
//            if (document.getPayorDebtAccount() != null && document.getPayorDebtAccount() != document.getDebtAccount()) {
//                workDocument.setPayorCustomerID(document.getPayorDebtAccount().getCustomer().getCode());
//            }

        // DocumentStatus
        /*
         * Deve ser preenchido com: 'N' ? Normal; Texto 1 'T' ? Por conta de
         * terceiros; 'A' ? Documento anulado.
         */
        SourceDocuments.WorkingDocuments.WorkDocument.DocumentStatus status =
                new SourceDocuments.WorkingDocuments.WorkDocument.DocumentStatus();
        if (document.isCancelled()) {
            status.setWorkStatus("A");
        } else {
            status.setWorkStatus(payer != null ? "T" : "N");
        }

        //TODO validar
        //Data da última gravação do estado do documento ao segundo. Tipo data e hora: “AAAA -MM -DDThh:mm:ss”
        status.setWorkStatusDate(convertToXMLDateTime(dataTypeFactory, new DateTime()));
        status.setSourceID("");
        // status.setReason("");
        // Deve ser preenchido com:
        // 'P' - Documento produzido na aplicacao;
        status.setSourceBilling(SAFTPTSourceBilling.P);

        workDocument.setDocumentStatus(status);

        // DocumentTotals
        SourceDocuments.WorkingDocuments.WorkDocument.DocumentTotals docTotals =
                new SourceDocuments.WorkingDocuments.WorkDocument.DocumentTotals();
        docTotals.setGrossTotal(amount.setScale(2, RoundingMode.HALF_EVEN));
        docTotals.setNetTotal(amount.setScale(2, RoundingMode.HALF_EVEN));
        docTotals.setTaxPayable(BigDecimal.ZERO
        /*document.getTotalAmount().subtract(document.getTotalNetAmount()).setScale(2, RoundingMode.HALF_EVEN)*/);

        workDocument.setDocumentTotals(docTotals);

        // WorkType
        /*
         * Deve ser preenchido com: Texto 2 "DC" — Documentos emitidos que
         * sejam suscetiveis de apresentacao ao cliente para conferencia de
         * entrega de mercadorias ou da prestacao de servicos. "FC" — Fatura
         * de consignacao nos termos do artigo 38º do codigo do IVA.
         */
        workDocument.setWorkType("DC");
        // Period
        /*
         * Período contabilístico (Period) . . . . . . . . . . Deve ser
         * indicado o número do mês do período de tributação, de '1' a '12',
         * contado desde a data do início. Pode ainda ser preenchido com
         * '13', '14', '15' ou '16' para movimentos efectuados no último mês
         * do período de tributação, relacionados com o apuramento do
         * resultado. Ex.: movimentos de apuramentos de inventários,
         * depreciaçẽes, ajustamentos ou apuramentos de resultados.
         */
        workDocument.setPeriod(documentDate.getMonthOfYear());
        // SourceID
        workDocument.setSourceID("");

        List<WorkDocument.Line> productLines = workDocument.getLine();
        WorkDocument.Line line = new Line();// convertToSAFTWorkDocumentLine(orderNoteLine, baseProducts);

        if (debtExemption || creditNote || reimbursement) {
            List<OrderReferences> orderReferences = line.getOrderReferences();
            OrderReferences reference = new OrderReferences();
            reference.setOriginatingON(WORKING_ORIGINATING_ON);
            reference.setLineNumber(BigInteger.ONE);
            orderReferences.add(reference);
        }

        if (debtRegistration || debt) {
            line.setDebitAmount(amount);
        }
        if (debtExemption || isAdvancedPayment || creditNote || reimbursement) {
            line.setCreditAmount(amount);
        }

        line.setDescription("Propinas 2º ciclo");
        line.setLineNumber(BigInteger.ONE);
        if (debtRegistration || debtExemption) {
            line.setProductCode("E0028");
            line.setProductDescription("ESP PROPINAS 2 CICLO");
            Metadata metada = new Metadata();
            metada.setDescription(
                    "{\"ANO_LECTIVO\":\"2017/2018\", \"CURSO\":\"MEGI\", \"START_DATE\":\"2017-09-05\", \"END_DATE\":\"2018-08-31\"}");
            line.setMetadata(metada);
        } else if (isAdvancedPayment) {
            line.setProductCode("0056");
            line.setProductDescription("Adiantamento de Pagamento");
        } else {
            line.setProductCode("0028");
            line.setProductDescription("PROPINAS 2 CICLO");
            Metadata metada = new Metadata();
            metada.setDescription("{\"COMPROMISSO\":\"986532145\"}");
            line.setMetadata(metada);
        }
        line.setQuantity(BigDecimal.ONE);
        if (debtRegistration) {
            line.setTaxPointDate(convertToXMLDate(dataTypeFactory, document.getWhenOccured()));
        } else {
            line.setTaxPointDate(convertToXMLDate(dataTypeFactory, documentDate));
        }
        Tax taxLine = new Tax();
        taxLine.setTaxPercentage(BigDecimal.ZERO);
        taxLine.setTaxCountryRegion("PT");
        taxLine.setTaxCode(VAT_TAX_CODE);
        taxLine.setTaxType("IVA");
        line.setTax(taxLine);
        line.setTaxExemptionReason("M99");
        line.setUnitOfMeasure("UNID");
        line.setUnitPrice(amount);
        line.setSettlementAmount(BigDecimal.ZERO);
        productLines.add(line);
        return workDocument;
    }

    private Payment convertToSAFTPaymentDocument(final Event document, final BigDecimal amount, final boolean debtRegistration,
            final boolean debtExemption, final boolean debt, final boolean isPayment, final boolean creditNote,
            final boolean reimbursement, final boolean advancedPayment, final boolean isDuplicate, Person payer)
            throws Exception {
        Payment payment = new Payment();

        // MovementDate TODO isto deve ter que receber a transaction
        DatatypeFactory dataTypeFactory;
        try {
            dataTypeFactory = DatatypeFactory.newInstance();
            final DateTime documentDate = new DateTime(2018, 01, 11, 02, 02);
            //TODO check payment date
            DateTime paymentDate = new DateTime(2018, 01, 11, 02, 02);//document.getLastPaymentDate();

            // SystemEntryDate
            payment.setSystemEntryDate(convertToXMLDateTime(dataTypeFactory, documentDate));

            payment.setTransactionDate(convertToXMLDate(dataTypeFactory, paymentDate));
            payment.setPaymentType(SAFTPTPaymentType.RG);
            String documentNumber = null;
            if (isPayment || creditNote || advancedPayment) {
                documentNumber = PAYMENT;
            } else if (reimbursement) {
                documentNumber = REIMBURSEMENT;
            } else {
                throw new Exception("Tipo documento não esperado nos pagamentos");
            }
            payment.setPaymentRefNo(documentNumber);

            // Finantial Transaction Reference
            //!Strings.isNullOrEmpty(document.getFinantialTransactionReference()) ? document.getFinantialTransactionReference() : ""
            payment.setFinantialTransactionReference("");

            //OriginDocumentNumber
            payment.setSourceID("");

            // CustomerID
            payment.setCustomerID(ClientMap.uVATNumberFor(payer != null ? payer : document.getPerson()));

            // DocumentStatus
            /*
             * Deve ser preenchido com: 'N' ? Normal; Texto 1 'T' ? Por conta de
             * terceiros; 'A' ? Documento anulado.
             */
            SourceDocuments.Payments.Payment.DocumentStatus status = new SourceDocuments.Payments.Payment.DocumentStatus();
            //TODO ou se foi fechado para dar origem a outra dívida
            if (document.isCancelled()) {
                status.setPaymentStatus("A");
            } else {
                status.setPaymentStatus(payer != null ? "T" : "N");
            }
            //TODO validar
            //Data da última gravação do estado do documento ao segundo. Tipo data e hora: “AAAA -MM -DDThh:mm:ss”
            status.setPaymentStatusDate(convertToXMLDateTime(dataTypeFactory, new DateTime()));
            status.setSourceID("");

            if (!Strings.isNullOrEmpty(document.getCancelJustification())) {//TODO ver com a questão do status, anulado ou não
//                status.setReason(document.getCancelJustification().substring(0, MAX_REASON));
            }

            status.setSourcePayment(SAFTPTSourcePayment.P);
            payment.setDocumentStatus(status);

            //PaymentMethods
            PaymentMethod method = new PaymentMethod();
            BigDecimal excessPayment = new BigDecimal(50);

            method.setPaymentAmount(amount.setScale(2, RoundingMode.HALF_EVEN));

            //TODO confirm this payment date
            method.setPaymentDate(dataTypeFactory.newXMLGregorianCalendarDate(paymentDate.getYear(), paymentDate.getMonthOfYear(),
                    paymentDate.getDayOfMonth(), DatatypeConstants.FIELD_UNDEFINED));
            //convertToXMLDate(dataTypeFactory, transaction.getWhenProcessed()));
            method.setPaymentMechanism("SI");
            payment.setSettlementType(SAFTPTSettlementType.NL);
            if (creditNote) {
                method.setPaymentMechanism("OU");
                method.setPaymentAmount(BigDecimal.ZERO);
                payment.setSettlementType(SAFTPTSettlementType.NN);
            } else if (reimbursement) {
                method.setPaymentMechanism("OU");
                method.setPaymentAmount(BigDecimal.ZERO);
                payment.setSettlementType(SAFTPTSettlementType.NR);

                ReimbursementProcess reimbursementProcess = new ReimbursementProcess();
                reimbursementProcess.setStatus(ReimbursementStatusType.PENDING);
                payment.setReimbursementProcess(reimbursementProcess);
            } else if (advancedPayment) {
                if (!isDuplicate) {
                    method.setPaymentAmount(amount.add(excessPayment));
                }
            }

            method.setPaymentMethodReference("paymentMethodReference"); //TODO código pagamento se for SIBS
            payment.getPaymentMethod().add(method);

            // DocumentTotals
            DocumentTotals docTotals = new DocumentTotals();

            String originatingOn = PAYMENT_ORIGINATING_ON;
            if (!(advancedPayment && isDuplicate)) {
                boolean isDebit = true;
                if (creditNote || reimbursement) {
                    isDebit = false;
                }
                SourceDocuments.Payments.Payment.Line line =
                        getLine(isDebit, dataTypeFactory, amount, BigInteger.ONE, originatingOn);
                payment.getLine().add(line);

                if (creditNote) {
                    line = getLine(creditNote, dataTypeFactory, amount, BigInteger.valueOf(2), PAYMENT_ND_ORIGINATING_ON);
                    payment.getLine().add(line);
                }
            }

            if (advancedPayment) {
                AdvancedPaymentCredit advancedPaymentCredit = new AdvancedPaymentCredit();
                advancedPaymentCredit.setOriginatingON(CREDIT_NOTE_NA);
                advancedPaymentCredit.setCreditAmount(excessPayment);
                payment.setAdvancedPaymentCredit(advancedPaymentCredit);
            }

// TODO para quando se quer "pagar" algo sem dar entrada de dinheir, por exemplo qd houve um adiantamento e há uma nova dívida onde se quer usar esse adiantamento            
//          PaymentMethod voidMethod = new PaymentMethod();
//          voidMethod.setPaymentAmount(BigDecimal.ZERO);
//
//          /* ANIL: 2015/10/20 converted from dateTime to Date */
//          voidMethod.setPaymentDate(convertToXMLDate(dataTypeFactory, calculatePaymentDate(document)));
//
//          voidMethod.setPaymentMechanism("OU");
//          voidMethod.setPaymentMethodReference("");
//
//          payment.getPaymentMethod().add(voidMethod);
//          payment.setSettlementType(SAFTPTSettlementType.NN);

            docTotals.setGrossTotal(BigDecimal.ZERO);
            docTotals.setNetTotal(BigDecimal.ZERO);
            docTotals.setTaxPayable(BigDecimal.ZERO);
            if (isPayment) {
                docTotals.setGrossTotal(amount);
                docTotals.setNetTotal(amount);
            }
            payment.setDocumentTotals(docTotals);

        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }
        return payment;
    }

    private SourceDocuments.Payments.Payment.Line getLine(final boolean isDebit, DatatypeFactory dataTypeFactory,
            BigDecimal amountToPay, BigInteger lineNumber, String originatingOn) {
        SourceDocuments.Payments.Payment.Line line = new SourceDocuments.Payments.Payment.Line();
        line.setLineNumber(lineNumber);
        //SourceDocument
        SourceDocumentID sourceDocument = new SourceDocumentID();
        sourceDocument.setLineNumber(lineNumber);
        sourceDocument.setOriginatingON(originatingOn);

        /* converted from dateTime to Date */
        sourceDocument.setInvoiceDate(convertToXMLDate(dataTypeFactory, new DateTime(2018, 1, 11, 05, 05)
        /*settlementEntry.getInvoiceEntry().getFinantialDocument().getDocumentDate()*/));

        sourceDocument.setDescription("PROPINA 2 CICLO"/*settlementEntry.getDescription()*/);
        line.getSourceDocumentID().add(sourceDocument);
        //SettlementAmount
        line.setSettlementAmount(BigDecimal.ZERO);

        if (isDebit) {
            line.setDebitAmount(amountToPay);
        } else {
            line.setCreditAmount(amountToPay);
        }
        return line;
    }

    public static XMLGregorianCalendar convertToXMLDate(DatatypeFactory dataTypeFactory, DateTime date) {
        return dataTypeFactory.newXMLGregorianCalendarDate(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth(),
                DatatypeConstants.FIELD_UNDEFINED);
    }

    private Customer convertCustomerToSAFTCustomer(final Person customer) {
        Customer c = new Customer();
        c.setDisable("");

        // AccountID
        /*
         * Deve ser indicada a respectiva conta corrente do cliente no plano de
         * contas da contabilidade, caso esteja definida. Caso contr?rio dever?
         * ser preenchido com a designação 'Desconhecido'.
         */

        c.setAccountID("STUDENT" /*"Desconhecido"/*customer.getCustomerAccountId()*/);
        c.setBillingAddress(convertAddressToSAFTAddress(customer));
        c.setCompanyName(customer.getName());
        c.setCustomerID(ClientMap.uVATNumberFor(customer));
        c.setCustomerTaxID(customer.getSocialSecurityNumber());
        // Email
        c.setEmail(customer.getDefaultEmailAddressValue());

        // SelfBillingIndicator
        /*
         * Indicador da existência de acordo de autofacturação entre o cliente e
         * o fornecedor. Deve ser preenchido com '1' se houver acordo e com '0' (zero) no caso contrário.
         */
        c.setSelfBillingIndicator(0);

        // Telephone
        c.setTelephone(customer.getDefaultMobilePhoneNumber());
        c.setFiscalCountry("PT"/*customer.getCountry().getName()*/);
        c.setNationality("PT"/*customer.getCountry().getCountryNationality().getContent(new Locale("pt"))*/);

        return c;
    }

    private AddressStructure convertAddressToSAFTAddress(final Person customer) {
        final AddressStructure companyAddress = new AddressStructure();

        companyAddress.setCountry(customer.getCountry() != null ? customer.getCountry().getCode() : MORADA_DESCONHECIDO);
        companyAddress
                .setAddressDetail(!Strings.isNullOrEmpty(customer.getAddress()) ? customer.getAddress() : MORADA_DESCONHECIDO);
        companyAddress.setCity(!Strings.isNullOrEmpty(customer.getDistrictSubdivisionOfResidence()) ? customer
                .getDistrictSubdivisionOfResidence() : MORADA_DESCONHECIDO);
        companyAddress.setPostalCode(!Strings.isNullOrEmpty(customer.getAreaCode()) ? customer.getAreaCode() : "0000-000");
        companyAddress.setRegion(!Strings.isNullOrEmpty(customer.getDistrictOfResidence()) ? customer
                .getDistrictOfResidence() : MORADA_DESCONHECIDO);
//        companyAddress.setStreetName(customer.getAddress());

        return companyAddress;
    }

    private XMLGregorianCalendar convertToXMLDateTime(DatatypeFactory dataTypeFactory, DateTime documentDate) {
        return dataTypeFactory.newXMLGregorianCalendar(documentDate.getYear(), documentDate.getMonthOfYear(),
                documentDate.getDayOfMonth(), documentDate.getHourOfDay(), documentDate.getMinuteOfHour(),
                documentDate.getSecondOfMinute(), 0, DatatypeConstants.FIELD_UNDEFINED);
    }

    private Line convertToSAFTWorkDocumentLine(Event event, Map<String, Product> baseProducts) {
        Product currentProduct = null;

//        FenixProduct product = event.getProduct();
//
//        if (product.getCode() != null && baseProducts.containsKey(product.getCode())) {
//            currentProduct = baseProducts.get(product.getCode());
//        } else {
//            currentProduct = convertProductToSAFTProduct(product);
//            baseProducts.put(currentProduct.getProductCode(), currentProduct);
//        }
//
//        XMLGregorianCalendar documentDateCalendar = null;
//        try {
//            DatatypeFactory dataTypeFactory = DatatypeFactory.newInstance();
//            DateTime documentDate = event.getFinantialDocument().getDocumentDate();
//
//            /* ANIL: 2015/10/20 converted from dateTime to Date */
//            documentDateCalendar = convertToXMLDate(dataTypeFactory, documentDate);
//
//        } catch (DatatypeConfigurationException e) {
//            e.printStackTrace();
//        }
//
//        Line line = new Line();
//
//        // Consider in replacing amount with net amount (check SAFT)
//        if (entry.isCreditNoteEntry()) {
//            line.setCreditAmount(event.getNetAmount().setScale(2, RoundingMode.HALF_EVEN));
//        } else if (entry.isDebitNoteEntry()) {
//            line.setDebitAmount(event.getNetAmount().setScale(2, RoundingMode.HALF_EVEN));
//        }
//
//        // If document was exported in legacy ERP than the amount is open amount when integration started
////        if (entry.getFinantialDocument().isExportedInLegacyERP()) {
////            if (entry.isCreditNoteEntry()) {
////                line.setCreditAmount(
////                        SAPExporterUtils.openAmountAtDate(entry, ERP_INTEGRATION_START_DATE).setScale(2, RoundingMode.HALF_EVEN));
////            } else if (entry.isDebitNoteEntry()) {
////                line.setDebitAmount(
////                        SAPExporterUtils.openAmountAtDate(entry, ERP_INTEGRATION_START_DATE).setScale(2, RoundingMode.HALF_EVEN));
////            }
////        }
//
//        // Description
//        line.setDescription(entry.getDescription());
//        List<OrderReferences> orderReferences = line.getOrderReferences();
//
//        //Add the references on the document creditEntries <-> debitEntries
//        if (entry.isCreditNoteEntry()) {
//            CreditEntry creditEntry = (CreditEntry) entry;
//            if (creditEntry.getDebitEntry() != null) {
//                //Metadata
//                Metadata metadata = new Metadata();
//                metadata.setDescription(creditEntry.getDebitEntry().getERPIntegrationMetadata());
//                line.setMetadata(metadata);
//
//                OrderReferences reference = new OrderReferences();
//
//                reference.setOriginatingON(creditEntry.getDebitEntry().getFinantialDocument().getUiDocumentNumber());
//                reference.setOrderDate(documentDateCalendar);
//
//                if (((DebitNote) creditEntry.getDebitEntry().getFinantialDocument()).isExportedInLegacyERP()) {
//                    final DebitNote debitNote = (DebitNote) creditEntry.getDebitEntry().getFinantialDocument();
//                    if (!Strings.isNullOrEmpty(debitNote.getLegacyERPCertificateDocumentReference())) {
//                        reference.setOriginatingON(debitNote.getLegacyERPCertificateDocumentReference());
//                    } else {
//                        reference.setOriginatingON("");
//                    }
//                }
//
//                reference.setLineNumber(BigInteger.ONE);
//
//                orderReferences.add(reference);
//            }
//
//        } else if (entry.isDebitNoteEntry()) {
//            DebitEntry debitEntry = (DebitEntry) entry;
//
//            Metadata metadata = new Metadata();
//            metadata.setDescription(debitEntry.getERPIntegrationMetadata());
//            line.setMetadata(metadata);
//        }
//
//        // ProductCode
//        line.setProductCode(currentProduct.getProductCode());
//        // ProductDescription
//        line.setProductDescription(currentProduct.getProductDescription());
//        // Quantity
//        line.setQuantity(BigDecimal.ONE);
//        // SettlementAmount
//        line.setSettlementAmount(BigDecimal.ZERO);
//        // Tax
//        line.setTax(getSAFTWorkingDocumentsTax(product, entry));
//        line.setTaxPointDate(documentDateCalendar);
//
//        // TaxExemptionReason
//        /*
//         * Motivo da isen??o de imposto (TaxExemptionReason). Campo de
//         * preenchimento obrigat?rio, quando os campos percentagem da taxa de
//         * imposto (TaxPercentage) ou montante do imposto (TaxAmount) s?o iguais
//         * a zero. Deve ser referido o preceito legal aplic?vel. . . . . . . . .
//         * . Texto 60
//         */
////        if (Constants.isEqual(line.getTax().getTaxPercentage(), BigDecimal.ZERO)
////                || (line.getTax().getTaxAmount() != null && Constants.isEqual(line.getTax().getTaxAmount(), BigDecimal.ZERO))) {
////            if (product.getVatExemptionReason() != null) {
////                line.setTaxExemptionReason(
////                        product.getVatExemptionReason().getCode() + "-" + product.getVatExemptionReason().getName().getContent());
////            } else {
////                // HACK : DEFAULT
////                line.setTaxExemptionReason(Constants.bundle("warning.ERPExporter.vat.exemption.unknown"));
////            }
////        }
//        // UnitOfMeasure
//        line.setUnitOfMeasure(product.getUnitOfMeasure().getContent());
//        // UnitPrice
//        line.setUnitPrice(entry.getAmount().setScale(2, RoundingMode.HALF_EVEN));
//
////        if (entry.getFinantialDocument().isExportedInLegacyERP()) {
////            line.setUnitPrice(
////                    Constants.divide(SAPExporterUtils.openAmountAtDate(entry, ERP_INTEGRATION_START_DATE), entry.getQuantity())
////                            .setScale(2, RoundingMode.HALF_EVEN));
////        }
//
//        return line;
        return null;
    }

    private Tax getSAFTWorkingDocumentsTax(Object /*FenixProduct*/ product, final Event event) {
        Tax tax = new Tax();
        // VatType vat = product.getVatType();
        // Tax-TaxCode
        tax.setTaxCode(VAT_TAX_CODE);
        tax.setTaxCountryRegion("PT");
        // Tax-TaxPercentage
        tax.setTaxPercentage(VAT_TAX);
        // Tax-TaxType
        tax.setTaxType("IVA");
        // TODO: Fill with vat amount
        //tax.setTaxAmount(entry.getVatAmount());
        return tax;
    }
}