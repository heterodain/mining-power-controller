package com.heterodain.mining.powercontroller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * デバイスの設定
 */
@Component
@ConfigurationProperties("device")
@Data
public class DeviceConfig {
    /* チャージコントローラーの設定 */
    private PvController pvController;

    /**
     * チャージコントローラーの設定情報
     */
    @Data
    public static class PvController {
        /* シリアル通信ポート名 */
        private String comPort;
        /* RS485のユニットID */
        private Integer unitId;
    }
}