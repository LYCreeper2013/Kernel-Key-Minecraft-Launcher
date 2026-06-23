package cn.lycreeper.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class GameLauncher {
    private static final Logger LOGGER = Logger.getLogger(GameLauncher.class.getName());
    private final VersionManager versionManager;
    private final Callback callback;

    public interface Callback {
        void onLog(String message);
        void onProgress(int percent);
        void onGameStarted(Process process);
        void onGameExited(int exitCode);
    }

    public GameLauncher(VersionManager versionManager, Callback callback) {
        this.versionManager = versionManager;
        this.callback = callback;
    }

    public void launch(String versionId, String javaPath, int minMemory, int maxMemory,
                       String username, String uuid, String accessToken, String userType) {

        callback.onLog("========== 开始启动游戏 ==========");
        callback.onProgress(5);

        try {
            Path versionDir = Paths.get(versionManager.gameDir, "versions", versionId);
            Path jsonPath = versionDir.resolve(versionId + ".json");

            if (!Files.exists(jsonPath)) {
                callback.onLog("错误: 版本配置文件不存在 - " + jsonPath);
                return;
            }

            String jsonContent = Files.readString(jsonPath);
            JsonObject versionJson = JsonParser.parseString(jsonContent).getAsJsonObject();

            callback.onLog("解析版本: " + versionId);
            callback.onProgress(10);

            // 处理继承
            String inheritsFrom = versionJson.has("inheritsFrom") ?
                    versionJson.get("inheritsFrom").getAsString() : null;

            // 合并父版本配置
            JsonObject mergedJson = versionJson;
            if (inheritsFrom != null) {
                Path parentJsonPath = Paths.get(versionManager.gameDir, "versions",
                        inheritsFrom, inheritsFrom + ".json");
                if (Files.exists(parentJsonPath)) {
                    String parentContent = Files.readString(parentJsonPath);
                    JsonObject parentJson = JsonParser.parseString(parentContent).getAsJsonObject();
                    mergedJson = mergeJson(parentJson, versionJson);
                    callback.onLog("合并父版本配置: " + inheritsFrom);
                }
            }

            // 确定主类
            String mainClass = getMainClass(mergedJson, versionId);
            callback.onLog("主类: " + mainClass);
            callback.onProgress(15);

            // 检测加载器类型
            String modLoader = detectModLoader(mergedJson, versionId);
            callback.onLog("加载器类型: " + modLoader);
            callback.onProgress(20);

            // 确保必要文件存在
            ensureRequiredFiles(versionId, inheritsFrom, modLoader);

            // 构建 classpath
            StringBuilder classpathBuilder = new StringBuilder();
            Path librariesDir = Paths.get(versionManager.gameDir, "libraries");
            Set<String> addedLibs = new HashSet<>();

            collectLibraries(mergedJson, librariesDir, classpathBuilder, addedLibs);

            // Fabric 特殊处理：添加 fabric-loader jar
            if ("fabric".equals(modLoader)) {
                Path fabricLoaderJar = findFabricLoaderJar(librariesDir);
                if (fabricLoaderJar != null) {
                    if (classpathBuilder.length() > 0) classpathBuilder.append(File.pathSeparator);
                    classpathBuilder.append(fabricLoaderJar.toAbsolutePath().toString());
                    callback.onLog("添加 Fabric Loader: " + fabricLoaderJar.getFileName());
                } else {
                    callback.onLog("警告: 未找到 fabric-loader jar");
                }
            }

            // Forge 特殊处理：确保必要库在 classpath 中
            if ("forge".equals(modLoader)) {
                Path bootstrapJar = findJarRecursive(librariesDir, "bootstraplauncher");
                if (bootstrapJar != null && !addedLibs.contains("bootstraplauncher")) {
                    callback.onLog("添加 bootstraplauncher: " + bootstrapJar.getFileName());
                }

                Path secureJar = findJarRecursive(librariesDir, "securejarhandler");
                if (secureJar != null) {
                    callback.onLog("找到 securejarhandler: " + secureJar.getFileName());
                }
            }

            // 添加版本 jar 文件
            Path versionJar = versionDir.resolve(versionId + ".jar");
            if (Files.exists(versionJar)) {
                if (classpathBuilder.length() > 0) classpathBuilder.append(File.pathSeparator);
                classpathBuilder.append(versionJar.toAbsolutePath().toString());
                callback.onLog("添加版本 jar: " + versionJar.getFileName());
            } else if (inheritsFrom != null) {
                Path parentJar = Paths.get(versionManager.gameDir, "versions",
                        inheritsFrom, inheritsFrom + ".jar");
                if (Files.exists(parentJar)) {
                    if (classpathBuilder.length() > 0) classpathBuilder.append(File.pathSeparator);
                    classpathBuilder.append(parentJar.toAbsolutePath().toString());
                    callback.onLog("添加父版本 jar: " + parentJar.getFileName());
                }
            }

            String classpath = classpathBuilder.toString();
            callback.onLog("Classpath 构建完成");
            callback.onProgress(50);

            // 构建启动命令
            List<String> command = buildLaunchCommand(
                    javaPath, minMemory, maxMemory,
                    versionId, inheritsFrom, modLoader,
                    classpath, mainClass, mergedJson,
                    username, uuid, accessToken, userType
            );

            callback.onProgress(80);
            callback.onLog(String.join(" ",command));
            // 启动进程
            callback.onLog("正在启动游戏进程...");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(versionManager.gameDir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            callback.onProgress(100);
            callback.onLog("游戏已启动，PID: " + process.pid());
            callback.onLog("========== 游戏启动完成 ==========");
            callback.onGameStarted(process);

            // 读取游戏输出
            readProcessOutput(process);

            // 等待游戏退出
            CompletableFuture.runAsync(() -> {
                try {
                    int exitCode = process.waitFor();
                    callback.onGameExited(exitCode);
                    if (exitCode != 0) {
                        callback.onLog("游戏异常退出，退出码: " + exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    callback.onLog("等待游戏退出被中断");
                }
            });

        } catch (Exception e) {
            callback.onLog("启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 构建启动命令
     */
    private List<String> buildLaunchCommand(String javaPath, int minMemory, int maxMemory,
                                            String versionId, String inheritsFrom,
                                            String modLoader, String classpath,
                                            String mainClass, JsonObject mergedJson,
                                            String username, String uuid,
                                            String accessToken, String userType) {
        List<String> command = new ArrayList<>();
        command.add(javaPath);

        // 内存设置
        command.add("-Xms" + minMemory + "M");
        command.add("-Xmx" + maxMemory + "M");

        // 编码
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");

        // 启动器标识
        command.add("-Dminecraft.launcher.brand=KKMCL");
        command.add("-Dminecraft.launcher.version=1.0");

        // GC 设置
        command.add("-XX:+UseG1GC");
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:G1NewSizePercent=20");
        command.add("-XX:G1ReservePercent=20");
        command.add("-XX:MaxGCPauseMillis=50");
        command.add("-XX:-OmitStackTraceInFastThrow");
        command.add("-XX:+DisableExplicitGC");

        // 原生库路径
        Path nativesPath = Paths.get(versionManager.gameDir, "versions",
                versionId, versionId + "-natives");
        if (Files.exists(nativesPath)) {
            command.add("-Djava.library.path=" + nativesPath.toAbsolutePath());
        } else if (inheritsFrom != null) {
            Path parentNatives = Paths.get(versionManager.gameDir, "versions",
                    inheritsFrom, inheritsFrom + "-natives");
            if (Files.exists(parentNatives)) {
                command.add("-Djava.library.path=" + parentNatives.toAbsolutePath());
            }
        }

        // Fabric 特定参数
        if ("fabric".equals(modLoader)) {
            command.add("-Dfabric.log.level=INFO");
            command.add("-Dfabric.development=false");
        }

        // Forge 特定参数
        // Forge 特定参数
        if ("forge".equals(modLoader)) {
            command.add("-Dforge.logging.console.level=debug");
            command.add("-Dforge.logging.markers=SCAN,REGISTRIES,REGISTRYDUMP");
            command.add("-Djava.net.preferIPv6Addresses=system");
            command.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            command.add("-Dfml.ignorePatchDiscrepancies=true");

            // ========== 添加以下参数 ==========
            // 解决 Java 17+ 模块访问问题
            command.add("--add-opens");
            command.add("java.base/java.lang.invoke=ALL-UNNAMED");
            command.add("--add-opens");
            command.add("java.base/java.lang.reflect=ALL-UNNAMED");
            command.add("--add-opens");
            command.add("java.base/java.lang=ALL-UNNAMED");
            command.add("--add-opens");
            command.add("java.base/java.util=ALL-UNNAMED");
            command.add("--add-opens");
            command.add("java.base/java.util.concurrent=ALL-UNNAMED");
            // =================================

            // 构建 ignoreList
            String ignoreList = "bootstraplauncher,securejarhandler,asm-commons," +
                    "asm-util,asm-analysis,asm-tree,asm,JarJarFileSystems," +
                    "client-extra,fmlcore,javafmllanguage,mclanguage,forge-";
            command.add("-DignoreList=" + ignoreList);
        }

        // 日志安全
        command.add("-Dlog4j2.formatMsgNoLookups=true");

        // Classpath
        command.add("-cp");
        command.add(classpath);

        // 主类
        command.add(mainClass);

        // 游戏参数
        command.add("--username");
        command.add(username);
        command.add("--version");
        command.add(versionId);
        command.add("--gameDir");
        command.add(versionManager.gameDir);
        command.add("--assetsDir");
        command.add(Paths.get(versionManager.gameDir, "assets").toString());
        command.add("--assetIndex");
        command.add(mergedJson.has("assets") ?
                mergedJson.get("assets").getAsString() : "legacy");
        command.add("--uuid");
        command.add(uuid);
        command.add("--accessToken");
        command.add(accessToken);
        command.add("--userType");
        command.add(userType);
        command.add("--versionType");
        command.add("KKMCL");

        // 窗口大小
        command.add("--width");
        command.add("854");
        command.add("--height");
        command.add("480");

        return command;
    }

    /**
     * 确保必要文件存在
     */
    private void ensureRequiredFiles(String versionId, String inheritsFrom, String modLoader) throws IOException {
        Path versionDir = Paths.get(versionManager.gameDir, "versions", versionId);

        // 确保版本 jar 存在
        Path versionJar = versionDir.resolve(versionId + ".jar");
        if (!Files.exists(versionJar) && inheritsFrom != null) {
            Path parentJar = Paths.get(versionManager.gameDir, "versions",
                    inheritsFrom, inheritsFrom + ".jar");
            if (Files.exists(parentJar)) {
                Files.copy(parentJar, versionJar);
                callback.onLog("已复制父版本 jar 到当前版本目录");
            }
        }

        // Forge 需要配置文件
        if ("forge".equals(modLoader)) {
            Path forgeConfig = versionDir.resolve("forge.cfg");
            if (!Files.exists(forgeConfig)) {
                // 检查是否有其他配置文件
                try (var stream = Files.list(versionDir)) {
                    boolean hasConfig = stream.anyMatch(p ->
                            p.getFileName().toString().endsWith(".cfg") ||
                                    p.getFileName().toString().equals("config.json") ||
                                    p.getFileName().toString().equals("forge.properties"));

                    if (!hasConfig) {
                        callback.onLog("警告: 未找到 Forge 配置文件，尝试创建默认配置");
                        // Forge 通常会在首次启动时自动创建配置
                    }
                }
            }
        }
    }

    /**
     * 合并 JSON 配置
     */
    private JsonObject mergeJson(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();

        for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
            String key = entry.getKey();

            if ("libraries".equals(key)) {
                JsonArray parentLibs = merged.has("libraries") ?
                        merged.getAsJsonArray("libraries") : new JsonArray();
                JsonArray childLibs = entry.getValue().getAsJsonArray();

                Set<String> libNames = new HashSet<>();
                for (JsonElement lib : parentLibs) {
                    if (lib.isJsonObject() && lib.getAsJsonObject().has("name")) {
                        libNames.add(lib.getAsJsonObject().get("name").getAsString());
                    }
                }

                for (JsonElement lib : childLibs) {
                    if (lib.isJsonObject() && lib.getAsJsonObject().has("name")) {
                        String name = lib.getAsJsonObject().get("name").getAsString();
                        if (!libNames.contains(name)) {
                            parentLibs.add(lib);
                            libNames.add(name);
                        }
                    }
                }

                merged.add("libraries", parentLibs);
            } else {
                merged.add(key, entry.getValue());
            }
        }

        return merged;
    }

    /**
     * 获取主类
     */
    private String getMainClass(JsonObject json, String versionId) {
        if (json.has("mainClass") && !json.get("mainClass").isJsonNull()) {
            return json.get("mainClass").getAsString();
        }

        String lowerId = versionId.toLowerCase();
        if (lowerId.contains("fabric")) {
            return "net.fabricmc.loader.impl.launch.knot.KnotClient";
        }
        if (lowerId.contains("forge")) {
            return "cpw.mods.bootstraplauncher.BootstrapLauncher";
        }

        return "net.minecraft.client.main.Main";
    }

    /**
     * 检测模组加载器类型
     */
    private String detectModLoader(JsonObject json, String versionId) {
        // 检查主类
        if (json.has("mainClass") && !json.get("mainClass").isJsonNull()) {
            String mainClass = json.get("mainClass").getAsString().toLowerCase();
            if (mainClass.contains("fabric")) return "fabric";
            if (mainClass.contains("forge") || mainClass.contains("bootstraplauncher")) return "forge";
            if (mainClass.contains("quilt")) return "quilt";
        }



        // 从版本 ID 判断
        String lowerId = versionId.toLowerCase();
        if (lowerId.contains("fabric")) return "fabric";
        if (lowerId.contains("forge")) return "forge";
        if (lowerId.contains("quilt")) return "quilt";

        return "vanilla";
    }

    /**
     * 收集 libraries 到 classpath
     */
    private void collectLibraries(JsonObject json, Path librariesDir,
                                  StringBuilder classpath, Set<String> addedLibs) {
        if (!json.has("libraries")) return;

        JsonArray libraries = json.getAsJsonArray("libraries");

        for (JsonElement lib : libraries) {
            if (!lib.isJsonObject()) continue;
            JsonObject libObj = lib.getAsJsonObject();

            if (!libObj.has("name")) continue;
            String name = libObj.get("name").getAsString();

            // 跳过已添加的
            if (addedLibs.contains(name)) continue;

            // 检查规则是否允许
            if (!isLibraryAllowed(libObj)) continue;

            // 解析路径
            String[] parts = name.split(":");
            if (parts.length < 3) continue;

            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];

            Path libPath = null;

            // 优先使用 downloads 中的路径
            if (libObj.has("downloads")) {
                JsonObject downloads = libObj.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    JsonObject artifactObj = downloads.getAsJsonObject("artifact");
                    if (artifactObj.has("path")) {
                        libPath = librariesDir.resolve(artifactObj.get("path").getAsString());
                    }
                }
            }

            // 如果 downloads 中没有，从 name 构建路径
            if (libPath == null) {
                String jarName = artifact + "-" + version + ".jar";
                if (parts.length >= 4) {
                    jarName = artifact + "-" + version + "-" + parts[3] + ".jar";
                }
                libPath = librariesDir.resolve(group).resolve(artifact).resolve(version).resolve(jarName);
            }

            if (Files.exists(libPath)) {
                if (classpath.length() > 0) classpath.append(File.pathSeparator);
                classpath.append(libPath.toAbsolutePath().toString());
                addedLibs.add(name);
            } else {
                // 尝试搜索文件（某些库的路径可能不同）
                Path foundJar = findJarRecursive(
                        librariesDir.resolve(group).resolve(artifact), artifact);
                if (foundJar != null) {
                    if (classpath.length() > 0) classpath.append(File.pathSeparator);
                    classpath.append(foundJar.toAbsolutePath().toString());
                    addedLibs.add(name);
                }
            }
        }
    }

    /**
     * 检查库是否允许在当前系统加载
     */
    private boolean isLibraryAllowed(JsonObject lib) {
        if (!lib.has("rules")) return true;

        JsonArray rules = lib.getAsJsonArray("rules");
        boolean allowed = false;

        for (JsonElement ruleElem : rules) {
            if (!ruleElem.isJsonObject()) continue;
            JsonObject rule = ruleElem.getAsJsonObject();

            boolean ruleApplies = true;

            // 检查操作系统限制
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) {
                    String osName = System.getProperty("os.name").toLowerCase();
                    String ruleOs = os.get("name").getAsString().toLowerCase();

                    if (osName.contains("win") && !ruleOs.equals("windows")) ruleApplies = false;
                    if (osName.contains("mac") && !ruleOs.equals("osx")) ruleApplies = false;
                    if (osName.contains("linux") && !ruleOs.equals("linux")) ruleApplies = false;
                }
            }

            if (ruleApplies && rule.has("action")) {
                allowed = "allow".equals(rule.get("action").getAsString());
            }
        }

        return allowed;
    }

    /**
     * 查找 Fabric Loader jar
     */
    private Path findFabricLoaderJar(Path librariesDir) {
        Path fabricDir = librariesDir.resolve("net/fabricmc/fabric-loader");
        if (!Files.exists(fabricDir)) {
            // 尝试搜索整个 libraries 目录
            return findJarRecursive(librariesDir, "fabric-loader");
        }

        try {
            return Files.walk(fabricDir)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> p.getFileName().toString().contains("fabric-loader"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("javadoc"))
                    .filter(p -> !p.getFileName().toString().contains("launchwrapper"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return findJarRecursive(librariesDir, "fabric-loader");
        }
    }

    /**
     * 递归搜索 jar 文件
     */
    private Path findJarRecursive(Path dir, String keyword) {
        if (!Files.exists(dir)) return null;

        try {
            return Files.walk(dir, 5)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> p.getFileName().toString().toLowerCase()
                            .contains(keyword.toLowerCase()))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("javadoc"))
                    .filter(p -> !p.getFileName().toString().contains("launchwrapper"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 读取进程输出
     */
    private void readProcessOutput(Process process) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    callback.onLog("[游戏] " + line);
                }
            } catch (IOException e) {
                if (!"Stream closed".equals(e.getMessage())) {
                    callback.onLog("读取游戏输出失败: " + e.getMessage());
                }
            }
        });
    }
}