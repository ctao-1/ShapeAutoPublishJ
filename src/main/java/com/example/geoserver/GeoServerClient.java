// filepath: d:\PythonAutoPublish\GeoServerPublisherJava\src\main\java\com\example\geoserver\GeoServerClient.java
//仅封装 GeoServer REST 调用
package com.example.geoserver;

import com.example.geoserver.model.BoundingBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public class GeoServerClient {
    private final String baseUrl; // e.g., http://localhost:8080/geoserver
    private final String user;
    private final String pass;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeoServerClient(String baseUrl, String user, String pass) {
        this.baseUrl = baseUrl.replaceAll("/$", "");// 移除 URL 末尾的斜杠（统一格式）
        this.user = user;
        this.pass = pass;
        this.http = HttpClient.newHttpClient();
    }

    private String basicAuth() {
        // 对 "用户名:密码" 进行 Base64 编码，生成 Basic 认证令牌
        String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private HttpRequest.Builder reqBuilder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", basicAuth())// 添加认证头
                .header("Accept", "application/json");// 声明接受 JSON 响应
    }

    public boolean workspaceExists(String ws) throws Exception {
        // 构造查询 URL
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + ".json";
        // 发送 GET 请求，忽略响应体（只需状态码）
        var req = reqBuilder(url).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.discarding());
        return res.statusCode() == 200;
    }

    public boolean createWorkspace(String ws) throws Exception {
        String url = baseUrl + "/rest/workspaces";
        String body = "{\"workspace\":{\"name\":\"" + escape(ws) + "\"}}";
        var req = reqBuilder(url).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        // 201（创建成功）或 200（已存在）均返回 true
        return res.statusCode() == 201 || res.statusCode() == 200;
    }

    public boolean datastoreExists(String ws, String store) throws Exception {
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores/" + enc(store) + ".json";
        var req = reqBuilder(url).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.discarding());
        return res.statusCode() == 200;
    }

    public boolean createDirectoryDatastore(String ws, String store, String folder, String charset) throws Exception {
        // Normalize to forward slashes and ensure trailing slash; file: scheme (no double slashes)
        String normalized = folder.replace("\\", "/");
        if (!normalized.endsWith("/")) normalized += "/";
        String urlParam = "file:" + normalized;

        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores";
        //%s字符串占位符
        String body = """   
        {
          "dataStore": {
            "name": "%s",   
            "connectionParameters": {
              "url": "%s",
              "charset": "%s",
              "create spatial index": "true",
              "cache and reuse memory maps": "true"
            }
          }
        }
        """.formatted(escape(store), escape(urlParam), escape(charset));

        var req = reqBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 201 || res.statusCode() == 200;
    }

    public boolean featureTypeExists(String ws, String store, String layer) throws Exception {
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores/" + enc(store)
                + "/featuretypes/" + enc(layer) + ".json";
        var req = reqBuilder(url).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.discarding());
        return res.statusCode() == 200;
    }

    public boolean layerExists(String ws, String layer) throws Exception {
        // Keep colon unescaped between ws:layer; encode parts otherwise
        String qualified = enc(ws) + ":" + enc(layer);
        String url = baseUrl + "/rest/layers/" + qualified + ".json";
        var req = reqBuilder(url).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.discarding());
        return res.statusCode() == 200;
    }

    public boolean publishFeatureType(String ws, String store, String layer, String title, String srsOrNull) throws Exception {
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores/" + enc(store) + "/featuretypes";
        // Let GeoServer infer CRS from .prj (safer). If you must force, add "srs": "EPSG:xxxx".
        String srsPart = (srsOrNull == null || srsOrNull.isBlank()) ? "" : "\"srs\":\"" + escape(srsOrNull) + "\",";
        String body = """
        {
          "featureType": {
            "name": "%s",
            %s
            "title": "%s",
            "enabled": true
          }
        }
        """.formatted(escape(layer), srsPart, escape(title));
        var req = reqBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    // GeoServer may return 201 (created) or 200 (already exists / synchronous response).
    return res.statusCode() == 201 || res.statusCode() == 200;
    }

    public boolean setDefaultStyle(String ws, String layer, String styleName) throws Exception {
        String qualified = enc(ws) + ":" + enc(layer);
        String url = baseUrl + "/rest/layers/" + qualified;
        String body = """
        {"layer":{"defaultStyle":{"name":"%s","workspace":"%s"}}}
        """.formatted(escape(styleName), escape(ws));
        var req = reqBuilder(url)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 || res.statusCode() == 201;
    }

    public boolean recalcFeatureTypeBBox(String ws, String store, String layer, String which) throws Exception {
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores/" + enc(store)
                + "/featuretypes/" + enc(layer) + ".json?recalculate=" + urlEnc(which);
        String body = "{\"featureType\":{}}";
        var req = reqBuilder(url)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 || res.statusCode() == 201;
    }

    public Optional<BoundingBox> getFeatureTypeBBox(String ws, String store, String layer) throws Exception {
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores/" + enc(store)
                + "/featuretypes/" + enc(layer) + ".json";
        var req = reqBuilder(url).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return Optional.empty();

        JsonNode root = mapper.readTree(res.body()).path("featureType");
        JsonNode bbox = root.path("nativeBoundingBox");
        if (missing(bbox)) bbox = root.path("latLonBoundingBox");
        if (missing(bbox)) return Optional.empty();

        if (bbox.has("minx") && bbox.has("miny") && bbox.has("maxx") && bbox.has("maxy")) {
            return Optional.of(new BoundingBox(
                    bbox.path("minx").asDouble(),
                    bbox.path("miny").asDouble(),
                    bbox.path("maxx").asDouble(),
                    bbox.path("maxy").asDouble()
            ));
        }
        return Optional.empty();
    }

    /**
     * Return the declared SRS for a featureType (e.g. "EPSG:4326"), or null if not present.
     */
    public String getFeatureTypeSRS(String ws, String store, String layer) throws Exception {
        String url = baseUrl + "/rest/workspaces/" + enc(ws) + "/datastores/" + enc(store)
                + "/featuretypes/" + enc(layer) + ".json";
        var req = reqBuilder(url).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JsonNode root = mapper.readTree(res.body()).path("featureType");
        // common property names: "srs", "nativeCRS", "nativeSRS"
        if (root.has("srs") && !root.path("srs").isNull()) return root.path("srs").asText();
        if (root.has("nativeCRS") && !root.path("nativeCRS").isNull()) return root.path("nativeCRS").asText();
        if (root.has("nativeSRS") && !root.path("nativeSRS").isNull()) return root.path("nativeSRS").asText();
        return null;
    }

    public String buildOpenLayersPreview(String ws, String layer, BoundingBox bbox3857OrNative) {
        // Use EPSG:3857 in preview; if your data is not 3857, adjust accordingly
        String params = "service=WMS&version=1.1.0&request=GetMap"
                + "&layers=" + urlEnc(ws + ":" + layer)
                + "&bbox=" + urlEnc(bbox3857OrNative.toString())
                + "&width=768&height=494"
                + "&srs=" + urlEnc("EPSG:3857")
                + "&styles=&format=" + urlEnc("application/openlayers");
        return baseUrl + "/" + enc(ws) + "/wms?" + params;
    }

    /**
     * Build an OpenLayers preview URL but try to read the layer's declared SRS from GeoServer.
     * If the featureType declares an SRS, that will be used; otherwise defaults to EPSG:3857.
     * Note: this does not reproject the bbox; ensure the provided bbox is in the chosen SRS.
     */
    public String buildOpenLayersPreview(String ws, String store, String layer, BoundingBox bbox) {
        String srs = "EPSG:3857";
        try {
            String declared = getFeatureTypeSRS(ws, store, layer);
            if (declared != null && !declared.isBlank()) srs = declared;
        } catch (Exception e) {
            // ignore and fall back to default
        }
        String params = "service=WMS&version=1.1.0&request=GetMap"
                + "&layers=" + urlEnc(ws + ":" + layer)
                + "&bbox=" + urlEnc(bbox.toString())
                + "&width=768&height=494"
                + "&srs=" + urlEnc(srs)
                + "&styles=&format=" + urlEnc("application/openlayers");
        return baseUrl + "/" + enc(ws) + "/wms?" + params;
    }
    private static boolean missing(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        // Minimal JSON string escape for quotes/backslashes
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}