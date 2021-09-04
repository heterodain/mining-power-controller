package com.heterodain.mining.powercontroller.task;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import com.heterodain.mining.powercontroller.config.DeviceConfig;
import com.heterodain.mining.powercontroller.config.ServiceConfig;
import com.heterodain.mining.powercontroller.config.ControlConfig;
import com.heterodain.mining.powercontroller.device.PvControllerDevice;
import com.heterodain.mining.powercontroller.device.PvControllerDevice.RealtimeData;
import com.heterodain.mining.powercontroller.service.AmbientService;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * PVコントローラー関連の非同期タスク
 */
@Component
@Slf4j
public class PvControllerTasks {
    @Autowired
    private DeviceConfig deviceConfig;
    @Autowired
    private ServiceConfig serviceConfig;
    @Autowired
    private ControlConfig controlConfig;

    @Autowired
    private PvControllerDevice pvControllerDevice;
    @Autowired
    private AmbientService ambientService;

    /** RS485シリアル接続 */
    private SerialConnection conn;
    /** GPIOコントローラー */
    private GpioController gpio;
    /** 負荷出力抵抗オンオフ制御GPIO */
    private GpioPinDigitalOutput loadPowerRegisterSw;
    /** PC電源オンオフ制御GPIO */
    private GpioPinDigitalOutput pcPowerSw;
    /** PC電源状態監視GPIO */
    private GpioPinDigitalInput pcPowerStatus;
    /** 初期化済みフラグ */
    private boolean initialized = false;
    /** 計測データ(3秒値) */
    private List<RealtimeData> threeSecDatas = new ArrayList<>();
    /** 計測データ(30秒値) */
    private List<RealtimeData> thirtySecDatas = new ArrayList<>();

    /**
     * 初期化
     */
    @PostConstruct
    public void init() throws IOException {
        // GPIO初期化
        log.info("GIPOを初期化します。");
        gpio = GpioFactory.getInstance();
        loadPowerRegisterSw = initOutputGPIO(RaspiPin.GPIO_27, "LOAD_POWER_REG_SW", PinState.LOW);
        pcPowerSw = initOutputGPIO(RaspiPin.GPIO_25, "PC_POWER_SW", PinState.LOW);
        pcPowerStatus = initInputGPIO(RaspiPin.GPIO_00, "PC_POWER_STATUS", PinPullResistance.PULL_DOWN);

        // PVコントローラー初期化
        var pvcSetting = deviceConfig.getPvController();
        log.info("PVコントローラーに接続します。unitId={}", pvcSetting.getUnitId());

        var serialParam = new SerialParameters();
        serialParam.setPortName(pvcSetting.getComPort());
        serialParam.setBaudRate(115200);
        serialParam.setDatabits(8);
        serialParam.setParity("None");
        serialParam.setStopbits(1);
        serialParam.setEncoding("rtu");
        serialParam.setEcho(false);
        conn = new SerialConnection(serialParam);
        conn.open();

        initialized = true;
    }

    /**
     * 3秒毎にPVコントローラーからデータ取得
     */
    @Scheduled(initialDelay = 3 * 1000, fixedDelay = 3 * 1000)
    public void realtime() {
        if (!initialized) {
            return;
        }

        try {
            var data = pvControllerDevice.readCurrent(deviceConfig.getPvController(), conn);
            synchronized (threeSecDatas) {
                threeSecDatas.add(data);
            }
        } catch (Exception e) {
            log.warn("PVコントローラーへのアクセスに失敗しました。", e);
        }
    }

    /**
     * 30秒毎に電源制御
     */
    @Scheduled(cron = "0 * * * * *")
    public void controlPower() {
        if (threeSecDatas.size() < 5) {
            return;
        }

        // 集計
        RealtimeData summary;
        synchronized (threeSecDatas) {
            summary = RealtimeData.summary(threeSecDatas);
            threeSecDatas.clear();
        }
        synchronized (thirtySecDatas) {
            thirtySecDatas.add(summary);
        }

        // PCの電源状態取得
        boolean pcPowerOn = pcPowerStatus.isHigh();

        // 電源制御
        try {
            var pvControllerConfig = deviceConfig.getPvController();
            var powerConfig = controlConfig.getPower();

            if (summary.getBattSOC() >= powerConfig.getPowerOnSoc() && !pcPowerOn) {
                // バッテリー残量が設定値以上のとき、PC電源ON
                log.info("負荷出力をONします。");

                // DCDCコンバーターにいきなり接続すると、
                // 突入電流でチャージコントローラーの保護回路が働いてしまうので、
                // 5Ω抵抗経由で接続したあと、ダイレクトに接続する
                loadPowerRegisterSw.high();
                Thread.sleep(300);
                pvControllerDevice.changeLoadSwith(pvControllerConfig, conn, true);
                Thread.sleep(1000);
                loadPowerRegisterSw.low();
                Thread.sleep(4000);

                log.info("PC電源をONします。");
                pcPowerSw.high();
                Thread.sleep(300);
                pcPowerSw.low();

            } else if (/* summary.getBattSOC() <= 30D && */ summary.getBattVolt() <= powerConfig.getPowerOffVoltage()
                    && pcPowerOn) {
                // 電圧が設定値以下のとき、PC電源OFF
                log.info("PC電源をOFFします。");
                pcPowerSw.high();
                Thread.sleep(300);
                pcPowerSw.low();

                Thread.sleep(20000);
                log.info("負荷出力をOFFします。");
                pvControllerDevice.changeLoadSwith(pvControllerConfig, conn, false);
            }

        } catch (Exception e) {
            log.warn("電源制御に失敗しました。", e);
        }
    }

    /**
     * 3分毎にAmbientにデータ送信
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void sendAmbient() {
        if (thirtySecDatas.isEmpty()) {
            return;
        }

        // 集計
        RealtimeData summary;
        synchronized (thirtySecDatas) {
            summary = RealtimeData.summary(thirtySecDatas);
            thirtySecDatas.clear();
        }

        // PCの電源状態取得
        boolean pcPowerOn = pcPowerStatus.isHigh();

        // Ambient送信
        try {
            var sendDatas = new Double[] { summary.getPvPower(), summary.getBattVolt(), summary.getLoadPower(),
                    summary.getBattSOC(), pcPowerOn ? 1D : 0D };
            log.debug("Ambientに3分値を送信します。pcPower={},battVolt={},loadPower={},battSOC={},pcPowerOn={}", sendDatas[0],
                    sendDatas[1], sendDatas[2], sendDatas[3], sendDatas[4]);

            ambientService.send(serviceConfig.getAmbient(), ZonedDateTime.now(), sendDatas);
        } catch (Exception e) {
            log.warn("Ambientへのデータ送信に失敗しました。", e);
        }
    }

    /**
     * 終了処理
     */
    @PreDestroy
    public void destroy() {
        if (gpio != null) {
            log.debug("GPIOをシャットダウンします。");
            gpio.shutdown();
        }
        if (conn != null) {
            log.debug("PVコントローラーを切断します。unitId={}", deviceConfig.getPvController().getUnitId());
            conn.close();
        }

        initialized = false;
    }

    private GpioPinDigitalOutput initOutputGPIO(Pin pin, String name, PinState initial) {
        while (true) {
            try {
                Thread.sleep(3000);
                GpioPinDigitalOutput result = gpio.provisionDigitalOutputPin(pin, name, initial);
                result.setShutdownOptions(true, initial);
                return result;
            } catch (Exception e) {
                log.warn("GPIO(" + pin + ")の初期化に失敗しました。リトライします。", e);
            }
        }
    }

    private GpioPinDigitalInput initInputGPIO(Pin pin, String name, PinPullResistance pull) {
        while (true) {
            try {
                Thread.sleep(3000);
                return gpio.provisionDigitalInputPin(pin, name, pull);
            } catch (Exception e) {
                log.warn("GPIO(" + pin + ")の初期化に失敗しました。リトライします。", e);
            }
        }
    }
}
