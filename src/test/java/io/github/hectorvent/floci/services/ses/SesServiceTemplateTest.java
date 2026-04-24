package io.github.hectorvent.floci.services.ses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link SesService#applyTemplateData} covering
 * template variable substitution edge cases.
 */
class SesServiceTemplateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void undefinedVariable_replacedWithEmptyString() {
        JsonNode data = MAPPER.createObjectNode().put("name", "Alice");
        String result = SesService.applyTemplateData("Hello {{name}}, team {{team}}", data);
        assertEquals("Hello Alice, team ", result);
    }

    @Test
    void spacedVariable_matchesCorrectly() {
        JsonNode data = MAPPER.createObjectNode().put("name", "Alice");
        String result = SesService.applyTemplateData("Hello {{ name }}", data);
        assertEquals("Hello Alice", result);
    }

    @Test
    void hyphenatedVariableName() {
        JsonNode data = MAPPER.createObjectNode().put("first-name", "Alice");
        String result = SesService.applyTemplateData("Hello {{first-name}}", data);
        assertEquals("Hello Alice", result);
    }

    @Test
    void unclosedBraces_leftAsIs() {
        JsonNode data = MAPPER.createObjectNode().put("name", "Alice");
        String result = SesService.applyTemplateData("Hello {{name}} and {{foo", data);
        assertEquals("Hello Alice and {{foo", result);
    }

    @Test
    void nonStringJsonValues() throws Exception {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("count", 42);
        data.put("active", true);
        data.set("nested", MAPPER.readTree("{\"key\":\"val\"}"));

        assertEquals("Items: 42", SesService.applyTemplateData("Items: {{count}}", data));
        assertEquals("Active: true", SesService.applyTemplateData("Active: {{active}}", data));
        assertEquals("Data: {\"key\":\"val\"}", SesService.applyTemplateData("Data: {{nested}}", data));
    }

    @Test
    void emptyTemplateData_allVariablesCleared() {
        JsonNode data = MAPPER.createObjectNode();
        String result = SesService.applyTemplateData("Hello {{name}}, {{team}}", data);
        assertEquals("Hello , ", result);
    }

    @Test
    void nullTemplateData_allVariablesCleared() {
        String result = SesService.applyTemplateData("Hello {{name}}", null);
        assertEquals("Hello ", result);
    }

    @Test
    void nullText_returnsNull() {
        assertNull(SesService.applyTemplateData(null, MAPPER.createObjectNode()));
    }

    @Test
    void emptyText_returnsEmpty() {
        assertEquals("", SesService.applyTemplateData("", MAPPER.createObjectNode()));
    }

    @Test
    void noVariables_textUnchanged() {
        JsonNode data = MAPPER.createObjectNode().put("name", "Alice");
        assertEquals("Hello world", SesService.applyTemplateData("Hello world", data));
    }

    @Test
    void duplicateVariables_allReplaced() {
        JsonNode data = MAPPER.createObjectNode().put("name", "Alice");
        String result = SesService.applyTemplateData("{{name}} and {{name}}", data);
        assertEquals("Alice and Alice", result);
    }

    @Test
    void replacementWithRegexMetacharacters() {
        JsonNode data = MAPPER.createObjectNode().put("val", "price is $100 (50% off)");
        String result = SesService.applyTemplateData("The {{val}}", data);
        assertEquals("The price is $100 (50% off)", result);
    }

    @Test
    void variableNameCaseSensitive() {
        JsonNode data = MAPPER.createObjectNode().put("Name", "Alice");
        String result = SesService.applyTemplateData("Hello {{name}} and {{Name}}", data);
        assertEquals("Hello  and Alice", result);
    }

    @Test
    void emptyStringValue() {
        JsonNode data = MAPPER.createObjectNode().put("name", "");
        assertEquals("Hello ", SesService.applyTemplateData("Hello {{name}}", data));
    }
}
