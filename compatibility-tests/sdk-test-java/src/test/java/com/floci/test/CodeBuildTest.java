package com.floci.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.ArtifactsType;
import software.amazon.awssdk.services.codebuild.model.BatchGetProjectsResponse;
import software.amazon.awssdk.services.codebuild.model.ComputeType;
import software.amazon.awssdk.services.codebuild.model.CreateProjectResponse;
import software.amazon.awssdk.services.codebuild.model.CreateReportGroupResponse;
import software.amazon.awssdk.services.codebuild.model.DeleteReportGroupRequest;
import software.amazon.awssdk.services.codebuild.model.EnvironmentType;
import software.amazon.awssdk.services.codebuild.model.ImportSourceCredentialsResponse;
import software.amazon.awssdk.services.codebuild.model.ListCuratedEnvironmentImagesResponse;
import software.amazon.awssdk.services.codebuild.model.ListProjectsResponse;
import software.amazon.awssdk.services.codebuild.model.ListReportGroupsResponse;
import software.amazon.awssdk.services.codebuild.model.ListSourceCredentialsResponse;
import software.amazon.awssdk.services.codebuild.model.ProjectArtifacts;
import software.amazon.awssdk.services.codebuild.model.ProjectEnvironment;
import software.amazon.awssdk.services.codebuild.model.ProjectSource;
import software.amazon.awssdk.services.codebuild.model.ReportExportConfig;
import software.amazon.awssdk.services.codebuild.model.ReportExportConfigType;
import software.amazon.awssdk.services.codebuild.model.ReportType;
import software.amazon.awssdk.services.codebuild.model.AuthType;
import software.amazon.awssdk.services.codebuild.model.ServerType;
import software.amazon.awssdk.services.codebuild.model.SourceType;
import software.amazon.awssdk.services.codebuild.model.Tag;
import software.amazon.awssdk.services.codebuild.model.UpdateProjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CodeBuildTest {

    static CodeBuildClient codebuild;
    static String reportGroupArn;
    static String sourceCredentialsArn;

    @BeforeAll
    static void setup() {
        codebuild = TestFixtures.codeBuildClient();
    }

    @AfterAll
    static void teardown() {
        codebuild.close();
    }

    @Test
    @Order(1)
    void createProject() {
        CreateProjectResponse resp = codebuild.createProject(r -> r
                .name("sdk-test-project")
                .description("SDK test project")
                .source(ProjectSource.builder()
                        .type(SourceType.S3)
                        .location("my-bucket/source.zip")
                        .build())
                .artifacts(ProjectArtifacts.builder()
                        .type(ArtifactsType.NO_ARTIFACTS)
                        .build())
                .environment(ProjectEnvironment.builder()
                        .type(EnvironmentType.LINUX_CONTAINER)
                        .image("aws/codebuild/standard:7.0")
                        .computeType(ComputeType.BUILD_GENERAL1_SMALL)
                        .build())
                .serviceRole("arn:aws:iam::000000000000:role/codebuild-role")
                .tags(Tag.builder().key("env").value("test").build()));

        assertThat(resp.project().name()).isEqualTo("sdk-test-project");
        assertThat(resp.project().arn()).contains(":project/sdk-test-project");
        assertThat(resp.project().description()).isEqualTo("SDK test project");
        assertThat(resp.project().timeoutInMinutes()).isEqualTo(60);
    }

    @Test
    @Order(2)
    void batchGetProjects() {
        BatchGetProjectsResponse resp = codebuild.batchGetProjects(r -> r
                .names("sdk-test-project", "nonexistent"));

        assertThat(resp.projects()).hasSize(1);
        assertThat(resp.projects().get(0).name()).isEqualTo("sdk-test-project");
        assertThat(resp.projectsNotFound()).containsExactly("nonexistent");
    }

    @Test
    @Order(3)
    void listProjects() {
        ListProjectsResponse resp = codebuild.listProjects(r -> r.build());
        assertThat(resp.projects()).contains("sdk-test-project");
    }

    @Test
    @Order(4)
    void updateProject() {
        UpdateProjectResponse resp = codebuild.updateProject(r -> r
                .name("sdk-test-project")
                .description("Updated by SDK test")
                .timeoutInMinutes(120));

        assertThat(resp.project().description()).isEqualTo("Updated by SDK test");
        assertThat(resp.project().timeoutInMinutes()).isEqualTo(120);
    }

    @Test
    @Order(5)
    void createReportGroup() {
        CreateReportGroupResponse resp = codebuild.createReportGroup(r -> r
                .name("sdk-report-group")
                .type(ReportType.TEST)
                .exportConfig(ReportExportConfig.builder()
                        .exportConfigType(ReportExportConfigType.NO_EXPORT)
                        .build()));

        assertThat(resp.reportGroup().name()).isEqualTo("sdk-report-group");
        assertThat(resp.reportGroup().arn()).contains(":report-group/sdk-report-group");
        assertThat(resp.reportGroup().status().toString()).isEqualTo("ACTIVE");
        reportGroupArn = resp.reportGroup().arn();
    }

    @Test
    @Order(6)
    void listReportGroups() {
        ListReportGroupsResponse resp = codebuild.listReportGroups(r -> r.build());
        assertThat(resp.reportGroups()).contains(reportGroupArn);
    }

    @Test
    @Order(7)
    void importSourceCredentials() {
        ImportSourceCredentialsResponse resp = codebuild.importSourceCredentials(r -> r
                .token("ghp_test_token_sdk")
                .serverType(ServerType.GITHUB)
                .authType(AuthType.PERSONAL_ACCESS_TOKEN));

        assertThat(resp.arn()).contains(":token/github-");
        sourceCredentialsArn = resp.arn();
    }

    @Test
    @Order(8)
    void listSourceCredentials() {
        ListSourceCredentialsResponse resp = codebuild.listSourceCredentials(r -> r.build());
        assertThat(resp.sourceCredentialsInfos()).isNotEmpty();
        assertThat(resp.sourceCredentialsInfos().get(0).serverType()).isEqualTo(ServerType.GITHUB);
        assertThat(resp.sourceCredentialsInfos().get(0).authType()).isEqualTo(AuthType.PERSONAL_ACCESS_TOKEN);
    }

    @Test
    @Order(9)
    void listCuratedEnvironmentImages() {
        ListCuratedEnvironmentImagesResponse resp = codebuild.listCuratedEnvironmentImages(r -> r.build());
        assertThat(resp.platforms()).isNotEmpty();
        assertThat(resp.platforms().get(0).platformAsString()).isNotBlank();
    }

    @Test
    @Order(10)
    void deleteSourceCredentials() {
        codebuild.deleteSourceCredentials(r -> r.arn(sourceCredentialsArn));

        ListSourceCredentialsResponse after = codebuild.listSourceCredentials(r -> r.build());
        assertThat(after.sourceCredentialsInfos()).isEmpty();
    }

    @Test
    @Order(11)
    void deleteReportGroup() {
        codebuild.deleteReportGroup(DeleteReportGroupRequest.builder()
                .arn(reportGroupArn)
                .build());
    }

    @Test
    @Order(12)
    void deleteProject() {
        codebuild.deleteProject(r -> r.name("sdk-test-project"));

        ListProjectsResponse after = codebuild.listProjects(r -> r.build());
        assertThat(after.projects()).doesNotContain("sdk-test-project");
    }
}
