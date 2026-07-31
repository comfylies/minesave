package com.gamesaves.gamesaves.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("extractionExecutor")
    public Executor extractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("zip-extract-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("ZIP extraction thread pool initialized: core=2, max=4");
        return executor;
    }

    /**
     * 提取文件并行上传到存储的专用线程池。
     * 上传是网络 IO 密集操作，并行度按配置（app.extraction.upload-parallelism，默认 8），
     * 不再使用 parallelStream 的公共 ForkJoinPool（CPU 核数并行度 + 多压缩包互相争抢）。
     */
    @Value("${app.extraction.upload-parallelism:8}")
    private int uploadParallelism;

    @Bean("uploadExecutor")
    public Executor uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(uploadParallelism);
        executor.setMaxPoolSize(uploadParallelism);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("zip-upload-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("ZIP upload thread pool initialized: parallelism={}", uploadParallelism);
        return executor;
    }
}
