package com.heterodain.mining.powercontroller.config;

import java.net.http.HttpClient;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * コンポーネント設定
 */
@Configuration
public class AppConfig {
    /** デフォルトのHTTPコネクションタイムアウト(秒) */
    private static final int DEFAULT_HTTP_CONNECTION_TIMEOUT = 15;

    /**
     * JSONパーサーをDIコンテナに登録
     * 
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * タスクスケジューラーの設定
     * 
     * @return タスクスケジューラー
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5); // 5スレッド同時実行
        taskScheduler.setThreadNamePrefix("task");
        return taskScheduler;
    }

    /**
     * タスク実行の設定
     * 
     * @return タスク実行
     */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1); // 1スレッド同時実行
        pool.setWaitForTasksToCompleteOnShutdown(false);
        pool.setThreadNamePrefix("exec");
        pool.initialize();
        return pool;
    }

    /**
     * Httpクライアント
     * 
     * @return Httpクライアント
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(DEFAULT_HTTP_CONNECTION_TIMEOUT)).build();
    }
}
