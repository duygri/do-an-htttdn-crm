package com.htttdn.crm.service;

import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class CustomerLifecycleQuery {
    private final JdbcTemplate db;
    public CustomerLifecycleQuery(JdbcTemplate db) { this.db=db; }
    public record Filters(String q, String stage, Boolean dormant, Long minCompletedOrders, Long maxCompletedOrders,
        BigDecimal minCompletedValue, BigDecimal maxCompletedValue, LocalDate lastPurchaseFrom, LocalDate lastPurchaseTo,
        String sort, String direction, int page, int size) {}
    public record Customer(long id, String fullName, String email, String phone, Integer age, String preferences,
        Instant createdAt, boolean locked, String lockReason, long completedOrders, BigDecimal completedValue,
        Instant lastCompletedOrderAt, CustomerLifecycle.Segment segmentation) {}
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CUSTOMER_FILTER",message); }
    private static final Map<String,String> SORTS=Map.of("createdAt","created_at","completedOrders","completed_orders",
        "completedValue","completed_value","lastPurchaseAt","last_completed_order_at");
    public CustomerProfileService.Page<Customer> list(Filters f) { return list(f,LocalDate.now(CustomerLifecycle.ZONE)); }
    CustomerProfileService.Page<Customer> list(Filters f, LocalDate today) {
        if(f.page()<0 || f.size()<1 || f.size()>100) throw invalid("Phân trang không hợp lệ.");
        if(f.q()!=null && f.q().length()>255) throw invalid("Từ khóa tối đa 255 ký tự.");
        if(f.stage()!=null && !Set.of("NO_PURCHASE","FIRST_PURCHASE","RETURNING","LOYAL").contains(f.stage())) throw invalid("Nhóm khách hàng không hợp lệ.");
        if(!SORTS.containsKey(f.sort()) || !Set.of("asc","desc").contains(f.direction())) throw invalid("Sắp xếp không hợp lệ.");
        if(f.minCompletedOrders()!=null && f.minCompletedOrders()<0 || f.maxCompletedOrders()!=null && f.maxCompletedOrders()<0
            || f.minCompletedOrders()!=null && f.maxCompletedOrders()!=null && f.minCompletedOrders()>f.maxCompletedOrders()) throw invalid("Khoảng số đơn không hợp lệ.");
        if(f.minCompletedValue()!=null && f.minCompletedValue().signum()<0 || f.maxCompletedValue()!=null && f.maxCompletedValue().signum()<0
            || f.minCompletedValue()!=null && f.maxCompletedValue()!=null && f.minCompletedValue().compareTo(f.maxCompletedValue())>0) throw invalid("Khoảng giá trị không hợp lệ.");
        if(f.lastPurchaseFrom()!=null && f.lastPurchaseTo()!=null && f.lastPurchaseFrom().isAfter(f.lastPurchaseTo())) throw invalid("Khoảng ngày mua không hợp lệ.");
        String cte="""
            with purchases as (
              select customer_id,count(*) completed_orders,sum(total_amount) completed_value,max(created_at) last_completed_order_at
              from orders where status='COMPLETED' group by customer_id
            ), metrics as (
              select c.customer_id,c.full_name,c.email,c.phone,c.age,c.preferences,c.created_at,c.locked,c.lock_reason,
                coalesce(p.completed_orders,0) completed_orders,coalesce(p.completed_value,0) completed_value,p.last_completed_order_at
              from customers c left join purchases p on p.customer_id=c.customer_id
              where c.role='CUSTOMER' and c.deleted_at is null
            ), classified as (select metrics.*, %s stage from metrics)
            """.formatted(CustomerLifecycle.stageSql());
        StringBuilder where=new StringBuilder(" where 1=1");
        List<Object> args=new ArrayList<>();
        if(f.q()!=null && !f.q().isBlank()) {
            where.append(" and (lower(full_name) like ? escape '!' or lower(email) like ? escape '!' or lower(phone) like ? escape '!')");
            String q="%"+f.q().trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            args.add(q);args.add(q);args.add(q);
        }
        if(f.stage()!=null) add(where,args,"stage = ?",f.stage());
        if(f.dormant()!=null) {
            Instant cutoff=today.minusDays(CustomerLifecycle.DORMANT_DAYS).plusDays(1).atStartOfDay(CustomerLifecycle.ZONE).toInstant();
            add(where,args,f.dormant()?"(completed_orders>0 and last_completed_order_at < ?)":"(completed_orders=0 or last_completed_order_at is null or last_completed_order_at >= ?)",java.sql.Timestamp.from(cutoff));
        }
        if(f.minCompletedOrders()!=null) add(where,args,"completed_orders >= ?",f.minCompletedOrders());
        if(f.maxCompletedOrders()!=null) add(where,args,"completed_orders <= ?",f.maxCompletedOrders());
        if(f.minCompletedValue()!=null) add(where,args,"completed_value >= ?",f.minCompletedValue());
        if(f.maxCompletedValue()!=null) add(where,args,"completed_value <= ?",f.maxCompletedValue());
        if(f.lastPurchaseFrom()!=null) add(where,args,"last_completed_order_at >= ?",java.sql.Timestamp.from(f.lastPurchaseFrom().atStartOfDay(CustomerLifecycle.ZONE).toInstant()));
        if(f.lastPurchaseTo()!=null) add(where,args,"last_completed_order_at < ?",java.sql.Timestamp.from(f.lastPurchaseTo().plusDays(1).atStartOfDay(CustomerLifecycle.ZONE).toInstant()));
        long count=db.queryForObject(cte+"select count(*) from classified"+where,Long.class,args.toArray());
        args.add(f.size());args.add((long)f.page()*f.size());
        var rows=db.query(cte+"select * from classified"+where+" order by "+SORTS.get(f.sort())+" "+f.direction()+" nulls last,customer_id desc limit ? offset ?",(r,n)->{
            Instant last=r.getTimestamp("last_completed_order_at")==null?null:r.getTimestamp("last_completed_order_at").toInstant();
            long completed=r.getLong("completed_orders");BigDecimal value=r.getBigDecimal("completed_value");
            return new Customer(r.getLong("customer_id"),r.getString("full_name"),r.getString("email"),r.getString("phone"),
                (Integer)r.getObject("age"),r.getString("preferences"),r.getTimestamp("created_at").toInstant(),r.getBoolean("locked"),
                r.getString("lock_reason"),completed,value,last,CustomerLifecycle.calculate(completed,value,last,today));
        },args.toArray());
        return new CustomerProfileService.Page<>(rows,count,(count+f.size()-1)/f.size(),f.page(),f.size());
    }
    private static void add(StringBuilder where,List<Object> args,String condition,Object value) { where.append(" and ").append(condition);args.add(value); }
}
