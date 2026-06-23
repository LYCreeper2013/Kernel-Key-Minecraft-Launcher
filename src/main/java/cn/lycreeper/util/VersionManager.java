package cn.lycreeper.util;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class VersionManager {
    public final String gameDir;

    public VersionManager(String gameDir) {
        this.gameDir = gameDir;
    }

    // ========== 数据模型 ==========

    public static class VersionJson {
        public String id;
        public String inheritsFrom;
        public String mainClass;
        public String assets;
        public String type;
        public String time;
        public String releaseTime;
        public String minecraftArguments;
        public List<Library> libraries = new ArrayList<>();
        public List<Rule> rules = new ArrayList<>();
        public Map<String, DownloadInfo> downloads = new HashMap<>();
        public AssetIndex assetIndex;
        public JavaVersion javaVersion;
    }

    public static class Library {
        public String name;
        public LibraryDownloads downloads;
        public Map<String, String> natives;  // 操作系统 -> 原生库分类器
        public List<Rule> rules = new ArrayList<>();
        public boolean isNative;  // 是否为原生库
    }

    public static class LibraryDownloads {
        public ArtifactInfo artifact;
        public Map<String, ArtifactInfo> classifiers;  // 分类器下载信息
    }

    public static class ArtifactInfo {
        public String path;
        public String url;
        public String sha1;
        public long size;
    }

    public static class DownloadInfo {
        public String url;
        public String sha1;
        public long size;
        public String path;
    }

    public static class AssetIndex {
        public String id;
        public String url;
        public String sha1;
        public long size;
        public long totalSize;
    }

    public static class Rule {
        public String action; // "allow" 或 "disallow"
        public Map<String, Object> os; // 操作系统限制
        public Map<String, Object> features; // 特性限制
    }

    public static class JavaVersion {
        public String component;
        public int majorVersion;
    }

    public static class VersionInfo {
        public String id;
        public String type;
        public String url;
        public String time;
        public String releaseTime;
    }

    // ========== 版本解析 ==========

    /**
     * 解析版本 JSON 配置
     */
    public VersionJson parseVersion(String versionId) throws IOException {
        Path versionDir = Paths.get(gameDir, "versions", versionId);
        Path jsonPath = versionDir.resolve(versionId + ".json");

        if (!Files.exists(jsonPath)) {
            throw new FileNotFoundException("版本配置文件不存在: " + jsonPath);
        }

        String content = Files.readString(jsonPath);
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        VersionJson version = new VersionJson();
        version.id = getString(json, "id");
        version.inheritsFrom = getString(json, "inheritsFrom");
        version.mainClass = getString(json, "mainClass");
        version.assets = getString(json, "assets");
        version.type = getString(json, "type");
        version.time = getString(json, "time");
        version.releaseTime = getString(json, "releaseTime");
        version.minecraftArguments = getString(json, "minecraftArguments");

        // 解析 libraries
        if (json.has("libraries")) {
            JsonArray libArray = json.getAsJsonArray("libraries");
            for (JsonElement elem : libArray) {
                JsonObject libObj = elem.getAsJsonObject();
                Library lib = parseLibrary(libObj);
                if (lib != null) {
                    version.libraries.add(lib);
                }
            }
        }

        // 如果继承其他版本，合并父版本的配置
        if (version.inheritsFrom != null) {
            VersionJson parentVersion = parseVersion(version.inheritsFrom);
            version = mergeVersions(parentVersion, version);
        }

        return version;
    }

    /**
     * 解析单个库
     */
    private Library parseLibrary(JsonObject libObj) {
        Library lib = new Library();
        lib.name = getString(libObj, "name");

        // 解析下载信息
        if (libObj.has("downloads")) {
            JsonObject downloadsObj = libObj.getAsJsonObject("downloads");
            lib.downloads = new LibraryDownloads();

            // artifact
            if (downloadsObj.has("artifact")) {
                JsonObject artifactObj = downloadsObj.getAsJsonObject("artifact");
                lib.downloads.artifact = new ArtifactInfo();
                lib.downloads.artifact.path = getString(artifactObj, "path");
                lib.downloads.artifact.url = getString(artifactObj, "url");
                lib.downloads.artifact.sha1 = getString(artifactObj, "sha1");
                lib.downloads.artifact.size = getLong(artifactObj, "size");
            }

            // classifiers (包含原生库)
            if (downloadsObj.has("classifiers")) {
                lib.downloads.classifiers = new HashMap<>();
                JsonObject classifiersObj = downloadsObj.getAsJsonObject("classifiers");
                for (Map.Entry<String, JsonElement> entry : classifiersObj.entrySet()) {
                    JsonObject classifierObj = entry.getValue().getAsJsonObject();
                    ArtifactInfo info = new ArtifactInfo();
                    info.path = getString(classifierObj, "path");
                    info.url = getString(classifierObj, "url");
                    info.sha1 = getString(classifierObj, "sha1");
                    info.size = getLong(classifierObj, "size");
                    lib.downloads.classifiers.put(entry.getKey(), info);
                }
            }
        }

        // 解析原生库映射 (natives)
        if (libObj.has("natives")) {
            lib.natives = new HashMap<>();
            JsonObject nativesObj = libObj.getAsJsonObject("natives");
            for (Map.Entry<String, JsonElement> entry : nativesObj.entrySet()) {
                lib.natives.put(entry.getKey(), entry.getValue().getAsString());
            }
            lib.isNative = true;
        }

        // 解析规则
        if (libObj.has("rules")) {
            JsonArray rulesArray = libObj.getAsJsonArray("rules");
            for (JsonElement elem : rulesArray) {
                JsonObject ruleObj = elem.getAsJsonObject();
                Rule rule = new Rule();
                rule.action = getString(ruleObj, "action");

                if (ruleObj.has("os")) {
                    rule.os = new HashMap<>();
                    JsonObject osObj = ruleObj.getAsJsonObject("os");
                    for (Map.Entry<String, JsonElement> osEntry : osObj.entrySet()) {
                        rule.os.put(osEntry.getKey(), osEntry.getValue().getAsString());
                    }
                }

                if (ruleObj.has("features")) {
                    rule.features = new HashMap<>();
                    JsonObject featuresObj = ruleObj.getAsJsonObject("features");
                    for (Map.Entry<String, JsonElement> featEntry : featuresObj.entrySet()) {
                        rule.features.put(featEntry.getKey(), featEntry.getValue().getAsString());
                    }
                }

                lib.rules.add(rule);
            }
        }

        return lib;
    }

    /**
     * 合并父版本和子版本的配置
     */
    private VersionJson mergeVersions(VersionJson parent, VersionJson child) {
        // 如果子版本没有定义某些字段，使用父版本的
        if (child.mainClass == null) child.mainClass = parent.mainClass;
        if (child.assets == null) child.assets = parent.assets;
        if (child.minecraftArguments == null) child.minecraftArguments = parent.minecraftArguments;

        // 合并 libraries（去重）
        Set<String> libNames = new HashSet<>();
        for (Library lib : child.libraries) {
            libNames.add(lib.name);
        }

        for (Library parentLib : parent.libraries) {
            if (!libNames.contains(parentLib.name)) {
                child.libraries.add(parentLib);
                libNames.add(parentLib.name);
            }
        }

        return child;
    }

    // ========== 版本检测 ==========

    /**
     * 获取已安装的版本列表
     */
    public List<String> getInstalledVersions() {
        List<String> versions = new ArrayList<>();
        Path versionsDir = Paths.get(gameDir, "versions");

        if (!Files.exists(versionsDir)) {
            return versions;
        }

        try (var stream = Files.list(versionsDir)) {
            for (Path versionDir : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(versionDir)) {
                    String versionName = versionDir.getFileName().toString();
                    Path jsonFile = versionDir.resolve(versionName + ".json");
                    if (Files.exists(jsonFile)) {
                        versions.add(versionName);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return versions;
    }

    /**
     * 检测模组加载器类型
     */
    public String detectModLoader(String versionId) {
        try {
            VersionJson version = parseVersion(versionId);

            // 检查主类
            if (version.mainClass != null) {
                if (version.mainClass.contains("fabric")) return "fabric";
                if (version.mainClass.contains("bootstraplauncher") ||
                        version.mainClass.contains("cpw.mods")) return "forge";
                if (version.mainClass.contains("launchwrapper")) return "forge_legacy";
            }

            // 检查 libraries
            for (Library lib : version.libraries) {
                if (lib.name == null) continue;
                String name = lib.name.toLowerCase();
                if (name.contains("fabric-loader") || name.contains("fabricmc")) {
                    return "fabric";
                }
                if (name.contains("minecraftforge") ||
                        (name.contains("forge") && name.contains("universal"))) {
                    return "forge";
                }
                if (name.contains("quilt-loader")) return "quilt";
            }

            // 检查版本ID
            String lowerId = versionId.toLowerCase();
            if (lowerId.contains("fabric")) return "fabric";
            if (lowerId.contains("forge")) return "forge";
            if (lowerId.contains("quilt")) return "quilt";

        } catch (Exception e) {
            // 回退到简单的字符串匹配
            String lowerId = versionId.toLowerCase();
            if (lowerId.contains("fabric")) return "fabric";
            if (lowerId.contains("forge")) return "forge";
        }

        return "vanilla";
    }

    // ========== Classpath 构建 ==========

    /**
     * 构建 classpath
     */
    public String buildClasspath(VersionJson version, String versionId, String modLoader) throws IOException {
        Set<String> classpathEntries = new LinkedHashSet<>();
        Path gameDirPath = Paths.get(gameDir);

        // 添加所有库文件
        for (Library lib : version.libraries) {
            // 跳过原生库
            if (lib.isNative && lib.natives != null) continue;

            // 检查规则是否允许在当前系统加载
            if (!isLibraryAllowed(lib)) continue;

            Path libPath = resolveLibraryPath(gameDirPath, lib);
            if (libPath != null && Files.exists(libPath)) {
                classpathEntries.add(libPath.toAbsolutePath().toString());
            }
        }

        // 添加版本 jar
        Path versionDir = gameDirPath.resolve("versions").resolve(versionId);
        Path versionJar = versionDir.resolve(versionId + ".jar");
        if (Files.exists(versionJar)) {
            classpathEntries.add(versionJar.toAbsolutePath().toString());
        }

        return String.join(File.pathSeparator, classpathEntries);
    }

    /**
     * 检查库是否允许在当前系统加载
     */
    private boolean isLibraryAllowed(Library lib) {
        if (lib.rules == null || lib.rules.isEmpty()) {
            return true;
        }

        boolean allowed = false;
        for (Rule rule : lib.rules) {
            boolean ruleApplies = true;

            // 检查操作系统限制
            if (rule.os != null) {
                String osName = System.getProperty("os.name").toLowerCase();
                String ruleOs = rule.os.get("name").toString();
                if (ruleOs != null) {
                    if (osName.contains("win") && !ruleOs.equals("windows")) ruleApplies = false;
                    if (osName.contains("mac") && !ruleOs.equals("osx")) ruleApplies = false;
                    if (osName.contains("linux") && !ruleOs.equals("linux")) ruleApplies = false;
                }
            }

            if (ruleApplies) {
                allowed = "allow".equals(rule.action);
            }
        }

        return allowed;
    }

    /**
     * 解析库文件路径
     */
    private Path resolveLibraryPath(Path gameDirPath, Library lib) {
        // 优先使用 downloads 中的路径
        if (lib.downloads != null && lib.downloads.artifact != null &&
                lib.downloads.artifact.path != null) {
            return gameDirPath.resolve("libraries").resolve(lib.downloads.artifact.path);
        }

        // 从 name 构建路径
        if (lib.name != null) {
            String[] parts = lib.name.split(":");
            if (parts.length >= 3) {
                String group = parts[0].replace('.', '/');
                String artifact = parts[1];
                String version = parts[2];
                String jarName = artifact + "-" + version + ".jar";

                // 检查是否有时 Classifier
                if (parts.length >= 4) {
                    jarName = artifact + "-" + version + "-" + parts[3] + ".jar";
                }

                return gameDirPath.resolve("libraries")
                        .resolve(group)
                        .resolve(artifact)
                        .resolve(version)
                        .resolve(jarName);
            }
        }

        return null;
    }

    // ========== 资源下载 ==========

    /**
     * 下载资源文件
     */
    public void downloadAssets(VersionJson version, Consumer<String> logCallback) throws IOException {
        Path assetsDir = Paths.get(gameDir, "assets");
        Path indexesDir = assetsDir.resolve("indexes");
        Path objectsDir = assetsDir.resolve("objects");

        String assetIndex = version.assets != null ? version.assets : "legacy";
        Path indexFile = indexesDir.resolve(assetIndex + ".json");

        // 下载资源索引
        if (!Files.exists(indexFile)) {
            Files.createDirectories(indexesDir);
            String indexUrl = "https://piston-meta.mojang.com/v1/packages/" +
                    getAssetIndexHash(assetIndex) + "/" + assetIndex + ".json";

            try {
                downloadFile(indexUrl, indexFile);
                logCallback.accept("下载资源索引: " + assetIndex);
            } catch (Exception e) {
                logCallback.accept("下载资源索引失败: " + e.getMessage());
                return;
            }
        }

        // 解析资源索引并下载缺失的资源
        if (Files.exists(indexFile)) {
            String content = Files.readString(indexFile);
            JsonObject indexJson = JsonParser.parseString(content).getAsJsonObject();

            if (indexJson.has("objects")) {
                JsonObject objects = indexJson.getAsJsonObject("objects");
                int total = objects.size();
                int downloaded = 0;

                for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                    String assetName = entry.getKey();
                    JsonObject assetObj = entry.getValue().getAsJsonObject();
                    String hash = assetObj.get("hash").getAsString();

                    String subPath = hash.substring(0, 2) + "/" + hash;
                    Path objectPath = objectsDir.resolve(subPath);

                    if (!Files.exists(objectPath)) {
                        Files.createDirectories(objectPath.getParent());
                        String assetUrl = "https://resources.download.minecraft.net/" + subPath;

                        try {
                            downloadFile(assetUrl, objectPath);
                        } catch (Exception e) {
                            logCallback.accept("下载资源失败: " + assetName);
                        }
                    }

                    downloaded++;
                    if (downloaded % 100 == 0) {
                        logCallback.accept("资源进度: " + downloaded + "/" + total);
                    }
                }

                logCallback.accept("资源检查完成");
            }
        }
    }

    private String getAssetIndexHash(String assetIndex) {
        // 常见资源索引的哈希值
        Map<String, String> hashes = new HashMap<>();
        hashes.put("legacy", "770572e819335b6c0a053f8378ad88eda189fc14");
        hashes.put("1.20", "b078c4e8a46e3b53afb6c73e4a1991193e196539");
        hashes.put("1.20.1", "b078c4e8a46e3b53afb6c73e4a1991193e196539");
        hashes.put("1.19", "5bafe59967d86feba734acfe0c587ed8db4b4c38");
        hashes.put("1.19.4", "5bafe59967d86feba734acfe0c587ed8db4b4c38");
        hashes.put("1.18", "9e10fea24bc5583f7cda1ba5f5703e194c5f4da5");
        hashes.put("1.18.2", "9e10fea24bc5583f7cda1ba5f5703e194c5f4da5");
        hashes.put("1.17", "0c88283c2d158295107c7ca4d31a3a3b3b4f363a");
        hashes.put("1.17.1", "0c88283c2d158295107c7ca4d31a3a3b3b4f363a");
        hashes.put("1.16", "4bb60a1f0f414c034656a16e9e0f8b0c97e4b0a9");
        hashes.put("1.16.5", "4bb60a1f0f414c034656a16e9e0f8b0c97e4b0a9");

        return hashes.getOrDefault(assetIndex,
                hashes.get("legacy"));
    }

    // ========== 工具方法 ==========

    private void downloadFile(String url, Path target) throws IOException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(60))
                .build();

        try {
            java.net.http.HttpResponse<InputStream> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                Files.copy(response.body(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new IOException("HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断", e);
        }
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private long getLong(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : 0;
    }
}