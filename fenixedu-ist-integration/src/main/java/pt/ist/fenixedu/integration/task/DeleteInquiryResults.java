package pt.ist.fenixedu.integration.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.exceptions.DomainException;
import org.fenixedu.academic.domain.reports.GepReportFile;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixedu.quc.domain.InquiryResult;
import pt.ist.fenixedu.quc.domain.InquiryResultComment;
import pt.ist.fenixframework.Atomic.TxMode;
import pt.ist.fenixframework.FenixFramework;

import com.google.common.collect.Lists;

public class DeleteInquiryResults extends CustomTask {

    static int count = 0;

    @Override
    public TxMode getTxMode() {
        return TxMode.READ;
    }

    @Override
    public void runTask() throws Exception {
        StringBuffer deletedComments = new StringBuffer();
        ExecutionSemester previousExecutionPeriod = ExecutionSemester.readActualExecutionSemester().getPreviousExecutionPeriod();
        List<List<InquiryResult>> partition =
                Lists.partition(new ArrayList(previousExecutionPeriod.getInquiryResultsSet()), 5000);

        for (List<InquiryResult> list : partition) {

            DeleteResults deleteResults = new DeleteResults(list, deletedComments);
            deleteResults.start();
            try {
                deleteResults.join();
            } catch (InterruptedException e) {
                if (deleteResults.domainException != null) {
                    throw deleteResults.domainException;
                }
                throw new Error(e);
            }
        }

        taskLog(String.valueOf(count));
        output("deleted_comments.csv", deletedComments.toString().getBytes());
    }

    private static class DeleteResults extends Thread {
        List<InquiryResult> toDelete;
        StringBuffer deletedComments;
        DomainException domainException;

        public DeleteResults(List<InquiryResult> toDelete, StringBuffer deletedComments) {
            this.toDelete = toDelete;
            this.deletedComments = deletedComments;
        }

        @Override
        public void run() {
            try {
                FenixFramework.getTransactionManager().withTransaction(new Callable<Void>() {

                    @Override
                    public Void call() throws Exception {
                        for (InquiryResult inquiryResult : toDelete) {
                            try {
                                inquiryResult.getInquiryResultCommentsSet().stream().forEach(inquiryComment -> {
                                    exportInquiryComment(inquiryComment);
                                    count++;
                                    inquiryComment.delete();
                                });
                                inquiryResult.delete();
                            } catch (DomainException e) {
                                domainException = e;
                                throw e;
                            }
                        }
                        return null;
                    }
                });
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

        protected void exportInquiryComment(InquiryResultComment inquiryComment) {
            InquiryResult inquiryResult = inquiryComment.getInquiryResult();

            String executionDegreeCode =
                    inquiryResult.getExecutionDegree() != null ? GepReportFile.getExecutionDegreeCode(inquiryResult
                            .getExecutionDegree()) : "";
            deletedComments.append(executionDegreeCode).append("\t");

            String resultType = inquiryResult.getResultType() != null ? inquiryResult.getResultType().toString() : "";
            deletedComments.append(resultType).append("\t");

            String executionCourseCode =
                    inquiryResult.getExecutionCourse() != null ? GepReportFile.getExecutionCourseCode(inquiryResult
                            .getExecutionCourse()) : "";
            deletedComments.append(executionCourseCode).append("\t");

            String executionPeriodCode =
                    inquiryResult.getExecutionPeriod() != null ? GepReportFile.getExecutionSemesterCode(inquiryResult
                            .getExecutionPeriod()) : "";
            deletedComments.append(executionPeriodCode).append("\t");

            String resultClassificationString =
                    inquiryResult.getResultClassification() != null ? inquiryResult.getResultClassification().toString() : "";
            deletedComments.append(resultClassificationString).append("\t");

            String value = inquiryResult.getValue();
            deletedComments.append(value).append("\t");

            String scale = inquiryResult.getScaleValue();
            deletedComments.append(scale).append("\t");

            String inquiryQuestionCode = inquiryResult.getInquiryQuestion().getCode().toString();
            deletedComments.append(inquiryQuestionCode).append("\t");

            String professorshipCode =
                    inquiryResult.getProfessorship() != null ? GepReportFile.getProfessorshipCode(inquiryResult
                            .getProfessorship()) : "";
            deletedComments.append(professorshipCode).append("\t");

            String shiftType = inquiryResult.getShiftType() != null ? inquiryResult.getShiftType().toString() : "";
            deletedComments.append(shiftType).append("\t");

            String connectionTypeString = inquiryResult.getConnectionType().toString();
            deletedComments.append(connectionTypeString).append("\t");

            /* ------------------------- Comment data ------------------------- */
            deletedComments.append(inquiryComment.getPerson().getUsername()).append("\t");
            deletedComments.append(inquiryComment.getPersonCategory().toString()).append("\t");
            deletedComments.append(inquiryComment.getResultOrder()).append("\t");
            deletedComments.append(inquiryComment.getComment().replaceAll("(\\r|\\n|\\r\\n)+", "#n")).append("\n");
        }
    }

}
