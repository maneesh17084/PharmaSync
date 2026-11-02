package com.pharmasync.ui.panels;

import com.pharmasync.model.Category;
import com.pharmasync.model.Medicine;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;
import com.pharmasync.ui.FormBuilder;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;
import com.pharmasync.ui.dialogs.BatchListDialog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.pharmasync.ui.ListTableModel.col;

/** Medicine master: add, edit and search (Module 2, FR-03). */
public class MedicinePanel extends JPanel implements Refreshable {

    private static final String[] FORMS = {"Tablet", "Capsule", "Syrup", "Suspension", "Injection", "Ointment",
            "Cream", "Drops", "Inhaler", "Powder", "Chewable Tablet", "Supply"};
    private static final String[] GST = {"0", "5", "12", "18", "28"};

    private final JTextField search = UiUtil.searchField(24, "Search code, brand, generic or maker");
    private final JCheckBox showInactive = new JCheckBox("Show inactive");
    private final JLabel count = new JLabel();
    private final ListTableModel<Medicine> model = new ListTableModel<>(
            col("Code", String.class, Medicine::code),
            col("Brand", String.class, Medicine::brandName),
            col("Generic", String.class, Medicine::genericName),
            col("Composition", String.class, Medicine::composition),
            col("Category", String.class, Medicine::categoryName),
            col("Form", String.class, Medicine::dosageForm),
            col("Strength", String.class, Medicine::strength),
            col("Manufacturer", String.class, Medicine::manufacturer),
            col("Cost", BigDecimal.class, Medicine::purchasePrice),
            col("Price", BigDecimal.class, Medicine::sellingPrice),
            col("GST %", BigDecimal.class, Medicine::gstRate),
            col("Reorder", Integer.class, Medicine::reorderLevel),
            col("Stock", Integer.class, Medicine::currentStock),
            col("Status", String.class, m -> !m.active() ? "INACTIVE" : m.isLowStock() ? "LOW STOCK" : "OK"));
    private final StyledTable table = new StyledTable(model);

    public MedicinePanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> {
            Medicine m = model.row(r);
            return !m.active() ? StyledTable.GREY : m.isLowStock() ? StyledTable.AMBER : null;
        });

        JButton add = UiUtil.button("Add medicine", () -> edit(null));
        JButton edit = UiUtil.button("Edit", () -> model.selected(table).ifPresent(this::edit));
        JButton batches = UiUtil.button("View batches (FEFO)", () -> model.selected(table)
                .ifPresent(m -> BatchListDialog.show(this, m)));
        JButton export = UiUtil.button("Export CSV", () -> UiUtil.exportCsv(this, table, "medicines"));
        add.setVisible(Session.isAdmin());
        edit.setVisible(Session.isAdmin());
        table.onDoubleClick(() -> model.selected(table).ifPresent(m -> {
            if (Session.isAdmin()) {
                edit(m);
            } else {
                BatchListDialog.show(this, m);
            }
        }));

        UiUtil.onTextChange(search, this::refresh);
        showInactive.addActionListener(e -> refresh());

        JPanel top = new JPanel(new BorderLayout());
        top.add(UiUtil.heading("Medicines"), BorderLayout.NORTH);
        top.add(UiUtil.row(search, showInactive, add, edit, batches, export, count), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        String text = search.getText();
        boolean activeOnly = !showInactive.isSelected();
        UiUtil.async(this, () -> Services.MASTER.searchMedicines(text, activeOnly), rows -> {
            model.setRows(rows);
            long low = rows.stream().filter(m -> m.active() && m.isLowStock()).count();
            count.setText(rows.size() + " medicines, " + low + " low on stock");
        });
    }

    private void edit(Medicine existing) {
        boolean isNew = existing == null;
        JTextField code = new JTextField(isNew ? Services.MASTER.suggestMedicineCode() : existing.code(), 20);
        JTextField brand = new JTextField(isNew ? "" : existing.brandName());
        JTextField generic = new JTextField(isNew ? "" : existing.genericName());
        JTextField composition = new JTextField(isNew ? "" : UiUtil.nz(existing.composition()));
        JTextField manufacturer = new JTextField(isNew ? "" : UiUtil.nz(existing.manufacturer()));
        List<Category> cats = new ArrayList<>();
        cats.add(new Category(0, "(none)", null));
        cats.addAll(Services.MASTER.listCategories());
        JComboBox<Category> category = new JComboBox<>(cats.toArray(new Category[0]));
        if (!isNew && existing.categoryId() != null) {
            cats.stream().filter(c -> c.id() == existing.categoryId()).findFirst().ifPresent(category::setSelectedItem);
        }
        JComboBox<String> form = new JComboBox<>(FORMS);
        form.setEditable(true);
        form.setSelectedItem(isNew ? "Tablet" : UiUtil.nz(existing.dosageForm()));
        JTextField strength = new JTextField(isNew ? "" : UiUtil.nz(existing.strength()));
        JTextField pack = new JTextField(isNew ? "" : UiUtil.nz(existing.packSize()));
        JTextField cost = new JTextField(isNew ? "0.00" : existing.purchasePrice().toPlainString());
        JTextField price = new JTextField(isNew ? "0.00" : existing.sellingPrice().toPlainString());
        JComboBox<String> gst = new JComboBox<>(GST);
        gst.setEditable(true);
        gst.setSelectedItem(isNew ? "12" : existing.gstRate().stripTrailingZeros().toPlainString());
        JTextField reorder = new JTextField(isNew ? "100" : String.valueOf(existing.reorderLevel()));
        JTextField storage = new JTextField(isNew ? "Below 25C" : UiUtil.nz(existing.storageCondition()));
        JCheckBox active = new JCheckBox("Active (available for purchase and sale)", isNew || existing.active());

        JPanel p = new FormBuilder()
                .add("Code *", code).add("Brand name *", brand).add("Generic name *", generic)
                .add("Composition", composition).add("Manufacturer", manufacturer).add("Category", category)
                .add("Dosage form", form).add("Strength", strength).add("Pack size", pack)
                .add("Purchase price *", cost).add("Selling price *", price).add("GST %", gst)
                .add("Reorder level", reorder).add("Storage condition", storage).add("", active)
                .build();

        boolean saved = FormBuilder.show(this, isNew ? "Add medicine" : "Edit medicine", p, () -> {
            Category c = (Category) category.getSelectedItem();
            Services.MASTER.saveMedicine(new Medicine(isNew ? 0 : existing.id(), code.getText().trim(),
                    brand.getText().trim(), generic.getText().trim(), UiUtil.blankToNull(composition.getText()),
                    UiUtil.blankToNull(manufacturer.getText()), c == null || c.id() == 0 ? null : c.id(), null,
                    UiUtil.blankToNull(String.valueOf(form.getSelectedItem())), UiUtil.blankToNull(strength.getText()),
                    UiUtil.blankToNull(pack.getText()), UiUtil.parseDecimal(cost.getText(), "Purchase price"),
                    UiUtil.parseDecimal(price.getText(), "Selling price"),
                    UiUtil.parseDecimal(String.valueOf(gst.getSelectedItem()), "GST %"),
                    UiUtil.parseInt(reorder.getText(), "Reorder level"), UiUtil.blankToNull(storage.getText()),
                    active.isSelected(), 0));
        });
        if (saved) {
            refresh();
        }
    }
}
