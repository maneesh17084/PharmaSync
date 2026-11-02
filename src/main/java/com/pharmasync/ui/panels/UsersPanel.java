package com.pharmasync.ui.panels;

import com.pharmasync.model.Role;
import com.pharmasync.model.User;
import com.pharmasync.service.BusinessException;
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
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDateTime;

import static com.pharmasync.ui.ListTableModel.col;

/** Admin and staff accounts (FR-02). */
public class UsersPanel extends JPanel implements Refreshable {

    private final ListTableModel<User> model = new ListTableModel<>(
            col("Username", String.class, User::username),
            col("Full name", String.class, User::fullName),
            col("Role", Role.class, User::role),
            col("Active", Boolean.class, User::active),
            col("Must change password", Boolean.class, User::mustChangePassword),
            col("Last login", LocalDateTime.class, User::lastLogin));
    private final StyledTable table = new StyledTable(model);

    public UsersPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.tint(r -> model.row(r).active() ? null : StyledTable.GREY);
        table.onDoubleClick(() -> model.selected(table).ifPresent(this::edit));
        JPanel top = new JPanel(new BorderLayout());
        top.add(UiUtil.heading("Users"), BorderLayout.NORTH);
        top.add(UiUtil.row(
                UiUtil.button("Add user", this::add),
                UiUtil.button("Edit", () -> model.selected(table).ifPresent(this::edit)),
                UiUtil.button("Reset password", () -> model.selected(table).ifPresent(this::resetPassword)),
                new JLabel("Staff can sell, receive stock and manage customers. Admins can do everything.")),
                BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        UiUtil.async(this, Services.AUTH::listUsers, model::setRows);
    }

    private void add() {
        JTextField username = new JTextField(18);
        JTextField fullName = new JTextField(18);
        JComboBox<Role> role = new JComboBox<>(Role.values());
        role.setSelectedItem(Role.STAFF);
        JPasswordField password = new JPasswordField(18);
        var form = new FormBuilder().add("Username *", username).add("Full name *", fullName).add("Role", role)
                .add("Temporary password *", password).build();
        if (FormBuilder.show(this, "Add user", form, () -> Services.AUTH.createUser(username.getText(),
                fullName.getText(), (Role) role.getSelectedItem(), new String(password.getPassword())))) {
            UiUtil.info(this, "User created. They must change the password at first login.");
            refresh();
        }
    }

    private void edit(User u) {
        JTextField fullName = new JTextField(u.fullName(), 18);
        JComboBox<Role> role = new JComboBox<>(Role.values());
        role.setSelectedItem(u.role());
        JCheckBox active = new JCheckBox("Active", u.active());
        var form = new FormBuilder().add("Username", new JLabel(u.username())).add("Full name *", fullName)
                .add("Role", role).add("", active).build();
        if (FormBuilder.show(this, "Edit user", form, () -> Services.AUTH.updateUser(u.id(), fullName.getText(),
                (Role) role.getSelectedItem(), active.isSelected()))) {
            refresh();
        }
    }

    private void resetPassword(User u) {
        JPasswordField p1 = new JPasswordField(18);
        JPasswordField p2 = new JPasswordField(18);
        var form = new FormBuilder().add("New temporary password", p1).add("Confirm", p2).build();
        if (FormBuilder.show(this, "Reset password for " + u.username(), form, () -> {
            String a = new String(p1.getPassword());
            if (!a.equals(new String(p2.getPassword()))) {
                throw new BusinessException("Passwords do not match");
            }
            Services.AUTH.resetPassword(u.id(), a);
        })) {
            UiUtil.info(this, "Password reset. " + u.username() + " must change it at next login.");
            refresh();
        }
    }
}
