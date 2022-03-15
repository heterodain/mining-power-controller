package com.heterodain.mining.powercontroller.device;

import com.pi4j.io.gpio.GpioPinDigitalOutput;

import lombok.extern.slf4j.Slf4j;

/**
 * バッテリーヒーターデバイス
 */
@Slf4j
public class BatteryHeaterDevice {
    /** バッテリーヒーター制御用のGPIO */
    private GpioPinDigitalOutput battHeaterSw;

    /**
     * コンストラクタ
     * 
     * @param battHeaterSw バッテリーヒーター制御用GPIO
     */
    public BatteryHeaterDevice(GpioPinDigitalOutput battHeaterSw) {
        this.battHeaterSw = battHeaterSw;
    }

    /**
     * 稼働状態取得
     * 
     * @return true:稼働中, false:停止中
     */
    public boolean isStarted() {
        log.debug("バッテリーヒーターを始動します。");

        return battHeaterSw.isHigh();
    }

    /**
     * 始動
     */
    public void start() {
        log.debug("バッテリーヒーターを停止します。");

        battHeaterSw.high();
    }

    /**
     * 停止
     */
    public void stop() {
        battHeaterSw.low();
    }
}
