package org.enggis.enventx.api.whatsapp;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class CloudApiWhatsAppClient implements WhatsAppClient {

    private static final int MAX_ERROR_LENGTH = 300;

    private final WhatsAppProperties properties;
    private final RestClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public CloudApiWhatsAppClient(WhatsAppProperties properties,
                                  org.springframework.beans.factory.ObjectProvider<RestClient.Builder> builders) {
        this(properties, builders.getIfAvailable(RestClient::builder));
    }

    public CloudApiWhatsAppClient(WhatsAppProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http = builder.baseUrl(properties.apiBaseUrl()).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String sendTemplate(String toNumber, String templateName, String languageCode, List<String> bodyParameters) {
        var parameters = bodyParameters.stream().map(text -> Map.of("type", "text", "text", text)).toList();
        var body = Map.of(
                "messaging_product", "whatsapp",
                "to", toNumber,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", languageCode),
                        "components", List.of(Map.of("type", "body", "parameters", parameters))));
        try {
            var response = http.post()
                    .uri("/{id}/messages", properties.phoneNumberId())
                    .header("Authorization", "Bearer " + properties.accessToken())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.get("messages") instanceof List<?> messages && !messages.isEmpty()
                    && messages.get(0) instanceof Map<?, ?> first && first.get("id") != null) {
                return first.get("id").toString();
            }
            throw new WhatsAppSendException("WhatsApp accepted the request but returned no message id");
        } catch (RestClientResponseException e) {
            var detail = e.getResponseBodyAsString();
            throw new WhatsAppSendException("WhatsApp API returned HTTP " + e.getStatusCode().value()
                    + (detail.isBlank() ? "" : ": " + truncate(detail)), e);
        } catch (RestClientException e) {
            throw new WhatsAppSendException("WhatsApp API is unreachable", e);
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
