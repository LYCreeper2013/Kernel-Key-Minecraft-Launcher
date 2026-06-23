package cn.lycreeper.ui.panels;

import javax.swing.*;
import java.awt.*;

public class ThanksPanel extends JPanel {

    public ThanksPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(245, 247, 250));

        JScrollPane scrollPane = new JScrollPane(createContentPanel());
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(245, 247, 250));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 30, 30));

        // 标题
        JLabel titleLabel = new JLabel("鸣 谢", SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 32));
        titleLabel.setForeground(new Color(44, 62, 80));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);

        panel.add(Box.createVerticalStrut(30));

        // 开发者区域
        panel.add(createDeveloperCard());
        panel.add(Box.createVerticalStrut(20));

        // 开源项目区域
        panel.add(createOpenSourceCard());
        panel.add(Box.createVerticalStrut(20));

        // 特别鸣谢
        panel.add(createSpecialThanksCard());
        panel.add(Box.createVerticalStrut(20));

        // 版权信息
        JLabel copyrightLabel = new JLabel("Copyright © 2026 LYCreeper | 项目 KKMCL", SwingConstants.CENTER);
        copyrightLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        copyrightLabel.setForeground(new Color(189, 195, 199));
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(copyrightLabel);

        return panel;
    }

    private JPanel createDeveloperCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titleLabel = new JLabel("核心开发者");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        card.add(Box.createVerticalStrut(10));

        JPanel devPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        devPanel.setBackground(new Color(240, 244, 248));
        devPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel devLabel = new JLabel("LYCreeper - 核心开发、架构设计");
        devLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        devLabel.setForeground(new Color(44, 62, 80));
        devPanel.add(devLabel);

        card.add(devPanel);

        return card;
    }

    private JPanel createOpenSourceCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titleLabel = new JLabel("使用的开源项目");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        card.add(Box.createVerticalStrut(10));

        card.add(createProjectItem("Java 第三方库", "Gson - JSON 解析库"));
        card.add(Box.createVerticalStrut(8));
        card.add(createProjectItem("Microsoft 登录", "OAuth 2.0 设备码流程"));
        card.add(Box.createVerticalStrut(8));
        card.add(createProjectItem("Mod 平台", "Modrinth API 集成"));

        return card;
    }

    private JPanel createProjectItem(String category, String name) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(new Color(248, 249, 250));
        item.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel categoryLabel = new JLabel(category);
        categoryLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        categoryLabel.setForeground(new Color(127, 140, 141));
        categoryLabel.setPreferredSize(new Dimension(100, 20));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        nameLabel.setForeground(new Color(44, 62, 80));

        item.add(categoryLabel, BorderLayout.WEST);
        item.add(nameLabel, BorderLayout.CENTER);

        return item;
    }

    private JPanel createSpecialThanksCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titleLabel = new JLabel("特别鸣谢");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(new Color(231, 76, 60));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        card.add(Box.createVerticalStrut(10));

        String[] thanks = {
                "• IntelliJ IDEA - 提供开发工具支持",
                "• Minecraft 社区 - 提供技术参考",
                "• 参与测试的 KKMCL 用户 - 的支持与反馈"
        };

        for (String thank : thanks) {
            JLabel label = new JLabel(thank);
            label.setFont(new Font("微软雅黑", Font.PLAIN, 13));
            label.setForeground(new Color(127, 140, 141));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(label);
            card.add(Box.createVerticalStrut(5));
        }

        return card;
    }
}