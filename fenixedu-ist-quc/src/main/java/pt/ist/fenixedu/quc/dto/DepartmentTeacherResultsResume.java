/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST QUC.
 *
 * FenixEdu IST QUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST QUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST QUC.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.quc.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanComparator;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.Teacher;

import pt.ist.fenixedu.quc.domain.ResultPersonCategory;

public class DepartmentTeacherResultsResume implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<TeacherShiftTypeGroupsResumeResult> teacherShiftTypeGroupsResumeResults;
    private Teacher teacher;
    private Person president;
    private ResultPersonCategory personCategory;
    private boolean backToResume;
    private boolean showAllComments;

    public DepartmentTeacherResultsResume(Teacher teacher, Person president, ResultPersonCategory personCategory,
            boolean backToResume, boolean showAllComments) {
        setTeacher(teacher);
        setPresident(president);
        setPersonCategory(personCategory);
        setBackToResume(backToResume);
        setShowAllComments(showAllComments);
    }

    @Override
    public int hashCode() {
        return getTeacher().hashCode();
    }

    public void addTeacherShiftTypeGroupsResumeResult(TeacherShiftTypeGroupsResumeResult teacherShiftTypeGroupsResumeResult) {
        if (getTeacherShiftTypeGroupsResumeResults() == null) {
            setTeacherShiftTypeGroupsResumeResults(new ArrayList<TeacherShiftTypeGroupsResumeResult>());
        }
        getTeacherShiftTypeGroupsResumeResults().add(teacherShiftTypeGroupsResumeResult);
    }

    public void setTeacherShiftTypeGroupsResumeResults(
            List<TeacherShiftTypeGroupsResumeResult> teacherShiftTypeGroupsResumeResults) {
        this.teacherShiftTypeGroupsResumeResults = teacherShiftTypeGroupsResumeResults;
    }

    public List<TeacherShiftTypeGroupsResumeResult> getTeacherShiftTypeGroupsResumeResults() {
        return teacherShiftTypeGroupsResumeResults;
    }

    public List<TeacherShiftTypeGroupsResumeResult> getOrderedTeacherShiftResumes() {
        Collections.sort(getTeacherShiftTypeGroupsResumeResults(), new BeanComparator("professorship.executionCourse.name"));
        Collections.sort(getTeacherShiftTypeGroupsResumeResults(), new BeanComparator("shiftType"));
        return getTeacherShiftTypeGroupsResumeResults();
    }

    public Collection<List<TeacherShiftTypeGroupsResumeResult>> getTeacherShiftResumesByUC() {
        Map<Professorship, List<TeacherShiftTypeGroupsResumeResult>> result =
                new HashMap<Professorship, List<TeacherShiftTypeGroupsResumeResult>>();
        for (TeacherShiftTypeGroupsResumeResult teacherShiftTypeGroupsResumeResult : getOrderedTeacherShiftResumes()) {
            List<TeacherShiftTypeGroupsResumeResult> teacherShiftTypeGroupsResumeResultList =
                    result.get(teacherShiftTypeGroupsResumeResult.getProfessorship());
            if (teacherShiftTypeGroupsResumeResultList == null) {
                teacherShiftTypeGroupsResumeResultList = new ArrayList<TeacherShiftTypeGroupsResumeResult>();
                result.put(teacherShiftTypeGroupsResumeResult.getProfessorship(), teacherShiftTypeGroupsResumeResultList);
            }
            teacherShiftTypeGroupsResumeResultList.add(teacherShiftTypeGroupsResumeResult);
        }
        return result.values();
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public Person getPresident() {
        return president;
    }

    public void setPresident(Person president) {
        this.president = president;
    }

    public ResultPersonCategory getPersonCategory() {
        return personCategory;
    }

    public void setPersonCategory(ResultPersonCategory personCategory) {
        this.personCategory = personCategory;
    }

    public void setBackToResume(boolean backToResume) {
        this.backToResume = backToResume;
    }

    public boolean isBackToResume() {
        return backToResume;
    }

    public void setShowAllComments(boolean showAllComments) {
        this.showAllComments = showAllComments;
    }

    public boolean isShowAllComments() {
        return showAllComments;
    }
}
