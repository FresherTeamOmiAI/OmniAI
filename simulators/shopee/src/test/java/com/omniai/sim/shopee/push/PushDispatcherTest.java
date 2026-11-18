package com.omniai.sim.shopee.push;

import com.omniai.sim.shopee.SimProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Simulator tự đẩy push tới hub (T-21.6): giữ nguyên body và chữ ký, hub lỗi thì gửi lại tối đa 3 lần. */
class PushDispatcherTest {

    private HttpServer hub;
    private ExecutorService executor;
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile int failStatus = 500;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> signatures = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startHub() throws IOException {
        hub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hub.createContext("/api/channels/shopee/push", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            signatures.add(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean fail = failuresLeft.getAndDecrement() > 0;
            byte[] reply = (fail ? "{\"error\":\"process_failed\"}" : "{\"error\":\"\",\"final_text\":\"Chào bạn\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(fail ? failStatus : 200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        hub.start();
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void stopHub() {
        hub.stop(0);
        executor.shutdownNow();
    }

    private PushDispatcher dispatcher() {
        String url = "http://127.0.0.1:" + hub.getAddress().getPort() + "/api/channels/shopee/push";
        SimProperties props = new SimProperties(2000001L, "test-partner-key", 10001L, "shop", "test-access-token", url,
                new SimProperties.Push(Duration.ofSeconds(2), 3, Duration.ofMillis(10)),
                new SimProperties.Datasource("jdbc:postgresql://localhost/shopee", "u", "p"));
        return new PushDispatcher(props, JsonMapper.builder().build(), executor, Clock.systemUTC());
    }

    @Test
    void retriesUntilHubAcceptsAndKeepsBodyAndSignature() {
        failuresLeft.set(2);
        PushDispatcher pushes = dispatcher();

        pushes.dispatch(10, "42", "{\"code\":10}", "chu-ky");

        await().atMost(Duration.ofSeconds(5)).until(() -> pushes.deliveries().size() == 1);
        PushDispatcher.Delivery delivery = pushes.deliveries().getFirst();
        assertThat(delivery.delivered()).isTrue();
        assertThat(delivery.code()).isEqualTo(10);
        assertThat(delivery.ref()).isEqualTo("42");
        assertThat(delivery.finalText()).isEqualTo("Chào bạn");
        assertThat(delivery.attempts()).extracting(PushDispatcher.Attempt::httpStatus).containsExactly(500, 500, 200);
        assertThat(bodies).containsOnly("{\"code\":10}");
        assertThat(signatures).containsOnly("chu-ky");
    }

    @Test
    void givesUpAfterThreeRejections() {
        failuresLeft.set(10);
        failStatus = 401;
        PushDispatcher pushes = dispatcher();

        pushes.dispatch(3, "26ABC", "{\"code\":3}", "sai");

        await().atMost(Duration.ofSeconds(5)).until(() -> pushes.deliveries().size() == 1);
        PushDispatcher.Delivery delivery = pushes.deliveries().getFirst();
        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery.finalText()).isNull();
        assertThat(delivery.attempts()).hasSize(3).allSatisfy(a -> assertThat(a.httpStatus()).isEqualTo(401));
        assertThat(bodies).hasSize(3);
    }
}
