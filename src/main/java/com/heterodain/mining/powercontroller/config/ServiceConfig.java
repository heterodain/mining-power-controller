package com.heterodain.mining.powercontroller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * サービスの設定
 */
@Component
@ConfigurationProperties("service")
@Data
public class ServiceConfig {
    /** Ambientの設定(3分値) */
    private Ambient ambient;
    /** Nicehashの設定 */
    private Nicehash nicehash;

    /**
     * Ambientの設定情報
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Ambient {
        /** チャネルID */
        private Integer channelId;
        /** リードキー */
        private String readKey;
        /** ライトキー */
        private String writeKey;
    }

    /**
     * Nicehashの設定情報
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Nicehash {
        /** オーガニゼーションID */
        private String orgId;
        /** APIキー */
        private String apiKey;
        /** APIシークレットキー */
        private String apiSecret;
        /** リグID */
        private String rigId;
    }
}