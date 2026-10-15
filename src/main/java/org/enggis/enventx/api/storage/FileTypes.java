package org.enggis.enventx.api.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

/** Extension allow-list plus magic-byte sniffing; the client-supplied content type is never trusted. */
public final class FileTypes {

    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> ATTACHMENT_EXTENSIONS =
            Set.of("pdf", "docx", "pptx", "xlsx", "png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> GUIDELINE_EXTENSIONS = Set.of("pdf", "docx");

    public enum Kind { ATTACHMENT, POSTER, GUIDELINE }

    private FileTypes() {
    }

    /** Returns the detected content type, or throws 400/413/415 with a machine-readable reason. */
    public static String validate(Kind kind, String fileName, byte[] head, long size) {
        if (size <= 0) {
            throw bad(HttpStatus.BAD_REQUEST, "FILE_EMPTY");
        }
        if (size > MAX_BYTES) {
            throw bad(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        var extension = extensionOf(fileName);
        var allowed = switch (kind) {
            case ATTACHMENT -> ATTACHMENT_EXTENSIONS;
            case POSTER -> IMAGE_EXTENSIONS;
            case GUIDELINE -> GUIDELINE_EXTENSIONS;
        };
        if (!allowed.contains(extension)) {
            throw bad(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE_NOT_ALLOWED");
        }
        var sniffed = sniff(head);
        if (sniffed == null || !matches(extension, sniffed)) {
            throw bad(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_CONTENT_MISMATCH");
        }
        return contentType(extension, sniffed);
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        var dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Last path segment only, control characters removed, length limited. */
    public static String cleanFileName(String original) {
        var name = original == null ? "file" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    /** Header-safe variant used for the download name in signed URLs. */
    public static String headerSafeName(String fileName) {
        return cleanFileName(fileName).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private enum Sniffed { PDF, PNG, JPEG, GIF, WEBP, ZIP }

    private static Sniffed sniff(byte[] h) {
        if (h == null || h.length < 12) {
            return null;
        }
        if (h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') return Sniffed.PDF;
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return Sniffed.PNG;
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return Sniffed.JPEG;
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return Sniffed.GIF;
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return Sniffed.WEBP;
        if (h[0] == 'P' && h[1] == 'K' && h[2] == 3 && h[3] == 4) return Sniffed.ZIP;
        return null;
    }

    private static boolean matches(String extension, Sniffed sniffed) {
        return switch (sniffed) {
            case PDF -> extension.equals("pdf");
            case PNG -> extension.equals("png");
            case JPEG -> extension.equals("jpg") || extension.equals("jpeg");
            case GIF -> extension.equals("gif");
            case WEBP -> extension.equals("webp");
            case ZIP -> Set.of("docx", "pptx", "xlsx").contains(extension);
        };
    }

    private static String contentType(String extension, Sniffed sniffed) {
        return switch (sniffed) {
            case PDF -> "application/pdf";
            case PNG -> "image/png";
            case JPEG -> "image/jpeg";
            case GIF -> "image/gif";
            case WEBP -> "image/webp";
            case ZIP -> switch (extension) {
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            };
        };
    }

    private static ResponseStatusException bad(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
