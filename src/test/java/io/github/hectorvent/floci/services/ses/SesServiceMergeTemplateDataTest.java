package io.github.hectorvent.floci.services.ses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SesServiceMergeTemplateDataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void bothNull_returnsNull() {
        assertNull(SesService.mergeTemplateData(null, null));
    }

    @Test
    void emptyReplacement_returnsDefaultsWithoutCopy() {
        JsonNode defaults = MAPPER.createObjectNode().put("team", "floci");
        JsonNode replacement = MAPPER.createObjectNode();
        assertSame(defaults, SesService.mergeTemplateData(defaults, replacement));
    }

    @Test
    void emptyDefaults_returnsReplacementWithoutCopy() {
        JsonNode defaults = MAPPER.createObjectNode();
        JsonNode replacement = MAPPER.createObjectNode().put("name", "Alice");
        assertSame(replacement, SesService.mergeTemplateData(defaults, replacement));
    }

    @Test
    void bothNonEmpty_replacementOverridesDefaults() {
        JsonNode defaults = MAPPER.createObjectNode().put("team", "floci").put("name", "default");
        JsonNode replacement = MAPPER.createObjectNode().put("name", "Alice");
        JsonNode merged = SesService.mergeTemplateData(defaults, replacement);
        assertEquals("Alice", merged.path("name").asText());
        assertEquals("floci", merged.path("team").asText());
    }
}
