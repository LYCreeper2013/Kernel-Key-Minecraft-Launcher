package cn.lycreeper.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class JavaUtil {

    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("version \"(\\d+)\\.\\d+\\.\\d+\"");
    private static final Pattern LEGACY_VERSION_PATTERN = Pattern.compile("1\\.(\\d+)\\.0");

    /**
     * 扫描系统中的 Java 安装
     */
    public static List<JavaInfo> findJavaInstallations() {
        List<JavaInfo> javaList = new ArrayList<>();

        // 1. 检查 JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            String javaPath = findJavaExecutable(javaHome);
            if (javaPath != null) {
                JavaInfo info = getJavaInfo(javaPath);
                if (info != null) {
                    javaList.add(info);
                }
            }
        }

        // 2. 检查 PATH 中的 java
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                String javaPath = Paths.get(dir, "java").toString();
                if (File.separatorChar == '\\') {
                    javaPath = Paths.get(dir, "java.exe").toString();
                }
                if (Files.exists(Paths.get(javaPath))) {
                    JavaInfo info = getJavaInfo(javaPath);
                    if (info != null && !containsJavaPath(javaList, javaPath)) {
                        javaList.add(info);
                    }
                }
            }
        }

        // 3. Windows 注册表扫描
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            javaList.addAll(scanWindowsRegistry());
        }

        // 4. 常见安装路径扫描
        javaList.addAll(scanCommonPaths());

        // 去重并按版本降序排序
        return javaList.stream()
                .distinct()
                .sorted((a, b) -> Integer.compare(b.version, a.version))
                .toList();
    }

    /**
     * 扫描 Windows 注册表
     */
    private static List<JavaInfo> scanWindowsRegistry() {
        List<JavaInfo> javaList = new ArrayList<>();
        try {
            // 查询 HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Runtime Environment
            Process process = Runtime.getRuntime().exec(
                    "reg query \"HKLM\\SOFTWARE\\JavaSoft\\Java Runtime Environment\" /s");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                String currentPath = null;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("JavaHome")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 3) {
                            currentPath = parts[2];
                        }
                    } else if (line.contains("java.exe") && currentPath != null) {
                        String javaPath = Paths.get(currentPath, "bin", "java.exe").toString();
                        if (Files.exists(Paths.get(javaPath))) {
                            JavaInfo info = getJavaInfo(javaPath);
                            if (info != null) {
                                javaList.add(info);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // 忽略注册表读取失败
        }
        return javaList;
    }

    /**
     * 扫描常见安装路径
     */
    private static List<JavaInfo> scanCommonPaths() {
        List<JavaInfo> javaList = new ArrayList<>();
        List<String> commonPaths = new ArrayList<>();

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // Windows
            commonPaths.add("C:\\Program Files\\Java\\");
            commonPaths.add("C:\\Program Files (x86)\\Java\\");
            commonPaths.add(System.getProperty("user.home") + "\\.jdks\\");
        } else {
            // Linux / macOS
            commonPaths.add("/usr/lib/jvm/");
            commonPaths.add("/Library/Java/JavaVirtualMachines/");
            commonPaths.add(System.getProperty("user.home") + "/.sdkman/candidates/java/");
        }

        for (String basePath : commonPaths) {
            Path base = Paths.get(basePath);
            if (Files.exists(base)) {
                try (var stream = Files.list(base)) {
                    stream.filter(Files::isDirectory)
                            .filter(dir -> dir.toString().toLowerCase().contains("jdk") ||
                                    dir.toString().toLowerCase().contains("jre") ||
                                    dir.toString().toLowerCase().contains("java"))
                            .forEach(dir -> {
                                String javaPath = findJavaExecutable(dir.toString());
                                if (javaPath != null && Files.exists(Paths.get(javaPath))) {
                                    JavaInfo info = getJavaInfo(javaPath);
                                    if (info != null && !containsJavaPath(javaList, javaPath)) {
                                        javaList.add(info);
                                    }
                                }
                            });
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
        return javaList;
    }

    /**
     * 查找 Java 可执行文件路径
     */
    private static String findJavaExecutable(String javaHome) {
        Path javaPath = Paths.get(javaHome, "bin", "java");
        if (File.separatorChar == '\\') {
            javaPath = Paths.get(javaHome, "bin", "java.exe");
        }
        if (Files.exists(javaPath)) {
            return javaPath.toString();
        }

        // 尝试去掉可能的末尾 bin 目录
        if (javaHome.endsWith("bin")) {
            javaHome = javaHome.substring(0, javaHome.length() - 4);
            return findJavaExecutable(javaHome);
        }

        return null;
    }

    /**
     * 获取 Java 版本信息
     */
    private static JavaInfo getJavaInfo(String javaPath) {
        try {
            Process process = new ProcessBuilder(javaPath, "-version")
                    .redirectErrorStream(true)
                    .start();

            String output = readProcessOutput(process);
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

            int version = parseJavaVersion(output);
            String type = version >= 9 ? "JVM" : "JRE";

            if (version > 0) {
                return new JavaInfo(javaPath, version, type);
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    /**
     * 解析 Java 版本号
     */
    private static int parseJavaVersion(String output) {
        // 匹配 "version "21.0.5""
        Matcher m = JAVA_VERSION_PATTERN.matcher(output);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }

        // 匹配 "1.8.0" 格式
        m = LEGACY_VERSION_PATTERN.matcher(output);
        if (m.find()) {
            return 8;
        }

        return 0;
    }

    /**
     * 读取进程输出
     */
    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 检查是否已包含该 Java 路径
     */
    private static boolean containsJavaPath(List<JavaInfo> list, String path) {
        return list.stream().anyMatch(info -> info.path.equals(path));
    }

    /**
     * Java 信息类
     */
    public static class JavaInfo {
        public final String path;
        public final int version;
        public final String type;

        public JavaInfo(String path, int version, String type) {
            this.path = path;
            this.version = version;
            this.type = type;
        }

        @Override
        public String toString() {
            return path + " (Java " + version + ", " + type + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            JavaInfo other = (JavaInfo) obj;
            return path.equals(other.path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }
}