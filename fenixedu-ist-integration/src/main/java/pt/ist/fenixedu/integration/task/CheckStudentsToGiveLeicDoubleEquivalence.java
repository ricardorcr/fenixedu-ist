package pt.ist.fenixedu.integration.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.fenixedu.academic.domain.CurricularCourse;
import org.fenixedu.academic.domain.DegreeCurricularPlan;
import org.fenixedu.academic.domain.Enrolment;
import org.fenixedu.academic.domain.StudentCurricularPlan;
import org.fenixedu.academic.domain.studentCurriculum.Credits;
import org.fenixedu.academic.domain.studentCurriculum.CurriculumLine;
import org.fenixedu.academic.domain.studentCurriculum.EnrolmentWrapper;
import org.fenixedu.academic.domain.studentCurriculum.InternalEnrolmentWrapper;
import org.fenixedu.academic.domain.studentCurriculum.InternalSubstitution;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixframework.FenixFramework;

public class CheckStudentsToGiveLeicDoubleEquivalence extends CustomTask {

    CurricularCourse sourceDigitalSystems = FenixFramework.getDomainObject("1529008374041");
    CurricularCourse sourceComputerArchitecture = FenixFramework.getDomainObject("1529008374042");

    CurricularCourse destinationComputerArchitectureIntroduction = FenixFramework.getDomainObject("1529008522543");
    CurricularCourse destinationComputerOrganization = FenixFramework.getDomainObject("1529008522544");
    List<CurricularCourse> enrolled = new ArrayList<CurricularCourse>();

    {
        enrolled.add(sourceComputerArchitecture);
        enrolled.add(sourceDigitalSystems);
    }

    @Override
    public void runTask() throws Exception {
        DegreeCurricularPlan dcp = FenixFramework.getDomainObject("2581275345327"); //LEIC A
        for (StudentCurricularPlan scp : dcp.getActiveStudentCurricularPlans()) {
            if (!alreadyHasEquivalence(scp)) {
                if (getApprovedCourses(scp.getApprovedCurriculumLines()).size() > 1) {
                    taskLog("Vou dar a equivalência ao aluno %s\n", scp.getRegistration().getStudent().getPerson().getUsername());
                }
            }
        }
    }

    private boolean alreadyHasEquivalence(StudentCurricularPlan scp) {
        List<CurricularCourse> givenEquivalences = new ArrayList<CurricularCourse>();
        for (Credits credits : scp.getCreditsSet()) {
            if (credits instanceof InternalSubstitution) {
                for (EnrolmentWrapper enrolmentWrapper : credits.getEnrolmentsSet()) {
                    InternalEnrolmentWrapper iew = (InternalEnrolmentWrapper) enrolmentWrapper;
                    Enrolment enrolment = (Enrolment) iew.getIEnrolment();
                    givenEquivalences.add(enrolment.getCurricularCourse());
                }
            }
            if (enrolled.size() == givenEquivalences.size() && givenEquivalences.containsAll(enrolled)) {
                return true;
            }
            givenEquivalences.clear();
        }
        return false;
    }

    private List<CurricularCourse> getApprovedCourses(Collection<CurriculumLine> approvedCurriculumLines) {
        List<CurricularCourse> courses = new ArrayList<CurricularCourse>();
        for (CurriculumLine curriculumLine : approvedCurriculumLines) {
            if (curriculumLine.getCurricularCourse() == sourceDigitalSystems
                    || curriculumLine.getCurricularCourse() == sourceComputerArchitecture) {
                courses.add(curriculumLine.getCurricularCourse());
                if (courses.size() > 1) {
                    return courses;
                }
            }
        }
        return courses;
    }
}
