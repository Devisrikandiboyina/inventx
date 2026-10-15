package org.enggis.enventx.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextSanitizerTest {

    @Test
    void stripsTagsScriptsAndComments() {
        assertThat(TextSanitizer.clean("Hi <b>there</b><script>alert(1)</script><!-- x --> you"))
                .isEqualTo("Hi there you");
    }

    @Test
    void removesControlCharactersButKeepsNewlines() {
        assertThat(TextSanitizer.clean("a\u0000b\r\nc\td")).isEqualTo("ab\nc\td");
    }

    @Test
    void nullStaysNull() {
        assertThat(TextSanitizer.clean(null)).isNull();
    }

    @Test
    void requiredRejectsEmptyResult() {
        assertThatThrownBy(() -> TextSanitizer.required("<script>x</script>  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TEXT_REQUIRED");
    }
}