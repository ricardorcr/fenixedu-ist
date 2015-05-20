package pt.ist.fenixedu.integration.task.updateData;

import org.fenixedu.academic.domain.candidacy.IngressionType;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixedu.integration.domain.student.importation.DgesIngressionTypeMapping;

public class InitializeDgesIngressionTypeMapping extends CustomTask {

    @Override
    public void runTask() throws Exception {

        DgesIngressionTypeMapping ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA01").get());
        ingressionTypeMapping.setDgesCode("1");

        ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA02").get());
        ingressionTypeMapping.setDgesCode("2");

        ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA03").get());
        ingressionTypeMapping.setDgesCode("3");

        ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA04").get());
        ingressionTypeMapping.setDgesCode("4");

        ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA05").get());
        ingressionTypeMapping.setDgesCode("5");

        ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA06").get());
        ingressionTypeMapping.setDgesCode("6");

        ingressionTypeMapping = new DgesIngressionTypeMapping();
        ingressionTypeMapping.setIngressionType(IngressionType.findIngressionTypeByCode("CNA07").get());
        ingressionTypeMapping.setDgesCode("D");
    }
}