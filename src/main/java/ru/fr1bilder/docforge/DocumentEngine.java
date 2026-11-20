package ru.fr1bilder.docforge;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;
import org.apache.poi.xwpf.usermodel.*;

/** Подстановка полей в основной текст, таблицы, колонтитулы и вложенные таблицы. */
public final class DocumentEngine {
  private DocumentEngine() {}

  private static Pattern pattern() {
    return Pattern.compile("\\{\\{\\s*([\\p{L}\\p{N}_ .-]+?)\\s*}}");
  }

  public static Set<String> fields(Path template) throws IOException {
    Set<String> result = new LinkedHashSet<>();
    try (InputStream in = Files.newInputStream(template);
        XWPFDocument doc = new XWPFDocument(in)) {
      visit(
          doc,
          p -> {
            Matcher m = pattern().matcher(paragraphText(p).text());
            while (m.find()) result.add(m.group(1).trim());
          });
    }
    return result;
  }

  public static void render(Path template, Path destination, Map<String, String> values)
      throws IOException {
    Set<String> required = fields(template);
    List<String> missing =
        required.stream()
            .filter(k -> !values.containsKey(k) || values.get(k) == null || values.get(k).isBlank())
            .toList();
    if (!missing.isEmpty())
      throw new IllegalArgumentException("Не заполнены поля: " + String.join(", ", missing));
    if (Files.exists(destination)) throw new FileAlreadyExistsException(destination.toString());
    Path parent = destination.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path staging = Files.createTempFile(parent, ".docforge-", ".tmp");
    try {
      try (InputStream in = Files.newInputStream(template);
          XWPFDocument doc = new XWPFDocument(in)) {
        visit(doc, p -> replace(p, values));
        try (OutputStream out = Files.newOutputStream(staging)) {
          doc.write(out);
        }
      }
      Files.move(staging, destination);
    } finally {
      Files.deleteIfExists(staging);
    }
  }

  private record ParagraphText(
      List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText> nodes,
      List<Integer> starts,
      String text) {}

  private static ParagraphText paragraphText(XWPFParagraph paragraph) {
    List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText> nodes = new ArrayList<>();
    List<Integer> starts = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    for (XWPFRun run : paragraph.getRuns()) {
      try (var cursor = run.getCTR().newCursor()) {
        if (!cursor.toFirstChild()) continue;
        do {
          if (cursor.getObject()
                  instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText node
              && cursor.getName().getLocalPart().equals("t")) {
            starts.add(text.length());
            nodes.add(node);
            text.append(node.getStringValue());
          } else if (!cursor.getName().getLocalPart().equals("rPr")) {
            // Табуляция, перенос, рисунок и поле Word разделяют текстовые фрагменты.
            // Их нельзя незаметно склеить в несуществующий в документе плейсхолдер.
            text.append('\u0000');
          }
        } while (cursor.toNextSibling());
      }
    }
    return new ParagraphText(nodes, starts, text.toString());
  }

  private static void replace(XWPFParagraph paragraph, Map<String, String> values) {
    // Координаты строятся по текстовым узлам: Word может разделить одно поле на несколько runs.
    ParagraphText paragraphText = paragraphText(paragraph);
    var nodes = paragraphText.nodes();
    var starts = paragraphText.starts();
    Matcher matcher = pattern().matcher(paragraphText.text());
    record Match(int start, int end, String value) {}
    List<Match> matches = new ArrayList<>();
    while (matcher.find())
      matches.add(new Match(matcher.start(), matcher.end(), values.get(matcher.group(1).trim())));
    int[] lengths = nodes.stream().mapToInt(node -> node.getStringValue().length()).toArray();
    // Справа налево: индексы предыдущих полей остаются действительными, стили окружающего текста
    // сохраняются.
    for (int m = matches.size() - 1; m >= 0; m--) {
      Match match = matches.get(m);
      for (int i = nodes.size() - 1; i >= 0; i--) {
        String s = nodes.get(i).getStringValue();
        int originalEnd = starts.get(i) + lengths[i];
        if (starts.get(i) >= match.end() || originalEnd <= match.start()) continue;
        int from = Math.max(0, match.start() - starts.get(i));
        int to = Math.min(s.length(), match.end() - starts.get(i));
        String insert = match.start() >= starts.get(i) ? match.value() : "";
        nodes.get(i).setStringValue(s.substring(0, from) + insert + s.substring(to));
        nodes.get(i).setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
      }
    }
  }

  private static void visit(XWPFDocument doc, Consumer<XWPFParagraph> action) {
    visitBody(doc, action);
    doc.getHeaderList().forEach(h -> visitBody(h, action));
    doc.getFooterList().forEach(f -> visitBody(f, action));
  }

  private static void visitBody(IBody body, Consumer<XWPFParagraph> action) {
    for (IBodyElement element : body.getBodyElements()) {
      if (element instanceof XWPFParagraph p) action.accept(p);
      else if (element instanceof XWPFTable t)
        for (XWPFTableRow r : t.getRows())
          for (XWPFTableCell c : r.getTableCells()) visitBody(c, action);
    }
  }
}
