package com.heterodain.mining.powercontroller.device;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import org.springframework.stereotype.Component;

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

        var buff = new byte[2];
        device.read(buff, 0, 2);
        return ((double) (((int) buff[0]) << 8 | Byte.toUnsignedInt(buff[1]))) / 256D;
    }
}
