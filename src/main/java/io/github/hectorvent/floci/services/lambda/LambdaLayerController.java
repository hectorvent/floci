package io.github.hectorvent.floci.services.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Lambda layer endpoints — use the /2018-10-31 API version prefix.
 *
 * ListLayers:        GET /2018-10-31/layers
 * ListLayerVersions: GET /2018-10-31/layers/{LayerName}/versions
 */
@Path("/2018-10-31")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LambdaLayerController {

    private final ObjectMapper objectMapper;

    @Inject
    public LambdaLayerController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/layers")
    public Response listLayers() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("Layers");
        return Response.ok(root).build();
    }

    @GET
    @Path("/layers/{layerName}/versions")
    public Response listLayerVersions(@PathParam("layerName") String layerName) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("LayerVersions");
        return Response.ok(root).build();
    }
}
