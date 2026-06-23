package cn.lycreeper;

import cn.lycreeper.ui.MainWindow;
import javax.swing.SwingUtilities;

public class Launcher {
    public static void main(String[] args) {
        System.out.println("KKMCL Java 版启动器");
        System.out.println("JDK 版本: " + System.getProperty("java.version"));

        SwingUtilities.invokeLater(() -> {
            new MainWindow().setVisible(true);
        });
    }
}