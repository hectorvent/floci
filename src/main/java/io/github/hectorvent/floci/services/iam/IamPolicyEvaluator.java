package io.github.hectorvent.floci.services.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hectorvent.floci.services.iam.model.PolicyStatement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a set of IAM policy documents against a requested action and resource.
 *
 * Follows the standard AWS evaluation logic:
 *   1. Explicit Deny in any policy → DENY
 *   2. Explicit Allow in any policy → ALLOW
 *   3. Implicit deny → DENY
 *
 * Conditions, NotAction, NotResource, NotPrincipal are out of scope for Phase 1.
 */
@ApplicationScoped
public class IamPolicyEvaluator {

    public enum Decision { ALLOW, DENY }

    private static final Logger LOG = Logger.getLogger(IamPolicyEvaluator.class);

    private final ObjectMapper objectMapper;

    @Inject
    public IamPolicyEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates the given policy documents for {@code action} on {@code resource}.
     *
     * @param policyDocuments list of raw JSON policy document strings
     * @param action          IAM action, e.g. "s3:GetObject"
     * @param resource        resource ARN, e.g. "arn:aws:s3:::my-bucket/key"
     * @return {@link Decision#ALLOW} or {@link Decision#DENY}
     */
    public Decision evaluate(List<String> policyDocuments, String action, String resource) {
        List<PolicyStatement> statements = new ArrayList<>();
        for (String doc : policyDocuments) {
            try {
                statements.addAll(parseStatements(doc));
            } catch (Exception e) {
                LOG.warnv("Failed to parse policy document: {0}", e.getMessage());
            }
        }

        // 1. Explicit deny wins
        for (PolicyStatement stmt : statements) {
            if (stmt.isDeny() && matches(stmt, action, resource)) {
                return Decision.DENY;
            }
        }

        // 2. Explicit allow
        for (PolicyStatement stmt : statements) {
            if (stmt.isAllow() && matches(stmt, action, resource)) {
                return Decision.ALLOW;
            }
        }

        // 3. Implicit deny
        return Decision.DENY;
    }

    private boolean matches(PolicyStatement stmt, String action, String resource) {
        return matchesAny(stmt.getActions(), action) && matchesAny(stmt.getResources(), resource);
    }

    private boolean matchesAny(List<String> patterns, String value) {
        for (String pattern : patterns) {
            if (globMatches(pattern, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Case-insensitive glob matching supporting {@code *} (any sequence) and {@code ?} (any char).
     */
    public static boolean globMatches(String pattern, String value) {
        if (pattern == null || value == null) return false;
        return globMatchesHelper(pattern.toLowerCase(), value.toLowerCase(), 0, 0);
    }

    private static boolean globMatchesHelper(String pat, String val, int pi, int vi) {
        while (pi < pat.length() && vi < val.length()) {
            char p = pat.charAt(pi);
            if (p == '*') {
                // skip consecutive stars
                while (pi < pat.length() && pat.charAt(pi) == '*') pi++;
                if (pi == pat.length()) return true;
                for (int i = vi; i <= val.length(); i++) {
                    if (globMatchesHelper(pat, val, pi, i)) return true;
                }
                return false;
            } else if (p == '?' || p == val.charAt(vi)) {
                pi++;
                vi++;
            } else {
                return false;
            }
        }
        while (pi < pat.length() && pat.charAt(pi) == '*') pi++;
        return pi == pat.length() && vi == val.length();
    }

    private List<PolicyStatement> parseStatements(String document) throws Exception {
        JsonNode root = objectMapper.readTree(document);
        JsonNode stmtNode = root.path("Statement");
        List<PolicyStatement> result = new ArrayList<>();

        if (stmtNode.isArray()) {
            for (JsonNode s : stmtNode) {
                result.add(parseStatement(s));
            }
        } else if (stmtNode.isObject()) {
            result.add(parseStatement(stmtNode));
        }
        return result;
    }

    private PolicyStatement parseStatement(JsonNode stmt) {
        String effect = stmt.path("Effect").asText("Allow");
        List<String> actions = nodeToList(stmt.get("Action"));
        List<String> resources = nodeToList(stmt.get("Resource"));
        return new PolicyStatement(effect, actions, resources);
    }

    private List<String> nodeToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null) return list;
        if (node.isTextual()) {
            list.add(node.asText());
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }
}
