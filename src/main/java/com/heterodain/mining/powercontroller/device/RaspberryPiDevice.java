package com.heterodain.mining.powercontroller.device;

import java.io.IOException;

import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * ラズベリーパイデバイス
 */
@Component
@Slf4j
public class RaspberryPiDevice {

    public synchronized GpioPinDigitalOutput initOutputGPIO(Pin pin, String name, PinState initial)
            throws InterruptedException {
        log.info("GPIOを初期化します。{}", pin);

        var gpioController = GpioFactory.getInstance();
        while (true) {
            Thread.sleep(3000);
            try {
                var result = gpioController.provisionDigitalOutputPin(pin, name, initial);
                result.setShutdownOptions(true, initial);
                return result;
            } catch (Exception e) {
                log.warn("", e);
                log.warn("{}の初期化に失敗しました。リトライします。", pin);
            }
        }
    }

    public synchronized GpioPinDigitalInput initInputGPIO(Pin pin, String name, PinPullResistance pull)
            throws InterruptedException {
        log.info("GPIOを初期化します。{}", pin);

        var gpioController = GpioFactory.getInstance();
        while (true) {
            Thread.sleep(3000);
            try {
                return gpioController.provisionDigitalInputPin(pin, name, pull);
            } catch (Exception e) {
                log.warn("", e);
                log.warn("{}の初期化に失敗しました。リトライします。", pin);
            }
        }
    }

    public I2CDevice initI2cDevice(int address) throws UnsupportedBusNumberException, IOException {
        log.info("I2Cデバイスに接続します。address={}", address);

        var bus = I2CFactory.getInstance(I2CBus.BUS_1);
        return bus.getDevice(address);
    }

    public synchronized void shutdown() {
        log.info("GPIOをシャットダウンします。");

        GpioFactory.getInstance().shutdown();
    }
}
