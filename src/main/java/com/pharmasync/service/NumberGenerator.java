package com.pharmasync.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Document numbers such as INV-20261101-0007 and PO-20261101-0002. */
public final class NumberGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private NumberGenerator() {
    }

    public static String prefix(String type, LocalDate date) {
        return type + "-" + date.format(DAY) + "-";
    }

    /** Next number after lastIssued (null when none has been issued with this prefix yet). */
    public static String next(String prefix, String lastIssued) {
        int seq = 0;
        if (lastIssued != null && lastIssued.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(lastIssued.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                seq = 0;
            }
        }
        return prefix + String.format("%04d", seq + 1);
    }
}
