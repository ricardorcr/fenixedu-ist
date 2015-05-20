package pt.ist.fenixedu.integration.task;

import java.io.InputStreamReader;
import java.io.Serializable;

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

public class UpdateQucResults extends CustomTask {

    @Override
    public TxMode getTxMode() {
        return TxMode.READ;
    }

    @Override
    public void runTask() throws Exception {
        GroupBasedFile qucResults = FenixFramework.getDomainObject("1970943312265367");
        String stringResults = CharStreams.toString(new InputStreamReader(qucResults.getStream()));

        DateTime resultDate = new DateTime(2015, 9, 24, 14, 15);
        String[] allRows = stringResults.split("\r\n");
        int numberOfRowsToRead = 100;
        String[] rows = new String[numberOfRowsToRead];
        for (int iter = 0, cycleCount = 0; iter < allRows.length; iter++, cycleCount++) {
            if (iter == 0) {
                continue;
            }
            rows[cycleCount] = allRows[iter];
            if (cycleCount == numberOfRowsToRead - 1) {

                taskLog("Vou fazer updates %s\n", iter);
                final String[] rowsToUpdate = rows;
                FenixFramework.atomic(() -> updateRows(rowsToUpdate, resultDate));
                cycleCount = 0;
                rows = new String[numberOfRowsToRead];
            }
        }
        final String[] rowsToUpdate = rows;
        FenixFramework.atomic(() -> updateRows(rowsToUpdate, resultDate));
    }

    private static void updateRows(String[] rows, DateTime resultDate) {
        for (String row : rows) {
            if (row != null) {
                String[] columns = row.split("\t");
                InquiryResultBean inquiryResultBean = new InquiryResultBean(columns);
                InquiryResult inquiryResult = getInquiryResult(inquiryResultBean);
                if (inquiryResult != null) {
                    inquiryResult.setValue(inquiryResultBean.getValue());
                    inquiryResult.setResultClassification(inquiryResultBean.getResultClassification());
                } else {
                    throw new DomainException("result not found: " + getPrintableColumns(columns));
                }
            }
        }
    }

    private static InquiryResult getInquiryResult(InquiryResultBean inquiryResultBean) {
        for (InquiryResult inquiryResult : inquiryResultBean.getExecutionSemester().getInquiryResultsSet()) {
            if (inquiryResult.getInquiryQuestion() == inquiryResultBean.getInquiryQuestion()
                    && inquiryResult.getExecutionCourse() == inquiryResultBean.getExecutionCourse()
                    && inquiryResult.getExecutionDegree() == inquiryResultBean.getExecutionDegree()
                    && inquiryResult.getProfessorship() == inquiryResultBean.getProfessorship()
                    && inquiryResult.getResultType() == inquiryResultBean.getResultType()
                    && inquiryResult.getShiftType() == inquiryResultBean.getShiftType()
                    && inquiryResult.getScaleValue().equalsIgnoreCase(inquiryResultBean.getScaleValue())
                    && inquiryResult.getConnectionType() == inquiryResultBean.getConnectionType()) {
                return inquiryResult;
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

    private static class InquiryResultBean implements Serializable {

        private static final long serialVersionUID = 1L;
        private String value;
        private String scaleValue;
        private InquiryConnectionType connectionType;
        private InquiryResultType resultType;
        private ResultClassification resultClassification;
        private ExecutionSemester executionSemester;
        private ExecutionDegree executionDegree;
        private ExecutionCourse executionCourse;
        private InquiryQuestion inquiryQuestion;
        private Professorship professorship;
        private ShiftType shiftType;

        public InquiryResultBean(String[] row) {

            //TODO rever indices das colunas
            //columns[columns.length - 1] = columns[columns.length - 1].split("\r")[0];
            //meter aqui algumas validações
            //se vier com valor + classificação dá erro
            String executionDegreeCode = row[0];
            ExecutionDegree executionDegree =
                    !StringUtils.isEmpty(executionDegreeCode) ? getExecutionDegreeByCode(executionDegreeCode) : null;
            setExecutionDegree(executionDegree);

            String resultTypeString = row[1];
            if (!StringUtils.isEmpty(resultTypeString)) {
                InquiryResultType inquiryResultType = InquiryResultType.valueOf(resultTypeString);
                if (inquiryResultType == null) {
                    throw new DomainException("resultType doesn't exists: " + getPrintableColumns(row));
                }
                setResultType(inquiryResultType);
            }

            String executionCourseCode = row[2];
            ExecutionCourse executionCourse =
                    !StringUtils.isEmpty(executionCourseCode) ? getExecutionCourseByCode(executionCourseCode) : null;
            setExecutionCourse(executionCourse);

            String executionPeriodCode = row[3];
            ExecutionSemester executionSemester = getExecutionSemesterByCode(executionPeriodCode);
            if (executionSemester == null) {
                throw new DomainException("executionPeriod resultType doesn't exists: " + getPrintableColumns(row));
            }
            setExecutionSemester(executionSemester);

            String resultClassificationString = row[4];
            if (!StringUtils.isEmpty(resultClassificationString)) {
                ResultClassification classification = ResultClassification.valueOf(resultClassificationString);
                if (classification == null) {
                    throw new DomainException("classification doesn't exists: : " + getPrintableColumns(row));
                }
                setResultClassification(classification);
            }

            String value = row[5] != null ? row[5].replace(",", ".") : row[5];
            String scaleValue = row[6];
            setValue(value);
            setScaleValue(scaleValue);

            String inquiryQuestionCode = row[7];
            if (!(StringUtils.isEmpty(inquiryQuestionCode) && ResultClassification.GREY.equals(getResultClassification()))) {
                InquiryQuestion inquiryQuestion = getInquiryQuestionByCode(Long.valueOf(inquiryQuestionCode));
                if (inquiryQuestion == null) {
                    throw new DomainException("não tem question: " + getPrintableColumns(row));
                }
                setInquiryQuestion(inquiryQuestion);
            }

            String professorshipCode = row[8];
            Professorship professorship =
                    !StringUtils.isEmpty(professorshipCode) ? getProfessorshipByCode(professorshipCode) : null;
            setProfessorship(professorship);

            String shiftTypeString = row[9];
            ShiftType shiftType = !StringUtils.isEmpty(shiftTypeString) ? ShiftType.valueOf(shiftTypeString) : null;
            setShiftType(shiftType);

            String connectionTypeString = row[10];
            if (StringUtils.isEmpty(connectionTypeString)) {
                throw new DomainException("connectionType doesn't exists: " + getPrintableColumns(row));
            }
            InquiryConnectionType connectionType = InquiryConnectionType.valueOf(connectionTypeString);
            setConnectionType(connectionType);
        }

        private String getPrintableColumns(String[] columns) {
            StringBuilder stringBuilder = new StringBuilder();
            for (String value : columns) {
                stringBuilder.append(value).append("\t");
            }
            return stringBuilder.toString();
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getScaleValue() {
            return scaleValue;
        }

        public void setScaleValue(String scaleValue) {
            this.scaleValue = scaleValue;
        }

        public InquiryConnectionType getConnectionType() {
            return connectionType;
        }

        public void setConnectionType(InquiryConnectionType connectionType) {
            this.connectionType = connectionType;
        }

        public InquiryResultType getResultType() {
            return resultType;
        }

        public void setResultType(InquiryResultType resultType) {
            this.resultType = resultType;
        }

        public ResultClassification getResultClassification() {
            return resultClassification;
        }

        public void setResultClassification(ResultClassification resultClassification) {
            this.resultClassification = resultClassification;
        }

        public ExecutionSemester getExecutionSemester() {
            return executionSemester;
        }

        public void setExecutionSemester(ExecutionSemester executionSemester) {
            this.executionSemester = executionSemester;
        }

        public ExecutionDegree getExecutionDegree() {
            return executionDegree;
        }

        public void setExecutionDegree(ExecutionDegree executionDegree) {
            this.executionDegree = executionDegree;
        }

        public ExecutionCourse getExecutionCourse() {
            return executionCourse;
        }

        public void setExecutionCourse(ExecutionCourse executionCourse) {
            this.executionCourse = executionCourse;
        }

        public InquiryQuestion getInquiryQuestion() {
            return inquiryQuestion;
        }

        public void setInquiryQuestion(InquiryQuestion inquiryQuestion) {
            this.inquiryQuestion = inquiryQuestion;
        }

        public Professorship getProfessorship() {
            return professorship;
        }

        public void setProfessorship(Professorship professorship) {
            this.professorship = professorship;
        }

        public ShiftType getShiftType() {
            return shiftType;
        }

        public void setShiftType(ShiftType shiftType) {
            this.shiftType = shiftType;
        }

    }

    /**
     * Receives a unique code that identifies a ExecutionSemester and returns the domain object
     * 
     * @param code - the semester plus the year
     * @return the correspondent ExecutionSemester
     */
    private static ExecutionSemester getExecutionSemesterByCode(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        return ExecutionSemester.readBySemesterAndExecutionYear(Integer.valueOf(decodedParts[0]), decodedParts[1]);
    }

    /**
     * Receives a unique code that identifies a ExecutionCourse and returns the domain object
     * 
     * @param code - the execution course sigla plus the execution semester code
     * @return the correspondent ExecutionCourse
     */
    private static ExecutionCourse getExecutionCourseByCode(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        return ExecutionCourse.readBySiglaAndExecutionPeriod(decodedParts[0], getExecutionSemesterByCode(decodedParts[1]
                + GepReportFile.CODE_SEPARATOR + decodedParts[2]));
    }

    /**
     * Receives a unique code that identifies a ExecutionDegree and returns the domain object
     * 
     * @param code - the code of the degree curricular plan plus the execution year code
     * @return the correspondent ExecutionDegree
     */
    private static ExecutionDegree getExecutionDegreeByCode(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        DegreeCurricularPlan dcp =
                getDegreeCurricularPlanByCode(decodedParts[0] + GepReportFile.CODE_SEPARATOR + decodedParts[1]);
        ExecutionYear executionYear = getExecutionYear(decodedParts[2]);
        return ExecutionDegree.getByDegreeCurricularPlanAndExecutionYear(dcp, executionYear);
    }

    /**
     * Receives a unique code that identifies a DegreeCurricularPlan and returns the domain object
     * 
     * @param code - the name of the curricular plan plus the degree sigla
     * @return the correspondent DegreeCurricularPlan
     */
    private static DegreeCurricularPlan getDegreeCurricularPlanByCode(String code) {
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
    private static Professorship getProfessorshipByCode(String code) {
        String[] decodedParts = code.split(GepReportFile.CODE_SEPARATOR);
        ExecutionCourse executionCourse =
                getExecutionCourseByCode(decodedParts[1] + GepReportFile.CODE_SEPARATOR + decodedParts[2]
                        + GepReportFile.CODE_SEPARATOR + decodedParts[3]);
        return executionCourse.getProfessorship(Person.findByUsername(decodedParts[0]));
    }

    private static InquiryQuestion getInquiryQuestionByCode(Long inquiryQuestionCode) {
        for (InquiryQuestion inquiryQuestion : Bennu.getInstance().getInquiryQuestionsSet()) {
            if (inquiryQuestion.getCode().equals(inquiryQuestionCode)) {
                return inquiryQuestion;
            }
        }
        return null;
    }

}
