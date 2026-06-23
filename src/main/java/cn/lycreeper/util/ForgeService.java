// ========== ForgeService.java ==========
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

public class ForgeService {
    private final HttpClient httpClient;
    private final String gameDir;

    public ForgeService(String gameDir) {
        this.gameDir = gameDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static class ForgeVersion {
        public String mcVersion;
        public String forgeVersion;
        public String installerUrl;
        public boolean recommended;
    }

    /**
     * 获取可用的 Forge 版本列表
     */
    public CompletableFuture<List<ForgeVersion>> getVersionsAsync(String mcVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 使用 Forge Maven 元数据
                String url = "https://files.minecraftforge.net/net/minecraftforge/forge/index_" + mcVersion + ".html";
                // 使用 API 获取版本
                String apiUrl = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
                String response = get(apiUrl);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();

                List<ForgeVersion> versions = new ArrayList<>();

                if (json.has("promos")) {
                    JsonObject promos = json.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : promos.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue().getAsString();

                        if (key.endsWith("-recommended") && key.startsWith(mcVersion + "-")) {
                            ForgeVersion fv = new ForgeVersion();
                            fv.mcVersion = mcVersion;
                            fv.forgeVersion = value;
                            fv.recommended = true;
                            fv.installerUrl = buildInstallerUrl(mcVersion, value);
                            versions.add(fv);
                        } else if (key.endsWith("-latest") && key.startsWith(mcVersion + "-")) {
                            ForgeVersion fv = new ForgeVersion();
                            fv.mcVersion = mcVersion;
                            fv.forgeVersion = value;
                            fv.recommended = false;
                            fv.installerUrl = buildInstallerUrl(mcVersion, value);
                            versions.add(fv);
                        }
                    }
                }

                // 去重
                Map<String, ForgeVersion> uniqueMap = new LinkedHashMap<>();
                for (ForgeVersion v : versions) {
                    uniqueMap.put(v.forgeVersion, v);
                }

                return new ArrayList<>(uniqueMap.values());

            } catch (Exception e) {
                throw new RuntimeException("获取 Forge 版本失败: " + e.getMessage(), e);
            }
        });
    }

    private String buildInstallerUrl(String mcVersion, String forgeVersion) {
        // Forge 新版格式: forge-{mcVersion}-{forgeVersion}-installer.jar
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/" +
                mcVersion + "-" + forgeVersion + "/forge-" + mcVersion + "-" + forgeVersion + "-installer.jar";
    }

    /**
     * 安装 Forge
     * @param mcVersion Minecraft 版本
     * @param forgeVersion Forge 版本
     * @param customName 自定义版本名称
     * @param logCallback 日志回调
     * @param progressCallback 进度回调
     */
    public CompletableFuture<String> installForgeAsync(String mcVersion, String forgeVersion,
                                                       String customName,
                                                       Consumer<String> logCallback,
                                                       Consumer<Integer> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String versionName = (customName != null && !customName.isEmpty())
                        ? customName
                        : mcVersion + "-forge-" + forgeVersion;

                logCallback.accept("开始安装 Forge...");
                logCallback.accept("Minecraft 版本: " + mcVersion);
                logCallback.accept("Forge 版本: " + forgeVersion);
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

                // 2. 下载 Forge 安装器
                String installerUrl = buildInstallerUrl(mcVersion, forgeVersion);
                logCallback.accept("下载 Forge 安装器: " + installerUrl);

                Path tempDir = Paths.get(gameDir, "forge_temp");
                Files.createDirectories(tempDir);
                Path installerPath = tempDir.resolve("forge-installer.jar");

                downloadFile(installerUrl, installerPath);
                logCallback.accept("Forge 安装器下载完成");
                progressCallback.accept(40);

                // 3. 运行 Forge 安装器（提取版本信息）
                logCallback.accept("运行 Forge 安装器...");
                installForgeFromInstaller(installerPath, versionName, logCallback);
                progressCallback.accept(80);

                // 4. 复制 Minecraft 本体 jar
                Path versionDir = Paths.get(gameDir, "versions", versionName);
                Path targetJar = versionDir.resolve(versionName + ".jar");
                if (!Files.exists(targetJar)) {
                    Path sourceJar = findMcJar(mcVersion);
                    if (sourceJar != null) {
                        Files.copy(sourceJar, targetJar);
                        logCallback.accept("已复制 Minecraft jar 文件");
                    }
                }
                progressCallback.accept(90);

                // 5. 下载所需的库文件
                Path jsonPath = versionDir.resolve(versionName + ".json");
                if (Files.exists(jsonPath)) {
                    String jsonContent = Files.readString(jsonPath);
                    JsonObject jsonObj = JsonParser.parseString(jsonContent).getAsJsonObject();

                    if (jsonObj.has("libraries")) {
                        JsonArray libraries = jsonObj.getAsJsonArray("libraries");
                        Path librariesDir = Paths.get(gameDir, "libraries");
                        int total = libraries.size();
                        int count = 0;

                        for (JsonElement libElem : libraries) {
                            JsonObject lib = libElem.getAsJsonObject();
                            String libName = lib.get("name").getAsString();

                            if (lib.has("downloads")) {
                                JsonObject downloads = lib.getAsJsonObject("downloads");
                                if (downloads.has("artifact")) {
                                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                                    String libUrl = artifact.get("url").getAsString();
                                    String libPath = artifact.get("path").getAsString();

                                    Path targetLibPath = librariesDir.resolve(libPath);
                                    if (!Files.exists(targetLibPath)) {
                                        Files.createDirectories(targetLibPath.getParent());
                                        try {
                                            downloadFile(libUrl, targetLibPath);
                                        } catch (Exception e) {
                                            logCallback.accept("警告: 库下载失败 " + libName);
                                        }
                                    }
                                }
                            }

                            count++;
                            int percent = 80 + (count * 15 / total);
                            progressCallback.accept(Math.min(percent, 95));
                        }
                    }
                }

                // 清理临时文件
                try {
                    Files.deleteIfExists(installerPath);
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {}

                progressCallback.accept(100);
                logCallback.accept("✅ Forge 安装完成！版本名称: " + versionName);

                return versionName;

            } catch (Exception e) {
                logCallback.accept("Forge 安装失败: " + e.getMessage());
                throw new RuntimeException("Forge 安装失败: " + e.getMessage(), e);
            }
        });
    }

    private void installForgeFromInstaller(Path installerPath, String versionName,
                                           Consumer<String> logCallback) throws Exception {
        // 使用 ProcessBuilder 运行 Forge 安装器
        // forge-installer.jar --installClient <minecraft directory>
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", installerPath.toAbsolutePath().toString(),
                "--installClient", gameDir
        );
        pb.directory(new File(gameDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logCallback.accept("[Forge] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Forge 安装器退出码: " + exitCode);
        }
    }

    private Path findMcJar(String mcVersion) throws IOException {
        Path versionsDir = Paths.get(gameDir, "versions");
        Path mcVersionDir = versionsDir.resolve(mcVersion);

        Path jarPath = mcVersionDir.resolve(mcVersion + ".jar");
        if (Files.exists(jarPath)) return jarPath;

        try (var stream = Files.list(mcVersionDir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (path.toString().endsWith(".jar") && !path.getFileName().toString().contains("sources")) {
                    return path;
                }
            }
        }

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