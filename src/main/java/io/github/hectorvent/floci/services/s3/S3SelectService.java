package io.github.hectorvent.floci.services.s3;

import io.github.hectorvent.floci.core.common.AwsEventStreamEncoder;
import io.github.hectorvent.floci.core.common.XmlParser;
import io.github.hectorvent.floci.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

@ApplicationScoped
public class S3SelectService {

    private final ObjectMapper objectMapper;

    @Inject
    public S3SelectService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] select(S3Object object, String requestXml) {
        String expression = XmlParser.extractFirst(requestXml, "Expression", "").toUpperCase();
        String inputType = requestXml.contains("<CSV>") ? "CSV" : (requestXml.contains("<JSON>") ? "JSON" : null);
        
        byte[] rawData = object.getData();
        if (rawData == null) return new byte[0];
        String content = new String(rawData, StandardCharsets.UTF_8);
        
        StringBuilder result = new StringBuilder();
        
        if ("CSV".equals(inputType)) {
            boolean useHeaders = requestXml.contains("<FileHeaderInfo>USE</FileHeaderInfo>");
            String filtered = S3SelectEvaluator.evaluateCsv(content, expression, useHeaders);
            result.append(filtered);
        } else if ("JSON".equals(inputType)) {
            // Assume one JSON object per line (JSON Lines) or a single array
            try {
                JsonNode node = objectMapper.readTree(content);
                if (node.isArray()) {
                    for (JsonNode item : node) {
                        result.append(objectMapper.writeValueAsString(item)).append("\n");
                    }
                } else {
                    result.append(objectMapper.writeValueAsString(node)).append("\n");
                }
            } catch (Exception e) {
                // If it's not valid JSON, just return raw or fail
                result.append(content);
            }
        } else {
            result.append(content);
        }

        return encodeEventStream(result.toString());
    }

    private byte[] encodeEventStream(String payload) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            LinkedHashMap<String, String> recordsHeaders = new LinkedHashMap<>();
            recordsHeaders.put(":message-type", "event");
            recordsHeaders.put(":event-type", "Records");
            recordsHeaders.put(":content-type", "application/octet-stream");
            baos.write(AwsEventStreamEncoder.encodeMessage(recordsHeaders, payload.getBytes(StandardCharsets.UTF_8)));

            LinkedHashMap<String, String> statsHeaders = new LinkedHashMap<>();
            statsHeaders.put(":message-type", "event");
            statsHeaders.put(":event-type", "Stats");
            statsHeaders.put(":content-type", "text/xml");
            byte[] statsPayload = "<Stats><BytesScanned>100</BytesScanned><BytesProcessed>100</BytesProcessed><BytesReturned>100</BytesReturned></Stats>".getBytes(StandardCharsets.UTF_8);
            baos.write(AwsEventStreamEncoder.encodeMessage(statsHeaders, statsPayload));

            LinkedHashMap<String, String> endHeaders = new LinkedHashMap<>();
            endHeaders.put(":message-type", "event");
            endHeaders.put(":event-type", "End");
            baos.write(AwsEventStreamEncoder.encodeMessage(endHeaders, new byte[0]));

            return baos.toByteArray();
        } catch (Exception e) {
            return payload.getBytes(StandardCharsets.UTF_8);
        }
    }
}
