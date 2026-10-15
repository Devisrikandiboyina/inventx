package org.enggis.enventx.api.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void declaresOpenApi31BearerJwtSchemeAndSixModuleTags() {
        var api = new OpenApiConfig().inventxOpenApi();

        assertEquals("3.1.0", api.getOpenapi());
        var scheme = api.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
        assertEquals(List.of("Auth", "Ideas", "Organizer", "Announcements", "Notifications", "Guidelines"),
                api.getTags().stream().map(Tag::getName).toList());
    }
}
