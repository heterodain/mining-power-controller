package com.heterodain.mining.powercontroller.device;

import java.util.List;

import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadCoilsResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteCoilRequest;
import com.ghgande.j2mod.modbus.msg.WriteCoilResponse;
import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.pi4j.io.gpio.GpioPinDigitalOutput;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * PVコントローラーデバイス
 */
@AllArgsConstructor
@Slf4j
public class PvControllerDevice {
    /** RS485ユニットID */
    private int unitId;
    /** 負荷出力抵抗制御用GPIO */
    private GpioPinDigitalOutput loadPowerRegisterSw;

    /**
     * 負荷出力抵抗ON
     */
    public void loadRegisterOn() {
        log.info("負荷出力抵抗をONします。");

        loadPowerRegisterSw.high();
    }

    /**
     * 負荷出力抵抗OFF
     */
    public void loadRegisterOff() {
        log.info("負荷出力抵抗をOFFします。");

        loadPowerRegisterSw.low();
    }

    /**
     * リアルタイム情報取得
     * 
     * @param conn シリアル接続
     * @return リアルタイム情報
     * @throws ModbusException
     */
    public synchronized RealtimeData readCurrent(SerialConnection conn) throws ModbusException {
        // 電力と電圧の瞬時値取得
        var req = new ReadInputRegistersRequest(0x3100, 16);
        req.setUnitID(unitId);
        var tr = new ModbusSerialTransaction(conn);
        tr.setRequest(req);
        tr.execute();

        var res = (ReadInputRegistersResponse) tr.getResponse();

        var data = new RealtimeData();
        data.pvPower = ((double) res.getRegisterValue(2) + res.getRegisterValue(3) * 0x10000) / 100;
        data.loadPower = ((double) res.getRegisterValue(14) + res.getRegisterValue(15) * 0x10000) / 100;
        data.battVolt = ((double) res.getRegisterValue(4)) / 100;

        // バッテリー残量取得
        req = new ReadInputRegistersRequest(0x311A, 1);
        req.setUnitID(unitId);
        tr = new ModbusSerialTransaction(conn);
        tr.setRequest(req);
        tr.execute();

        res = (ReadInputRegistersResponse) tr.getResponse();
        data.battSOC = ((double) res.getRegisterValue(0));

        // 充電ステージ取得
        req = new ReadInputRegistersRequest(0x3201, 1);
        req.setUnitID(unitId);
        tr = new ModbusSerialTransaction(conn);
        tr.setRequest(req);
        tr.execute();

        res = (ReadInputRegistersResponse) tr.getResponse();
        data.stage = STAGE.values()[(res.getRegisterValue(0) >> 2) & 0x0003];

        log.trace("{}", data);

        return data;
    }

    /**
     * 負荷出力スイッチ状態取得
     * 
     * @param conn シリアル接続
     * @return true:スイッチON,false:スイッチOFF
     * @throws ModbusException
     */
    public synchronized boolean readLoadSwitch(SerialConnection conn) throws ModbusException {
        var req = new ReadCoilsRequest(2, 1);
        req.setUnitID(unitId);
        ModbusSerialTransaction tr = new ModbusSerialTransaction(conn);
        tr.setRequest(req);
        tr.execute();

        var res = (ReadCoilsResponse) tr.getResponse();
        log.trace("Coil={}", res.getCoilStatus(0));

        return res.getCoilStatus(0);
    }

    /**
     * 負荷出力スイッチON/OFF
     * 
     * @param conn シリアル接続
     * @param sw   ture:スイッチON,false:スイッチOFF
     * @throws ModbusException
     */
    public synchronized void changeLoadSwith(SerialConnection conn, boolean sw)
            throws ModbusException {
        log.info("負荷出力を{}します。", sw ? "ON" : "OFF");

        var req = new WriteCoilRequest(2, sw);
        req.setUnitID(unitId);
        // req.setDataLength(1);
        ModbusSerialTransaction tr = new ModbusSerialTransaction(conn);
        tr.setRequest(req);
        tr.execute();

        var res = (WriteCoilResponse) tr.getResponse();
        log.trace("Coil={}", res.getCoil());
    }

    /**
     * データ
     */
    @Getter
    @Setter
    @ToString
    public static class RealtimeData {
        /** 発電電力(W) */
        private Double pvPower;
        /** バッテリー電圧(V) */
        private Double battVolt;
        /** 負荷電力(W) */
        private Double loadPower;
        /** バッテリー残量(%) */
        private Double battSOC;
        /** 充電ステージ */
        private STAGE stage;

        /**
         * データの平均値取得
         * 
         * @param datas データ
         * @return 平均値
         */
        public static RealtimeData summary(List<RealtimeData> datas) {
            var summary = new RealtimeData();
            summary.setPvPower(datas.stream().mapToDouble(RealtimeData::getPvPower).average().orElse(0D));
            summary.setBattVolt(datas.stream().mapToDouble(RealtimeData::getBattVolt).average().orElse(0D));
            summary.setLoadPower(datas.stream().mapToDouble(RealtimeData::getLoadPower).average().orElse(0D));
            summary.setBattSOC(datas.stream().mapToDouble(RealtimeData::getBattSOC).average().orElse(0D));
            summary.setStage(datas.stream().map(d -> d.getStage()).reduce((a, b) -> b).orElse(null));

            return summary;
        }
    }

    /**
     * 充電ステージ
     */
    @AllArgsConstructor
    @Getter
    public static enum STAGE {
        NO_CHARGING(0), FLOAT(3), BOOST(1), EQULIZATION(2);

        private int index;
    }
}