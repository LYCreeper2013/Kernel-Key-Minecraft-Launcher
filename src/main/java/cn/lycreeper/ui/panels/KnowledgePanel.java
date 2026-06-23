package cn.lycreeper.ui.panels;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

public class KnowledgePanel extends JPanel {

    public KnowledgePanel() {
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
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // 标题
        JLabel titleLabel = new JLabel("MINECRAFT 知识百科", SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 28));
        titleLabel.setForeground(new Color(52, 73, 94));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);

        panel.add(Box.createVerticalStrut(10));

        JLabel subtitleLabel = new JLabel("探索方块世界的无限可能", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(149, 165, 166));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(subtitleLabel);

        panel.add(Box.createVerticalStrut(30));

        // 游戏简介卡片
        panel.add(createIntroCard());
        panel.add(Box.createVerticalStrut(20));

        // 核心知识区域
        panel.add(createKnowledgeSection());
        panel.add(Box.createVerticalStrut(20));

        // 新手技巧
        panel.add(createTipsSection());
        panel.add(Box.createVerticalStrut(20));

        // 资源链接
        panel.add(createLinksSection());

        return panel;
    }

    private JPanel createIntroCard() {
        JPanel card = new JPanel(new BorderLayout(15, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        // 图标
        JLabel iconLabel = new JLabel("🎮", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setPreferredSize(new Dimension(80, 80));

        // 文字
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(Color.WHITE);

        JLabel nameLabel = new JLabel("Minecraft 沙盒建造游戏");
        nameLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        nameLabel.setForeground(new Color(52, 152, 219));

        JLabel descLabel = new JLabel("由Mojang Studios开发，玩家可以在方块世界中探索、采集、建造和生存。");
        descLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        descLabel.setForeground(new Color(127, 140, 141));

        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(descLabel);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createKnowledgeSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titleLabel = new JLabel("核心知识");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLabel);

        section.add(Box.createVerticalStrut(15));

        JPanel gridPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        gridPanel.setBackground(Color.WHITE);

        gridPanel.add(createKnowledgeCard("生物分类",
                "• 友好：村民、动物\n• 敌对：僵尸、苦力怕\n• Boss：末影龙、凋灵"));
        gridPanel.add(createKnowledgeCard("基础合成",
                "• 工作台：4木板\n• 熔炉：8圆石\n• 铁剑：2铁锭+1木棍"));
        gridPanel.add(createKnowledgeCard("游戏维度",
                "• 主世界：出生点\n• 下界：危险区域\n• 末地：最终挑战"));

        section.add(gridPanel);

        return section;
    }

    private JPanel createKnowledgeCard(String title, String content) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(245, 245, 245));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel contentLabel = new JLabel(content);
        contentLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        contentLabel.setForeground(new Color(127, 140, 141));
        contentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(contentLabel);

        return card;
    }

    private JPanel createTipsSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titleLabel = new JLabel("新手实用技巧");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLabel);

        section.add(Box.createVerticalStrut(15));

        JPanel gridPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        gridPanel.setBackground(Color.WHITE);

        gridPanel.add(createTipCard("第一天", "优先收集木头，晚上前建造庇护所"));
        gridPanel.add(createTipCard("挖矿", "矿洞探索带足火把和食物，注意岩浆"));

        section.add(gridPanel);
        section.add(Box.createVerticalStrut(10));

        JPanel gridPanel2 = new JPanel(new GridLayout(1, 2, 15, 0));
        gridPanel2.setBackground(Color.WHITE);

        gridPanel2.add(createTipCard("交易", "与村民交易获得稀有物品"));
        gridPanel2.add(createTipCard("红石", "红石可用于自动化装置"));

        section.add(gridPanel2);

        return section;
    }

    private JPanel createTipCard(String title, String content) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(249, 249, 249));
        card.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        titleLabel.setForeground(new Color(52, 73, 94));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel contentLabel = new JLabel(content);
        contentLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        contentLabel.setForeground(new Color(127, 140, 141));
        contentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(contentLabel);

        return card;
    }

    private JPanel createLinksSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titleLabel = new JLabel("更多资源");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLabel);

        section.add(Box.createVerticalStrut(15));

        JPanel linksPanel = new JPanel(new GridLayout(2, 3, 15, 15));
        linksPanel.setBackground(Color.WHITE);

        linksPanel.add(createLinkButton("MC百科", "中文Mod百科", "https://www.mcmod.cn"));
        linksPanel.add(createLinkButton("Minecraft Wiki", "官方维基百科", "https://minecraft.wiki"));
        linksPanel.add(createLinkButton("Minecraft官网", "官方网站", "https://www.minecraft.net"));
        linksPanel.add(createLinkButton("Modrinth", "现代Mod平台", "https://modrinth.com"));
        linksPanel.add(createLinkButton("CurseForge", "Mod资源平台", "https://www.curseforge.com/minecraft"));

        section.add(linksPanel);

        return section;
    }

    private JPanel createLinkButton(String title, String subtitle, String url) {
        JPanel button = new JPanel();
        button.setLayout(new BoxLayout(button, BoxLayout.Y_AXIS));
        button.setBackground(new Color(240, 248, 255));
        button.setBorder(BorderFactory.createLineBorder(new Color(52, 152, 219)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(220, 240, 255));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(240, 248, 255));
            }
        });

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        titleLabel.setForeground(new Color(52, 152, 219));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(subtitle, SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        subtitleLabel.setForeground(new Color(127, 140, 141));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        button.add(Box.createVerticalStrut(12));
        button.add(titleLabel);
        button.add(Box.createVerticalStrut(5));
        button.add(subtitleLabel);
        button.add(Box.createVerticalStrut(12));

        return button;
    }
}