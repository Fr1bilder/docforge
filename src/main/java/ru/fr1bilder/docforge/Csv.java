package ru.fr1bilder.docforge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Csv {
    private Csv() {}
    public static List<Map<String, String>> read(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        List<List<String>> rows = parse(text, detectSeparator(text));
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV пуст");
        List<String> header = rows.getFirst();
        if (header.stream().anyMatch(String::isBlank) || new HashSet<>(header).size() != header.size())
            throw new IllegalArgumentException("Заголовки CSV должны быть непустыми и уникальными");
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() == 1 && row.getFirst().isBlank()) continue;
            if (row.size() != header.size()) throw new IllegalArgumentException("Строка " + (i + 1) + ": число столбцов не совпадает с заголовком");
            Map<String, String> entry = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) entry.put(header.get(j), row.get(j));
            result.add(entry);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("CSV не содержит записей");
        return result;
    }
    private static char detectSeparator(String text) {
        boolean quoted = false; int comma = 0, semi = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') { if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') i++; else quoted = !quoted; }
            else if (!quoted) { if (c == '\n' || c == '\r') break; if (c == ',') comma++; if (c == ';') semi++; }
        }
        return semi > comma ? ';' : ',';
    }
    static List<List<String>> parse(String text, char separator) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder(); boolean quoted = false, afterQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { value.append('"'); i++; } else { quoted = false; afterQuote = true; } }
                else value.append(c);
            } else if (c == '"') {
                if (!value.isEmpty() || afterQuote) throw new IllegalArgumentException("Некорректные кавычки CSV");
                quoted = true;
            } else if (c == separator) { row.add(value.toString()); value.setLength(0); afterQuote = false; }
            else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(value.toString()); rows.add(row); row = new ArrayList<>(); value.setLength(0); afterQuote = false;
            } else { if (afterQuote) throw new IllegalArgumentException("Текст после закрывающей кавычки CSV"); value.append(c); }
        }
        if (quoted) throw new IllegalArgumentException("Незакрытая кавычка CSV");
        if (!row.isEmpty() || !value.isEmpty() || afterQuote) { row.add(value.toString()); rows.add(row); }
        return rows;
    }
}
