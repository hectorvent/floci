package io.github.hectorvent.floci.services.scheduler;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.services.scheduler.model.ScheduleGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AWS EventBridge Scheduler REST endpoints.
 * Paths mirror the AWS SDK v2 SchedulerClient (e.g. {@code POST /schedule-groups/{Name}}).
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchedulerController {

    private static final Logger LOG = Logger.getLogger(SchedulerController.class);

    private final SchedulerService schedulerService;
    private final RegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Inject
    public SchedulerController(SchedulerService schedulerService,
                               RegionResolver regionResolver,
                               ObjectMapper objectMapper) {
        this.schedulerService = schedulerService;
        this.regionResolver = regionResolver;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────── CreateScheduleGroup ────────────────────────────

    @POST
    @Path("/schedule-groups/{name}")
    public Response createScheduleGroup(@Context HttpHeaders headers,
                                        @PathParam("name") String name,
                                        String body) {
        String region = regionResolver.resolveRegion(headers);
        try {
            Map<String, String> tags = parseTags(body);
            ScheduleGroup group = schedulerService.createScheduleGroup(name, tags, region);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("ScheduleGroupArn", group.getArn());
            return Response.ok(response).build();
        } catch (AwsException e) {
            throw e;
        } catch (Exception e) {
            throw new AwsException("ValidationException", e.getMessage(), 400);
        }
    }

    // ──────────────────────────── GetScheduleGroup ────────────────────────────

    @GET
    @Path("/schedule-groups/{name}")
    public Response getScheduleGroup(@Context HttpHeaders headers,
                                     @PathParam("name") String name) {
        String region = regionResolver.resolveRegion(headers);
        ScheduleGroup group = schedulerService.getScheduleGroup(name, region);
        return Response.ok(buildGroupResponse(group)).build();
    }

    // ──────────────────────────── DeleteScheduleGroup ────────────────────────────

    @DELETE
    @Path("/schedule-groups/{name}")
    public Response deleteScheduleGroup(@Context HttpHeaders headers,
                                        @PathParam("name") String name) {
        String region = regionResolver.resolveRegion(headers);
        schedulerService.deleteScheduleGroup(name, region);
        return Response.ok().build();
    }

    // ──────────────────────────── ListScheduleGroups ────────────────────────────

    @GET
    @Path("/schedule-groups")
    public Response listScheduleGroups(@Context HttpHeaders headers,
                                       @QueryParam("NamePrefix") String namePrefix) {
        String region = regionResolver.resolveRegion(headers);
        List<ScheduleGroup> groups = schedulerService.listScheduleGroups(namePrefix, region);
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode items = response.putArray("ScheduleGroups");
        for (ScheduleGroup group : groups) {
            items.add(objectMapper.valueToTree(buildGroupResponse(group)));
        }
        return Response.ok(response).build();
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private Map<String, Object> buildGroupResponse(ScheduleGroup group) {
        Map<String, Object> response = new HashMap<>();
        response.put("Name", group.getName());
        response.put("Arn", group.getArn());
        response.put("State", group.getState());
        if (group.getCreationDate() != null) {
            response.put("CreationDate", group.getCreationDate().getEpochSecond());
        }
        if (group.getLastModificationDate() != null) {
            response.put("LastModificationDate", group.getLastModificationDate().getEpochSecond());
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseTags(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
        Object tagsObj = parsed.get("Tags");
        if (!(tagsObj instanceof List<?> tagList)) {
            return Map.of();
        }
        Map<String, String> tags = new HashMap<>();
        for (Object entry : tagList) {
            if (entry instanceof Map<?, ?> tagMap) {
                Object key = tagMap.get("Key");
                Object value = tagMap.get("Value");
                if (key != null && value != null) {
                    tags.put(key.toString(), value.toString());
                }
            }
        }
        return tags;
    }
}
