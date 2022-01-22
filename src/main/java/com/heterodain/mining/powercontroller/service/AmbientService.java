package com.heterodain.mining.powercontroller.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heterodain.mining.powercontroller.config.ServiceConfig.Ambient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * Ambientサービス
 */
@Service
@Slf4j
public class AmbientService {
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Autowired
    private ObjectMapper om;

    /**
     * チャネルにデータ送信
     * 
     * @param info  チャネル情報
     * @param ts    タイムスタンプ
     * @param datas 送信データ(最大8個)
     * @throws IOException
     * @throws InterruptedException
     */
    public synchronized void send(Ambient info, ZonedDateTime ts, Double... datas)
            throws IOException, InterruptedException {

        // 送信するJSONを構築
        var rootNode = om.createObjectNode();
        rootNode.put("writeKey", info.getWriteKey());

        var dataArrayNode = om.createArrayNode();
        var dataNode = om.createObjectNode();
        var utcTs = ts.withZoneSameInstant(UTC).toLocalDateTime();
        dataNode.put("created", utcTs.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        for (int i = 1; i <= datas.length; i++) {
            if (datas[i - 1] != null) {
                dataNode.put("d" + i, datas[i - 1]);
            }
        }
        dataArrayNode.add(dataNode);
        rootNode.set("data", dataArrayNode);

        var jsonString = om.writeValueAsString(rootNode);

        // HTTP POST
        var url = "http://54.65.206.59/api/v2/channels/" + info.getChannelId() + "/dataarray";
        log.trace("request > " + url);
        log.trace("body > " + jsonString);

        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        try (var os = conn.getOutputStream()) {
            os.write(jsonString.getBytes(StandardCharsets.UTF_8));
        }
        var resCode = conn.getResponseCode();
        if (resCode != 200) {
            throw new IOException("Ambient Response Code " + resCode);
        }
    }
}