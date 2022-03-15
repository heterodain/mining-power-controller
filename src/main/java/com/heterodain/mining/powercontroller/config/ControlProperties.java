package com.heterodain.mining.powercontroller.config;

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
public class ControlProperties {
    /** 電源制御の設定 */
    private Power power;
    /** ファン制御の設定 */
    private Fan fan;
    /** バッテリーヒーターの設定 */
    private BatteryHeater batteryHeater;

    /**
     * 電源制御の設定
     */
    @Data
    public static class Power {
        /** 電源をONにする条件 */
        private PowerCondition powerOnCondition;
        /** 電源をOFFにする条件 */
        private PowerCondition powerOffCondition;

        /** 高電力設定プロファイル名 */
        private String highProfileName;
        /** 低電力設定プロファイル名 */
        private String lowProfileName;
        /** 調整感度(ワット) */
        private Double hysteresis;
    }

    /**
     * 電源制御の条件設定
     */
    @Data
    public static class PowerCondition {
        /** 電圧(V) */
        private Double voltage;
        /** 発電電力(W) */
        private Double power;
        /** 残量(%) */
        private Double soc;
        /** 充電ステージ */
        private STAGE stage;

        /** 引数の値が設定値以上かどうか */
        public boolean graterEqual(Double _power, Double _soc, Double _voltage, STAGE _stage) {
            int ret = 0;
            if (power != null) {
                ret = _power == power ? 0 : _power < power ? -1 : 1;
            }
            if (voltage != null && ret <= 0) {
                ret = _voltage == voltage ? 0 : _voltage < voltage ? -1 : 1;
            }
            if (soc != null && ret <= 0) {
                ret = _soc == soc ? 0 : _soc < soc ? -1 : 1;
            }
            if (stage != null && ret <= 0) {
                ret = _stage.getIndex() - stage.getIndex();
            }
            return ret >= 0;
        }

        /** 引数の値が設定値以下かどうか */
        public boolean lessEqual(Double _power, Double _soc, Double _voltage, STAGE _stage) {
            int ret = 0;
            if (power != null) {
                ret = _power == power ? 0 : _power < power ? -1 : 1;
            }
            if (voltage != null && ret <= 0) {
                ret = _voltage == voltage ? 0 : _voltage < voltage ? -1 : 1;
            }
            if (soc != null && ret <= 0) {
                ret = _soc == soc ? 0 : _soc < soc ? -1 : 1;
            }
            if (stage != null && ret <= 0) {
                ret = _stage.getIndex() - stage.getIndex();
            }
            return ret <= 0;
        }
    }

    /**
     * ファン制御の設定
     */
    @Data
    public static class Fan {
        /** PC停止後、冷却FAN停止までの時間(分) */
        private Integer powerOffDuration;
        /** 15分毎の冷却FAN動作時間(秒) */
        private Integer duration;
    }

    /**
     * バッテリーヒーターの設定
     */
    @Data
    public static class BatteryHeater {
        /** 温度範囲 */
        private Double[] temperatureRange;
        /** 制御時間帯 */
        private String[] hourRange;
    }
}