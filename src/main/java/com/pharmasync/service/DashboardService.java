package com.pharmasync.service;

import com.pharmasync.db.Jdbc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Figures for the dashboard tiles and charts. */
public class DashboardService {

    public record Kpis(long activeMedicines, BigDecimal stockValue, long lowStock, long nearExpiry, long expired,
                       BigDecimal todaySales, long todayInvoices, BigDecimal monthSales, BigDecimal receivables) {
    }

    private final StockService stock;

    public DashboardService(StockService stock) {
        this.stock = stock;
    }

    public Kpis kpis() {
        LocalDate today = LocalDate.now();
        return new Kpis(
                Jdbc.queryLong("SELECT COUNT(*) FROM medicines WHERE active = TRUE"),
                Jdbc.queryDecimal("SELECT SUM(quantity * purchase_price) FROM batches "
                        + "WHERE status = 'ACTIVE' AND expiry_date >= date('now','localtime')"),
                stock.lowStock().size(),
                stock.nearExpiry().size(),
                stock.expiredInStock().size(),
                Jdbc.queryDecimal("SELECT SUM(total_amount) FROM sales WHERE sale_date >= ? AND sale_date < ?",
                        today, today.plusDays(1)),
                Jdbc.queryLong("SELECT COUNT(*) FROM sales WHERE sale_date >= ? AND sale_date < ?",
                        today, today.plusDays(1)),
                Jdbc.queryDecimal("SELECT SUM(total_amount) FROM sales WHERE sale_date >= ? AND sale_date < ?",
                        today.withDayOfMonth(1), today.plusDays(1)),
                Jdbc.queryDecimal("SELECT SUM(total_amount - amount_paid) FROM sales"));

    }

    /** Revenue per day for the last n days, including days without sales. */
    public Map<LocalDate, BigDecimal> dailySales(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, BigDecimal> series = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            series.put(start.plusDays(i), BigDecimal.ZERO);
        }
        Jdbc.query("SELECT DATE(sale_date) AS d, SUM(total_amount) AS t FROM sales WHERE sale_date >= ? "
                + "GROUP BY DATE(sale_date)", rs -> series.put(Jdbc.getDate(rs, "d"), Jdbc.getDecimal(rs, "t")), start);
        return series;
    }

    /** Best-selling medicines by quantity over the last n days. */
    public Map<String, Long> topMedicines(int days, int limit) {
        Map<String, Long> top = new LinkedHashMap<>();
        Jdbc.query("SELECT m.brand_name, SUM(si.quantity) AS q FROM sales_items si JOIN sales s ON s.id = si.sale_id "
                        + "JOIN medicines m ON m.id = si.medicine_id WHERE s.sale_date >= ? "
                        + "GROUP BY m.id, m.brand_name ORDER BY q DESC LIMIT " + limit,
                rs -> top.put(rs.getString(1), rs.getLong(2)), LocalDate.now().minusDays(days - 1L));
        return top;
    }
}
