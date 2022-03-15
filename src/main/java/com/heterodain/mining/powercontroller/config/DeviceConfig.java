package com.heterodain.mining.powercontroller.config;

import java.io.IOException;

import com.heterodain.mining.powercontroller.device.BatteryHeaterDevice;
import com.heterodain.mining.powercontroller.device.CoolingFanDevice;
import com.heterodain.mining.powercontroller.device.Lm75aDevice;
import com.heterodain.mining.powercontroller.device.MiningRigDevice;
import com.heterodain.mining.powercontroller.device.PvControllerDevice;
import com.heterodain.mining.powercontroller.device.RaspberryPiDevice;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * デバイス設定
 */
@Configuration
@Slf4j
public class DeviceConfig {

    /**
     * マイニングリグデバイス
     * 
     * @param raspberryPiDevice ラズベリーパイデバイス
     * @return マイニングリグデバイス
     * @throws InterruptedException
     */
    // TODO GPIOピン番号の設定化
    @Bean
    public MiningRigDevice miningRigDevice(RaspberryPiDevice raspberryPiDevice) throws InterruptedException {
        var statusGpio = raspberryPiDevice.initInputGPIO(RaspiPin.GPIO_00, "PC_POWER_STATUS",
                PinPullResistance.PULL_DOWN);
        var swGipo = raspberryPiDevice.initOutputGPIO(RaspiPin.GPIO_25, "PC_POWER_SW", PinState.LOW);
        return new MiningRigDevice(statusGpio, swGipo);
    }

    /**
     * 冷却FANデバイス
     * 
     * @param raspberryPiDevice ラズベリーパイデバイス
     * @return 冷却FANデバイス
     * @throws InterruptedException
     */
    // TODO GPIOピン番号の設定化
    @Bean
    public CoolingFanDevice coolingFanDevice(RaspberryPiDevice raspberryPiDevice) throws InterruptedException {
        var gpio = raspberryPiDevice.initOutputGPIO(RaspiPin.GPIO_02, "FAN_POWER_SW", PinState.LOW);
        return new CoolingFanDevice(gpio);
    }

    /**
     * バッテリーヒーターデバイス
     * 
     * @param raspberryPiDevice ラズベリーパイデバイス
     * @return バッテリーヒーターデバイス
     * @throws InterruptedException
     */
    // TODO GPIOピン番号の設定化
    @Bean
    public BatteryHeaterDevice batteryHeaterDevice(RaspberryPiDevice raspberryPiDevice) throws InterruptedException {
        var gpio = raspberryPiDevice.initOutputGPIO(RaspiPin.GPIO_24, "BATT_HEATER_SW", PinState.LOW);
        return new BatteryHeaterDevice(gpio);
    }

    /**
     * Epeverチャージコントローラデバイス
     * 
     * @param raspberryPiDevice ラズベリーパイデバイス
     * @param deviceProperties  デバイス設定
     * @return
     * @throws InterruptedException
     */
    // TODO GPIOピン番号の設定化
    @Bean
    public PvControllerDevice pvControllerDevice(RaspberryPiDevice raspberryPiDevice, DeviceProperties deviceProperties)
            throws InterruptedException {
        var loadPowerRegisterSw = raspberryPiDevice.initOutputGPIO(RaspiPin.GPIO_27, "LOAD_POWER_REG_SW", PinState.LOW);
        var unitId = deviceProperties.getPvController().getUnitId();
        return new PvControllerDevice(unitId, loadPowerRegisterSw);
    }

    /**
     * 温度センサーデバイス
     * 
     * @param raspberryPiDevice ラズベリーパイデバイス
     * @param deviceProperties  デバイス設定
     * @return 温度センサーデバイス
     * @throws UnsupportedBusNumberException
     * @throws IOException
     */
    @Bean
    public Lm75aDevice lm75aDevice(RaspberryPiDevice raspberryPiDevice, DeviceProperties deviceProperties)
            throws UnsupportedBusNumberException, IOException {
        var address = deviceProperties.getLm75a().getAddress();
        var i2cdevice = raspberryPiDevice.initI2cDevice(address);
        return new Lm75aDevice(i2cdevice);
    }
}
