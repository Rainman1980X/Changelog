package de.burger.it;

import javax.swing.*;
import java.awt.*;

public class MainApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConfigValueModel model = new ConfigValueModel();
            ConfigProcessor processor = new ConfigProcessor(model);
            ConfigJsonMapper mapper = new ConfigJsonMapper();
            ConfigPersistenceBridge bridge = new ConfigPersistenceBridge(mapper, processor);

            // GUI vorbereiten
            JFrame frame = new JFrame("Konfigurationsdialog");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            JPanel panel = new JPanel(new GridLayout(2, 1));
            ConfigBoundTextField field = new ConfigBoundTextField("username", processor);
            panel.add(new JLabel("Username:"));
            panel.add(field);
            frame.add(panel, BorderLayout.CENTER);

            // Buttons
            JButton saveButton = new JButton("Speichern");
            saveButton.addActionListener(e -> {
                try {
                    field.publish();
                    bridge.save("userdialog");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            JButton loadButton = new JButton("Laden");
            loadButton.addActionListener(e -> {
                try {

                    bridge.load("userdialog");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            JPanel buttons = new JPanel();
            buttons.add(loadButton);
            buttons.add(saveButton);
            frame.add(buttons, BorderLayout.SOUTH);

            // Erst jetzt: Laden beim Start
            try {
                bridge.load("userdialog");
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            frame.setSize(400, 150);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

