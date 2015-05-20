package pt.ist.fenixedu.integration.task.exportData.student;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fenixedu.academic.domain.EntryPhase;
import org.fenixedu.academic.domain.ExecutionDegree;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.person.RoleType;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;
import org.fenixedu.spaces.domain.Space;

import pt.ist.fenixedu.integration.domain.student.importation.DegreeCandidateDTO;
import pt.ist.fenixedu.integration.domain.student.importation.DgesStudentImportationProcess;

public class ExportExistingStudentsFromDgesImportation extends CustomTask {

    @Override
    public void runTask() throws Exception {

        ExecutionYear curreExecutionYear = ExecutionYear.readCurrentExecutionYear();

        for (DgesStudentImportationProcess importationProcess : DgesStudentImportationProcess.readDoneJobs(curreExecutionYear)) {
            if (!importationProcess.getEntryPhase().equals(EntryPhase.SECOND_PHASE)) {
                continue;
            }

            Set<Person> personSet = new HashSet<Person>();
            final Spreadsheet spreadsheet = new Spreadsheet("Students");
            spreadsheet
                    .setHeaders(new String[] { "Número de Aluno", "Nome", "BI", "Curso", "Ano", "Campus", "Username", "Email" });
            String universityAcronym =
                    "ALAMEDA".equals(importationProcess.getDgesStudentImportationForCampus().getName()) ? "A" : "T";

            final List<DegreeCandidateDTO> degreeCandidateDTOs =
                    parseDgesFile(importationProcess.getDgesStudentImportationFile().getContent(), universityAcronym,
                            importationProcess.getEntryPhase());

            for (DegreeCandidateDTO dto : degreeCandidateDTOs) {
                Person person = null;
                try {
                    person = dto.getMatchingPerson();
                } catch (DegreeCandidateDTO.NotFoundPersonException e) {
                    continue;
                } catch (DegreeCandidateDTO.TooManyMatchedPersonsException e) {
                    continue;
                } catch (DegreeCandidateDTO.MatchingPersonException e) {
                    throw new RuntimeException(e);
                }

                if (personSet.contains(person)) {
                    continue;
                }

                if ((person.getStudent() != null && !person.getStudent().getRegistrationsSet().isEmpty())
                        || person.getTeacher() != null || RoleType.TEACHER.isMember(person.getUser())) {
                    addRow(spreadsheet, person.getStudent().getNumber().toString(), person.getName(),
                            person.getDocumentIdNumber(),
                            dto.getExecutionDegree(curreExecutionYear, importationProcess.getDgesStudentImportationForCampus()),
                            curreExecutionYear, importationProcess.getDgesStudentImportationForCampus(), person.getUsername(),
                            person.getEmailForSendingEmails());

                    personSet.add(person);
                    continue;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            spreadsheet.exportToCSV(baos, "\t");

            output(String.format("%s_existing_students_%s_%s.csv", importationProcess.getDgesStudentImportationForCampus()
                    .getName(), importationProcess.getEntryPhase(), curreExecutionYear.getName().replace("/", "_")),
                    baos.toByteArray());
        }
    }

    private void addRow(final Spreadsheet spreadsheet, final String studentNumber, String studentName, String documentIdNumber,
            final ExecutionDegree executionDegree, final ExecutionYear executionYear, final Space campus, String username,
            String email) {
        final Row row = spreadsheet.addRow();

        row.setCell(0, studentNumber);
        row.setCell(1, studentName);
        row.setCell(2, documentIdNumber);
        row.setCell(3, executionDegree.getDegreeCurricularPlan().getName());
        row.setCell(4, executionYear.getYear());
        row.setCell(5, campus.getName());
        row.setCell(6, username);
        row.setCell(7, email);
    }

    protected List<DegreeCandidateDTO> parseDgesFile(byte[] contents, String university, EntryPhase entryPhase) {

        final List<DegreeCandidateDTO> result = new ArrayList<DegreeCandidateDTO>();
        String[] lines = readContent(contents);
        for (String dataLine : lines) {
            DegreeCandidateDTO dto = new DegreeCandidateDTO();
            if (dto.fillWithFileLineData(dataLine)) {
                result.add(dto);
            }
        }
        setConstantFields(university, entryPhase, result);
        return result;

    }

    public String[] readContent(byte[] contents) {
        try {
            String fileContents = new String(contents, "UTF-8");
            return fileContents.split("\n");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void setConstantFields(String university, EntryPhase entryPhase, final Collection<DegreeCandidateDTO> result) {
        for (final DegreeCandidateDTO degreeCandidateDTO : result) {
            degreeCandidateDTO.setIstUniversity(university);
            degreeCandidateDTO.setEntryPhase(entryPhase);
        }
    }
}
