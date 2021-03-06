<%--

    Copyright © 2013 Instituto Superior Técnico

    This file is part of FenixEdu IST Tutorship.

    FenixEdu IST Tutorship is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FenixEdu IST Tutorship is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with FenixEdu IST Tutorship.  If not, see <http://www.gnu.org/licenses/>.

--%>
<%@ page language="java" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://fenix-ashes.ist.utl.pt/fenix-renderers" prefix="fr" %>


<h2><bean:message key="title.tutorship.tutor.tutorships" bundle="PEDAGOGICAL_COUNCIL" /></h2>

<html:link page="/viewTutors.do?method=backToTutors" paramId="tutorshipIntentionID" paramName="tutorshipIntention" paramProperty="externalId">
	<bean:message key="label.return" bundle="APPLICATION_RESOURCES"/>
</html:link>

<h3><bean:write name="tutorshipIntention" property="teacher.person.name"/> - <bean:write name="tutorshipIntention" property="teacher.person.username"/></h3>
		
<fr:view name="tutorshipIntention" property="tutorships">
	<fr:schema type="pt.ist.fenixedu.tutorship.domain.Tutorship" bundle="APPLICATION_RESOURCES">
		<fr:slot name="student.number" key="label.number"/>
		<fr:slot name="student.name" key="label.name"/>
		<fr:slot name="student.person.defaultMobilePhoneNumber" key="label.mobile"/>
		<fr:slot name="student.person.defaultEmailAddressValue" key="label.mail"/>
	</fr:schema>
	<fr:layout name="tabular">
		<fr:property name="classes" value="tstyle1" />
	</fr:layout>
</fr:view>