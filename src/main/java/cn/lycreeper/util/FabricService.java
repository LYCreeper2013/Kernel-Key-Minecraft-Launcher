// ========== FabricService.java ==========
package cn.lycreeper.util;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FabricService {
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private final HttpClient httpClient;
    private final String gameDir;

    public FabricService(String gameDir) {
        this.gameDir = gameDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static class FabricVersion {
        public String version;
        public boolean stable;
    }

    public static class LoaderVersion {
        public String version;
        public boolean stable;
    }

    /**
     * 获取可用的 Fabric 版本列表
     */
    public CompletableFuture<List<FabricVersion>> getGameVersionsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = FABRIC_META + "/versions/game";
                String response = get(url);
                JsonArray array = JsonParser.parseString(response).getAsJsonArray();
                List<FabricVersion> versions = new ArrayList<>();
                for (JsonElement elem : array) {
                    JsonObject obj = elem.getAsJsonObject();
                    FabricVersion v = new FabricVersion();
                    v.version = obj.get("version").getAsString();
                    v.stable = obj.get("stable").getAsBoolean();
                    versions.add(v);
                }
                return versions;
            } catch (Exception e) {
                throw new RuntimeException("获取 Fabric 版本失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取可用的 Fabric Loader 版本列表
     */
    public CompletableFuture<List<LoaderVersion>> getLoaderVersionsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = FABRIC_META + "/versions/loader";
                String response = get(url);
                JsonArray array = JsonParser.parseString(response).getAsJsonArray();
                List<LoaderVersion> versions = new ArrayList<>();
                for (JsonElement elem : array) {
                    JsonObject obj = elem.getAsJsonObject();
                    LoaderVersion v = new LoaderVersion();
                    v.version = obj.get("version").getAsString();
                    v.stable = obj.get("stable").getAsBoolean();
                    versions.add(v);
                }
                return versions;
            } catch (Exception e) {
                throw new RuntimeException("获取 Loader 版本失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 安装 Fabric
     * @param mcVersion Minecraft 版本
     * @param loaderVersion Fabric Loader 版本
     * @param customName 自定义版本名称（可选）
     * @param logCallback 日志回调
     * @param progressCallback 进度回调
     */
    public CompletableFuture<String> installFabricAsync(String mcVersion, String loaderVersion,
                                                        String customName,
                                                        Consumer<String> logCallback,
                                                        Consumer<Integer> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String versionName = (customName != null && !customName.isEmpty())
                        ? customName
                        : "fabric-loader-" + loaderVersion + "-" + mcVersion;

                logCallback.accept("开始安装 Fabric...");
                logCallback.accept("Minecraft 版本: " + mcVersion);
                logCallback.accept("Fabric Loader: " + loaderVersion);
                logCallback.accept("版本名称: " + versionName);
                progressCallback.accept(5);

                // 1. 确保基础版本存在
                logCallback.accept("检查 Minecraft " + mcVersion + " 基础版本...");
                Path mcVersionDir = Paths.get(gameDir, "versions", mcVersion);
                if (!Files.exists(mcVersionDir)) {
                    logCallback.accept("下载 Minecraft " + mcVersion + "...");
                    VersionDownloader downloader = new VersionDownloader(gameDir);
                    downloader.setLogCallback(logCallback);
                    downloader.setProgressCallback(p -> {
                        progressCallback.accept(5 + p / 5);
                    });
                    downloader.downloadVersion(mcVersion, "vanilla");
                }
                progressCallback.accept(20);

                // 2. 获取 Fabric 版本配置
                logCallback.accept("获取 Fabric 加载器配置...");
                String profileUrl = FABRIC_META + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
                String profileJson = get(profileUrl);
                progressCallback.accept(30);

                // 3. 保存 Fabric 配置到版本目录
                Path versionDir = Paths.get(gameDir, "versions", versionName);
                Files.createDirectories(versionDir);

                Path jsonPath = versionDir.resolve(versionName + ".json");
                Files.writeString(jsonPath, profileJson);
                logCallback.accept("已保存 Fabric 配置: " + jsonPath);
                progressCallback.accept(50);

                // 4. 下载 Fabric 库文件
                JsonObject jsonObj = JsonParser.parseString(profileJson).getAsJsonObject();
                if (jsonObj.has("libraries")) {
                    JsonArray libraries = jsonObj.getAsJsonArray("libraries");
                    Path librariesDir = Paths.get(gameDir, "libraries");
                    int total = libraries.size();
                    int count = 0;

                    for (JsonElement libElem : libraries) {
                        JsonObject lib = libElem.getAsJsonObject();
                        String libName = lib.get("name").getAsString();

                        // 尝试从中央仓库下载
                        String libUrl = null;
                        if (lib.has("url") && !lib.get("url").isJsonNull()) {
                            libUrl = lib.get("url").getAsString();
                        } else {
                            libUrl = "https://maven.fabricmc.net/";
                        }

                        String[] parts = libName.split(":");
                        if (parts.length >= 3) {
                            String group = parts[0].replace('.', '/');
                            String artifact = parts[1];
                            String version = parts[2];
                            String jarName = artifact + "-" + version + ".jar";
                            String fullPath = group + "/" + artifact + "/" + version + "/" + jarName;

                            Path libPath = librariesDir.resolve(fullPath);
                            if (!Files.exists(libPath)) {
                                Files.createDirectories(libPath.getParent());
                                try {
                                    downloadFile(libUrl + fullPath, libPath);
                                } catch (Exception e) {
                                    // 尝试备用URL
                                    try {
                                        String altUrl = "https://libraries.minecraft.net/" + fullPath;
                                        downloadFile(altUrl, libPath);
                                    } catch (Exception e2) {
                                        logCallback.accept("警告: 无法下载 " + jarName + " - " + e.getMessage());
                                    }
                                }
                            }
                        }

                        count++;
                        int percent = 50 + (count * 40 / total);
                        progressCallback.accept(Math.min(percent, 90));
                    }
                }

                progressCallback.accept(95);

                // 5. 复制 Minecraft 本体 jar
                Path targetJar = versionDir.resolve(versionName + ".jar");
                if (!Files.exists(targetJar)) {
                    Path sourceJar = findMcJar(mcVersion);
                    if (sourceJar != null) {
                        Files.copy(sourceJar, targetJar);
                        logCallback.accept("已复制 Minecraft jar 文件");
                    }
                }

                progressCallback.accept(100);
                logCallback.accept("✅ Fabric 安装完成！版本名称: " + versionName);

                return versionName;

            } catch (Exception e) {
                logCallback.accept("Fabric 安装失败: " + e.getMessage());
                throw new RuntimeException("Fabric 安装失败: " + e.getMessage(), e);
            }
        });
    }

    private Path findMcJar(String mcVersion) throws IOException {
        Path versionsDir = Paths.get(gameDir, "versions");
        Path mcVersionDir = versionsDir.resolve(mcVersion);

        // 尝试标准路径
        Path jarPath = mcVersionDir.resolve(mcVersion + ".jar");
        if (Files.exists(jarPath)) return jarPath;

        // 尝试其他可能的路径
        try (var stream = Files.list(mcVersionDir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (path.toString().endsWith(".jar") && !path.getFileName().toString().contains("sources")) {
                    return path;
                }
            }
        }

        // 搜索客户端 jar
        Path clientJar = mcVersionDir.resolve("client.jar");
        if (Files.exists(clientJar)) return clientJar;

        return null;
    }

    private void downloadFile(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            try (InputStream in = response.body()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            throw new IOException("HTTP " + response.statusCode());
        }
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}