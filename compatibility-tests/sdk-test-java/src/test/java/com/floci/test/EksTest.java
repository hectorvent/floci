package com.floci.test;

import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.model.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EKS Elastic Kubernetes Service")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EksTest {

    private static EksClient eks;
    private static String clusterName;
    private static String clusterArn;

    @BeforeAll
    static void setup() {
        eks = TestFixtures.eksClient();
        clusterName = "sdk-test-cluster-" + (System.currentTimeMillis() % 100000);
    }

    @AfterAll
    static void cleanup() {
        if (eks != null) {
            try {
                eks.deleteCluster(DeleteClusterRequest.builder()
                        .name(clusterName)
                        .build());
            } catch (Exception ignored) {}
            eks.close();
        }
    }

    @Test
    @Order(1)
    void createCluster() {
        CreateClusterResponse response = eks.createCluster(CreateClusterRequest.builder()
                .name(clusterName)
                .roleArn("arn:aws:iam::000000000000:role/eks-role")
                .resourcesVpcConfig(VpcConfigRequest.builder()
                        .subnetIds(List.of())
                        .securityGroupIds(List.of())
                        .build())
                .version("1.29")
                .tags(Map.of("env", "test"))
                .build());

        assertThat(response.cluster()).isNotNull();
        assertThat(response.cluster().name()).isEqualTo(clusterName);
        assertThat(response.cluster().arn()).isNotBlank();
        assertThat(response.cluster().version()).isEqualTo("1.29");
        assertThat(response.cluster().status()).isIn(ClusterStatus.CREATING, ClusterStatus.ACTIVE);

        clusterArn = response.cluster().arn();
    }

    @Test
    @Order(2)
    void listClusters() {
        ListClustersResponse response = eks.listClusters(ListClustersRequest.builder().build());

        assertThat(response.clusters()).isNotNull();
        assertThat(response.clusters()).contains(clusterName);
    }

    @Test
    @Order(3)
    void describeCluster() {
        DescribeClusterResponse response = eks.describeCluster(DescribeClusterRequest.builder()
                .name(clusterName)
                .build());

        assertThat(response.cluster()).isNotNull();
        assertThat(response.cluster().name()).isEqualTo(clusterName);
        assertThat(response.cluster().arn()).isEqualTo(clusterArn);
        assertThat(response.cluster().status()).isIn(ClusterStatus.CREATING, ClusterStatus.ACTIVE);
        assertThat(response.cluster().resourcesVpcConfig()).isNotNull();
        assertThat(response.cluster().kubernetesNetworkConfig()).isNotNull();
        assertThat(response.cluster().certificateAuthority()).isNotNull();
    }

    @Test
    @Order(4)
    void describeClusterNotFound() {
        assertThatThrownBy(() -> eks.describeCluster(DescribeClusterRequest.builder()
                        .name("nonexistent-cluster-xyz")
                        .build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @Order(5)
    void tagResource() {
        eks.tagResource(TagResourceRequest.builder()
                .resourceArn(clusterArn)
                .tags(Map.of("team", "platform", "cost-center", "eng"))
                .build());

        // Verify tags are stored
        ListTagsForResourceResponse listResponse = eks.listTagsForResource(
                ListTagsForResourceRequest.builder()
                        .resourceArn(clusterArn)
                        .build());

        assertThat(listResponse.tags()).containsEntry("team", "platform");
        assertThat(listResponse.tags()).containsEntry("cost-center", "eng");
        assertThat(listResponse.tags()).containsEntry("env", "test");
    }

    @Test
    @Order(6)
    void untagResource() {
        eks.untagResource(UntagResourceRequest.builder()
                .resourceArn(clusterArn)
                .tagKeys("env")
                .build());

        ListTagsForResourceResponse listResponse = eks.listTagsForResource(
                ListTagsForResourceRequest.builder()
                        .resourceArn(clusterArn)
                        .build());

        assertThat(listResponse.tags()).doesNotContainKey("env");
        assertThat(listResponse.tags()).containsKey("team");
    }

    @Test
    @Order(7)
    void createDuplicateClusterFails() {
        assertThatThrownBy(() -> eks.createCluster(CreateClusterRequest.builder()
                        .name(clusterName)
                        .roleArn("arn:aws:iam::000000000000:role/eks-role")
                        .resourcesVpcConfig(VpcConfigRequest.builder().build())
                        .build()))
                .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @Order(100)
    void deleteCluster() {
        DeleteClusterResponse response = eks.deleteCluster(DeleteClusterRequest.builder()
                .name(clusterName)
                .build());

        assertThat(response.cluster()).isNotNull();
        assertThat(response.cluster().name()).isEqualTo(clusterName);
        assertThat(response.cluster().status()).isEqualTo(ClusterStatus.DELETING);
    }

    @Test
    @Order(101)
    void describeDeletedClusterFails() {
        assertThatThrownBy(() -> eks.describeCluster(DescribeClusterRequest.builder()
                        .name(clusterName)
                        .build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
