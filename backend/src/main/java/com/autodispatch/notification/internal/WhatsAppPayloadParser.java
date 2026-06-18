package com.autodispatch.notification.internal;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts user messages from Meta webhook payloads
 * (entry[].changes[].value.messages[]). Delivery-status events carry no user
 * message and are ignored. Garbage payloads parse to an empty list — the
 * webhook still acknowledges them.
 */
@Component
class WhatsAppPayloadParser {

    record InboundMessage(String messageId, String fromWaId, String text, String buttonPayload) {
    }

    private static final Logger log = LoggerFactory.getLogger(WhatsAppPayloadParser.class);

    private final ObjectMapper objectMapper;

    WhatsAppPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<InboundMessage> parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            List<InboundMessage> out = new ArrayList<>();
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode msg : change.path("value").path("messages")) {
                        String id = stringOrNull(msg.path("id"));
                        String from = stringOrNull(msg.path("from"));
                        if (id == null || from == null) {
                            continue;
                        }
                        String type = stringOrNull(msg.path("type"));
                        String text = null;
                        String buttonPayload = null;
                        if ("text".equals(type)) {
                            text = stringOrNull(msg.path("text").path("body"));
                        } else if ("interactive".equals(type)) {
                            buttonPayload = stringOrNull(msg.path("interactive").path("button_reply").path("id"));
                        }
                        out.add(new InboundMessage(id, from, text, buttonPayload));
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("Unparseable webhook payload ignored");
            return List.of();
        }
    }

    private static String stringOrNull(JsonNode node) {
        return node.isString() ? node.stringValue() : null;
    }
}
