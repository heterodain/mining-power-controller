package com.heterodain.mining.powercontroller.task;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import com.heterodain.mining.powercontroller.config.DeviceProperties;
import com.heterodain.mining.powercontroller.config.ServiceProperties;
import com.heterodain.mining.powercontroller.config.ControlProperties;
import com.heterodain.mining.powercontroller.device.BatteryHeaterDevice;
import com.heterodain.mining.powercontroller.device.CoolingFanDevice;
import com.heterodain.mining.powercontroller.device.Lm75aDevice;
import com.heterodain.mining.powercontroller.device.MiningRigDevice;
import com.heterodain.mining.powercontroller.device.PvControllerDevice;
import com.heterodain.mining.powercontroller.device.RaspberryPiDevice;
import com.heterodain.mining.powercontroller.device.PvControllerDevice.RealtimeData;
import com.heterodain.mining.powercontroller.service.AmbientService;
import com.heterodain.mining.powercontroller.service.HiveService;
import com.heterodain.mining.powercontroller.service.NicehashService;
import com.heterodain.mining.powercontroller.service.HiveService.OcProfile;
import com.heterodain.mining.powercontroller.service.NicehashService.RigStatus;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * PVコントローラー関連の非同期タスク
 */
@Component
@Slf4j
public class PvControllerTasks {
    @Autowired
    private DeviceProperties deviceProperties;
    @Autowired
    private ServiceProperties serviceProperties;
    @Autowired
    private ControlProperties controlProperties;

    @Autowired
    private RaspberryPiDevice raspberryPiDevice;
    @Autowired
    private PvControllerDevice pvControllerDevice;
    @Autowired
    private Lm75aDevice lm75aDevice;
    @Autowired
    private MiningRigDevice miningRigDevice;
    @Autowired
    private CoolingFanDevice coolingFanDevice;
    @Autowired
    private BatteryHeaterDevice batteryHeaterDevice;

    @Autowired
    private AmbientService ambientService;
    @Autowired
    private NicehashService nicehashService;
    @Autowired
    private HiveService hiveService;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    /** RS485シリアル接続 */
    private SerialConnection conn;
    /** 初期化済みフラグ */
    private boolean initialized = false;

    /** 計測データ(3秒値) */
    private List<RealtimeData> threeSecDatas = new ArrayList<>();
    /** 計測データ(1分値) */
    private List<RealtimeData> oneMinDatas = new ArrayList<>();
    /** 計測データ(15分値) */
    private List<RealtimeData> fifteenMinDatas = new ArrayList<>();

    /** ファン停止タスク実行結果 */
    private Future<?> fanStopFuture;
    /** PC起動時刻 */
    private LocalDateTime pcStartTime;
    /** シャットダウン要求 */
    private boolean shutdownRequest = false;
    /** リグの状態(Nicehash OS) */
    private RigStatus currentRigStatus;
    /** OCプロファイル(Hive OS) */
    private OcProfile currentOcProfile;

    /**
     * 初期化
     */
    @PostConstruct
    public void init() throws Exception {
        var pvcConfig = deviceProperties.getPvController();

        // RS485シリアル接続
        var serialParam = new SerialParameters();
        serialParam.setPortName(pvcConfig.getComPort());
        serialParam.setBaudRate(115200);
        serialParam.setDatabits(8);
        serialParam.setParity("None");
        serialParam.setStopbits(1);
        serialParam.setEncoding("rtu");
        serialParam.setEcho(false);
        conn = new SerialConnection(serialParam);
        conn.open();

        // 既にPCが起動中だった場合はファンを始動
        if (miningRigDevice.isStarted()) {
            coolingFanDevice.start();
        }

        // Nicehash OSのリグ状態取得
        var nicehashConfig = serviceProperties.getNicehashApi();
        if (nicehashConfig != null) {
            currentRigStatus = nicehashService.getRigStatus(nicehashConfig);
        }

        // Hive OSのOCプロファイル取得
        var hiveConfig = serviceProperties.getHiveApi();
        if (hiveConfig != null) {
            var ocProfileId = hiveService.getWorkerOcProfileId(hiveConfig);
            currentOcProfile = hiveService.getOcProfiles(hiveConfig).get(ocProfileId);
        }

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
            var data = pvControllerDevice.readCurrent(conn);
            synchronized (threeSecDatas) {
                threeSecDatas.add(data);
            }
        } catch (Exception e) {
            log.error("PVコントローラーへのアクセスに失敗しました。", e);
        }
    }

    /**
     * 1分毎に電源制御
     */
    @Scheduled(fixedDelay = 1 * 60 * 1000, initialDelay = 1 * 60 * 1000)
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
        synchronized (oneMinDatas) {
            oneMinDatas.add(summary);
        }

        // リグの電源状態取得
        var pcPowerOn = miningRigDevice.isStarted();

        // 電源制御
        try {
            var powerConfig = controlProperties.getPower();
            var powerOnCondition = powerConfig.getPowerOnCondition();
            var powerOffCondition = powerConfig.getPowerOffCondition();

            if (!pcPowerOn && powerOnCondition.graterEqual(summary.getPvPower(), summary.getBattSOC(),
                    summary.getBattVolt(), summary.getStage())) {
                // 設定条件以上のとき、リグの電源ON
                log.info("リグを起動します。");

                // DCDCコンバーターにいきなり接続すると、
                // 突入電流でチャージコントローラーの保護回路が働いてしまうので、
                // 5Ω抵抗経由で接続したあと、ダイレクトに接続する
                pvControllerDevice.loadRegisterOn();
                Thread.sleep(300);
                pvControllerDevice.changeLoadSwith(conn, true);
                Thread.sleep(1000);
                pvControllerDevice.loadRegisterOff();

                Thread.sleep(4000);

                miningRigDevice.start();
                pcStartTime = LocalDateTime.now();

                if (fanStopFuture != null && !fanStopFuture.isDone()) {
                    fanStopFuture.cancel(true);
                }
                Thread.sleep(100);
                coolingFanDevice.start();

            } else if (shutdownRequest || (pcPowerOn && powerOffCondition.lessEqual(summary.getPvPower(),
                    summary.getBattSOC(), summary.getBattVolt(), summary.getStage()))) {
                // 設定条件以下のとき、リグの電源OFF
                log.info("リグを停止します。");

                miningRigDevice.stop();

                Thread.sleep(20000);

                pvControllerDevice.changeLoadSwith(conn, false);

                // 指定時間待ってから冷却ファンを止める
                if (taskExecutor.getActiveCount() == 0) {
                    fanStopFuture = taskExecutor.submit(() -> {
                        try {
                            Thread.sleep(controlProperties.getFan().getPowerOffDuration() * 60 * 1000);
                        } catch (InterruptedException ignore) {
                            // NOP
                        }
                        coolingFanDevice.stop();
                    });
                }

                shutdownRequest = false;
            }

        } catch (Exception e) {
            log.error("電源制御に失敗しました。", e);
        }
    }

    /**
     * 3分毎にAmbientにデータ送信
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void sendAmbient() throws Exception {
        if (oneMinDatas.isEmpty()) {
            return;
        }

        // 集計
        RealtimeData summary;
        synchronized (oneMinDatas) {
            summary = RealtimeData.summary(oneMinDatas);
            oneMinDatas.clear();
        }
        synchronized (fifteenMinDatas) {
            fifteenMinDatas.add(summary);
        }

        // バッテリー温度取得
        Double battTemp;
        try {
            battTemp = lm75aDevice.readCurrent();
        } catch (Exception e) {
            log.warn("バッテリー温度の取得に失敗しました。", e);
            battTemp = null;
        }

        // Ambient送信
        var ambientConfig = serviceProperties.getAmbient();
        if (ambientConfig != null) {
            // Nicehash OSのPower Modeか、Hive OSのOCプロファイルの数値を取得(9=HIGH,11=MEDIUM,12=LOW)
            Double powerModeOrLimitValue = currentRigStatus == null ? null
                    : currentRigStatus.getRigPowerMode().getStatusValue();
            if (powerModeOrLimitValue == null && currentOcProfile != null) {
                powerModeOrLimitValue = currentOcProfile.getName()
                        .equals(controlProperties.getPower().getHighProfileName()) ? 9D : 12D;
            }

            try {
                var sendDatas = new Double[] { summary.getPvPower(), summary.getBattVolt(), summary.getLoadPower(),
                        powerModeOrLimitValue, battTemp };
                log.debug("Ambientに3分値を送信します。pcPower={},battVolt={},loadPower={},rigPM/PL={},battTemp={}",
                        sendDatas[0], sendDatas[1], sendDatas[2], sendDatas[3], sendDatas[4]);

                ambientService.send(ambientConfig, ZonedDateTime.now(), null, sendDatas);
            } catch (Exception e) {
                log.error("Ambientへのデータ送信に失敗しました。", e);
            }
        }
    }

    /**
     * 5分毎にバッテリー温度制御
     * 
     * @throws IOException
     * @throws UnsupportedBusNumberException
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void batteryTempControl() throws UnsupportedBusNumberException, IOException {
        var heaterConfig = controlProperties.getBatteryHeater();
        var hourRange = heaterConfig.getHourRange();
        var tempRange = heaterConfig.getTemperatureRange();

        // 時間帯チェック
        if (hourRange != null) {
            var range = Arrays.stream(hourRange).map(LocalTime::parse).toArray(LocalTime[]::new);
            var now = LocalTime.now();
            if (now.compareTo(range[0]) < 0 || now.compareTo(range[1]) > 0) {
                if (batteryHeaterDevice.isStarted()) {
                    batteryHeaterDevice.stop();
                }
                return;
            }
        }

        // バッテリー温度取得、ヒーター制御
        double battTemp;
        try {
            battTemp = lm75aDevice.readCurrent();
        } catch (Exception e) {
            log.warn("バッテリー温度の取得に失敗しました。", e);
            return;
        }

        var battHeaterStarted = batteryHeaterDevice.isStarted();
        if (battTemp < tempRange[0] && !battHeaterStarted) {
            batteryHeaterDevice.start();
        } else if (battTemp > tempRange[1] && battHeaterStarted) {
            batteryHeaterDevice.stop();
        }
    }

    /**
     * 15分毎にPowerMode/PowerLimit制御
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

        // PC起動後15分間は制御しない
        if (pcStartTime == null || ChronoUnit.MINUTES.between(pcStartTime, LocalDateTime.now()) < 15) {
            return;
        }

        var pcPowerOn = miningRigDevice.isStarted();
        var histeresis = controlProperties.getPower().getHysteresis();

        // Power Mode制御
        var nicehashConfig = serviceProperties.getNicehashApi();
        if (nicehashConfig != null) {
            var currentPowerMode = currentRigStatus == null ? null : currentRigStatus.getRigPowerMode();
            RigStatus newRigStatus = null;
            if (pcPowerOn && (summary.getPvPower() - summary.getLoadPower()) > histeresis) {
                newRigStatus = nicehashService.turnUpPowerMode(nicehashConfig);
            } else if (pcPowerOn && (summary.getLoadPower() - summary.getPvPower()) > histeresis) {
                newRigStatus = nicehashService.turnDownPowerMode(nicehashConfig);
            }
            if (newRigStatus != null && newRigStatus.getRigPowerMode() != currentPowerMode) {
                log.info("リグのPowerModeを{}に変更しました。", newRigStatus.getRigPowerMode());
            }
        }

        // Power Limit制御
        var hiveConfig = serviceProperties.getHiveApi();
        if (hiveConfig != null) {
            var currentOcProfileId = currentOcProfile == null ? null : currentOcProfile.getId();
            OcProfile newOcProfile = null;
            if (pcPowerOn && (summary.getPvPower() - summary.getLoadPower()) > histeresis) {
                newOcProfile = hiveService.turnUpPowerLimit(hiveConfig, controlProperties.getPower());
            } else if (pcPowerOn && (summary.getLoadPower() - summary.getPvPower()) > histeresis) {
                newOcProfile = hiveService.turnDownPowerLimit(hiveConfig, controlProperties.getPower());
            }
            if (newOcProfile != null && !newOcProfile.getId().equals(currentOcProfileId)) {
                log.info("ワーカーのOCプロファイルを{}に変更しました。", newOcProfile.getName());
            }
        }

        // 起動失敗時にシャットダウン
        // TODO しきい値を設定化
        if (pcPowerOn && summary.getLoadPower() < 100D) {
            shutdownRequest = true;
        }
    }

    /**
     * 15分毎にファンを回す
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void fanControl() {
        // PCが電源OFFかつ、クーリング中でなければファンを回す
        var pcPowerOn = miningRigDevice.isStarted();
        if (!pcPowerOn && taskExecutor.getActiveCount() == 0) {
            try {
                coolingFanDevice.start();
                Thread.sleep(controlProperties.getFan().getDuration() * 1000);
                coolingFanDevice.stop();
            } catch (Exception e) {
                log.error("ファン制御に失敗しました。", e);
            }
        }
    }

    /**
     * 終了処理
     */
    @PreDestroy
    public void destroy() {
        if (conn != null) {
            log.debug("PVコントローラーを切断します。");
            conn.close();
        }

        raspberryPiDevice.shutdown();

        initialized = false;
    }
}
