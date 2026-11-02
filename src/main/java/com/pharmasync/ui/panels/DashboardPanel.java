package com.pharmasync.ui.panels;

import com.pharmasync.service.DashboardService;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;
import com.pharmasync.ui.Card;
import com.pharmasync.ui.Icons;
import com.pharmasync.ui.MainFrame;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.Theme;
import com.pharmasync.ui.Theme.Tone;
import com.pharmasync.ui.UiUtil;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Overview tiles plus sales trend and top-seller charts. */
public class DashboardPanel extends JPanel implements Refreshable {

    private final MainFrame frame;
    private final JLabel greeting = new JLabel();
    private final JPanel tiles = new JPanel(new GridLayout(2, 4, 14, 14));
    private final JPanel charts = new JPanel(new GridLayout(1, 2, 14, 14));

    public DashboardPanel(MainFrame frame) {
        super(new BorderLayout(10, 10));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        tiles.setOpaque(false);
        charts.setOpaque(false);

        greeting.setFont(Theme.font(Font.BOLD, 8));
        JLabel date = UiUtil.muted("Here is how the store is doing today, "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)) + ".");
        date.setFont(Theme.font(Font.PLAIN, 1));
        JPanel titles = new JPanel(new BorderLayout(0, 4));
        titles.setOpaque(false);
        titles.add(greeting, BorderLayout.NORTH);
        titles.add(date, BorderLayout.SOUTH);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 0));
        top.add(titles, BorderLayout.WEST);
        top.add(UiUtil.row(UiUtil.button("New sales order", () -> frame.navigate("sales")),
                UiUtil.button("Refresh", this::refresh)), BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(14, 14));
        center.setOpaque(false);
        center.add(tiles, BorderLayout.NORTH);
        center.add(charts, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private record Data(DashboardService.Kpis kpis, Map<LocalDate, BigDecimal> daily, Map<String, Long> top) {
    }

    @Override
    public void refresh() {
        int hour = LocalTime.now().getHour();
        String part = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        greeting.setText(part + ", " + Session.current().fullName());
        UiUtil.async(this, () -> new Data(Services.DASHBOARD.kpis(), Services.DASHBOARD.dailySales(14),
                Services.DASHBOARD.topMedicines(30, 7)), this::show);
    }

    private void show(Data d) {
        DashboardService.Kpis k = d.kpis();
        tiles.removeAll();
        tiles.add(tile("Today's sales", "Rs " + UiUtil.money(k.todaySales()),
                k.todayInvoices() + " invoice" + (k.todayInvoices() == 1 ? "" : "s") + " today", "trending", Tone.SUCCESS, "salesHistory"));
        tiles.add(tile("This month", "Rs " + UiUtil.money(k.monthSales()), "Sales since the 1st", "chart", Tone.SUCCESS, "salesHistory"));
        tiles.add(tile("Receivables", "Rs " + UiUtil.money(k.receivables()), "Balance due from customers", "wallet",
                k.receivables().signum() > 0 ? Tone.WARNING : Tone.SUCCESS, "salesHistory"));
        tiles.add(tile("Stock value", "Rs " + UiUtil.money(k.stockValue()), "Sellable stock at cost", "package", Tone.INFO, "batches"));
        tiles.add(tile("Active medicines", String.valueOf(k.activeMedicines()), "In the catalogue", "pill", Tone.INFO, "medicines"));
        tiles.add(tile("Low stock", String.valueOf(k.lowStock()), k.lowStock() > 0 ? "Below reorder level" : "All above reorder level",
                "alert", k.lowStock() > 0 ? Tone.WARNING : Tone.SUCCESS, "alerts"));
        tiles.add(tile("Near expiry", String.valueOf(k.nearExpiry()), k.nearExpiry() > 0 ? "Batches to sell first" : "Nothing expiring soon",
                "clock", k.nearExpiry() > 0 ? Tone.WARNING : Tone.SUCCESS, "alerts"));
        tiles.add(tile("Expired in stock", String.valueOf(k.expired()), k.expired() > 0 ? "Quarantine, return or dispose" : "No expired stock",
                "octagon", k.expired() > 0 ? Tone.DANGER : Tone.SUCCESS, "alerts"));

        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
        DefaultCategoryDataset sales = new DefaultCategoryDataset();
        d.daily().forEach((day, total) -> sales.addValue(total, "Sales", day.format(f)));
        JFreeChart salesChart = ChartFactory.createBarChart(null, null, null, sales,
                PlotOrientation.VERTICAL, false, true, false);
        Theme.styleChart(salesChart, Theme.accent());
        salesChart.getCategoryPlot().getDomainAxis().setCategoryLabelPositions(
                org.jfree.chart.axis.CategoryLabelPositions.UP_45);
        Theme.chartTooltips(salesChart, "Rs ", new DecimalFormat("#,##0.00"), "");

        DefaultCategoryDataset top = new DefaultCategoryDataset();
        d.top().forEach((name, qty) -> top.addValue(qty, "Units", name));
        JFreeChart topChart = ChartFactory.createBarChart(null, null, null, top,
                PlotOrientation.HORIZONTAL, false, true, false);
        Theme.styleChart(topChart, new Color(0x6366F1));
        ((org.jfree.chart.renderer.category.BarRenderer) topChart.getCategoryPlot().getRenderer()).setMaximumBarWidth(0.12);
        Theme.chartTooltips(topChart, "", new DecimalFormat("#,##0"), " units sold");
        Theme.chartValueLabels(topChart, new DecimalFormat("#,##0"));

        charts.removeAll();
        charts.add(chartCard("Sales trend", "Invoice value per day, last 14 days (Rs)", salesChart));
        charts.add(chartCard("Top sellers", "Units sold, last 30 days", topChart));
        revalidate();
        repaint();
    }

    private static JComponent chartCard(String title, String subtitle, JFreeChart chart) {
        Card card = new Card(new BorderLayout(0, 8));
        JLabel t = new JLabel(title);
        t.setFont(Theme.font(Font.BOLD, 2));
        JLabel s = UiUtil.muted(subtitle);
        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        head.add(t, BorderLayout.NORTH);
        head.add(s, BorderLayout.SOUTH);
        ChartPanel panel = new ChartPanel(chart);
        panel.setOpaque(false);
        panel.setPopupMenu(null);
        panel.setMinimumDrawWidth(200);
        panel.setMinimumDrawHeight(150);
        panel.setMaximumDrawWidth(4000);
        panel.setMaximumDrawHeight(3000);
        panel.setDisplayToolTips(true);
        panel.setInitialDelay(150);
        panel.setDismissDelay(15000);
        card.add(head, BorderLayout.NORTH);
        card.add(panel, BorderLayout.CENTER);
        return card;
    }

    private JComponent tile(String label, String value, String caption, String icon, Tone tone, String target) {
        Card card = new Card(new BorderLayout(14, 0));
        card.setPreferredSize(new Dimension(220, 104));
        card.add(new IconBadge(icon, tone), BorderLayout.WEST);

        JLabel l = UiUtil.muted(label);
        l.setFont(Theme.font(Font.BOLD, -0.5f));
        JLabel v = new JLabel(value);
        v.setFont(Theme.font(Font.BOLD, 9));
        JLabel c = UiUtil.muted(caption);
        c.setFont(Theme.font(Font.PLAIN, -1));
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(l, BorderLayout.NORTH);
        text.add(v, BorderLayout.CENTER);
        text.add(c, BorderLayout.SOUTH);
        card.add(text, BorderLayout.CENTER);

        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Open " + label.toLowerCase(Locale.ROOT));
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                frame.navigate(target);
            }
        });
        return card;
    }

    /** Rounded square in the tone's soft colour with the icon in the tone's strong colour. */
    private static final class IconBadge extends JComponent {
        private final Tone tone;
        private final javax.swing.Icon icon;

        IconBadge(String name, Tone tone) {
            this.tone = tone;
            this.icon = Icons.get(name, 22, () -> Theme.toneForeground(tone));
            setPreferredSize(new Dimension(46, 46));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - 46) / 2;
            g2.setColor(Theme.toneBackground(tone));
            g2.fillRoundRect(0, y, 46, 46, 14, 14);
            icon.paintIcon(this, g2, 12, y + 12);
            g2.dispose();
        }
    }
}
