package pt.ist.fenixedu.integration.task;

import java.io.InputStreamReader;
import java.util.concurrent.Callable;

import org.apache.commons.lang.StringUtils;
import org.fenixedu.academic.domain.DegreeCurricularPlan;
import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionDegree;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.ShiftType;
import org.fenixedu.academic.domain.exceptions.DomainException;
import org.fenixedu.academic.domain.reports.GepReportFile;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.io.domain.GroupBasedFile;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.DateTime;

import pt.ist.fenixedu.quc.domain.InquiryConnectionType;
import pt.ist.fenixedu.quc.domain.InquiryQuestion;
import pt.ist.fenixedu.quc.domain.InquiryResult;
import pt.ist.fenixedu.quc.domain.InquiryResultType;
import pt.ist.fenixedu.quc.domain.ResultClassification;
import pt.ist.fenixframework.Atomic.TxMode;
import pt.ist.fenixframework.FenixFramework;

import com.google.common.io.CharStreams;

public class ImportQucResults extends CustomTask {

    @Override
    public TxMode getTxMode() {
        return TxMode.READ;
    }

    @Override
    public void runTask() throws Exception {
        GroupBasedFile qucResults = FenixFramework.getDomainObject("1407993358846747");
        String stringResults = CharStreams.toString(new InputStreamReader(qucResults.getStream()));

        DateTime resultDate = new DateTime(2015, 9, 21, 14, 15);
        String[] allRows = stringResults.split("\r\n");
        int numberOfRowsToRead = 250;
        String[] rows = new String[numberOfRowsToRead];
        for (int iter = 0, cycleCount = 0; iter < allRows.length; iter++, cycleCount++) {
            if (iter == 0) {
                continue;
            }
            rows[cycleCount] = allRows[iter];
            if (cycleCount == numberOfRowsToRead - 1) {

                ImportResults writeRows = new ImportResults(rows, resultDate);
                writeRows.start();
                try {
                    writeRows.join();
                } catch (InterruptedException e) {
                    if (writeRows.domainException != null) {
                        throw writeRows.domainException;
                    }
                    throw new Error(e);
                }
                cycleCount = 0;
                rows = new String[numberOfRowsToRead];
            }
        }
        ImportResults writeRows = new ImportResults(rows, resultDate);
        writeRows.start();
        try {
            writeRows.join();
        } catch (InterruptedException e) {
            if (writeRows.domainException != null) {
                throw writeRows.domainException;
            }
            throw new Error(e);
        }
    }

    private static class ImportResults extends Thread {
        String[] rows;
        DateTime resultDate;
        DomainException domainException;

        public ImportResults(String[] rows, DateTime resultDate) {
            this.rows = rows;
            this.resultDate = resultDate;
        }

        @Override
        public void run() {
            try {
                FenixFramework.getTransactionManager().withTransaction(new Callable<Void>() {

                    @Override
                    public Void call() {
                        importRows(rows, resultDate);
                        return null;
                    }

                });
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    private static void importRows(String[] rows, DateTime resultDate) {
        for (String row : rows) {
            if (row != null) {
                String[] columns = row.split("\t");
                //TODO rever indices das colunas
                //columns[columns.length - 1] = columns[columns.length - 1].split("\r")[0];
                //meter aqui algumas validações
                //se vier com valor + classificação dá erro

                InquiryResult inquiryResult = new InquiryResult();
                inquiryResult.setResultDate(resultDate);

                setConnectionType(columns, inquiryResult);
                setClassification(columns, inquiryResult);
                setInquiryRelation(columns, inquiryResult);
                setExecutionSemester(columns, inquiryResult);
                setResultType(columns, inquiryResult);
                setValue(columns, inquiryResult);
            }
        }
    }

    private static void setValue(String[] columns, InquiryResult inquiryResult) {
        String value = columns[5] != null ? columns[5].replace(",", ".") : columns[5];
        String scaleValue = columns[6];
        inquiryResult.setValue(value);
        inquiryResult.setScaleValue(scaleValue);
    }

    private static void setConnectionType(String[] columns, InquiryResult inquiryResult) {
        String connectionTypeString = columns[10];
        if (StringUtils.isEmpty(connectionTypeString)) {
            throw new DomainException("connectionType: " + getPrintableColumns(columns));
        }
        InquiryConnectionType connectionType = InquiryConnectionType.valueOf(connectionTypeString);
        inquiryResult.setConnectionType(connectionType);
    }

    private static void setResultType(String[] columns, InquiryResult inquiryResult) {
        String resultTypeString = columns[1];
        if (!StringUtils.isEmpty(resultTypeString)) {
            InquiryResultType inquiryResultType = InquiryResultType.valueOf(resultTypeString);
            if (inquiryResultType == null) {
                throw new DomainException("resultType: " + getPrintableColumns(columns));
            }
            inquiryResult.setResultType(inquiryResultType);
        }
    }

    private static void setClassification(String[] columns, InquiryResult inquiryResult) {
        String resultClassificationString = columns[4];
        if (!StringUtils.isEmpty(resultClassificationString)) {
            ResultClassification classification = ResultClassification.valueOf(resultClassificationString);
            if (classification == null) {
                throw new DomainException("classification: " + getPrintableColumns(columns));
            }
            inquiryResult.setResultClassification(classification);
        }
    }

    private static void setExecutionSemester(String[] columns, InquiryResult inquiryResult) {
        String executionPeriodCode = columns[3];
        ExecutionSemester executionSemester = getExecutionSemester(executionPeriodCode);
        if (executionSemester == null) {
            throw new DomainException("executionPeriod: " + getPrintableColumns(columns));
        }
        inquiryResult.setExecutionPeriod(executionSemester);
    }

    /**
     * Receives a unique code that identifies a ExecutionSemester and returns the domain object
     * 
     * @param code - the semester plus the year
     * @return the correspondent ExecutionSemester
     */
    private static ExecutionSemester getExecutionSemester(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        return ExecutionSemester.readBySemesterAndExecutionYear(Integer.valueOf(decodedParts[0]), decodedParts[1]);
    }

    /**
     * Receives a unique code that identifies a ExecutionCourse and returns the domain object
     * 
     * @param code - the execution course sigla plus the execution semester code
     * @return the correspondent ExecutionCourse
     */
    private static ExecutionCourse getExecutionCourse(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        return ExecutionCourse.readBySiglaAndExecutionPeriod(decodedParts[0], getExecutionSemester(decodedParts[1]
                + GepReportFile.CODE_SEPARATOR + decodedParts[2]));
    }

    /**
     * Receives a unique code that identifies a ExecutionDegree and returns the domain object
     * 
     * @param code - the code of the degree curricular plan plus the execution year code
     * @return the correspondent ExecutionDegree
     */
    private static ExecutionDegree getExecutionDegree(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        DegreeCurricularPlan dcp = getDegreeCurricularPlan(decodedParts[0] + GepReportFile.CODE_SEPARATOR + decodedParts[1]);
        ExecutionYear executionYear = getExecutionYear(decodedParts[2]);
        return ExecutionDegree.getByDegreeCurricularPlanAndExecutionYear(dcp, executionYear);
    }

    /**
     * Receives a unique code that identifies a DegreeCurricularPlan and returns the domain object
     * 
     * @param code - the name of the curricular plan plus the degree sigla
     * @return the correspondent DegreeCurricularPlan
     */
    private static DegreeCurricularPlan getDegreeCurricularPlan(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        return DegreeCurricularPlan.readByNameAndDegreeSigla(decodedParts[0], decodedParts[1]);
    }

    private static ExecutionYear getExecutionYear(String code) {
        return ExecutionYear.readExecutionYearByName(code);
    }

    /**
     * Receives a unique code that identifies a Professorship and returns the domain object
     * 
     * @param code - the person username plus the execution course code
     * @return the correspondent Professorship
     */
    private static Professorship getProfessorship(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        ExecutionCourse executionCourse =
                getExecutionCourse(decodedParts[1] + GepReportFile.CODE_SEPARATOR + decodedParts[2]
                        + GepReportFile.CODE_SEPARATOR + decodedParts[3]);
        return executionCourse.getProfessorship(Person.findByUsername(decodedParts[0]));
    }

    /*
     * OID_EXECUTION_DEGREE RESULT_TYPE OID_EXECUTION_COURSE OID_EXECUTION_PERIOD RESULT_CLASSIFICATION VALUE_ SCALE_VALUE
     * OID_INQUIRY_QUESTION OID_PROFESSORSHIP SHIFT_TYPE CONNECTION_TYPE
     */
    private static void setInquiryRelation(String[] columns, InquiryResult inquiryResult) {
        String inquiryQuestionCode = columns[7];
        String executionCourseCode = columns[2];
        String executionDegreeCode = columns[0];
        String professorshipCode = columns[8];
        String shiftTypeString = columns[9];
        ExecutionCourse executionCourse =
                !StringUtils.isEmpty(executionCourseCode) ? getExecutionCourse(executionCourseCode) : null;
        ExecutionDegree executionDegree =
                !StringUtils.isEmpty(executionDegreeCode) ? getExecutionDegree(executionDegreeCode) : null;
        Professorship professorship =
                !StringUtils.isEmpty(professorshipCode) ? (Professorship) getProfessorship(professorshipCode) : null;
        ShiftType shiftType = !StringUtils.isEmpty(shiftTypeString) ? ShiftType.valueOf(shiftTypeString) : null;
        inquiryResult.setExecutionCourse(executionCourse);
        inquiryResult.setExecutionDegree(executionDegree);
        inquiryResult.setProfessorship(professorship);
        inquiryResult.setShiftType(shiftType);

        if (!(StringUtils.isEmpty(inquiryQuestionCode) && ResultClassification.GREY.equals(inquiryResult
                .getResultClassification()))) {
            InquiryQuestion inquiryQuestion = getInquiryQuestion(Long.valueOf(inquiryQuestionCode));
            if (inquiryQuestion == null) {
                throw new DomainException("não tem question: " + getPrintableColumns(columns));
            }
            inquiryResult.setInquiryQuestion(inquiryQuestion);
        }
    }

    private static InquiryQuestion getInquiryQuestion(Long inquiryQuestionCode) {
        for (InquiryQuestion inquiryQuestion : Bennu.getInstance().getInquiryQuestionsSet()) {
            if (inquiryQuestion.getCode().equals(inquiryQuestionCode)) {
                return inquiryQuestion;
            }
        }
        return null;
    }

    private static String getPrintableColumns(String[] columns) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String value : columns) {
            stringBuilder.append(value).append("\t");
        }
        return stringBuilder.toString();
    }

}
