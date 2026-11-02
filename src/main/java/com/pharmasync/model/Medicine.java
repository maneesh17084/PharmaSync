package com.pharmasync.model;

import java.math.BigDecimal;

/** Medicine master record. currentStock is the sellable quantity (active, unexpired batches). */
public record Medicine(int id, String code, String brandName, String genericName, String composition,
                       String manufacturer, Integer categoryId, String categoryName, String dosageForm,
                       String strength, String packSize, BigDecimal purchasePrice, BigDecimal sellingPrice,
                       BigDecimal gstRate, int reorderLevel, String storageCondition, boolean active,
                       int currentStock) {

    public boolean isLowStock() {
        return currentStock < reorderLevel;
    }

    public String displayName() {
        return brandName + " (" + genericName + ")";
    }

    @Override
    public String toString() {
        return code + " - " + displayName();
    }
}
