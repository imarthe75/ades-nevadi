package mx.ades.modules.horarios.application.export;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HorarioExcelExporter {

    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
    private static final int[] DIAS_NUM = {1, 2, 3, 4, 5};

    public byte[] generarExcel(List<Map<String, Object>> horarios, String nombreCorrida) {
        try (Workbook workbook = new XSSFWorkbook()) {
            
            // Estilos
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle cellStyle = createCellStyle(workbook);
            
            // Agrupar horarios por grupo
            Map<String, List<Map<String, Object>>> porGrupo = horarios.stream()
                .filter(h -> h.get("nombre_grupo") != null)
                .collect(Collectors.groupingBy(h -> String.valueOf(h.get("nombre_grupo"))));
            
            // Crear una pestaña por grupo
            porGrupo.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String grupoStr = entry.getKey();
                    List<Map<String, Object>> entradas = entry.getValue();
                    crearPestanaGrupo(workbook, grupoStr, entradas, headerStyle, cellStyle);
                });

            // Agrupar por docente
            Map<String, List<Map<String, Object>>> porDocente = horarios.stream()
                .filter(h -> h.get("nombre_profesor") != null)
                .collect(Collectors.groupingBy(h -> String.valueOf(h.get("nombre_profesor"))));
                
            porDocente.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String docenteStr = entry.getKey();
                    List<Map<String, Object>> entradas = entry.getValue();
                    // Limitar longitud del nombre de pestaña a 31 (límite de Excel)
                    String sheetName = "Doc " + docenteStr;
                    if (sheetName.length() > 31) {
                        sheetName = sheetName.substring(0, 31);
                    }
                    crearPestanaDocente(workbook, sheetName, entradas, headerStyle, cellStyle);
                });

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar el Excel de horarios", e);
        }
    }

    private void crearPestanaGrupo(Workbook workbook, String nombre, List<Map<String, Object>> entradas, CellStyle headerStyle, CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet("Grupo " + nombre);
        crearEstructuraGrid(sheet, headerStyle, cellStyle, entradas, true);
    }
    
    private void crearPestanaDocente(Workbook workbook, String nombre, List<Map<String, Object>> entradas, CellStyle headerStyle, CellStyle cellStyle) {
        Sheet sheet = workbook.createSheet(nombre);
        crearEstructuraGrid(sheet, headerStyle, cellStyle, entradas, false);
    }

    private void crearEstructuraGrid(Sheet sheet, CellStyle headerStyle, CellStyle cellStyle, List<Map<String, Object>> entradas, boolean esGrupo) {
        // Cabeceras
        Row headerRow = sheet.createRow(0);
        Cell cellHora = headerRow.createCell(0);
        cellHora.setCellValue("Hora");
        cellHora.setCellStyle(headerStyle);
        sheet.setColumnWidth(0, 4000);

        for (int i = 0; i < DIAS.length; i++) {
            Cell c = headerRow.createCell(i + 1);
            c.setCellValue(DIAS[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i + 1, 6000);
        }

        // Determinar franjas horarias únicas
        List<String> franjas = entradas.stream()
            .map(h -> String.valueOf(h.get("hora_inicio")).substring(0, 5))
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        int rowIndex = 1;
        for (String franja : franjas) {
            Row row = sheet.createRow(rowIndex++);
            Cell cFranja = row.createCell(0);
            cFranja.setCellValue(franja);
            cFranja.setCellStyle(headerStyle);

            for (int i = 0; i < DIAS_NUM.length; i++) {
                int diaNum = DIAS_NUM[i];
                List<Map<String, Object>> celdas = entradas.stream()
                    .filter(h -> Integer.parseInt(String.valueOf(h.get("dia_semana"))) == diaNum && String.valueOf(h.get("hora_inicio")).startsWith(franja))
                    .collect(Collectors.toList());

                Cell cell = row.createCell(i + 1);
                cell.setCellStyle(cellStyle);

                if (!celdas.isEmpty()) {
                    Map<String, Object> h = celdas.get(0); // Tomar el primero
                    String texto = String.valueOf(h.get("nombre_materia") != null ? h.get("nombre_materia") : "");
                    if (esGrupo && h.get("nombre_profesor") != null) {
                        texto += "\n" + h.get("nombre_profesor");
                    } else if (!esGrupo && h.get("nombre_grupo") != null) {
                        texto += "\n" + h.get("nombre_grupo");
                    }
                    if (h.get("nombre_aula") != null) {
                        texto += "\n" + h.get("nombre_aula");
                    }
                    cell.setCellValue(texto);
                }
            }
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
