package io.github.hectorvent.floci.services.s3;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying that S3VirtualHostFilter preserves leading slashes
 * in object keys when rewriting virtual-hosted-style requests to path-style.
 */
class S3VirtualHostFilterLeadingSlashTest {

    private S3VirtualHostFilter filter;

    @BeforeEach
    void setUp() {
        filter = new S3VirtualHostFilter("http://localhost:4566", Optional.empty());
    }

    private ContainerRequestContext mockRequest(String host, URI requestUri) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(ctx.getHeaderString("Host")).thenReturn(host);
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(requestUri);
        // Capture the rewritten URI
        doNothing().when(ctx).setRequestUri(any(URI.class));
        return ctx;
    }

    @Test
    void rewritePreservesSingleLeadingSlashInKey() {
        // Virtual-hosted request: GET //file.txt on bucket.localhost
        // Original path is //file.txt (key = /file.txt)
        URI uri = URI.create("http://my-bucket.localhost:4566//file.txt");
        ContainerRequestContext ctx = mockRequest("my-bucket.localhost:4566", uri);

        filter.filter(ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(ctx).setRequestUri(captor.capture());
        URI rewritten = captor.getValue();
        // Path should be /my-bucket//file.txt — double slash preserved
        assertEquals("/my-bucket//file.txt", rewritten.getRawPath(),
            "Filter must preserve double slash when key starts with /");
    }

    @Test
    void rewritePreservesMultipleLeadingSlashesInKey() {
        // Key = //deep/file.txt → path is ///deep/file.txt
        URI uri = URI.create("http://my-bucket.localhost:4566///deep/file.txt");
        ContainerRequestContext ctx = mockRequest("my-bucket.localhost:4566", uri);

        filter.filter(ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(ctx).setRequestUri(captor.capture());
        URI rewritten = captor.getValue();
        assertEquals("/my-bucket///deep/file.txt", rewritten.getRawPath(),
            "Filter must preserve triple slash when key starts with //");
    }

    @Test
    void rewriteNormalKeyStillWorks() {
        // Normal key without leading slash
        URI uri = URI.create("http://my-bucket.localhost:4566/file.txt");
        ContainerRequestContext ctx = mockRequest("my-bucket.localhost:4566", uri);

        filter.filter(ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(ctx).setRequestUri(captor.capture());
        URI rewritten = captor.getValue();
        assertEquals("/my-bucket/file.txt", rewritten.getRawPath(),
            "Filter must correctly rewrite normal keys");
    }

    @Test
    void rewritePreservesQueryParams() {
        // Leading-slash key with query params (e.g., tagging)
        URI uri = URI.create("http://my-bucket.localhost:4566//file.txt?tagging");
        ContainerRequestContext ctx = mockRequest("my-bucket.localhost:4566", uri);

        filter.filter(ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(ctx).setRequestUri(captor.capture());
        URI rewritten = captor.getValue();
        assertEquals("/my-bucket//file.txt", rewritten.getRawPath());
        assertEquals("tagging", rewritten.getQuery());
    }

    @Test
    void rewriteRootPathPreserved() {
        // Virtual-hosted request to bucket root (list objects)
        URI uri = URI.create("http://my-bucket.localhost:4566/");
        ContainerRequestContext ctx = mockRequest("my-bucket.localhost:4566", uri);

        filter.filter(ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(ctx).setRequestUri(captor.capture());
        URI rewritten = captor.getValue();
        assertEquals("/my-bucket/", rewritten.getRawPath());
    }

    @Test
    void noRewriteForPathStyleRequest() {
        // Path-style request — Host is just localhost, no bucket prefix
        URI uri = URI.create("http://localhost:4566/my-bucket//file.txt");
        ContainerRequestContext ctx = mockRequest("localhost:4566", uri);

        filter.filter(ctx);

        // setRequestUri should NOT be called for path-style requests
        verify(ctx, never()).setRequestUri(any(URI.class));
    }
}
