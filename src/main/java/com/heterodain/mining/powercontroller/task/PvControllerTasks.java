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
import com.heterodain.mining.powercontroller.device.PvControllerDevice;
import com.heterodain.mining.powercontroller.device.PvControllerDevice.RealtimeData;
import com.heterodain.mining.powercontroller.service.AmbientService;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
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
    private PvControllerDevice pvControllerDevice;
    @Autowired
    private AmbientService ambientService;

    @Autowired
    private TaskExecutor taskExecutor;

    /** RS485シリアル接続 */
    private SerialConnection conn;
    /** GPIOコントローラー */
    private GpioController gpio;
    /** PC電源オンオフ制御GPIO */
    private GpioPinDigitalOutput pcPowerSw;
    /** PC電源状態監視GPIO */
    private GpioPinDigitalInput pcPowerStatus;
    /** 初期化済みフラグ */
    private boolean initialized = false;
    /** 計測データ */
    private List<RealtimeData> datas = new ArrayList<>();

    /**
     * 初期化
     */
    @PostConstruct
    public void init() throws IOException {
        // GPIO初期化
        log.info("GIPOを初期化します。");
        gpio = GpioFactory.getInstance();

        while (true) {
            try {
                Thread.sleep(3000);
                pcPowerSw = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_25, "PC_POWER_SW", PinState.LOW);
                pcPowerSw.setShutdownOptions(true, PinState.LOW);
                break;
            } catch (Exception e) {
                log.warn("GPIO26(25)の初期化に失敗しました。リトライします。", e);
            }
        }

        while (true) {
            try {
                Thread.sleep(3000);
                pcPowerStatus = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00, "PC_POWER_STATUS",
                        PinPullResistance.PULL_DOWN);
                break;
            } catch (Exception e) {
                log.warn("GPIO17(0)の初期化に失敗しました。リトライします。", e);
            }
        }

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
            synchronized (datas) {
                datas.add(data);
            }
        } catch (Exception e) {
            log.warn("PVコントローラーへのアクセスに失敗しました。", e);
        }
    }

    /**
     * 3分毎に電源制御&Ambientにデータ送信
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void summary() {
        if (datas.size() < 5) {
            return;
        }

        // 集計
        double pvPower, battVolt, loadPower, battSOC;
        synchronized (datas) {
            pvPower = datas.stream().mapToDouble(RealtimeData::getPvPower).average().orElse(0D);
            battVolt = datas.stream().mapToDouble(RealtimeData::getBattVolt).average().orElse(0D);
            loadPower = datas.stream().mapToDouble(RealtimeData::getLoadPower).average().orElse(0D);
            battSOC = datas.stream().mapToDouble(RealtimeData::getBattSOC).average().orElse(0D);
            datas.clear();
        }

        // PCの電源状態取得
        boolean pcPowerOn = pcPowerStatus.isHigh();

        // 電源制御
        taskExecutor.execute(() -> {
            try {
                if (battSOC > 91D && !pcPowerOn) {
                    // バッテリー残量>91%のとき、PC電源ON
                    log.debug("チャージコントローラーの負荷出力をONします。");
                    pvControllerDevice.changeLoadSwith(deviceConfig.getPvController(), conn, true);
                    Thread.sleep(10000);

                    log.info("PC電源をONします。");
                    pcPowerSw.high();
                    Thread.sleep(300);
                    pcPowerSw.low();

                } else if (/* battSOC < 30D && */ battVolt < 23.8D && pcPowerOn) {
                    // 電圧が23.8ボルト未満のとき、PC電源OFF
                    // TODO 電圧は負荷やバッテリーの劣化度に応じて要調整(負荷100Wで24.1, 200Wで23.8くらいが目安)
                    log.info("PC電源をOFFします。");
                    pcPowerSw.high();
                    Thread.sleep(300);
                    pcPowerSw.low();
                }

                // 稀に負荷出力がOFFに切り替わらないことがあるので、毎回OFFにする
                if (!pcPowerOn) {
                    log.debug("チャージコントローラーの負荷出力をOFFします。");
                    pvControllerDevice.changeLoadSwith(deviceConfig.getPvController(), conn, false);
                }
            } catch (Exception e) {
                log.warn("電源制御に失敗しました。", e);
            }
        });

        // Ambient送信
        taskExecutor.execute(() -> {
            try {
                var sendDatas = new Double[] { pvPower, battVolt, loadPower, battSOC, pcPowerOn ? 1D : 0D };
                log.debug("Ambientに3分値を送信します。pcPower={},battVolt={},loadPower={},battSOC={},pcPowerOn={}", sendDatas[0],
                        sendDatas[1], sendDatas[2], sendDatas[3], sendDatas[4]);

                ambientService.send(serviceConfig.getAmbient(), ZonedDateTime.now(), sendDatas);
            } catch (Exception e) {
                log.warn("Ambientへのデータ送信に失敗しました。", e);
            }
        });
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
}
