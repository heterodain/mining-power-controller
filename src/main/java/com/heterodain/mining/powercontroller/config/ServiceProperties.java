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
public class ServiceProperties {
    /** Ambientの設定(3分値) */
    private Ambient ambient;
    /** Nicehash APIの設定 */
    private NicehashApi nicehashApi;
    /** Hive APIの設定 */
    private HiveApi hiveApi;

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
     * Nicehash APIの設定情報
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class NicehashApi {
        /** オーガニゼーションID */
        private String orgId;
        /** APIキー */
        private String apiKey;
        /** APIシークレットキー */
        private String apiSecret;
        /** リグID */
        private String rigId;
    }

    /**
     * Hive APIの設定情報
     */
    @Data
    public static class HiveApi {
        /** ファームID */
        private Integer farmId;
        /** ワーカーID */
        private Integer workerId;
        /** パーソナルAPIトークン */
        private String personalToken;
    }
}