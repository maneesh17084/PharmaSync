package com.pharmasync.ui.dialogs;

import com.pharmasync.service.BusinessException;
import com.pharmasync.service.Services;
import com.pharmasync.ui.FormBuilder;

import javax.swing.JPasswordField;
import java.awt.Component;

public final class ChangePasswordDialog {

    private ChangePasswordDialog() {
    }

    /** Returns true when the password was changed. */
    public static boolean show(Component parent, boolean forced) {
        JPasswordField current = new JPasswordField(18);
        JPasswordField next = new JPasswordField(18);
        JPasswordField confirm = new JPasswordField(18);
        var form = new FormBuilder()
                .add("Current password", current)
                .add("New password", next)
                .add("Confirm new password", confirm)
                .build();
        String title = forced ? "Please set a new password before continuing" : "Change password";
        return FormBuilder.show(parent, title, form, () -> {
            String n = new String(next.getPassword());
            if (!n.equals(new String(confirm.getPassword()))) {
                throw new BusinessException("New passwords do not match");
            }
            Services.AUTH.changeOwnPassword(new String(current.getPassword()), n);
        });
    }
}
