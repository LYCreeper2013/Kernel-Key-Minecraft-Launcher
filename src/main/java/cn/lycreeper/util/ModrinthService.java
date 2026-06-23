package cn.lycreeper.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ModrinthService {
    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HttpClient httpClient;

    // 缓存正式版列表
    private static List<String> cachedReleaseVersions;
    private static long cacheTimestamp = 0;
    private static final long CACHE_DURATION = 3600000; // 1小时缓存

    public ModrinthService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    // ========== 数据模型 ==========

    public static class ModSearchResult {
        public String projectId;
        public String slug;
        public String title;
        public String description;
        public int downloads;
        public String iconUrl;
        public String author;
        public String dateModified;
        public String latestVersion;
        public String projectType;
        public List<String> categories = new ArrayList<>();
        public List<String> displayCategories = new ArrayList<>();
        public int follows;
        public String dateCreated;

        public String getShortDescription() {
            if (description == null || description.isEmpty()) return "暂无描述";
            String shortDesc = description.length() > 100 ? description.substring(0, 100) + "..." : description;
            return shortDesc.replace("\n", " ");
        }

        public String getFormattedDownloads() {
            return String.format("%,d", downloads);
        }

        public boolean isMod() {
            return "mod".equals(projectType);
        }

        @Override
        public String toString() {
            return title + " (" + slug + ") - " + getFormattedDownloads() + " 下载";
        }
    }

    public static class ModDetail {
        public String id;
        public String slug;
        public String title;
        public String description;
        public int downloads;
        public List<String> versions = new ArrayList<>();
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public String iconUrl;
        public String teamId;
        public String published;
        public String updated;
        public String projectType;
        public List<String> categories = new ArrayList<>();
        public int follows;
    }

    public static class ModVersion {
        public String id;
        public String name;
        public String versionNumber;
        public List<ModFile> files = new ArrayList<>();
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public String datePublished;
        public List<Dependency> dependencies = new ArrayList<>();

        public ModFile getPrimaryFile() {
            return files.stream().filter(f -> f.primary).findFirst().orElse(files.isEmpty() ? null : files.get(0));
        }
    }

    public static class ModFile {
        public String url;
        public String filename;
        public long size;
        public boolean primary;
        public FileHashes hashes;
    }

    public static class FileHashes {
        public String sha1;
        public String sha512;
    }

    public static class Dependency {
        public String versionId;
        public String projectId;
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

    public enum SortOrder {
        RELEVANCE("relevance"),
        DOWNLOADS("downloads"),
        FOLLOWS("follows"),
        NEWEST("newest"),
        UPDATED("updated");

        public final String value;
        SortOrder(String value) { this.value = value; }
    }

    public static class VersionInfo {
        public String id;
        public String type;
        public String url;
        public String releaseTime;

        @Override
        public String toString() {
            return id + " (" + type + ")";
        }
    }

    // ========== 版本获取方法 ==========

    /**
     * 获取所有正式版 Minecraft 版本列表（从 Mojang 官方 API 动态获取）
     */
    public List<String> getCommonGameVersions() {
        // 检查缓存
        if (cachedReleaseVersions != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION) {
            return new ArrayList<>(cachedReleaseVersions);
        }

        try {
            // 从 Mojang 官方 API 获取版本清单
            String jsonContent = fetchVersionManifest();
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonArray versionsArray = root.getAsJsonArray("versions");

            List<String> releaseVersions = new ArrayList<>();
            for (JsonElement elem : versionsArray) {
                JsonObject obj = elem.getAsJsonObject();
                String type = obj.get("type").getAsString();
                if ("release".equals(type)) {
                    String versionId = obj.get("id").getAsString();
                    releaseVersions.add(versionId);
                }
            }

            // 按版本号排序（最新的在前）
            releaseVersions.sort((a, b) -> compareVersions(b, a));

            // 更新缓存
            cachedReleaseVersions = releaseVersions;
            cacheTimestamp = System.currentTimeMillis();

            return new ArrayList<>(releaseVersions);

        } catch (Exception e) {
            System.err.println("获取正式版列表失败: " + e.getMessage());
            // 返回备用硬编码列表
            return getFallbackVersionList();
        }
    }

    /**
     * 异步获取所有正式版 Minecraft 版本列表
     */
    public CompletableFuture<List<String>> getCommonGameVersionsAsync() {
        return CompletableFuture.supplyAsync(this::getCommonGameVersions);
    }

    /**
     * 获取所有版本（包括快照版）
     */
    public List<VersionInfo> getAllVersions() {
        List<VersionInfo> versions = new ArrayList<>();

        try {
            String jsonContent = fetchVersionManifest();
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonArray versionsArray = root.getAsJsonArray("versions");

            for (JsonElement elem : versionsArray) {
                JsonObject obj = elem.getAsJsonObject();
                VersionInfo info = new VersionInfo();
                info.id = obj.get("id").getAsString();
                info.type = obj.get("type").getAsString();
                info.url = obj.get("url").getAsString();
                info.releaseTime = obj.get("releaseTime").getAsString();
                versions.add(info);
            }

            versions.sort((a, b) -> b.releaseTime.compareTo(a.releaseTime));

        } catch (Exception e) {
            System.err.println("获取所有版本失败: " + e.getMessage());
        }

        return versions;
    }

    private String fetchVersionManifest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VERSION_MANIFEST_URL))
                .header("User-Agent", "KKMCL/1.0.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private int compareVersions(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");

        for (int i = 0; i < Math.min(aParts.length, bParts.length); i++) {
            try {
                int aNum = Integer.parseInt(aParts[i]);
                int bNum = Integer.parseInt(bParts[i]);
                if (aNum != bNum) return Integer.compare(aNum, bNum);
            } catch (NumberFormatException e) {
                return aParts[i].compareTo(bParts[i]);
            }
        }
        return Integer.compare(aParts.length, bParts.length);
    }

    private List<String> getFallbackVersionList() {
        return Arrays.asList(
                "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
                "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
                "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                "1.18.2", "1.18.1", "1.18",
                "1.17.1", "1.17",
                "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
                "1.15.2", "1.15.1", "1.15",
                "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
                "1.13.2", "1.13.1", "1.13",
                "1.12.2", "1.12.1", "1.12"
        );
    }

    // ========== 搜索方法 ==========

    /**
     * 搜索 Mod（只返回 Mod 类型）
     */
    public CompletableFuture<List<ModSearchResult>> searchModsAsync(String query, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("query", query);
                params.put("limit", String.valueOf(limit));
                params.put("offset", String.valueOf(offset));
                params.put("facets", "[[\"project_type:mod\"]]");

                String url = buildUrl(BASE_URL + "/search", params);
                String response = get(url);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonArray hits = json.getAsJsonArray("hits");

                List<ModSearchResult> results = new ArrayList<>();
                for (JsonElement element : hits) {
                    results.add(parseSearchResult(element.getAsJsonObject()));
                }
                return results;
            } catch (Exception e) {
                throw new RuntimeException("搜索 Mod 失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 搜索 Mod（简化版）
     */
    public CompletableFuture<List<ModSearchResult>> searchModsAsync(String query) {
        return searchModsAsync(query, 20, 0);
    }

    /**
     * 带过滤条件的搜索
     */
    public CompletableFuture<List<ModSearchResult>> searchModsWithFilterAsync(
            String query, String gameVersion, String loader, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> facets = new ArrayList<>();
                facets.add("[\"project_type:mod\"]");
                if (gameVersion != null && !gameVersion.isEmpty()) {
                    facets.add("[\"versions:" + gameVersion + "\"]");
                }
                if (loader != null && !loader.isEmpty()) {
                    facets.add("[\"categories:" + loader + "\"]");
                }

                Map<String, String> params = new HashMap<>();
                params.put("query", query);
                params.put("limit", String.valueOf(limit));
                params.put("facets", "[" + String.join(",", facets) + "]");

                String url = buildUrl(BASE_URL + "/search", params);
                String response = get(url);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonArray hits = json.getAsJsonArray("hits");

                List<ModSearchResult> results = new ArrayList<>();
                for (JsonElement element : hits) {
                    results.add(parseSearchResult(element.getAsJsonObject()));
                }
                return results;
            } catch (Exception e) {
                throw new RuntimeException("过滤搜索失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取热门 Mod
     */
    public CompletableFuture<List<ModSearchResult>> getPopularModsAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("query", "");
                params.put("limit", String.valueOf(limit));
                params.put("facets", "[[\"project_type:mod\"]]");
                params.put("index", "downloads");

                String url = buildUrl(BASE_URL + "/search", params);
                String response = get(url);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonArray hits = json.getAsJsonArray("hits");

                List<ModSearchResult> results = new ArrayList<>();
                for (JsonElement element : hits) {
                    results.add(parseSearchResult(element.getAsJsonObject()));
                }
                return results;
            } catch (Exception e) {
                throw new RuntimeException("获取热门 Mod 失败: " + e.getMessage(), e);
            }
        });
    }

    // ========== Mod 详情和版本 ==========

    /**
     * 获取 Mod 详细信息
     */
    public CompletableFuture<ModDetail> getModDetailAsync(String modIdOrSlug) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/project/" + modIdOrSlug;
                String response = get(url);
                return parseModDetail(JsonParser.parseString(response).getAsJsonObject());
            } catch (Exception e) {
                throw new RuntimeException("获取 Mod 详情失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取 Mod 的所有版本
     */
    public CompletableFuture<List<ModVersion>> getModVersionsAsync(String modIdOrSlug) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/project/" + modIdOrSlug + "/version";
                String response = get(url);
                JsonArray array = JsonParser.parseString(response).getAsJsonArray();

                List<ModVersion> versions = new ArrayList<>();
                for (JsonElement element : array) {
                    versions.add(parseModVersion(element.getAsJsonObject()));
                }
                return versions;
            } catch (Exception e) {
                throw new RuntimeException("获取 Mod 版本失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取最新兼容版本
     */
    public CompletableFuture<ModVersion> getLatestCompatibleVersionAsync(
            String modIdOrSlug, String gameVersion, String loader) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/project/" + modIdOrSlug + "/version");
                List<String> queryParams = new ArrayList<>();
                if (gameVersion != null && !gameVersion.isEmpty()) {
                    queryParams.add("game_versions=[\"" + gameVersion + "\"]");
                }
                if (loader != null && !loader.isEmpty()) {
                    queryParams.add("loaders=[\"" + loader + "\"]");
                }
                if (!queryParams.isEmpty()) {
                    urlBuilder.append("?").append(String.join("&", queryParams));
                }

                String response = get(urlBuilder.toString());
                JsonArray array = JsonParser.parseString(response).getAsJsonArray();
                if (array.size() > 0) {
                    return parseModVersion(array.get(0).getAsJsonObject());
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException("获取兼容版本失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取多个 Mod 的详细信息（批量）
     */
    public CompletableFuture<List<ModDetail>> getMultipleModDetailsAsync(List<String> modIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String idsJson = GSON.toJson(modIds);
                String url = BASE_URL + "/projects?ids=" + URLEncoder.encode(idsJson, "UTF-8");
                String response = get(url);
                JsonArray array = JsonParser.parseString(response).getAsJsonArray();

                List<ModDetail> details = new ArrayList<>();
                for (JsonElement element : array) {
                    details.add(parseModDetail(element.getAsJsonObject()));
                }
                return details;
            } catch (Exception e) {
                throw new RuntimeException("批量获取 Mod 详情失败: " + e.getMessage(), e);
            }
        });
    }

    // ========== 下载方法 ==========

    /**
     * 获取 Mod 下载 URL
     */
    public CompletableFuture<String> getModDownloadUrlAsync(String modIdOrSlug, String gameVersion, String loader) {
        return getLatestCompatibleVersionAsync(modIdOrSlug, gameVersion, loader)
                .thenApply(version -> {
                    if (version == null) throw new RuntimeException("未找到兼容版本");
                    ModFile file = version.getPrimaryFile();
                    if (file == null) throw new RuntimeException("该版本没有可下载的文件");
                    return file.url;
                });
    }

    /**
     * 下载 Mod 文件
     */
    public CompletableFuture<Path> downloadModAsync(String downloadUrl, Path savePath, Consumer<DownloadProgress> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(savePath.getParent());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                long totalBytes = response.headers().firstValueAsLong("content-length").orElse(-1);

                try (InputStream in = response.body();
                     FileOutputStream out = new FileOutputStream(savePath.toFile())) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long received = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        received += bytesRead;

                        if (progressCallback != null) {
                            DownloadProgress progress = new DownloadProgress();
                            progress.bytesReceived = received;
                            progress.totalBytes = totalBytes;
                            progress.fileName = savePath.getFileName().toString();
                            progress.status = "下载中...";
                            if (totalBytes > 0) {
                                progress.percentage = (received * 100.0) / totalBytes;
                            }
                            progressCallback.accept(progress);
                        }
                    }
                }

                if (progressCallback != null) {
                    DownloadProgress complete = new DownloadProgress();
                    complete.bytesReceived = totalBytes;
                    complete.totalBytes = totalBytes;
                    complete.fileName = savePath.getFileName().toString();
                    complete.percentage = 100;
                    complete.status = "下载完成";
                    progressCallback.accept(complete);
                }

                return savePath;
            } catch (Exception e) {
                throw new RuntimeException("下载 Mod 失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 一键下载 Mod
     */
    public CompletableFuture<Path> quickDownloadModAsync(
            String modIdOrSlug, String gameVersion, String loader, Path saveDirectory, Consumer<DownloadProgress> progressCallback) {
        return getModDownloadUrlAsync(modIdOrSlug, gameVersion, loader)
                .thenCompose(url -> {
                    String filename = modIdOrSlug + "-" + gameVersion + ".jar";
                    Path savePath = saveDirectory.resolve(filename);
                    return downloadModAsync(url, savePath, progressCallback);
                });
    }

    /**
     * 下载指定版本的 Mod
     */
    public CompletableFuture<Path> downloadModVersionAsync(ModVersion version, Path directory, Consumer<DownloadProgress> progressCallback) {
        ModFile file = version.getPrimaryFile();
        if (file == null) {
            return CompletableFuture.failedFuture(new RuntimeException("该版本没有可下载的文件"));
        }
        Path savePath = directory.resolve(file.filename);
        return downloadModAsync(file.url, savePath, progressCallback);
    }

    // ========== 工具方法 ==========

    /**
     * 获取支持的加载器列表
     */
    public List<String> getSupportedLoaders() {
        return Arrays.asList("fabric", "forge", "quilt", "neoforge", "liteloader", "rift");
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ========== 私有方法 ==========

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "KKMCL/1.0.0")
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

    private ModSearchResult parseSearchResult(JsonObject obj) {
        ModSearchResult result = new ModSearchResult();
        result.projectId = getString(obj, "project_id");
        result.slug = getString(obj, "slug");
        result.title = getString(obj, "title");
        result.description = getString(obj, "description");
        result.downloads = getInt(obj, "downloads");
        result.iconUrl = getString(obj, "icon_url");
        result.author = getString(obj, "author");
        result.dateModified = getString(obj, "date_modified");
        result.latestVersion = getString(obj, "latest_version");
        result.projectType = getString(obj, "project_type");
        result.categories = getStringList(obj, "categories");
        result.displayCategories = getStringList(obj, "display_categories");
        result.follows = getInt(obj, "follows");
        result.dateCreated = getString(obj, "date_created");
        return result;
    }

    private ModDetail parseModDetail(JsonObject obj) {
        ModDetail detail = new ModDetail();
        detail.id = getString(obj, "id");
        detail.slug = getString(obj, "slug");
        detail.title = getString(obj, "title");
        detail.description = getString(obj, "description");
        detail.downloads = getInt(obj, "downloads");
        detail.versions = getStringList(obj, "versions");
        detail.gameVersions = getStringList(obj, "game_versions");
        detail.loaders = getStringList(obj, "loaders");
        detail.iconUrl = getString(obj, "icon_url");
        detail.teamId = getString(obj, "team");
        detail.published = getString(obj, "published");
        detail.updated = getString(obj, "updated");
        detail.projectType = getString(obj, "project_type");
        detail.categories = getStringList(obj, "categories");
        detail.follows = getInt(obj, "follows");
        return detail;
    }

    private ModVersion parseModVersion(JsonObject obj) {
        ModVersion version = new ModVersion();
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
                ModFile file = new ModFile();
                file.url = getString(fileObj, "url");
                file.filename = getString(fileObj, "filename");
                file.size = getLong(fileObj, "size");
                file.primary = getBoolean(fileObj, "primary");
                if (fileObj.has("hashes")) {
                    JsonObject hashes = fileObj.getAsJsonObject("hashes");
                    file.hashes = new FileHashes();
                    file.hashes.sha1 = getString(hashes, "sha1");
                    file.hashes.sha512 = getString(hashes, "sha512");
                }
                version.files.add(file);
            }
        }

        if (obj.has("dependencies")) {
            JsonArray depsArray = obj.getAsJsonArray("dependencies");
            for (JsonElement depElem : depsArray) {
                JsonObject depObj = depElem.getAsJsonObject();
                Dependency dep = new Dependency();
                dep.versionId = getString(depObj, "version_id");
                dep.projectId = getString(depObj, "project_id");
                dep.dependencyType = getString(depObj, "dependency_type");
                version.dependencies.add(dep);
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