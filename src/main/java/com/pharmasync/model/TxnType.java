package com.pharmasync.model;

/** Kinds of stock movement recorded in stock_transactions. */
public enum TxnType {
    OPENING, PURCHASE_IN, SALE_OUT, ADJUSTMENT, QUARANTINE, RETURN, DISPOSAL
}
