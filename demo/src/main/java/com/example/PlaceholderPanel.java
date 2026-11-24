package com.example;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;

/**
 * A stand-in screen that says what will go here. Each one gets replaced by a
 * real panel later.
 */
public class PlaceholderPanel extends JPanel {

    public PlaceholderPanel(String title, String... plannedFeatures) {
        setLayout(new GridBagLayout());

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(heading);

        JLabel soon = new JLabel("Include:");
        soon.setForeground(Color.GRAY);
        soon.setAlignmentX(Component.LEFT_ALIGNMENT);
        soon.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
        box.add(soon);

        for (String f : plannedFeatures) {
            JLabel item = new JLabel("•  " + f);
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(item);
        }
        add(box);
    }
}
