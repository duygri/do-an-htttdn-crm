package com.htttdn.crm.service;

import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Doanh thu theo tiền đã thu thực tế (PayOS đã thanh toán, COD đã thu),
 * kèm chi phí mua hàng từ phiếu nhập và chênh lệch. Hỗ trợ nhóm ngày/tháng/quý/năm.
 */
@Service @Transactional(readOnly=true)
public class CashflowReportService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> GROUPS=Set.of("day","month","quarter","year");
    private static final String RECEIPTS="WITH receipts AS (SELECT order_id,status,CASE WHEN payment_method='PAYOS' THEN total_amount ELSE cod_collected_amount END AS amount,CASE WHEN payment_method='PAYOS' THEN paid_at ELSE cod_collected_at END AS confirmed_at FROM orders WHERE (payment_method='PAYOS' AND payment_status='PAID' AND total_amount>0) OR (payment_method='COD' AND cod_collected_at IS NOT NULL AND cod_collected_amount>0)) ";
    // Aggregate to Vietnam calendar days in SQL (bounded by the selected range); larger buckets roll up in Java.
    private static String day(String column){return "CAST(FLOOR((EXTRACT(EPOCH FROM "+column+")+25200)/86400) AS BIGINT)";}
    private final NamedParameterJdbcTemplate db;
    public CashflowReportService(NamedParameterJdbcTemplate db){this.db=db;}
    public record Series(String period,BigDecimal collected,long collectedOrders,BigDecimal purchaseCost,BigDecimal paidToSuppliers,BigDecimal difference){}
    public record Product(Long productId,String name,long quantity,BigDecimal grossItemValue){}
    public record ProductShare(Long productId,String name,long quantity,BigDecimal grossItemValue,BigDecimal share){}
    public record Unknown(long orders,BigDecimal amount){}
    public record View(Instant from,Instant to,String groupBy,String timezone,BigDecimal collected,BigDecimal purchaseCost,BigDecimal paidToSuppliers,BigDecimal difference,long collectedOrders,List<Series> series,List<Product> topProducts,List<ProductShare> products,Unknown unknownMilestone,Instant updatedAt){}
    private static final class Bucket {BigDecimal collected=BigDecimal.ZERO,purchaseCost=BigDecimal.ZERO,paidToSuppliers=BigDecimal.ZERO;long orders;}
    public View report(Instant from,Instant to,String groupBy){
        String group=groupBy==null||groupBy.isBlank()?"month":groupBy.toLowerCase(Locale.ROOT);
        if(from==null&&to==null){LocalDate today=LocalDate.now(ZONE);from=today.withDayOfYear(1).atStartOfDay(ZONE).toInstant();to=today.plusYears(1).withDayOfYear(1).atStartOfDay(ZONE).toInstant();}
        if(from==null||to==null||!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(366*5+2))>0||!GROUPS.contains(group))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REVENUE_RANGE","Chọn khoảng thời gian hợp lệ tối đa 5 năm và nhóm theo ngày/tháng/quý/năm.");
        var args=Map.<String,Object>of("from",Timestamp.from(from),"to",Timestamp.from(to));
        Map<String,Bucket> buckets=new LinkedHashMap<>();
        LocalDate first=from.atZone(ZONE).toLocalDate(),last=to.minusNanos(1).atZone(ZONE).toLocalDate();
        for(LocalDate d=first;!d.isAfter(last);d=d.plusDays(1))buckets.computeIfAbsent(key(d,group),ignored->new Bucket());
        String receiptDay=day("confirmed_at");
        db.query(RECEIPTS+"SELECT "+receiptDay+" AS bucket,COUNT(*) AS count,SUM(amount) AS amount FROM receipts WHERE confirmed_at>=:from AND confirmed_at<:to GROUP BY "+receiptDay,args,r->{var b=buckets.get(key(LocalDate.ofEpochDay(r.getLong("bucket")),group));if(b!=null){b.collected=b.collected.add(r.getBigDecimal("amount"));b.orders+=r.getLong("count");}});
        var costArgs=Map.<String,Object>of("fromDate",java.sql.Date.valueOf(first),"toDate",java.sql.Date.valueOf(last));
        db.query("SELECT invoice_date,SUM(total_amount) AS amount FROM supplier_receipts WHERE invoice_date>=:fromDate AND invoice_date<=:toDate GROUP BY invoice_date",costArgs,r->{var b=buckets.get(key(r.getDate("invoice_date").toLocalDate(),group));if(b!=null)b.purchaseCost=b.purchaseCost.add(r.getBigDecimal("amount"));});
        db.query("SELECT paid_at,SUM(amount) AS amount FROM supplier_payments WHERE paid_at>=:fromDate AND paid_at<=:toDate GROUP BY paid_at",costArgs,r->{var b=buckets.get(key(r.getDate("paid_at").toLocalDate(),group));if(b!=null)b.paidToSuppliers=b.paidToSuppliers.add(r.getBigDecimal("amount"));});
        var series=buckets.entrySet().stream().map(e->{var b=e.getValue();return new Series(e.getKey(),b.collected,b.orders,b.purchaseCost,b.paidToSuppliers,b.collected.subtract(b.purchaseCost));}).toList();
        BigDecimal collected=series.stream().map(Series::collected).reduce(BigDecimal.ZERO,BigDecimal::add),cost=series.stream().map(Series::purchaseCost).reduce(BigDecimal.ZERO,BigDecimal::add),paid=series.stream().map(Series::paidToSuppliers).reduce(BigDecimal.ZERO,BigDecimal::add);
        var top=db.query(RECEIPTS+"SELECT i.product_id,COALESCE(p.name,'Sản phẩm đã lưu') AS name,SUM(i.quantity) AS quantity,SUM(i.quantity*i.unit_price) AS gross FROM receipts r JOIN order_items i ON i.order_id=r.order_id LEFT JOIN products p ON p.product_id=i.product_id WHERE r.confirmed_at>=:from AND r.confirmed_at<:to AND r.status<>'CANCELLED' GROUP BY i.product_id,p.name ORDER BY quantity DESC,i.product_id ASC LIMIT 10",args,(r,n)->new Product(r.getLong("product_id"),r.getString("name"),r.getLong("quantity"),r.getBigDecimal("gross")));
        var productRows=db.query(RECEIPTS+"SELECT i.product_id,COALESCE(p.name,'Sản phẩm đã lưu') AS name,SUM(i.quantity) AS quantity,SUM(i.quantity*i.unit_price) AS gross FROM receipts r JOIN order_items i ON i.order_id=r.order_id LEFT JOIN products p ON p.product_id=i.product_id WHERE r.confirmed_at>=:from AND r.confirmed_at<:to AND r.status<>'CANCELLED' GROUP BY i.product_id,p.name ORDER BY quantity DESC,i.product_id ASC",args,(r,n)->new Product(r.getLong("product_id"),r.getString("name"),r.getLong("quantity"),r.getBigDecimal("gross")));
        BigDecimal totalGross=productRows.stream().map(Product::grossItemValue).reduce(BigDecimal.ZERO,BigDecimal::add);
        var products=productRows.stream().map(p->new ProductShare(p.productId(),p.name(),p.quantity(),p.grossItemValue(),totalGross.signum()==0?BigDecimal.ZERO.setScale(2):p.grossItemValue().multiply(BigDecimal.valueOf(100)).divide(totalGross,2,java.math.RoundingMode.HALF_UP))).toList();
        Unknown unknown=db.queryForObject("SELECT COUNT(*) AS count,COALESCE(SUM(total_amount),0) AS amount FROM orders WHERE payment_method='PAYOS' AND payment_status='PAID' AND paid_at IS NULL AND total_amount>0",Map.of(),(r,n)->new Unknown(r.getLong("count"),r.getBigDecimal("amount")));
        return new View(from,to,group,ZONE.getId(),collected,cost,paid,collected.subtract(cost),series.stream().mapToLong(Series::collectedOrders).sum(),series,top,products,unknown,Instant.now());
    }
    private static String key(LocalDate d,String group){
        return switch(group){
            case "day"->d.toString();
            case "quarter"->d.getYear()+"-Q"+((d.getMonthValue()-1)/3+1);
            case "year"->Integer.toString(d.getYear());
            default->YearMonth.from(d).toString();
        };
    }
}
