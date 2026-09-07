package io.github.hectorvent.floci.services.iam;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.storage.AccountAwareStorageBackend;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.iam.model.SAMLProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Minimal IAM SAML provider registry used by STS assertion verification. */
@ApplicationScoped
public class SAMLProviderService {
    private static final Pattern ARN = Pattern.compile("^arn:aws:iam::(\\d{12}):saml-provider/[A-Za-z0-9+=,.@_-]{1,128}$");
    private final StorageBackend<String, SAMLProvider> providers;

    @Inject
    public SAMLProviderService(StorageFactory storageFactory) {
        this(storageFactory.create("iam", "iam-saml-providers.json", new TypeReference<>() {}));
    }

    SAMLProviderService(StorageBackend<String, SAMLProvider> providers) {
        this.providers = providers;
    }

    public SAMLProvider create(String accountId, String name, String metadata) {
        String arn = "arn:aws:iam::" + accountId + ":saml-provider/" + name;
        if (!ARN.matcher(arn).matches()) {
            throw new AwsException("InvalidInput", "Invalid SAML provider name.", 400);
        }
        if (metadata == null || metadata.isBlank()) {
            throw new AwsException("InvalidInput", "SAML metadata document must not be empty.", 400);
        }
        SAMLMetadata.Parsed parsed;
        try {
            parsed = SAMLMetadata.parse(metadata);
        } catch (Exception e) {
            throw new AwsException("InvalidInput", "The SAML metadata document is invalid.", 400);
        }
        SAMLProvider provider = new SAMLProvider();
        provider.setArn(arn);
        provider.setEntityId(parsed.entityId());
        provider.setCertificate(parsed.certificateBase64());
        if (providers instanceof AccountAwareStorageBackend<SAMLProvider> aware) {
            aware.putForAccount(accountId, arn, provider);
        } else {
            providers.put(arn, provider);
        }
        return provider;
    }

    public Optional<SAMLProvider> find(String arn) {
        var matcher = ARN.matcher(arn == null ? "" : arn);
        if (matcher.matches() && providers instanceof AccountAwareStorageBackend<SAMLProvider> aware) {
            return aware.getForAccount(matcher.group(1), arn);
        }
        return providers.get(arn);
    }
    public List<SAMLProvider> list(String accountId) {
        if (providers instanceof AccountAwareStorageBackend<SAMLProvider> aware) {
            return aware.scanForAccount(accountId, k -> true);
        }
        return providers.scan(k -> true);
    }

    public SAMLProvider get(String arn) {
        return find(arn).orElseThrow(() -> new AwsException("NoSuchEntity",
                "The SAML provider with ARN " + arn + " cannot be found.", 404));
    }

    /** Metadata parser shared by provider registration and the assertion verifier. */
    static final class SAMLMetadata {
        private SAMLMetadata() {}
        record Parsed(String entityId, String certificateBase64) {}

        static Parsed parse(String metadata) throws Exception {
            var doc = SAMLXml.document(metadata);
            var entity = doc.getDocumentElement().getAttribute("entityID");
            var cert = SAMLXml.text(doc, "X509Certificate");
            if (entity == null || entity.isBlank() || cert == null || cert.isBlank()) throw new Exception();
            Base64.getDecoder().decode(cert.replaceAll("\\s+", ""));
            return new Parsed(entity, cert.replaceAll("\\s+", ""));
        }
    }
}
