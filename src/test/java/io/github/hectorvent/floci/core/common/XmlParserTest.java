package io.github.hectorvent.floci.core.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XmlParserTest {

    @Test
    void extractGroupsMulti_withNestedElements_skipsNestedAndReturnsNull() {
        String xml = """
                <Root>
                  <Item>
                    <Id>1</Id>
                    <Nested>
                      <Child>value</Child>
                    </Nested>
                    <Name>Test</Name>
                  </Item>
                </Root>
                """;

        List<Map<String, List<String>>> result = XmlParser.extractGroupsMulti(xml, "Item");

        assertEquals(1, result.size());
        Map<String, List<String>> group = result.get(0);
        assertEquals(List.of("1"), group.get("Id"));
        assertEquals(List.of("Test"), group.get("Name"));
        assertFalse(group.containsKey("Nested"), "Nested element should be skipped (returned null)");
    }

    @Test
    void extractGroupsMulti_withEmptyElement_returnsEmptyString() {
        String xml = """
                <Root>
                  <Item>
                    <Id>1</Id>
                    <Empty/>
                    <Name>Test</Name>
                  </Item>
                </Root>
                """;

        List<Map<String, List<String>>> result = XmlParser.extractGroupsMulti(xml, "Item");

        assertEquals(1, result.size());
        Map<String, List<String>> group = result.get(0);
        assertEquals(List.of("1"), group.get("Id"));
        assertEquals(List.of(""), group.get("Empty"));
        assertEquals(List.of("Test"), group.get("Name"));
    }

    @Test
    void extractGroupsMulti_withMixedContent_skipsAndReturnsNull() {
        String xml = """
                <Root>
                  <Item>
                    <Mixed>text<Child/>more text</Mixed>
                    <Valid>ok</Valid>
                  </Item>
                </Root>
                """;

        List<Map<String, List<String>>> result = XmlParser.extractGroupsMulti(xml, "Item");

        assertEquals(1, result.size());
        Map<String, List<String>> group = result.get(0);
        assertFalse(group.containsKey("Mixed"));
        assertEquals(List.of("ok"), group.get("Valid"));
    }

    @Test
    void extractGroups_withNestedElements_skipsNested() {
        String xml = """
                <Root>
                  <Item>
                    <Id>1</Id>
                    <Filter>
                       <Key>prefix</Key>
                    </Filter>
                    <Name>Test</Name>
                  </Item>
                </Root>
                """;

        List<Map<String, String>> result = XmlParser.extractGroups(xml, "Item");

        assertEquals(1, result.size());
        Map<String, String> group = result.get(0);
        assertEquals("1", group.get("Id"));
        assertEquals("Test", group.get("Name"));
        assertNull(group.get("Filter"));
    }
}
