package com.htttdn.crm.service;

import com.htttdn.crm.util.Csv;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only CSV exports for revenue, orders and customers. Every file is capped at 10.000 rows. */
@Service
public class CsvReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);
    private static final int MAX_ROWS = 10000;
    private final NamedParameterJdbcTemplate db;
    private final CashflowReportService cashflow;

    public CsvReportService(NamedParameterJdbcTemplate db, CashflowReportService cashflow) {
        this.db = db;
        this.cashflow = cashflow;
    }

    public ResponseEntity<String> revenueCsv(Instant from, Instant to, String groupBy) {
        var report = cashflow.report(from, to, groupBy);
        List<String> rows = new ArrayList<>();
        rows.add(Csv.line("period", "collected", "collectedOrders", "purchaseCost", "difference"));
        report.series().forEach(series -> rows.add(Csv.line(
            series.period(), plain(series.collected()), series.collectedOrders(),
            plain(series.purchaseCost()), plain(series.difference()))));
        return Csv.response("doanh-thu.csv", rows);
    }

    public ResponseEntity<String> ordersCsv(String status, String q, Instant from, Instant to) {
        Map<String, Object> args = new HashMap<>();
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        args.put("status", status == null ? "" : status.trim());
        args.put("q", search);
        args.put("like", "%" + search + "%");
        args.put("from", from == null ? null : Timestamp.from(from));
        args.put("to", to == null ? null : Timestamp.from(to));
        List<String> rows = new ArrayList<>();
        rows.add(Csv.line("orderCode", "customer", "phone", "status", "paymentMethod", "paymentStatus", "totalAmount", "createdAt"));
        db.query("""
            SELECT o.order_code,c.full_name,COALESCE(c.phone,'') AS phone,o.status,
                   COALESCE(o.payment_method,'') AS payment_method,COALESCE(o.payment_status,'') AS payment_status,
                   o.total_amount,o.created_at
            FROM orders o JOIN customers c ON c.customer_id=o.customer_id
            WHERE (:status='' OR o.status=:status)
              AND (:q='' OR lower(c.full_name) LIKE :like OR lower(COALESCE(c.email,'')) LIKE :like
                   OR CAST(o.order_code AS VARCHAR) LIKE :like)
              AND (:from IS NULL OR o.created_at>=:from)
              AND (:to IS NULL OR o.created_at<:to)
            ORDER BY o.created_at DESC,o.order_id DESC
            """ + " LIMIT " + MAX_ROWS, args, (org.springframework.jdbc.core.RowCallbackHandler) row -> rows.add(Csv.line(
                row.getLong("order_code"), row.getString("full_name"), row.getString("phone"), row.getString("status"),
                row.getString("payment_method"), row.getString("payment_status"),
                plain(row.getBigDecimal("total_amount")), STAMP.format(row.getTimestamp("created_at").toInstant()))));
        return Csv.response("don-hang.csv", rows);
    }

    public ResponseEntity<String> customersCsv(String q) {
        Map<String, Object> args = new HashMap<>();
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        args.put("q", search);
        args.put("like", "%" + search + "%");
        List<String> rows = new ArrayList<>();
        rows.add(Csv.line("fullName", "email", "phone", "orderCount", "completedValue", "createdAt"));
        db.query("""
            SELECT c.full_name,COALESCE(c.email,'') AS email,COALESCE(c.phone,'') AS phone,
                   COUNT(o.order_id) AS order_count,
                   COALESCE(SUM(CASE WHEN o.status='COMPLETED' THEN o.total_amount ELSE 0 END),0) AS completed_value,
                   c.created_at
            FROM customers c LEFT JOIN orders o ON o.customer_id=c.customer_id
            WHERE c.role='CUSTOMER' AND c.deleted_at IS NULL
              AND (:q='' OR lower(c.full_name) LIKE :like OR lower(COALESCE(c.email,'')) LIKE :like
                   OR COALESCE(c.phone,'') LIKE :like)
            GROUP BY c.customer_id,c.full_name,c.email,c.phone,c.created_at
            ORDER BY c.created_at DESC,c.customer_id DESC
            """ + " LIMIT " + MAX_ROWS, args, (org.springframework.jdbc.core.RowCallbackHandler) row -> rows.add(Csv.line(
                row.getString("full_name"), row.getString("email"), row.getString("phone"),
                row.getLong("order_count"), plain(row.getBigDecimal("completed_value")),
                STAMP.format(row.getTimestamp("created_at").toInstant()))));
        return Csv.response("khach-hang.csv", rows);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
