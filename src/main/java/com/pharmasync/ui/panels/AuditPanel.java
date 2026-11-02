package com.pharmasync.ui.panels;

import com.pharmasync.model.AuditLog;
import com.pharmasync.model.StockTransaction;
import com.pharmasync.model.TxnType;
import com.pharmasync.service.Services;
import com.pharmasync.ui.DateField;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.pharmasync.ui.ListTableModel.col;

/** User activity log and stock movement ledger (FR-19). */
public class AuditPanel extends JPanel implements Refreshable {

    private final DateField from = new DateField(LocalDate.now().minusDays(7));
    private final DateField to = new DateField(LocalDate.now());
    private final JTextField search = UiUtil.searchField(18, "Search user, action or details");

    private final ListTableModel<AuditLog> audit = new ListTableModel<>(
            col("Time", LocalDateTime.class, AuditLog::createdAt),
            col("User", String.class, AuditLog::username),
            col("Action", String.class, AuditLog::action),
            col("Entity", String.class, AuditLog::entity),
            col("Id", String.class, AuditLog::entityId),
            col("Details", String.class, AuditLog::details));
    private final StyledTable auditTable = new StyledTable(audit);

    private final ListTableModel<StockTransaction> moves = new ListTableModel<>(
            col("Time", LocalDateTime.class, StockTransaction::createdAt),
            col("Medicine", String.class, StockTransaction::medicineName),
            col("Batch", String.class, StockTransaction::batchNo),
            col("Type", TxnType.class, StockTransaction::type),
            col("Qty", Integer.class, StockTransaction::quantity),
            col("Batch balance", Integer.class, StockTransaction::balanceAfter),
            col("Reference", String.class, StockTransaction::referenceNo),
            col("Remarks", String.class, StockTransaction::remarks),
            col("User", String.class, StockTransaction::username));
    private final StyledTable movesTable = new StyledTable(moves);
    private final JTabbedPane tabs = new JTabbedPane();

    public AuditPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        auditTable.tint(r -> audit.row(r).action().endsWith("FAILED") ? StyledTable.RED : null);
        movesTable.tint(r -> moves.row(r).quantity() > 0 ? StyledTable.GREEN
                : moves.row(r).quantity() < 0 ? null : StyledTable.GREY);
        UiUtil.onTextChange(search, this::refresh);

        JPanel top = new JPanel(new GridLayout(0, 1));
        top.add(UiUtil.heading("Audit Log & Stock Ledger"));
        top.add(UiUtil.row(new JLabel("From:"), from, new JLabel("To:"), to, search,
                UiUtil.button("Show", this::refresh),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this,
                        tabs.getSelectedIndex() == 0 ? auditTable : movesTable,
                        tabs.getSelectedIndex() == 0 ? "audit_log" : "stock_ledger"))));
        add(top, BorderLayout.NORTH);
        tabs.addTab("User activity", new JScrollPane(auditTable));
        tabs.addTab("Stock movements", new JScrollPane(movesTable));
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        LocalDate f = from.getDate();
        LocalDate t = to.getDate();
        String text = search.getText();
        UiUtil.async(this, () -> new Object[]{Services.AUDIT_DAO.find(f, t, text), Services.STOCK.movements(f, t, text)},
                data -> {
                    @SuppressWarnings("unchecked") var a = (java.util.List<AuditLog>) data[0];
                    @SuppressWarnings("unchecked") var m = (java.util.List<StockTransaction>) data[1];
                    audit.setRows(a);
                    moves.setRows(m);
                    tabs.setTitleAt(0, "User activity (" + a.size() + ")");
                    tabs.setTitleAt(1, "Stock movements (" + m.size() + ")");
                });
    }
}
