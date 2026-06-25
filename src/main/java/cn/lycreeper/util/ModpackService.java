package cn.lycreeper.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.SwingUtilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ModpackService {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HttpClient httpClient;
    private final String gameDir;

    public ModpackService(String gameDir) {
        this.gameDir = gameDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ========== 数据模型 ==========

    public static class ModpackInfo {
        public String id;
        public String slug;
        public String title;
        public String description;
        public String author;
        public int downloads;
        public int follows;
        public String iconUrl;
        public List<String> versions = new ArrayList<>();
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public String datePublished;
        public String dateUpdated;
        public String source;

        public String getShortDescription() {
            if (description == null || description.isEmpty()) return "暂无描述";
            String shortDesc = description.length() > 100 ? description.substring(0, 100) + "..." : description;
            return shortDesc.replace("\n", " ");
        }

        public String getFormattedDownloads() {
            return String.format("%,d", downloads);
        }
    }

    public static class ModpackVersion {
        public String id;
        public String name;
        public String versionNumber;
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public List<ModpackFile> files = new ArrayList<>();
        public String datePublished;
        public List<Dependency> dependencies = new ArrayList<>();
    }

    public static class ModpackFile {
        public String url;
        public String filename;
        public long size;
        public String sha1;
        public boolean primary;
    }

    public static class Dependency {
        public String projectId;
        public String versionId;
        public String dependencyType;
    }

    public static class DownloadProgress {
        public long bytesReceived;
        public long totalBytes;
        public double percentage;
        public String fileName;
        public String status;

        public String getProgressText() {
            if (totalBytes > 0) {
                return String.format("%.1f%% (%s/%s)", percentage, formatSize(bytesReceived), formatSize(totalBytes));
            }
            return formatSize(bytesReceived) + " 已下载";
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    // ========== 搜索整合包 ==========

    public CompletableFuture<List<ModpackInfo>> searchModpacksAsync(String query, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("query", query);
                params.put("limit", String.valueOf(limit));
                params.put("offset", String.valueOf(offset));
                params.put("facets", "[[\"project_type:modpack\"]]");

                String url = buildUrl(MODRINTH_API + "/search", params);
                String response = get(url);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonArray hits = json.getAsJsonArray("hits");

                List<ModpackInfo> results = new ArrayList<>();
                for (JsonElement element : hits) {
                    results.add(parseModpackInfo(element.getAsJsonObject()));
                }
                return results;
            } catch (Exception e) {
                throw new RuntimeException("搜索整合包失败: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<List<ModpackInfo>> getPopularModpacksAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("query", "");
                params.put("limit", String.valueOf(limit));
                params.put("facets", "[[\"project_type:modpack\"]]");
                params.put("index", "downloads");

                String url = buildUrl(MODRINTH_API + "/search", params);
                String response = get(url);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonArray hits = json.getAsJsonArray("hits");

                List<ModpackInfo> results = new ArrayList<>();
                for (JsonElement element : hits) {
                    results.add(parseModpackInfo(element.getAsJsonObject()));
                }
                return results;
            } catch (Exception e) {
                throw new RuntimeException("获取热门整合包失败: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<List<ModpackVersion>> getVersionsAsync(String slug) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = MODRINTH_API + "/project/" + slug + "/version";
                String response = get(url);

                JsonArray array = JsonParser.parseString(response).getAsJsonArray();
                List<ModpackVersion> versions = new ArrayList<>();

                for (JsonElement elem : array) {
                    versions.add(parseModpackVersion(elem.getAsJsonObject()));
                }
                return versions;
            } catch (Exception e) {
                throw new RuntimeException("获取版本列表失败: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<ModpackVersion> getVersionAsync(String slug, String versionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = MODRINTH_API + "/project/" + slug + "/version/" + versionId;
                String response = get(url);
                return parseModpackVersion(JsonParser.parseString(response).getAsJsonObject());
            } catch (Exception e) {
                throw new RuntimeException("获取版本失败: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<ModpackVersion> getLatestCompatibleVersionAsync(String slug, String gameVersion, String loader) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = MODRINTH_API + "/project/" + slug + "/version";
                String response = get(url);
                JsonArray array = JsonParser.parseString(response).getAsJsonArray();

                ModpackVersion bestMatch = null;
                int bestScore = -1;

                for (JsonElement elem : array) {
                    JsonObject obj = elem.getAsJsonObject();
                    ModpackVersion version = parseModpackVersion(obj);

                    if (version.files == null || version.files.isEmpty()) continue;
                    boolean hasValidFile = false;
                    for (ModpackFile file : version.files) {
                        if (file.url != null && !file.url.isEmpty()) {
                            hasValidFile = true;
                            break;
                        }
                    }
                    if (!hasValidFile) continue;

                    int score = 0;
                    if (gameVersion != null && version.gameVersions.contains(gameVersion)) score += 10;
                    if (loader != null && version.loaders.contains(loader)) score += 5;
                    if (gameVersion == null && loader == null) score = 1;

                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = version;
                    }
                }

                return bestMatch;
            } catch (Exception e) {
                throw new RuntimeException("获取版本失败: " + e.getMessage(), e);
            }
        });
    }

    // ========== 下载和安装整合包 ==========

    private CompletableFuture<Path> downloadFileWithRetry(String url, Path savePath, Consumer<DownloadProgress> progressCallback, int maxRetries) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;

            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    if (retry > 0) {
                        Thread.sleep(2000 * retry);
                    }

                    Files.createDirectories(savePath.getParent());

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", "KKMCL/1.0.0")
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build();

                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() != 200) {
                        throw new IOException("HTTP " + response.statusCode());
                    }

                    long totalBytes = response.headers().firstValueAsLong("content-length").orElse(-1);

                    try (InputStream in = response.body();
                         FileOutputStream out = new FileOutputStream(savePath.toFile())) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long received = 0;

                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            received += bytesRead;

                            if (progressCallback != null && totalBytes > 0) {
                                final long receivedFinal = received;
                                final long totalFinal = totalBytes;
                                SwingUtilities.invokeLater(() -> {
                                    DownloadProgress progress = new DownloadProgress();
                                    progress.bytesReceived = receivedFinal;
                                    progress.totalBytes = totalFinal;
                                    progress.fileName = savePath.getFileName().toString();
                                    progress.percentage = (receivedFinal * 100.0) / totalFinal;
                                    progress.status = "下载中...";
                                    progressCallback.accept(progress);
                                });
                            }
                        }
                    }

                    if (Files.size(savePath) == 0) {
                        throw new IOException("下载的文件为空");
                    }

                    return savePath;

                } catch (Exception e) {
                    lastException = e;
                    try {
                        Files.deleteIfExists(savePath);
                    } catch (IOException ignored) {}
                }
            }

            throw new RuntimeException("下载失败，已重试 " + maxRetries + " 次: " + lastException.getMessage(), lastException);
        });
    }

    public CompletableFuture<Void> installModpackAsync(String slug, String versionId,
                                                       Consumer<String> logCallback,
                                                       Consumer<Integer> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logCallback.accept("开始安装整合包...");
                progressCallback.accept(0);

                ModpackVersion version;
                if (versionId != null && !versionId.isEmpty()) {
                    logCallback.accept("获取指定版本信息: " + versionId);
                    version = getVersionAsync(slug, versionId).get();
                } else {
                    logCallback.accept("获取最新版本信息...");
                    version = getLatestCompatibleVersionAsync(slug, null, null).get();
                }

                if (version == null) {
                    throw new RuntimeException("未找到可用的版本");
                }
                if (version.files == null || version.files.isEmpty()) {
                    throw new RuntimeException("版本没有可下载的文件");
                }

                ModpackFile file = null;
                for (ModpackFile f : version.files) {
                    if (f.primary) {
                        file = f;
                        break;
                    }
                }
                if (file == null && !version.files.isEmpty()) {
                    file = version.files.get(0);
                }
                if (file == null || file.url == null || file.url.isEmpty()) {
                    throw new RuntimeException("未找到有效的下载链接");
                }

                logCallback.accept("版本: " + version.versionNumber);
                if (file.filename != null) {
                    logCallback.accept("文件名: " + file.filename);
                }
                progressCallback.accept(5);

                String mcVersion = getMcVersionFromModpack(version);
                String loader = getLoaderFromModpack(version);
                logCallback.accept("Minecraft 版本: " + mcVersion);
                logCallback.accept("模组加载器: " + loader);
                progressCallback.accept(8);

                String versionName = slug + "-" + version.versionNumber;
                Path versionDir = Paths.get(gameDir, "versions", versionName);

                // 确保基础版本已安装
                logCallback.accept("检查并安装 Minecraft " + mcVersion + "...");
                installMinecraftVersion(mcVersion, loader, logCallback, progressCallback);
                progressCallback.accept(15);

                Path tempDir = Paths.get(gameDir, "modpacks", "temp_" + System.currentTimeMillis());
                Files.createDirectories(tempDir);

                String fileName = file.filename;
                if (fileName == null || fileName.isEmpty()) {
                    fileName = slug + "_" + version.versionNumber + ".mrpack";
                }
                if (!fileName.endsWith(".mrpack") && !fileName.endsWith(".zip")) {
                    fileName = fileName + ".mrpack";
                }
                Path zipPath = tempDir.resolve(fileName);

                logCallback.accept("下载整合包: " + fileName);
                progressCallback.accept(18);

                final String downloadUrl = file.url;
                final Consumer<DownloadProgress> progress = p -> {
                    if (p.percentage > 0) {
                        int percent = 18 + (int)(p.percentage * 0.12);
                        progressCallback.accept(Math.min(percent, 30));
                    }
                    logCallback.accept("下载: " + p.getProgressText());
                };

                downloadFileWithRetry(downloadUrl, zipPath, progress, 3).get();

                if (Files.size(zipPath) == 0) {
                    throw new RuntimeException("下载的文件为空，请检查网络连接后重试");
                }

                logCallback.accept("下载完成，文件大小: " + formatFileSize(Files.size(zipPath)));
                progressCallback.accept(32);

                logCallback.accept("解压整合包...");
                Path modpackDir = tempDir.resolve("extracted");
                Files.createDirectories(modpackDir);
                extractMrpack(zipPath, modpackDir, logCallback);

                progressCallback.accept(45);

                logCallback.accept("解析整合包配置...");
                // 修改：传入游戏根目录而不是版本目录
                parseAndInstallDependencies(modpackDir, Paths.get(gameDir), logCallback, progressCallback);

                progressCallback.accept(70);

                logCallback.accept("安装配置文件到版本目录...");
                copyOverridesToVersion(modpackDir, versionDir, logCallback);

                progressCallback.accept(80);

                logCallback.accept("复制 Minecraft 本体 jar 文件...");
                copyMinecraftJar(mcVersion, versionName, versionDir, logCallback);

                progressCallback.accept(90);

                logCallback.accept("创建版本配置...");
                createVersionConfig(modpackDir, mcVersion, loader, version.versionNumber, slug, versionName, versionDir, logCallback);

                progressCallback.accept(95);

                logCallback.accept("清理临时文件...");
                deleteDirectory(tempDir.toFile());

                progressCallback.accept(100);
                logCallback.accept("整合包安装完成！");
                logCallback.accept("请在「启动」标签页中选择版本: " + versionName);

                return null;

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("安装失败: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Void> installModpackAsync(String slug,
                                                       Consumer<String> logCallback,
                                                       Consumer<Integer> progressCallback) {
        return installModpackAsync(slug, null, logCallback, progressCallback);
    }

    private String getMcVersionFromModpack(ModpackVersion version) {
        if (version.gameVersions != null && !version.gameVersions.isEmpty()) {
            return version.gameVersions.get(0);
        }
        return "1.20.1";
    }

    private String getLoaderFromModpack(ModpackVersion version) {
        if (version.loaders != null && !version.loaders.isEmpty()) {
            String loader = version.loaders.get(0).toLowerCase();
            if (loader.contains("fabric")) return "fabric";
            if (loader.contains("forge")) return "forge";
            if (loader.contains("quilt")) return "quilt";
            return loader;
        }
        return "fabric";
    }

    private void installMinecraftVersion(String mcVersion, String loader,
                                         Consumer<String> logCallback,
                                         Consumer<Integer> progressCallback) {
        try {
            VersionDownloader downloader = new VersionDownloader(gameDir);
            downloader.setLogCallback(logCallback);
            downloader.setProgressCallback(percent -> {
                int mappedPercent = 8 + (percent * 7 / 100);
                progressCallback.accept(Math.min(mappedPercent, 15));
            });

            downloader.downloadVersion(mcVersion, loader);
            logCallback.accept("Minecraft " + mcVersion + " 基础版本安装完成");

        } catch (Exception e) {
            logCallback.accept("Minecraft 版本安装失败: " + e.getMessage());
            throw new RuntimeException("安装 Minecraft 版本失败: " + e.getMessage(), e);
        }
    }

    /**
     * 复制 Minecraft 本体 jar 文件到整合包目录
     */
    private void copyMinecraftJar(String mcVersion, String versionName, Path versionDir, Consumer<String> logCallback) throws IOException {
        Path targetJar = versionDir.resolve(versionName + ".jar");

        // 如果已经存在，跳过
        if (Files.exists(targetJar)) {
            logCallback.accept("jar 文件已存在: " + targetJar);
            return;
        }

        // 查找基础版本的 jar 文件
        Path sourceJar = findBaseVersionJar(mcVersion);

        if (sourceJar != null && Files.exists(sourceJar)) {
            Files.copy(sourceJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
            logCallback.accept("已复制 jar 文件: " + sourceJar.getFileName() + " -> " + versionName + ".jar");
        } else {
            logCallback.accept("警告: 未找到基础版本 jar 文件 " + mcVersion);
            logCallback.accept("尝试从 VersionDownloader 获取...");

            // 尝试重新下载基础版本
            try {
                VersionDownloader downloader = new VersionDownloader(gameDir);
                downloader.setLogCallback(logCallback);
                downloader.downloadVersion(mcVersion, "vanilla");

                // 再次查找
                sourceJar = findBaseVersionJar(mcVersion);
                if (sourceJar != null && Files.exists(sourceJar)) {
                    Files.copy(sourceJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                    logCallback.accept("重新下载后复制成功");
                } else {
                    throw new IOException("仍无法找到基础版本 jar 文件");
                }
            } catch (Exception e) {
                logCallback.accept("无法获取基础版本 jar: " + e.getMessage());
                throw new IOException("无法复制 Minecraft 本体 jar 文件", e);
            }
        }
    }

    /**
     * 查找基础版本的 jar 文件
     */
    private Path findBaseVersionJar(String mcVersion) throws IOException {
        Path versionsDir = Paths.get(gameDir, "versions");

        // 尝试多种可能的文件名
        String[] possibleNames = {
                mcVersion + ".jar",
                mcVersion + "-" + mcVersion + ".jar",
                mcVersion + "-forge.jar",
                mcVersion + "-fabric.jar"
        };

        // 首先尝试标准路径
        for (String name : possibleNames) {
            Path jarPath = versionsDir.resolve(mcVersion).resolve(name);
            if (Files.exists(jarPath)) {
                return jarPath;
            }
        }

        // 搜索包含该版本名的目录
        try (var stream = Files.list(versionsDir)) {
            for (Path versionPath : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(versionPath)) {
                    String dirName = versionPath.getFileName().toString();
                    if (dirName.startsWith(mcVersion) || dirName.contains(mcVersion)) {
                        for (String name : possibleNames) {
                            Path jarPath = versionPath.resolve(name);
                            if (Files.exists(jarPath)) {
                                return jarPath;
                            }
                        }
                        // 尝试任何 jar 文件
                        try (var jarStream = Files.list(versionPath)) {
                            for (Path jarPath : (Iterable<Path>) jarStream::iterator) {
                                if (jarPath.toString().endsWith(".jar") &&
                                        jarPath.getFileName().toString().contains(mcVersion)) {
                                    return jarPath;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private void extractMrpack(Path zipPath, Path destDir, Consumer<String> logCallback) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        logCallback.accept("解压完成");
    }

    /**
     * 解析并安装依赖（修复版 - MOD安装到游戏根目录）
     */
    private void parseAndInstallDependencies(Path modpackDir, Path gameRootDir,
                                             Consumer<String> logCallback,
                                             Consumer<Integer> progressCallback) throws IOException {
        Path indexFile = modpackDir.resolve("modrinth.index.json");

        // 调试：打印解压后的目录结构
        logCallback.accept("=== 解压目录内容 ===");
        try (var stream = Files.list(modpackDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String type = Files.isDirectory(p) ? "[DIR]" : "[FILE]";
                logCallback.accept(type + " " + p.getFileName());
            }
        }

        // 使用游戏根目录，而不是版本目录
        Path modsDir = gameRootDir.resolve("mods");
        Path configDir = gameRootDir.resolve("config");
        Path scriptsDir = gameRootDir.resolve("scripts");

        // 创建必要的目录
        Files.createDirectories(modsDir);
        Files.createDirectories(configDir);
        Files.createDirectories(scriptsDir);

        if (!Files.exists(indexFile)) {
            logCallback.accept("未找到 modrinth.index.json，尝试传统格式...");
            // 修改：传入游戏根目录
            handleTraditionalFormat(modpackDir, gameRootDir, logCallback, progressCallback);
            return;
        }

        String content = Files.readString(indexFile, StandardCharsets.UTF_8);
        logCallback.accept("找到 modrinth.index.json，大小: " + content.length() + " 字节");

        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        if (!json.has("files")) {
            logCallback.accept("index.json 中没有 files 字段");
            return;
        }

        JsonArray files = json.getAsJsonArray("files");

        int total = files.size();
        int processed = 0;
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        logCallback.accept("需要处理 " + total + " 个文件");
        logCallback.accept("MOD安装目录: " + modsDir.toString());
        logCallback.accept("Config安装目录: " + configDir.toString());

        for (JsonElement fileElem : files) {
            JsonObject fileObj;
            try {
                fileObj = fileElem.getAsJsonObject();
            } catch (Exception e) {
                processed++;
                continue;
            }

            String path = fileObj.has("path") ? fileObj.get("path").getAsString() : "";

            // 检查文件类型
            boolean isMod = path.startsWith("mods/") || path.contains("/mods/");
            boolean isConfig = path.startsWith("config/") || path.contains("/config/");
            boolean isScript = path.startsWith("scripts/") || path.contains("/scripts/");

            if (!isMod && !isConfig && !isScript) {
                processed++;
                continue;
            }

            String fileName = path.substring(path.lastIndexOf('/') + 1);

            Path destPath;
            if (isMod && (fileName.endsWith(".jar") || fileName.endsWith(".litemod"))) {
                // MOD文件安装到游戏根目录的mods文件夹
                String relativePath = path.startsWith("mods/") ?
                        path.substring("mods/".length()) : path;
                destPath = modsDir.resolve(relativePath);
                Files.createDirectories(destPath.getParent());
            } else if (isConfig) {
                String relativePath = path.startsWith("config/") ?
                        path.substring("config/".length()) : path;
                destPath = configDir.resolve(relativePath);
                Files.createDirectories(destPath.getParent());
            } else if (isScript) {
                String relativePath = path.startsWith("scripts/") ?
                        path.substring("scripts/".length()) : path;
                destPath = scriptsDir.resolve(relativePath);
                Files.createDirectories(destPath.getParent());
            } else {
                processed++;
                continue;
            }

            String downloadUrl = extractDownloadUrl(fileObj, logCallback, fileName);

            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                if (!Files.exists(destPath)) {
                    logCallback.accept("下载文件: " + fileName + " -> " + destPath);
                    try {
                        downloadFileWithRetry(downloadUrl, destPath, null, 2).get();
                        logCallback.accept("✓ 下载完成: " + fileName);
                        successCount++;
                    } catch (Exception e) {
                        logCallback.accept("✗ 下载失败: " + fileName + " - " + e.getMessage());
                        failCount++;
                    }
                } else {
                    logCallback.accept("文件已存在，跳过: " + fileName);
                    skipCount++;
                }
            } else {
                // 尝试从本地已解压的文件复制
                Path localFile = modpackDir.resolve(path);
                if (Files.exists(localFile)) {
                    logCallback.accept("从本地复制: " + fileName);
                    try {
                        Files.createDirectories(destPath.getParent());
                        Files.copy(localFile, destPath, StandardCopyOption.REPLACE_EXISTING);
                        successCount++;
                    } catch (Exception e) {
                        logCallback.accept("✗ 复制失败: " + fileName + " - " + e.getMessage());
                        failCount++;
                    }
                } else {
                    logCallback.accept("跳过 (无有效下载链接): " + fileName);
                    skipCount++;
                }
            }

            processed++;
            if (total > 0) {
                int percent = 70 + (processed * 15 / total);
                progressCallback.accept(Math.min(percent, 84));
            }
        }

        logCallback.accept("依赖处理完成: 成功 " + successCount + ", 跳过 " + skipCount + ", 失败 " + failCount);
    }

    /**
     * 处理传统格式的整合包（直接包含 mods 文件夹）- 修复版
     */
    private void handleTraditionalFormat(Path modpackDir, Path gameRootDir,
                                         Consumer<String> logCallback,
                                         Consumer<Integer> progressCallback) throws IOException {
        logCallback.accept("尝试传统格式处理...");

        // 检查是否有 mods 目录，复制到游戏根目录
        Path directMods = modpackDir.resolve("mods");
        if (Files.exists(directMods)) {
            logCallback.accept("发现传统格式 mods 目录，复制到游戏根目录");
            Path targetMods = gameRootDir.resolve("mods");
            copyDirectory(directMods, targetMods);
        }

        // 检查是否有 config 目录
        Path directConfig = modpackDir.resolve("config");
        if (Files.exists(directConfig)) {
            logCallback.accept("复制 config 目录到游戏根目录");
            Path targetConfig = gameRootDir.resolve("config");
            copyDirectory(directConfig, targetConfig);
        }

        // 检查是否有 scripts 目录
        Path directScripts = modpackDir.resolve("scripts");
        if (Files.exists(directScripts)) {
            logCallback.accept("复制 scripts 目录到游戏根目录");
            Path targetScripts = gameRootDir.resolve("scripts");
            copyDirectory(directScripts, targetScripts);
        }

        // 检查是否有 overrides 目录（覆盖文件）
        Path overridesDir = modpackDir.resolve("overrides");
        if (Files.exists(overridesDir)) {
            logCallback.accept("复制 overrides 目录到游戏根目录");
            copyDirectory(overridesDir, gameRootDir);
        }

        // 检查根目录下是否有直接的jar文件（某些整合包直接把mod放在根目录）
        try (var stream = Files.newDirectoryStream(modpackDir, "*.jar")) {
            boolean hasJars = false;
            for (Path jarPath : stream) {
                if (!hasJars) {
                    logCallback.accept("发现根目录下的jar文件，复制到mods目录");
                    hasJars = true;
                }
                Path targetMod = gameRootDir.resolve("mods").resolve(jarPath.getFileName());
                Files.createDirectories(targetMod.getParent());
                Files.copy(jarPath, targetMod, StandardCopyOption.REPLACE_EXISTING);
                logCallback.accept("  复制: " + jarPath.getFileName());
            }
        }
    }

    private String extractDownloadUrl(JsonObject fileObj, Consumer<String> logCallback, String fileName) {
        if (fileObj.has("downloads")) {
            JsonElement downloadsElem = fileObj.get("downloads");

            if (downloadsElem.isJsonPrimitive() && downloadsElem.getAsJsonPrimitive().isString()) {
                return downloadsElem.getAsString();
            }

            if (downloadsElem.isJsonObject()) {
                JsonObject downloads = downloadsElem.getAsJsonObject();
                if (downloads.has("url") && downloads.get("url").isJsonPrimitive()) {
                    return downloads.get("url").getAsString();
                }
                if (downloads.has("downloadUrl") && downloads.get("downloadUrl").isJsonPrimitive()) {
                    return downloads.get("downloadUrl").getAsString();
                }
            }

            if (downloadsElem.isJsonArray()) {
                JsonArray downloadsArray = downloadsElem.getAsJsonArray();
                if (downloadsArray.size() > 0) {
                    JsonElement first = downloadsArray.get(0);
                    if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString()) {
                        return first.getAsString();
                    }
                    if (first.isJsonObject()) {
                        JsonObject firstObj = first.getAsJsonObject();
                        if (firstObj.has("url") && firstObj.get("url").isJsonPrimitive()) {
                            return firstObj.get("url").getAsString();
                        }
                    }
                }
            }
        }

        if (fileObj.has("url") && fileObj.get("url").isJsonPrimitive()) {
            return fileObj.get("url").getAsString();
        }

        return null;
    }

    private void copyOverridesToVersion(Path modpackDir, Path versionDir, Consumer<String> logCallback) throws IOException {
        Path overridesDir = modpackDir.resolve("overrides");
        if (!Files.exists(overridesDir)) {
            // 可能 overrides 就在根目录
            overridesDir = modpackDir;
            if (!Files.exists(overridesDir)) {
                logCallback.accept("未找到 overrides 目录");
                return;
            }
        }

        String[] dirsToCopy = {"config", "scripts", "kubejs", "shaderpacks", "resourcepacks",
                "defaultconfigs", "patchouli_books", "structures", "bin", "advancements",
                "datapacks", "libraries", "assets"};

        for (String dirName : dirsToCopy) {
            Path sourceDir = overridesDir.resolve(dirName);
            if (Files.exists(sourceDir)) {
                Path targetDir = versionDir.resolve(dirName);
                logCallback.accept("复制目录: " + dirName);
                copyDirectory(sourceDir, targetDir);
            }
        }

        // 复制根目录下的配置文件
        try (var stream = Files.newDirectoryStream(overridesDir)) {
            for (Path sourcePath : stream) {
                if (Files.isRegularFile(sourcePath)) {
                    String fileName = sourcePath.getFileName().toString();
                    if (fileName.endsWith(".mcmeta") || fileName.equals("pack.png") ||
                            fileName.endsWith(".json") || fileName.equals("pack.txt")) {
                        Path targetPath = versionDir.resolve(fileName);
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        logCallback.accept("复制文件: " + fileName);
                    }
                }
            }
        }

        Path overridesMods = overridesDir.resolve("mods");
        if (Files.exists(overridesMods)) {
            Path targetMods = versionDir.resolve("mods");
            logCallback.accept("复制 overrides/mods 目录到版本目录（仅限特殊配置）");
            copyDirectory(overridesMods, targetMods);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        try (var stream = Files.walk(source)) {
            for (Path sourcePath : (Iterable<Path>) stream::iterator) {
                Path relativePath = source.relativize(sourcePath);
                Path targetPath = target.resolve(relativePath);

                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void createVersionConfig(Path modpackDir, String mcVersion, String loader,
                                     String packVersion, String slug, String versionName,
                                     Path versionDir, Consumer<String> logCallback) throws IOException {
        Path indexFile = modpackDir.resolve("modrinth.index.json");
        String packTitle = slug;

        if (Files.exists(indexFile)) {
            try {
                String content = Files.readString(indexFile, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("name")) {
                    packTitle = json.get("name").getAsString();
                }
            } catch (Exception e) {
                logCallback.accept("读取 index.json 失败: " + e.getMessage());
            }
        }

        Files.createDirectories(versionDir);

        JsonObject versionJson = new JsonObject();
        versionJson.addProperty("id", versionName);
        versionJson.addProperty("inheritsFrom", mcVersion);
        versionJson.addProperty("time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date()));
        versionJson.addProperty("releaseTime", versionJson.get("time").getAsString());
        versionJson.addProperty("type", "release");

        if ("fabric".equalsIgnoreCase(loader)) {
            versionJson.addProperty("mainClass", "net.fabricmc.loader.impl.launch.knot.KnotClient");
        } else if ("forge".equalsIgnoreCase(loader)) {
            versionJson.addProperty("mainClass", "cpw.mods.bootstraplauncher.BootstrapLauncher");
        } else {
            versionJson.addProperty("mainClass", "net.minecraft.client.main.Main");
        }

        JsonObject modpackInfo = new JsonObject();
        modpackInfo.addProperty("name", packTitle);
        modpackInfo.addProperty("version", packVersion);
        modpackInfo.addProperty("source", "modrinth");
        versionJson.add("modpack", modpackInfo);

        Path jsonPath = versionDir.resolve(versionName + ".json");
        try (FileWriter writer = new FileWriter(jsonPath.toFile())) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writer.write(gson.toJson(versionJson));
        }

        logCallback.accept("已创建版本配置: " + versionName);
        logCallback.accept("版本目录: " + versionDir.toString());

        // 验证 jar 文件是否存在
        Path jarFile = versionDir.resolve(versionName + ".jar");
        if (Files.exists(jarFile)) {
            logCallback.accept("jar 文件已就绪: " + jarFile.getFileName());
        } else {
            logCallback.accept("警告: jar 文件不存在，可能需要手动复制");
        }
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "KKMCL/1.0.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String buildUrl(String base, Map<String, String> params) {
        if (params == null || params.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base);
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                sb.append(first ? "?" : "&");
                first = false;
                try {
                    sb.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                } catch (Exception e) {
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
        }
        return sb.toString();
    }

    private ModpackInfo parseModpackInfo(JsonObject obj) {
        ModpackInfo info = new ModpackInfo();
        info.id = getString(obj, "id");
        info.slug = getString(obj, "slug");
        info.title = getString(obj, "title");
        info.description = getString(obj, "description");
        info.downloads = getInt(obj, "downloads");
        info.follows = getInt(obj, "follows");
        info.iconUrl = getString(obj, "icon_url");
        info.datePublished = getString(obj, "date_published");
        info.dateUpdated = getString(obj, "date_modified");
        info.source = "modrinth";

        if (obj.has("author")) {
            info.author = getString(obj, "author");
        } else if (obj.has("team")) {
            info.author = getString(obj, "team");
        }

        info.gameVersions = getStringList(obj, "game_versions");
        info.loaders = getStringList(obj, "loaders");

        return info;
    }

    private ModpackVersion parseModpackVersion(JsonObject obj) {
        ModpackVersion version = new ModpackVersion();
        version.id = getString(obj, "id");
        version.name = getString(obj, "name");
        version.versionNumber = getString(obj, "version_number");
        version.datePublished = getString(obj, "date_published");
        version.gameVersions = getStringList(obj, "game_versions");
        version.loaders = getStringList(obj, "loaders");

        if (obj.has("files")) {
            JsonArray filesArray = obj.getAsJsonArray("files");
            for (JsonElement fileElem : filesArray) {
                JsonObject fileObj = fileElem.getAsJsonObject();
                ModpackFile file = new ModpackFile();
                file.url = getString(fileObj, "url");
                file.filename = getString(fileObj, "filename");
                file.size = getLong(fileObj, "size");
                file.primary = getBoolean(fileObj, "primary");
                version.files.add(file);
            }
        }

        return version;
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private int getInt(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
    }

    private long getLong(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : 0L;
    }

    private boolean getBoolean(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).getAsBoolean();
    }

    private List<String> getStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray array = obj.getAsJsonArray(key);
            for (JsonElement elem : array) {
                if (elem.isJsonPrimitive()) {
                    list.add(elem.getAsString());
                }
            }
        }
        return list;
    }
}