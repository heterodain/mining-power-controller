package com.heterodain.mining.powercontroller.device;

import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;

import lombok.extern.slf4j.Slf4j;

/**
 * マイニングリグデバイス(PC)
 */
@Slf4j
public class MiningRigDevice {
    /** 電源状態監視用GPIO */
    private GpioPinDigitalInput pcPowerStatus;
    /** 電源オンオフ制御用GPIO */
    private GpioPinDigitalOutput pcPowerSw;

    /**
     * コンストラクタ
     * 
     * @param pcPowerStatus 電源状態監視用GPIO
     * @param pcPowerSw     電源オンオフ制御用GPIO
     */
    public MiningRigDevice(GpioPinDigitalInput pcPowerStatus, GpioPinDigitalOutput pcPowerSw) {
        this.pcPowerStatus = pcPowerStatus;
        this.pcPowerSw = pcPowerSw;
    }

    /**
     * 電源状態取得
     * 
     * @return true:電源ON, false:電源OFF
     */
    public boolean isPowerOn() {
        return pcPowerStatus.isHigh();
    }

    /**
     * 電源ON
     * 
     * @throws InterruptedException
     */
    public void powerOn() throws InterruptedException {
        log.debug("リグの電源をONします。");

        pcPowerSw.high();
        Thread.sleep(300);
        pcPowerSw.low();
    }

    /**
     * 電源OFF
     * 
     * @throws InterruptedException
     */
    public void powerOff() throws InterruptedException {
        log.debug("リグの電源をOFFします。");

        pcPowerSw.high();
        Thread.sleep(300);
        pcPowerSw.low();
    }
}
