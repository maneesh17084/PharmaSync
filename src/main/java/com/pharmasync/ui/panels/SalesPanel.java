package com.pharmasync.ui.panels;

import com.pharmasync.model.Batch;
import com.pharmasync.model.Customer;
import com.pharmasync.model.IndianState;
import com.pharmasync.model.Medicine;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.SaleItem;
import com.pharmasync.model.SupplyType;
import com.pharmasync.service.BusinessException;
import com.pharmasync.service.ExpiryService;
import com.pharmasync.service.Gst;
import com.pharmasync.service.InvoiceCalculator;
import com.pharmasync.service.SalesService.SaleLine;
import com.pharmasync.service.SalesService.SaleRequest;
import com.pharmasync.service.SalesService.SaleResult;
import com.pharmasync.service.Services;
import com.pharmasync.service.SettingsService;

import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.pharmasync.ui.ListTableModel.col;

/**
 * Retailer order entry: checks valid stock, suggests FEFO batches, rejects unavailable quantities,
 * calculates the bill and confirms the sale with an invoice (Module 5, Business logic E).
 */
public class SalesPanel extends JPanel implements Refreshable {

    private static final String AUTO = "Auto (FEFO)";

    private record OrderLine(Medicine medicine, int qty, Batch batch, BigDecimal rate, BigDecimal discount) {
        InvoiceCalculator.LineAmounts amounts() {
            return InvoiceCalculator.line(qty, rate, discount, medicine.gstRate());
        }
    }

    private record Suggestion(int order, Batch batch, int take) {
    }

    private final JComboBox<Customer> customer = new JComboBox<>();
    private final JComboBox<String> paymentMode = new JComboBox<>(new String[]{"CREDIT", "CASH", "UPI", "NEFT", "CHEQUE"});
    private final JComboBox<PaymentStatus> paymentStatus = new JComboBox<>(PaymentStatus.values());
    private final JTextField amountReceived = new JTextField(8);
    private IndianState companyState;

    private final JComboBox<Medicine> medicine = new JComboBox<>();
    private final JComboBox<Object> batch = new JComboBox<>();
    private final JTextField qty = new JTextField(6);
    private final JTextField rate = new JTextField(7);
    private final JTextField discount = new JTextField("0", 4);
    private final JLabel available = new JLabel();
    private List<Batch> sellable = List.of();

    private final ListTableModel<Suggestion> suggestions = new ListTableModel<>(
            col("#", Integer.class, Suggestion::order),
            col("Batch", String.class, s -> s.batch().batchNo()),
            col("Expiry", LocalDate.class, s -> s.batch().expiryDate()),
            col("Days left", Long.class, s -> ExpiryService.daysUntil(s.batch().expiryDate(), LocalDate.now())),
            col("In batch", Integer.class, s -> s.batch().quantity()),
            col("Dispatch", Integer.class, Suggestion::take));
    private final StyledTable suggestionTable = new StyledTable(suggestions);
    private final JLabel suggestionNote = new JLabel(" ");

    private final ListTableModel<OrderLine> lines = new ListTableModel<>(
            col("Medicine", String.class, l -> l.medicine().displayName()),
            col("Batch", String.class, l -> l.batch() == null ? AUTO : l.batch().batchNo()),
            col("Qty", Integer.class, OrderLine::qty),
            col("Rate", BigDecimal.class, OrderLine::rate),
            col("Disc %", BigDecimal.class, OrderLine::discount),
            col("GST %", BigDecimal.class, l -> l.medicine().gstRate()),
            col("Taxable", BigDecimal.class, l -> l.amounts().taxable()),
            col("GST", BigDecimal.class, l -> l.amounts().tax()),
            col("Amount", BigDecimal.class, l -> l.amounts().total()));
    private final StyledTable lineTable = new StyledTable(lines);
    private final JLabel totals = new JLabel();

    public SalesPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        medicine.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof Medicine m) {
                    setText(m.brandName() + " - " + m.genericName() + "   [stock " + m.currentStock() + "]");
                }
                return this;
            }
        });
        medicine.addActionListener(e -> medicineChanged());
        batch.addActionListener(e -> updateSuggestion());
        UiUtil.onTextChange(qty, this::updateSuggestion);

        JPanel orderInfo = new JPanel(new GridLayout(0, 1));
        orderInfo.add(UiUtil.heading("New Sales Order"));
        orderInfo.add(UiUtil.row(new JLabel("Customer:"), customer, new JLabel("Payment mode:"), paymentMode,
                new JLabel("Payment status:"), paymentStatus, new JLabel("Amount received:"), amountReceived,
                UiUtil.button("Reload lists", this::refresh)));
        amountReceived.setEnabled(false);
        amountReceived.setToolTipText("Only for PARTIAL: the amount the customer pays now");
        paymentStatus.addActionListener(e -> amountReceived.setEnabled(paymentStatus.getSelectedItem() == PaymentStatus.PARTIAL));
        customer.addActionListener(e -> updateTotals());

        JPanel entry = new JPanel(new GridLayout(0, 1));
        UiUtil.section(entry, "Add medicine to order");
        medicine.setPreferredSize(new Dimension(380, medicine.getPreferredSize().height));
        batch.setPreferredSize(new Dimension(260, batch.getPreferredSize().height));
        entry.add(UiUtil.row(new JLabel("Medicine:"), medicine));
        entry.add(UiUtil.row(new JLabel("Batch:"), batch, new JLabel("Qty:"), qty));
        entry.add(UiUtil.row(new JLabel("Rate:"), rate, new JLabel("Disc %:"), discount,
                UiUtil.button("Add to order", this::addLine)));
        entry.add(UiUtil.row(available));

        JPanel suggestionPanel = new JPanel(new BorderLayout());
        UiUtil.section(suggestionPanel, "FEFO dispatch suggestion");
        JScrollPane ssp = new JScrollPane(suggestionTable);
        ssp.setPreferredSize(new Dimension(460, 130));
        suggestionPanel.add(ssp, BorderLayout.CENTER);
        suggestionPanel.add(suggestionNote, BorderLayout.SOUTH);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(orderInfo, BorderLayout.NORTH);
        north.add(entry, BorderLayout.CENTER);
        north.add(suggestionPanel, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        add(new JScrollPane(lineTable), BorderLayout.CENTER);

        totals.setFont(totals.getFont().deriveFont(Font.BOLD, 14f));
        JButton confirm = UiUtil.button("Confirm order & generate invoice", this::confirm);
        confirm.setFont(confirm.getFont().deriveFont(Font.BOLD));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(UiUtil.button("Remove line", () -> {
            int r = lineTable.getSelectedRow();
            if (r >= 0) {
                lines.remove(lineTable.convertRowIndexToModel(r));
                afterLinesChanged();
            }
        }));
        buttons.add(UiUtil.button("Clear order", this::clearOrder));
        buttons.add(confirm);
        JPanel south = new JPanel(new BorderLayout());
        south.add(totals, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        updateTotals();
    }

    @Override
    public void refresh() {
        UiUtil.async(this, () -> new Object[]{Services.MASTER.searchCustomers("", true),
                Services.MASTER.searchMedicines("", true),
                IndianState.ofCode(Services.SETTINGS.get(SettingsService.COMPANY_STATE))}, data -> {
            @SuppressWarnings("unchecked") List<Customer> cs = (List<Customer>) data[0];
            @SuppressWarnings("unchecked") List<Medicine> ms = (List<Medicine>) data[1];
            companyState = (IndianState) data[2];
            Object selCustomer = customer.getSelectedItem();
            customer.removeAllItems();
            cs.forEach(customer::addItem);
            if (selCustomer != null) {
                customer.setSelectedItem(selCustomer);
            }
            Medicine selMed = (Medicine) medicine.getSelectedItem();
            medicine.removeAllItems();
            ms.forEach(medicine::addItem);
            if (selMed != null) {
                ms.stream().filter(m -> m.id() == selMed.id()).findFirst().ifPresent(medicine::setSelectedItem);
            }
            medicineChanged();
        });
    }

    private void medicineChanged() {
        Medicine m = (Medicine) medicine.getSelectedItem();
        batch.removeAllItems();
        batch.addItem(AUTO);
        if (m == null) {
            sellable = List.of();
            available.setText("");
            updateSuggestion();
            return;
        }
        rate.setText(m.sellingPrice().toPlainString());
        sellable = Services.SALES.sellableBatches(m.id());
        sellable.forEach(batch::addItem);
        int stock = sellable.stream().mapToInt(Batch::quantity).sum();
        int reserved = reservedInOrder(m, null);
        available.setText("Valid stock: " + stock + (reserved > 0 ? "  (already in this order: " + reserved + ")" : "")
                + "   |   expired / blocked batches are excluded");
        updateSuggestion();
    }

    /** Units of this medicine already on the order (for one batch, or for all when batch is null). */
    private int reservedInOrder(Medicine m, Batch b) {
        return lines.rows().stream()
                .filter(l -> l.medicine().id() == m.id() && (b == null || l.batch() != null && l.batch().id() == b.id()))
                .mapToInt(OrderLine::qty).sum();
    }

    /** Sellable batches with quantities reduced by lines already on the order. */
    private List<Batch> remainingBatches(Medicine m) {
        List<Batch> remaining = new ArrayList<>();
        int fefoReserved = lines.rows().stream().filter(l -> l.medicine().id() == m.id() && l.batch() == null)
                .mapToInt(OrderLine::qty).sum();
        for (Batch b : sellable) {
            int left = b.quantity() - reservedInOrder(m, b);
            int fromFefo = Math.min(Math.max(left, 0), fefoReserved);
            fefoReserved -= fromFefo;
            left -= fromFefo;
            if (left > 0) {
                remaining.add(b.withQuantity(left));
            }
        }
        return remaining;
    }

    private void updateSuggestion() {
        Medicine m = (Medicine) medicine.getSelectedItem();
        List<Suggestion> rows = new ArrayList<>();
        int q;
        try {
            q = Integer.parseInt(qty.getText().trim());
        } catch (NumberFormatException e) {
            q = 0;
        }
        if (m == null) {
            suggestions.setRows(rows);
            suggestionNote.setText(" ");
            return;
        }
        List<Batch> remaining = remainingBatches(m);
        int total = remaining.stream().mapToInt(Batch::quantity).sum();
        if (batch.getSelectedItem() instanceof Batch chosen) {
            Batch left = remaining.stream().filter(b -> b.id() == chosen.id()).findFirst().orElse(chosen.withQuantity(0));
            rows.add(new Suggestion(1, left, Math.min(q, left.quantity())));
            suggestionNote.setText(q > left.quantity() ? " Batch " + chosen.batchNo() + " has only " + left.quantity()
                    + " units" : " Manual batch choice (FEFO recommends dispatching the earliest expiry first)");
        } else {
            int need = q;
            int i = 1;
            for (Batch b : remaining) {
                int take = Math.min(need, b.quantity());
                rows.add(new Suggestion(i++, b, take));
                need -= take;
            }
            suggestionNote.setText(q == 0 ? " Enter a quantity to see which batches will be dispatched"
                    : need > 0 ? " Short by " + need + " units - only " + total + " valid units available"
                    : " Dispatch from the batches above in this order");
        }
        suggestions.setRows(rows);
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
            BigDecimal r = UiUtil.parseDecimal(rate.getText(), "Rate");
            BigDecimal d = UiUtil.parseDecimal(discount.getText().isBlank() ? "0" : discount.getText(), "Discount");
            Batch chosen = batch.getSelectedItem() instanceof Batch b ? b : null;

            List<Batch> remaining = remainingBatches(m);
            int avail = chosen == null ? remaining.stream().mapToInt(Batch::quantity).sum()
                    : remaining.stream().filter(b -> b.id() == chosen.id()).mapToInt(Batch::quantity).sum();
            if (avail <= 0) {
                throw new BusinessException("No valid stock available for " + m.brandName()
                        + (chosen == null ? "" : " in batch " + chosen.batchNo()) + ". The line was rejected.");
            }
            if (q > avail) {
                int choice = JOptionPane.showConfirmDialog(this, "Only " + avail + " valid units of " + m.brandName()
                                + " are available (requested " + q + ").\n\nAdd " + avail
                                + " units and reject the remaining " + (q - avail) + "?",
                        "Insufficient stock", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
                q = avail;
            }
            InvoiceCalculator.line(q, r, d, m.gstRate()); // validates discount / rate
            lines.add(new OrderLine(m, q, chosen, r, d));
            qty.setText("");
            afterLinesChanged();
        });
    }

    private void afterLinesChanged() {
        updateTotals();
        medicineChanged();
    }

    private void updateTotals() {
        InvoiceCalculator.Totals t = InvoiceCalculator.totals(lines.rows().stream().map(OrderLine::amounts).toList());
        totals.setText("<html>&nbsp;&nbsp;Items: " + lines.getRowCount() + "&nbsp;&nbsp;&nbsp; Gross: " + UiUtil.money(t.subtotal())
                + "&nbsp;&nbsp;&nbsp; Discount: " + UiUtil.money(t.discount()) + "&nbsp;&nbsp;&nbsp; GST: " + UiUtil.money(t.tax())
                + "&nbsp;&nbsp;&nbsp; TOTAL: Rs " + UiUtil.money(t.total())
                + "<br>&nbsp;&nbsp;<span style='font-weight:normal'>" + gstNote(t.tax()) + "</span></html>");
    }

    /** Place of supply and the CGST/SGST or IGST split the invoice will carry. */
    private String gstNote(BigDecimal tax) {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null) {
            return "Select a customer to see the place of supply";
        }
        if (c.state() == null) {
            return "Set the state of " + c.name() + " in Customers before billing";
        }
        if (companyState == null) {
            return "Set the company's state in Settings before billing";
        }
        SupplyType type = Gst.supplyType(companyState, c.state());
        Gst.Split split = Gst.split(tax, type);
        return "Place of supply: " + c.state() + "  -  " + (type == SupplyType.INTER_STATE
                ? "inter-state, IGST " + UiUtil.money(split.igst())
                : "intra-state, CGST " + UiUtil.money(split.cgst()) + " + " + c.state().stateTaxName() + " "
                + UiUtil.money(split.sgst()));
    }

    private void clearOrder() {
        lines.setRows(List.of());
        afterLinesChanged();
    }

    private void confirm() {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null) {
            UiUtil.info(this, "Select a customer first.");
            return;
        }
        if (lines.getRowCount() == 0) {
            UiUtil.info(this, "Add at least one medicine to the order.");
            return;
        }
        if (!UiUtil.confirm(this, "Confirm order for " + c.name() + " with " + lines.getRowCount() + " line(s)?\n"
                + "Stock will be deducted and an invoice generated.")) {
            return;
        }
        List<SaleLine> saleLines = lines.rows().stream().map(l -> new SaleLine(l.medicine().id(), l.qty(),
                l.batch() == null ? null : l.batch().id(), l.rate(), l.discount())).toList();
        PaymentStatus status = (PaymentStatus) paymentStatus.getSelectedItem();
        BigDecimal received;
        try {
            received = status == PaymentStatus.PARTIAL ? UiUtil.parseDecimal(amountReceived.getText(), "Amount received") : null;
        } catch (BusinessException e) {
            UiUtil.showError(this, e);
            return;
        }
        SaleRequest req = new SaleRequest(c.id(), (String) paymentMode.getSelectedItem(), status, received, saleLines, null);
        UiUtil.async(this, () -> {
            SaleResult result = Services.SALES.createSale(req);
            Path pdf;
            try {
                pdf = Services.PDF.writeSaleInvoice(result.sale(), c, result.items());
            } catch (IOException e) {
                pdf = null;
            }
            return new Object[]{result, pdf};
        }, data -> {
            SaleResult result = (SaleResult) data[0];
            Path pdf = (Path) data[1];
            clearOrder();
            refresh();
            String batches = result.items().stream().map(this::describe).collect(Collectors.joining("\n"));
            amountReceived.setText("");
            String msg = "Invoice " + result.sale().invoiceNo() + " saved.\nTotal: Rs " + UiUtil.money(result.sale().totalAmount())
                    + "    Paid: Rs " + UiUtil.money(result.sale().amountPaid())
                    + "    Balance due: Rs " + UiUtil.money(result.sale().balanceDue())
                    + "\n\nDispatched batches:\n" + batches;
            if (pdf == null) {
                UiUtil.info(this, msg + "\n\n(The PDF could not be written.)");
            } else if (JOptionPane.showConfirmDialog(this, msg + "\n\nOpen the PDF invoice?", "Order confirmed",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE) == JOptionPane.YES_OPTION) {
                UiUtil.openFile(this, pdf);
            }
        });
    }

    private String describe(SaleItem i) {
        return "  " + i.medicineName() + ": " + i.quantity() + " from batch " + i.batchNo() + " (exp " + i.expiryDate() + ")";
    }
}
