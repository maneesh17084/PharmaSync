package com.pharmasync.ui.panels;

import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import com.pharmasync.model.Medicine;
import com.pharmasync.service.ExpiryService;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;
import com.pharmasync.ui.dialogs.PurchaseDialog;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pharmasync.ui.ListTableModel.col;

/** Low-stock, near-expiry and expired alerts with follow-up actions (Module 6, FR-14, FR-15). */
public class AlertsPanel extends JPanel implements Refreshable {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel thresholdNote = new JLabel();

    private final ListTableModel<Medicine> low = new ListTableModel<>(
            col("Code", String.class, Medicine::code),
            col("Medicine", String.class, Medicine::displayName),
            col("Category", String.class, Medicine::categoryName),
            col("Valid stock", Integer.class, Medicine::currentStock),
            col("Reorder level", Integer.class, Medicine::reorderLevel),
            col("Shortfall", Integer.class, m -> m.reorderLevel() - m.currentStock()),
            col("Suggested order", Integer.class, AlertsPanel::suggestedOrder));
    private final StyledTable lowTable = new StyledTable(low);

    private final ListTableModel<Batch> near = batchModel();
    private final StyledTable nearTable = new StyledTable(near).tint(r -> StyledTable.AMBER);
    private final ListTableModel<Batch> expired = batchModel();
    private final StyledTable expiredTable = new StyledTable(expired)
            .tint(r -> expired.row(r).status() == BatchStatus.QUARANTINED ? StyledTable.GREY : StyledTable.RED);

    public AlertsPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        tabs.addTab("Low stock", tab(lowTable, UiUtil.row(
                UiUtil.button("Create purchase order for all", this::orderLowStock),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, lowTable, "low_stock"))),
                "Medicines whose valid (unexpired, active) stock is below the reorder level."));

        tabs.addTab("Near expiry", tab(nearTable, UiUtil.row(
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, nearTable, "near_expiry"))),
                "Sell these first - FEFO dispatch already prioritises them. Consider returning slow movers to the supplier."));

        JPanel expiredActions = UiUtil.row(
                UiUtil.button("Quarantine all expired", () -> {
                    if (UiUtil.attempt(this, () -> UiUtil.info(this,
                            Services.STOCK.quarantineAllExpired() + " batch(es) moved to quarantine."))) {
                        refresh();
                    }
                }),
                UiUtil.button("Return selected to supplier", () -> writeOff(true)),
                UiUtil.button("Dispose selected", () -> writeOff(false)),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, expiredTable, "expired")));
        expiredActions.setVisible(Session.isAdmin());
        tabs.addTab("Expired", tab(expiredTable, expiredActions,
                "Expired batches are blocked from sale and dispatch. Quarantine, return or dispose of them."));

        JPanel top = new JPanel(new BorderLayout());
        top.add(UiUtil.heading("Stock Alerts"), BorderLayout.WEST);
        top.add(UiUtil.row(thresholdNote, UiUtil.button("Refresh", this::refresh)), BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private static int suggestedOrder(Medicine m) {
        // Bring stock back to twice the reorder level, rounded up to a multiple of 10.
        int target = Math.max(m.reorderLevel() * 2 - m.currentStock(), 0);
        return (target + 9) / 10 * 10;
    }

    private static ListTableModel<Batch> batchModel() {
        return new ListTableModel<>(
                col("Batch", String.class, Batch::batchNo),
                col("Medicine", String.class, Batch::medicineName),
                col("Expiry date", LocalDate.class, Batch::expiryDate),
                col("Days left", Long.class, b -> ExpiryService.daysUntil(b.expiryDate(), LocalDate.now())),
                col("Qty", Integer.class, Batch::quantity),
                col("Value at cost", BigDecimal.class, b -> b.purchasePrice().multiply(BigDecimal.valueOf(b.quantity()))),
                col("Supplier", String.class, Batch::supplierName),
                col("Status", BatchStatus.class, Batch::status));
    }

    private static JComponent tab(StyledTable table, JPanel actions, String note) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(actions, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(new JLabel(" " + note), BorderLayout.SOUTH);
        return p;
    }

    private record Data(List<Medicine> low, List<Batch> near, List<Batch> expired, int threshold) {
    }

    @Override
    public void refresh() {
        UiUtil.async(this, () -> new Data(Services.STOCK.lowStock(), Services.STOCK.nearExpiry(),
                Services.STOCK.expiredInStock(), Services.EXPIRY.thresholdDays()), d -> {
            low.setRows(d.low());
            near.setRows(d.near());
            expired.setRows(d.expired());
            tabs.setTitleAt(0, "Low stock (" + d.low().size() + ")");
            tabs.setTitleAt(1, "Near expiry (" + d.near().size() + ")");
            tabs.setTitleAt(2, "Expired (" + d.expired().size() + ")");
            thresholdNote.setText("Near-expiry threshold: " + d.threshold() + " days");
        });
    }

    private void orderLowStock() {
        if (low.getRowCount() == 0) {
            UiUtil.info(this, "No medicines are below their reorder level.");
            return;
        }
        Map<Medicine, Integer> prefill = new LinkedHashMap<>();
        low.rows().forEach(m -> prefill.put(m, Math.max(suggestedOrder(m), 10)));
        if (PurchaseDialog.open(this, false, prefill)) {
            refresh();
        }
    }

    private void writeOff(boolean toSupplier) {
        expired.selected(expiredTable).ifPresentOrElse(b -> {
            String what = toSupplier ? "Return" : "Dispose";
            String remarks = JOptionPane.showInputDialog(this, what + " " + b.quantity() + " units of batch "
                    + b.batchNo() + " (" + b.medicineName() + ").\nRemarks:");
            if (remarks == null) {
                return;
            }
            if (UiUtil.attempt(this, () -> {
                if (toSupplier) {
                    Services.STOCK.returnToSupplier(b.id(), remarks);
                } else {
                    Services.STOCK.dispose(b.id(), remarks);
                }
            })) {
                refresh();
            }
        }, () -> UiUtil.info(this, "Select an expired batch first."));
    }
}
