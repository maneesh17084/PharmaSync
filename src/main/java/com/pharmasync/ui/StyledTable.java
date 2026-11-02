package com.pharmasync.ui;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * JTable with sorting, single selection, zebra rows, optional per-row tint and double-click action.
 * Values are formatted by type: money right-aligned with 2 decimals, readable dates, and status words
 * (PAID, EXPIRED, NEAR_EXPIRY, ...) as coloured pills.
 */
public class StyledTable extends JTable {

    /** Semantic row colours; mapped to the current theme by {@link Theme#rowTint}. */
    public static final Color RED = new Color(0xF8, 0xD7, 0xDA);
    public static final Color AMBER = new Color(0xFF, 0xF3, 0xCD);
    public static final Color GREEN = new Color(0xD1, 0xE7, 0xDD);
    public static final Color GREY = new Color(0xE2, 0xE3, 0xE5);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private IntFunction<Color> rowTint;
    private String emptyText = "No records to show";
    private DefaultTableCellRenderer moneyRenderer;
    private DefaultTableCellRenderer dateRenderer;
    private BadgeRenderer badgeRenderer;

    public StyledTable(TableModel model) {
        super(model);
        setAutoCreateRowSorter(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFillsViewportHeight(true);
        getTableHeader().setReorderingAllowed(false);
        createRenderers();
        // Size columns to their content whenever the rows change, so values are not cut off.
        model.addTableModelListener(e -> SwingUtilities.invokeLater(this::fitColumns));
    }

    /** Sets each column's preferred width from its header and the first rows; extra space is shared out. */
    public void fitColumns() {
        int sample = Math.min(getRowCount(), 150);
        for (int c = 0; c < getColumnCount(); c++) {
            TableColumn column = getColumnModel().getColumn(c);
            TableCellRenderer hr = getTableHeader().getDefaultRenderer();
            int width = hr.getTableCellRendererComponent(this, column.getHeaderValue(), false, false, -1, c)
                    .getPreferredSize().width;
            for (int r = 0; r < sample; r++) {
                Component comp = prepareRenderer(getCellRenderer(r, c), r, c);
                width = Math.max(width, comp.getPreferredSize().width);
            }
            column.setPreferredWidth(Math.min(width + 20, 360));
        }
    }

    /** Tint for a model row index, or null for the default background. */
    public StyledTable tint(IntFunction<Color> rowTint) {
        this.rowTint = rowTint;
        return this;
    }

    public StyledTable emptyText(String text) {
        this.emptyText = text;
        return this;
    }

    public StyledTable onDoubleClick(Runnable action) {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && getSelectedRow() >= 0) {
                    action.run();
                }
            }
        });
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        createRenderers();
    }

    private void createRenderers() {
        moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof BigDecimal b ? UiUtil.money(b) : String.valueOf(value));
            }
        };
        moneyRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        dateRenderer = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof LocalDateTime dt ? dt.format(DATE_TIME)
                        : value instanceof LocalDate d ? d.format(DATE) : value == null ? "" : value.toString());
            }
        };
        badgeRenderer = new BadgeRenderer();
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        Object v = getValueAt(row, column);
        if (v instanceof BigDecimal) {
            return moneyRenderer;
        }
        if (v instanceof LocalDate || v instanceof LocalDateTime) {
            return dateRenderer;
        }
        if (v instanceof Enum<?> || v instanceof String s && Theme.isStatusWord(s)) {
            return badgeRenderer;
        }
        return super.getCellRenderer(row, column);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (!isRowSelected(row)) {
            Color tint = rowTint == null ? null : Theme.rowTint(rowTint.apply(convertRowIndexToModel(row)));
            Color alternate = UIManager.getColor("Table.alternateRowColor");
            c.setBackground(tint != null ? tint : row % 2 == 1 && alternate != null ? alternate : getBackground());
            c.setForeground(getForeground());
        }
        return c;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() == 0 && emptyText != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(Theme.muted());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getVisibleRect().width - fm.stringWidth(emptyText)) / 2 + getVisibleRect().x;
            g2.drawString(emptyText, Math.max(x, 8), getVisibleRect().y + 48);
            g2.dispose();
        }
    }

    /** Status values drawn as rounded pills coloured by {@link Theme#toneOf}. */
    private static final class BadgeRenderer extends JLabel implements TableCellRenderer {

        private Theme.Tone tone = Theme.Tone.NEUTRAL;

        BadgeRenderer() {
            setOpaque(true);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            String raw = value instanceof Enum<?> e ? e.name() : String.valueOf(value);
            tone = Theme.toneOf(raw);
            String words = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
            setText(words.isEmpty() ? "" : Character.toUpperCase(words.charAt(0)) + words.substring(1));
            setFont(table.getFont().deriveFont(Font.BOLD, table.getFont().getSize2D() - 1));
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            java.awt.Dimension d = super.getPreferredSize();
            return new java.awt.Dimension(d.width + 18, d.height);
        }


        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(getText());
                int pillH = fm.getHeight() + 4;
                int pillW = Math.min(textW + 18, getWidth() - 12);
                int x = 8;
                int y = (getHeight() - pillH) / 2;
                g2.setColor(Theme.toneBackground(tone));
                g2.fillRoundRect(x, y, pillW, pillH, pillH, pillH);
                g2.setColor(Theme.toneForeground(tone));
                g2.drawString(getText(), x + 9, y + (pillH - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }
}
