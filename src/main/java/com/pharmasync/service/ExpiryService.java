package com.pharmasync.service;

import com.pharmasync.dao.SettingsDao;
import com.pharmasync.model.ExpiryStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Classifies batches as expired, near expiry or valid using a configurable day threshold. */
public class ExpiryService {

    public static final String THRESHOLD_KEY = "near_expiry_days";
    public static final int DEFAULT_THRESHOLD_DAYS = 90;

    private final SettingsDao settings;

    public ExpiryService(SettingsDao settings) {
        this.settings = settings;
    }

    public static ExpiryStatus classify(LocalDate expiryDate, LocalDate today, int thresholdDays) {
        if (expiryDate.isBefore(today)) {
            return ExpiryStatus.EXPIRED;
        }
        if (!expiryDate.isAfter(today.plusDays(thresholdDays))) {
            return ExpiryStatus.NEAR_EXPIRY;
        }
        return ExpiryStatus.VALID;
    }

    public static long daysUntil(LocalDate expiryDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiryDate);
    }

    public ExpiryStatus classify(LocalDate expiryDate) {
        return classify(expiryDate, LocalDate.now(), thresholdDays());
    }

    public int thresholdDays() {
        return settings.getInt(THRESHOLD_KEY, DEFAULT_THRESHOLD_DAYS);
    }

    public void setThresholdDays(int days) {
        Session.requireAdmin();
        if (days < 1 || days > 730) {
            throw new BusinessException("Near-expiry threshold must be between 1 and 730 days");
        }
        settings.put(THRESHOLD_KEY, String.valueOf(days));
    }
}
