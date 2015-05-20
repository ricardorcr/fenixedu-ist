package pt.ist.fenixedu.integration.task.exportData.student;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fenixedu.academic.domain.EntryPhase;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixedu.integration.domain.student.importation.DegreeCandidateDTO;
import pt.ist.fenixedu.integration.domain.student.importation.DgesStudentImportationProcess;

public class MoreThanOneCandidacy1stTimeStudents extends CustomTask {

    @Override
    public void runTask() throws Exception {

        ExecutionYear curreExecutionYear = ExecutionYear.readCurrentExecutionYear();

        for (DgesStudentImportationProcess importationProcess : DgesStudentImportationProcess.readDoneJobs(curreExecutionYear)) {
            if (!importationProcess.getEntryPhase().equals(EntryPhase.FIRST_PHASE)) {
                continue;
            }

            Set<Person> personSet = new HashSet<Person>();
            final List<DegreeCandidateDTO> degreeCandidateDTOs =
                    parseDgesFile(importationProcess.getDgesStudentImportationFile().getContent(), "",
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

                if (person.getCandidaciesSet().size() > 1) {
                    taskLog("User %s has more than one candidacy\n", person.getUsername());
                }
            }
        }
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

}
