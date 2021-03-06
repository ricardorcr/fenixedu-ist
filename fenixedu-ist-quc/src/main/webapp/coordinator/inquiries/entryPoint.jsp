<%--

    Copyright © 2013 Instituto Superior Técnico

    This file is part of FenixEdu IST QUC.

    FenixEdu IST QUC is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FenixEdu IST QUC is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with FenixEdu IST QUC.  If not, see <http://www.gnu.org/licenses/>.

--%>
<%@ page language="java" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<h2>${portal.message('resources.FenixEduQucResources', 'link.coordinator.QUCResults')}</h2>

<p><span class="emphasis">${degrees.size()}</span> <bean:message key="label.masterDegree.administrativeOffice.degreesFound"/></p>
<p class="mbottom05"><bean:message key="label.masterDegree.chooseOne"/></p>

<table class="tstyle4 thlight mtop05">
	<tr>
		<th><bean:message key="label.name" bundle="APPLICATION_RESOURCES" /></th>
		<th><bean:message key="label.curricularPlan" bundle="APPLICATION_RESOURCES" /></th>
	</tr>
	
	<logic:iterate id="degreeCurricularPlan" name="degrees">
		<tr>
		   <td>
				<logic:notEmpty name="degreeCurricularPlan" property="degree.phdProgram">
					<bean:write name="degreeCurricularPlan" property="degree.phdProgram.presentationName"/>
				</logic:notEmpty>
				<logic:empty name="degreeCurricularPlan" property="degree.phdProgram">
					<bean:write name="degreeCurricularPlan" property="degree.presentationName"/>
				</logic:empty>
		   </td>
		   <td class="acenter">
				<a href="${pageContext.request.contextPath}/coordinator/viewInquiriesResults.do?method=prepare&degreeCurricularPlanID=${degreeCurricularPlan.externalId}">${degreeCurricularPlan.name}</a>
		   </td>	   
		</tr>
	</logic:iterate>

</table>
