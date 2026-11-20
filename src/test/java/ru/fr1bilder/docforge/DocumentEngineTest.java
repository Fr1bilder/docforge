package ru.fr1bilder.docforge;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentEngineTest {
    @TempDir Path dir;
    private Path template() throws Exception {
        Path file=dir.resolve("template.docx");
        try(XWPFDocument doc=new XWPFDocument()){
            XWPFParagraph p=doc.createParagraph();p.createRun().setText("До ");XWPFRun bold=p.createRun();bold.setBold(true);bold.setText("{{Кли");p.createRun().setText("ент}} после {{Номер}}.");
            doc.createTable(1,1).getRow(0).getCell(0).setText("{{Клиент}}");
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("{{Номер}}");
            try(var out=Files.newOutputStream(file)){doc.write(out);}
        }
        return file;
    }
    @Test void splitRunsTablesAndHeaderPreserveSurroundings()throws Exception{
        Path in=template(),out=dir.resolve("out.docx");
        assertEquals(Set.of("Клиент","Номер"),DocumentEngine.fields(in));
        DocumentEngine.render(in,out,Map.of("Клиент","Студия $ Север \\","Номер","КП-3"));
        try(var stream=Files.newInputStream(out);XWPFDocument doc=new XWPFDocument(stream)){
            assertEquals("До Студия $ Север \\ после КП-3.",doc.getParagraphs().getFirst().getText());
            assertTrue(doc.getParagraphs().getFirst().getRuns().get(1).isBold());
            assertEquals("Студия $ Север \\",doc.getTables().getFirst().getRow(0).getCell(0).getText());
            assertEquals("КП-3",doc.getHeaderList().getFirst().getText().strip());
        }
        assertEquals(Set.of("Клиент","Номер"),DocumentEngine.fields(in));
        assertThrows(FileAlreadyExistsException.class,()->DocumentEngine.render(in,out,Map.of("Клиент","x","Номер","y")));
    }
    @Test void missingValuesDoNotProduceFile()throws Exception{
        Path in=template(),out=dir.resolve("out.docx");assertThrows(IllegalArgumentException.class,()->DocumentEngine.render(in,out,Map.of("Номер","1")));assertFalse(Files.exists(out));
    }
    @Test void tabsAndBreaksCannotCreatePhantomFields() throws Exception {
        Path in=dir.resolve("boundaries.docx"),out=dir.resolve("boundaries-out.docx");
        try(XWPFDocument doc=new XWPFDocument()){
            XWPFRun run=doc.createParagraph().createRun();run.setText("{{Имя");run.addTab();run.setText("Клиента}} / {{Клиент}}");
            XWPFRun broken=doc.createParagraph().createRun();broken.setText("{{Ном");broken.addBreak();broken.setText("ер}}");
            try(var stream=Files.newOutputStream(in)){doc.write(stream);}
        }
        assertEquals(Set.of("Клиент"),DocumentEngine.fields(in));
        DocumentEngine.render(in,out,Map.of("Клиент","Север"));
        try(var stream=Files.newInputStream(out);XWPFDocument doc=new XWPFDocument(stream)){
            assertEquals("{{Имя\tКлиента}} / Север",doc.getParagraphs().getFirst().getText());
            assertEquals("{{Ном\nер}}",doc.getParagraphs().get(1).getText());
        }
    }
    @Test void multipleFieldsInsideSameRunAndRepeatedNames()throws Exception{
        Path in=dir.resolve("repeat.docx"),out=dir.resolve("repeat-out.docx");
        try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{ A }} / {{B}} / {{A}}");try(var stream=Files.newOutputStream(in)){doc.write(stream);}}
        DocumentEngine.render(in,out,Map.of("A","long replacement","B","!"));
        try(var stream=Files.newInputStream(out);XWPFDocument doc=new XWPFDocument(stream)){assertEquals("long replacement / ! / long replacement",doc.getParagraphs().getFirst().getText());}
    }
    @Test void demoAndProfileRoundTrip()throws Exception{
        Workspace w=new Workspace(dir);w.seed();assertEquals(3,w.listTemplates().size());w.seed();assertEquals(3,w.listTemplates().size());
        var records=Csv.read(dir.resolve("Пример-пакет.csv"));assertEquals(2,records.size());
        w.saveProfile("Компания",Map.of("Компания","Север"));assertEquals("Север",w.loadProfile(w.profiles.resolve("Компания.properties")).get("Компания"));
        assertThrows(IllegalArgumentException.class,()->w.saveProfile("../outside",Map.of()));
        Path out=w.uniqueOutput("../КП:1");assertEquals(w.output,out.getParent());Files.writeString(out,"test");assertNotEquals(out,w.uniqueOutput("../КП:1"));
        w.record(out,"Шаблон","Тест");assertEquals(out.toString(),w.history().getFirst()[3]);
    }
}
