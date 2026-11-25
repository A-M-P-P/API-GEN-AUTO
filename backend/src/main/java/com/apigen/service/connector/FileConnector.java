package com.apigen.service.connector;

import com.apigen.config.AppProperties;
import com.apigen.domain.ConnectionType;
import com.apigen.execution.ExecutionContext;
import com.apigen.execution.TemplateEngine;
import com.apigen.service.ConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Connecteur fichiers : lecture/écriture CSV, JSON et XLSX (système de fichiers local).
 */
@Component
public class FileConnector {

    private final ConnectionService connectionService;
    private final TemplateEngine templates;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public FileConnector(ConnectionService connectionService,
                         TemplateEngine templates,
                         ObjectMapper objectMapper,
                         AppProperties appProperties) {
        this.connectionService = connectionService;
        this.templates = templates;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public Object read(Map<String, Object> step, ExecutionContext context) {
        try {
            Path base = resolveBase(step, context);
            String relative = templates.resolveString(string(step.get("path"), ""), context);
            Path file = secureResolve(base, relative);
            String format = string(step.get("format"), extension(file)).toUpperCase(Locale.ROOT);

            return switch (format) {
                case "CSV" -> readCsv(file);
                case "JSON" -> objectMapper.readValue(file.toFile(), Object.class);
                case "XLSX", "XLS" -> readXlsx(file);
                default -> throw new IllegalArgumentException("Format de fichier non supporté : " + format);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Lecture du fichier échouée : " + rootMessage(e), e);
        }
    }

    public Map<String, Object> write(Map<String, Object> step, ExecutionContext context) {
        try {
            Path base = resolveBase(step, context);
            String relative = templates.resolveString(string(step.get("path"), ""), context);
            Path file = secureResolve(base, relative);
            String format = string(step.get("format"), extension(file)).toUpperCase(Locale.ROOT);
            Object data = resolveData(step.get("data"), context);

            Files.createDirectories(file.getParent() == null ? base : file.getParent());
            int rows;
            switch (format) {
                case "CSV" -> rows = writeCsv(file, asRows(data));
                case "JSON" -> {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
                    rows = data instanceof List<?> list ? list.size() : 1;
                }
                case "XLSX", "XLS" -> rows = writeXlsx(file, asRows(data));
                default -> throw new IllegalArgumentException("Format de fichier non supporté : " + format);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", file.toAbsolutePath().toString());
            result.put("format", format);
            result.put("rows", rows);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Écriture du fichier échouée : " + rootMessage(e), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asRows(Object data) {
        if (data instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                } else {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("value", item);
                    rows.add(row);
                }
            }
            return rows;
        }
        if (data instanceof Map<?, ?> map) {
            return new ArrayList<>(List.of((Map<String, Object>) map));
        }
        return List.of();
    }

    private Path resolveBase(Map<String, Object> step, ExecutionContext context) {
        Object rawId = templates.resolve(step.get("connectionId"), context);
        if (rawId != null && !String.valueOf(rawId).isBlank()) {
            Long id = rawId instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(rawId));
            ConnectionService.ConnectionSettings settings = connectionService.settings(id);
            if (settings.connection().getType() != ConnectionType.FILE) {
                throw new IllegalArgumentException("La connexion " + id + " n'est pas de type FILE");
            }
            JsonNode config = settings.baseConfig();
            String basePath = config.hasNonNull("basePath") ? config.get("basePath").asText() : null;
            return Path.of(basePath == null || basePath.isBlank() ? appProperties.files().baseDir() : basePath);
        }
        // Sans connexion FILE : le contexte de l'organisation surcharge la racine globale.
        Object orgBase = templates.getByPath("org.overrides.files.baseDir", context);
        String baseDir = orgBase == null || String.valueOf(orgBase).isBlank()
                ? appProperties.files().baseDir() : String.valueOf(orgBase);
        return Path.of(baseDir);
    }

    private Path secureResolve(Path base, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("Le chemin du fichier est requis");
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relative).toAbsolutePath().normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("Accès au chemin refusé : " + relative);
        }
        return resolved;
    }

    private List<Map<String, Object>> readCsv(Path file) throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < line.length ? line[i] : null);
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private List<Map<String, Object>> readXlsx(Path file) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            XSSFSheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            XSSFRow headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }
            int lastCell = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < lastCell; i++) {
                headers.add(string(cellValue(headerRow.getCell(i)), "column_" + i));
            }
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                XSSFRow row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String value = cellValue(row.getCell(i));
                    item.put(headers.get(i), value);
                    if (value != null && !value.isBlank()) {
                        hasValue = true;
                    }
                }
                if (hasValue) {
                    rows.add(item);
                }
            }
            return rows;
        }
    }

    private int writeCsv(Path file, List<Map<String, Object>> rows) throws Exception {
        Set<String> headers = new LinkedHashSet<>();
        rows.forEach(row -> headers.addAll(row.keySet()));
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8),
                ';', CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(headers.toArray(new String[0]));
            for (Map<String, Object> row : rows) {
                String[] line = headers.stream().map(header -> string(row.get(header), "")).toArray(String[]::new);
                writer.writeNext(line);
            }
        }
        return rows.size();
    }

    private int writeXlsx(Path file, List<Map<String, Object>> rows) throws Exception {
        Set<String> headers = new LinkedHashSet<>();
        rows.forEach(row -> headers.addAll(row.keySet()));
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("data");
            XSSFRow header = sheet.createRow(0);
            int column = 0;
            for (String name : headers) {
                header.createCell(column++).setCellValue(name);
            }
            int rowIndex = 1;
            for (Map<String, Object> row : rows) {
                XSSFRow line = sheet.createRow(rowIndex++);
                int cellIndex = 0;
                for (String name : headers) {
                    Object value = row.get(name);
                    Cell cell = line.createCell(cellIndex++);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else if (value instanceof Boolean bool) {
                        cell.setCellValue(bool);
                    } else if (value != null) {
                        cell.setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(Files.newOutputStream(file));
        }
        return rows.size();
    }

    private String cellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toString();
            }
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value) && !Double.isInfinite(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return cell.getStringCellValue();
        }
        return cell.getStringCellValue();
    }

    private Object resolveData(Object dataSpec, ExecutionContext context) {
        if (dataSpec == null) {
            return context.getOutput();
        }
        if (dataSpec instanceof String path && !path.isBlank()) {
            Object value = templates.getByPath(path, context);
            return value == null ? path : value;
        }
        return templates.resolve(dataSpec, context);
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int index = name.lastIndexOf('.');
        return index < 0 ? "CSV" : name.substring(index + 1);
    }

    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
