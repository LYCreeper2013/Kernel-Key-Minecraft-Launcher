package cn.lycreeper.ui;

import cn.lycreeper.ui.panels.*;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {

    public MainWindow() {
        setTitle("KKMCL");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1250, 650);
        setLocationRelativeTo(null);

        // 创建标签页面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("微软雅黑", Font.PLAIN, 13));

        // 添加各个标签页
        tabbedPane.addTab("启动", new LauncherPanel());
        tabbedPane.addTab("下载", new DownloadPanel());
        tabbedPane.addTab("杂志", new KnowledgePanel());
        tabbedPane.addTab("鸣谢", new ThanksPanel());


        // 设置选中第一个标签页
        tabbedPane.setSelectedIndex(0);

        add(tabbedPane);

        // 设置窗口图标（可选）
        try {
            setIconImage(Toolkit.getDefaultToolkit().getImage(
                    getClass().getResource("/icons/minecraft.png")));
        } catch (Exception e) {
            // 忽略图标加载失败
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new MainWindow().setVisible(true);
        });
    }
}