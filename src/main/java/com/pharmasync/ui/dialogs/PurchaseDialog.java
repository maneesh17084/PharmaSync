package com.pharmasync.ui.dialogs;

import com.pharmasync.model.Medicine;
import com.pharmasync.model.Supplier;
import com.pharmasync.service.BusinessException;
import com.pharmasync.service.InvoiceCalculator;
import com.pharmasync.service.PurchaseService.PurchaseLine;
import com.pharmasync.service.PurchaseService.PurchaseRequest;
import com.pharmasync.service.Services;
import com.pharmasync.ui.DateField;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import static com.pharmasync.ui.ListTableModel.col;

/**
 * Creates a purchase order, or records a shipment received directly (with batch and expiry details),
 * which posts stock immediately.
 */
public class PurchaseDialog extends JDialog {

    private record Draft(Medicine medicine, int qty, BigDecimal cost, BigDecimal mrp, BigDecimal gst, String batchNo,
                         LocalDate mfg, LocalDate expiry) {
        BigDecimal amount() {
            return InvoiceCalculator.line(qty, cost, BigDecimal.ZERO, gst).total();
        }
    }

    private final boolean receiveNow;
    private final JComboBox<Supplier> supplier;
    private final JTextField invoiceRef = new JTextField(14);
    private final DateField orderDate = new DateField(LocalDate.now());
    private final JTextField notes = new JTextField(30);

    private final JComboBox<Medicine> medicine;
    private final JTextField qty = new JTextField(6);
    private final JTextField cost = new JTextField(7);
    private final JTextField mrp = new JTextField(7);
    private final JTextField gst = new JTextField(4);
    private final JTextField batchNo = new JTextField(10);
    private final JTextField mfg = new JTextField(9);
    private final JTextField expiry = new JTextField(9);
    private final JLabel totals = new JLabel();

    private final ListTableModel<Draft> lines = new ListTableModel<>(
            col("Medicine", String.class, d -> d.medicine().displayName()),
            col("Qty", Integer.class, Draft::qty),
            col("Unit cost", BigDecimal.class, Draft::cost),
            col("MRP", BigDecimal.class, Draft::mrp),
            col("GST %", BigDecimal.class, Draft::gst),
            col("Batch", String.class, Draft::batchNo),
            col("Mfg", LocalDate.class, Draft::mfg),
            col("Expiry", LocalDate.class, Draft::expiry),
            col("Amount", BigDecimal.class, Draft::amount));
    private final StyledTable table = new StyledTable(lines);
    private boolean saved;

    private PurchaseDialog(Window owner, boolean receiveNow, Map<Medicine, Integer> prefill) {
        super(owner, receiveNow ? "Record received shipment" : "New purchase order", ModalityType.APPLICATION_MODAL);
        this.receiveNow = receiveNow;
        supplier = new JComboBox<>(Services.MASTER.searchSuppliers("", true).toArray(new Supplier[0]));
        medicine = new JComboBox<>(Services.MASTER.searchMedicines("", true).toArray(new Medicine[0]));
        medicine.addActionListener(e -> fillDefaults());
        fillDefaults();

        JPanel header = new JPanel(new GridLayout(0, 1));
        header.add(UiUtil.row(new JLabel("Supplier:"), supplier, new JLabel("Supplier invoice no.:"), invoiceRef,
                new JLabel("Date:"), orderDate));
        header.add(UiUtil.row(new JLabel("Notes:"), notes));
        UiUtil.section(header, "Purchase");

        JPanel entry = new JPanel(new GridLayout(0, 1));
        entry.add(UiUtil.row(new JLabel("Medicine:"), medicine, new JLabel("Qty:"), qty, new JLabel("Unit cost:"), cost,
                new JLabel("MRP:"), mrp, new JLabel("GST %:"), gst));
        entry.add(UiUtil.row(new JLabel("Batch no.:"), batchNo, new JLabel("Mfg date:"), mfg, new JLabel("Expiry date:"),
                expiry, new JLabel(receiveNow ? "(yyyy-mm-dd or mm/yyyy)" : "(optional now - required when receiving)"),
                UiUtil.button("Add line", this::addLine)));
        UiUtil.section(entry, "Add medicine");

        JPanel north = new JPanel(new BorderLayout());
        north.add(header, BorderLayout.NORTH);
        north.add(entry, BorderLayout.CENTER);

        JButton remove = UiUtil.button("Remove selected line", () -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                lines.remove(table.convertRowIndexToModel(r));
                updateTotals();
            }
        });
        JButton save = UiUtil.button(receiveNow ? "Receive & update stock" : "Save purchase order", this::save);
        save.setFont(save.getFont().deriveFont(Font.BOLD));
        JPanel south = new JPanel(new BorderLayout());
        south.add(totals, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(remove);
        buttons.add(save);
        buttons.add(UiUtil.button("Cancel", this::dispose));
        south.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(north, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(1000, 260));
        root.add(sp, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        if (prefill != null) {
            prefill.forEach((m, q) -> lines.add(new Draft(m, q, m.purchasePrice(), defaultMrp(m), m.gstRate(), null, null, null)));
        }
        updateTotals();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog; returns true if a purchase was saved. */
    public static boolean open(Component parent, boolean receiveNow, Map<Medicine, Integer> prefill) {
        PurchaseDialog d = new PurchaseDialog(SwingUtilities.getWindowAncestor(parent), receiveNow, prefill);
        d.setVisible(true);
        return d.saved;
    }

    private void fillDefaults() {
        Medicine m = (Medicine) medicine.getSelectedItem();
        if (m != null) {
            cost.setText(m.purchasePrice().toPlainString());
            mrp.setText(defaultMrp(m).toPlainString());
            gst.setText(m.gstRate().stripTrailingZeros().toPlainString());
        }
    }

    private static BigDecimal defaultMrp(Medicine m) {
        return m.sellingPrice().multiply(BigDecimal.valueOf(1.15)).setScale(2, RoundingMode.HALF_UP);
    }

    private void addLine() {
        UiUtil.attempt(this, () -> {
            Medicine m = (Medicine) medicine.getSelectedItem();
            if (m == null) {
                throw new BusinessException("Select a medicine");
            }
            int q = UiUtil.parseInt(qty.getText(), "Quantity");
            if (q <= 0) {
                throw new BusinessException("Quantity must be greater than zero");
            }
            LocalDate mfgDate = UiUtil.parseDate(mfg.getText(), "Mfg date", false);
            LocalDate expDate = UiUtil.parseDate(expiry.getText(), "Expiry date", true);
            if (expDate != null && !expDate.isAfter(LocalDate.now())) {
                throw new BusinessException("This batch is already expired - expired stock cannot be received");
            }
            lines.add(new Draft(m, q, UiUtil.parseDecimal(cost.getText(), "Unit cost"),
                    UiUtil.parseDecimal(mrp.getText(), "MRP"), UiUtil.parseDecimal(gst.getText(), "GST %"),
                    UiUtil.blankToNull(batchNo.getText()), mfgDate, expDate));
            qty.setText("");
            batchNo.setText("");
            mfg.setText("");
            expiry.setText("");
            updateTotals();
        });
    }

    private void updateTotals() {
        InvoiceCalculator.Totals t = InvoiceCalculator.totals(lines.rows().stream()
                .map(d -> InvoiceCalculator.line(d.qty(), d.cost(), BigDecimal.ZERO, d.gst())).toList());
        totals.setText("  " + lines.getRowCount() + " line(s)    Subtotal: " + UiUtil.money(t.subtotal())
                + "    GST: " + UiUtil.money(t.tax()) + "    Total: Rs " + UiUtil.money(t.total()));
    }

    private void save() {
        UiUtil.attempt(this, () -> {
            Supplier s = (Supplier) supplier.getSelectedItem();
            if (s == null) {
                throw new BusinessException("Select a supplier");
            }
            List<PurchaseLine> pl = lines.rows().stream().map(d -> new PurchaseLine(d.medicine().id(), d.qty(), d.cost(),
                    d.mrp(), d.gst(), d.batchNo(), d.mfg(), d.expiry())).toList();
            PurchaseRequest req = new PurchaseRequest(s.id(), invoiceRef.getText(), orderDate.getDate(), notes.getText(), pl);
            var p = receiveNow ? Services.PURCHASES.createAndReceive(req, orderDate.getDate())
                    : Services.PURCHASES.createOrder(req);
            saved = true;
            UiUtil.info(this, (receiveNow ? "Shipment received as " : "Purchase order saved as ") + p.purchaseNo()
                    + (receiveNow ? ".\nStock has been updated batch-wise." : "."));
            dispose();
        });
    }
}
