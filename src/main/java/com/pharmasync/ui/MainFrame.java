package com.pharmasync.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.pharmasync.db.DatabaseManager;
import com.pharmasync.model.User;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;
import com.pharmasync.ui.dialogs.ChangePasswordDialog;
import com.pharmasync.ui.panels.AlertsPanel;
import com.pharmasync.ui.panels.AuditPanel;
import com.pharmasync.ui.panels.BatchPanel;
import com.pharmasync.ui.panels.CategoryPanel;
import com.pharmasync.ui.panels.CustomerPanel;
import com.pharmasync.ui.panels.DashboardPanel;
import com.pharmasync.ui.panels.MedicinePanel;
import com.pharmasync.ui.panels.PurchasePanel;
import com.pharmasync.ui.panels.ReportsPanel;
import com.pharmasync.ui.panels.SalesHistoryPanel;
import com.pharmasync.ui.panels.SalesPanel;
import com.pharmasync.ui.panels.SettingsPanel;
import com.pharmasync.ui.panels.SupplierPanel;
import com.pharmasync.ui.panels.UsersPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Main window: role-filtered sidebar navigation, a top bar with breadcrumb and account menu, screens in the middle (FR-02). */
public class MainFrame extends JFrame {

    private record Screen(String title, String section, String icon, boolean adminOnly, Supplier<JComponent> factory) {
    }

    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, JComponent> created = new HashMap<>();
    private final Map<String, JToggleButton> navButtons = new HashMap<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel crumbSection = UiUtil.muted("");
    private final JLabel crumbPage = new JLabel();
    private String current;

    public MainFrame() {
        super("PharmaSync - Wholesale Medical Store Inventory");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(Icons.windowIcons());
        setMinimumSize(new Dimension(1180, 720));

        screens.put("dashboard", new Screen("Dashboard", "Overview", "dashboard", false, () -> new DashboardPanel(this)));
        screens.put("alerts", new Screen("Stock Alerts", "Overview", "alert", false, AlertsPanel::new));
        screens.put("sales", new Screen("New Sales Order", "Sales", "cart", false, SalesPanel::new));
        screens.put("salesHistory", new Screen("Invoices", "Sales", "invoice", false, SalesHistoryPanel::new));
        screens.put("customers", new Screen("Customers", "Sales", "users", false, CustomerPanel::new));
        screens.put("medicines", new Screen("Medicines", "Inventory", "pill", false, MedicinePanel::new));
        screens.put("batches", new Screen("Batches & Expiry", "Inventory", "layers", false, BatchPanel::new));
        screens.put("categories", new Screen("Categories", "Inventory", "tag", true, CategoryPanel::new));
        screens.put("purchases", new Screen("Purchases", "Purchasing", "truck", false, PurchasePanel::new));
        screens.put("suppliers", new Screen("Suppliers", "Purchasing", "building", true, SupplierPanel::new));
        screens.put("reports", new Screen("Reports", "Administration", "chart", true, ReportsPanel::new));
        screens.put("users", new Screen("Users", "Administration", "shield", true, UsersPanel::new));
        screens.put("audit", new Screen("Audit Log", "Administration", "clipboard", true, AuditPanel::new));
        screens.put("settings", new Screen("Settings", "Administration", "settings", true, SettingsPanel::new));

        content.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 10));
        JPanel main = new JPanel(new BorderLayout());
        main.add(buildTopBar(), BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);
        main.add(buildStatusBar(), BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(main, BorderLayout.CENTER);
        pack();
        setSize(1360, 840);
        setLocationRelativeTo(null);
        navigate("dashboard");
    }

    public void navigate(String key) {
        Screen screen = screens.get(key);
        if (screen == null || screen.adminOnly() && !Session.isAdmin()) {
            return;
        }
        JComponent panel = created.computeIfAbsent(key, k -> {
            JComponent c = screen.factory().get();
            content.add(c, k);
            return c;
        });
        current = key;
        cards.show(content, key);
        JToggleButton b = navButtons.get(key);
        if (b != null) {
            b.setSelected(true);
        }
        crumbSection.setText(screen.section() + "   /");
        crumbPage.setText(screen.title());
        if (panel instanceof Refreshable r) {
            r.refresh();
        }
    }

    // ---- sidebar ----

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.color("sidebar.background", new Color(0x0F172A)));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        side.setPreferredSize(new Dimension(236, 0));

        JLabel brand = new JLabel("PharmaSync", Icons.logo(36), SwingConstants.LEFT);
        brand.setIconTextGap(10);
        brand.setFont(Theme.font(Font.BOLD, 6));
        brand.setForeground(Color.WHITE);
        JLabel tagline = new JLabel("Wholesale Pharma Inventory");
        tagline.setFont(Theme.font(Font.PLAIN, -1));
        tagline.setForeground(Theme.color("sidebar.section", new Color(0x64748B)));
        tagline.setBorder(BorderFactory.createEmptyBorder(0, 44, 0, 0));
        JPanel brandBox = new JPanel(new BorderLayout(0, 0));
        brandBox.setOpaque(false);
        brandBox.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 12));
        brandBox.add(brand, BorderLayout.NORTH);
        brandBox.add(tagline, BorderLayout.SOUTH);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
        ButtonGroup group = new ButtonGroup();
        String section = null;
        for (Map.Entry<String, Screen> e : screens.entrySet()) {
            Screen s = e.getValue();
            if (s.adminOnly() && !Session.isAdmin()) {
                continue;
            }
            if (!s.section().equals(section)) {
                section = s.section();
                JLabel l = new JLabel(section.toUpperCase(Locale.ROOT));
                l.setFont(Theme.font(Font.BOLD, -2));
                l.setForeground(Theme.color("sidebar.section", new Color(0x64748B)));
                l.setBorder(BorderFactory.createEmptyBorder(12, 10, 5, 0));
                l.setAlignmentX(LEFT_ALIGNMENT);
                nav.add(l);
            }
            NavButton b = new NavButton(s.title(), s.icon());
            b.addActionListener(ev -> navigate(e.getKey()));
            group.add(b);
            navButtons.put(e.getKey(), b);
            nav.add(b);
            nav.add(Box.createVerticalStrut(2));
        }
        JScrollPane navScroll = new JScrollPane(nav);
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);
        navScroll.setBorder(null);
        navScroll.getVerticalScrollBar().setUnitIncrement(16);
        navScroll.putClientProperty(FlatClientProperties.SCROLL_PANE_SMOOTH_SCROLLING, true);

        side.add(brandBox, BorderLayout.NORTH);
        side.add(navScroll, BorderLayout.CENTER);
        side.add(buildUserCard(), BorderLayout.SOUTH);
        return side;
    }

    private JComponent buildUserCard() {
        User u = Session.current();
        JLabel name = new JLabel(u.fullName());
        name.setFont(Theme.font(Font.BOLD, 0));
        name.setForeground(Color.WHITE);
        JLabel role = new JLabel(u.username() + "  ·  " + (u.role().name().equals("ADMIN") ? "Administrator" : "Staff"));
        role.setFont(Theme.font(Font.PLAIN, -1));
        role.setForeground(Theme.color("sidebar.text", new Color(0xCBD5E1)));
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(role, BorderLayout.SOUTH);

        JButton logout = new JButton(Icons.fixed("logout", 18, new Color(0xCBD5E1)));
        logout.setToolTipText("Log out");
        logout.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        logout.putClientProperty(FlatClientProperties.STYLE, "toolbar.hoverBackground: #1E293B; toolbar.pressedBackground: #334155");
        logout.addActionListener(e -> logout());

        JPanel card = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.color("sidebar.divider", new Color(0x1E293B)));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 10));
        card.add(new Avatar(u.fullName(), 36), BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        card.add(logout, BorderLayout.EAST);
        return card;
    }

    // ---- top bar & status bar ----

    private JComponent buildTopBar() {
        crumbPage.setFont(Theme.font(Font.BOLD, 1));
        JPanel crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        crumbs.setOpaque(false);
        crumbs.add(crumbSection);
        crumbs.add(crumbPage);

        JButton theme = toolbarButton(Theme.isDark() ? "sun" : "moon", "Switch light / dark theme");
        theme.addActionListener(e -> {
            Theme.toggle();
            theme.setIcon(Icons.get(Theme.isDark() ? "sun" : "moon", 18, Theme::muted));
            if (current != null && created.get(current) instanceof Refreshable r) {
                r.refresh();
            }
        });
        JButton alerts = toolbarButton("alert", "Stock alerts");
        alerts.addActionListener(e -> navigate("alerts"));

        User u = Session.current();
        JButton account = new JButton(u.fullName(), Icons.get("chevron-down", 14, Theme::muted));
        account.setHorizontalTextPosition(SwingConstants.LEFT);
        account.setIconTextGap(6);
        account.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem pw = new JMenuItem("Change password", Icons.get("key", 16));
        pw.addActionListener(e -> {
            if (ChangePasswordDialog.show(this, false)) {
                UiUtil.info(this, "Password changed.");
            }
        });
        JMenuItem themeItem = new JMenuItem("Switch light / dark", Icons.get("moon", 16));
        themeItem.addActionListener(e -> theme.doClick());
        JMenuItem out = new JMenuItem("Log out", Icons.get("logout", 16));
        out.addActionListener(e -> logout());
        menu.add(pw);
        menu.add(themeItem);
        menu.addSeparator();
        menu.add(out);
        account.addActionListener(e -> menu.show(account, 0, account.getHeight() + 2));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(alerts);
        right.add(theme);
        right.add(Box.createHorizontalStrut(6));
        right.add(new Avatar(u.fullName(), 28));
        right.add(account);

        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.color("topbar.background", Color.WHITE));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.color("topbar.border", new Color(0xE3E8EF)));
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 14));
        bar.add(crumbs, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildStatusBar() {
        JLabel db = UiUtil.muted("SQLite  " + DatabaseManager.config().getFile());
        db.setIcon(Icons.get("database", 13, Theme::muted));
        db.setFont(Theme.font(Font.PLAIN, -1));
        JLabel date = UiUtil.muted(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH)));
        date.setFont(Theme.font(Font.PLAIN, -1));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 22, 6, 18));
        bar.add(db, BorderLayout.WEST);
        bar.add(date, BorderLayout.EAST);
        return bar;
    }

    private static JButton toolbarButton(String icon, String tip) {
        JButton b = new JButton(Icons.get(icon, 18, Theme::muted));
        b.setToolTipText(tip);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void logout() {
        Services.AUTH.logout();
        dispose();
        new LoginFrame().setVisible(true);
    }

    // ---- custom painted widgets ----

    /** Sidebar entry: icon + label, rounded highlight on hover and an accent bar when selected. */
    private static final class NavButton extends JToggleButton {
        private static final Color TEXT = new Color(0xCBD5E1);

        NavButton(String text, String icon) {
            super(text);
            setIcon(Icons.fixed(icon, 18, new Color(0x94A3B8)));
            setSelectedIcon(Icons.fixed(icon, 18, Color.WHITE));
            setRolloverIcon(Icons.fixed(icon, 18, Color.WHITE));
            setRolloverSelectedIcon(Icons.fixed(icon, 18, Color.WHITE));
            setIconTextGap(12);
            setHorizontalAlignment(SwingConstants.LEFT);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
            setPreferredSize(new Dimension(200, 35));
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
            // Text style follows selection and hover; set on state change, never while painting.
            getModel().addChangeListener(e -> applyState());
            applyState();
        }

        private void applyState() {
            boolean active = isSelected() || getModel().isRollover();
            Font font = Theme.font(isSelected() ? Font.BOLD : Font.PLAIN, 0.5f);
            if (!font.equals(getFont())) {
                setFont(font);
            }
            setForeground(active ? Color.WHITE : TEXT);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setContentAreaFilled(false);
            setBorderPainted(false);
            if (getModel() != null) {
                applyState();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            if (isSelected()) {
                g2.setColor(Theme.color("sidebar.selected", new Color(0x134E4A)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(0x2DD4BF));
                g2.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);
            } else if (hover) {
                g2.setColor(Theme.color("sidebar.hover", new Color(0x1E293B)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Round badge with the user's initials. */
    private static final class Avatar extends JComponent {
        private final String initials;
        private final int size;

        Avatar(String name, int size) {
            this.size = size;
            String[] parts = name.trim().split("\\s+");
            String first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
            String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
            this.initials = (first + last).toUpperCase(Locale.ROOT);
            setPreferredSize(new Dimension(size, size));
            setToolTipText(name);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = (getHeight() - size) / 2;
            g2.setPaint(new java.awt.GradientPaint(0, y, new Color(0x14B8A6), size, y + size, new Color(0x0F766E)));
            g2.fillOval(0, y, size, size);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(Font.BOLD, size > 30 ? 0 : -2));
            var fm = g2.getFontMetrics();
            g2.drawString(initials, (size - fm.stringWidth(initials)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
