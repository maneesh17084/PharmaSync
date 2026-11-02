package com.pharmasync.ui;

import com.pharmasync.service.BusinessException;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Builds label/field forms and shows them in an OK/Cancel dialog that stays open on validation errors. */
public class FormBuilder {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private int row;

    public FormBuilder() {
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.setOpaque(false);
    }

    public FormBuilder add(String label, JComponent field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.WEST;
        l.insets = new Insets(3, 3, 3, 10);
        panel.add(new JLabel(label), l);

        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row++;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(3, 3, 3, 3);
        panel.add(field, f);
        return this;
    }

    public JPanel build() {
        return panel;
    }

    /**
     * Shows the form; on OK runs onOk. If onOk throws, the error is shown and the form stays open.
     * Returns true when onOk completed successfully.
     */
    public static boolean show(Component parent, String title, JComponent form, Runnable onOk) {
        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }
            try {
                onOk.run();
                return true;
            } catch (BusinessException e) {
                UiUtil.showError(parent, e);
            } catch (RuntimeException e) {
                UiUtil.showError(parent, e);
                return false;
            }
        }
    }
}
