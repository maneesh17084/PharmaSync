package com.pharmasync.ui.panels;

import com.pharmasync.model.Customer;
import com.pharmasync.model.CustomerType;
import com.pharmasync.model.IndianState;

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

/** Retail pharmacies, hospitals and clinics (FR-10). */
public class CustomerPanel extends JPanel implements Refreshable {

    private final JTextField search = UiUtil.searchField(24, "Search name, contact or phone");
    private final ListTableModel<Customer> model = new ListTableModel<>(
            col("Name", String.class, Customer::name),
            col("Type", CustomerType.class, Customer::type),
            col("Contact", String.class, Customer::contactPerson),
            col("Phone", String.class, Customer::phone),
            col("Email", String.class, Customer::email),
            col("Address", String.class, Customer::address),
            col("State", IndianState.class, Customer::state),
            col("GSTIN", String.class, Customer::gstin),
            col("Drug licence", String.class, Customer::drugLicenseNo),
            col("Active", Boolean.class, Customer::active));
    private final StyledTable table = new StyledTable(model);

    public CustomerPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> model.row(r).active() ? null : StyledTable.GREY);
        table.onDoubleClick(() -> model.selected(table).ifPresent(this::edit));
        UiUtil.onTextChange(search, this::refresh);
        JPanel top = new JPanel(new BorderLayout());
        top.add(UiUtil.heading("Customers - pharmacies, hospitals & clinics"), BorderLayout.NORTH);
        top.add(UiUtil.row(search,
                UiUtil.button("Register customer", () -> edit(null)),
                UiUtil.button("Edit", () -> model.selected(table).ifPresent(this::edit)),
                UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table, "customers"))), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        String text = search.getText();
        UiUtil.async(this, () -> Services.MASTER.searchCustomers(text, false), model::setRows);
    }

    private void edit(Customer c) {
        boolean isNew = c == null;
        JTextField name = new JTextField(isNew ? "" : c.name(), 26);
        JComboBox<CustomerType> type = new JComboBox<>(CustomerType.values());
        type.setSelectedItem(isNew ? CustomerType.PHARMACY : c.type());
        JTextField contact = new JTextField(isNew ? "" : UiUtil.nz(c.contactPerson()));
        JTextField phone = new JTextField(isNew ? "" : UiUtil.nz(c.phone()));
        JTextField email = new JTextField(isNew ? "" : UiUtil.nz(c.email()));
        JTextField address = new JTextField(isNew ? "" : UiUtil.nz(c.address()));
        JComboBox<IndianState> state = UiUtil.stateCombo(isNew ? null : c.state());
        JTextField gstin = new JTextField(isNew ? "" : UiUtil.nz(c.gstin()));
        JTextField licence = new JTextField(isNew ? "" : UiUtil.nz(c.drugLicenseNo()));
        JCheckBox active = new JCheckBox("Active", isNew || c.active());
        var form = new FormBuilder().add("Name *", name).add("Type", type).add("Contact person", contact)
                .add("Phone", phone).add("Email", email).add("Address", address).add("State (place of supply) *", state)
                .add("GSTIN", gstin)
                .add("Drug licence no.", licence).add("", active).build();
        if (FormBuilder.show(this, isNew ? "Register customer" : "Edit customer", form, () ->
                Services.MASTER.saveCustomer(new Customer(isNew ? 0 : c.id(), name.getText().trim(),
                        (CustomerType) type.getSelectedItem(), UiUtil.blankToNull(contact.getText()),
                        UiUtil.blankToNull(phone.getText()), UiUtil.blankToNull(email.getText()),
                        UiUtil.blankToNull(address.getText()), (IndianState) state.getSelectedItem(),
                        UiUtil.blankToNull(gstin.getText()),
                        UiUtil.blankToNull(licence.getText()), active.isSelected())))) {
            refresh();
        }
    }
}
