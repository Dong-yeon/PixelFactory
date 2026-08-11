package com.pixelfactory.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** GET /api/events/recent에서 조건에 맞는 FactoryEvent가 나타날 때까지 폴링한다. */
public final class EventAwait {

    private EventAwait() {
    }

    public static Map<String, Object> untilEvent(TestApiClient api, Predicate<Map<String, Object>> predicate) {
        AtomicReference<Map<String, Object>> found = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<Map<String, Object>> events = TestApiClient.asList(api.get("/api/events/recent?limit=50").data());
            Map<String, Object> match = events.stream().filter(predicate).findFirst().orElse(null);
            assertThat(match).as("no matching event yet among %d recent events", events.size()).isNotNull();
            found.set(match);
        });

        return found.get();
    }
}
