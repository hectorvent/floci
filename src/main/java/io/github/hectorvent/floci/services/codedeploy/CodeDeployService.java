package io.github.hectorvent.floci.services.codedeploy;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.codedeploy.model.Application;
import io.github.hectorvent.floci.services.codedeploy.model.DeploymentConfig;
import io.github.hectorvent.floci.services.codedeploy.model.DeploymentGroup;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeDeployService {

    // key: region -> name -> application
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Application>> applications = new ConcurrentHashMap<>();
    // key: region -> appName -> groupName -> group
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, DeploymentGroup>>> deploymentGroups = new ConcurrentHashMap<>();
    // key: region -> configName -> config (pre-seeded with built-ins)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, DeploymentConfig>> deploymentConfigs = new ConcurrentHashMap<>();
    // key: resourceArn -> tags
    private final ConcurrentHashMap<String, Map<String, String>> tags = new ConcurrentHashMap<>();

    private static final List<String> BUILT_IN_CONFIG_NAMES = List.of(
            "CodeDeployDefault.OneAtATime",
            "CodeDeployDefault.HalfAtATime",
            "CodeDeployDefault.AllAtOnce",
            "CodeDeployDefault.LambdaAllAtOnce",
            "CodeDeployDefault.LambdaCanary10Percent5Minutes",
            "CodeDeployDefault.LambdaCanary10Percent10Minutes",
            "CodeDeployDefault.LambdaCanary10Percent15Minutes",
            "CodeDeployDefault.LambdaCanary10Percent30Minutes",
            "CodeDeployDefault.LambdaLinear10PercentEvery1Minute",
            "CodeDeployDefault.LambdaLinear10PercentEvery2Minutes",
            "CodeDeployDefault.LambdaLinear10PercentEvery3Minutes",
            "CodeDeployDefault.LambdaLinear10PercentEvery10Minutes",
            "CodeDeployDefault.ECSAllAtOnce",
            "CodeDeployDefault.ECSCanary10Percent5Minutes",
            "CodeDeployDefault.ECSCanary10Percent15Minutes",
            "CodeDeployDefault.ECSLinear10PercentEvery1Minutes",
            "CodeDeployDefault.ECSLinear10PercentEvery3Minutes"
    );

    private Map<String, Application> applicationsFor(String region) {
        return applications.computeIfAbsent(region, r -> new ConcurrentHashMap<>());
    }

    private Map<String, ConcurrentHashMap<String, DeploymentGroup>> deploymentGroupsFor(String region) {
        return deploymentGroups.computeIfAbsent(region, r -> new ConcurrentHashMap<>());
    }

    private Map<String, DeploymentConfig> deploymentConfigsFor(String region) {
        return deploymentConfigs.computeIfAbsent(region, r -> {
            ConcurrentHashMap<String, DeploymentConfig> store = new ConcurrentHashMap<>();
            double now = Instant.now().toEpochMilli() / 1000.0;
            for (String name : BUILT_IN_CONFIG_NAMES) {
                store.put(name, buildBuiltInConfig(name, now));
            }
            return store;
        });
    }

    private DeploymentConfig buildBuiltInConfig(String name, double now) {
        DeploymentConfig cfg = new DeploymentConfig();
        cfg.setDeploymentConfigId("d-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        cfg.setDeploymentConfigName(name);
        cfg.setCreateTime(now);

        if (name.startsWith("CodeDeployDefault.Lambda")) {
            cfg.setComputePlatform("Lambda");
            if (name.equals("CodeDeployDefault.LambdaAllAtOnce")) {
                cfg.setTrafficRoutingConfig(Map.of("type", "AllAtOnce"));
            } else if (name.contains("Canary")) {
                int pct = 10;
                int minutes = extractMinutes(name);
                cfg.setTrafficRoutingConfig(Map.of("type", "TimeBasedCanary",
                        "timeBasedCanary", Map.of("canaryPercentage", pct, "canaryInterval", minutes)));
            } else if (name.contains("Linear")) {
                int pct = 10;
                int minutes = extractMinutes(name);
                cfg.setTrafficRoutingConfig(Map.of("type", "TimeBasedLinear",
                        "timeBasedLinear", Map.of("linearPercentage", pct, "linearInterval", minutes)));
            }
        } else if (name.startsWith("CodeDeployDefault.ECS")) {
            cfg.setComputePlatform("ECS");
            if (name.equals("CodeDeployDefault.ECSAllAtOnce")) {
                cfg.setTrafficRoutingConfig(Map.of("type", "AllAtOnce"));
            } else if (name.contains("Canary")) {
                int pct = 10;
                int minutes = extractMinutes(name);
                cfg.setTrafficRoutingConfig(Map.of("type", "TimeBasedCanary",
                        "timeBasedCanary", Map.of("canaryPercentage", pct, "canaryInterval", minutes)));
            } else if (name.contains("Linear")) {
                int pct = 10;
                int minutes = extractMinutes(name);
                cfg.setTrafficRoutingConfig(Map.of("type", "TimeBasedLinear",
                        "timeBasedLinear", Map.of("linearPercentage", pct, "linearInterval", minutes)));
            }
        } else {
            cfg.setComputePlatform("Server");
            if (name.equals("CodeDeployDefault.AllAtOnce")) {
                cfg.setMinimumHealthyHosts(Map.of("type", "FLEET_PERCENT", "value", 0));
            } else if (name.equals("CodeDeployDefault.HalfAtATime")) {
                cfg.setMinimumHealthyHosts(Map.of("type", "FLEET_PERCENT", "value", 50));
            } else {
                cfg.setMinimumHealthyHosts(Map.of("type", "HOST_COUNT", "value", 1));
            }
        }
        return cfg;
    }

    private int extractMinutes(String name) {
        // e.g. "…Every1Minute" -> 1, "…5Minutes" -> 5
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)Minute").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    // ---- Applications ----

    public Application createApplication(String region, String name, String computePlatform,
                                          List<Map<String, String>> tags) {
        Map<String, Application> store = applicationsFor(region);
        if (store.containsKey(name)) {
            throw new AwsException("ApplicationAlreadyExistsException",
                    "Application already exists: " + name, 400);
        }
        Application app = new Application();
        app.setApplicationId(UUID.randomUUID().toString());
        app.setApplicationName(name);
        app.setCreateTime(Instant.now().toEpochMilli() / 1000.0);
        app.setLinkedToGitHub(false);
        app.setComputePlatform(computePlatform != null ? computePlatform : "Server");
        store.put(name, app);

        if (tags != null && !tags.isEmpty()) {
            String arn = applicationArn(region, name);
            applyTags(arn, tags);
        }
        return app;
    }

    public Application getApplication(String region, String name) {
        Application app = applicationsFor(region).get(name);
        if (app == null) {
            throw new AwsException("ApplicationDoesNotExistException",
                    "Application does not exist: " + name, 400);
        }
        return app;
    }

    public void updateApplication(String region, String currentName, String newName) {
        Map<String, Application> store = applicationsFor(region);
        Application app = store.get(currentName);
        if (app == null) {
            throw new AwsException("ApplicationDoesNotExistException",
                    "Application does not exist: " + currentName, 400);
        }
        if (newName != null && !newName.equals(currentName)) {
            if (store.containsKey(newName)) {
                throw new AwsException("ApplicationAlreadyExistsException",
                        "Application already exists: " + newName, 400);
            }
            store.remove(currentName);
            app.setApplicationName(newName);
            store.put(newName, app);
        }
    }

    public void deleteApplication(String region, String name) {
        if (applicationsFor(region).remove(name) == null) {
            throw new AwsException("ApplicationDoesNotExistException",
                    "Application does not exist: " + name, 400);
        }
    }

    public List<String> listApplications(String region) {
        return new ArrayList<>(applicationsFor(region).keySet());
    }

    public List<Application> batchGetApplications(String region, List<String> names) {
        Map<String, Application> store = applicationsFor(region);
        return names.stream()
                .map(n -> {
                    Application a = store.get(n);
                    if (a == null) {
                        throw new AwsException("ApplicationDoesNotExistException",
                                "Application does not exist: " + n, 400);
                    }
                    return a;
                })
                .collect(Collectors.toList());
    }

    // ---- Deployment Groups ----

    public DeploymentGroup createDeploymentGroup(String region, String appName, String groupName,
                                                  String deploymentConfigName, String serviceRoleArn,
                                                  Map<String, Object> fields) {
        getApplication(region, appName);
        Map<String, ConcurrentHashMap<String, DeploymentGroup>> appGroups = deploymentGroupsFor(region);
        ConcurrentHashMap<String, DeploymentGroup> groupStore = appGroups.computeIfAbsent(appName, a -> new ConcurrentHashMap<>());
        if (groupStore.containsKey(groupName)) {
            throw new AwsException("DeploymentGroupAlreadyExistsException",
                    "Deployment group already exists: " + groupName, 400);
        }

        DeploymentGroup group = new DeploymentGroup();
        group.setApplicationName(appName);
        group.setDeploymentGroupId(UUID.randomUUID().toString());
        group.setDeploymentGroupName(groupName);
        group.setDeploymentConfigName(deploymentConfigName != null ? deploymentConfigName : "CodeDeployDefault.OneAtATime");
        group.setServiceRoleArn(serviceRoleArn);
        applyGroupFields(group, fields);
        groupStore.put(groupName, group);
        return group;
    }

    public DeploymentGroup getDeploymentGroup(String region, String appName, String groupName) {
        getApplication(region, appName);
        Map<String, ConcurrentHashMap<String, DeploymentGroup>> appGroups = deploymentGroupsFor(region);
        ConcurrentHashMap<String, DeploymentGroup> groupStore = appGroups.get(appName);
        DeploymentGroup group = groupStore != null ? groupStore.get(groupName) : null;
        if (group == null) {
            throw new AwsException("DeploymentGroupDoesNotExistException",
                    "Deployment group does not exist: " + groupName, 400);
        }
        return group;
    }

    public DeploymentGroup updateDeploymentGroup(String region, String appName,
                                                  String currentGroupName, String newGroupName,
                                                  String deploymentConfigName, String serviceRoleArn,
                                                  Map<String, Object> fields) {
        DeploymentGroup group = getDeploymentGroup(region, appName, currentGroupName);
        ConcurrentHashMap<String, DeploymentGroup> groupStore = deploymentGroupsFor(region)
                .computeIfAbsent(appName, a -> new ConcurrentHashMap<>());

        if (deploymentConfigName != null) { group.setDeploymentConfigName(deploymentConfigName); }
        if (serviceRoleArn != null) { group.setServiceRoleArn(serviceRoleArn); }
        applyGroupFields(group, fields);

        if (newGroupName != null && !newGroupName.equals(currentGroupName)) {
            groupStore.remove(currentGroupName);
            group.setDeploymentGroupName(newGroupName);
            groupStore.put(newGroupName, group);
        }
        return group;
    }

    public void deleteDeploymentGroup(String region, String appName, String groupName) {
        getApplication(region, appName);
        Map<String, ConcurrentHashMap<String, DeploymentGroup>> appGroups = deploymentGroupsFor(region);
        ConcurrentHashMap<String, DeploymentGroup> groupStore = appGroups.get(appName);
        if (groupStore == null || groupStore.remove(groupName) == null) {
            throw new AwsException("DeploymentGroupDoesNotExistException",
                    "Deployment group does not exist: " + groupName, 400);
        }
    }

    public List<String> listDeploymentGroups(String region, String appName) {
        getApplication(region, appName);
        Map<String, ConcurrentHashMap<String, DeploymentGroup>> appGroups = deploymentGroupsFor(region);
        ConcurrentHashMap<String, DeploymentGroup> groupStore = appGroups.get(appName);
        return groupStore != null ? new ArrayList<>(groupStore.keySet()) : List.of();
    }

    public List<DeploymentGroup> batchGetDeploymentGroups(String region, String appName, List<String> names) {
        getApplication(region, appName);
        Map<String, ConcurrentHashMap<String, DeploymentGroup>> appGroups = deploymentGroupsFor(region);
        ConcurrentHashMap<String, DeploymentGroup> groupStore = appGroups.get(appName);
        if (groupStore == null) {
            return List.of();
        }
        return names.stream()
                .map(groupStore::get)
                .filter(g -> g != null)
                .collect(Collectors.toList());
    }

    // ---- Deployment Configs ----

    public DeploymentConfig createDeploymentConfig(String region, String name,
                                                    Map<String, Object> minimumHealthyHosts,
                                                    String computePlatform,
                                                    Map<String, Object> trafficRoutingConfig,
                                                    Map<String, Object> zonalConfig) {
        Map<String, DeploymentConfig> store = deploymentConfigsFor(region);
        if (store.containsKey(name)) {
            throw new AwsException("DeploymentConfigAlreadyExistsException",
                    "Deployment configuration already exists: " + name, 400);
        }
        if (name.startsWith("CodeDeployDefault.")) {
            throw new AwsException("InvalidDeploymentConfigNameException",
                    "Cannot create a deployment configuration starting with 'CodeDeployDefault.'", 400);
        }
        DeploymentConfig cfg = new DeploymentConfig();
        cfg.setDeploymentConfigId(UUID.randomUUID().toString());
        cfg.setDeploymentConfigName(name);
        cfg.setMinimumHealthyHosts(minimumHealthyHosts);
        cfg.setCreateTime(Instant.now().toEpochMilli() / 1000.0);
        cfg.setComputePlatform(computePlatform != null ? computePlatform : "Server");
        cfg.setTrafficRoutingConfig(trafficRoutingConfig);
        cfg.setZonalConfig(zonalConfig);
        store.put(name, cfg);
        return cfg;
    }

    public DeploymentConfig getDeploymentConfig(String region, String name) {
        DeploymentConfig cfg = deploymentConfigsFor(region).get(name);
        if (cfg == null) {
            throw new AwsException("DeploymentConfigDoesNotExistException",
                    "Deployment configuration does not exist: " + name, 400);
        }
        return cfg;
    }

    public void deleteDeploymentConfig(String region, String name) {
        if (name.startsWith("CodeDeployDefault.")) {
            throw new AwsException("InvalidDeploymentConfigNameException",
                    "Cannot delete a built-in deployment configuration.", 400);
        }
        if (deploymentConfigsFor(region).remove(name) == null) {
            throw new AwsException("DeploymentConfigDoesNotExistException",
                    "Deployment configuration does not exist: " + name, 400);
        }
    }

    public List<String> listDeploymentConfigs(String region) {
        return new ArrayList<>(deploymentConfigsFor(region).keySet());
    }

    // ---- Tags ----

    public void tagResource(String arn, List<Map<String, String>> tagList) {
        Map<String, String> tagMap = tags.computeIfAbsent(arn, k -> new ConcurrentHashMap<>());
        for (Map<String, String> t : tagList) {
            tagMap.put(t.get("Key"), t.get("Value"));
        }
    }

    public void untagResource(String arn, List<String> tagKeys) {
        Map<String, String> tagMap = tags.get(arn);
        if (tagMap != null) {
            tagKeys.forEach(tagMap::remove);
        }
    }

    public List<Map<String, String>> listTagsForResource(String arn) {
        Map<String, String> tagMap = tags.getOrDefault(arn, Map.of());
        return tagMap.entrySet().stream()
                .map(e -> Map.of("Key", e.getKey(), "Value", e.getValue()))
                .collect(Collectors.toList());
    }

    public String applicationArn(String region, String name) {
        return "arn:aws:codedeploy:" + region + ":000000000000:application:" + name;
    }

    public String deploymentGroupArn(String region, String appName, String groupName) {
        return "arn:aws:codedeploy:" + region + ":000000000000:deploymentgroup:" + appName + "/" + groupName;
    }

    @SuppressWarnings("unchecked")
    private void applyGroupFields(DeploymentGroup group, Map<String, Object> fields) {
        if (fields == null) { return; }
        Object ec2TagFilters = fields.get("ec2TagFilters");
        if (ec2TagFilters instanceof List<?> list) {
            group.setEc2TagFilters((List<Map<String, String>>) list);
        }
        Object onPremTagFilters = fields.get("onPremisesInstanceTagFilters");
        if (onPremTagFilters instanceof List<?> list) {
            group.setOnPremisesInstanceTagFilters((List<Map<String, String>>) list);
        }
        Object asg = fields.get("autoScalingGroups");
        if (asg instanceof List<?> list) {
            group.setAutoScalingGroups((List<Map<String, Object>>) list);
        }
        setMapField(group, fields, "deploymentStyle", DeploymentGroup::setDeploymentStyle);
        setMapField(group, fields, "blueGreenDeploymentConfiguration", DeploymentGroup::setBlueGreenDeploymentConfiguration);
        setMapField(group, fields, "loadBalancerInfo", DeploymentGroup::setLoadBalancerInfo);
        setMapField(group, fields, "ec2TagSet", DeploymentGroup::setEc2TagSet);
        setMapField(group, fields, "onPremisesTagSet", DeploymentGroup::setOnPremisesTagSet);
        setMapField(group, fields, "alarmConfiguration", DeploymentGroup::setAlarmConfiguration);
        setMapField(group, fields, "autoRollbackConfiguration", DeploymentGroup::setAutoRollbackConfiguration);
        Object triggerConfigs = fields.get("triggerConfigurations");
        if (triggerConfigs instanceof List<?> list) {
            group.setTriggerConfigurations((List<Map<String, Object>>) list);
        }
        Object ecsServices = fields.get("ecsServices");
        if (ecsServices instanceof List<?> list) {
            group.setEcsServices((List<Map<String, Object>>) list);
        }
        if (fields.containsKey("computePlatform")) {
            group.setComputePlatform((String) fields.get("computePlatform"));
        }
        if (fields.containsKey("outdatedInstancesStrategy")) {
            group.setOutdatedInstancesStrategy((String) fields.get("outdatedInstancesStrategy"));
        }
        if (fields.containsKey("terminationHookEnabled")) {
            group.setTerminationHookEnabled((Boolean) fields.get("terminationHookEnabled"));
        }
    }

    @SuppressWarnings("unchecked")
    private void setMapField(DeploymentGroup group, Map<String, Object> fields, String key,
                              java.util.function.BiConsumer<DeploymentGroup, Map<String, Object>> setter) {
        Object val = fields.get(key);
        if (val instanceof Map<?, ?> m) {
            setter.accept(group, (Map<String, Object>) m);
        }
    }

    private void applyTags(String arn, List<Map<String, String>> tagList) {
        Map<String, String> tagMap = tags.computeIfAbsent(arn, k -> new ConcurrentHashMap<>());
        for (Map<String, String> t : tagList) {
            String key = t.containsKey("Key") ? t.get("Key") : t.get("key");
            String value = t.containsKey("Value") ? t.get("Value") : t.get("value");
            if (key != null) { tagMap.put(key, value != null ? value : ""); }
        }
    }
}
