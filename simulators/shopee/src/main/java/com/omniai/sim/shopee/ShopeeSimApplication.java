package com.omniai.sim.shopee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Giả lập Shopee Open Platform v2 (product, order, sellerchat, push) theo schema thật. Owner: ChienPX.
 * Hợp đồng và nguồn tài liệu: docs/channel-api/shopee.md. Dữ liệu nằm trong Postgres (database shopee).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopeeSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopeeSimApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Gửi push bất đồng bộ, không chặn request phát tin/đơn (virtual thread, Java 21). */
    @Bean(destroyMethod = "shutdown")
    ExecutorService pushExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
