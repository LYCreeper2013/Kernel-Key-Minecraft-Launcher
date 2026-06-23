package cn.lycreeper.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.lycreeper.ui.dialogs.ModDownloadDialog;
import cn.lycreeper.util.ModpackService;
import cn.lycreeper.util.ModpackService.ModpackInfo;
import cn.lycreeper.util.ModpackService.ModpackVersion;
import cn.lycreeper.util.ModrinthService;
import cn.lycreeper.util.ModrinthService.ModSearchResult;
import cn.lycreeper.util.VersionDownloader;

public class DownloadPanel extends JPanel {

    // ========== UI 组件 ==========
    private JTabbedPane tabbedPane;

    // Minecraft 版本标签页组件
    private JPanel versionContentPanel;
    private JPanel globalLoadingOverlay;
    private JLabel loadingText;

    // 可折叠区域
    private JPanel releasePanel;
    private JPanel snapshotPanel;
    private JPanel otherPanel;
    private JToggleButton releaseToggle;
    private JToggleButton snapshotToggle;
    private JToggleButton otherToggle;
    private JList<String> releaseVersionList;
    private JList<String> snapshotVersionList;
    private JList<String> otherVersionList;
    private DefaultListModel<String> releaseListModel;
    private DefaultListModel<String> snapshotListModel;
    private DefaultListModel<String> otherListModel;

    // 模组标签页组件
    private JTextField modNameField;
    private JButton searchButton;
    private JList<ModSearchResult> modList;
    private DefaultListModel<ModSearchResult> modListModel;
    private JPanel modLoadingOverlay;
    private JPanel emptyStateOverlay;

    // 整合包标签页组件
    private JTextField modpackSearchField;
    private JButton modpackSearchButton;
    private JButton modpackRefreshButton;
    private JPanel modpackResultsPanel;
    private JProgressBar modpackProgressBar;
    private JLabel modpackStatusLabel;
    private JButton modpackCancelButton;
    private JScrollPane modpackScrollPane;

    // 服务
    private ModrinthService modrinthService;
    private VersionDownloader versionDownloader;
    private ModpackService modpackService;
    private boolean isLoadingVersions = false;
    private boolean isSearchingMods = false;
    private boolean isDownloadingModpack = false;
    private String currentModpackSlug;

    // 游戏目录
    private final String gameDir = System.getProperty("user.home") + File.separator + ".minecraft";

    // 版本清单缓存
    private List<VersionManifestEntry> allVersions;

    // 版本清单数据类
    private static class VersionManifestEntry {
        String id;
        String type;
        String url;
        String time;
        String releaseTime;
        String sha1;
        int complianceLevel;
    }

    public DownloadPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(240, 240, 240));

        modrinthService = new ModrinthService();
        modpackService = new ModpackService(gameDir);
        versionDownloader = new VersionDownloader(gameDir);

        initUI();
        loadMinecraftVersions();
    }

    private void initUI() {
        tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.LEFT);
        tabbedPane.setFont(new Font("微软雅黑", Font.PLAIN, 13));

        tabbedPane.addTab("Minecraft", createMinecraftTab());
        tabbedPane.addTab("模组", createModTab());
        tabbedPane.addTab("整合包", createModpackTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ========== Minecraft 版本标签页 ==========
    private JPanel createMinecraftTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 240, 240));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(new Color(240, 240, 240));

        // 加载覆盖层
        globalLoadingOverlay = createLoadingOverlay("加载版本列表中...");
        container.add(globalLoadingOverlay, BorderLayout.CENTER);

        // 版本内容面板
        versionContentPanel = new JPanel();
        versionContentPanel.setLayout(new BoxLayout(versionContentPanel, BoxLayout.Y_AXIS));
        versionContentPanel.setBackground(new Color(240, 240, 240));
        versionContentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        versionContentPanel.setVisible(false);

        // 正式版区域
        releasePanel = createCollapsibleSection("正式版", true);
        releaseToggle = (JToggleButton) ((JPanel) releasePanel.getComponent(0)).getComponent(0);
        releaseListModel = new DefaultListModel<>();
        releaseVersionList = createVersionList(releaseListModel);
        ((JPanel) releasePanel.getComponent(1)).add(new JScrollPane(releaseVersionList));

        // 预览版区域
        snapshotPanel = createCollapsibleSection("预览版", true);
        snapshotToggle = (JToggleButton) ((JPanel) snapshotPanel.getComponent(0)).getComponent(0);
        snapshotListModel = new DefaultListModel<>();
        snapshotVersionList = createVersionList(snapshotListModel);
        ((JPanel) snapshotPanel.getComponent(1)).add(new JScrollPane(snapshotVersionList));

        // 其他版本区域
        otherPanel = createCollapsibleSection("其他版本", true);
        otherToggle = (JToggleButton) ((JPanel) otherPanel.getComponent(0)).getComponent(0);
        otherListModel = new DefaultListModel<>();
        otherVersionList = createVersionList(otherListModel);
        ((JPanel) otherPanel.getComponent(1)).add(new JScrollPane(otherVersionList));

        versionContentPanel.add(releasePanel);
        versionContentPanel.add(Box.createVerticalStrut(12));
        versionContentPanel.add(snapshotPanel);
        versionContentPanel.add(Box.createVerticalStrut(12));
        versionContentPanel.add(otherPanel);

        container.add(versionContentPanel, BorderLayout.CENTER);
        scrollPane.setViewportView(container);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCollapsibleSection(String title, boolean defaultExpanded) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(221, 221, 221)),
                new EmptyBorder(0, 0, 0, 0)
        ));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(248, 249, 250));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JToggleButton toggleButton = new JToggleButton(title, defaultExpanded);
        toggleButton.setFont(new Font("微软雅黑", Font.BOLD, 14));
        toggleButton.setForeground(new Color(51, 51, 51));
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel arrowLabel = new JLabel(defaultExpanded ? "▼" : "▶");
        arrowLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        arrowLabel.setForeground(new Color(102, 102, 102));

        toggleButton.addActionListener(e -> {
            boolean expanded = toggleButton.isSelected();
            arrowLabel.setText(expanded ? "▼" : "▶");
            JPanel content = (JPanel) section.getComponent(1);
            content.setVisible(expanded);
            section.revalidate();
        });

        header.add(toggleButton, BorderLayout.CENTER);
        header.add(arrowLabel, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Color.WHITE);
        content.setVisible(defaultExpanded);
        content.setBorder(new EmptyBorder(0, 0, 8, 0));

        section.add(header, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);

        return section;
    }

    private JList<String> createVersionList(DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        list.setBackground(Color.WHITE);
        list.setBorder(new EmptyBorder(8, 16, 8, 16));
        list.setFixedCellHeight(35);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                onVersionSelected(list.getSelectedValue());
                if (list != releaseVersionList) releaseVersionList.clearSelection();
                if (list != snapshotVersionList) snapshotVersionList.clearSelection();
                if (list != otherVersionList) otherVersionList.clearSelection();
            }
        });
        return list;
    }

    private JPanel createLoadingOverlay(String text) {
        JPanel overlay = new JPanel(new GridBagLayout());
        overlay.setBackground(new Color(255, 255, 255, 200));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(255, 255, 255, 200));

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(50, 50));
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingText = new JLabel(text);
        loadingText.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        loadingText.setForeground(new Color(102, 102, 102));
        loadingText.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(spinner);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(loadingText);

        overlay.add(centerPanel);
        overlay.setVisible(true);

        return overlay;
    }

    private void loadMinecraftVersions() {
        if (isLoadingVersions) return;

        isLoadingVersions = true;
        globalLoadingOverlay.setVisible(true);
        versionContentPanel.setVisible(false);
        loadingText.setText("正在从 Mojang 获取版本列表...");

        new Thread(() -> {
            try {
                String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
                String jsonContent = fetchUrl(manifestUrl);

                JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
                JsonArray versionsArray = root.getAsJsonArray("versions");

                allVersions = new ArrayList<>();
                for (JsonElement elem : versionsArray) {
                    JsonObject obj = elem.getAsJsonObject();
                    VersionManifestEntry entry = new VersionManifestEntry();
                    entry.id = obj.get("id").getAsString();
                    entry.type = obj.get("type").getAsString();
                    entry.url = obj.get("url").getAsString();
                    entry.releaseTime = obj.get("releaseTime").getAsString();
                    if (obj.has("sha1")) entry.sha1 = obj.get("sha1").getAsString();
                    if (obj.has("complianceLevel")) entry.complianceLevel = obj.get("complianceLevel").getAsInt();
                    allVersions.add(entry);
                }

                SwingUtilities.invokeLater(() -> {
                    populateVersionLists();
                    globalLoadingOverlay.setVisible(false);
                    versionContentPanel.setVisible(true);
                });

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    loadingText.setText("加载失败: " + e.getMessage());
                    JOptionPane.showMessageDialog(DownloadPanel.this,
                            "无法加载 Minecraft 版本列表\n请检查网络连接",
                            "加载失败", JOptionPane.ERROR_MESSAGE);
                    globalLoadingOverlay.setVisible(false);
                });
            } finally {
                isLoadingVersions = false;
            }
        }).start();
    }

    private String fetchUrl(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "KKMCL/1.0");

        try (InputStream is = conn.getInputStream()) {
            Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private void populateVersionLists() {
        releaseListModel.clear();
        snapshotListModel.clear();
        otherListModel.clear();

        if (allVersions == null || allVersions.isEmpty()) return;

        List<String> releases = new ArrayList<>();
        List<String> snapshots = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (VersionManifestEntry entry : allVersions) {
            if ("release".equals(entry.type)) {
                releases.add(entry.id);
            } else if ("snapshot".equals(entry.type)) {
                if (!entry.id.contains("potato") && !entry.id.contains("or_b")) {
                    snapshots.add(entry.id);
                } else {
                    others.add(entry.id);
                }
            } else {
                others.add(entry.id);
            }
        }

        releases.sort((a, b) -> compareVersions(b, a));
        snapshots.sort((a, b) -> compareVersions(b, a));
        others.sort((a, b) -> compareVersions(b, a));

        for (String v : releases) releaseListModel.addElement(v);
        for (String v : snapshots) snapshotListModel.addElement(v);
        for (String v : others) otherListModel.addElement(v);

        updateSectionTitle(releaseToggle, "正式版", releaseListModel.size());
        updateSectionTitle(snapshotToggle, "预览版", snapshotListModel.size());
        updateSectionTitle(otherToggle, "其他版本", otherListModel.size());
    }

    private void updateSectionTitle(JToggleButton toggle, String baseTitle, int count) {
        String title = count > 0 ? baseTitle + " (" + count + ")" : baseTitle;
        toggle.setText(title);
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

    private void onVersionSelected(String version) {
        VersionManifestEntry selectedEntry = null;
        for (VersionManifestEntry entry : allVersions) {
            if (entry.id.equals(version)) {
                selectedEntry = entry;
                break;
            }
        }

        if (selectedEntry == null) return;

        String typeText = "release".equals(selectedEntry.type) ? "正式版" :
                ("snapshot".equals(selectedEntry.type) ? "预览版" : "其他");

        int option = JOptionPane.showConfirmDialog(this,
                String.format("是否下载 Minecraft %s？\n\n类型: %s\n发布时间: %s",
                        version, typeText, selectedEntry.releaseTime.substring(0, 10)),
                "确认下载",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option == JOptionPane.YES_OPTION) {
            downloadMinecraftVersion(version);
        }

        releaseVersionList.clearSelection();
        snapshotVersionList.clearSelection();
        otherVersionList.clearSelection();
    }

    private void downloadMinecraftVersion(String version) {
        addLog("开始下载 Minecraft " + version);

        new Thread(() -> {
            versionDownloader.setLogCallback(this::addLog);
            versionDownloader.setProgressCallback(percent -> {});
            versionDownloader.downloadVersion(version, "vanilla");

            SwingUtilities.invokeLater(() -> {

                JOptionPane.showMessageDialog(DownloadPanel.this,
                        "Minecraft " + version + " 下载完成！\n保存位置: " + gameDir + File.separator + "versions",
                        "下载完成", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    // ========== 模组标签页 ==========
    private JPanel createModTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 249, 250));

        JPanel searchPanel = createSearchPanel();
        JPanel listContainer = createModListPanel();

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(listContainer, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 249, 250));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(224, 224, 224)),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(248, 249, 250));

        JLabel titleLabel = new JLabel("搜索 Mod");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(51, 51, 51));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(new Color(248, 249, 250));
        inputPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel("Mod 名称");
        nameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        nameLabel.setPreferredSize(new Dimension(80, 36));

        modNameField = new JTextField();
        modNameField.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        modNameField.setPreferredSize(new Dimension(300, 36));
        modNameField.addActionListener(e -> searchMods());

        searchButton = new JButton("搜索");
        searchButton.setFont(new Font("微软雅黑", Font.BOLD, 13));
        searchButton.setBackground(new Color(0, 120, 215));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFocusPainted(false);
        searchButton.setBorderPainted(false);
        searchButton.setPreferredSize(new Dimension(90, 36));
        searchButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        searchButton.addActionListener(e -> searchMods());

        inputPanel.add(nameLabel, BorderLayout.WEST);
        inputPanel.add(modNameField, BorderLayout.CENTER);
        inputPanel.add(searchButton, BorderLayout.EAST);

        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(inputPanel);

        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createModListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(224, 224, 224)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        panel.setPreferredSize(new Dimension(400, 300));

        modListModel = new DefaultListModel<>();
        modList = new JList<>(modListModel);
        modList.setCellRenderer(new ModListCellRenderer());
        modList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modList.setBackground(Color.WHITE);
        modList.setBorder(new EmptyBorder(8, 8, 8, 8));
        modList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ModSearchResult selected = modList.getSelectedValue();
                    if (selected != null) {
                        openModDownloadDialog(selected);
                    }
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(modList);
        listScroll.setBorder(null);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);

        modLoadingOverlay = createModLoadingOverlay();
        modLoadingOverlay.setVisible(false);

        emptyStateOverlay = createEmptyStateOverlay();
        emptyStateOverlay.setVisible(false);

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(400, 300));

        listScroll.setBounds(0, 0, 400, 300);
        modLoadingOverlay.setBounds(0, 0, 400, 300);
        emptyStateOverlay.setBounds(0, 0, 400, 300);

        layeredPane.add(listScroll, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(modLoadingOverlay, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(emptyStateOverlay, JLayeredPane.PALETTE_LAYER);

        panel.add(layeredPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createModLoadingOverlay() {
        JPanel overlay = new JPanel(new GridBagLayout());
        overlay.setBackground(new Color(255, 255, 255, 200));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(255, 255, 255, 200));

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(50, 50));
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = new JLabel("正在搜索 Mod...");
        label.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        label.setForeground(new Color(102, 102, 102));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(spinner);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(label);

        overlay.add(centerPanel);

        return overlay;
    }

    private JPanel createEmptyStateOverlay() {
        JPanel overlay = new JPanel(new GridBagLayout());
        overlay.setBackground(Color.WHITE);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(Color.WHITE);

        JLabel iconLabel = new JLabel("🔍");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("暂无 Mod");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(153, 153, 153));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hintLabel = new JLabel("请输入 Mod 名称并点击搜索");
        hintLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        hintLabel.setForeground(new Color(204, 204, 204));
        hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(iconLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(hintLabel);

        overlay.add(centerPanel);

        return overlay;
    }

    private void searchMods() {
        String query = modNameField.getText().trim();
        if (query.isEmpty()) {
            addLog("请输入要搜索的 Mod 名称");
            return;
        }

        if (isSearchingMods) {
            addLog("正在搜索中，请稍候...");
            return;
        }

        addLog("正在搜索 Mod: " + query);
        isSearchingMods = true;
        searchButton.setEnabled(false);
        modListModel.clear();
        modLoadingOverlay.setVisible(true);
        emptyStateOverlay.setVisible(false);

        modrinthService.searchModsAsync(query, 30, 0)
                .thenAccept(results -> {
                    SwingUtilities.invokeLater(() -> {
                        isSearchingMods = false;
                        searchButton.setEnabled(true);
                        modLoadingOverlay.setVisible(false);

                        if (results == null || results.isEmpty()) {
                            emptyStateOverlay.setVisible(true);
                            addLog("未找到相关 Mod");
                        } else {
                            for (ModSearchResult mod : results) {
                                modListModel.addElement(mod);
                            }
                            addLog("找到 " + results.size() + " 个 Mod");
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        isSearchingMods = false;
                        searchButton.setEnabled(true);
                        modLoadingOverlay.setVisible(false);
                        emptyStateOverlay.setVisible(true);
                        addLog("搜索失败: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void openModDownloadDialog(ModSearchResult mod) {
        Window window = SwingUtilities.getWindowAncestor(this);
        String modsPath = gameDir + File.separator + "mods";

        File modsDir = new File(modsPath);
        if (!modsDir.exists()) {
            modsDir.mkdirs();
        }

        ModDownloadDialog dialog = new ModDownloadDialog(
                window,
                mod.title,
                mod.slug,
                mod.iconUrl,
                modsPath
        );

        dialog.setDownloadCallback(new ModDownloadDialog.DownloadCallback() {
            @Override
            public void onDownloadStarted(String fileName) {
                addLog("开始下载: " + fileName);
            }

            @Override
            public void onDownloadProgress(int percent) {}

            @Override
            public void onDownloadComplete(String filePath) {
                addLog("下载完成: " + filePath);
            }

            @Override
            public void onDownloadFailed(String error) {
                addLog("下载失败: " + error);
            }
        });

        dialog.setVisible(true);
    }

    // ========== 整合包标签页 ==========
    private JPanel createModpackTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 249, 250));

        // 顶部搜索栏
        JPanel searchPanel = createModpackSearchPanel();
        panel.add(searchPanel, BorderLayout.NORTH);

        // 中间结果区域
        modpackResultsPanel = new JPanel();
        modpackResultsPanel.setLayout(new BoxLayout(modpackResultsPanel, BoxLayout.Y_AXIS));
        modpackResultsPanel.setBackground(new Color(248, 249, 250));

        modpackScrollPane = new JScrollPane(modpackResultsPanel);
        modpackScrollPane.setBorder(BorderFactory.createEmptyBorder());
        modpackScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(modpackScrollPane, BorderLayout.CENTER);

        // 底部进度条
        JPanel bottomPanel = createModpackProgressPanel();
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // 加载热门整合包
        loadPopularModpacks();

        return panel;
    }

    private JPanel createModpackSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 249, 250));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(224, 224, 224)),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(248, 249, 250));

        JLabel titleLabel = new JLabel("整合包下载");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(51, 51, 51));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(new Color(248, 249, 250));
        inputPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel("整合包名称");
        nameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        nameLabel.setPreferredSize(new Dimension(80, 36));

        modpackSearchField = new JTextField();
        modpackSearchField.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        modpackSearchField.setPreferredSize(new Dimension(300, 36));
        modpackSearchField.addActionListener(e -> searchModpacks());

        modpackSearchButton = new JButton("搜索");
        modpackSearchButton.setFont(new Font("微软雅黑", Font.BOLD, 13));
        modpackSearchButton.setBackground(new Color(0, 120, 215));
        modpackSearchButton.setForeground(Color.WHITE);
        modpackSearchButton.setFocusPainted(false);
        modpackSearchButton.setBorderPainted(false);
        modpackSearchButton.setPreferredSize(new Dimension(90, 36));
        modpackSearchButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        modpackSearchButton.addActionListener(e -> searchModpacks());

        modpackRefreshButton = new JButton("热门推荐");
        modpackRefreshButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        modpackRefreshButton.setBackground(new Color(240, 240, 240));
        modpackRefreshButton.setFocusPainted(false);
        modpackRefreshButton.setPreferredSize(new Dimension(90, 36));
        modpackRefreshButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        modpackRefreshButton.addActionListener(e -> loadPopularModpacks());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setBackground(new Color(248, 249, 250));
        buttonPanel.add(modpackSearchButton);
        buttonPanel.add(modpackRefreshButton);

        inputPanel.add(nameLabel, BorderLayout.WEST);
        inputPanel.add(modpackSearchField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(inputPanel);

        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createModpackProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(new Color(248, 249, 250));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 16, 16, 16));

        modpackProgressBar = new JProgressBar(0, 100);
        modpackProgressBar.setStringPainted(true);
        modpackProgressBar.setVisible(false);

        modpackStatusLabel = new JLabel("");
        modpackStatusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        modpackStatusLabel.setForeground(new Color(102, 102, 102));

        modpackCancelButton = new JButton("取消");
        modpackCancelButton.setVisible(false);
        modpackCancelButton.addActionListener(e -> cancelModpackDownload());

        JPanel progressPanel = new JPanel(new BorderLayout(10, 5));
        progressPanel.setBackground(new Color(248, 249, 250));
        progressPanel.add(modpackProgressBar, BorderLayout.CENTER);
        progressPanel.add(modpackCancelButton, BorderLayout.EAST);

        panel.add(progressPanel, BorderLayout.CENTER);
        panel.add(modpackStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadPopularModpacks() {
        showModpackLoading();

        modpackService.getPopularModpacksAsync(50)
                .thenAccept(this::displayModpacks)
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        showModpackError("加载失败: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void searchModpacks() {
        String query = modpackSearchField.getText().trim();
        if (query.isEmpty()) {
            loadPopularModpacks();
            return;
        }

        showModpackLoading();

        modpackService.searchModpacksAsync(query, 50, 0)
                .thenAccept(this::displayModpacks)
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        showModpackError("搜索失败: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void showModpackLoading() {
        SwingUtilities.invokeLater(() -> {
            modpackResultsPanel.removeAll();
            JLabel loadingLabel = new JLabel("加载中...", SwingConstants.CENTER);
            loadingLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            loadingLabel.setForeground(Color.GRAY);
            loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            modpackResultsPanel.add(loadingLabel);
            modpackResultsPanel.revalidate();
            modpackResultsPanel.repaint();
        });
    }

    private void showModpackError(String message) {
        SwingUtilities.invokeLater(() -> {
            modpackResultsPanel.removeAll();
            JLabel errorLabel = new JLabel(message, SwingConstants.CENTER);
            errorLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            errorLabel.setForeground(Color.RED);
            errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            modpackResultsPanel.add(errorLabel);
            modpackResultsPanel.revalidate();
            modpackResultsPanel.repaint();
        });
    }

    private void displayModpacks(List<ModpackInfo> modpacks) {
        SwingUtilities.invokeLater(() -> {
            modpackResultsPanel.removeAll();

            if (modpacks == null || modpacks.isEmpty()) {
                JLabel emptyLabel = new JLabel("没有找到整合包", SwingConstants.CENTER);
                emptyLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                emptyLabel.setForeground(Color.GRAY);
                emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                modpackResultsPanel.add(emptyLabel);
            } else {
                for (ModpackInfo info : modpacks) {
                    modpackResultsPanel.add(createModpackCard(info));
                    modpackResultsPanel.add(Box.createVerticalStrut(10));
                }
            }

            modpackResultsPanel.revalidate();
            modpackResultsPanel.repaint();
        });
    }

    private JPanel createModpackCard(ModpackInfo info) {
        JPanel card = new JPanel(new BorderLayout(10, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(200, 200, 200)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        card.setPreferredSize(new Dimension(0, 130));

        // 左：图标
        JLabel iconLabel = new JLabel("📦");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setPreferredSize(new Dimension(70, 70));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 中：信息
        JPanel infoPanel = new JPanel(new BorderLayout(5, 5));
        infoPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(info.title);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        titleLabel.setForeground(new Color(0, 120, 215));

        JLabel authorLabel = new JLabel("作者: " + (info.author != null ? info.author : "未知"));
        authorLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        authorLabel.setForeground(Color.GRAY);

        JLabel descLabel = new JLabel("<html>" + info.getShortDescription() + "</html>");
        descLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        descLabel.setForeground(new Color(80, 80, 80));

        // 标签行
        JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tagsPanel.setBackground(Color.WHITE);

        if (!info.gameVersions.isEmpty()) {
            tagsPanel.add(createModpackTag(info.gameVersions.get(0), new Color(100, 100, 200)));
        }
        if (!info.loaders.isEmpty()) {
            String loader = info.loaders.get(0);
            Color color = loader.equalsIgnoreCase("fabric") ? new Color(107, 156, 222) : new Color(222, 107, 107);
            tagsPanel.add(createModpackTag(loader, color));
        }
        tagsPanel.add(createModpackTag("📥 " + info.getFormattedDownloads(), new Color(80, 160, 80)));

        infoPanel.add(titleLabel, BorderLayout.NORTH);
        infoPanel.add(authorLabel, BorderLayout.CENTER);
        infoPanel.add(descLabel, BorderLayout.SOUTH);

        // 右：按钮
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(Color.WHITE);

        JButton installBtn = new JButton("📥 安装");
        installBtn.setFont(new Font("微软雅黑", Font.BOLD, 12));
        installBtn.setBackground(new Color(0, 120, 215));
        installBtn.setForeground(Color.WHITE);
        installBtn.setFocusPainted(false);
        installBtn.setBorderPainted(false);
        installBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        installBtn.addActionListener(e -> installModpack(info));

        rightPanel.add(installBtn, BorderLayout.NORTH);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);
        card.add(tagsPanel, BorderLayout.SOUTH);

        return card;
    }

    private JLabel createModpackTag(String text, Color bgColor) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        label.setForeground(bgColor);
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return label;
    }

    private void installModpack(ModpackInfo info) {
        if (isDownloadingModpack) {
            JOptionPane.showMessageDialog(this, "正在下载中，请稍后...", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 先获取版本列表，让用户选择版本
        JDialog loadingDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "获取版本信息", Dialog.ModalityType.APPLICATION_MODAL);
        loadingDialog.setSize(300, 150);
        loadingDialog.setLocationRelativeTo(this);
        JLabel loadingLabel = new JLabel("正在获取版本列表...", SwingConstants.CENTER);
        loadingDialog.add(loadingLabel);

        modpackService.getVersionsAsync(info.slug)
                .thenAccept(versions -> {
                    SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        if (versions == null || versions.isEmpty()) {
                            JOptionPane.showMessageDialog(this,
                                    "未找到可用的版本",
                                    "错误", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        // 显示版本选择对话框
                        showVersionSelectionDialog(info, versions);
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        JOptionPane.showMessageDialog(this,
                                "获取版本失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    private void showVersionSelectionDialog(ModpackInfo info, List<ModpackVersion> versions) {
        // 创建版本选择对话框
        JDialog versionDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "选择版本 - " + info.title, Dialog.ModalityType.APPLICATION_MODAL);
        versionDialog.setSize(500, 400);
        versionDialog.setLocationRelativeTo(this);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (ModpackVersion v : versions) {
            String display = v.versionNumber;
            if (v.gameVersions != null && !v.gameVersions.isEmpty()) {
                display += " (MC: " + String.join(", ", v.gameVersions) + ")";
            }
            if (v.loaders != null && !v.loaders.isEmpty()) {
                display += " [" + String.join(", ", v.loaders) + "]";
            }
            listModel.addElement(display);
        }

        JList<String> versionList = new JList<>(listModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setFont(new Font("微软雅黑", Font.PLAIN, 13));

        JButton installBtn = new JButton("安装");
        installBtn.setEnabled(false);

        versionList.addListSelectionListener(e -> {
            installBtn.setEnabled(versionList.getSelectedValue() != null);
        });

        installBtn.addActionListener(e -> {
            int index = versionList.getSelectedIndex();
            if (index >= 0 && index < versions.size()) {
                ModpackVersion selectedVersion = versions.get(index);
                versionDialog.dispose();
                startInstallation(info, selectedVersion.id);
            }
        });

        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> versionDialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(installBtn);
        buttonPanel.add(cancelBtn);

        versionDialog.setLayout(new BorderLayout());
        versionDialog.add(new JScrollPane(versionList), BorderLayout.CENTER);
        versionDialog.add(buttonPanel, BorderLayout.SOUTH);
        versionDialog.setVisible(true);
    }

    private void startInstallation(ModpackInfo info, String versionId) {
        isDownloadingModpack = true;
        currentModpackSlug = info.slug;

        modpackProgressBar.setVisible(true);
        modpackProgressBar.setValue(0);
        modpackCancelButton.setVisible(true);
        modpackStatusLabel.setText("正在准备安装: " + info.title);

        modpackService.installModpackAsync(info.slug, versionId,
                        this::onModpackLog,
                        this::onModpackProgress)
                .thenRun(() -> {
                    SwingUtilities.invokeLater(() -> {
                        modpackStatusLabel.setText("✅ 安装完成: " + info.title);
                        modpackProgressBar.setVisible(false);
                        modpackCancelButton.setVisible(false);
                        isDownloadingModpack = false;

                        JOptionPane.showMessageDialog(DownloadPanel.this,
                                "整合包 \"" + info.title + "\" 安装完成！\n\n请在「启动」标签页中选择对应的版本启动游戏。",
                                "安装成功", JOptionPane.INFORMATION_MESSAGE);
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        modpackStatusLabel.setText("❌ 安装失败: " + ex.getMessage());
                        modpackProgressBar.setVisible(false);
                        modpackCancelButton.setVisible(false);
                        isDownloadingModpack = false;

                        JOptionPane.showMessageDialog(DownloadPanel.this,
                                "安装失败: " + ex.getMessage(),
                                "安装失败", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    private void onModpackLog(String message) {
        SwingUtilities.invokeLater(() -> {
            modpackStatusLabel.setText(message);
            addLog("[整合包] " + message);
        });
    }

    private void onModpackProgress(int percent) {
        SwingUtilities.invokeLater(() -> {
            modpackProgressBar.setValue(percent);
            modpackProgressBar.setString(percent + "%");
        });
    }

    private void cancelModpackDownload() {
        isDownloadingModpack = false;
        modpackProgressBar.setVisible(false);
        modpackCancelButton.setVisible(false);
        modpackStatusLabel.setText("已取消下载");
        addLog("已取消整合包下载");
    }

    // ========== 辅助方法 ==========
    private void addLog(String message) {
        System.out.println("[DownloadPanel] " + message);
    }

    // ========== Mod 列表渲染器 ==========
    private static class ModListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof ModSearchResult) {
                ModSearchResult mod = (ModSearchResult) value;
                setText(mod.title);
                setFont(new Font("微软雅黑", Font.PLAIN, 14));
                setBorder(new EmptyBorder(12, 16, 12, 16));
                setIconTextGap(12);

                if (isSelected) {
                    setBackground(new Color(227, 242, 253));
                } else {
                    setBackground(Color.WHITE);
                }
            }

            return this;
        }
    }
}