package com.pharmasync.ui.panels;

import com.pharmasync.db.DatabaseManager;
import com.pharmasync.model.IndianState;
import com.pharmasync.service.Services;
import com.pharmasync.service.SettingsService;
import com.pharmasync.ui.FormBuilder;
import com.pharmasync.ui.Refreshable;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;

import java.util.LinkedHashMap;
import java.util.Map;

/** Expiry alert threshold and company details printed on invoices. */
public class SettingsPanel extends JPanel implements Refreshable {

    private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(90, 1, 730, 15));
    private final JTextField name = new JTextField(36);
    private final JTextField address = new JTextField(36);
    private final JTextField phone = new JTextField(36);
    private final JTextField gstin = new JTextField(36);
    private final JTextField licence = new JTextField(36);
    private final JComboBox<IndianState> state = UiUtil.stateCombo(null);
    private final JLabel sampleWarning = new JLabel();
    private final JLabel db = new JLabel();

    public SettingsPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel alerts = new FormBuilder()
                .add("Near-expiry alert threshold (days)", threshold)
                .build();
        UiUtil.section(alerts, "Expiry alerts");

        sampleWarning.setForeground(new Color(0xDC, 0x26, 0x26));
        JPanel company = new FormBuilder()
                .add("", sampleWarning)
                .add("Company name", name).add("Address", address)
                .add("State (decides CGST/SGST or IGST) *", state).add("Phone", phone)
                .add("GSTIN", gstin).add("Drug licence no.", licence)
                .build();
        UiUtil.section(company, "Company details (printed on invoices)");

        JPanel dbPanel = new JPanel(new BorderLayout());
        UiUtil.section(dbPanel, "Database");
        dbPanel.add(db, BorderLayout.CENTER);

        JPanel body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.add(alerts);
        body.add(company);
        body.add(dbPanel);
        body.add(UiUtil.row(UiUtil.button("Save settings", this::save)));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(body, BorderLayout.NORTH);
        add(UiUtil.heading("Settings"), BorderLayout.NORTH);
        add(wrap, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        UiUtil.async(this, () -> new Object[]{Services.EXPIRY.thresholdDays(), Services.SETTINGS.all(),
                Services.SETTINGS.isCompanyConfigured()}, data -> {
            threshold.setValue(data[0]);
            @SuppressWarnings("unchecked") Map<String, String> s = (Map<String, String>) data[1];
            name.setText(s.getOrDefault(SettingsService.COMPANY_NAME, ""));
            address.setText(s.getOrDefault(SettingsService.COMPANY_ADDRESS, ""));
            phone.setText(s.getOrDefault(SettingsService.COMPANY_PHONE, ""));
            gstin.setText(s.getOrDefault(SettingsService.COMPANY_GSTIN, ""));
            licence.setText(s.getOrDefault(SettingsService.COMPANY_LICENSE, ""));
            state.setSelectedItem(IndianState.ofCode(s.get(SettingsService.COMPANY_STATE)));
            sampleWarning.setText((Boolean) data[2] ? ""

                    : "These are demo company details. Enter your real company name and GSTIN; until then every "
                    + "PDF is marked SAMPLE.");
            db.setText("  SQLite database file: " + DatabaseManager.config().getFile());
        });
    }

    private void save() {
        if (UiUtil.attempt(this, () -> {
            Services.EXPIRY.setThresholdDays((Integer) threshold.getValue());
            Map<String, String> values = new LinkedHashMap<>();
            values.put(SettingsService.COMPANY_NAME, name.getText());
            values.put(SettingsService.COMPANY_ADDRESS, address.getText());
            values.put(SettingsService.COMPANY_PHONE, phone.getText());
            values.put(SettingsService.COMPANY_GSTIN, gstin.getText());
            values.put(SettingsService.COMPANY_LICENSE, licence.getText());
            values.put(SettingsService.COMPANY_STATE, IndianState.codeOf((IndianState) state.getSelectedItem()));
            Services.SETTINGS.saveAll(values);
        })) {
            UiUtil.info(this, "Settings saved.");
            refresh();
        }
    }
}
