package com.heterodain.mining.powercontroller.device;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import org.springframework.stereotype.Component;

import lombok.var;

/**
 * LM75A温度センサーデバイス
 */
@Component
public class Lm75aDevice {

    /**
     * 現在の温度取得
     * 
     * @return 温度
     */
    public synchronized double readCurrent(int address) throws UnsupportedBusNumberException, IOException {
        var i2cBus = I2CFactory.getInstance(I2CBus.BUS_1);
        var device = i2cBus.getDevice(address);
        return device.read();
    }
}
