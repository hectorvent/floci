package io.github.hectorvent.floci.services.autoscaling;

import io.github.hectorvent.floci.core.common.AwsArnUtils;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.autoscaling.model.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class AutoScalingService {

    private static final String DEFAULT_ACCOUNT = "000000000000";

    // region :: name → resource
    private final Map<String, LaunchConfiguration> launchConfigs = new ConcurrentHashMap<>();
    private final Map<String, AutoScalingGroup>    groups         = new ConcurrentHashMap<>();
    private final Map<String, LifecycleHook>       hooks          = new ConcurrentHashMap<>();
    private final Map<String, ScalingPolicy>       policies       = new ConcurrentHashMap<>();
    private final Map<String, ScalingActivity>     activities     = new ConcurrentHashMap<>();

    // ── Launch Configurations ──────────────────────────────────────────────────

    public LaunchConfiguration createLaunchConfiguration(String region, String name, String imageId,
                                                          String instanceType, String keyName,
                                                          List<String> securityGroups, String userData,
                                                          String iamInstanceProfile,
                                                          boolean associatePublicIpAddress) {
        String key = lcKey(region, name);
        if (launchConfigs.containsKey(key)) {
            throw new AwsException("AlreadyExists",
                    "Launch configuration '" + name + "' already exists.", 400);
        }
        LaunchConfiguration lc = new LaunchConfiguration();
        lc.setLaunchConfigurationName(name);
        lc.setLaunchConfigurationArn(
                AwsArnUtils.Arn.of("autoscaling", region, DEFAULT_ACCOUNT,
                        "launchConfiguration:" + name).toString());
        lc.setImageId(imageId);
        lc.setInstanceType(instanceType != null ? instanceType : "t3.micro");
        lc.setKeyName(keyName);
        lc.setSecurityGroups(securityGroups != null ? new ArrayList<>(securityGroups) : new ArrayList<>());
        lc.setUserData(userData);
        lc.setIamInstanceProfile(iamInstanceProfile);
        lc.setAssociatePublicIpAddress(associatePublicIpAddress);
        lc.setCreatedTime(Instant.now());
        lc.setRegion(region);
        launchConfigs.put(key, lc);
        return lc;
    }

    public List<LaunchConfiguration> describeLaunchConfigurations(String region, List<String> names) {
        if (names != null && !names.isEmpty()) {
            return names.stream()
                    .map(n -> launchConfigs.get(lcKey(region, n)))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return launchConfigs.values().stream()
                .filter(lc -> region.equals(lc.getRegion()))
                .collect(Collectors.toList());
    }

    public void deleteLaunchConfiguration(String region, String name) {
        if (launchConfigs.remove(lcKey(region, name)) == null) {
            throw new AwsException("ValidationError",
                    "Launch configuration '" + name + "' not found.", 400);
        }
    }

    // ── Auto Scaling Groups ────────────────────────────────────────────────────

    public AutoScalingGroup createAutoScalingGroup(String region, String name,
                                                    String launchConfigName,
                                                    String launchTemplateName, String launchTemplateVersion,
                                                    int minSize, int maxSize, int desiredCapacity,
                                                    int defaultCooldown, List<String> availabilityZones,
                                                    List<String> targetGroupArns, List<String> lbNames,
                                                    String healthCheckType, int healthCheckGracePeriod,
                                                    List<String> terminationPolicies,
                                                    Map<String, String> tags) {
        String key = asgKey(region, name);
        if (groups.containsKey(key)) {
            throw new AwsException("AlreadyExists",
                    "Auto Scaling group '" + name + "' already exists.", 400);
        }
        if (launchConfigName == null && launchTemplateName == null) {
            throw new AwsException("ValidationError",
                    "Either LaunchConfigurationName or LaunchTemplate must be specified.", 400);
        }

        AutoScalingGroup asg = new AutoScalingGroup();
        asg.setAutoScalingGroupName(name);
        asg.setAutoScalingGroupArn(
                AwsArnUtils.Arn.of("autoscaling", region, DEFAULT_ACCOUNT,
                        "autoScalingGroup:" + name).toString());
        asg.setLaunchConfigurationName(launchConfigName);
        asg.setLaunchTemplateName(launchTemplateName);
        asg.setLaunchTemplateVersion(launchTemplateVersion);
        asg.setMinSize(minSize);
        asg.setMaxSize(maxSize);
        asg.setDesiredCapacity(desiredCapacity);
        asg.setDefaultCooldown(defaultCooldown > 0 ? defaultCooldown : 300);
        asg.setAvailabilityZones(availabilityZones != null ? new ArrayList<>(availabilityZones) : new ArrayList<>());
        asg.setTargetGroupARNs(targetGroupArns != null ? new ArrayList<>(targetGroupArns) : new ArrayList<>());
        asg.setLoadBalancerNames(lbNames != null ? new ArrayList<>(lbNames) : new ArrayList<>());
        asg.setHealthCheckType(healthCheckType != null ? healthCheckType : "EC2");
        asg.setHealthCheckGracePeriod(healthCheckGracePeriod);
        asg.setTerminationPolicies(terminationPolicies != null ? new ArrayList<>(terminationPolicies) : List.of("Default"));
        asg.setCreatedTime(Instant.now());
        asg.setRegion(region);
        if (tags != null) {
            asg.getTags().putAll(tags);
        }
        groups.put(key, asg);
        return asg;
    }

    public void updateAutoScalingGroup(String region, String name,
                                        String launchConfigName,
                                        String launchTemplateName, String launchTemplateVersion,
                                        Integer minSize, Integer maxSize, Integer desiredCapacity,
                                        Integer defaultCooldown, List<String> availabilityZones,
                                        String healthCheckType, Integer healthCheckGracePeriod,
                                        List<String> terminationPolicies) {
        AutoScalingGroup asg = requireGroup(region, name);
        if (launchConfigName != null) {
            asg.setLaunchConfigurationName(launchConfigName);
        }
        if (launchTemplateName != null) {
            asg.setLaunchTemplateName(launchTemplateName);
            asg.setLaunchTemplateVersion(launchTemplateVersion);
        }
        if (minSize != null) { asg.setMinSize(minSize); }
        if (maxSize != null) { asg.setMaxSize(maxSize); }
        if (desiredCapacity != null) { asg.setDesiredCapacity(desiredCapacity); }
        if (defaultCooldown != null) { asg.setDefaultCooldown(defaultCooldown); }
        if (availabilityZones != null) { asg.setAvailabilityZones(new ArrayList<>(availabilityZones)); }
        if (healthCheckType != null) { asg.setHealthCheckType(healthCheckType); }
        if (healthCheckGracePeriod != null) { asg.setHealthCheckGracePeriod(healthCheckGracePeriod); }
        if (terminationPolicies != null) { asg.setTerminationPolicies(new ArrayList<>(terminationPolicies)); }
    }

    public void deleteAutoScalingGroup(String region, String name, boolean forceDelete) {
        AutoScalingGroup asg = requireGroup(region, name);
        List<AsgInstance> active = asg.getInstances().stream()
                .filter(i -> !"Terminated".equals(i.getLifecycleState()))
                .collect(Collectors.toList());
        if (!active.isEmpty() && !forceDelete) {
            throw new AwsException("ResourceInUse",
                    "Auto Scaling group '" + name + "' has " + active.size()
                            + " instance(s). Set ForceDelete=true to delete anyway.", 400);
        }
        groups.remove(asgKey(region, name));
        // clean up associated hooks and policies
        hooks.entrySet().removeIf(e -> e.getValue().getAutoScalingGroupName().equals(name));
        policies.entrySet().removeIf(e -> e.getValue().getAutoScalingGroupName().equals(name));
    }

    public List<AutoScalingGroup> describeAutoScalingGroups(String region, List<String> names) {
        if (names != null && !names.isEmpty()) {
            return names.stream()
                    .map(n -> groups.get(asgKey(region, n)))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return groups.values().stream()
                .filter(g -> region == null || region.equals(g.getRegion()))
                .collect(Collectors.toList());
    }

    public void setDesiredCapacity(String region, String name, int desiredCapacity) {
        AutoScalingGroup asg = requireGroup(region, name);
        if (desiredCapacity < asg.getMinSize() || desiredCapacity > asg.getMaxSize()) {
            throw new AwsException("ValidationError",
                    "New DesiredCapacity=" + desiredCapacity + " must be between MinSize="
                            + asg.getMinSize() + " and MaxSize=" + asg.getMaxSize() + ".", 400);
        }
        asg.setDesiredCapacity(desiredCapacity);
    }

    // ── Instance management ────────────────────────────────────────────────────

    public List<AsgInstance> describeAutoScalingInstances(String region, List<String> instanceIds) {
        List<AsgInstance> all = groups.values().stream()
                .filter(g -> region.equals(g.getRegion()))
                .flatMap(g -> g.getInstances().stream())
                .collect(Collectors.toList());
        if (instanceIds != null && !instanceIds.isEmpty()) {
            Set<String> ids = new HashSet<>(instanceIds);
            return all.stream().filter(i -> ids.contains(i.getInstanceId())).collect(Collectors.toList());
        }
        return all;
    }

    public void attachInstances(String region, String name, List<String> instanceIds) {
        AutoScalingGroup asg = requireGroup(region, name);
        for (String id : instanceIds) {
            AsgInstance inst = new AsgInstance();
            inst.setInstanceId(id);
            inst.setLifecycleState("InService");
            inst.setHealthStatus("Healthy");
            inst.setAvailabilityZone(
                    asg.getAvailabilityZones().isEmpty() ? region + "a" : asg.getAvailabilityZones().get(0));
            inst.setLaunchConfigurationName(asg.getLaunchConfigurationName());
            asg.getInstances().add(inst);
        }
        if (asg.getInstances().size() > asg.getDesiredCapacity()) {
            asg.setDesiredCapacity(asg.getInstances().size());
        }
    }

    public void detachInstances(String region, String name, List<String> instanceIds,
                                 boolean decrementDesiredCapacity) {
        AutoScalingGroup asg = requireGroup(region, name);
        asg.getInstances().removeIf(i -> instanceIds.contains(i.getInstanceId()));
        if (decrementDesiredCapacity) {
            int newDesired = Math.max(asg.getMinSize(), asg.getDesiredCapacity() - instanceIds.size());
            asg.setDesiredCapacity(newDesired);
        }
    }

    public void terminateInstanceInAutoScalingGroup(String region, String instanceId,
                                                     boolean decrementDesiredCapacity) {
        AutoScalingGroup asg = groups.values().stream()
                .filter(g -> region.equals(g.getRegion()))
                .filter(g -> g.getInstances().stream().anyMatch(i -> instanceId.equals(i.getInstanceId())))
                .findFirst()
                .orElseThrow(() -> new AwsException("ValidationError",
                        "Instance '" + instanceId + "' not found in any Auto Scaling group.", 400));
        asg.getInstances().stream()
                .filter(i -> instanceId.equals(i.getInstanceId()))
                .findFirst()
                .ifPresent(i -> i.setLifecycleState("Terminating"));
        if (decrementDesiredCapacity) {
            int newDesired = Math.max(asg.getMinSize(), asg.getDesiredCapacity() - 1);
            asg.setDesiredCapacity(newDesired);
        }
    }

    // ── Load balancer attachment ───────────────────────────────────────────────

    public void attachLoadBalancerTargetGroups(String region, String name, List<String> tgArns) {
        AutoScalingGroup asg = requireGroup(region, name);
        for (String arn : tgArns) {
            if (!asg.getTargetGroupARNs().contains(arn)) {
                asg.getTargetGroupARNs().add(arn);
            }
        }
    }

    public void detachLoadBalancerTargetGroups(String region, String name, List<String> tgArns) {
        AutoScalingGroup asg = requireGroup(region, name);
        asg.getTargetGroupARNs().removeAll(tgArns);
    }

    public List<String> describeLoadBalancerTargetGroups(String region, String name) {
        return requireGroup(region, name).getTargetGroupARNs();
    }

    public void attachLoadBalancers(String region, String name, List<String> lbNames) {
        AutoScalingGroup asg = requireGroup(region, name);
        for (String lb : lbNames) {
            if (!asg.getLoadBalancerNames().contains(lb)) {
                asg.getLoadBalancerNames().add(lb);
            }
        }
    }

    public void detachLoadBalancers(String region, String name, List<String> lbNames) {
        requireGroup(region, name).getLoadBalancerNames().removeAll(lbNames);
    }

    // ── Lifecycle hooks ────────────────────────────────────────────────────────

    public void putLifecycleHook(String region, String asgName, String hookName,
                                  String transition, String notificationTargetArn,
                                  String roleArn, String notificationMetadata,
                                  Integer heartbeatTimeout, String defaultResult) {
        requireGroup(region, asgName);
        String key = hookKey(region, asgName, hookName);
        LifecycleHook hook = hooks.computeIfAbsent(key, k -> new LifecycleHook());
        hook.setLifecycleHookName(hookName);
        hook.setAutoScalingGroupName(asgName);
        hook.setLifecycleTransition(transition);
        hook.setNotificationTargetArn(notificationTargetArn);
        hook.setRoleArn(roleArn);
        hook.setNotificationMetadata(notificationMetadata);
        if (heartbeatTimeout != null) { hook.setHeartbeatTimeout(heartbeatTimeout); }
        if (defaultResult != null) { hook.setDefaultResult(defaultResult); }
    }

    public void deleteLifecycleHook(String region, String asgName, String hookName) {
        hooks.remove(hookKey(region, asgName, hookName));
    }

    public List<LifecycleHook> describeLifecycleHooks(String region, String asgName, List<String> hookNames) {
        requireGroup(region, asgName);
        List<LifecycleHook> result = hooks.values().stream()
                .filter(h -> asgName.equals(h.getAutoScalingGroupName()))
                .collect(Collectors.toList());
        if (hookNames != null && !hookNames.isEmpty()) {
            Set<String> names = new HashSet<>(hookNames);
            result = result.stream().filter(h -> names.contains(h.getLifecycleHookName())).collect(Collectors.toList());
        }
        return result;
    }

    public void completeLifecycleAction(String region, String asgName, String hookName,
                                         String instanceId, String actionResult, String token) {
        // Stored-only — Phase 2 reconciler observes this via the instance lifecycle state
        requireGroup(region, asgName);
    }

    // ── Scaling policies ───────────────────────────────────────────────────────

    public ScalingPolicy putScalingPolicy(String region, String asgName, String policyName,
                                           String policyType, String adjustmentType,
                                           int scalingAdjustment, int cooldown) {
        requireGroup(region, asgName);
        String key = policyKey(region, asgName, policyName);
        ScalingPolicy policy = policies.computeIfAbsent(key, k -> new ScalingPolicy());
        policy.setPolicyName(policyName);
        policy.setPolicyArn(AwsArnUtils.Arn.of("autoscaling", region, DEFAULT_ACCOUNT,
                "scalingPolicy:" + asgName + ":" + policyName).toString());
        policy.setAutoScalingGroupName(asgName);
        policy.setPolicyType(policyType != null ? policyType : "SimpleScaling");
        policy.setAdjustmentType(adjustmentType);
        policy.setScalingAdjustment(scalingAdjustment);
        policy.setCooldown(cooldown);
        policy.setRegion(region);
        return policy;
    }

    public void deletePolicy(String region, String asgName, String policyNameOrArn) {
        policies.entrySet().removeIf(e -> {
            ScalingPolicy p = e.getValue();
            return p.getPolicyName().equals(policyNameOrArn) || p.getPolicyArn().equals(policyNameOrArn);
        });
    }

    public List<ScalingPolicy> describePolicies(String region, String asgName, List<String> policyNames) {
        return policies.values().stream()
                .filter(p -> region.equals(p.getRegion()))
                .filter(p -> asgName == null || asgName.equals(p.getAutoScalingGroupName()))
                .filter(p -> policyNames == null || policyNames.isEmpty() || policyNames.contains(p.getPolicyName()))
                .collect(Collectors.toList());
    }

    // ── Scaling activities ─────────────────────────────────────────────────────

    public List<ScalingActivity> describeScalingActivities(String region, String asgName) {
        return activities.values().stream()
                .filter(a -> asgName == null || asgName.equals(a.getAutoScalingGroupName()))
                .sorted(Comparator.comparing(ScalingActivity::getStartTime).reversed())
                .collect(Collectors.toList());
    }

    public ScalingActivity recordActivity(String region, String asgName, String description,
                                           String cause, String statusCode) {
        ScalingActivity activity = new ScalingActivity();
        activity.setActivityId(UUID.randomUUID().toString());
        activity.setAutoScalingGroupName(asgName);
        activity.setDescription(description);
        activity.setCause(cause);
        activity.setStartTime(Instant.now());
        activity.setStatusCode(statusCode);
        activity.setProgress("Successful".equals(statusCode) ? 100 : 0);
        activities.put(activity.getActivityId(), activity);
        return activity;
    }

    public void completeActivity(String activityId, String statusCode, String statusMessage) {
        ScalingActivity activity = activities.get(activityId);
        if (activity != null) {
            activity.setEndTime(Instant.now());
            activity.setStatusCode(statusCode);
            activity.setStatusMessage(statusMessage);
            activity.setProgress(100);
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    AutoScalingGroup requireGroup(String region, String name) {
        AutoScalingGroup asg = groups.get(asgKey(region, name));
        if (asg == null) {
            throw new AwsException("ValidationError",
                    "Auto Scaling group '" + name + "' not found.", 400);
        }
        return asg;
    }

    private static String lcKey(String region, String name) {
        return region + "::" + name;
    }

    static String asgKey(String region, String name) {
        return region + "::" + name;
    }

    private static String hookKey(String region, String asgName, String hookName) {
        return region + "::" + asgName + "::" + hookName;
    }

    private static String policyKey(String region, String asgName, String policyName) {
        return region + "::" + asgName + "::" + policyName;
    }
}
