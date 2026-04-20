package io.github.hectorvent.floci.services.eks;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.eks.model.ClusterStatus;
import io.github.hectorvent.floci.services.eks.model.CreateClusterRequest;
import io.github.hectorvent.floci.services.eks.model.Cluster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class EksServiceTest {

    private EksService eksService;
    private EmulatorConfig config;
    private EksClusterManager clusterManager;

    @BeforeEach
    void setUp() {
        StorageFactory storageFactory = Mockito.mock(StorageFactory.class);
        when(storageFactory.create(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new InMemoryStorage<>());

        config = Mockito.mock(EmulatorConfig.class);
        var servicesConfig = Mockito.mock(EmulatorConfig.ServicesConfig.class);
        var eksConfig = Mockito.mock(EmulatorConfig.EksServiceConfig.class);

        when(config.services()).thenReturn(servicesConfig);
        when(servicesConfig.eks()).thenReturn(eksConfig);
        when(eksConfig.mock()).thenReturn(true);
        when(eksConfig.apiServerBasePort()).thenReturn(6500);
        when(config.defaultRegion()).thenReturn("us-east-1");
        when(config.defaultAccountId()).thenReturn("000000000000");

        clusterManager = Mockito.mock(EksClusterManager.class);
        eksService = new EksService(storageFactory, config, clusterManager);
    }

    @Test
    void createCluster() {
        CreateClusterRequest req = new CreateClusterRequest();
        req.setName("test-cluster");
        req.setRoleArn("arn:aws:iam::000000000000:role/eks-role");
        req.setVersion("1.29");

        Cluster cluster = eksService.createCluster(req);

        assertNotNull(cluster);
        assertEquals("test-cluster", cluster.getName());
        assertEquals(ClusterStatus.ACTIVE, cluster.getStatus());
        assertTrue(cluster.getArn().contains("test-cluster"));
        assertEquals("1.29", cluster.getVersion());
        assertNotNull(cluster.getCreatedAt());
    }

    @Test
    void createClusterDuplicateFails() {
        CreateClusterRequest req = new CreateClusterRequest();
        req.setName("dup-cluster");
        req.setRoleArn("arn:aws:iam::000000000000:role/eks-role");

        eksService.createCluster(req);

        assertThrows(AwsException.class, () -> eksService.createCluster(req));
    }

    @Test
    void describeCluster() {
        CreateClusterRequest req = new CreateClusterRequest();
        req.setName("my-cluster");
        req.setRoleArn("arn:aws:iam::000000000000:role/eks-role");
        eksService.createCluster(req);

        Cluster described = eksService.describeCluster("my-cluster");
        assertEquals("my-cluster", described.getName());
    }

    @Test
    void describeClusterNotFound() {
        AwsException ex = assertThrows(AwsException.class,
                () -> eksService.describeCluster("nonexistent"));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void listClusters() {
        CreateClusterRequest req1 = new CreateClusterRequest();
        req1.setName("cluster-a");
        req1.setRoleArn("arn:aws:iam::000000000000:role/eks-role");

        CreateClusterRequest req2 = new CreateClusterRequest();
        req2.setName("cluster-b");
        req2.setRoleArn("arn:aws:iam::000000000000:role/eks-role");

        eksService.createCluster(req1);
        eksService.createCluster(req2);

        List<String> names = eksService.listClusters();
        assertEquals(2, names.size());
        assertTrue(names.contains("cluster-a"));
        assertTrue(names.contains("cluster-b"));
    }

    @Test
    void deleteCluster() {
        CreateClusterRequest req = new CreateClusterRequest();
        req.setName("to-delete");
        req.setRoleArn("arn:aws:iam::000000000000:role/eks-role");
        eksService.createCluster(req);

        Cluster deleted = eksService.deleteCluster("to-delete");
        assertEquals(ClusterStatus.DELETING, deleted.getStatus());
        assertTrue(eksService.listClusters().isEmpty());
    }

    @Test
    void taggingOperations() {
        CreateClusterRequest req = new CreateClusterRequest();
        req.setName("tagged-cluster");
        req.setRoleArn("arn:aws:iam::000000000000:role/eks-role");
        Cluster cluster = eksService.createCluster(req);

        String arn = cluster.getArn();

        // tagResource
        eksService.tagResource(arn, Map.of("env", "test", "team", "platform"));
        Map<String, String> tags = eksService.listTagsForResource(arn);
        assertEquals("test", tags.get("env"));
        assertEquals("platform", tags.get("team"));

        // untagResource
        eksService.untagResource(arn, List.of("env"));
        tags = eksService.listTagsForResource(arn);
        assertFalse(tags.containsKey("env"));
        assertEquals("platform", tags.get("team"));
    }
}
