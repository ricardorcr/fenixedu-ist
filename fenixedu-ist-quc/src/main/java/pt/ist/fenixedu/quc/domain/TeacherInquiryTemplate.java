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
package pt.ist.fenixedu.quc.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.academic.domain.ShiftType;
import org.fenixedu.academic.domain.Teacher;
import org.fenixedu.bennu.core.domain.Bennu;
import org.joda.time.DateTime;

import pt.ist.fenixedu.contracts.domain.personnelSection.contracts.ProfessionalCategory;
import pt.ist.fenixedu.teacher.domain.teacher.DegreeTeachingService;

public class TeacherInquiryTemplate extends TeacherInquiryTemplate_Base {

    public TeacherInquiryTemplate(DateTime begin, DateTime end) {
        super();
        init(begin, end);
    }

    public static TeacherInquiryTemplate getCurrentTemplate() {
        final Collection<InquiryTemplate> inquiryTemplates = Bennu.getInstance().getInquiryTemplatesSet();
        for (final InquiryTemplate inquiryTemplate : inquiryTemplates) {
            if (inquiryTemplate instanceof TeacherInquiryTemplate && inquiryTemplate.isOpen()) {
                return (TeacherInquiryTemplate) inquiryTemplate;
            }
        }
        return null;
    }

    public static TeacherInquiryTemplate getTemplateByExecutionPeriod(ExecutionSemester executionSemester) {
        final Collection<InquiryTemplate> inquiryTemplates = Bennu.getInstance().getInquiryTemplatesSet();
        for (final InquiryTemplate inquiryTemplate : inquiryTemplates) {
            if (inquiryTemplate instanceof TeacherInquiryTemplate && executionSemester == inquiryTemplate.getExecutionPeriod()) {
                return (TeacherInquiryTemplate) inquiryTemplate;
            }
        }
        return null;
    }

    public static boolean hasToAnswerTeacherInquiry(Person person, Professorship professorship) {
        if (!InquiriesRoot.isAvailableForInquiry(professorship.getExecutionCourse())) {
            return false;
        }
        final Teacher teacher = person.getTeacher();
        boolean mandatoryTeachingService = false;
        if (teacher != null
                && ProfessionalCategory.isTeacherProfessorCategory(teacher, professorship.getExecutionCourse()
                        .getExecutionPeriod())) {
            mandatoryTeachingService = true;
        }

        boolean isToAnswer = true;
        if (mandatoryTeachingService) {
            if (!professorship.getInquiryResultsSet().isEmpty()) {
                return isToAnswer;
            }

            isToAnswer = false;
            final Map<ShiftType, Double> shiftTypesPercentageMap = new HashMap<ShiftType, Double>();
            for (final DegreeTeachingService degreeTeachingService : professorship.getDegreeTeachingServicesSet()) {
                for (final ShiftType shiftType : degreeTeachingService.getShift().getTypes()) {
                    Double percentage = shiftTypesPercentageMap.get(shiftType);
                    if (percentage == null) {
                        percentage = degreeTeachingService.getPercentage();
                    } else {
                        percentage += degreeTeachingService.getPercentage();
                    }
                    shiftTypesPercentageMap.put(shiftType, percentage);
                }
            }
            for (final ShiftType shiftType : shiftTypesPercentageMap.keySet()) {
                final Double percentage = shiftTypesPercentageMap.get(shiftType);
                if (percentage >= 20) {
                    isToAnswer = true;
                    break;
                }
            }
        }

        return isToAnswer;
    }

    public static Collection<ExecutionCourse> getExecutionCoursesWithTeachingInquiriesToAnswer(Person person) {
        final Collection<ExecutionCourse> result = new ArrayList<ExecutionCourse>();
        final TeacherInquiryTemplate currentTemplate = getCurrentTemplate();
        if (currentTemplate != null) {
            for (final Professorship professorship : person.getProfessorships(currentTemplate.getExecutionPeriod())) {
                final boolean isToAnswer = hasToAnswerTeacherInquiry(person, professorship);
                if (isToAnswer
                        && (professorship.getInquiryTeacherAnswer() == null
                                || professorship.getInquiryTeacherAnswer().hasRequiredQuestionsToAnswer(currentTemplate) || InquiryResultComment
                                    .hasMandatoryCommentsToMake(professorship))) {
                    result.add(professorship.getExecutionCourse());
                }
            }
        }
        return result;
    }

    public static int getNumberOfNotAnsweredQuestions(Person teacher, ExecutionSemester executionSemester) {
        InquiryTemplate inquiryTemplate = getTeachingInquiryTemplate(executionSemester);
        List<InquiryTeacherAnswer> inquiryTeacherAnswers =
                teacher.getProfessorships(executionSemester).stream().filter(p -> hasToAnswerTeacherInquiry(teacher, p))
                        .map(p -> p.getInquiryTeacherAnswer()).collect(Collectors.toList());
//                person.getInquiryDelegateAnswersSet().stream()
//                        .filter(inquiryAnswer -> inquiryAnswer.getExecutionCourse().getExecutionPeriod() == executionSemester)
//                        .collect(Collectors.toList());
//        
//        return inquiryTemplate.getNumberOfQuestions() - professorship.getInquiryTeacherAnswer().getNumberOfAnsweredQuestions();
        return 0;
    }
}
