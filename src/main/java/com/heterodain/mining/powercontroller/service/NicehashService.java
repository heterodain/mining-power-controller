package com.heterodain.mining.powercontroller.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.heterodain.mining.powercontroller.config.ServiceConfig.Nicehash;

import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * Nicehashサービス
 */
@Service
@Slf4j
public class NicehashService {

    @Autowired
    private ObjectMapper om;

    /**
     * Nicehashサーバーの時刻取得
     * 
     * @return 時刻
     * @throws IOException
     */
    public String getServerTime() throws IOException {
        var url = new URL("https://api2.nicehash.com/api/v2/time");
        var httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setRequestMethod("GET");
        httpConn.addRequestProperty("Accept", "application/json");
        try (var is = httpConn.getInputStream()) {
            var json = om.readTree(is);
            return json.get("serverTime").asText();
        }
    }

    /**
     * リグ情報取得
     * 
     * @param config API接続設定
     * @param time   時刻
     * @return リグ情報
     * @throws Exception
     */
    public RigStatus getRigStatus(Nicehash config, String time) throws Exception {
        var uri = new URI("https://api2.nicehash.com/main/api/v2/mining/rigs2");
        var method = "GET";
        var headers = createAuthHeader(config, time, method, uri, null);

        var httpConn = (HttpURLConnection) uri.toURL().openConnection();
        httpConn.setRequestMethod(method);
        httpConn.addRequestProperty("Accept", "application/json");
        headers.entrySet().forEach(e -> httpConn.addRequestProperty(e.getKey(), e.getValue()));

        try (var is = httpConn.getInputStream()) {
            var json = om.readTree(is);
            var rigsJson = (ArrayNode) json.get("miningRigs");
            for (var rigJson : rigsJson) {
                var rigStatus = om.treeToValue(rigJson, RigStatus.class);
                if (config.getRigId().equals(rigStatus.getRigId())) {
                    return rigStatus;
                }
            }
            return null;
        }
    }

    /**
     * リグのTDP設定
     * 
     * @param config API接続設定
     * @param time   時刻
     * @param mode   パワーモード(TDP)
     * @return 設定の変更が成功した場合にtrue
     * @throws Exception
     */
    public boolean setRigPowerMode(Nicehash config, String time, POWER_MODE mode) throws Exception {
        var uri = new URI("https://api2.nicehash.com/main/api/v2/mining/rigs/status2");
        var method = "POST";
        var body = "{\"rigId\":\"" + config.getRigId() + "\",\"action\":\"POWER_MODE\",\"options\":[\"" + mode + "\"]}";
        var headers = createAuthHeader(config, time, method, uri, body);

        var httpConn = (HttpURLConnection) uri.toURL().openConnection();
        httpConn.setRequestMethod(method);
        httpConn.setDoOutput(true);
        httpConn.addRequestProperty("Accept", "application/json");
        httpConn.addRequestProperty("Content-type", "application/json");
        headers.entrySet().forEach(e -> httpConn.addRequestProperty(e.getKey(), e.getValue()));
        try (var os = httpConn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        try (var is = httpConn.getInputStream()) {
            var json = om.readTree(is);
            if (json.get("success").asBoolean()) {
                return true;
            }
            log.warn(json.get("message").asText());
            return false;
        }
    }

    /**
     * 認証用リクエストヘッダ構築
     */
    private Map<String, String> createAuthHeader(Nicehash config, String time, String method, URI uri, String body)
            throws Exception {
        var nonce = UUID.randomUUID().toString();

        var mac = Mac.getInstance("HmacSHA256");
        var secret_key = new SecretKeySpec(config.getApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secret_key);
        mac.update(config.getApiKey().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        mac.update(time.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        mac.update(nonce.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        mac.update((byte) 0);
        mac.update(config.getOrgId().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        mac.update((byte) 0);
        mac.update(method.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        mac.update(uri.getPath().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        if (uri.getQuery() != null) {
            mac.update(uri.getQuery().getBytes(StandardCharsets.UTF_8));
        }
        if (body != null) {
            mac.update((byte) 0);
            mac.update(body.getBytes(StandardCharsets.UTF_8));
        }

        var digest = Hex.encodeHexString(mac.doFinal());

        var headers = new HashMap<String, String>();
        headers.put("X-Time", time);
        headers.put("X-Nonce", nonce);
        headers.put("X-Auth", config.getApiKey() + ":" + digest);
        headers.put("X-Organization-Id", config.getOrgId());
        return headers;
    }

    /**
     * パワーモード
     */
    @AllArgsConstructor
    @Getter
    public static enum POWER_MODE {
        UNKNOWN(5), MIXED(15), HIGH(9), MEDIUM(11), LOW(12);

        double statusValue;
    }

    /**
     * リグのステータス
     */
    public static enum MINER_STATUS {
        BENCHMARKING, MINING, STOPPED, OFFLINE, ERROR, PENDING, DISABLED, TRANSFERRED, UNKNOWN;
    }

    /**
     * GPUのステータス
     */
    public static enum DEVICE_STATUS {
        UNKNOWN, DISABLED, INACTIVE, MINING, BENCHMARKING, ERROR, PENDING, OFFLINE;
    }

    /**
     * リグの情報
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class RigStatus {
        private String rigId;
        private String name;
        private MINER_STATUS minerStatus;
        private List<Device> devices;
        private POWER_MODE rigPowerMode;
    }

    /**
     * GPUの情報
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Device {
        private String id;
        private String name;
        private Status status;
        private PowerMode powerMode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Status {
        private DEVICE_STATUS enumName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class PowerMode {
        private POWER_MODE enumName;
    }
}
