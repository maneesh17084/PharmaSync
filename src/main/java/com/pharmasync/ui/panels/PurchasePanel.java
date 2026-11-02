package com.pharmasync.ui.panels;

import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.SupplyType;

import com.pharmasync.model.Purchase;
import com.pharmasync.model.PurchaseItem;
import com.pharmasync.model.PurchaseStatus;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;
import com.pharmasync.ui.DateField;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;
import com.pharmasync.ui.dialogs.PurchaseDialog;
import com.pharmasync.ui.dialogs.ReceiveDialog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.pharmasync.ui.ListTableModel.col;

/** Purchase orders, shipment receipt and purchase history (Module 4, FR-08, FR-09, FR-17). */
public class PurchasePanel extends JPanel implements Refreshable {

    private final DateField from = new DateField(LocalDate.now().minusDays(90));
    private final DateField to = new DateField(LocalDate.now());
    private final JComboBox<Object> status = new JComboBox<>(new Object[]{"All", PurchaseStatus.ORDERED,
            PurchaseStatus.RECEIVED, PurchaseStatus.CANCELLED});

    private final ListTableModel<Purchase> model = new ListTableModel<>(
            col("PO No", String.class, Purchase::purchaseNo),
            col("Order date", LocalDate.class, Purchase::orderDate),
            col("Supplier", String.class, Purchase::supplierName),
            col("Supplier inv.", String.class, Purchase::supplierInvoiceRef),
            col("Status", PurchaseStatus.class, Purchase::status),
            col("Received", LocalDate.class, Purchase::receivedDate),
            col("Subtotal", BigDecimal.class, Purchase::subtotal),
            col("GST", BigDecimal.class, Purchase::taxAmount),
            col("Total", BigDecimal.class, Purchase::totalAmount),
            col("Tax type", String.class, p -> p.supplyType() == SupplyType.INTER_STATE ? "IGST" : "CGST+SGST"),
            col("Payment", PaymentStatus.class, Purchase::paymentStatus),
            col("Paid", BigDecimal.class, Purchase::amountPaid),
            col("By", String.class, Purchase::createdBy));
    private final StyledTable table = new StyledTable(model);

    private final ListTableModel<PurchaseItem> items = new ListTableModel<>(
            col("Medicine", String.class, PurchaseItem::medicineName),
            col("Batch", String.class, PurchaseItem::batchNo),
            col("Mfg", LocalDate.class, PurchaseItem::mfgDate),
            col("Expiry", LocalDate.class, PurchaseItem::expiryDate),
            col("Qty", Integer.class, PurchaseItem::quantity),
            col("Unit cost", BigDecimal.class, PurchaseItem::unitCost),
            col("MRP", BigDecimal.class, PurchaseItem::mrp),
            col("GST %", BigDecimal.class, PurchaseItem::gstRate),
            col("Amount", BigDecimal.class, PurchaseItem::lineTotal));
    private final StyledTable itemTable = new StyledTable(items);

    public PurchasePanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> switch (model.row(r).status()) {
            case ORDERED -> StyledTable.AMBER;
            case CANCELLED -> StyledTable.GREY;
            case RECEIVED -> null;
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadItems();
            }
        });

        JButton cancel = UiUtil.button("Cancel order", () -> withSelected(p -> {
            if (UiUtil.confirm(this, "Cancel purchase order " + p.purchaseNo() + "?")) {
                Services.PURCHASES.cancel(p.id());
            }
        }));
        JButton pay = UiUtil.button("Record payment", () -> withSelected(p -> {
            String amount = JOptionPane.showInputDialog(this, "Payment amount for " + p.purchaseNo()
                    + " (outstanding " + UiUtil.money(p.totalAmount().subtract(p.amountPaid())) + "):");
            if (amount != null) {
                Services.PURCHASES.recordPayment(p.id(), UiUtil.parseDecimal(amount, "Amount"));
            }
        }));
        cancel.setVisible(Session.isAdmin());
        pay.setVisible(Session.isAdmin());

        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(UiUtil.heading("Purchases"));
        top.add(UiUtil.row(
                UiUtil.button("New purchase order", () -> {
                    if (PurchaseDialog.open(this, false, null)) {
                        refresh();
                    }
                }),
                UiUtil.button("Record received shipment", () -> {
                    if (PurchaseDialog.open(this, true, null)) {
                        refresh();
                    }
                }),
                UiUtil.button("Receive selected order", () -> model.selected(table).ifPresent(p -> {
                    if (p.status() != PurchaseStatus.ORDERED) {
                        UiUtil.info(this, p.purchaseNo() + " is " + p.status() + ".");
                    } else if (ReceiveDialog.show(this, p)) {
                        refresh();
                    }
                })),
                cancel, pay,
                UiUtil.button("PDF", () -> model.selected(table).ifPresent(p -> UiUtil.attempt(this, () -> {
                    try {
                        UiUtil.openFile(this, Services.PDF.writePurchaseRecord(p, Services.PURCHASES.items(p.id())));
                    } catch (java.io.IOException e) {
                        throw new com.pharmasync.service.BusinessException(e.getMessage());
                    }
                })))));
        top.add(UiUtil.row(new JLabel("From:"), from, new JLabel("To:"), to, new JLabel("Status:"), status,
                UiUtil.button("Show", this::refresh),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table, "purchases"))));
        add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JLabel(" Items of selected purchase"), BorderLayout.NORTH);
        bottom.add(new JScrollPane(itemTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), bottom);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        LocalDate f = from.getDate();
        LocalDate t = to.getDate();
        PurchaseStatus st = status.getSelectedItem() instanceof PurchaseStatus s ? s : null;
        UiUtil.async(this, () -> Services.PURCHASES.find(f, t, st), rows -> {
            model.setRows(rows);
            items.setRows(List.of());
        });
    }

    private void loadItems() {
        model.selected(table).ifPresentOrElse(
                p -> UiUtil.async(this, () -> Services.PURCHASES.items(p.id()), items::setRows),
                () -> items.setRows(List.of()));
    }

    private void withSelected(java.util.function.Consumer<Purchase> action) {
        model.selected(table).ifPresentOrElse(p -> {
            if (UiUtil.attempt(this, () -> action.accept(p))) {
                refresh();
            }
        }, () -> UiUtil.info(this, "Select a purchase first."));
    }
}
