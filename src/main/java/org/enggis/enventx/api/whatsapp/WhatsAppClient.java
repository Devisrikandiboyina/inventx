package org.enggis.enventx.api.whatsapp;

import java.util.List;

public interface WhatsAppClient {

    /** Sends an approved template message to one opted-in number and returns the provider message id. */
    String sendTemplate(String toNumber, String templateName, String languageCode, List<String> bodyParameters);
}
