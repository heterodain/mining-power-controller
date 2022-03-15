package com.heterodain.mining.powercontroller.device;

import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * マイニングリグデバイス(PC)
 */
@AllArgsConstructor
@Slf4j
public class MiningRigDevice {
    /** 電源状態監視用GPIO */
    private GpioPinDigitalInput pcPowerStatus;
    /** 電源オンオフ制御用GPIO */
    private GpioPinDigitalOutput pcPowerSw;

    /**
     * 稼働状態取得
     * 
     * @return true:電源ON, false:電源OFF
     */
    public boolean isStarted() {
        return pcPowerStatus.isHigh();
    }

    /**
     * 起動
     * 
     * @throws InterruptedException
     */
    public void start() throws InterruptedException {
        log.debug("リグの電源をONします。");

        pcPowerSw.high();
        Thread.sleep(300);
        pcPowerSw.low();
    }

    /**
     * 停止
     * 
     * @throws InterruptedException
     */
    public void stop() throws InterruptedException {
        log.debug("リグの電源をOFFします。");

        pcPowerSw.high();
        Thread.sleep(300);
        pcPowerSw.low();
    }
}
