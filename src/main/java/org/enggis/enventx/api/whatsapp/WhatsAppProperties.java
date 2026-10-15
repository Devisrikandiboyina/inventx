package org.enggis.enventx.api.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * WhatsApp Business Platform (Cloud API) settings. Everything comes from the environment; the token never
 * leaves the backend. The template must have two body variables: {{1}} announcement title, {{2}} text.
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        String apiBaseUrl,
        String phoneNumberId,
        String accessToken,
        String templateName,
        String languageCode,
        List<String> recipients,
        String adminWorkflowUrl
) {

    private static final Pattern E164_DIGITS = Pattern.compile("\\d{8,15}");

    public WhatsAppProperties {
        apiBaseUrl = blankToDefault(apiBaseUrl, "https://graph.facebook.com/v21.0");
        phoneNumberId = blankToNull(phoneNumberId);
        accessToken = blankToNull(accessToken);
        templateName = blankToNull(templateName);
        languageCode = blankToDefault(languageCode, "en");
        recipients = recipients == null ? List.of() : recipients.stream()
                .filter(r -> r != null)
                .map(r -> r.replaceAll("[\\s\\-()]", "").replaceFirst("^\\+", ""))
                .filter(r -> E164_DIGITS.matcher(r).matches())
                .distinct()
                .toList();
        var url = blankToNull(adminWorkflowUrl);
        adminWorkflowUrl = url != null && url.toLowerCase(Locale.ROOT).startsWith("https://") ? url : null;
    }

    public boolean configured() {
        return phoneNumberId != null && accessToken != null && templateName != null && !recipients.isEmpty();
    }

    public List<String> missingSettings() {
        var missing = new java.util.ArrayList<String>();
        if (phoneNumberId == null) {
            missing.add("whatsapp.phone-number-id");
        }
        if (accessToken == null) {
            missing.add("whatsapp.access-token");
        }
        if (templateName == null) {
            missing.add("whatsapp.template-name");
        }
        if (recipients.isEmpty()) {
            missing.add("whatsapp.recipients");
        }
        return missing;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        var v = blankToNull(value);
        return v == null ? fallback : v;
    }
}
