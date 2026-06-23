package cn.lycreeper.ui.dialogs;

import cn.lycreeper.util.ModrinthService;
import cn.lycreeper.util.ModrinthService.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ModDownloadDialog extends JDialog {

    // Mod 信息
    private final String modName;
    private final String modSlug;
    private final String iconUrl;
    private final String savePath;

    // UI 组件
    private JLabel modIconLabel;
    private JLabel modNameLabel;
    private JLabel modSlugLabel;
    private JLabel savePathLabel;
    private JLabel selectedVersionLabel;
    private JComboBox<String> versionFilterCombo;
    private JComboBox<String> gameVersionFilterCombo;
    private JComboBox<String> loaderFilterCombo;
    private JList<ModVersion> versionList;
    private DefaultListModel<ModVersion> versionListModel;
    private JTextArea versionDetailArea;
    private JButton downloadButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JCheckBox downloadDependenciesCheckbox;

    // 服务
    private ModrinthService modrinthService;
    private List<ModVersion> allVersions;
    private ModVersion selectedVersion;
    private boolean isDownloading = false;

    // 回调
    private DownloadCallback callback;

    public interface DownloadCallback {
        void onDownloadStarted(String fileName);
        void onDownloadProgress(int percent);
        void onDownloadComplete(String filePath);
        void onDownloadFailed(String error);
    }

    public ModDownloadDialog(Window owner, String modName, String modSlug, String iconUrl, String savePath) {
        super(owner, "Mod下载 - " + modName, ModalityType.APPLICATION_MODAL);
        this.modName = modName;
        this.modSlug = modSlug;
        this.iconUrl = iconUrl;
        this.savePath = savePath;

        modrinthService = new ModrinthService();

        initUI();
        loadVersions();

        setSize(750, 650);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    public void setDownloadCallback(DownloadCallback callback) {
        this.callback = callback;
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(248, 249, 250));

        // 头部信息区域
        add(createHeaderPanel(), BorderLayout.NORTH);

        // 中间内容区域
        add(createContentPanel(), BorderLayout.CENTER);

        // 底部状态栏
        add(createStatusPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(new Color(240, 248, 255));
        header.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Mod 图标
        JPanel iconPanel = new JPanel(new BorderLayout());
        iconPanel.setBackground(new Color(240, 248, 255));
        modIconLabel = new JLabel();
        modIconLabel.setPreferredSize(new Dimension(80, 80));
        modIconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        modIconLabel.setVerticalAlignment(SwingConstants.CENTER);
        modIconLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        modIconLabel.setText("🎮");
        modIconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        iconPanel.add(modIconLabel, BorderLayout.CENTER);

        // Mod 信息
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(new Color(240, 248, 255));

        modNameLabel = new JLabel(modName);
        modNameLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        modNameLabel.setForeground(new Color(51, 51, 51));

        modSlugLabel = new JLabel("ID: " + modSlug);
        modSlugLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        modSlugLabel.setForeground(new Color(102, 102, 102));

        savePathLabel = new JLabel("保存路径: " + savePath);
        savePathLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        savePathLabel.setForeground(new Color(102, 102, 102));

        infoPanel.add(modNameLabel);
        infoPanel.add(Box.createVerticalStrut(5));
        infoPanel.add(modSlugLabel);
        infoPanel.add(Box.createVerticalStrut(5));
        infoPanel.add(savePathLabel);

        header.add(iconPanel, BorderLayout.WEST);
        header.add(infoPanel, BorderLayout.CENTER);

        // 加载图标
        loadIcon();

        return header;
    }

    private JPanel createContentPanel() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBackground(new Color(248, 249, 250));
        content.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // 基本信息区域
        JPanel infoPanel = createInfoPanel();

        // 版本列表区域
        JPanel versionPanel = createVersionPanel();

        // 下载选项区域
        JPanel downloadPanel = createDownloadPanel();

        // 组装
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(248, 249, 250));
        topPanel.add(infoPanel, BorderLayout.NORTH);
        topPanel.add(versionPanel, BorderLayout.CENTER);

        content.add(topPanel, BorderLayout.CENTER);
        content.add(downloadPanel, BorderLayout.SOUTH);

        return content;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 221, 221)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 标题
        JLabel titleLabel = new JLabel("Mod 信息");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);

        // Mod Slug
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Mod Slug:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(modSlug), gbc);

        // 保存路径
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("保存路径:"), gbc);
        gbc.gridx = 1;
        JLabel pathValue = new JLabel(savePath);
        pathValue.setForeground(new Color(102, 102, 102));
        panel.add(pathValue, gbc);

        // 当前版本
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("当前版本:"), gbc);
        gbc.gridx = 1;
        selectedVersionLabel = new JLabel("未选择");
        selectedVersionLabel.setForeground(new Color(33, 150, 243));
        selectedVersionLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
        panel.add(selectedVersionLabel, gbc);

        return panel;
    }

    private JPanel createVersionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 221, 221)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // 标题
        JLabel titleLabel = new JLabel("可用版本列表");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        panel.add(titleLabel, BorderLayout.NORTH);

        // 筛选栏
        JPanel filterPanel = createFilterPanel();
        panel.add(filterPanel, BorderLayout.NORTH);

        // 版本列表
        versionListModel = new DefaultListModel<>();
        versionList = new JList<>(versionListModel);
        versionList.setCellRenderer(new VersionListCellRenderer());
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onVersionSelected();
            }
        });
        JScrollPane listScroll = new JScrollPane(versionList);
        listScroll.setPreferredSize(new Dimension(400, 200));
        panel.add(listScroll, BorderLayout.CENTER);

        // 版本详情区域
        JPanel detailPanel = createVersionDetailPanel();
        panel.add(detailPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        panel.add(new JLabel("筛选:"));

        versionFilterCombo = new JComboBox<>(new String[]{"所有版本", "按游戏版本", "按加载器"});
        versionFilterCombo.setPreferredSize(new Dimension(120, 28));
        versionFilterCombo.addActionListener(e -> onFilterChanged());
        panel.add(versionFilterCombo);

        gameVersionFilterCombo = new JComboBox<>();
        gameVersionFilterCombo.setPreferredSize(new Dimension(120, 28));
        gameVersionFilterCombo.setVisible(false);
        gameVersionFilterCombo.addActionListener(e -> applyFilter());
        panel.add(gameVersionFilterCombo);

        loaderFilterCombo = new JComboBox<>(new String[]{"fabric", "forge", "quilt", "neoforge"});
        loaderFilterCombo.setPreferredSize(new Dimension(100, 28));
        loaderFilterCombo.setVisible(false);
        loaderFilterCombo.addActionListener(e -> applyFilter());
        panel.add(loaderFilterCombo);

        return panel;
    }

    private JPanel createVersionDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "版本详情",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 12)
        ));

        versionDetailArea = new JTextArea(4, 40);
        versionDetailArea.setEditable(false);
        versionDetailArea.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        versionDetailArea.setBackground(new Color(245, 245, 245));
        versionDetailArea.setLineWrap(true);
        versionDetailArea.setWrapStyleWord(true);

        panel.add(new JScrollPane(versionDetailArea), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(249, 249, 249));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 221, 221)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // 自动下载前置 Mod 选项
        downloadDependenciesCheckbox = new JCheckBox("自动下载前置 Mod", true);
        downloadDependenciesCheckbox.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        downloadDependenciesCheckbox.setBackground(new Color(249, 249, 249));

        // 下载按钮
        downloadButton = new JButton("⬇️ 下载选中的版本");
        downloadButton.setFont(new Font("微软雅黑", Font.BOLD, 14));
        downloadButton.setBackground(new Color(76, 175, 80));
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setPreferredSize(new Dimension(200, 40));
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> downloadMod());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(new Color(249, 249, 249));
        buttonPanel.add(downloadButton);

        panel.add(downloadDependenciesCheckbox);
        panel.add(Box.createVerticalStrut(10));
        panel.add(buttonPanel);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(245, 245, 245));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(221, 221, 221)),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));

        statusLabel = new JLabel("准备就绪");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(102, 102, 102));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(150, 20));
        progressBar.setVisible(false);

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.EAST);

        return panel;
    }

    private void loadIcon() {
        if (iconUrl != null && !iconUrl.isEmpty()) {
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL(iconUrl);
                    ImageIcon icon = new ImageIcon(url);
                    Image scaledImage = icon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
                    SwingUtilities.invokeLater(() -> {
                        modIconLabel.setIcon(new ImageIcon(scaledImage));
                        modIconLabel.setText("");
                    });
                } catch (Exception e) {
                    System.out.println("加载图标失败: " + e.getMessage());
                }
            }).start();
        }
    }

    private void loadVersions() {
        statusLabel.setText("正在加载版本列表...");
        downloadButton.setEnabled(false);

        modrinthService.getModVersionsAsync(modSlug)
                .thenAccept(versions -> {
                    SwingUtilities.invokeLater(() -> {
                        allVersions = versions;
                        versionListModel.clear();
                        for (ModVersion v : versions) {
                            versionListModel.addElement(v);
                        }

                        // 初始化筛选器
                        initFilters();

                        statusLabel.setText("找到 " + versions.size() + " 个版本");
                        if (!versions.isEmpty()) {
                            versionList.setSelectedIndex(0);
                            downloadButton.setEnabled(true);
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("加载版本列表失败");
                        JOptionPane.showMessageDialog(this,
                                "加载版本列表失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    private void initFilters() {
        if (allVersions == null) return;

        // 获取所有唯一的游戏版本
        List<String> gameVersions = allVersions.stream()
                .flatMap(v -> v.gameVersions.stream())
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.toList());

        gameVersionFilterCombo.removeAllItems();
        for (String v : gameVersions) {
            gameVersionFilterCombo.addItem(v);
        }
    }

    private void onFilterChanged() {
        int selected = versionFilterCombo.getSelectedIndex();

        gameVersionFilterCombo.setVisible(selected == 1);
        loaderFilterCombo.setVisible(selected == 2);

        if (selected == 0) {
            applyFilter();
        }
    }

    private void applyFilter() {
        if (allVersions == null) return;

        int filterType = versionFilterCombo.getSelectedIndex();
        List<ModVersion> filtered = new java.util.ArrayList<>(allVersions);

        if (filterType == 1) {
            String selectedGameVersion = (String) gameVersionFilterCombo.getSelectedItem();
            if (selectedGameVersion != null) {
                filtered = filtered.stream()
                        .filter(v -> v.gameVersions.contains(selectedGameVersion))
                        .collect(Collectors.toList());
            }
        } else if (filterType == 2) {
            String selectedLoader = (String) loaderFilterCombo.getSelectedItem();
            if (selectedLoader != null) {
                filtered = filtered.stream()
                        .filter(v -> v.loaders.contains(selectedLoader))
                        .collect(Collectors.toList());
            }
        }

        versionListModel.clear();
        for (ModVersion v : filtered) {
            versionListModel.addElement(v);
        }

        if (!filtered.isEmpty()) {
            versionList.setSelectedIndex(0);
            statusLabel.setText("找到 " + filtered.size() + " 个版本");
        } else {
            statusLabel.setText("没有符合条件的版本");
            downloadButton.setEnabled(false);
        }
    }

    private void onVersionSelected() {
        selectedVersion = versionList.getSelectedValue();
        if (selectedVersion == null) return;

        // 更新选择的版本显示
        selectedVersionLabel.setText(selectedVersion.name + " (" + selectedVersion.versionNumber + ")");

        // 更新版本详情
        String detail = String.format("""
            版本号: %s
            名称: %s
            发布日期: %s
            支持的 Minecraft 版本: %s
            支持的加载器: %s
            文件列表:
            """,
                selectedVersion.versionNumber,
                selectedVersion.name,
                selectedVersion.datePublished != null ? selectedVersion.datePublished.substring(0, 10) : "未知",
                String.join(", ", selectedVersion.gameVersions),
                String.join(", ", selectedVersion.loaders)
        );

        for (ModFile file : selectedVersion.files) {
            detail += String.format("  • %s (%.2f MB)%s\n",
                    file.filename,
                    file.size / 1024.0 / 1024.0,
                    file.primary ? " [主要]" : "");
        }

        versionDetailArea.setText(detail);
        versionDetailArea.setCaretPosition(0);

        downloadButton.setEnabled(true);
    }

    private void downloadMod() {
        if (selectedVersion == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个版本", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (selectedVersion.files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "该版本没有可下载的文件", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isDownloading = true;
        downloadButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        ModFile primaryFile = selectedVersion.files.stream()
                .filter(f -> f.primary)
                .findFirst()
                .orElse(selectedVersion.files.get(0));

        // 确认下载
        String gameVersions = String.join(", ", selectedVersion.gameVersions);
        String loaders = String.join(", ", selectedVersion.loaders);

        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("即将下载:\n\n版本: %s (%s)\n文件: %s\n大小: %.2f MB\n\n支持的 Minecraft: %s\n支持的加载器: %s\n\n是否继续？",
                        selectedVersion.name,
                        selectedVersion.versionNumber,
                        primaryFile.filename,
                        primaryFile.size / 1024.0 / 1024.0,
                        gameVersions,
                        loaders),
                "确认下载",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            resetDownloadState();
            return;
        }

        statusLabel.setText("正在下载: " + primaryFile.filename);
        if (callback != null) {
            callback.onDownloadStarted(primaryFile.filename);
        }

        // 创建保存目录
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        Path targetPath = Paths.get(savePath, primaryFile.filename);

        // 下载主 Mod
        modrinthService.downloadModAsync(primaryFile.url, targetPath, progress -> {
            SwingUtilities.invokeLater(() -> {
                int percent = (int) progress.percentage;
                progressBar.setValue(percent);
                statusLabel.setText(String.format("下载中: %.1f%% - %s", progress.percentage, progress.status));
                if (callback != null) {
                    callback.onDownloadProgress(percent);
                }
            });
        }).thenAccept(path -> {
            // 下载完成
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("下载完成！");
                progressBar.setValue(100);

                if (callback != null) {
                    callback.onDownloadComplete(path.toString());
                }

                addLog("✓ 下载完成: " + primaryFile.filename);

                // 自动下载前置 Mod
                if (downloadDependenciesCheckbox.isSelected() && selectedVersion.dependencies != null) {
                    downloadDependencies(selectedVersion.dependencies,
                            selectedVersion.gameVersions.isEmpty() ? "1.20.4" : selectedVersion.gameVersions.get(0),
                            selectedVersion.loaders.isEmpty() ? "fabric" : selectedVersion.loaders.get(0));
                } else {
                    showDownloadCompleteDialog(primaryFile.filename);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("下载失败");
                progressBar.setVisible(false);
                if (callback != null) {
                    callback.onDownloadFailed(ex.getMessage());
                }
                addLog("✗ 下载失败: " + ex.getMessage());
                JOptionPane.showMessageDialog(this,
                        "下载失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                resetDownloadState();
            });
            return null;
        });
    }

    private void downloadDependencies(List<Dependency> dependencies, String gameVersion, String loader) {
        List<Dependency> requiredDeps = dependencies.stream()
                .filter(d -> "required".equals(d.dependencyType) && d.projectId != null)
                .collect(Collectors.toList());

        if (requiredDeps.isEmpty()) {
            showDownloadCompleteDialog(null);
            return;
        }

        addLog("检测到 " + requiredDeps.size() + " 个前置 Mod，开始下载...");

        // 逐个下载前置 Mod
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Dependency dep : requiredDeps) {
            future = future.thenCompose(v -> downloadSingleDependency(dep.projectId, gameVersion, loader));
        }

        future.thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                addLog("所有前置 Mod 下载完成！");
                showDownloadCompleteDialog(null);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                addLog("前置 Mod 下载失败: " + ex.getMessage());
                showDownloadCompleteDialog(null);
            });
            return null;
        });
    }

    private CompletableFuture<Void> downloadSingleDependency(String projectId, String gameVersion, String loader) {
        addLog("正在下载前置 Mod: " + projectId);

        return modrinthService.getLatestCompatibleVersionAsync(projectId, gameVersion, loader)
                .thenCompose(version -> {
                    if (version == null || version.files.isEmpty()) {
                        addLog("未找到前置 Mod 的兼容版本: " + projectId);
                        return CompletableFuture.completedFuture(null);
                    }

                    ModFile file = version.files.stream()
                            .filter(f -> f.primary)
                            .findFirst()
                            .orElse(version.files.get(0));

                    Path targetPath = Paths.get(savePath, file.filename);
                    addLog("  下载: " + file.filename);

                    return modrinthService.downloadModAsync(file.url, targetPath, null)
                            .thenAccept(path -> {
                                addLog("  ✓ 前置 Mod 下载完成: " + file.filename);
                            });
                })
                .exceptionally(ex -> {
                    addLog("  ✗ 前置 Mod 下载失败: " + ex.getMessage());
                    return null;
                });
    }

    private void showDownloadCompleteDialog(String fileName) {
        progressBar.setVisible(false);

        int option = JOptionPane.showConfirmDialog(this,
                "下载完成！\n\n是否打开所在文件夹？",
                "下载完成",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE);

        if (option == JOptionPane.YES_OPTION) {
            try {
                Desktop.getDesktop().open(new File(savePath));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        resetDownloadState();
    }

    private void resetDownloadState() {
        isDownloading = false;
        downloadButton.setEnabled(true);
        progressBar.setVisible(false);
        progressBar.setValue(0);
        statusLabel.setText("准备就绪");
    }

    private void addLog(String message) {
        System.out.println("[ModDownload] " + message);
        if (callback != null) {
            callback.onDownloadStarted(message);
        }
    }

    // 版本列表渲染器
    private static class VersionListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModVersion) {
                ModVersion v = (ModVersion) value;
                String gameVersions = v.gameVersions.stream().limit(3).collect(Collectors.joining(", "));
                if (v.gameVersions.size() > 3) gameVersions += "...";
                String loaders = String.join(", ", v.loaders);
                setText(String.format("<html><b>%s</b> (%s)<br><font size='2' color='#666'>%s | %s</font></html>",
                        v.name, v.versionNumber, gameVersions, loaders));
            }
            return this;
        }
    }
}