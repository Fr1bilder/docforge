package ru.fr1bilder.docforge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CsvTest {
  @TempDir Path dir;

  @Test
  void handlesBomQuotesSemicolonAndMultiline() throws Exception {
    Path file = dir.resolve("data.csv");
    Files.writeString(
        file, "\uFEFFКлиент;Описание\r\n\"ООО \"\"Север\"\"\";\"строка 1\nстрока 2\"\r\n");
    var rows = Csv.read(file);
    assertEquals("ООО \"Север\"", rows.getFirst().get("Клиент"));
    assertEquals("строка 1\nстрока 2", rows.getFirst().get("Описание"));
  }

  @Test
  void handlesCommaAndEmptyLastField() throws Exception {
    Path file = dir.resolve("data.csv");
    Files.writeString(file, "name,value\n\"a,b\",\n");
    assertEquals("", Csv.read(file).getFirst().get("value"));
  }

  @Test
  void rejectsMalformedInput() throws Exception {
    Path file = dir.resolve("data.csv");
    for (String text :
        new String[] {"a,a\n1,2", "a,b\n1", "a,b\n\"1,2", "a,b\n\"a\"z,b", "a,b\n"}) {
      Files.writeString(file, text);
      assertThrows(IllegalArgumentException.class, () -> Csv.read(file), text);
    }
  }
}
