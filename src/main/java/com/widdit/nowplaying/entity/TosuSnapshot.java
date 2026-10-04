package com.widdit.nowplaying.entity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Immutable atomic source snapshot; no song-search result is needed. */
public final class TosuSnapshot {
    public final String identity, title, artist, status, coverPath;
    public final long positionMs, durationMs;
    private TosuSnapshot(JSONObject j) {
        identity = j.getString("identity"); title = j.getString("title"); artist = j.getString("artist");
        status = j.getString("status"); coverPath = j.getString("coverPath");
        durationMs = Math.max(0, j.getLongValue("durationMs"));
        positionMs = Math.max(0, j.getLongValue("positionMs"));
    }
    public static TosuSnapshot parse(String json) {
        JSONObject j = JSON.parseObject(json);
        if (j == null || !("Playing".equals(j.getString("status")) || "Paused".equals(j.getString("status")))) return null;
        if (j.getString("identity") == null || j.getString("identity").isBlank()
                || j.getString("title") == null || j.getString("title").isBlank()) return null;
        return new TosuSnapshot(j);
    }
    public String windowTitle() { return title + " - " + (artist == null ? "" : artist); }
    public String coverUrl(int port) {
        if (coverPath == null || coverPath.isBlank()) return "";
        String path = coverPath.replace('\\', '/');
        String[] parts = path.split("/", -1);
        if (path.startsWith("/") || path.contains(":") || Arrays.stream(parts).anyMatch(p -> p.isEmpty() || p.equals("..") || p.equals("."))) return "";
        return "http://127.0.0.1:" + port + "/files/beatmap/" + Arrays.stream(parts)
                .map(p -> URLEncoder.encode(p, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }
}
