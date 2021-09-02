package com.heterodain.mining.powercontroller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 制御の設定
 */
@Component
@ConfigurationProperties("control")
@Data
public class ControlConfig {
    /* 電源制御の設定 */
    private Power power;

    /**
     * 電源制御の設定情報
     */
    @Data
    public static class Power {
        /** 電源をONにするバッテリー残量 */
        Double powerOnSoc;
        /** 電源をOFFにするバッテリー電圧 */
        Double powerOffVoltage;
    }
}