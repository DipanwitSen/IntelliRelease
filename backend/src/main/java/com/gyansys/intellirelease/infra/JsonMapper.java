package com.gyansys.intellirelease.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Serialisation boundary between engine records and the JSONB columns that
 * store them.
 *
 * <p>Engine outputs are Java records; the Knowledge Repository stores them as
 * JSON documents so the exact shape an engine produced — including every
 * evidence string and provenance tag — survives verbatim for as long as the
 * platform does. A flattened relational projection would lose precisely the
 * detail that makes an old analysis defensible.
 */
@Component
public class JsonMapper {

    private final ObjectMapper objectMapper;

    public JsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialise " + value.getClass().getSimpleName(), exception);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialise into " + type.getSimpleName(), exception);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialise JSON payload", exception);
        }
    }

    /**
     * Parses without binding to a type — used where a payload is passed through
     * rather than reasoned about, such as a raw webhook body.
     */
    public com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload is not valid JSON", exception);
        }
    }

    public ObjectMapper raw() {
        return objectMapper;
    }
}
