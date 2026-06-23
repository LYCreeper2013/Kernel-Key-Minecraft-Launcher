package cn.lycreeper.util;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class VersionDownloader {
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String gameDirectory;
    private boolean isDownloading = false;
    private Consumer<String> logCallback;
    private Consumer<Integer> progressCallback;

    public VersionDownloader(String gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    public void setProgressCallback(Consumer<Integer> callback) {
        this.progressCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
        System.out.println("[Downloader] " + message);
    }

    private void setProgress(int percent) {
        if (progressCallback != null) {
            progressCallback.accept(percent);
        }
    }

    /**
     * 下载 Minecraft 版本
     * @param versionId 版本号，如 "1.20.4"
     * @param loaderType 加载器类型 ("vanilla", "fabric", "forge")
     */
    public void downloadVersion(String versionId, String loaderType) {
        if (isDownloading) {
            log("下载已在进行中");
            return;
        }

        isDownloading = true;
        new Thread(() -> {
            try {
                if ("vanilla".equals(loaderType)) {
                    downloadVanilla(versionId);
                } else if ("fabric".equals(loaderType)) {
                    downloadFabric(versionId);
                } else if ("forge".equals(loaderType)) {
                    downloadForge(versionId);
                } else {
                    downloadVanilla(versionId);
                }
            } catch (Exception e) {
                log("下载失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isDownloading = false;
            }
        }).start();
    }

    private void downloadVanilla(String versionId) throws Exception {
        log("开始下载 Minecraft " + versionId);
        setProgress(0);

        // 1. 获取版本清单
        log("获取版本清单...");
        String manifestJson = fetch(VERSION_MANIFEST_URL);
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        JsonArray versions = manifest.getAsJsonArray("versions");

        // 2. 查找目标版本
        String versionUrl = null;
        for (JsonElement elem : versions) {
            JsonObject v = elem.getAsJsonObject();
            if (v.get("id").getAsString().equals(versionId)) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }

        if (versionUrl == null) {
            throw new Exception("未找到版本: " + versionId);
        }

        // 3. 获取版本详细信息
        log("获取版本详细信息...");
        String versionJson = fetch(versionUrl);
        JsonObject version = JsonParser.parseString(versionJson).getAsJsonObject();

        // 4. 创建版本目录
        Path versionDir = Paths.get(gameDirectory, "versions", versionId);
        Files.createDirectories(versionDir);

        // 5. 保存版本 JSON
        Path versionJsonPath = versionDir.resolve(versionId + ".json");
        Files.writeString(versionJsonPath, versionJson);
        log("版本 JSON 已保存: " + versionJsonPath);

        // 6. 下载客户端 JAR
        if (version.has("downloads") && version.getAsJsonObject("downloads").has("client")) {
            JsonObject client = version.getAsJsonObject("downloads").getAsJsonObject("client");
            String clientUrl = client.get("url").getAsString();
            Path clientPath = versionDir.resolve(versionId + ".jar");

            log("下载客户端文件...");
            downloadFile(clientUrl, clientPath);
            log("客户端下载完成");
        }

        setProgress(20);

        // 7. 下载库文件
        if (version.has("libraries")) {
            log("下载库文件...");
            JsonArray libraries = version.getAsJsonArray("libraries");
            int total = libraries.size();
            int downloaded = 0;

            for (JsonElement libElem : libraries) {
                JsonObject lib = libElem.getAsJsonObject();
                if (shouldDownloadLibrary(lib)) {
                    downloadLibrary(lib);
                }
                downloaded++;
                setProgress(20 + (int)((downloaded * 60.0) / total));
            }
            log("库文件下载完成");
        }

        // 8. 下载资源索引
        if (version.has("assetIndex")) {
            JsonObject assetIndex = version.getAsJsonObject("assetIndex");
            String indexId = assetIndex.get("id").getAsString();
            String indexUrl = assetIndex.get("url").getAsString();

            Path indexesDir = Paths.get(gameDirectory, "assets", "indexes");
            Files.createDirectories(indexesDir);
            Path indexPath = indexesDir.resolve(indexId + ".json");

            log("下载资源索引: " + indexId);
            downloadFile(indexUrl, indexPath);

            // 可选：下载关键资源文件
            downloadEssentialAssets(indexPath);
        }

        setProgress(100);
        log("Minecraft " + versionId + " 下载完成！");
    }

    private void downloadFabric(String minecraftVersion) throws Exception {
        log("开始下载 Fabric " + minecraftVersion);
        setProgress(0);

        // 获取 Fabric 加载器版本
        String metaUrl = "https://meta.fabricmc.net/v2/versions/loader/" + minecraftVersion;
        String metaJson = fetch(metaUrl);
        JsonArray loaders = JsonParser.parseString(metaJson).getAsJsonArray();

        if (loaders.isEmpty()) {
            throw new Exception("未找到 Fabric 加载器版本");
        }

        JsonObject latestLoader = loaders.get(0).getAsJsonObject();
        String loaderVersion = latestLoader.getAsJsonObject("loader").get("version").getAsString();
        String intermediaryVersion = latestLoader.getAsJsonObject("intermediary").get("version").getAsString();

        log("Fabric 加载器版本: " + loaderVersion);
        log("Intermediary 版本: " + intermediaryVersion);

        // 创建 Fabric 版本目录
        String fabricVersionId = "fabric-loader-" + loaderVersion + "-" + minecraftVersion;
        Path versionDir = Paths.get(gameDirectory, "versions", fabricVersionId);
        Files.createDirectories(versionDir);

        // 下载 Fabric Loader JAR
        String loaderUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/" + loaderVersion + "/fabric-loader-" + loaderVersion + ".jar";
        Path loaderPath = versionDir.resolve("fabric-loader-" + loaderVersion + ".jar");
        downloadFile(loaderUrl, loaderPath);
        log("Fabric Loader 下载完成");

        setProgress(50);

        // 下载原版客户端
        downloadVanilla(minecraftVersion);

        setProgress(80);

        // 创建 Fabric 版本 JSON
        createFabricVersionJson(versionDir, minecraftVersion, loaderVersion, intermediaryVersion);

        setProgress(100);
        log("Fabric " + minecraftVersion + " 安装完成！版本ID: " + fabricVersionId);
    }

    private void downloadForge(String minecraftVersion) throws Exception {
        log("Forge 下载功能开发中...");
        log("请使用原版或 Fabric 版本");
        setProgress(100);
    }

    private boolean shouldDownloadLibrary(JsonObject library) {
        // 检查规则
        if (library.has("rules")) {
            JsonArray rules = library.getAsJsonArray("rules");
            String os = getCurrentOS();

            boolean allow = false;
            for (JsonElement ruleElem : rules) {
                JsonObject rule = ruleElem.getAsJsonObject();
                String action = rule.get("action").getAsString();

                if (rule.has("os")) {
                    JsonObject osObj = rule.getAsJsonObject("os");
                    if (osObj.has("name") && osObj.get("name").getAsString().equals(os)) {
                        allow = "allow".equals(action);
                    }
                } else {
                    allow = "allow".equals(action);
                }
            }
            return allow;
        }
        return true;
    }

    private void downloadLibrary(JsonObject library) throws Exception {
        if (!library.has("downloads")) return;

        JsonObject downloads = library.getAsJsonObject("downloads");

        // 下载 artifact
        if (downloads.has("artifact")) {
            JsonObject artifact = downloads.getAsJsonObject("artifact");
            String url = artifact.get("url").getAsString();
            String path = artifact.get("path").getAsString();
            Path destPath = Paths.get(gameDirectory, "libraries", path);

            if (!Files.exists(destPath)) {
                Files.createDirectories(destPath.getParent());
                downloadFile(url, destPath);
                log("  已下载: " + destPath.getFileName());
            }
        }

        // 下载 natives（如果需要）
        String os = getCurrentOS();
        if (downloads.has("classifiers")) {
            JsonObject classifiers = downloads.getAsJsonObject("classifiers");
            String classifierName = null;

            switch (os) {
                case "windows": classifierName = "natives-windows"; break;
                case "osx": classifierName = "natives-macos"; break;
                case "linux": classifierName = "natives-linux"; break;
            }

            if (classifierName != null && classifiers.has(classifierName)) {
                JsonObject nativeArtifact = classifiers.getAsJsonObject(classifierName);
                String url = nativeArtifact.get("url").getAsString();
                String path = nativeArtifact.get("path").getAsString();
                Path destPath = Paths.get(gameDirectory, "libraries", path);

                if (!Files.exists(destPath)) {
                    Files.createDirectories(destPath.getParent());
                    downloadFile(url, destPath);
                    log("  已下载原生库: " + destPath.getFileName());
                }
            }
        }
    }

    private void downloadEssentialAssets(Path indexPath) throws Exception {
        String jsonContent = Files.readString(indexPath);
        JsonObject index = JsonParser.parseString(jsonContent).getAsJsonObject();

        if (!index.has("objects")) return;

        JsonObject objects = index.getAsJsonObject("objects");

        // 只下载关键资源（语言文件）
        List<String> toDownload = new ArrayList<>();
        for (String key : objects.keySet()) {
            if (key.startsWith("minecraft/lang/")) {
                JsonObject obj = objects.getAsJsonObject(key);
                String hash = obj.get("hash").getAsString();
                toDownload.add(hash);
            }
        }

        log("需要下载 " + toDownload.size() + " 个语言文件");

        int downloaded = 0;
        for (String hash : toDownload) {
            String prefix = hash.substring(0, 2);
            Path destPath = Paths.get(gameDirectory, "assets", "objects", prefix, hash);

            if (!Files.exists(destPath)) {
                String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
                Files.createDirectories(destPath.getParent());
                downloadFile(url, destPath);
                downloaded++;
            }
        }

        if (downloaded > 0) {
            log("已下载 " + downloaded + " 个资源文件");
        }
    }

    private void createFabricVersionJson(Path versionDir, String mcVersion,
                                         String loaderVersion, String intermediaryVersion) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("id", "fabric-loader-" + loaderVersion + "-" + mcVersion);
        json.addProperty("inheritsFrom", mcVersion);
        json.addProperty("releaseTime", java.time.Instant.now().toString());
        json.addProperty("time", java.time.Instant.now().toString());
        json.addProperty("type", "release");
        json.addProperty("mainClass", "net.fabricmc.loader.impl.launch.knot.KnotClient");

        // 添加 Fabric 库
        JsonArray libraries = new JsonArray();

        JsonObject fabricLoader = new JsonObject();
        fabricLoader.addProperty("name", "net.fabricmc:fabric-loader:" + loaderVersion);
        libraries.add(fabricLoader);

        JsonObject intermediary = new JsonObject();
        intermediary.addProperty("name", "net.fabricmc:intermediary:" + intermediaryVersion);
        libraries.add(intermediary);

        json.add("libraries", libraries);

        Path jsonPath = versionDir.resolve(json.get("id").getAsString() + ".json");
        Files.writeString(jsonPath, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        log("Fabric 版本 JSON 已创建: " + jsonPath);
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "KKMCL/1.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("HTTP " + response.statusCode() + ": " + url);
        }
        return response.body();
    }

    private void downloadFile(String url, Path destPath) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new Exception("下载失败: " + url + " - HTTP " + response.statusCode());
        }

        try (InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(destPath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private String getCurrentOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }
}