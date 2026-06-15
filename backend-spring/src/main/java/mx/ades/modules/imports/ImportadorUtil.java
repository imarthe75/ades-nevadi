package mx.ades.modules.imports;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ImportadorUtil {

    @Data
    @AllArgsConstructor
    public static class ParsedFile {
        private List<String> headers;
        private List<List<String>> rows;
    }

    public static String normalizeHeader(String header) {
        if (header == null) return "";
        String s = header.trim().toLowerCase();
        s = s.replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replaceAll("\\s+", "_");
        return s;
    }

    public static String getCol(List<String> row, List<String> headers, String... names) {
        for (String name : names) {
            String n = normalizeHeader(name);
            int idx = headers.indexOf(n);
            if (idx != -1 && idx < row.size()) {
                return row.get(idx).trim();
            }
        }
        return "";
    }

    public static LocalDate parseDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        s = s.trim();
        // Try various formats: DD/MM/YYYY, YYYY-MM-DD, DD-MM-YYYY, DD/MM/YY
        String[] formats = {"d/M/yyyy", "yyyy-MM-dd", "d-M-yyyy", "d/M/yy"};
        for (String fmt : formats) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(fmt));
            } catch (Exception e) {
                // Try next
            }
        }
        return null;
    }

    public static Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static ParsedFile parseFile(byte[] content, String filename) throws Exception {
        String ext = filename != null ? filename.toLowerCase() : "";
        if (ext.endsWith(".xlsx") || ext.endsWith(".xls")) {
            return parseExcel(content);
        }
        return parseCsv(content);
    }

    private static ParsedFile parseCsv(byte[] content) {
        String text;
        try {
            if (content.length >= 3 && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF) {
                text = new String(content, 3, content.length - 3, StandardCharsets.UTF_8);
            } else {
                text = new String(content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            text = new String(content, StandardCharsets.ISO_8859_1);
        }

        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        boolean isFirst = true;

        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> parsedLine = parseCsvLine(line);
                if (parsedLine.isEmpty()) continue;
                if (isFirst) {
                    for (String h : parsedLine) {
                        headers.add(normalizeHeader(h));
                    }
                    isFirst = false;
                } else {
                    rows.add(parsedLine);
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return new ParsedFile(headers, rows);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char delimiter = ',';

        // simple heuristic to detect semicolon delimiter
        if (line.contains(";") && !line.contains(",")) {
            delimiter = ';';
        }

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                values.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }

    private static ParsedFile parseExcel(byte[] content) throws Exception {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(content);
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int maxCols = 0;
            for (Row row : sheet) {
                maxCols = Math.max(maxCols, row.getLastCellNum());
            }
            boolean isFirst = true;
            for (Row row : sheet) {
                List<String> r = new ArrayList<>(maxCols);
                boolean hasContent = false;
                for (int c = 0; c < maxCols; c++) {
                    Cell cell = row.getCell(c);
                    String val = "";
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING -> val = cell.getStringCellValue();
                            case NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    val = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                                } else {
                                    double d = cell.getNumericCellValue();
                                    if (d == (long) d) {
                                        val = String.format("%d", (long) d);
                                    } else {
                                        val = String.valueOf(d);
                                    }
                                }
                            }
                            case BOOLEAN -> val = String.valueOf(cell.getBooleanCellValue());
                            case FORMULA -> {
                                try {
                                    val = cell.getStringCellValue();
                                } catch (Exception ex) {
                                    val = String.valueOf(cell.getNumericCellValue());
                                }
                            }
                            default -> val = "";
                        }
                    }
                    String trimmed = val.trim();
                    r.add(trimmed);
                    if (!trimmed.isEmpty()) hasContent = true;
                }
                if (hasContent) {
                    if (isFirst) {
                        for (String h : r) {
                            headers.add(normalizeHeader(h));
                        }
                        isFirst = false;
                    } else {
                        rows.add(r);
                    }
                }
            }
        }
        return new ParsedFile(headers, rows);
    }
}
