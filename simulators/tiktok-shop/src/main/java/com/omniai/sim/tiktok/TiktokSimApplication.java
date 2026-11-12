package com.omniai.sim.tiktok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Giả lập TikTok Shop Open API (Authorization, Event, Customer Service) theo schema thật. Owner: TuTM.
 * Hợp đồng và nguồn tài liệu: docs/channel-api/tiktok.md. State giữ trong bộ nhớ, restart là mất.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TiktokSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiktokSimApplication.class, args);
    }
}
