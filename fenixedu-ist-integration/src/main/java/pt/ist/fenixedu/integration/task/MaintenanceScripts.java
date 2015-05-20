package pt.ist.fenixedu.integration.task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.fenixedu.academic.domain.CompetenceCourse;
import org.fenixedu.academic.domain.Degree;
import org.fenixedu.academic.domain.DegreeCurricularPlan;
import org.fenixedu.academic.domain.DegreeCurricularPlanEquivalencePlan;
import org.fenixedu.academic.domain.Department;
import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.Teacher;
import org.fenixedu.academic.domain.TeacherCategory;
import org.fenixedu.academic.domain.accounting.PaymentCode;
import org.fenixedu.academic.domain.accounting.PaymentCodeMapping;
import org.fenixedu.academic.domain.accounting.PaymentCodeState;
import org.fenixedu.academic.domain.accounting.PaymentCodeType;
import org.fenixedu.academic.domain.accounting.events.AnnualEvent;
import org.fenixedu.academic.domain.accounting.events.gratuity.GratuityEventWithPaymentPlan;
import org.fenixedu.academic.domain.accounting.paymentCodes.IndividualCandidacyPaymentCode;
import org.fenixedu.academic.domain.accounting.paymentPlans.FullGratuityPaymentPlan;
import org.fenixedu.academic.domain.accounting.paymentPlans.GratuityPaymentPlan;
import org.fenixedu.academic.domain.accounting.serviceAgreementTemplates.DegreeCurricularPlanServiceAgreementTemplate;
import org.fenixedu.academic.domain.candidacyProcess.mobility.MobilityApplicationProcess;
import org.fenixedu.academic.domain.candidacyProcess.mobility.MobilityCoordinator;
import org.fenixedu.academic.domain.degree.DegreeType;
import org.fenixedu.academic.domain.degree.degreeCurricularPlan.DegreeCurricularPlanState;
import org.fenixedu.academic.domain.degreeStructure.CycleCourseGroup;
import org.fenixedu.academic.domain.phd.PhdProgram;
import org.fenixedu.academic.domain.phd.PhdProgramFocusArea;
import org.fenixedu.academic.domain.reports.GepReportFile;
import org.fenixedu.academic.util.Money;
import org.fenixedu.academic.util.MultiLanguageString;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.core.security.Authenticate;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import pt.ist.fenixedu.quc.domain.InquiryQuestion;
import pt.ist.fenixframework.FenixFramework;

public class MaintenanceScripts extends CustomTask {

    @Override
    public void runTask() throws Exception {
        // TODO Auto-generated method stub

    }

    public void changePhdProgramName() {
        PhdProgram pdhProgram = FenixFramework.getDomainObject("4604204947708");
        MultiLanguageString newName =
                new MultiLanguageString(MultiLanguageString.pt, "Biotecnologia e Biociências").with(MultiLanguageString.en,
                        "Biotechnology and Biosciences");
        pdhProgram.setName(newName);
    }

    private static String[] FOCUS_AREAS_TO_BE_ACTIVE = new String[] { "4771708666064" };

    public void setPhdFocusProgramsActive() {

        List<PhdProgramFocusArea> focusAreasToActivate = new ArrayList<PhdProgramFocusArea>();
        for (String focusAreaOID : FOCUS_AREAS_TO_BE_ACTIVE) {
            focusAreasToActivate.add(FenixFramework.getDomainObject(focusAreaOID));
        }

        for (PhdProgramFocusArea focusArea : Bennu.getInstance().getPhdProgramFocusAreasSet()) {
            if (focusAreasToActivate.contains(focusArea)) {
                focusArea.setActive(true);
            } else {
                focusArea.setActive(false);
            }
        }
    }

    public void mappingPaymentCodes() throws Exception {
        PaymentCode oldCode = FenixFramework.getDomainObject("845172254451632");
        PaymentCode newCode = FenixFramework.getDomainObject("845172254451704");
        PaymentCodeMapping.create(ExecutionYear.readCurrentExecutionYear(), oldCode, newCode);
    }

    public void updateInquiryQuestionToolTip() {
        InquiryQuestion inquiryQuestion = FenixFramework.getDomainObject("5927054870681");

        MultiLanguageString mls =
                new MultiLanguageString(
                        MultiLanguageString.pt,
                        "O resultado corresponde à média das medianas dos resultados correspondentes a todas as questões das secções "
                                + "\"Proveito da aprendizagem presencial\", \"Capacidade pedagógica\" e \"Interacção com os alunos\" do inquérito aos alunos.")
                        .with(MultiLanguageString.en,
                                "The result corresponds to the average of the medians of the results regarding all the questions from sections "
                                        + "\"Classroom learning benefit\", \"Pedagogical skills\" and \"Students interaction\"");
        inquiryQuestion.setToolTip(mls);
    }

    public void setDegreeCurricularPlanSource() {
        new DegreeCurricularPlanEquivalencePlan(FenixFramework.getDomainObject("284056252055554"),
                FenixFramework.getDomainObject("2581275345328"));
    }

    public void setGratuityPlans() {
        int count = 0;
        ExecutionYear currentExecutionYear = ExecutionYear.readCurrentExecutionYear();
        for (AnnualEvent annualEvent : currentExecutionYear.getAnnualEventsSet()) {
            if (annualEvent instanceof GratuityEventWithPaymentPlan) {
                GratuityEventWithPaymentPlan eventWithPaymentPlan = (GratuityEventWithPaymentPlan) annualEvent;
                if (eventWithPaymentPlan.getGratuityPaymentPlan() == null) {
                    GratuityPaymentPlan paymentPlanFor =
                            eventWithPaymentPlan.getDegreeCurricularPlanServiceAgreement().getGratuityPaymentPlanFor(
                                    eventWithPaymentPlan.getStudentCurricularPlan(), eventWithPaymentPlan.getExecutionYear());
                    if (paymentPlanFor != null) {
                        eventWithPaymentPlan.setGratuityPaymentPlan(paymentPlanFor);
                        taskLog("Setting payment plan for %s %s\n", eventWithPaymentPlan.getExternalId(), eventWithPaymentPlan
                                .getPerson().getUsername());
                        count++;
                    }
                }
            }
        }
        taskLog(count + " Gratuity events set with the correspondent playment plan");
    }

    public void addAffinityCycles() throws Exception {
        DegreeCurricularPlan dcpLee = FenixFramework.getDomainObject("2581275345460"); // LEE 2006
        DegreeCurricularPlan dcpLerc = FenixFramework.getDomainObject("2581275345458"); // LERC 2006
        DegreeCurricularPlan dcpMeec = FenixFramework.getDomainObject("2581275345334"); // MEEC 2006
        DegreeCurricularPlan dcpMeicA = FenixFramework.getDomainObject("284056252055554"); // MEIC-A 2015
        DegreeCurricularPlan dcpMeicT = FenixFramework.getDomainObject("284056252055555"); // MEIC-T 2015

        //LEE 2006 -> MEIC-A 2015 MEIC-T 2015
        dcpLee.getFirstCycleCourseGroup().addDestinationAffinities(dcpMeicA.getSecondCycleCourseGroup());
        dcpLee.getFirstCycleCourseGroup().addDestinationAffinities(dcpMeicT.getSecondCycleCourseGroup());

        //LERC 2006 -> MEIC-A 2015 MEIC-T 2015
        dcpLerc.getFirstCycleCourseGroup().addDestinationAffinities(dcpMeicA.getSecondCycleCourseGroup());
        dcpLerc.getFirstCycleCourseGroup().addDestinationAffinities(dcpMeicT.getSecondCycleCourseGroup());

        //MEEC 2006 -> MEIC-A 2015 MEIC-T 2015
        dcpMeec.getFirstCycleCourseGroup().addDestinationAffinities(dcpMeicA.getSecondCycleCourseGroup());
        dcpMeec.getFirstCycleCourseGroup().addDestinationAffinities(dcpMeicT.getSecondCycleCourseGroup());

        printAffinities();
    }

    private void printAffinities() {
        for (final DegreeCurricularPlan degreeCurricularPlan : DegreeCurricularPlan.readByDegreeTypesAndState(
                DegreeType.oneOf(DegreeType::isBolonhaDegree, DegreeType::isIntegratedMasterDegree),
                DegreeCurricularPlanState.ACTIVE)) {

            print(degreeCurricularPlan.getFirstCycleCourseGroup());
        }
    }

    private void print(final CycleCourseGroup firstCycleCourseGroup) {
        final StringBuilder builder = new StringBuilder();
        builder.append(firstCycleCourseGroup.getParentDegreeCurricularPlan().getName()).append("\t -> ");
        final Iterator<CycleCourseGroup> iter = firstCycleCourseGroup.getDestinationAffinitiesSet().iterator();
        while (iter.hasNext()) {
            builder.append(iter.next().getParentDegreeCurricularPlan().getName());
            if (iter.hasNext()) {
                builder.append(", ");
            }
        }
        taskLog(builder.toString());
    }

    /**
     * the students payed 100€ with the SIBS code, and then payed another 100€ using the same code
     * the system is not prepared to process the same code twice
     */
    public void reProcessCandidacyPaymentCodes() {
        PaymentCode firstCode = FenixFramework.getDomainObject("849299718014546"); //842111905 Ricardo Vieira Ribeiro
        firstCode.setState(PaymentCodeState.NEW);
        firstCode.process(Authenticate.getUser().getPerson(), new Money(100.0), new DateTime(2015, 8, 14, 1, 51), "00000", null);

        PaymentCode secondCode = FenixFramework.getDomainObject("849299718014682"); //843471912 Nuno Queiroz Barroso Colaço Ramos
        secondCode.setState(PaymentCodeState.NEW);
        secondCode
                .process(Authenticate.getUser().getPerson(), new Money(100.0), new DateTime(2015, 8, 10, 14, 19), "00000", null);
    }

    public void createServiceAgreementTemplatePaymentPlan() {
        ExecutionYear executionYear = FenixFramework.getDomainObject("2443836393482"); // 2012/2013
        DegreeCurricularPlanServiceAgreementTemplate serviceAgreementTemplate = FenixFramework.getDomainObject("2289217570092"); //DEAEAmb
        new FullGratuityPaymentPlan(executionYear, serviceAgreementTemplate, true);
    }

    public void createPaymentCodesForCandidacy() {
        LocalDate beginDate = new LocalDate(2015, 07, 01);
        LocalDate endDate = new LocalDate(2015, 07, 31);
        int numberOfPaymentCodes = 100;
        IndividualCandidacyPaymentCode.createPaymentCodes(PaymentCodeType.PHD_PROGRAM_CANDIDACY_PROCESS, beginDate, endDate,
                new Money("100.00"), new Money("100.00"), numberOfPaymentCodes);
    }

    public void cancelPaymentCodes() {
        LocalDate beginDate = new LocalDate(2015, 06, 22);
        for (PaymentCode paymentCode : Bennu.getInstance().getPaymentCodesSet()) {
            if (paymentCode.getStartDate().isEqual(beginDate)) {
                paymentCode.cancel();
            }
        }
    }

    private void executionYearProfessorships() {
        StringBuilder executionCourseTeachersReport = new StringBuilder();
        executionCourseTeachersReport.append("Código Disciplina Execução").append("\t").append("Código Professorship")
                .append("\t").append("Semestre").append("\t").append("Responsável").append("\t").append("Ist Username")
                .append("\t").append("Nome").append("\t").append("Categoria").append("\t").append("Departamento").append("\n");
        ExecutionYear executionYear = ExecutionYear.readCurrentExecutionYear().getPreviousExecutionYear();
        for (ExecutionSemester executionSemester : executionYear.getExecutionPeriodsSet()) {
            for (ExecutionCourse executionCourse : executionSemester.getAssociatedExecutionCoursesSet()) {
                for (Professorship professorship : executionCourse.getProfessorshipsSet()) {
                    executionCourseTeachersReport.append(GepReportFile.getExecutionCourseCode(executionCourse)).append("\t");
                    executionCourseTeachersReport.append(GepReportFile.getProfessorshipCode(professorship)).append("\t");
                    executionCourseTeachersReport.append(executionSemester.getName()).append("\t");
                    executionCourseTeachersReport.append(professorship.isResponsibleFor()).append("\t");
                    executionCourseTeachersReport.append(professorship.getPerson().getUsername()).append("\t");
                    executionCourseTeachersReport.append(professorship.getPerson().getName()).append("\t");
                    TeacherCategory category = professorship.getPerson().getTeacher().getCategory();
                    executionCourseTeachersReport.append(category != null ? category.getName().getContent() : "").append("\t");
                    Optional<Department> department =
                            professorship.getPerson().getTeacher().getDepartment(executionSemester.getAcademicInterval());
                    executionCourseTeachersReport.append(department.isPresent() ? department.get().getName() : "").append("\n");
                }
            }
        }
        output("Corpo_docente_disciplinas_2014_2015.csv", executionCourseTeachersReport.toString().getBytes());
    }

    private void conversionTableCompetenceCourse() {
        StringBuilder competenceReport = new StringBuilder();
        for (CompetenceCourse competenceCourse : Bennu.getInstance().getCompetenceCoursesSet()) {
            competenceReport.append(competenceCourse.getExternalId()).append("\t")
                    .append(GepReportFile.getCompetenceCourseCode(competenceCourse)).append("\n");
        }
        output("Tabela_conversao_competencias.csv", competenceReport.toString().getBytes());
    }

    private void createMobilityCoordinators() {
        MobilityApplicationProcess applicationProcess = FenixFramework.getDomainObject("849694855004163");

        Teacher teacher = FenixFramework.getDomainObject("450971566514"); //ist12325
        Degree degree = FenixFramework.getDomainObject("2761663971466"); //civil
        new MobilityCoordinator(applicationProcess, teacher, degree);

        teacher = FenixFramework.getDomainObject("450971566443"); //ist12091
        degree = FenixFramework.getDomainObject("2761663971471"); //fisica
        new MobilityCoordinator(applicationProcess, teacher, degree);
    }
}
