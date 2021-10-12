package com.heterodain.mining.powercontroller.task;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

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
import com.heterodain.mining.powercontroller.service.NicehashService;
import com.heterodain.mining.powercontroller.service.NicehashService.POWER_MODE;
import com.heterodain.mining.powercontroller.service.NicehashService.RigStatus;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    @Autowired
    private NicehashService nicehashService;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    /** RS485シリアル接続 */
    private SerialConnection conn;
    /** GPIOコントローラー */
    private GpioController gpio;
    /** 負荷出力抵抗オンオフ制御GPIO */
    private GpioPinDigitalOutput loadPowerRegisterSw;
    /** PC電源オンオフ制御GPIO */
    private GpioPinDigitalOutput pcPowerSw;
    /** 冷却ファンオンオフ制御GPIO */
    private GpioPinDigitalOutput fanPowerSw;
    /** PC電源状態監視GPIO */
    private GpioPinDigitalInput pcPowerStatus;
    /** 初期化済みフラグ */
    private boolean initialized = false;
    /** 計測データ(3秒値) */
    private List<RealtimeData> threeSecDatas = new ArrayList<>();
    /** 計測データ(30秒値) */
    private List<RealtimeData> thirtySecDatas = new ArrayList<>();
    /** 計測データ(15分値) */
    private List<RealtimeData> fifteenMinDatas = new ArrayList<>();
    /** ファン停止タスク実行結果 */
    private Future<?> fanStopFuture;
    /** リグの状態 */
    private RigStatus rigStatus;
    /** PC起動時刻 */
    private LocalDateTime pcStartTime;

    /**
     * 初期化
     */
    @PostConstruct
    public void init() throws Exception {
        // GPIO初期化
        log.info("GIPOを初期化します。");
        gpio = GpioFactory.getInstance();
        loadPowerRegisterSw = initOutputGPIO(RaspiPin.GPIO_27, "LOAD_POWER_REG_SW", PinState.LOW);
        pcPowerSw = initOutputGPIO(RaspiPin.GPIO_25, "PC_POWER_SW", PinState.LOW);
        fanPowerSw = initOutputGPIO(RaspiPin.GPIO_02, "FAN_POWER_SW", PinState.LOW);
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

        // 既にPCが起動中だった場合はファンを始動
        boolean pcPowerOn = pcPowerStatus.isHigh();
        if (pcPowerOn) {
            log.info("冷却ファンを始動します。");
            fanPowerSw.high();
        }

        // リグのステータス取得
        log.info("マイニングリグの情報を取得します。");
        var nicehashConfig = serviceConfig.getNicehash();
        var serverTime = nicehashService.getServerTime();
        rigStatus = nicehashService.getRigStatus(nicehashConfig, serverTime);
        log.debug("リグの状態: {}", rigStatus);

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
    @Scheduled(fixedDelay = 30 * 1000, initialDelay = 30 * 1000)
    public void powerControl() {
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
            var powerOnCondition = powerConfig.getPowerOnCondition();
            var powerOffCondition = powerConfig.getPowerOffCondition();

            if (!pcPowerOn && powerOnCondition.graterEqual(summary.getPvPower(), summary.getBattSOC(),
                    summary.getBattVolt(), summary.getStage())) {
                // 設定条件以上のとき、PC電源ON
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
                pcStartTime = LocalDateTime.now();

                log.info("冷却ファンを始動します。");
                if (fanStopFuture != null && !fanStopFuture.isDone()) {
                    fanStopFuture.cancel(true);
                }
                Thread.sleep(100);
                fanPowerSw.high();

            } else if (pcPowerOn && powerOffCondition.lessEqual(summary.getPvPower(), summary.getBattSOC(),
                    summary.getBattVolt(), summary.getStage())) {
                // 設定条件以下のとき、PC電源OFF
                log.info("PC電源をOFFします。");
                pcPowerSw.high();
                Thread.sleep(300);
                pcPowerSw.low();

                Thread.sleep(20000);

                log.info("負荷出力をOFFします。");
                pvControllerDevice.changeLoadSwith(pvControllerConfig, conn, false);

                // 指定時間待ってから冷却ファンを止める
                fanStopFuture = taskExecutor.submit(() -> {
                    try {
                        Thread.sleep(controlConfig.getFan().getPowerOffDuration() * 60 * 1000);
                    } catch (InterruptedException ignore) {
                        // NOP
                    }

                    log.info("冷却ファンを停止します。");
                    fanPowerSw.low();
                });
            }

        } catch (Exception e) {
            log.warn("電源制御に失敗しました。", e);
        }
    }

    /**
     * 3分毎にAmbientにデータ送信
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void sendAmbient() throws Exception {
        if (thirtySecDatas.isEmpty()) {
            return;
        }

        // 集計
        RealtimeData summary;
        synchronized (thirtySecDatas) {
            summary = RealtimeData.summary(thirtySecDatas);
            thirtySecDatas.clear();
        }
        synchronized (fifteenMinDatas) {
            fifteenMinDatas.add(summary);
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
     * 15分毎にTDP制御
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    public void tdpControl() throws Exception {
        if (fifteenMinDatas.isEmpty()) {
            return;
        }

        // 集計
        RealtimeData summary;
        synchronized (fifteenMinDatas) {
            summary = RealtimeData.summary(fifteenMinDatas);
            fifteenMinDatas.clear();
        }

        if (pcStartTime == null || ChronoUnit.MINUTES.between(pcStartTime, LocalDateTime.now()) < 15) {
            return;
        }

        boolean pcPowerOn = pcPowerStatus.isHigh();
        Double histeresis = controlConfig.getTdp().getHysteresis();

        // TDP制御
        if (pcPowerOn && (summary.getPvPower() - summary.getLoadPower()) > histeresis) {
            // 発電電力>消費電力のとき、TDPを上げる
            var powerMode = rigStatus.getRigPowerMode();
            var newPowerMode = powerMode == POWER_MODE.LOW ? POWER_MODE.MEDIUM
                    : powerMode == POWER_MODE.MEDIUM ? POWER_MODE.HIGH : POWER_MODE.HIGH;
            if (powerMode != newPowerMode) {
                log.info("リグのPowerModeを変更します。{} to {}", powerMode, newPowerMode);
                var time = nicehashService.getServerTime();
                if (nicehashService.setRigPowerMode(serviceConfig.getNicehash(), time, newPowerMode)) {
                    rigStatus.setRigPowerMode(newPowerMode);
                }
            }

        } else if (pcPowerOn && (summary.getLoadPower() - summary.getPvPower()) > histeresis) {
            // 発電電力<消費電力のとき、TDPを下げる
            var powerMode = rigStatus.getRigPowerMode();
            var newPowerMode = powerMode == POWER_MODE.HIGH ? POWER_MODE.MEDIUM
                    : powerMode == POWER_MODE.MEDIUM ? POWER_MODE.LOW : POWER_MODE.LOW;
            if (powerMode != newPowerMode) {
                log.info("リグのPowerModeを変更します。{} to {}", powerMode, newPowerMode);
                var time = nicehashService.getServerTime();
                if (nicehashService.setRigPowerMode(serviceConfig.getNicehash(), time, newPowerMode)) {
                    rigStatus.setRigPowerMode(newPowerMode);
                }
            }
        }
    }

    /**
     * 15分毎にファンを回す
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void fanControl() {
        // PCが電源OFFかつ、クーリング中でなければファンを回す
        boolean pcPowerOn = pcPowerStatus.isHigh();
        if (!pcPowerOn && taskExecutor.getActiveCount() == 0) {
            try {
                log.info("冷却ファンを始動します。");
                fanPowerSw.high();
                Thread.sleep(controlConfig.getFan().getDuration() * 1000);
                log.info("冷却ファンを停止します。");
                fanPowerSw.low();
            } catch (Exception e) {
                log.warn("ファン制御に失敗しました。", e);
            }
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
                log.warn("", e);
                log.warn(pin + "の初期化に失敗しました。リトライします。");
            }
        }
    }

    private GpioPinDigitalInput initInputGPIO(Pin pin, String name, PinPullResistance pull) {
        while (true) {
            try {
                Thread.sleep(3000);
                return gpio.provisionDigitalInputPin(pin, name, pull);
            } catch (Exception e) {
                log.warn("", e);
                log.warn(pin + "の初期化に失敗しました。リトライします。");
            }
        }
    }
}
