package com.heterodain.mining.powercontroller.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heterodain.mining.powercontroller.config.ServiceConfig.Ambient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Ambientサービス
 */
@Service
@Slf4j
public class AmbientService {
    /** UTCタイムゾーン */
    private static final ZoneId UTC = ZoneId.of("UTC");
    /** HTTP読み込みタイムアウト(秒) */
    private static final int READ_TIMEOUT = 30;

    /** Httpクライアント */
    @Autowired
    private HttpClient httpClient;

    /** JSONパーサー */
    @Autowired
    private ObjectMapper om;

    /** チャネルごとの前回送信時刻 */
    private Map<Integer, Long> lastSendTimes = new ConcurrentHashMap<>();

    /**
     * チャネルにデータ送信
     * 
     * @param config  API接続設定
     * @param ts      タイムスタンプ
     * @param comment コメント
     * @param datas   送信データ(最大8個)
     * @throws IOException
     * @throws InterruptedException
     */
    public void send(Ambient config, ZonedDateTime ts, String comment, Double... datas)
            throws IOException, InterruptedException {

        synchronized (config) {
            // チャネルへの送信間隔が6秒以上になるように調整 (同一チャネルへの送信は5秒以上間隔を空ける必要がある)
            var lastSendTime = lastSendTimes.get(config.getChannelId());
            if (lastSendTime != null) {
                var diff = System.currentTimeMillis() - lastSendTime;
                if (diff < 6000) {
                    Thread.sleep(6000 - diff);
                }
            }

            // 送信するJSONを構築
            var rootNode = om.createObjectNode();
            rootNode.put("writeKey", config.getWriteKey());

            var dataArrayNode = om.createArrayNode();
            var dataNode = om.createObjectNode();
            var utcTs = ts.withZoneSameInstant(UTC).toLocalDateTime();
            dataNode.put("created", utcTs.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            for (int i = 1; i <= datas.length; i++) {
                if (datas[i - 1] != null) {
                    dataNode.put("d" + i, datas[i - 1]);
                }
            }
            if (comment != null) {
                dataNode.put("cmnt", comment);
            }
            dataArrayNode.add(dataNode);
            rootNode.set("data", dataArrayNode);

            var payload = om.writeValueAsString(rootNode);

            // HTTP POST
            var uri = URI.create("http://ambidata.io/api/v2/channels/" + config.getChannelId() + "/dataarray");
            log.trace("request > [POST] {}", uri);
            log.trace("payload > {}", payload);

            var request = HttpRequest.newBuilder(uri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json").timeout(Duration.ofSeconds(READ_TIMEOUT)).build();
            var response = httpClient.send(request, BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                throw new IOException("Ambient Response Code " + response.statusCode());
            }

            lastSendTimes.put(config.getChannelId(), System.currentTimeMillis());
        }
    }

    /**
     * 1日分のデータ取得
     * 
     * @param config API接続設定
     * @param date   日付
     * @return 1日分のデータ
     * @throws IOException
     * @throws InterruptedException
     */
    public List<ReadData> read(Ambient config, LocalDate date) throws IOException, InterruptedException {

        // HTTP GET
        var uri = URI.create(
                "http://ambidata.io/api/v2/channels/" + config.getChannelId() + "/data?readKey=" + config.getReadKey()
                        + "&date=" + date.format(DateTimeFormatter.ISO_DATE));
        log.trace("request > [GET] {}", uri);

        var request = HttpRequest.newBuilder(uri).GET().version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(READ_TIMEOUT))
                .build();
        var response = httpClient.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Ambient Response Code " + response.statusCode());
        }

        try (var is = response.body()) {
            return om.readValue(is, new TypeReference<List<ReadData>>() {
            });
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReadData {
        private String created;
        private Double d1;
        private Double d2;
        private Double d3;
        private Double d4;
        private Double d5;
        private Double d6;
        private Double d7;
        private Double d8;
        private String cmnt;
    }
}