package pt.ist.fenixedu.integration.task;

import org.fenixedu.bennu.scheduler.custom.CustomTask;

public class DumpConclusionGrades extends CustomTask {

    /**
     * Migration script, no longer applies to this model
     */
    @Override
    public void runTask() throws Exception {
        StringBuilder output = new StringBuilder();
//        for (ConclusionProcessVersion processVersion : Bennu.getInstance().getConclusionProcessVersionsSet()) {
//
//            Integer finalAverage = processVersion.getFinalAverage();
//            String finalGrade = Grade.createGrade(finalAverage.toString(), GradeScale.TYPE20).exportAsString();
//            String rawGrade = Grade.createGrade(processVersion.getAverage().toPlainString(), GradeScale.TYPE20).exportAsString();
//            String descriptiveGrade = null;
//
//            if (finalAverage <= 13) {
//                descriptiveGrade = Grade.createGrade("sufficient", GradeScale.TYPEAPT).exportAsString();
//            } else if (finalAverage <= 15) {
//                descriptiveGrade = Grade.createGrade("good", GradeScale.TYPEAPT).exportAsString();
//            } else if (finalAverage <= 17) {
//                descriptiveGrade = Grade.createGrade("verygood", GradeScale.TYPEAPT).exportAsString();
//            } else {
//                descriptiveGrade = Grade.createGrade("excelent", GradeScale.TYPEAPT).exportAsString();
//            }
//
//            out(output,
//                    "UPDATE CONCLUSION_PROCESS_VERSION set FINAL_GRADE = %s, RAW_GRADE= %s, DESCRIPTIVE_GRADE = %s WHERE OID = %s;",
//                    finalGrade, rawGrade, descriptiveGrade, processVersion.getExternalId());
//        }
    }

    private static void out(StringBuilder builder, String format, Object... args) {
        builder.append(String.format(format, args));
    }
}
