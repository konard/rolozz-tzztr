package com.cor.collectorservice.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FlexibleStringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        List<String> result = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                } else if (item.isNumber()) {
                    result.add(item.asText());
                } else if (item.isBoolean()) {
                    result.add(String.valueOf(item.asBoolean()));
                } else if (!item.isNull()) {
                    result.add(item.toString());
                }
            }
        } else if (node.isTextual()) {
            result.add(node.asText());
        } else if (node.isNumber()) {
            result.add(node.asText());
            result.add(String.valueOf(node.asBoolean()));
        } else if (!node.isNull()) {
            result.add(node.toString());
        }

        return result;
    }
}
