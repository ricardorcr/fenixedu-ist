package pt.ist.fenixedu.integration.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.fenixedu.academic.domain.Enrolment;
import org.fenixedu.academic.util.ConnectionManager;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixframework.FenixFramework;

public class GenerateEnrolmentsFinalGrades extends CustomTask {

    @Override
    public void runTask() throws Exception {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        int filePage = 22;
        Enrolment enrolment = null;
        for (String enrolmentOID : getEnrolmentsExternalIds()) {
            try {
                enrolment = FenixFramework.getDomainObject(enrolmentOID);
                sb.append(enrolment.getExternalId()).append("\t").append(enrolment.getFinalGrade()).append("\n");
                count++;
                if (count % 5000 == 0) {
                    taskLog("Já vou com %s enrolments processados\n", count);
                }
                if (count % 25000 == 0) {
                    sb.append(enrolment.getExternalId()).append("\t").append(enrolment.getFinalGrade()).append("\n");
                    output("notas_enrolments" + filePage + ".csv", sb.toString().getBytes());
                    sb = new StringBuilder();
                    filePage++;
                }
                if (count == 500000) {
                    taskLog("O último enrolment foi o: %s\n", enrolmentOID);
                    break;
                }
            } catch (Exception e) {
                taskLog("Erro no enrolment %s\n", enrolment.getExternalId());
                sb.append("erro").append("\n");
                e.printStackTrace();
            }
        }
        output("notas_enrolments" + filePage + ".csv", sb.toString().getBytes());
    }

    private List<String> getEnrolmentsExternalIds() {
        try {
            Connection connection = ConnectionManager.getCurrentSQLConnection();

            PreparedStatement prepareStatement =
                    connection
                            .prepareStatement("select OID from CURRICULUM_MODULE where ((OID >> 32) & 0xFFFF) = 332 and OID > 1425930344776 order by OID;");
            ResultSet executeQuery = prepareStatement.executeQuery();// and OID > 1425930344776

            List<String> result = new ArrayList<String>();
            while (executeQuery.next()) {
                result.add(executeQuery.getString(1));
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
