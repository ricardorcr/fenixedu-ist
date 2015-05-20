/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST Integration.
 *
 * FenixEdu IST Integration is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Integration is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Integration.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.integration.task.updateData.enrolment;

import java.util.HashSet;
import java.util.Set;

import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.student.Student;
import org.fenixedu.academic.domain.util.email.Message;
import org.fenixedu.academic.domain.util.email.Recipient;
import org.fenixedu.academic.domain.util.email.Sender;
import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.core.security.Authenticate;
import org.fenixedu.bennu.scheduler.custom.CustomTask;

import pt.ist.fenixframework.FenixFramework;

public class SendEmailToFlunkedStudents extends CustomTask {

    private static final String[] FLUNKED_STUDENTS = new String[] { "40492", "42423", "44672", "50045", "51913", "53311",
            "55774", "56563", "57073", "57789", "58976", "66244", "67033", "68642", "69504", "69653", "69814", "69855", "69955",
            "70101", "70151", "70681", "70716", "72621", "73713", "73757", "74042", "74137", "74139", "74169", "74266", "74328" };

    @Override
    public void runTask() throws Exception {
        User user = User.findByUsername("ist24616");
        Authenticate.mock(user);

        Set<Person> students = new HashSet<Person>();
        for (int iter = 0; iter < FLUNKED_STUDENTS.length; iter++) {

            Student student = Student.readStudentByNumber(Integer.valueOf(FLUNKED_STUDENTS[iter]));
            if (student == null) {
                taskLog("Can't find student -> " + FLUNKED_STUDENTS[iter]);
                continue;
            }
            students.add(student.getPerson());
        }

        createEmail(students);
        taskLog("Done.");
    }

    private void createEmail(final Set<Person> students) {

        final Sender sender = getConcelhoDeGestaoSender();

        final Set<Recipient> tos = new HashSet<Recipient>();
        tos.add(new Recipient(students));

        final Set<String> bccs = new HashSet<String>();
        bccs.add("marta.graca@tecnico.usliboa.pt");

        new Message(sender, null, tos, getSubject(), getBody(), bccs);
        taskLog("Sent: " + students.size() + " emails");
    }

    private Sender getConcelhoDeGestaoSender() {
        return FenixFramework.getDomainObject("4196183080395");
    }

    private String getBody() {
        final StringBuilder body = new StringBuilder();

        //  Mail quando os alunos são retirados da lista de prescristos 
        body.append("Caro aluno do TÉCNICO,\n");
        body.append("\n");
        body.append("Após a apreciação do recurso apresentado, e por deferimento do mesmo, seu nome foi excluído da lista final de prescritos para 2015/16.\n");
        body.append("Assim, e se ainda não estiver inscrito em unidades curriculares no 1º semestre do ano letivo 2015/16, poderá inscrever-se entre 8 e 11 de setembro de 2015.\n");
        body.append("\n");
        body.append("De qualquer forma, o seu rendimento académico tem sido claramente abaixo do esperado. Sabemos que vários são os motivos que podem ter condicionado o seu "
                + "desempenho académico ao longo dos últimos anos. Provavelmente já terá tentado inverter esta situação, o GATu disponibiliza-se a traçar consigo um plano específico e individualizado para melhorar o seu rendimento académico.\n\n");
        body.append("Por forma a evitar a sua prescrição no próximo ano é aconselhado a:\n\n");
        body.append("- contactar o Gabinete de Apoio ao Tutorado (GATu) para: o perceber as vantagens ou esclarecer dúvidas caso pretenda alterar a sua inscrição em 2015/16 para o regime de tempo parcial. "
                + "Para mais informações sobre o Regime de Tempo Parcial consulte o Guia Académico em http://tecnico.ulisboa.pt/pt/alunos/\n");
        body.append("- esclarecer qualquer questão que tenha relativa à Lei das Prescrições e às condições de exceção que evitaram a sua prescrição. "
                + "Para mais informações sobre a Lei das Prescrições consulte a parte 2 do Guia Académico em http://tecnico.ulisboa.pt/pt/alunos/\n");
        body.append("- frequentar o Workshop Para Prescrever a Prescrição, que decorrerá no início de Setembro. Poderá inscrever-se ou consultar o Programa do Workshop em http://tutorado.tecnico.ulisboa.pt/formacao/alunos/\n");
        body.append("\n");
        body.append("GATu: Há mais de 10 anos ao lado dos alunos a contribuir para a melhoria do rendimento académico!\n\n");
        body.append("Com os melhores cumprimentos e votos de um bom ano escolar de 2015/2016,\n\n");
        body.append("Prof. Jorge Morgado Conselho de Gestão do Instituto Superior Técnico,\n");
        body.append("Assuntos Académicos\n");

        //Mail quando são postos como prescritos
//        body.append("Caro Aluno do IST,\n");
//        body.append("\n");
//        body.append("Após análise do seu currículo académico, verificou-se estar numa das situações previstas para prescrição no Regulamento de Prescrições do IST (disponível em http://www.ist.utl.pt/pt/alunos).\n");
//        body.append("\n");
//        body.append("A lista provisória de alunos a prescrever encontra-se afixada, junto dos Serviços Académicos, desde o dia 18 de agosto de 2014. Os alunos sujeitos a prescrição não poderão efectuar a sua inscrição em unidades curriculares no ano lectivo 2014/2015.\n");
//        body.append("\n");
//        body.append("No caso de existir alguma incorrecção na avaliação da sua situação, deverá requerer a sua correcção junto dos Serviços Académicos dentro dos prazos estabelecidos para recurso, de 18 de agosto a 22 de agosto de 2014.\n");
//        body.append("\n");
//        body.append("O resultado dos recursos será afixado no dia 8 de Setembro de 2014. No caso de deferimento a inscrição em unidades curriculares no 1º semestre do ano lectivo 2014/2015 poderá ser efectuada entre 9 e 12 de setembro de 2014.\n");
//        body.append("\n");
//        body.append("Técnico, 18 de Agosto de 2014\n");
//        body.append("O Conselho de Gestão do IST\n");

        return body.toString();
    }

    private String getSubject() {
        return "Levantamento de prescrição para o ano lectivo 2015/2016";
//        return "Prescrição para o ano lectivo 2014/2015";
    }
}
