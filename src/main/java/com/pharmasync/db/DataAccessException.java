package com.pharmasync.db;

/** Unchecked wrapper for SQL errors so DAO and service code stays readable. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
