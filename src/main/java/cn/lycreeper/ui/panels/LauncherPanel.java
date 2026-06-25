package cn.lycreeper.ui.panels;

import cn.lycreeper.util.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LauncherPanel extends JPanel {
    // UI 组件
    private JComboBox<String> gameVersionCombo;
    private JTextArea outputArea;
    private JButton launchButton;
    private JButton refreshButton;
    private JComboBox<String> javaPathCombo;
    private JTextField minMemoryText;
    private JTextField maxMemoryText;
    private JRadioButton offlineRadio;
    private JRadioButton microsoftRadio;
    private JTextField offlineNameText;
    private JProgressBar progressBar;
    private JLabel modLoaderStatusLabel;

    // 登录面板
    private JPanel offlinePanel;
    private JPanel microsoftPanel;

    // 微软登录相关
    private JLabel microsoftAvatarLabel;
    private JButton microsoftLoginBtn;
    private JButton microsoftCancelBtn;
    private JLabel microsoftStatusLabel;
    private JPanel microsoftInfoPanel;
    private JLabel microsoftUsernameLabel;
    private JLabel microsoftUUIDLabel;

    // 数据
    private final String gameDirectory = System.getProperty("user.dir") + "\\.minecraft";
    private MicrosoftAuthenticator authenticator;
    private MicrosoftAuthenticator.MicrosoftAccount microsoftAccount;
    private final VersionManager versionManager;
    private boolean isLaunching = false;

    public LauncherPanel() {
        versionManager = new VersionManager(gameDirectory);
        setLayout(new BorderLayout());
        setBackground(new Color(240, 248, 255));
        initUI();
        loadJavaVersions();
        loadGameVersions();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(new Color(240, 248, 255));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = createTopPanel();
        JPanel leftPanel = createLeftPanel();
        JPanel rightPanel = createRightPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(380);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        add(mainPanel);

        addLog("KKMCL Java 版启动器已启动");
        addLog("游戏目录: " + gameDirectory);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBackground(new Color(240, 248, 255));
        topPanel.setBorder(BorderFactory.createTitledBorder("游戏版本"));

        gameVersionCombo = new JComboBox<>();
        gameVersionCombo.setPreferredSize(new Dimension(200, 30));
        gameVersionCombo.addActionListener(e -> updateModLoaderStatus());
        refreshButton = new JButton("🔄 刷新");
        refreshButton.addActionListener(e -> loadGameVersions());

        topPanel.add(new JLabel("选择版本:"));
        topPanel.add(gameVersionCombo);
        topPanel.add(refreshButton);

        return topPanel;
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(new Color(240, 248, 255));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Java 设置"));

        // Java 路径
        JPanel javaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        javaPanel.setBackground(new Color(240, 248, 255));
        javaPanel.add(new JLabel("Java路径:"));
        javaPathCombo = new JComboBox<>();
        javaPathCombo.setPreferredSize(new Dimension(350, 30));
        javaPanel.add(javaPathCombo);

        // 内存设置
        JPanel memoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        memoryPanel.setBackground(new Color(240, 248, 255));
        memoryPanel.add(new JLabel("最小内存:"));
        minMemoryText = new JTextField("512", 6);
        memoryPanel.add(minMemoryText);
        memoryPanel.add(new JLabel("MB"));
        memoryPanel.add(Box.createHorizontalStrut(20));
        memoryPanel.add(new JLabel("最大内存:"));
        maxMemoryText = new JTextField("4096", 6);
        memoryPanel.add(maxMemoryText);
        memoryPanel.add(new JLabel("MB"));

        // 登录设置
        JPanel loginPanel = createLoginPanel();

        // 模组加载器状态
        modLoaderStatusLabel = new JLabel("原版 Minecraft");
        modLoaderStatusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        modLoaderStatusLabel.setForeground(Color.GRAY);

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(350, 25));
        progressBar.setVisible(false);

        // 启动按钮
        launchButton = new JButton("启动游戏");
        launchButton.setFont(new Font("微软雅黑", Font.BOLD, 16));
        launchButton.setBackground(new Color(0, 120, 215));
        launchButton.setForeground(Color.WHITE);
        launchButton.setPreferredSize(new Dimension(150, 45));
        launchButton.addActionListener(e -> launchGame());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(240, 248, 255));
        buttonPanel.add(launchButton);

        leftPanel.add(javaPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(memoryPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(loginPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(modLoaderStatusLabel);
        leftPanel.add(Box.createVerticalStrut(15));
        leftPanel.add(progressBar);
        leftPanel.add(Box.createVerticalStrut(15));
        leftPanel.add(buttonPanel);
        leftPanel.add(Box.createVerticalGlue());

        return leftPanel;
    }

    private JPanel createLoginPanel() {
        JPanel loginPanel = new JPanel();
        loginPanel.setBorder(BorderFactory.createTitledBorder("登录设置"));
        loginPanel.setBackground(new Color(240, 248, 255));
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));

        // 登录方式选择
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.setBackground(new Color(240, 248, 255));

        offlineRadio = new JRadioButton("离线登录", true);
        microsoftRadio = new JRadioButton("正版登录");
        ButtonGroup loginGroup = new ButtonGroup();
        loginGroup.add(offlineRadio);
        loginGroup.add(microsoftRadio);
        offlineRadio.addActionListener(e -> switchLoginPanel());
        microsoftRadio.addActionListener(e -> switchLoginPanel());

        radioPanel.add(offlineRadio);
        radioPanel.add(microsoftRadio);

        // 离线登录面板
        offlinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        offlinePanel.setBackground(new Color(240, 248, 255));
        offlinePanel.add(new JLabel("玩家名:"));
        offlineNameText = new JTextField("Player", 15);
        offlinePanel.add(offlineNameText);

        // 微软登录面板
        microsoftPanel = createMicrosoftPanel();
        microsoftPanel.setVisible(false);

        loginPanel.add(radioPanel);
        loginPanel.add(offlinePanel);
        loginPanel.add(microsoftPanel);

        return loginPanel;
    }

    private JPanel createMicrosoftPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(240, 248, 255));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        microsoftAvatarLabel = new JLabel();
        microsoftAvatarLabel.setPreferredSize(new Dimension(64, 64));
        microsoftAvatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
        microsoftAvatarLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.add(microsoftAvatarLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(new Color(240, 248, 255));
        microsoftLoginBtn = new JButton("登录");
        microsoftCancelBtn = new JButton("取消");
        microsoftCancelBtn.setVisible(false);
        microsoftLoginBtn.addActionListener(e -> doMicrosoftLogin());
        microsoftCancelBtn.addActionListener(e -> cancelMicrosoftLogin());
        buttonPanel.add(microsoftLoginBtn);
        buttonPanel.add(microsoftCancelBtn);
        panel.add(buttonPanel);

        microsoftStatusLabel = new JLabel("未登录");
        microsoftStatusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        microsoftStatusLabel.setForeground(Color.GRAY);
        panel.add(microsoftStatusLabel);

        microsoftInfoPanel = new JPanel();
        microsoftInfoPanel.setLayout(new BoxLayout(microsoftInfoPanel, BoxLayout.Y_AXIS));
        microsoftInfoPanel.setBackground(new Color(240, 248, 255));
        microsoftInfoPanel.setVisible(false);

        microsoftUsernameLabel = new JLabel();
        microsoftUsernameLabel.setFont(new Font("微软雅黑", Font.BOLD, 11));
        microsoftUUIDLabel = new JLabel();
        microsoftUUIDLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        microsoftUUIDLabel.setForeground(Color.GRAY);

        microsoftInfoPanel.add(microsoftUsernameLabel);
        microsoftInfoPanel.add(microsoftUUIDLabel);
        panel.add(microsoftInfoPanel);

        return panel;
    }

    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(240, 248, 255));
        rightPanel.setBorder(BorderFactory.createTitledBorder("启动日志"));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setBackground(Color.WHITE);
        outputArea.setForeground(new Color(0, 0, 0));
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        return rightPanel;
    }

    private void switchLoginPanel() {
        if (offlineRadio.isSelected()) {
            offlinePanel.setVisible(true);
            microsoftPanel.setVisible(false);
            addLog("切换到离线登录模式");
        } else {
            offlinePanel.setVisible(false);
            microsoftPanel.setVisible(true);
            addLog("切换到正版登录模式");
            if (microsoftAccount == null) {
                microsoftStatusLabel.setText("未登录");
                microsoftInfoPanel.setVisible(false);
                microsoftAvatarLabel.setIcon(null);
            }
        }
        revalidate();
        repaint();
    }

    private void doMicrosoftLogin() {
        microsoftLoginBtn.setVisible(false);
        microsoftCancelBtn.setVisible(true);
        microsoftStatusLabel.setText("正在获取验证码...");

        MicrosoftAuthenticator.LoginCallback callback = new MicrosoftAuthenticator.LoginCallback() {
            @Override
            public void onLog(String message) {
                addLog("[正版] " + message);
            }

            @Override
            public void onWaitingForCode(String userCode, String verificationUri) {
                SwingUtilities.invokeLater(() -> {
                    microsoftStatusLabel.setText("<html>请访问:<br>" + verificationUri + "<br>输入代码: " + userCode + "</html>");
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(new URI(verificationUri));
                        }
                    } catch (Exception ex) {
                        addLog("无法打开浏览器，请手动访问");
                    }
                });
            }

            @Override
            public void onSuccess(MicrosoftAuthenticator.MicrosoftAccount account) {
                SwingUtilities.invokeLater(() -> {
                    microsoftAccount = account;
                    microsoftLoginBtn.setVisible(false);
                    microsoftCancelBtn.setVisible(false);
                    microsoftStatusLabel.setText(account.username);
                    microsoftUsernameLabel.setText("玩家: " + account.username);
                    microsoftUUIDLabel.setText("UUID: " + account.uuid.substring(0, 8) + "...");
                    microsoftInfoPanel.setVisible(true);

                    microsoftAvatarLabel.setText("⌛");
                    microsoftAvatarLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
                    microsoftAvatarLabel.setForeground(Color.GRAY);
                    microsoftAvatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    microsoftAvatarLabel.setIcon(null);

                    addLog("正版登录成功: " + account.username);
                    addLog("UUID: " + account.uuid);

                    HeadService.getHeadFrontAsync(account.uuid, 64, true)
                            .thenAccept(head -> SwingUtilities.invokeLater(() -> {
                                if (head != null) {
                                    ImageIcon icon = new ImageIcon(head);
                                    microsoftAvatarLabel.setIcon(icon);
                                    microsoftAvatarLabel.setText("");
                                    addLog("✓ 头像获取成功");
                                } else {
                                    microsoftAvatarLabel.setText("🎮");
                                    microsoftAvatarLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
                                    microsoftAvatarLabel.setForeground(new Color(0, 120, 215));
                                    addLog("头像获取失败，使用默认图标");
                                }
                            }))
                            .exceptionally(ex -> {
                                SwingUtilities.invokeLater(() -> {
                                    microsoftAvatarLabel.setText("🎮");
                                    microsoftAvatarLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
                                    microsoftAvatarLabel.setForeground(new Color(0, 120, 215));
                                    addLog("头像获取异常: " + ex.getMessage());
                                });
                                return null;
                            });
                });
            }

            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> {
                    microsoftLoginBtn.setVisible(true);
                    microsoftCancelBtn.setVisible(false);
                    microsoftStatusLabel.setText("登录失败");
                    addLog("正版登录失败: " + error);
                });
            }
        };

        authenticator = new MicrosoftAuthenticator(callback);
        authenticator.startLogin();
    }

    private void cancelMicrosoftLogin() {
        if (authenticator != null) {
            authenticator.cancel();
        }
        microsoftLoginBtn.setVisible(true);
        microsoftCancelBtn.setVisible(false);
        microsoftStatusLabel.setText("已取消");
        addLog("已取消正版登录");
    }

    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            outputArea.append("[" + timestamp + "] " + message + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private void loadJavaVersions() {
        addLog("正在搜索 Java...");
        new Thread(() -> {
            List<JavaUtil.JavaInfo> javaList = JavaUtil.findJavaInstallations();
            SwingUtilities.invokeLater(() -> {
                javaPathCombo.removeAllItems();
                for (JavaUtil.JavaInfo info : javaList) {
                    javaPathCombo.addItem(info.path);
                }
                if (javaPathCombo.getItemCount() > 0) {
                    javaPathCombo.setSelectedIndex(0);
                }
                addLog("找到 " + javaList.size() + " 个 Java");

                // 显示推荐 Java 17+ 的提示（Fabric/Forge 需要）
                boolean hasJava17 = javaList.stream().anyMatch(j -> j.version >= 17);
                if (!hasJava17) {
                    addLog("⚠ 提示: 未找到 Java 17+，部分 Mod 可能需要更高版本");
                }
            });
        }).start();
    }

    private void loadGameVersions() {
        addLog("正在加载游戏版本...");
        new Thread(() -> {
            List<String> versions = versionManager.getInstalledVersions();
            SwingUtilities.invokeLater(() -> {
                gameVersionCombo.removeAllItems();
                if (versions.isEmpty()) {
                    gameVersionCombo.addItem("无可用版本");
                    addLog("未找到任何游戏版本");
                } else {
                    for (String v : versions) {
                        gameVersionCombo.addItem(v);
                    }
                    addLog("加载完成，共 " + versions.size() + " 个版本");
                    updateModLoaderStatus();
                }
            });
        }).start();
    }

    /**
     * 更新模组加载器状态显示
     */
    private void updateModLoaderStatus() {
        String selectedVersion = (String) gameVersionCombo.getSelectedItem();
        if (selectedVersion == null || "无可用版本".equals(selectedVersion)) {
            modLoaderStatusLabel.setText("原版 Minecraft");
            modLoaderStatusLabel.setForeground(Color.GRAY);
            return;
        }

        String modLoaderType = versionManager.detectModLoader(selectedVersion);
        switch (modLoaderType) {
            case "fabric":
                modLoaderStatusLabel.setText("🔷 Fabric 模组加载器");
                modLoaderStatusLabel.setForeground(new Color(107, 156, 222));
                addLog("检测到 Fabric 模组加载器");
                break;
            case "forge":
                modLoaderStatusLabel.setText("🟠 Forge 模组加载器");
                modLoaderStatusLabel.setForeground(new Color(222, 107, 107));
                addLog("检测到 Forge 模组加载器");
                break;
            default:
                modLoaderStatusLabel.setText("🟢 原版 Minecraft");
                modLoaderStatusLabel.setForeground(Color.GRAY);
                break;
        }
    }

    /**
     * 验证版本兼容性
     */
    private boolean validateVersionCompatibility(String versionId, String modLoaderType) {
        // Fabric 1.17+ 需要 Java 17
        if ("fabric".equals(modLoaderType)) {
            if (isVersionAtLeast(versionId, 1, 17)) {
                int javaVersion = getSelectedJavaVersion();
                if (javaVersion > 0 && javaVersion < 17) {
                    addLog("⚠ 警告: " + versionId + " 需要 Java 17+，当前使用 Java " + javaVersion);
                    int result = JOptionPane.showConfirmDialog(this,
                            versionId + " 通常需要 Java 17+ 才能运行 Fabric。\n" +
                                    "当前 Java 版本较低，是否继续启动？",
                            "版本兼容性警告",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    return result == JOptionPane.YES_OPTION;
                }
            }
        }

        // Forge 1.18+ 需要 Java 17
        if ("forge".equals(modLoaderType)) {
            if (isVersionAtLeast(versionId, 1, 18)) {
                int javaVersion = getSelectedJavaVersion();
                if (javaVersion > 0 && javaVersion < 17) {
                    addLog("⚠ 警告: " + versionId + " Forge 需要 Java 17+，当前使用 Java " + javaVersion);
                    int result = JOptionPane.showConfirmDialog(this,
                            versionId + " Forge 版本需要 Java 17+ 才能运行。\n" +
                                    "当前 Java 版本较低，是否继续启动？",
                            "版本兼容性警告",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    return result == JOptionPane.YES_OPTION;
                }
            }
        }

        return true;
    }

    /**
     * 检查版本是否大于等于指定版本
     */
    private boolean isVersionAtLeast(String versionId, int major, int minor) {
        try {
            String[] parts = versionId.split("\\.");
            if (parts.length >= 2) {
                int verMajor = Integer.parseInt(parts[0]);
                int verMinor = Integer.parseInt(parts[1]);
                return verMajor > major || (verMajor == major && verMinor >= minor);
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    /**
     * 获取选中的 Java 版本号
     */
    private int getSelectedJavaVersion() {
        String javaPath = (String) javaPathCombo.getSelectedItem();
        if (javaPath == null) {
            return 0;
        }
        return getJavaVersion(javaPath);
    }

    /**
     * 获取 Java 版本号
     */
    private int getJavaVersion(String javaPath) {
        try {
            Process process = new ProcessBuilder(javaPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Pattern pattern = Pattern.compile("version \"(\\d+)");
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                }
            }
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void launchGame() {
        if (isLaunching) {
            addLog("游戏正在启动中，请勿重复点击");
            return;
        }

        String version = (String) gameVersionCombo.getSelectedItem();
        if (version == null || "无可用版本".equals(version)) {
            addLog("请先选择一个有效的游戏版本");
            JOptionPane.showMessageDialog(this, "请先选择一个有效的游戏版本", "启动失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String javaPath = (String) javaPathCombo.getSelectedItem();
        if (javaPath == null || !new File(javaPath).exists()) {
            addLog("请选择有效的 Java 路径");
            JOptionPane.showMessageDialog(this, "请选择有效的 Java 路径", "启动失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 检测模组加载器类型
        String modLoaderType = versionManager.detectModLoader(version);
        addLog("检测到模组加载器: " + ("vanilla".equals(modLoaderType) ? "原版" : modLoaderType));

        // 版本兼容性检查
        if (!validateVersionCompatibility(version, modLoaderType)) {
            addLog("用户取消启动");
            return;
        }

        int minMemory, maxMemory;
        try {
            minMemory = Integer.parseInt(minMemoryText.getText().trim());
            maxMemory = Integer.parseInt(maxMemoryText.getText().trim());

            // 根据模组加载器调整最小内存
            if ("forge".equals(modLoaderType) && minMemory < 1024) {
                minMemory = 1024;
                addLog("Forge 需要更多内存，已调整最小内存为 1024MB");
            }
            if ("fabric".equals(modLoaderType) && minMemory < 768) {
                minMemory = 768;
                addLog("Fabric 推荐最小内存 768MB");
            }
            if (minMemory < 512) minMemory = 512;
            if (maxMemory < 1024) maxMemory = 2048;
            if (maxMemory < minMemory) maxMemory = minMemory + 1024;
        } catch (NumberFormatException e) {
            addLog("内存设置无效，使用默认值");
            minMemory = 512;
            maxMemory = 2048;
        }

        // 准备用户信息
        final String username;
        final String uuid;
        final String accessToken;
        final String userType;

        if (microsoftRadio.isSelected() && microsoftAccount != null) {
            username = microsoftAccount.username;
            uuid = microsoftAccount.uuid;
            accessToken = microsoftAccount.accessToken;
            userType = "msa";
            addLog("正版用户: " + username);
        } else {
            String name = offlineNameText.getText().trim();
            username = name.isEmpty() ? "Player" : name;
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString();
            accessToken = "offline-token";
            userType = "offline";
            addLog("离线用户: " + username);
        }

        final String finalVersion = version;
        final String finalJavaPath = javaPath;
        final int finalMinMemory = minMemory;
        final int finalMaxMemory = maxMemory;

        addLog("========== 开始启动游戏 ==========");
        addLog("版本: " + finalVersion);
        addLog("加载器: " + ("vanilla".equals(modLoaderType) ? "原版" : modLoaderType));
        addLog("Java: " + finalJavaPath);
        addLog("内存: " + finalMinMemory + "MB - " + finalMaxMemory + "MB");

        isLaunching = true;
        launchButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        GameLauncher.Callback callback = new GameLauncher.Callback() {
            @Override
            public void onLog(String message) {
                addLog(message);
            }

            @Override
            public void onProgress(int percent) {
                SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
            }

            @Override
            public void onGameStarted(Process process) {
                SwingUtilities.invokeLater(() -> {
                    addLog("========================================");
                    addLog("游戏已启动，进程ID: " + process.pid());
                    addLog("你可以关闭启动器，游戏将继续运行");
                    addLog("========================================");
                    launchButton.setEnabled(true);
                    progressBar.setVisible(false);
                    isLaunching = false;
                });
            }

            @Override
            public void onGameExited(int exitCode) {
                SwingUtilities.invokeLater(() -> {
                    addLog("游戏已退出，退出码: " + exitCode);
                    launchButton.setEnabled(true);
                    progressBar.setVisible(false);
                    isLaunching = false;
                    if (exitCode != 0) {
                        addLog("游戏异常退出");
                        if (exitCode == 1) {
                            addLog("提示: 检查 Java 版本是否兼容，或尝试增加最大内存");
                        }
                    }
                });
            }
        };

        GameLauncher launcher = new GameLauncher(versionManager, callback);
        new Thread(() -> launcher.launch(finalVersion, finalJavaPath, finalMinMemory, finalMaxMemory,
                username, uuid, accessToken, userType)).start();
    }
}