package xyz.ezsky.anilink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度配置。
 *
 * <p>提供多线程的 {@link TaskScheduler}，避免多个定时任务（媒体库重匹配、缓存清理、
 * RSS 订阅轮询、动漫记录同步等）在默认单线程调度器上相互阻塞。</p>
 */
@Configuration
public class SchedulingConfig {

    @Bean(name = "scheduledTaskScheduler")
    public TaskScheduler scheduledTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
