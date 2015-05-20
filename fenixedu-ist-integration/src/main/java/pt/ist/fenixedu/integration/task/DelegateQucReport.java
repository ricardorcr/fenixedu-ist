package pt.ist.fenixedu.integration.task;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.fenixedu.academic.domain.Degree;
import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionDegree;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.degree.DegreeType;
import org.fenixedu.academic.domain.student.Student;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;

import pt.ist.fenixedu.delegates.domain.student.Delegate;
import pt.ist.fenixedu.delegates.domain.student.YearDelegate;
import pt.ist.fenixedu.quc.domain.DelegateInquiryTemplate;
import pt.ist.fenixedu.quc.domain.InquiryDelegateAnswer;
import pt.ist.fenixedu.quc.domain.InquiryTemplate;
import pt.ist.fenixedu.quc.util.DelegateUtils;

public class DelegateQucReport extends CustomTask {

    @Override
    public void runTask() throws Exception {

        ExecutionYear previousExecutionYear = ExecutionYear.readCurrentExecutionYear().getPreviousExecutionYear();
        ExecutionSemester previousExecutionPeriod = ExecutionSemester.readActualExecutionSemester().getPreviousExecutionPeriod();
        final List<Delegate> yearDelegates = new ArrayList<Delegate>();
        List<Degree> degreeList =
                Degree.readAllMatching(DegreeType.oneOf(DegreeType::isBolonhaDegree, DegreeType::isIntegratedMasterDegree,
                        DegreeType::isBolonhaMasterDegree));

        for (Degree degree : degreeList) {
            Map<Integer, YearDelegate> yearDelegateByYear = new HashMap<Integer, YearDelegate>();
            for (Student student : DelegateUtils
                    .getAllDelegatesByExecutionYearAndFunctionType(degree, previousExecutionYear, true).stream()
                    .map(d -> d.getUser().getPerson().getStudent()).collect(Collectors.toList())) {
                YearDelegate yearDelegate = getYearDelegate(student, previousExecutionYear);
                YearDelegate yearDelegateMap = yearDelegateByYear.get(yearDelegate.getCurricularYear().getYear());
                if (yearDelegateMap == null) {
                    yearDelegateByYear.put(yearDelegate.getCurricularYear().getYear(), yearDelegate);
                } else {
                    if (yearDelegate.isAfter(yearDelegateMap)) {
                        yearDelegateByYear.put(yearDelegate.getCurricularYear().getYear(), yearDelegate);
                    }
                }
            }
            yearDelegates.addAll(yearDelegateByYear.values());
        }

        final Spreadsheet spreadsheet = new Spreadsheet("Delegados em falta");
        spreadsheet.setHeader("IstID");
        spreadsheet.setHeader("Nome");
        spreadsheet.setHeader("Curso");
        spreadsheet.setHeader("Ano");
        spreadsheet.setHeader("Perguntas por responder");
        spreadsheet.setHeader("Email");
        spreadsheet.setHeader("Telefone");
        yearDelegates
                .stream()
                .filter(delegate -> DelegateInquiryTemplate
                        .hasInquiriesToAnswer((YearDelegate) delegate, previousExecutionPeriod)
                        || getNumberOfNotAnsweredQuestions((YearDelegate) delegate, previousExecutionPeriod) > 0)
                .forEach(
                        delegate -> {
                            final Row row = spreadsheet.addRow();
                            row.setCell(delegate.getUser().getUsername());
                            row.setCell(delegate.getUser().getProfile().getFullName());
                            row.setCell(delegate.getDegree().getDegreeType().getName().getContent() + " - "
                                    + delegate.getDegree().getNameFor(previousExecutionYear.getAcademicInterval()).getContent());
                            row.setCell(delegate.getCurricularYear().getYear());
                            row.setCell(getNumberOfNotAnsweredQuestions((YearDelegate) delegate, previousExecutionPeriod));
                            row.setCell(delegate.getUser().getProfile().getEmail());
                            row.setCell(delegate.getUser().getPerson().getDefaultMobilePhone() != null ? delegate.getUser()
                                    .getPerson().getDefaultMobilePhone().getNumber() : "-");
                        });

        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        spreadsheet.exportToXLSSheet(byteArrayOS);

        output("delegados_por_responder.xls", byteArrayOS.toByteArray());
    }

    private YearDelegate getYearDelegate(Student student, ExecutionYear executionYear) {
        YearDelegate yearDelegate = null;
        for (Delegate delegate : student.getPerson().getUser().getDelegatesSet()) {
            if (delegate instanceof YearDelegate) {
                if (DelegateUtils.DelegateIsActiveForFirstExecutionYear(delegate, executionYear)) {
                    if (yearDelegate == null || delegate.getEnd().isAfter(yearDelegate.getEnd())) {
                        yearDelegate = (YearDelegate) delegate;
                    }
                }
            }
        }
        return yearDelegate;
    }

    private int getNumberOfNotAnsweredQuestions(YearDelegate yearDelegate, ExecutionSemester executionSemester) {
        InquiryTemplate delegateInquiryTemplate = DelegateInquiryTemplate.getDelegateInquiryTemplate(executionSemester);

        final ExecutionDegree executionDegree =
                yearDelegate.getDegree().getExecutionDegreesForExecutionYear(executionSemester.getExecutionYear()).stream()
                        .findFirst().orElse(null);
        Set<ExecutionCourse> executionCoursesToInquiries =
                DelegateUtils.getExecutionCoursesToInquiries(yearDelegate, executionSemester, executionDegree);
        List<InquiryDelegateAnswer> inquiryDelegateAnswers =
                yearDelegate.getInquiryDelegateAnswersSet().stream()
                        .filter(inquiryAnswer -> inquiryAnswer.getExecutionCourse().getExecutionPeriod() == executionSemester)
                        .collect(Collectors.toList());
        int numberOfNotAnsweredQuestions =
                (executionCoursesToInquiries.size() - inquiryDelegateAnswers.size())
                        * delegateInquiryTemplate.getNumberOfQuestions();
        int numberOfInquiryQuestions = delegateInquiryTemplate.getNumberOfQuestions();
        for (InquiryDelegateAnswer inquiryAnswer : inquiryDelegateAnswers) {
            numberOfNotAnsweredQuestions += numberOfInquiryQuestions - inquiryAnswer.getNumberOfAnsweredQuestions();
        }
        return numberOfNotAnsweredQuestions;
    }

}
