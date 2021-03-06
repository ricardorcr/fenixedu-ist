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
package org.fenixedu.academic.dto;

import java.util.ArrayList;

import org.fenixedu.academic.domain.CandidateSituation;
import org.fenixedu.academic.domain.MasterDegreeCandidate;
import org.fenixedu.academic.util.State;

/**
 * @author Fernanda Quitério Created on 1/Jul/2004
 * 
 */
public class InfoMasterDegreeCandidateWithInfoPerson extends InfoMasterDegreeCandidate {

    @Override
    public void copyFromDomain(MasterDegreeCandidate masterDegreeCandidate) {
        super.copyFromDomain(masterDegreeCandidate);
        if (masterDegreeCandidate != null) {
            setInfoPerson(InfoPerson.newInfoFromDomain(masterDegreeCandidate.getPerson()));
            setAverage(masterDegreeCandidate.getAverage());
            setCandidateNumber(masterDegreeCandidate.getCandidateNumber());
            setGivenCredits(masterDegreeCandidate.getGivenCredits());
            setGivenCreditsRemarks(masterDegreeCandidate.getGivenCreditsRemarks());
            setMajorDegree(masterDegreeCandidate.getMajorDegree());
            setMajorDegreeSchool(masterDegreeCandidate.getMajorDegreeSchool());
            setMajorDegreeYear(masterDegreeCandidate.getMajorDegreeYear());
            setSpecializationArea(masterDegreeCandidate.getSpecializationArea());
            setSubstituteOrder(masterDegreeCandidate.getSubstituteOrder());
            setSituationList(new ArrayList<InfoCandidateSituation>(masterDegreeCandidate.getSituationsSet().size()));

            for (CandidateSituation candidateSituation : masterDegreeCandidate.getSituationsSet()) {
                getSituationList().add(InfoCandidateSituation.newInfoFromDomain(candidateSituation));

                if (candidateSituation.getValidation().equals(new State(State.ACTIVE))) {
                    setInfoCandidateSituation(InfoCandidateSituation.newInfoFromDomain(candidateSituation));
                }
            }

        }
    }

    public static InfoMasterDegreeCandidate newInfoFromDomain(MasterDegreeCandidate masterDegreeCandidate) {
        InfoMasterDegreeCandidateWithInfoPerson infoMasterDegreeCandidateWithInfoPerson = null;
        if (masterDegreeCandidate != null) {
            infoMasterDegreeCandidateWithInfoPerson = new InfoMasterDegreeCandidateWithInfoPerson();
            infoMasterDegreeCandidateWithInfoPerson.copyFromDomain(masterDegreeCandidate);
        }
        return infoMasterDegreeCandidateWithInfoPerson;
    }

}