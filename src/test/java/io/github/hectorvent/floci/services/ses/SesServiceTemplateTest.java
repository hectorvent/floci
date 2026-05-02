package io.github.hectorvent.floci.services.ses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link SesService#applyTemplateData} covering
 * template variable substitution edge cases.
 */
class SesServiceTemplateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void undefinedVariable_throwsMissingRenderingAttribute() {
        JsonNode data = MAPPER.createObjectNode().put("name", "Alice");
        AwsException ex = assertThrows(AwsException.class,
                () -> SesService.applyTemplateData("Hello {{name}}, team {{team}}", data));
        assertEquals("MissingRenderingAttribute", ex.getErrorCode());
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
    void emptyTemplateData_throwsMissingRenderingAttribute() {
        JsonNode data = MAPPER.createObjectNode();
        AwsException ex = assertThrows(AwsException.class,
                () -> SesService.applyTemplateData("Hello {{name}}, {{team}}", data));
        assertEquals("MissingRenderingAttribute", ex.getErrorCode());
    }

    @Test
    void nullTemplateData_throwsMissingRenderingAttribute() {
        AwsException ex = assertThrows(AwsException.class,
                () -> SesService.applyTemplateData("Hello {{name}}", null));
        assertEquals("MissingRenderingAttribute", ex.getErrorCode());
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
    void variableNameCaseSensitive_matchesExact() {
        JsonNode data = MAPPER.createObjectNode().put("Name", "Alice");
        assertEquals("Hello Alice", SesService.applyTemplateData("Hello {{Name}}", data));
    }

    @Test
    void variableNameCaseSensitive_throwsForCaseMismatch() {
        JsonNode data = MAPPER.createObjectNode().put("Name", "Alice");
        AwsException ex = assertThrows(AwsException.class,
                () -> SesService.applyTemplateData("Hello {{name}}", data));
        assertEquals("MissingRenderingAttribute", ex.getErrorCode());
    }

    @Test
    void emptyStringValue() {
        JsonNode data = MAPPER.createObjectNode().put("name", "");
        assertEquals("Hello ", SesService.applyTemplateData("Hello {{name}}", data));
    }

    @Test
    void buildTestRenderMime_asciiBody_uses7bit() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime("Hello", "Hi there", "<p>Hi</p>", date, "BOUND");
        assertEquals(true, mime.contains("Subject: Hello\r\n"));
        assertEquals(true, mime.contains("Content-Type: multipart/alternative; boundary=\"BOUND\""));
        assertEquals(true, mime.contains("Content-Transfer-Encoding: 7bit"));
        assertEquals(false, mime.contains("Content-Transfer-Encoding: 8bit"));
        assertEquals(true, mime.endsWith("--BOUND--\r\n"));
    }

    @Test
    void buildTestRenderMime_utf8Body_uses8bit() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime("件名", "こんにちは", "<p>こんにちは</p>", date, "BOUND");
        assertEquals(true, mime.contains("Subject: 件名\r\n"));
        assertEquals(true, mime.contains("Content-Transfer-Encoding: 8bit"));
        assertEquals(true, mime.contains("こんにちは"));
    }

    @Test
    void buildTestRenderMime_subjectStripsCRLF() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime("Multi\r\nLine", "x", "x", date, "BOUND");
        // CR and LF are both C0 controls and are replaced with spaces.
        assertEquals(true, mime.contains("Subject: Multi  Line\r\n"));
    }

    @Test
    void pickTransferEncoding_asciiOnly() {
        assertEquals("7bit", SesService.pickTransferEncoding("Hello world"));
        assertEquals("7bit", SesService.pickTransferEncoding(""));
    }

    @Test
    void pickTransferEncoding_nonAscii() {
        assertEquals("8bit", SesService.pickTransferEncoding("こんにちは"));
        assertEquals("8bit", SesService.pickTransferEncoding("café"));
    }

    @Test
    void parseRenderingData_invalidJson_throwsInvalidRenderingParameter() {
        AwsException ex = assertThrows(AwsException.class,
                () -> SesService.parseRenderingData(MAPPER, "{not json"));
        assertEquals("InvalidRenderingParameter", ex.getErrorCode());
    }

    @Test
    void parseRenderingData_nonObject_throwsInvalidRenderingParameter() {
        AwsException ex = assertThrows(AwsException.class,
                () -> SesService.parseRenderingData(MAPPER, "[1,2,3]"));
        assertEquals("InvalidRenderingParameter", ex.getErrorCode());
    }

    @Test
    void parseRenderingData_blank_throwsInvalidRenderingParameter() {
        // Matches moto's TestRenderTemplate behavior: blank input is not valid JSON.
        assertEquals("InvalidRenderingParameter",
                assertThrows(AwsException.class, () -> SesService.parseRenderingData(MAPPER, null)).getErrorCode());
        assertEquals("InvalidRenderingParameter",
                assertThrows(AwsException.class, () -> SesService.parseRenderingData(MAPPER, "")).getErrorCode());
        assertEquals("InvalidRenderingParameter",
                assertThrows(AwsException.class, () -> SesService.parseRenderingData(MAPPER, "   ")).getErrorCode());
    }

    @Test
    void parseRenderingData_emptyObject_accepted() {
        assertEquals(true, SesService.parseRenderingData(MAPPER, "{}").isObject());
    }

    @Test
    void normalizeToCrlf_lfOnly_convertedToCrlf() {
        assertEquals("a\r\nb\r\nc", SesService.normalizeToCrlf("a\nb\nc"));
    }

    @Test
    void normalizeToCrlf_crOnly_convertedToCrlf() {
        assertEquals("a\r\nb\r\nc", SesService.normalizeToCrlf("a\rb\rc"));
    }

    @Test
    void normalizeToCrlf_alreadyCrlf_unchanged() {
        assertEquals("a\r\nb\r\nc", SesService.normalizeToCrlf("a\r\nb\r\nc"));
    }

    @Test
    void normalizeToCrlf_mixed_normalizesAll() {
        assertEquals("a\r\nb\r\nc\r\nd", SesService.normalizeToCrlf("a\nb\rc\r\nd"));
    }

    @Test
    void buildTestRenderMime_bodyWithBareLf_normalizedToCrlf() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime("S", "line1\nline2", "<p>x\ny</p>", date, "BOUND");
        assertEquals(true, mime.contains("line1\r\nline2"));
        assertEquals(true, mime.contains("x\r\ny"));
        assertEquals(false, mime.contains("line1\nline2"));
    }

    @Test
    void buildTestRenderMime_bodyEndingWithNewline_noExtraBlankLine() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime("S", "hello\n", "<p>hi</p>\n", date, "BOUND");
        assertEquals(false, mime.contains("hello\r\n\r\n--BOUND"));
        assertEquals(true, mime.contains("hello\r\n--BOUND"));
        assertEquals(false, mime.contains("</p>\r\n\r\n--BOUND"));
        assertEquals(true, mime.contains("</p>\r\n--BOUND"));
    }

    @Test
    void buildTestRenderMime_bodyWithoutTrailingNewline_addsCrlfBeforeBoundary() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime("S", "hello", "<p>hi</p>", date, "BOUND");
        assertEquals(true, mime.contains("hello\r\n--BOUND"));
        assertEquals(true, mime.contains("</p>\r\n--BOUND"));
    }

    @Test
    void mapErrorCodeToBulkStatus_invalidParameterValue() {
        assertEquals(io.github.hectorvent.floci.services.ses.model.BulkEmailEntryResult.Status.INVALID_PARAMETER,
                SesService.mapErrorCodeToBulkStatus("InvalidParameterValue"));
    }

    @Test
    void mapErrorCodeToBulkStatus_missingRenderingAttribute() {
        assertEquals(io.github.hectorvent.floci.services.ses.model.BulkEmailEntryResult.Status.INVALID_PARAMETER,
                SesService.mapErrorCodeToBulkStatus("MissingRenderingAttribute"));
    }

    @Test
    void mapErrorCodeToBulkStatus_invalidRenderingParameter() {
        assertEquals(io.github.hectorvent.floci.services.ses.model.BulkEmailEntryResult.Status.INVALID_PARAMETER,
                SesService.mapErrorCodeToBulkStatus("InvalidRenderingParameter"));
    }

    @Test
    void mapErrorCodeToBulkStatus_unknownCode_failed() {
        assertEquals(io.github.hectorvent.floci.services.ses.model.BulkEmailEntryResult.Status.FAILED,
                SesService.mapErrorCodeToBulkStatus("SomethingElse"));
    }

    @Test
    void sanitizeSubject_replacesC0ControlCharsWithSpace() {
        assertEquals("a b c", SesService.sanitizeSubject("a\u0001b\u001fc"));
        assertEquals("x y z", SesService.sanitizeSubject("x\ry\nz"));
        assertEquals("a b", SesService.sanitizeSubject("a\u0007b"));
    }

    @Test
    void sanitizeSubject_replacesDelWithSpace() {
        assertEquals("a b", SesService.sanitizeSubject("a\u007fb"));
    }

    @Test
    void sanitizeSubject_nullReturnsEmpty() {
        assertEquals("", SesService.sanitizeSubject(null));
    }

    @Test
    void sanitizeSubject_preservesPrintableAndUnicode() {
        assertEquals("Hello 太郎", SesService.sanitizeSubject("Hello 太郎"));
        assertEquals("Hello!", SesService.sanitizeSubject("Hello!"));
    }

    @Test
    void stripXml10InvalidChars_keepsTabNewlineCarriageReturn() {
        assertEquals("a\tb\nc\rd", SesService.stripXml10InvalidChars("a\tb\nc\rd"));
    }

    @Test
    void stripXml10InvalidChars_removesC0ControlsExceptWhitespace() {
        assertEquals("abc", SesService.stripXml10InvalidChars("a\u0001b\u001fc"));
        assertEquals("ab", SesService.stripXml10InvalidChars("a\u0008b"));
        assertEquals("ab", SesService.stripXml10InvalidChars("a\u000bb"));
        assertEquals("ab", SesService.stripXml10InvalidChars("a\u000cb"));
    }

    @Test
    void stripXml10InvalidChars_preservesUnicode() {
        assertEquals("件名 太郎", SesService.stripXml10InvalidChars("件名 太郎"));
    }

    @Test
    void stripXml10InvalidChars_removesNoncharacters() {
        assertEquals("ab", SesService.stripXml10InvalidChars("a\ufffeb"));
        assertEquals("ab", SesService.stripXml10InvalidChars("a\uffffb"));
    }

    @Test
    void stripXml10InvalidChars_removesLoneSurrogates() {
        assertEquals("ab", SesService.stripXml10InvalidChars("a\ud800b")); // lone high
        assertEquals("ab", SesService.stripXml10InvalidChars("a\udc00b")); // lone low
    }

    @Test
    void stripXml10InvalidChars_preservesPairedSupplementary() {
        // U+1F600 GRINNING FACE encoded as surrogate pair D83D DE00
        String emoji = "😀";
        assertEquals("a" + emoji + "b", SesService.stripXml10InvalidChars("a" + emoji + "b"));
    }

    @Test
    void buildTestRenderMime_subjectWithControlChars_replacedWithSpace() {
        java.time.ZonedDateTime date = java.time.ZonedDateTime.parse("2026-05-02T12:00:00Z");
        String mime = SesService.buildTestRenderMime(
                "Hello\u0001World", "x", "x", date, "BOUND");
        assertEquals(true, mime.contains("Subject: Hello World\r\n"));
        assertEquals(false, mime.contains("\u0001"));
    }
}
