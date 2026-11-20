package ru.fr1bilder.docforge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;

public final class Workspace {
  public final Path root, templates, output, profiles;

  public Workspace(Path root) throws IOException {
    this.root = root.toAbsolutePath();
    templates = this.root.resolve("templates");
    output = this.root.resolve("output");
    profiles = this.root.resolve("profiles");
    Files.createDirectories(templates);
    Files.createDirectories(output);
    Files.createDirectories(profiles);
  }

  public static Path defaultRoot() {
    String local = System.getenv("LOCALAPPDATA");
    return Path.of(local == null ? System.getProperty("user.home") : local, "DocForge");
  }

  public void seed() throws IOException {
    sample(
        "Коммерческое предложение.docx",
        "КОММЕРЧЕСКОЕ ПРЕДЛОЖЕНИЕ",
        new String[] {
          "№ {{Номер}} от {{Дата}}",
          "Для: {{Клиент}}",
          "Исполнитель: {{Компания}}",
          "Предлагаем: {{Услуга}}",
          "Стоимость: {{Сумма}} руб.",
          "Срок выполнения: {{Срок}}",
          "Контакт: {{Контакт}}"
        });
    sample(
        "Акт выполненных работ.docx",
        "АКТ ВЫПОЛНЕННЫХ РАБОТ",
        new String[] {
          "№ {{Номер}} от {{Дата}}", "Заказчик: {{Клиент}}", "Исполнитель: {{Компания}}",
          "Выполненные работы: {{Услуга}}", "Стоимость: {{Сумма}} руб.",
              "Замечания: {{Примечание}}",
          "Подпись заказчика: __________________", "Подпись исполнителя: __________________"
        });
    sample(
        "Внутренняя заявка.docx",
        "ЗАЯВКА НА ВЫПОЛНЕНИЕ РАБОТ",
        new String[] {
          "Заявка № {{Номер}} от {{Дата}}", "Инициатор: {{Клиент}}", "Подразделение: {{Компания}}",
          "Описание: {{Услуга}}", "Необходимый срок: {{Срок}}", "Связь: {{Контакт}}"
        });
    Path csv = root.resolve("Пример-пакет.csv");
    if (Files.notExists(csv))
      Files.writeString(
          csv,
          "\uFEFFНомер;Дата;Клиент;Компания;Услуга;Сумма;Срок;Контакт\n"
              + "КП-101;20.11.2026;Студия Север;Мастерская решений;Настройка системы учёта;45000;10"
              + " рабочих дней;demo@example.com\n"
              + "КП-102;20.11.2026;Магазин Берег;Мастерская решений;Обработка прайсов;18000;5"
              + " рабочих дней;demo@example.com\n",
          StandardCharsets.UTF_8);
  }

  private void sample(String name, String title, String[] lines) throws IOException {
    Path target = templates.resolve(name);
    if (Files.exists(target)) return;
    try (XWPFDocument doc = new XWPFDocument()) {
      doc.getProperties().getCoreProperties().setCreator("Fr1bilder");
      XWPFRun heading = doc.createParagraph().createRun();
      heading.setText(title);
      heading.setBold(true);
      heading.setFontSize(20);
      heading.setColor("175C52");
      for (String line : lines) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(180);
        XWPFRun run = p.createRun();
        run.setFontFamily("Calibri");
        run.setFontSize(12);
        run.setText(line);
      }
      XWPFRun foot = doc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun();
      foot.setText("Демонстрационный шаблон • {{Компания}}");
      foot.setFontSize(9);
      foot.setColor("667085");
      try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
        doc.write(out);
      }
    }
  }

  public List<Path> listTemplates() throws IOException {
    try (var stream = Files.list(templates)) {
      return stream
          .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
          .sorted()
          .toList();
    }
  }

  public Path uniqueOutput(String prefix) {
    String safe = prefix.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    if (safe.length() > 90) safe = safe.substring(0, 90);
    if (safe.isBlank()) safe = "Документ";
    Path path = output.resolve(safe + ".docx");
    int counter = 2;
    while (Files.exists(path)) path = output.resolve(safe + " (" + counter++ + ").docx");
    return path;
  }

  public void saveProfile(String name, Map<String, String> values) throws IOException {
    if (!name.matches("[\\p{L}\\p{N}_ -]{1,60}"))
      throw new IllegalArgumentException("Имя профиля: до 60 букв, цифр, пробелов, дефисов");
    Properties p = new Properties();
    p.putAll(values);
    try (Writer out =
        Files.newBufferedWriter(profiles.resolve(name + ".properties"), StandardCharsets.UTF_8)) {
      p.store(out, "DocForge");
    }
  }

  public Map<String, String> loadProfile(Path path) throws IOException {
    Properties p = new Properties();
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      p.load(reader);
    }
    Map<String, String> result = new LinkedHashMap<>();
    p.forEach((k, v) -> result.put(k.toString(), v.toString()));
    return result;
  }

  public synchronized void record(Path file, String template, String mode) throws IOException {
    String line =
        Instant.now()
            + "\t"
            + mode
            + "\t"
            + template.replace('\t', ' ')
            + "\t"
            + file.toAbsolutePath()
            + "\n";
    Files.writeString(
        root.resolve("history.tsv"),
        line,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  public List<String[]> history() throws IOException {
    Path log = root.resolve("history.tsv");
    if (!Files.exists(log)) return List.of();
    List<String[]> entries = new ArrayList<>();
    for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
      String[] parts = line.split("\t", 4);
      if (parts.length == 4) entries.add(parts);
    }
    Collections.reverse(entries);
    return entries;
  }
}
