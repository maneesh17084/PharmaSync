package com.pharmasync.ui.panels;

import com.pharmasync.model.Category;
import com.pharmasync.service.Services;
import com.pharmasync.ui.FormBuilder;
import com.pharmasync.ui.ListTableModel;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.StyledTable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;

import static com.pharmasync.ui.ListTableModel.col;

/** Medicine categories (FR-04). */
public class CategoryPanel extends JPanel implements Refreshable {

    private final ListTableModel<Category> model = new ListTableModel<>(
            col("Name", String.class, Category::name),
            col("Description", String.class, Category::description));
    private final StyledTable table = new StyledTable(model);

    public CategoryPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.onDoubleClick(() -> model.selected(table).ifPresent(this::edit));
        JPanel top = new JPanel(new BorderLayout());
        top.add(UiUtil.heading("Categories"), BorderLayout.NORTH);
        top.add(UiUtil.row(
                UiUtil.button("Add category", () -> edit(null)),
                UiUtil.button("Edit", () -> model.selected(table).ifPresent(this::edit)),
                UiUtil.button("Delete", () -> model.selected(table).ifPresent(c -> {
                    if (UiUtil.confirm(this, "Delete category " + c.name() + "?")
                            && UiUtil.attempt(this, () -> Services.MASTER.deleteCategory(c))) {
                        refresh();
                    }
                }))), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        UiUtil.async(this, Services.MASTER::listCategories, model::setRows);
    }

    private void edit(Category existing) {
        JTextField name = new JTextField(existing == null ? "" : existing.name(), 24);
        JTextField desc = new JTextField(existing == null ? "" : UiUtil.nz(existing.description()), 24);
        var form = new FormBuilder().add("Name *", name).add("Description", desc).build();
        if (FormBuilder.show(this, existing == null ? "Add category" : "Edit category", form, () ->
                Services.MASTER.saveCategory(new Category(existing == null ? 0 : existing.id(), name.getText(),
                        UiUtil.blankToNull(desc.getText()))))) {
            refresh();
        }
    }
}
