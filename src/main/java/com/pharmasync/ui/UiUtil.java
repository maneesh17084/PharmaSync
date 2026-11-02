package com.pharmasync.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.pharmasync.db.DataAccessException;
import com.pharmasync.model.IndianState;
import com.pharmasync.service.BusinessException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.AbstractBorder;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared helpers for dialogs, parsing, formatting and background loading. */
public final class UiUtil {

    public static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MM/yyyy");

    private UiUtil() {
    }

    // ---- errors ----

    public static void showError(Component parent, Throwable t) {
        Throwable e = t;
        while ((e instanceof ExecutionException || e instanceof RuntimeException && !(e instanceof BusinessException)
                && !(e instanceof DataAccessException)) && e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof BusinessException) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Cannot continue", JOptionPane.WARNING_MESSAGE);
        } else if (e instanceof DataAccessException) {
            JOptionPane.showMessageDialog(parent, "Database error:\n" + e.getMessage(), "Database error",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, "Unexpected error:\n" + e, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Runs an action and shows any failure. Returns true on success. */
    public static boolean attempt(Component parent, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            showError(parent, e);
            return false;
        }
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Please confirm", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "PharmaSync", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- background work ----

    /** Runs work off the event thread, then hands the result to done on the event thread. */
    public static <T> void async(Component c, Supplier<T> work, Consumer<T> done) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                c.setCursor(Cursor.getDefaultCursor());
                try {
                    done.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(c, e.getCause());
                }
            }
        }.execute();
    }

    /** Calls action 300 ms after the user stops typing. */
    public static void onTextChange(JTextField field, Runnable action) {
        Timer timer = new Timer(300, e -> action.run());
        timer.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { timer.restart(); }
            public void removeUpdate(DocumentEvent e) { timer.restart(); }
            public void changedUpdate(DocumentEvent e) { timer.restart(); }
        });
    }

    // ---- parsing & formatting ----

    public static String money(BigDecimal v) {
        return v == null ? "0.00" : String.format("%,.2f", v);
    }

    public static BigDecimal parseDecimal(String text, String field) {
        try {
            BigDecimal v = new BigDecimal(text.trim().replace(",", ""));
            if (v.signum() < 0) {
                throw new BusinessException(field + " must not be negative");
            }
            return v;
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(field + " must be a number");
        }
    }

    public static int parseInt(String text, String field) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(field + " must be a whole number");
        }
    }

    /**
     * Accepts yyyy-MM-dd, or MM/yyyy as printed on medicine packs. For MM/yyyy the first day of the
     * month is used for manufacturing dates and the last day for expiry dates.
     */
    public static LocalDate parseDate(String text, String field, boolean endOfMonth) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            if (t.matches("\\d{1,2}/\\d{4}")) {
                YearMonth ym = YearMonth.parse(t.length() == 6 ? "0" + t : t, MONTH_YEAR);
                return endOfMonth ? ym.atEndOfMonth() : ym.atDay(1);
            }
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            throw new BusinessException(field + " must be yyyy-mm-dd or mm/yyyy");
        }
    }

    /** State picker for GST; empty selection when the state is not known yet. */
    public static JComboBox<IndianState> stateCombo(IndianState selected) {
        JComboBox<IndianState> combo = new JComboBox<>(IndianState.values());
        combo.setMaximumRowCount(16);
        combo.setSelectedItem(selected);
        if (selected == null) {
            combo.setSelectedIndex(-1);
        }
        return combo;
    }

    public static String blankToNull(String s) {

        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---- small widgets ----

    /** Labels of each screen's main action, drawn as filled accent buttons. */
    private static final Set<String> PRIMARY = Set.of("Sign in", "Confirm order & generate invoice", "Save settings",
            "Run", "Add medicine", "Register customer", "Add supplier", "Add user", "Add category",
            "New purchase order", "Create purchase order for all", "Add to order", "Add line");
    /** Destructive actions, drawn with red text. */
    private static final Set<String> DANGER = Set.of("Delete", "Dispose selected", "Cancel order");

    /** First matching keyword (in order) gives a button its icon. */
    private static final String[][] BUTTON_ICONS = {
            {"export", "download"}, {"payment history", "clock"}, {"record payment", "card"},
            {"pdf", "pdf"}, {"receive", "package"}, {"record received", "package"}, {"return", "undo"},
            {"quarantine", "ban"}, {"dispose", "trash"}, {"delete", "trash"}, {"remove", "trash"},
            {"cancel", "x"}, {"clear", "x"}, {"confirm", "check"}, {"save", "check"}, {"run", "play"},
            {"view", "eye"}, {"fefo order", "eye"}, {"release", "check"}, {"password", "key"}, {"edit", "edit"}, {"adjust", "edit"},
            {"refresh", "refresh"}, {"reload", "refresh"}, {"show", "search"}, {"log out", "logout"},
            {"add", "plus"}, {"register", "plus"}, {"new ", "plus"}, {"create", "plus"}};

    public static JPanel row(JComponent... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        for (JComponent c : components) {
            p.add(c);
        }
        return p;
    }

    /** Page title shown at the top of each screen. */
    public static JLabel heading(String text) {
        JLabel l = new JLabel(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setFont(Theme.font(Font.BOLD, 8));
                setForeground(Theme.strong());
            }
        };
        l.setBorder(BorderFactory.createEmptyBorder(2, 4, 10, 4));
        return l;
    }

    /** Button with an icon chosen from its label; main actions are filled accent, destructive ones red. */
    public static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        String lower = text.toLowerCase(Locale.ROOT);
        boolean primary = PRIMARY.contains(text);
        boolean danger = DANGER.contains(text);
        for (String[] rule : BUTTON_ICONS) {
            if (lower.contains(rule[0])) {
                b.setIcon(primary ? Icons.fixed(rule[1], 16, Color.WHITE)
                        : danger ? Icons.get(rule[1], 16, () -> Theme.toneForeground(Theme.Tone.DANGER))
                        : Icons.get(rule[1], 16));
                b.setIconTextGap(6);
                break;
            }
        }
        if (primary) {
            primary(b);
        } else if (danger) {
            b.putClientProperty(FlatClientProperties.STYLE, "foreground: #DC2626");
        }
        return b;
    }

    /** Filled accent style for a screen's main action. */
    public static JButton primary(JButton b) {
        b.putClientProperty(FlatClientProperties.STYLE, "background: $PharmaSync.accent; foreground: #FFFFFF; "
                + "borderWidth: 0; focusedBorderColor: $PharmaSync.accent; hoverBackground: darken($PharmaSync.accent,6%); "
                + "pressedBackground: darken($PharmaSync.accent,12%); font: bold");
        if (b.getIcon() instanceof FlatSVGIcon icon) {
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
        }
        return b;
    }

    /** Puts a panel on a rounded card with a bold title, replacing the old etched titled border. */
    public static <T extends JComponent> T section(T panel, String title) {
        panel.setOpaque(false);
        panel.setBorder(new SectionBorder(title));
        return panel;
    }

    /** Search box with a magnifier icon, placeholder text and a clear button. */
    public static JTextField searchField(int columns, String placeholder) {
        JTextField f = new JTextField(columns);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("search", 16, Theme::muted));
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        return f;
    }

    /** Small muted helper text that keeps its colour across theme changes. */
    public static JLabel muted(String text) {
        JLabel l = new JLabel(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setForeground(Theme.muted());
            }
        };
        l.setForeground(Theme.muted());
        return l;
    }

    /** Rounded card outline and fill painted behind a section's children, with its title on top. */
    private static final class SectionBorder extends AbstractBorder {
        private final String title;

        SectionBorder(String title) {
            this.title = title;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Theme.card());
                g2.fillRoundRect(x + 1, y + 1, w - 3, h - 3, 14, 14);
                g2.setColor(Theme.cardBorder());
                g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 14, 14);
                if (title != null && !title.isEmpty()) {
                    g2.setFont(Theme.font(Font.BOLD, 1));
                    g2.setColor(Theme.strong());
                    g2.drawString(title, x + 16, y + 10 + g2.getFontMetrics().getAscent());
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            int top = title == null || title.isEmpty() ? 12 : 16 + c.getFontMetrics(Theme.font(Font.BOLD, 1)).getHeight();
            return new Insets(top, 12, 12, 12);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets i = getBorderInsets(c);
            insets.set(i.top, i.left, i.bottom, i.right);
            return insets;
        }
    }

    public static void openFile(Component parent, Path file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                info(parent, "Saved to " + file.toAbsolutePath());
            }
        } catch (IOException e) {
            info(parent, "Saved to " + file.toAbsolutePath());
        }
    }

    /** Exports the table's visible rows (in on-screen order) to a CSV file chosen by the user. */
    public static void exportCsv(Component parent, JTable table, String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName.replaceAll("[^A-Za-z0-9_-]", "_") + ".csv"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8)) {
            int cols = table.getColumnCount();
            StringBuilder header = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                header.append(c == 0 ? "" : ",").append(csv(table.getColumnName(c)));
            }
            out.println(header);
            for (int r = 0; r < table.getRowCount(); r++) {
                StringBuilder line = new StringBuilder();
                for (int c = 0; c < cols; c++) {
                    Object v = table.getValueAt(r, c);
                    line.append(c == 0 ? "" : ",").append(csv(v == null ? "" : v.toString()));
                }
                out.println(line);
            }
            info(parent, "Exported " + table.getRowCount() + " rows to\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showError(parent, new BusinessException("Could not write file: " + e.getMessage()));
        }
    }

    private static String csv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static void later(Runnable r) {
        SwingUtilities.invokeLater(r);
    }
}
