package com.heterodain.mining.powercontroller.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.heterodain.mining.powercontroller.config.ServiceConfig.NicehashApi;

import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Nicehashサービス
 */
@Service
@Slf4j
public class NicehashService {
    private static final String HIVE_API_BASE_URL = "https://api2.nicehash.com/api/v2";
    private static final String GET_SERVER_TIME_URL = HIVE_API_BASE_URL + "/time";
    private static final String GET_RIG_STATUS_URL = HIVE_API_BASE_URL + "/mining/rigs2";
    private static final String UPDATE_RIG_STATUS_URL = HIVE_API_BASE_URL + "/mining/rigs2";

    /** HTTP読み込みタイムアウト(秒) */
    private static final int READ_TIMEOUT = 30;

    /** Httpクライアント */
    @Autowired
    private HttpClient httpClient;

    /** JSONパーサー */
    @Autowired
    private ObjectMapper om;

    /**
     * Nicehashサーバーの時刻取得
     * 
     * @return 時刻
     * @throws IOException
     * @throws InterruptedException
     */
    public String getServerTime() throws IOException, InterruptedException {
        var uri = URI.create(GET_SERVER_TIME_URL);

        log.trace("request > [GET] {}", uri);

        var request = HttpRequest.newBuilder(uri).GET().header("Accept", "application/json")
                .timeout(Duration.ofSeconds(READ_TIMEOUT)).build();
        var response = httpClient.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Nicehash API Response Code " + response.statusCode());
        }

        try (var is = response.body()) {
            var json = om.readTree(is);
            log.trace("response > {}", json);

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
    public RigStatus getRigStatus(NicehashApi config, String time) throws Exception {
        var uri = URI.create(GET_RIG_STATUS_URL);
        var headers = createAuthHeader(config, time, "GET", uri, null);

        log.trace("request > [GET] {}", uri);

        var requestBuilder = HttpRequest.newBuilder(uri).GET().header("Accept", "application/json")
                .timeout(Duration.ofSeconds(READ_TIMEOUT));
        headers.entrySet().forEach(e -> requestBuilder.header(e.getKey(), e.getValue()));
        var request = requestBuilder.build();

        var response = httpClient.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Nicehash API Response Code " + response.statusCode());
        }

        try (var is = response.body()) {
            var json = om.readTree(is);
            log.trace("response > {}", json);

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
    public boolean setRigPowerMode(NicehashApi config, String time, POWER_MODE mode) throws Exception {
        var uri = URI.create(UPDATE_RIG_STATUS_URL);
        var payload = "{\"rigId\":\"" + config.getRigId() + "\",\"action\":\"POWER_MODE\",\"options\":[\"" + mode
                + "\"]}";
        var headers = createAuthHeader(config, time, "POST", uri, payload);

        log.trace("request > [POST] {}", uri);
        log.trace("payload > {}", payload);

        var requestBuilder = HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(payload))
                .header("Accept", "application/json")
                .header("Content-type", "application/json")
                .timeout(Duration.ofSeconds(READ_TIMEOUT));
        headers.entrySet().forEach(e -> requestBuilder.header(e.getKey(), e.getValue()));
        var request = requestBuilder.build();

        var response = httpClient.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Nicehash API Response Code " + response.statusCode());
        }

        try (var is = response.body()) {
            var json = om.readTree(is);
            log.trace("response > {}", json);

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
    private Map<String, String> createAuthHeader(NicehashApi config, String time, String method, URI uri,
            String payload)
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
        if (payload != null) {
            mac.update((byte) 0);
            mac.update(payload.getBytes(StandardCharsets.UTF_8));
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
