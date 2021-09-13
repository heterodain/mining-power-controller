package com.heterodain.mining.powercontroller.config;

import java.util.Arrays;
import java.util.List;

import com.heterodain.mining.powercontroller.device.PvControllerDevice.STAGE;

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
     * 電源制御の設定
     */
    @Data
    public static class Power {
        /** 電源をONにする条件 */
        private PowerCondition powerOnCondition;
        /** 電源をOFFにする条件 */
        private PowerCondition powerOffCondition;
    }

    /**
     * 電源制御の条件設定
     */
    @Data
    public static class PowerCondition {
        private static List<STAGE> STAGES = Arrays.asList(STAGE.values());

        /** 充電ステージ */
        private STAGE stage;
        /** 残量(%) */
        private Double soc;
        /** 電圧(V) */
        private Double voltage;

        /** 比較する */
        public int compare(STAGE _stage, Double _soc, Double _voltage) {
            if (stage != null) {
                return STAGES.indexOf(_stage) - STAGES.indexOf(stage);
            }
            if (soc != null) {
                return _soc == soc ? 0 : _soc < soc ? -1 : 1;
            }
            if (voltage != null) {
                return _voltage == voltage ? 0 : _voltage < voltage ? -1 : 1;
            }
            return 0;
        }
    }
}