package com.pharmasync.model;

/** Lifecycle of a batch. Only ACTIVE, unexpired batches can be sold. */
public enum BatchStatus {
    ACTIVE, QUARANTINED, RETURNED, DISPOSED
}
