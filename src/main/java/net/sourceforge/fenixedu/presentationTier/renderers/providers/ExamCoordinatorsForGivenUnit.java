/**
 * Copyright © 2002 Instituto Superior Técnico
 *
 * This file is part of FenixEdu Core.
 *
 * FenixEdu Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu Core.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fenixedu.academic.ui.renderers.providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.organizationalStructure.Unit;
import org.fenixedu.academic.domain.vigilancy.ExamCoordinator;
import org.fenixedu.academic.domain.vigilancy.VigilantGroup;
import org.fenixedu.academic.ui.struts.action.vigilancy.VigilantGroupBean;

import org.apache.commons.beanutils.BeanComparator;

import pt.ist.fenixWebFramework.rendererExtensions.converters.DomainObjectKeyArrayConverter;
import pt.ist.fenixWebFramework.renderers.DataProvider;
import pt.ist.fenixWebFramework.renderers.components.converters.Converter;

public class ExamCoordinatorsForGivenUnit implements DataProvider {

    @Override
    public Object provide(Object source, Object currentValue) {
        VigilantGroupBean bean = (VigilantGroupBean) source;
        ExecutionYear currentYear = ExecutionYear.readCurrentExecutionYear();
        Unit unit = bean.getSelectedUnit();
        List<ExamCoordinator> coordinators = new ArrayList<ExamCoordinator>();
        if (unit != null) {
            coordinators.addAll(ExamCoordinator.getExamCoordinatorsForGivenYear(unit, currentYear));
        }
        VigilantGroup group = bean.getSelectedVigilantGroup();
        if (group != null) {
            coordinators.removeAll(group.getExamCoordinatorsSet());
        }

        Collections.sort(coordinators, new BeanComparator("person.name"));
        return coordinators;

    }

    @Override
    public Converter getConverter() {
        return new DomainObjectKeyArrayConverter();
    }

}
