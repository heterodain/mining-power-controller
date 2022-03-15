package com.heterodain.mining.powercontroller.device;

import java.io.IOException;
import java.util.Arrays;

import com.pi4j.io.i2c.I2CDevice;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LM75A温度センサーデバイス
 */
@AllArgsConstructor
@Slf4j
public class Lm75aDevice {
    /** I2Cデバイス */
    private I2CDevice device;

    /**
     * 現在の温度取得
     * 
     * @return 温度
     * @throws IOException
     */
    public double readCurrent() throws IOException {
        var buff = new byte[2];
        device.read(buff, 0, 2);

        if (log.isTraceEnabled()) {
            log.trace("lm75a: {}", Arrays.toString(buff));
        }

        return ((double) (((int) buff[0]) << 8 | Byte.toUnsignedInt(buff[1]))) / 256D;
    }
}
