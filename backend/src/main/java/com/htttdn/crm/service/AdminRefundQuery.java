package com.htttdn.crm.service;

import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Read-only oversight of the refund ledger for the Admin portal. Rows are joined through the order and the customer so
 * the Admin never needs the manager screens; bank references and notes deliberately stay out of the payload.
 */
@Service @Transactional(readOnly=true)
public class AdminRefundQuery {
    private static final String JOIN=" FROM order_refunds r JOIN orders o ON o.order_id=r.order_id JOIN customers c ON c.customer_id=o.customer_id";
    private static final Set<String> STATUSES=Set.of("PENDING","COMPLETED","REJECTED");
    private final NamedParameterJdbcTemplate db;
    public AdminRefundQuery(NamedParameterJdbcTemplate db){this.db=db;}
    public record Row(Long id,Long orderId,Long orderCode,String customerName,BigDecimal amount,String status,String reasonType,String method,Instant requestedAt,Instant processedAt){}
    public record Page(List<Row> content,long totalElements,int totalPages,int number,int size){}
    public record Summary(long pendingCount,BigDecimal pendingAmount,long completedCount,BigDecimal completedAmount,long rejectedCount){}
    private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_FILTER","Bộ lọc hoàn tiền không hợp lệ.");}
    public Page list(String status,Instant from,Instant to,String q,int page,int size){
        if(page<0||size<1||size>100||(q!=null&&q.length()>120))throw invalid();
        if(status!=null&&!status.isBlank()&&!STATUSES.contains(status))throw invalid();
        if(from!=null&&to!=null&&!from.isBefore(to))throw invalid();
        var args=new HashMap<String,Object>();var where=new ArrayList<String>();where.add("1=1");
        if(status!=null&&!status.isBlank()){args.put("status",status);where.add("r.status=:status");}
        if(from!=null){args.put("from",Timestamp.from(from));where.add("r.requested_at>=:from");}
        if(to!=null){args.put("to",Timestamp.from(to));where.add("r.requested_at<:to");}
        if(q!=null&&!q.isBlank()){
            args.put("q","%"+q.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            where.add("(lower(c.full_name) LIKE :q ESCAPE '!' OR CAST(o.order_code AS varchar) LIKE :q ESCAPE '!')");
        }
        String sql=JOIN+" WHERE "+String.join(" AND ",where);
        Long total=db.queryForObject("SELECT count(*)"+sql,args,Long.class);
        long count=total==null?0L:total;
        args.put("limit",size);args.put("offset",(long)page*size);
        var rows=db.query("SELECT r.id,r.order_id,r.amount,r.status,r.reason_type,r.method,r.requested_at,r.processed_at,o.order_code,c.full_name"+sql
            +" ORDER BY r.id DESC LIMIT :limit OFFSET :offset",args,(r,n)->new Row(r.getLong("id"),r.getLong("order_id"),r.getLong("order_code"),
            r.getString("full_name"),r.getBigDecimal("amount"),r.getString("status"),r.getString("reason_type"),r.getString("method"),
            instant(r,"requested_at"),instant(r,"processed_at")));
        return new Page(rows,count,(int)((count+size-1)/size),page,size);
    }
    public Summary summary(){
        long[] counts=new long[3];
        Map<String,BigDecimal> amounts=new HashMap<>();
        db.query("SELECT status, count(*) AS refund_count, coalesce(sum(amount),0) AS refund_sum FROM order_refunds GROUP BY status",Map.of(),r->{
            int slot=switch(r.getString("status")){case "PENDING"->0;case "COMPLETED"->1;case "REJECTED"->2;default->-1;};
            if(slot>=0){counts[slot]=r.getLong("refund_count");amounts.put(r.getString("status"),r.getBigDecimal("refund_sum"));}
        });
        return new Summary(counts[0],amount(amounts,"PENDING"),counts[1],amount(amounts,"COMPLETED"),counts[2]);
    }
    private static BigDecimal amount(Map<String,BigDecimal> amounts,String status){BigDecimal value=amounts.get(status);return value==null?BigDecimal.ZERO:value;}
    private Instant instant(ResultSet r,String name)throws SQLException{var t=r.getTimestamp(name);return t==null?null:t.toInstant();}
}
