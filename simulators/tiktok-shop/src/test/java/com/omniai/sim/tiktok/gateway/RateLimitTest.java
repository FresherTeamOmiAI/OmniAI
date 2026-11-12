package com.omniai.sim.tiktok.gateway;

import com.omniai.sim.tiktok.SimTestSupport;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Quá 50 request đồng thời thì trả HTTP 429. Bật latency giả để các request chồng lên nhau. */
class RateLimitTest extends SimTestSupport {

    @Test
    void moreThanFiftyConcurrentRequestsGet429() throws Exception {
        String token = accessToken();
        post("/sim/faults", "{\"api_latency_ms\":1000}");
        int total = 60;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(total)) {
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return signedRaw("GET", "/customer_service/202309/conversations", Map.of("page_size", "5"),
                            null, token, Instant.now().getEpochSecond());
                }));
            }
            start.countDown();
            int ok = 0;
            int limited = 0;
            for (Future<HttpResponse<String>> f : futures) {
                HttpResponse<String> r = f.get();
                if (r.statusCode() == 429) {
                    limited++;
                    assertThat(json.readTree(r.body()).get("code").asInt()).isEqualTo(36009006);
                } else if (r.statusCode() == 200) {
                    ok++;
                }
            }
            assertThat(limited).isGreaterThanOrEqualTo(total - 50);
            assertThat(ok).isLessThanOrEqualTo(50).isPositive();
        }
        post("/sim/faults", "{\"api_latency_ms\":0}");
        assertThat(get("/customer_service/202309/conversations", Map.of("page_size", "5"), token).get("code").asInt()).isZero();
    }
}
