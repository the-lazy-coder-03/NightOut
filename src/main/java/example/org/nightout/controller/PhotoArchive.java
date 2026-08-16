package example.org.nightout.controller;

import example.org.nightout.exception.BusinessRuleException;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class PhotoArchive {

    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");

    private PhotoArchive() {
    }

    static List<Long> selectedIds(List<Long> ids) {
        List<Long> selectedIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (selectedIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one photo.");
        }
        return selectedIds;
    }

    static ResponseEntity<StreamingResponseBody> zip(String filename, List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new BusinessRuleException("Select at least one photo.");
        }
        StreamingResponseBody body = outputStream -> {
            Set<String> usedFilenames = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                for (Entry entry : entries) {
                    zip.putNextEntry(new ZipEntry(uniqueFilename(entry.filename(), usedFilenames)));
                    try (InputStream input = entry.resource().getInputStream()) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        };
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename)
                .build();
        return ResponseEntity.ok()
                .contentType(ZIP_MEDIA_TYPE)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(body);
    }

    private static String uniqueFilename(String filename, Set<String> usedFilenames) {
        String sanitized = sanitizeFilename(filename);
        if (usedFilenames.add(sanitized)) {
            return sanitized;
        }

        String base = sanitized;
        String extension = "";
        int extensionStart = sanitized.lastIndexOf('.');
        if (extensionStart > 0) {
            base = sanitized.substring(0, extensionStart);
            extension = sanitized.substring(extensionStart);
        }

        int copy = 2;
        String candidate;
        do {
            candidate = base + "-" + copy + extension;
            copy++;
        } while (!usedFilenames.add(candidate));
        return candidate;
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        String normalized = filename.replace('\\', '/');
        int basenameStart = normalized.lastIndexOf('/');
        if (basenameStart >= 0) {
            normalized = normalized.substring(basenameStart + 1);
        }
        normalized = normalized.replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_')
                .trim();
        return normalized.isBlank() ? "photo.jpg" : normalized;
    }

    record Entry(String filename, Resource resource) {
    }
}
