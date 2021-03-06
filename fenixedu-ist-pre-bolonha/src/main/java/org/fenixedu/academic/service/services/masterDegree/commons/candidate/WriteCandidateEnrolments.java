/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST Pre Bolonha.
 *
 * FenixEdu IST Pre Bolonha is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Pre Bolonha is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Pre Bolonha.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fenixedu.academic.service.services.masterDegree.commons.candidate;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.Transformer;
import org.fenixedu.academic.domain.CandidateEnrolment;
import org.fenixedu.academic.domain.CurricularCourse;
import org.fenixedu.academic.domain.MasterDegreeCandidate;
import org.fenixedu.academic.service.services.exceptions.FenixServiceException;
import org.fenixedu.academic.service.services.exceptions.NonExistingServiceException;
import org.fenixedu.academic.service.services.exceptions.NotAuthorizedException;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.FenixFramework;

public class WriteCandidateEnrolments {

    protected void run(Set<String> selectedCurricularCoursesIDs, String candidateID, Double credits, String givenCreditsRemarks)
            throws FenixServiceException {

        MasterDegreeCandidate masterDegreeCandidate = FenixFramework.getDomainObject(candidateID);
        if (masterDegreeCandidate == null) {
            throw new NonExistingServiceException();
        }

        masterDegreeCandidate.setGivenCredits(credits);

        if (credits.floatValue() != 0) {
            masterDegreeCandidate.setGivenCreditsRemarks(givenCreditsRemarks);
        }

        Collection<CandidateEnrolment> candidateEnrolments = masterDegreeCandidate.getCandidateEnrolmentsSet();
        List<String> candidateEnrolmentsCurricularCoursesIDs =
                (List<String>) CollectionUtils.collect(candidateEnrolments, new Transformer() {
                    @Override
                    public Object transform(Object arg0) {
                        CandidateEnrolment candidateEnrolment = (CandidateEnrolment) arg0;
                        return candidateEnrolment.getCurricularCourse().getExternalId();
                    }
                });

        Collection<String> curricularCoursesToEnroll =
                CollectionUtils.subtract(selectedCurricularCoursesIDs, candidateEnrolmentsCurricularCoursesIDs);

        final Collection<Integer> curricularCoursesToDelete =
                CollectionUtils.subtract(candidateEnrolmentsCurricularCoursesIDs, selectedCurricularCoursesIDs);

        Collection<CandidateEnrolment> candidateEnrollmentsToDelete =
                CollectionUtils.select(candidateEnrolments, new Predicate() {
                    @Override
                    public boolean evaluate(Object arg0) {
                        CandidateEnrolment candidateEnrolment = (CandidateEnrolment) arg0;
                        return (curricularCoursesToDelete.contains(candidateEnrolment.getCurricularCourse().getExternalId()));
                    }
                });

        writeFilteredEnrollments(masterDegreeCandidate, curricularCoursesToEnroll);

        for (CandidateEnrolment candidateEnrolmentToDelete : candidateEnrollmentsToDelete) {
            candidateEnrolmentToDelete.delete();
        }
    }

    /**
     * @param persistentSupport
     * @param masterDegreeCandidate
     * @param curricularCoursesToEnroll
     * @throws NonExistingServiceException
     * @throws ExcepcaoPersistencia
     */
    private void writeFilteredEnrollments(MasterDegreeCandidate masterDegreeCandidate,
            Collection<String> curricularCoursesToEnroll) throws NonExistingServiceException {
        Iterator<String> iterCurricularCourseIds = curricularCoursesToEnroll.iterator();
        while (iterCurricularCourseIds.hasNext()) {

            CurricularCourse curricularCourse = (CurricularCourse) FenixFramework.getDomainObject(iterCurricularCourseIds.next());

            if (curricularCourse == null) {
                throw new NonExistingServiceException();
            }

            CandidateEnrolment candidateEnrolment = new CandidateEnrolment();

            masterDegreeCandidate.addCandidateEnrolments(candidateEnrolment);
            candidateEnrolment.setCurricularCourse(curricularCourse);
        }
    }

    // Service Invokers migrated from Berserk

    private static final WriteCandidateEnrolments serviceInstance = new WriteCandidateEnrolments();

    @Atomic
    public static void runWriteCandidateEnrolments(Set<String> selectedCurricularCoursesIDs, String candidateID, Double credits,
            String givenCreditsRemarks) throws FenixServiceException, NotAuthorizedException {
        serviceInstance.run(selectedCurricularCoursesIDs, candidateID, credits, givenCreditsRemarks);
    }

}