package com.htttdn.crm.util;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** RFC 4180 CSV rendering with a UTF-8 BOM so Excel opens Vietnamese text correctly. */
public final class Csv {
    private Csv() {}

    public static String line(Object... values) {
        return Arrays.stream(values).map(Csv::cell).collect(Collectors.joining(","));
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
            ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    public static ResponseEntity<String> response(String filename, List<String> rows) {
        String content = "\uFEFF" + String.join("\r\n", rows) + "\r\n";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(content);
    }
}
