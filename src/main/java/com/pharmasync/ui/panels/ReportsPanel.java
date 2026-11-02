package com.pharmasync.ui.panels;

import com.pharmasync.db.TableData;
import com.pharmasync.service.ReportService.Report;
import com.pharmasync.service.Services;
import com.pharmasync.ui.DateField;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.Theme;
import com.pharmasync.ui.UiUtil;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;

/** Inventory, purchase, sales, expiry and movement reports with CSV export and charts (Module 7). */
public class ReportsPanel extends JPanel implements Refreshable {

    private static final int CHART_ROWS = 15;

    private final JComboBox<Report> report = new JComboBox<>(Report.values());
    private final DateField from = new DateField(LocalDate.now().withDayOfMonth(1).minusMonths(1));
    private final DateField to = new DateField(LocalDate.now());
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int c) {
            for (int r = 0; r < getRowCount(); r++) {
                Object v = getValueAt(r, c);
                if (v != null) {
                    return v.getClass();
                }
            }
            return Object.class;
        }
    };
    private final StyledTable table = new StyledTable(model);
    private final JPanel chartHolder = new JPanel(new BorderLayout());
    private final JLabel status = new JLabel(" ");
    private final JSplitPane split;

    public ReportsPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        report.addActionListener(e -> {
            Report r = (Report) report.getSelectedItem();
            from.setEnabled(r != null && r.usesDateRange());
            to.setEnabled(r != null && r.usesDateRange());
            run();
        });

        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(UiUtil.heading("Reports & Analytics"));
        top.add(UiUtil.row(new JLabel("Report:"), report, new JLabel("From:"), from, new JLabel("To:"), to,
                UiUtil.button("Run", this::run),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table,
                        String.valueOf(report.getSelectedItem()) + "_" + LocalDate.now()))));
        add(top, BorderLayout.NORTH);

        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), chartHolder);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        run();
    }

    private void run() {
        Report r = (Report) report.getSelectedItem();
        if (r == null) {
            return;
        }
        LocalDate f = from.getDate();
        LocalDate t = to.getDate();
        if (r.usesDateRange() && f.isAfter(t)) {
            UiUtil.info(this, "'From' date must not be after 'To' date.");
            return;
        }
        UiUtil.async(this, () -> Services.REPORTS.run(r, f, t), data -> show(r, data, f, t));
    }

    private void show(Report r, TableData data, LocalDate f, LocalDate t) {
        model.setDataVector(data.rows().toArray(new Object[0][]), data.columns().toArray());
        status.setText(r.title() + (r.usesDateRange() ? "  |  " + f + " to " + t : "  |  as of " + LocalDate.now())
                + "  |  " + data.rows().size() + " rows" + totalsText(data));

        chartHolder.removeAll();
        if (r.chartLabelCol() >= 0 && !data.rows().isEmpty()) {
            DefaultCategoryDataset ds = new DefaultCategoryDataset();
            String series = data.columns().get(r.chartValueCol());
            data.rows().stream().limit(CHART_ROWS).forEach(row -> {
                Object label = row[r.chartLabelCol()];
                Object value = row[r.chartValueCol()];
                if (value instanceof Number n) {
                    ds.addValue(n, series, String.valueOf(label));
                }
            });
            JFreeChart chart = ChartFactory.createBarChart(r.title() + " - " + series
                            + (data.rows().size() > CHART_ROWS ? " (first " + CHART_ROWS + ")" : ""),
                    null, null, ds, PlotOrientation.HORIZONTAL, false, true, false);
            Theme.styleChart(chart, Theme.accent());
            ((BarRenderer) chart.getCategoryPlot().getRenderer()).setMaximumBarWidth(0.08);
            boolean money = data.rows().stream().anyMatch(row -> row[r.chartValueCol()] instanceof BigDecimal);
            Theme.chartTooltips(chart, money ? "Rs " : "", new DecimalFormat(money ? "#,##0.00" : "#,##0"),
                    "  (" + series + ")");
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPopupMenu(null);
            chartPanel.setMaximumDrawWidth(4000);
            chartPanel.setMaximumDrawHeight(3000);
            chartPanel.setInitialDelay(150);
            chartPanel.setDismissDelay(15000);
            chartHolder.add(chartPanel, BorderLayout.CENTER);
            split.setDividerLocation(0.55);
        } else {
            chartHolder.add(UiUtil.muted("  No chart for this report."), BorderLayout.NORTH);

            split.setDividerLocation(0.9);
        }
        chartHolder.revalidate();
        chartHolder.repaint();
    }

    /** Sums money-like columns so the status bar shows report totals. */
    private static String totalsText(TableData data) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < data.columns().size(); c++) {
            String name = data.columns().get(c);
            if (!name.matches("(?i).*(total|value|revenue|gst|paid|outstanding|margin|unpaid|sales).*")) {
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            boolean numeric = false;
            for (Object[] row : data.rows()) {
                if (row[c] instanceof BigDecimal b) {
                    sum = sum.add(b);
                    numeric = true;
                }
            }
            if (numeric) {
                sb.append("  |  ").append(name).append(": ").append(UiUtil.money(sum));
            }
        }
        return sb.toString();
    }
}
