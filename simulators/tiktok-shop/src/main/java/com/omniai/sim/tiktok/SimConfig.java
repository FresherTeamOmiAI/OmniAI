package com.omniai.sim.tiktok;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SimConfig {

    /** Test thay bằng clock cố định để thử token hết hạn. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Gửi webhook bất đồng bộ, không chặn request của API (virtual thread, Java 21). */
    @Bean(destroyMethod = "shutdown")
    ExecutorService webhookExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
