package com.pharmasync.ui;

import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

/** Date picker spinner working in LocalDate (yyyy-MM-dd). */
public class DateField extends JSpinner {

    public DateField(LocalDate initial) {
        super(new SpinnerDateModel(toDate(initial), null, null, Calendar.DAY_OF_MONTH));
        setEditor(new JSpinner.DateEditor(this, "yyyy-MM-dd"));
    }

    public LocalDate getDate() {
        return ((Date) getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public void setDate(LocalDate date) {
        setValue(toDate(date));
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
