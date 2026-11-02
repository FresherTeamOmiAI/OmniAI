package com.omniai.brain;

import com.omniai.common.CanonicalMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class HttpBrainClient implements BrainClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBrainClient.class);

    private final RestClient restClient;

    public HttpBrainClient(@Value("${omniai.brain.url}") String brainUrl,
                           @Value("${omniai.brain.timeout:10s}") Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(brainUrl).requestFactory(factory).build();
    }

    @Override
    public BrainResponse ask(CanonicalMessage message) {
        BrainRequest request = new BrainRequest(
                message.messageId(),
                message.channel().name(),
                message.channel().isOwned(),
                message.channelUserId(),
                message.customerId(),
                message.text());
        try {
            BrainResponse response = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(BrainResponse.class);
            return response != null ? response : BrainResponse.unavailable();
        } catch (RestClientException e) {
            // AI Brain lỗi/không chạy -> không để khách chờ, chuyển nhân viên.
            log.warn("Không gọi được AI Brain: {}", e.getMessage());
            return BrainResponse.unavailable();
        }
    }
}
