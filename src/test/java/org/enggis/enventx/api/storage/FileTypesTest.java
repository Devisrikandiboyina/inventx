package org.enggis.enventx.api.storage;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileTypesTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 0, 0};

    private static int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test
    void acceptsMatchingExtensionAndContent() {
        assertEquals("application/pdf", FileTypes.validate(FileTypes.Kind.ATTACHMENT, "Plan.PDF", PDF, 100));
        assertEquals("image/png", FileTypes.validate(FileTypes.Kind.POSTER, "poster.png", PNG, 100));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileTypes.validate(FileTypes.Kind.GUIDELINE, "rules.docx", ZIP, 100));
    }

    @Test
    void rejectsDisguisedAndDisallowedFiles() {
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "evil.pdf", PNG, 100)));
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "run.exe", PDF, 100)));
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "noext", PDF, 100)));
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "a.html",
                "<html><script>x</script>".getBytes(StandardCharsets.US_ASCII), 100)));
    }

    @Test
    void posterAcceptsImagesOnlyAndGuidelinesPdfOrDocxOnly() {
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.POSTER, "p.pdf", PDF, 100)));
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.GUIDELINE, "g.png", PNG, 100)));
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.GUIDELINE, "g.xlsx", ZIP, 100)));
    }

    @Test
    void enforcesSizeLimits() {
        assertEquals(400, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "a.pdf", PDF, 0)));
        assertEquals(413, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "a.pdf", PDF,
                FileTypes.MAX_BYTES + 1)));
        FileTypes.validate(FileTypes.Kind.ATTACHMENT, "a.pdf", PDF, FileTypes.MAX_BYTES);
    }

    @Test
    void rejectsTooShortContent() {
        assertEquals(415, status(() -> FileTypes.validate(FileTypes.Kind.ATTACHMENT, "a.pdf",
                Arrays.copyOf(PDF, 5), 5)));
    }

    @Test
    void fileNamesAreStrippedOfPathsAndUnsafeCharacters() {
        assertEquals("passwd.pdf", FileTypes.cleanFileName("../../etc/passwd.pdf"));
        assertEquals("a.pdf", FileTypes.cleanFileName("C:\\users\\x\\a.pdf"));
        assertEquals("file", FileTypes.cleanFileName("   "));
        assertEquals("my_plan__v2_.pdf", FileTypes.headerSafeName("my plan \"v2\".pdf"));
    }
}
