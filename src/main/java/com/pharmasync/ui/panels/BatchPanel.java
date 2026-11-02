package com.pharmasync.ui.panels;

import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import com.pharmasync.model.ExpiryStatus;
import com.pharmasync.service.ExpiryService;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;
import com.pharmasync.ui.FormBuilder;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;
import com.pharmasync.ui.dialogs.BatchListDialog;
import com.pharmasync.ui.dialogs.BatchTint;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

import static com.pharmasync.ui.ListTableModel.col;

/** Batch-wise stock with expiry colouring and quarantine / return / disposal actions (Module 3). */
public class BatchPanel extends JPanel implements Refreshable {

    private final JTextField search = UiUtil.searchField(20, "Search batch, medicine or code");
    private final JComboBox<Object> status = new JComboBox<>(new Object[]{"All statuses", BatchStatus.ACTIVE,
            BatchStatus.QUARANTINED, BatchStatus.RETURNED, BatchStatus.DISPOSED});
    private final JCheckBox inStock = new JCheckBox("In stock only", true);
    private final JLabel summary = new JLabel();
    private int threshold = ExpiryService.DEFAULT_THRESHOLD_DAYS;

    private final ListTableModel<Batch> model = new ListTableModel<>(
            col("Batch", String.class, Batch::batchNo),
            col("Medicine", String.class, Batch::medicineName),
            col("Mfg date", LocalDate.class, Batch::mfgDate),
            col("Expiry date", LocalDate.class, Batch::expiryDate),
            col("Days left", Long.class, b -> ExpiryService.daysUntil(b.expiryDate(), LocalDate.now())),
            col("Qty", Integer.class, Batch::quantity),
            col("Cost", BigDecimal.class, Batch::purchasePrice),
            col("MRP", BigDecimal.class, Batch::mrp),
            col("Supplier", String.class, Batch::supplierName),
            col("Status", BatchStatus.class, Batch::status),
            col("Expiry status", ExpiryStatus.class,
                    b -> ExpiryService.classify(b.expiryDate(), LocalDate.now(), threshold)));
    private final StyledTable table = new StyledTable(model);

    public BatchPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> BatchTint.of(model.row(r), LocalDate.now(), threshold));

        UiUtil.onTextChange(search, this::refresh);
        status.addActionListener(e -> refresh());
        inStock.addActionListener(e -> refresh());

        JButton fefo = UiUtil.button("FEFO order for medicine", () -> model.selected(table).ifPresent(b ->
                Services.MASTER.searchMedicines("", false).stream().filter(m -> m.id() == b.medicineId())
                        .findFirst().ifPresent(m -> BatchListDialog.show(this, m))));
        JButton export = UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table, "batches"));

        JPanel admin = UiUtil.row(
                action("Quarantine", b -> Services.STOCK.quarantine(b.id(), ask("Reason for quarantine"))),
                action("Release", b -> Services.STOCK.release(b.id())),
                action("Return to supplier", b -> confirmThen(b, "Return all " + b.quantity() + " units of batch "
                        + b.batchNo() + " to the supplier?", () -> Services.STOCK.returnToSupplier(b.id(), ask("Remarks")))),
                action("Dispose", b -> confirmThen(b, "Dispose all " + b.quantity() + " units of batch " + b.batchNo()
                        + "? This cannot be undone.", () -> Services.STOCK.dispose(b.id(), ask("Disposal remarks")))),
                UiUtil.button("Adjust quantity", this::adjust));
        admin.setVisible(Session.isAdmin());

        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(UiUtil.heading("Batches & Expiry"));
        top.add(UiUtil.row(search, status, inStock, fefo, export));
        top.add(admin);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String text = search.getText();
        BatchStatus st = status.getSelectedItem() instanceof BatchStatus s ? s : null;
        boolean onlyInStock = inStock.isSelected();
        UiUtil.async(this, () -> {
            threshold = Services.EXPIRY.thresholdDays();
            return Services.STOCK.searchBatches(text, st, onlyInStock);
        }, rows -> {
            model.setRows(rows);
            LocalDate today = LocalDate.now();
            long expired = rows.stream().filter(b -> b.isExpired(today) && b.quantity() > 0).count();
            long near = rows.stream().filter(b -> ExpiryService.classify(b.expiryDate(), today, threshold)
                    == ExpiryStatus.NEAR_EXPIRY && b.quantity() > 0).count();
            summary.setText(rows.size() + " batches  |  red = expired (" + expired + ")  |  amber = expiring within "
                    + threshold + " days (" + near + ")  |  grey = quarantined/closed");
        });
    }

    /** Thrown when the user cancels a prompt, to abandon the action quietly. */
    private static final class Cancelled extends RuntimeException {
    }

    private JButton action(String label, Consumer<Batch> work) {
        return UiUtil.button(label, () -> model.selected(table).ifPresentOrElse(b -> {
            try {
                work.accept(b);
                refresh();
            } catch (Cancelled ignored) {
                // user backed out
            } catch (RuntimeException e) {
                UiUtil.showError(this, e);
            }
        }, () -> UiUtil.info(this, "Select a batch first.")));
    }

    private void confirmThen(Batch b, String message, Runnable work) {
        if (!UiUtil.confirm(this, message)) {
            throw new Cancelled();
        }
        work.run();
    }

    private String ask(String prompt) {
        String answer = JOptionPane.showInputDialog(this, prompt);
        if (answer == null) {
            throw new Cancelled();
        }
        return UiUtil.blankToNull(answer);
    }

    private void adjust() {
        model.selected(table).ifPresentOrElse(b -> {
            JTextField delta = new JTextField(8);
            JTextField reason = new JTextField(24);
            JPanel form = new FormBuilder()
                    .add("Batch", new JLabel(b.batchNo() + " - " + b.medicineName() + " (current " + b.quantity() + ")"))
                    .add("Change (+/- units)", delta)
                    .add("Reason", reason)
                    .build();
            if (FormBuilder.show(this, "Stock adjustment", form, () ->
                    Services.STOCK.adjust(b.id(), UiUtil.parseInt(delta.getText().replace("+", ""), "Change"), reason.getText()))) {
                refresh();
            }
        }, () -> UiUtil.info(this, "Select a batch first."));
    }
}
