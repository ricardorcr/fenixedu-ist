package pt.ist.fenixedu.integration.task;

import org.fenixedu.academic.domain.phd.PhdProgram;
import org.fenixedu.academic.util.MultiLanguageString;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixframework.FenixFramework;

public class Teste extends CustomTask {

    @Override
    public void runTask() throws Exception {
        PhdProgram pdhProgram = FenixFramework.getDomainObject("4604204947708");
        MultiLanguageString newName =
                new MultiLanguageString(MultiLanguageString.pt, "Biotecnologia e Biociências").with(MultiLanguageString.en,
                        "Biotechnology and Biosciences");
        pdhProgram.setName(newName);
    }
}
