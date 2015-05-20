/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST Integration.
 *
 * FenixEdu IST Integration is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Integration is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Integration.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.integration.task.updateData.enrolment;

import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.academic.domain.student.Student;
import org.fenixedu.academic.domain.student.registrationStates.RegistrationState;
import org.fenixedu.academic.domain.student.registrationStates.RegistrationStateType;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.DateTime;

public class CorrectFlunkedState extends CustomTask {

    private static final String[] FLUNKED_STUDENTS_TO_CORRECT = new String[] { "40492", "42423", "44672", "50045", "51913",
            "53311", "55774", "56563", "57073", "57789", "58976", "66244", "67033", "68642", "69504", "69653", "69814", "69855",
            "69955", "70101", "70151", "70681", "70716", "72621", "73713", "73757", "74042", "74137", "74139", "74169", "74266",
            "74328" };
    static int count = 0;

    @Override
    public void runTask() throws Exception {
        for (int iter = 0; iter < FLUNKED_STUDENTS_TO_CORRECT.length; iter++) {

            final Student student = Student.readStudentByNumber(Integer.valueOf(FLUNKED_STUDENTS_TO_CORRECT[iter]));
            if (student == null) {
                taskLog("Can't find student -> " + FLUNKED_STUDENTS_TO_CORRECT[iter]);
                continue;
            }

            processStudent(student);
        }
        taskLog("Modified: " + count);
    }

    private void processStudent(final Student student) {
        taskLog("Process Student -> " + student.getNumber());

        final Registration registration = getRegistrationWithFlunkedState(student);
        if (registration == null) {
            taskLog("\t- student is not in flunked state");
            return;
        }

        if (registration.getActiveStateType() != RegistrationStateType.REGISTERED) {
            RegistrationState registrationState =
                    RegistrationState.createRegistrationState(registration, null, new DateTime(),
                            RegistrationStateType.REGISTERED);
            registrationState.setRemarks("Prescrição levantada");
            taskLog("\t student modified");
        }
        count++;

        taskLog("*************************************");
    }

    private Registration getRegistrationWithFlunkedState(final Student student) {
        Registration result = null;

        for (final Registration registration : student.getRegistrationsSet()) {
            if (registration.isBolonha() && registration.getActiveStateType() == RegistrationStateType.FLUNKED) {
                if (result == null) {
                    result = registration;
                } else {
                    taskLog("Student " + student.getNumber() + " has more than one flunked registrations");
                    throw new RuntimeException();
                }
            }
        }
        return result;
    }
}
