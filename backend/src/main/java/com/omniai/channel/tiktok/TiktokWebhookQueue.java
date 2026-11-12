package com.omniai.channel.tiktok;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Hàng đợi xử lý webhook sau khi đã trả 200 cho sàn (gọi AI Brain có thể mất tới 10 s, sàn chỉ chờ 3 s).
 * Không khai báo executor thành bean để không đè {@code applicationTaskExecutor} mặc định của Spring Boot.
 */
@Component
public class TiktokWebhookQueue implements DisposableBean {

    private final ThreadPoolTaskExecutor executor;

    public TiktokWebhookQueue(TiktokProperties props) {
        executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("tiktok-webhook-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(props.webhookQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
    }

    /** @return false nếu hàng đợi đầy (controller trả 503 để sàn gửi lại). */
    public boolean offer(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (TaskRejectedException e) {
            return false;
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
