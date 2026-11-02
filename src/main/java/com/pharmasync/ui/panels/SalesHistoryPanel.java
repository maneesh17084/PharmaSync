package com.pharmasync.ui.panels;

import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Sale;
import com.pharmasync.model.SaleItem;
import com.pharmasync.model.SalePayment;
import com.pharmasync.model.SupplyType;
import com.pharmasync.service.BusinessException;
import com.pharmasync.service.Services;
import com.pharmasync.ui.DateField;
import com.pharmasync.ui.FormBuilder;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;

import java.awt.GridLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.pharmasync.ui.ListTableModel.col;

/** Sales history with invoice re-print, customer payments and outstanding balances (FR-16, FR-17). */
public class SalesHistoryPanel extends JPanel implements Refreshable {

    private final DateField from = new DateField(LocalDate.now().minusDays(30));
    private final DateField to = new DateField(LocalDate.now());
    private final JTextField search = UiUtil.searchField(18, "Customer or invoice no.");
    private final JLabel summary = new JLabel();

    private final ListTableModel<Sale> model = new ListTableModel<>(
            col("Invoice", String.class, Sale::invoiceNo),
            col("Date", LocalDateTime.class, Sale::saleDate),
            col("Customer", String.class, Sale::customerName),
            col("Gross", BigDecimal.class, Sale::subtotal),
            col("Discount", BigDecimal.class, Sale::discountAmount),
            col("GST", BigDecimal.class, Sale::taxAmount),
            col("Tax type", String.class, s -> s.supplyType() == SupplyType.INTER_STATE ? "IGST" : "CGST+SGST"),
            col("Total", BigDecimal.class, Sale::totalAmount),
            col("Paid", BigDecimal.class, Sale::amountPaid),
            col("Balance", BigDecimal.class, Sale::balanceDue),
            col("Mode", String.class, Sale::paymentMode),
            col("Payment", PaymentStatus.class, Sale::paymentStatus),
            col("By", String.class, Sale::createdBy));
    private final StyledTable table = new StyledTable(model);

    private final ListTableModel<SaleItem> items = new ListTableModel<>(
            col("Medicine", String.class, SaleItem::medicineName),
            col("Batch", String.class, SaleItem::batchNo),
            col("Expiry", LocalDate.class, SaleItem::expiryDate),
            col("Qty", Integer.class, SaleItem::quantity),
            col("Rate", BigDecimal.class, SaleItem::unitPrice),
            col("Disc %", BigDecimal.class, SaleItem::discountPct),
            col("GST %", BigDecimal.class, SaleItem::gstRate),
            col("GST", BigDecimal.class, SaleItem::taxAmount),
            col("Amount", BigDecimal.class, SaleItem::lineTotal));

    public SalesHistoryPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> model.row(r).paymentStatus() == PaymentStatus.PAID ? null : StyledTable.AMBER);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                model.selected(table).ifPresentOrElse(
                        s -> UiUtil.async(this, () -> Services.SALES.items(s.id()), items::setRows),
                        () -> items.setRows(List.of()));
            }
        });
        table.onDoubleClick(this::openPdf);
        UiUtil.onTextChange(search, this::refresh);

        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(UiUtil.heading("Invoices"));
        top.add(UiUtil.row(new JLabel("From:"), from, new JLabel("To:"), to, search,
                UiUtil.button("Show", this::refresh),
                UiUtil.button("Open PDF invoice", this::openPdf),
                UiUtil.primary(UiUtil.button("Record payment", this::recordPayment)),
                UiUtil.button("Payment history", this::showPayments),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table, "invoices"))));
        add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JLabel(" Items of selected invoice (batch-wise)"), BorderLayout.NORTH);
        bottom.add(new JScrollPane(new StyledTable(items)), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), bottom);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        LocalDate f = from.getDate();
        LocalDate t = to.getDate();
        String text = search.getText();
        UiUtil.async(this, () -> Services.SALES.find(f, t, text), rows -> {
            model.setRows(rows);
            BigDecimal total = rows.stream().map(Sale::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = rows.stream().map(Sale::amountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.setText(rows.size() + " invoices    Total: Rs " + UiUtil.money(total) + "    Received: Rs "
                    + UiUtil.money(paid) + "    Outstanding: Rs " + UiUtil.money(total.subtract(paid)));
        });
    }

    private void openPdf() {
        model.selected(table).ifPresent(s -> UiUtil.attempt(this, () -> {
            var customer = Services.MASTER.searchCustomers("", false).stream()
                    .filter(c -> c.id() == s.customerId()).findFirst().orElseThrow();
            try {
                UiUtil.openFile(this, Services.PDF.writeSaleInvoice(s, customer, Services.SALES.items(s.id())));
            } catch (IOException e) {
                throw new BusinessException("Could not create PDF: " + e.getMessage());
            }
        }));
    }

    private void recordPayment() {
        model.selected(table).ifPresent(s -> {
            if (s.balanceDue().signum() <= 0) {
                UiUtil.info(this, "Invoice " + s.invoiceNo() + " is fully paid.");
                return;
            }
            JTextField amount = new JTextField(s.balanceDue().toPlainString(), 12);
            JComboBox<String> mode = new JComboBox<>(new String[]{"CASH", "UPI", "NEFT", "CHEQUE", "CARD"});
            JTextField reference = new JTextField(16);
            DateField date = new DateField(LocalDate.now());
            JPanel form = new FormBuilder()
                    .add("Invoice", new JLabel(s.invoiceNo() + " - " + s.customerName()))
                    .add("Invoice total", new JLabel("Rs " + UiUtil.money(s.totalAmount())))
                    .add("Already paid", new JLabel("Rs " + UiUtil.money(s.amountPaid())))
                    .add("Balance due", new JLabel("Rs " + UiUtil.money(s.balanceDue())))
                    .add("Amount received *", amount).add("Mode", mode).add("Cheque / UTR / reference", reference)
                    .add("Payment date", date).build();
            if (FormBuilder.show(this, "Record customer payment", form, () -> Services.SALES.recordPayment(s.id(),
                    UiUtil.parseDecimal(amount.getText(), "Amount"), (String) mode.getSelectedItem(),
                    reference.getText(), date.getDate()))) {
                refresh();
            }
        });
    }

    private void showPayments() {
        model.selected(table).ifPresent(s -> UiUtil.async(this, () -> Services.SALES.payments(s.id()), payments -> {
            ListTableModel<SalePayment> pm = new ListTableModel<>(
                    col("Date", LocalDate.class, SalePayment::paymentDate),
                    col("Amount", BigDecimal.class, SalePayment::amount),
                    col("Mode", String.class, SalePayment::mode),
                    col("Reference", String.class, SalePayment::reference),
                    col("Recorded by", String.class, SalePayment::recordedBy),
                    col("Recorded at", LocalDateTime.class, SalePayment::createdAt));
            pm.setRows(payments);
            JPanel panel = new JPanel(new BorderLayout(4, 4));
            JScrollPane sp = new JScrollPane(new StyledTable(pm));
            sp.setPreferredSize(new Dimension(640, 200));
            panel.add(sp, BorderLayout.CENTER);
            panel.add(new JLabel("Total " + UiUtil.money(s.totalAmount()) + "    Paid " + UiUtil.money(s.amountPaid())
                    + "    Balance due " + UiUtil.money(s.balanceDue())), BorderLayout.SOUTH);
            JOptionPane.showMessageDialog(this, panel, "Payments for " + s.invoiceNo(), JOptionPane.PLAIN_MESSAGE);
        }));
    }
}
