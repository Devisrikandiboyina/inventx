package org.enggis.enventx.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    public static final String TAG_AUTH = "Auth";
    public static final String TAG_IDEAS = "Ideas";
    public static final String TAG_ORGANIZER = "Organizer";
    public static final String TAG_ANNOUNCEMENTS = "Announcements";
    public static final String TAG_NOTIFICATIONS = "Notifications";
    public static final String TAG_GUIDELINES = "Guidelines";

    @Bean
    OpenAPI inventxOpenApi() {
        return new OpenAPI()
                .openapi("3.1.0")
                .info(new Info()
                        .title("InventX API")
                        .version("v1")
                        .description("Idea submission, review and stage-gate platform"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken returned by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .tags(List.of(
                        tag(TAG_AUTH, "Signup and login"),
                        tag(TAG_IDEAS, "Idea lifecycle, team members and likes"),
                        tag(TAG_ORGANIZER, "Stage transitions and review (ORGANIZER / ADMIN)"),
                        tag(TAG_ANNOUNCEMENTS, "Pinned and scheduled announcements"),
                        tag(TAG_NOTIFICATIONS, "Per-user notifications"),
                        tag(TAG_GUIDELINES, "Participation guidelines and stage configuration")));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
