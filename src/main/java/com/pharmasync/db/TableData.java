package com.pharmasync.db;

import java.util.List;

/** Column names plus rows, as returned by ad-hoc report queries. */
public record TableData(List<String> columns, List<Object[]> rows) {
}
