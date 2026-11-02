package com.pharmasync.ui.dialogs;

import com.pharmasync.model.Purchase;
import com.pharmasync.model.PurchaseItem;
import com.pharmasync.service.Services;
import com.pharmasync.ui.DateField;
import com.pharmasync.ui.UiUtil;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Receives an open purchase order: the user enters batch number, mfg and expiry date for each line. */
public final class ReceiveDialog {

    private static final int BATCH = 2;
    private static final int MFG = 3;
    private static final int EXPIRY = 4;

    private ReceiveDialog() {
    }

    public static boolean show(Component parent, Purchase purchase) {
        List<PurchaseItem> items = Services.PURCHASES.items(purchase.id());
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Medicine", "Qty", "Batch no. *", "Mfg date *", "Expiry date *"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column >= BATCH;
            }
        };
        for (PurchaseItem i : items) {
            model.addRow(new Object[]{i.medicineName(), i.quantity(), UiUtil.nz(i.batchNo()),
                    i.mfgDate() == null ? "" : i.mfgDate().toString(),
                    i.expiryDate() == null ? "" : i.expiryDate().toString()});
        }
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JTextField invoiceRef = new JTextField(UiUtil.nz(purchase.supplierInvoiceRef()), 14);
        DateField received = new DateField(LocalDate.now());

        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(UiUtil.row(new JLabel("Supplier invoice no.:"), invoiceRef, new JLabel("Received on:"), received,
                new JLabel("Dates: yyyy-mm-dd or mm/yyyy")), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(820, 220));
        p.add(sp, BorderLayout.CENTER);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, p, "Receive " + purchase.purchaseNo() + " from "
                    + purchase.supplierName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            boolean ok = UiUtil.attempt(parent, () -> {
                List<PurchaseItem> filled = new ArrayList<>();
                for (int r = 0; r < items.size(); r++) {
                    String name = items.get(r).medicineName();
                    filled.add(items.get(r).withBatchInfo(
                            String.valueOf(model.getValueAt(r, BATCH)).trim(),
                            UiUtil.parseDate(String.valueOf(model.getValueAt(r, MFG)), "Mfg date of " + name, false),
                            UiUtil.parseDate(String.valueOf(model.getValueAt(r, EXPIRY)), "Expiry date of " + name, true)));
                }
                Services.PURCHASES.receive(purchase.id(), filled, invoiceRef.getText(), received.getDate());
            });
            if (ok) {
                UiUtil.info(parent, purchase.purchaseNo() + " received. Stock updated for " + items.size() + " batch line(s).");
                return true;
            }
        }
    }
}
