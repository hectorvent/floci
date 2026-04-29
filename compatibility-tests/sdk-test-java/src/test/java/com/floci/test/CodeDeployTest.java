package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.BatchGetApplicationsResponse;
import software.amazon.awssdk.services.codedeploy.model.BatchGetDeploymentGroupsResponse;
import software.amazon.awssdk.services.codedeploy.model.ComputePlatform;
import software.amazon.awssdk.services.codedeploy.model.CreateApplicationResponse;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentConfigResponse;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentGroupResponse;
import software.amazon.awssdk.services.codedeploy.model.DeploymentOption;
import software.amazon.awssdk.services.codedeploy.model.DeploymentReadyAction;
import software.amazon.awssdk.services.codedeploy.model.DeploymentStyle;
import software.amazon.awssdk.services.codedeploy.model.DeploymentType;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentConfigResponse;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupResponse;
import software.amazon.awssdk.services.codedeploy.model.ListApplicationsResponse;
import software.amazon.awssdk.services.codedeploy.model.ListDeploymentConfigsResponse;
import software.amazon.awssdk.services.codedeploy.model.ListDeploymentGroupsResponse;
import software.amazon.awssdk.services.codedeploy.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codedeploy.model.MinimumHealthyHosts;
import software.amazon.awssdk.services.codedeploy.model.MinimumHealthyHostsType;
import software.amazon.awssdk.services.codedeploy.model.Tag;
import software.amazon.awssdk.services.codedeploy.model.TrafficRoutingType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CodeDeployTest {

    static CodeDeployClient codedeploy;
    static String appId;
    static String dgId;

    @BeforeAll
    static void setup() {
        codedeploy = TestFixtures.codeDeployClient();
    }

    @AfterAll
    static void teardown() {
        codedeploy.close();
    }

    @Test
    @Order(1)
    void builtInDeploymentConfigsExist() {
        ListDeploymentConfigsResponse resp = codedeploy.listDeploymentConfigs(r -> r.build());
        assertThat(resp.deploymentConfigsList())
                .contains("CodeDeployDefault.AllAtOnce",
                           "CodeDeployDefault.HalfAtATime",
                           "CodeDeployDefault.OneAtATime",
                           "CodeDeployDefault.LambdaAllAtOnce",
                           "CodeDeployDefault.ECSAllAtOnce");
    }

    @Test
    @Order(2)
    void getLambdaDeploymentConfig() {
        GetDeploymentConfigResponse resp = codedeploy.getDeploymentConfig(r -> r
                .deploymentConfigName("CodeDeployDefault.LambdaAllAtOnce"));

        assertThat(resp.deploymentConfigInfo().deploymentConfigName())
                .isEqualTo("CodeDeployDefault.LambdaAllAtOnce");
        assertThat(resp.deploymentConfigInfo().computePlatform()).isEqualTo(ComputePlatform.LAMBDA);
        assertThat(resp.deploymentConfigInfo().trafficRoutingConfig().type())
                .isEqualTo(TrafficRoutingType.ALL_AT_ONCE);
    }

    @Test
    @Order(3)
    void getLambdaCanaryConfig() {
        GetDeploymentConfigResponse resp = codedeploy.getDeploymentConfig(r -> r
                .deploymentConfigName("CodeDeployDefault.LambdaCanary10Percent5Minutes"));

        assertThat(resp.deploymentConfigInfo().computePlatform()).isEqualTo(ComputePlatform.LAMBDA);
        assertThat(resp.deploymentConfigInfo().trafficRoutingConfig().type())
                .isEqualTo(TrafficRoutingType.TIME_BASED_CANARY);
        assertThat(resp.deploymentConfigInfo().trafficRoutingConfig().timeBasedCanary().canaryPercentage())
                .isEqualTo(10);
    }

    @Test
    @Order(4)
    void createApplication() {
        CreateApplicationResponse resp = codedeploy.createApplication(r -> r
                .applicationName("sdk-lambda-app")
                .computePlatform(ComputePlatform.LAMBDA)
                .tags(Tag.builder().key("env").value("test").build()));

        assertThat(resp.applicationId()).isNotBlank();
        appId = resp.applicationId();
    }

    @Test
    @Order(5)
    void getApplication() {
        var resp = codedeploy.getApplication(r -> r.applicationName("sdk-lambda-app"));
        assertThat(resp.application().applicationName()).isEqualTo("sdk-lambda-app");
        assertThat(resp.application().computePlatform()).isEqualTo(ComputePlatform.LAMBDA);
        assertThat(resp.application().linkedToGitHub()).isFalse();
    }

    @Test
    @Order(6)
    void listApplications() {
        ListApplicationsResponse resp = codedeploy.listApplications(r -> r.build());
        assertThat(resp.applications()).contains("sdk-lambda-app");
    }

    @Test
    @Order(7)
    void batchGetApplications() {
        BatchGetApplicationsResponse resp = codedeploy.batchGetApplications(r -> r
                .applicationNames("sdk-lambda-app"));

        assertThat(resp.applicationsInfo()).hasSize(1);
        assertThat(resp.applicationsInfo().get(0).applicationName()).isEqualTo("sdk-lambda-app");
    }

    @Test
    @Order(8)
    void createDeploymentGroup() {
        CreateDeploymentGroupResponse resp = codedeploy.createDeploymentGroup(r -> r
                .applicationName("sdk-lambda-app")
                .deploymentGroupName("sdk-lambda-dg")
                .deploymentConfigName("CodeDeployDefault.LambdaAllAtOnce")
                .serviceRoleArn("arn:aws:iam::000000000000:role/codedeploy-role")
                .deploymentStyle(DeploymentStyle.builder()
                        .deploymentType(DeploymentType.BLUE_GREEN)
                        .deploymentOption(DeploymentOption.WITH_TRAFFIC_CONTROL)
                        .build()));

        assertThat(resp.deploymentGroupId()).isNotBlank();
        dgId = resp.deploymentGroupId();
    }

    @Test
    @Order(9)
    void getDeploymentGroup() {
        GetDeploymentGroupResponse resp = codedeploy.getDeploymentGroup(r -> r
                .applicationName("sdk-lambda-app")
                .deploymentGroupName("sdk-lambda-dg"));

        assertThat(resp.deploymentGroupInfo().deploymentGroupName()).isEqualTo("sdk-lambda-dg");
        assertThat(resp.deploymentGroupInfo().deploymentConfigName())
                .isEqualTo("CodeDeployDefault.LambdaAllAtOnce");
    }

    @Test
    @Order(10)
    void listDeploymentGroups() {
        ListDeploymentGroupsResponse resp = codedeploy.listDeploymentGroups(r -> r
                .applicationName("sdk-lambda-app"));

        assertThat(resp.applicationName()).isEqualTo("sdk-lambda-app");
        assertThat(resp.deploymentGroups()).contains("sdk-lambda-dg");
    }

    @Test
    @Order(11)
    void batchGetDeploymentGroups() {
        BatchGetDeploymentGroupsResponse resp = codedeploy.batchGetDeploymentGroups(r -> r
                .applicationName("sdk-lambda-app")
                .deploymentGroupNames("sdk-lambda-dg"));

        assertThat(resp.deploymentGroupsInfo()).hasSize(1);
        assertThat(resp.deploymentGroupsInfo().get(0).deploymentGroupName()).isEqualTo("sdk-lambda-dg");
    }

    @Test
    @Order(12)
    void createCustomDeploymentConfig() {
        CreateDeploymentConfigResponse resp = codedeploy.createDeploymentConfig(r -> r
                .deploymentConfigName("SdkTestConfig")
                .minimumHealthyHosts(MinimumHealthyHosts.builder()
                        .type(MinimumHealthyHostsType.FLEET_PERCENT)
                        .value(75)
                        .build())
                .computePlatform(ComputePlatform.SERVER));

        assertThat(resp.deploymentConfigId()).isNotBlank();
    }

    @Test
    @Order(13)
    void getCustomDeploymentConfig() {
        GetDeploymentConfigResponse resp = codedeploy.getDeploymentConfig(r -> r
                .deploymentConfigName("SdkTestConfig"));

        assertThat(resp.deploymentConfigInfo().deploymentConfigName()).isEqualTo("SdkTestConfig");
        assertThat(resp.deploymentConfigInfo().computePlatform()).isEqualTo(ComputePlatform.SERVER);
        assertThat(resp.deploymentConfigInfo().minimumHealthyHosts().type())
                .isEqualTo(MinimumHealthyHostsType.FLEET_PERCENT);
        assertThat(resp.deploymentConfigInfo().minimumHealthyHosts().value()).isEqualTo(75);
    }

    @Test
    @Order(14)
    void tagAndListTags() {
        String arn = "arn:aws:codedeploy:us-east-1:000000000000:application:sdk-lambda-app";
        codedeploy.tagResource(r -> r
                .resourceArn(arn)
                .tags(Tag.builder().key("team").value("platform").build(),
                      Tag.builder().key("project").value("floci").build()));

        ListTagsForResourceResponse resp = codedeploy.listTagsForResource(r -> r.resourceArn(arn));
        // env=test was added during createApplication, plus team and project here
        assertThat(resp.tags().stream().map(Tag::key)).contains("team", "project", "env");
    }

    @Test
    @Order(15)
    void untagResource() {
        String arn = "arn:aws:codedeploy:us-east-1:000000000000:application:sdk-lambda-app";
        codedeploy.untagResource(r -> r.resourceArn(arn).tagKeys("project"));

        ListTagsForResourceResponse resp = codedeploy.listTagsForResource(r -> r.resourceArn(arn));
        assertThat(resp.tags().stream().map(Tag::key)).doesNotContain("project");
        assertThat(resp.tags().stream().map(Tag::key)).contains("team", "env");
    }

    @Test
    @Order(16)
    void cannotDeleteBuiltInConfig() {
        assertThatThrownBy(() ->
                codedeploy.deleteDeploymentConfig(r -> r.deploymentConfigName("CodeDeployDefault.AllAtOnce")))
                .isInstanceOf(software.amazon.awssdk.services.codedeploy.model.InvalidDeploymentConfigNameException.class);
    }

    @Test
    @Order(17)
    void cleanup() {
        codedeploy.deleteDeploymentGroup(r -> r
                .applicationName("sdk-lambda-app")
                .deploymentGroupName("sdk-lambda-dg"));

        codedeploy.deleteDeploymentConfig(r -> r.deploymentConfigName("SdkTestConfig"));

        codedeploy.deleteApplication(r -> r.applicationName("sdk-lambda-app"));

        ListApplicationsResponse after = codedeploy.listApplications(r -> r.build());
        assertThat(after.applications()).doesNotContain("sdk-lambda-app");
    }
}
