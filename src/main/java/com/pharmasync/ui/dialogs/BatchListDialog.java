package com.pharmasync.ui.dialogs;

import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import com.pharmasync.model.ExpiryStatus;
import com.pharmasync.model.Medicine;
import com.pharmasync.service.ExpiryService;
import com.pharmasync.service.FefoAllocator;
import com.pharmasync.service.Services;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.StyledTable;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.pharmasync.ui.ListTableModel.col;

/** Shows all batches of one medicine with their FEFO dispatch position. */
public final class BatchListDialog {

    private BatchListDialog() {
    }

    public static void show(Component parent, Medicine medicine) {
        LocalDate today = LocalDate.now();
        int threshold = Services.EXPIRY.thresholdDays();
        List<Batch> all = new ArrayList<>(Services.STOCK.batchesOf(medicine.id()));
        List<Batch> fefo = FefoAllocator.dispatchOrder(all, today);
        all.sort(Comparator.comparing(Batch::expiryDate));

        ListTableModel<Batch> model = new ListTableModel<>(
                col("Dispatch order", String.class, b -> {
                    int i = fefo.indexOf(b);
                    return i < 0 ? "blocked" : "#" + (i + 1);
                }),
                col("Batch", String.class, Batch::batchNo),
                col("Mfg", LocalDate.class, Batch::mfgDate),
                col("Expiry", LocalDate.class, Batch::expiryDate),
                col("Days left", Long.class, b -> ExpiryService.daysUntil(b.expiryDate(), today)),
                col("Qty", Integer.class, Batch::quantity),
                col("MRP", java.math.BigDecimal.class, Batch::mrp),
                col("Status", BatchStatus.class, Batch::status),
                col("Expiry status", ExpiryStatus.class, b -> ExpiryService.classify(b.expiryDate(), today, threshold)),
                col("Supplier", String.class, Batch::supplierName));
        model.setRows(all);
        StyledTable table = new StyledTable(model).tint(r -> BatchTint.of(model.row(r), today, threshold));

        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel("<html><b>" + medicine.displayName() + "</b> - sellable stock " + medicine.currentStock()
                + " units. Batches are dispatched in ascending expiry order; expired and blocked batches are skipped.</html>"),
                BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(900, 300));
        p.add(sp, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(parent, p, "Batches - " + medicine.brandName(), JOptionPane.PLAIN_MESSAGE);
    }
}
