package com.heterodain.mining.powercontroller.device;

import com.pi4j.io.gpio.GpioPinDigitalOutput;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 冷却FANデバイス
 */
@AllArgsConstructor
@Slf4j
public class CoolingFanDevice {
    /** 冷却FAN制御用のGPIO */
    private GpioPinDigitalOutput fanPowerSw;

    /**
     * 稼働状態取得
     * 
     * @return true:稼働中, false:停止中
     */
    public boolean isStarted() {
        return fanPowerSw.isHigh();
    }

    /**
     * 始動
     */
    public void start() {
        log.debug("冷却ファンを始動します。");

        fanPowerSw.high();
    }

    /**
     * 停止
     */
    public void stop() {
        log.debug("冷却ファンを始動します。");

        fanPowerSw.low();
    }
}
