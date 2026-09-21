package com.labelmate.labelmate.service;

import com.labelmate.labelmate.exception.ApiException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Robust CSV parsing for tabular (multi-column) datasets.
 *
 * <p>Handles quoted commas, escaped quotes, and multiline fields — naive
 * split-on-newline parsing corrupts such files. Streams from the upload so
 * large files need no special handling beyond the multipart limits.
 */
@Service
public class CsvTableParser {

    static final int MAX_COLUMNS = 100;
    static final int MAX_ROWS_PER_FILE = 100_000;
    static final int MAX_CELL_CHARS = 8000;

    public static final class ParsedTable {
        private final List<String> columns;
        private final List<Map<String, String>> rows;

        public ParsedTable(List<String> columns, List<Map<String, String>> rows) {
            this.columns = List.copyOf(columns);
            this.rows = List.copyOf(rows);
        }

        public List<String> getColumns() {
            return columns;
        }

        public List<Map<String, String>> getRows() {
            return rows;
        }
    }

    /**
     * Parses one uploaded CSV/TXT file into header columns plus data rows.
     * A header row is required; blank lines and all-blank rows are skipped;
     * duplicate headers are deduplicated ({@code name_2}); cells truncate at
     * {@value #MAX_CELL_CHARS} characters.
     */
    public ParsedTable parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file is empty");
        }
        try (InputStream in = file.getInputStream()) {
            return parse(in, file.getOriginalFilename());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the file: " + ex.getMessage());
        }
    }

    /**
     * Parses an already-stored file (e.g. a dataset attachment being ingested
     * after upload). Consumes and closes the stream.
     */
    public ParsedTable parse(InputStream in, String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (!(name.endsWith(".csv") || name.endsWith(".txt"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only .csv or .txt files are accepted");
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(false)
                .build();
        try (InputStream stream = in;
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                CSVParser parser = format.parse(reader)) {
            List<String> clean = cleanHeaders(new ArrayList<>(parser.getHeaderNames()));
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS_PER_FILE) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Too many rows (max " + MAX_ROWS_PER_FILE + " per file; split and upload in parts)");
                }
                Map<String, String> row = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int i = 0; i < clean.size(); i++) {
                    String value = "";
                    if (i < record.size() && record.get(i) != null) {
                        value = record.get(i).trim();
                    }
                    if (value.length() > MAX_CELL_CHARS) {
                        value = value.substring(0, MAX_CELL_CHARS);
                    }
                    if (!value.isEmpty()) {
                        allBlank = false;
                    }
                    row.put(clean.get(i), value);
                }
                if (!allBlank) {
                    rows.add(row);
                }
            }
            if (rows.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "No data rows found in the file");
            }
            return new ParsedTable(clean, rows);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not parse the file: " + ex.getMessage());
        }
    }

    private List<String> cleanHeaders(List<String> raw) {
        if (raw.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file has no header row");
        }
        if (raw.size() > MAX_COLUMNS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Too many columns (" + raw.size() + ", max " + MAX_COLUMNS + ")");
        }
        List<String> clean = new ArrayList<>();
        for (String column : raw) {
            String name = column == null ? "" : column.trim().replace("\uFEFF", "");
            if (name.isEmpty()) {
                continue;
            }
            String candidate = name;
            int suffix = 2;
            while (clean.contains(candidate)) {
                candidate = name + "_" + (suffix++);
            }
            clean.add(candidate);
        }
        if (clean.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The header has no usable column names");
        }
        return clean;
    }
}
