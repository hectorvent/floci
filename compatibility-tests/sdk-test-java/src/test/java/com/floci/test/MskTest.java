package com.floci.test;

import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.kafka.KafkaClient;
import software.amazon.awssdk.services.kafka.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MSK (Managed Streaming for Kafka)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MskTest {

    private static KafkaClient kafka;
    private static String clusterArn;
    private static final String CLUSTER_NAME = TestFixtures.uniqueName("msk-cluster");

    @BeforeAll
    static void setup() {
        kafka = TestFixtures.kafkaClient();
    }

    @AfterAll
    static void cleanup() {
        if (kafka != null) {
            if (clusterArn != null) {
                try {
                    kafka.deleteCluster(DeleteClusterRequest.builder().clusterArn(clusterArn).build());
                } catch (Exception ignored) {}
            }
            kafka.close();
        }
    }

    @Test
    @Order(1)
    void createCluster() {
        CreateClusterResponse response = kafka.createCluster(CreateClusterRequest.builder()
                .clusterName(CLUSTER_NAME)
                .kafkaVersion("3.6.1")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(BrokerNodeGroupInfo.builder()
                        .instanceType("kafka.m5.large")
                        .clientSubnets("subnet-12345")
                        .build())
                .build());

        assertThat(response.clusterArn()).isNotNull();
        assertThat(response.clusterName()).isEqualTo(CLUSTER_NAME);
        assertThat(response.state()).isIn(ClusterState.CREATING, ClusterState.ACTIVE);
        clusterArn = response.clusterArn();
    }

    @Test
    @Order(2)
    void describeCluster() {
        DescribeClusterResponse response = kafka.describeCluster(DescribeClusterRequest.builder()
                .clusterArn(clusterArn)
                .build());

        assertThat(response.clusterInfo()).isNotNull();
        assertThat(response.clusterInfo().clusterArn()).isEqualTo(clusterArn);
        assertThat(response.clusterInfo().clusterName()).isEqualTo(CLUSTER_NAME);
    }

    @Test
    @Order(3)
    void listClusters() {
        ListClustersResponse response = kafka.listClusters(ListClustersRequest.builder().build());

        assertThat(response.clusterInfoList()).anyMatch(c -> c.clusterArn().equals(clusterArn));
    }

    @Test
    @Order(4)
    void getBootstrapBrokers() {
        GetBootstrapBrokersResponse response = kafka.getBootstrapBrokers(GetBootstrapBrokersRequest.builder()
                .clusterArn(clusterArn)
                .build());

        // In mock mode it's immediate, in real mode it might be null while CREATING
        // but our MskService handles mock=true by setting it ACTIVE immediately.
        assertThat(response.bootstrapBrokerString()).isNotNull();
    }

    @Test
    @Order(5)
    void deleteCluster() {
        DeleteClusterResponse response = kafka.deleteCluster(DeleteClusterRequest.builder()
                .clusterArn(clusterArn)
                .build());

        assertThat(response.clusterArn()).isEqualTo(clusterArn);
        assertThat(response.state()).isEqualTo(ClusterState.DELETING);
    }
}
