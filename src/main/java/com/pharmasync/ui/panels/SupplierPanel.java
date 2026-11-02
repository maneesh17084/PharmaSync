package com.pharmasync.ui.panels;

import com.pharmasync.model.IndianState;
import com.pharmasync.model.Supplier;

import com.pharmasync.service.Services;
import com.pharmasync.ui.FormBuilder;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;

import static com.pharmasync.ui.ListTableModel.col;

/** Pharmaceutical suppliers (FR-07). */
public class SupplierPanel extends JPanel implements Refreshable {

    private final JTextField search = UiUtil.searchField(24, "Search name, contact or phone");
    private final ListTableModel<Supplier> model = new ListTableModel<>(
            col("Name", String.class, Supplier::name),
            col("Contact", String.class, Supplier::contactPerson),
            col("Phone", String.class, Supplier::phone),
            col("Email", String.class, Supplier::email),
            col("Address", String.class, Supplier::address),
            col("State", IndianState.class, Supplier::state),
            col("GSTIN", String.class, Supplier::gstin),
            col("Drug licence", String.class, Supplier::drugLicenseNo),
            col("Active", Boolean.class, Supplier::active));
    private final StyledTable table = new StyledTable(model);

    public SupplierPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> model.row(r).active() ? null : StyledTable.GREY);
        table.onDoubleClick(() -> model.selected(table).ifPresent(this::edit));
        UiUtil.onTextChange(search, this::refresh);
        JPanel top = new JPanel(new BorderLayout());
        top.add(UiUtil.heading("Suppliers"), BorderLayout.NORTH);
        top.add(UiUtil.row(search,
                UiUtil.button("Add supplier", () -> edit(null)),
                UiUtil.button("Edit", () -> model.selected(table).ifPresent(this::edit)),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table, "suppliers"))), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        String text = search.getText();
        UiUtil.async(this, () -> Services.MASTER.searchSuppliers(text, false), model::setRows);
    }

    private void edit(Supplier s) {
        boolean isNew = s == null;
        JTextField name = new JTextField(isNew ? "" : s.name(), 26);
        JTextField contact = new JTextField(isNew ? "" : UiUtil.nz(s.contactPerson()));
        JTextField phone = new JTextField(isNew ? "" : UiUtil.nz(s.phone()));
        JTextField email = new JTextField(isNew ? "" : UiUtil.nz(s.email()));
        JTextField address = new JTextField(isNew ? "" : UiUtil.nz(s.address()));
        JComboBox<IndianState> state = UiUtil.stateCombo(isNew ? null : s.state());
        JTextField gstin = new JTextField(isNew ? "" : UiUtil.nz(s.gstin()));
        JTextField licence = new JTextField(isNew ? "" : UiUtil.nz(s.drugLicenseNo()));
        JCheckBox active = new JCheckBox("Active", isNew || s.active());
        var form = new FormBuilder().add("Name *", name).add("Contact person", contact).add("Phone", phone)
                .add("Email", email).add("Address", address).add("State *", state).add("GSTIN", gstin)
                .add("Drug licence no.", licence)
                .add("", active).build();
        if (FormBuilder.show(this, isNew ? "Add supplier" : "Edit supplier", form, () ->
                Services.MASTER.saveSupplier(new Supplier(isNew ? 0 : s.id(), name.getText().trim(),
                        UiUtil.blankToNull(contact.getText()), UiUtil.blankToNull(phone.getText()),
                        UiUtil.blankToNull(email.getText()), UiUtil.blankToNull(address.getText()),
                        (IndianState) state.getSelectedItem(), UiUtil.blankToNull(gstin.getText()), UiUtil.blankToNull(licence.getText()), active.isSelected())))) {
            refresh();
        }
    }
}
