package io.github.hectorvent.floci.services.sqs;

import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;

/**
 * Test helper to create SqsService instances
 */
public class SqsServiceFactory {

    public static SqsService createInMemory(String baseUrl, RegionResolver regionResolver) {
        return new SqsService(new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                30, 262144, baseUrl, regionResolver);
    }
}
