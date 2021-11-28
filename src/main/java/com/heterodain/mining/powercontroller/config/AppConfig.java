package com.heterodain.mining.powercontroller.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import lombok.var;

/**
 * コンポーネント設定
 */
@Configuration
public class AppConfig {

    /**
     * JSONパーサーをDIコンテナに登録
     * 
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper getObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * タスクスケジューラーの設定
     * 
     * @return タスクスケジューラー
     */
    @Bean
    public ThreadPoolTaskScheduler getTaskScheduler() {
        var taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5); // 5スレッド同時実行
        taskScheduler.setThreadGroupName("task");
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
        pool.initialize();
        return pool;
    }
}
