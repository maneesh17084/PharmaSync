package com.pharmasync.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.pharmasync.model.User;
import com.pharmasync.service.Services;
import com.pharmasync.ui.dialogs.ChangePasswordDialog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;

/** Sign-in window (FR-01): brand panel on the left, the form on the right. */
public class LoginFrame extends JFrame {

    private final JTextField username = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final JButton login = UiUtil.button("Sign in", this::doLogin);

    public LoginFrame() {
        super("PharmaSync - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(Icons.windowIcons());

        JPanel root = new JPanel(new GridLayout(1, 2));
        root.add(brandPanel());
        root.add(formPanel());
        setContentPane(root);
        getRootPane().setDefaultButton(login);
        setSize(920, 580);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private JComponent brandPanel() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0F172A), getWidth(), getHeight(), new Color(0x0F766E)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Soft decorative rings.
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillOval(getWidth() - 220, -120, 360, 360);
                g2.fillOval(-140, getHeight() - 200, 300, 300);
                g2.dispose();
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(36, 48, 40, 40));

        // Tile-less logo (it carries the PharmaSync wordmark) so it blends into the panel's gradient.
        JLabel logo = new JLabel(Icons.logoMark(250));
        logo.setAlignmentX(LEFT_ALIGNMENT);
        JLabel tagline = white("<html>Inventory, GST billing and expiry control<br>for pharmaceutical wholesalers.</html>",
                Font.PLAIN, 1);
        tagline.setForeground(new Color(0xCBD5E1));

        p.add(logo);
        p.add(Box.createVerticalStrut(18));
        p.add(tagline);
        p.add(Box.createVerticalStrut(24));
        for (String feature : new String[]{"Batch-wise stock with FEFO dispatch", "Expiry and low-stock alerts",
                "GST invoices with CGST/SGST or IGST", "Customer payments and full audit trail"}) {
            JLabel f = white(feature, Font.PLAIN, 0.5f);
            f.setIcon(Icons.fixed("check", 16, new Color(0x5EEAD4)));
            f.setIconTextGap(10);
            f.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            p.add(f);
        }
        p.add(Box.createVerticalGlue());
        JLabel foot = white("Secure sign-in  ·  Role-based access  ·  Audit logged", Font.PLAIN, -1.5f);
        foot.setForeground(new Color(0x94A3B8));
        p.add(foot);
        return p;
    }

    private JComponent formPanel() {
        JLabel title = new JLabel("Welcome back");
        title.setFont(Theme.font(Font.BOLD, 11));
        JLabel subtitle = UiUtil.muted("Sign in to continue to your store");
        subtitle.setFont(Theme.font(Font.PLAIN, 1));

        username.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Username");
        username.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("user", 16, Theme::muted));
        password.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Password");
        password.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("lock", 16, Theme::muted));
        password.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        for (JComponent c : new JComponent[]{username, password, login}) {
            c.setPreferredSize(new Dimension(320, 40));
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            c.setAlignmentX(LEFT_ALIGNMENT);
        }
        login.setFont(Theme.font(Font.BOLD, 1));

        JLabel hint = UiUtil.muted("<html>First start: <b>admin</b> / admin123 or <b>staff</b> / staff123."
                + "<br>You will be asked to choose a new password.</html>");
        hint.setFont(Theme.font(Font.PLAIN, -1));
        Card hintCard = new Card(new BorderLayout(), 10);
        hintCard.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        hintCard.add(hint);
        hintCard.setAlignmentX(LEFT_ALIGNMENT);
        hintCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(340, 400));
        for (JComponent c : new JComponent[]{title, subtitle}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
        }
        form.add(title);
        form.add(Box.createVerticalStrut(6));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(30));
        form.add(fieldLabel("Username"));
        form.add(username);
        form.add(Box.createVerticalStrut(16));
        form.add(fieldLabel("Password"));
        form.add(password);
        form.add(Box.createVerticalStrut(24));
        form.add(login);
        form.add(Box.createVerticalStrut(22));
        form.add(hintCard);

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.add(form);
        return wrap;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 0));
        l.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel white(String text, int style, float delta) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, delta));
        l.setForeground(Color.WHITE);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private void doLogin() {
        String name = username.getText();
        String pass = new String(password.getPassword());
        UiUtil.async(this, () -> Services.AUTH.login(name, pass), this::afterLogin);
    }

    private void afterLogin(User user) {
        password.setText("");
        if (user.mustChangePassword() && !ChangePasswordDialog.show(this, true)) {
            Services.AUTH.logout();
            return;
        }
        dispose();
        new MainFrame().setVisible(true);
    }
}
