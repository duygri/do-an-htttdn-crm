package com.htttdn.crm.service;

import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service @Transactional(readOnly=true)
public class ManagerOrderQuery {
    private final NamedParameterJdbcTemplate db;
    public ManagerOrderQuery(NamedParameterJdbcTemplate db){this.db=db;}
    private static final String GROUP="CASE WHEN o.status='CANCELLED' THEN 'CANCELLED' WHEN coalesce(o.return_status,'NONE')<>'NONE' THEN 'RETURN' WHEN o.status IN ('PENDING','PENDING_PAYMENT') THEN 'PENDING' WHEN o.status='PREPARING' THEN 'PREPARING' WHEN o.status='CONFIRMED' THEN 'CONFIRMED' WHEN o.status IN ('SHIPPED','DELIVERING') THEN 'SHIPPED' ELSE o.status END";
    private static final String JOIN=" FROM orders o JOIN customers c ON c.customer_id=o.customer_id ";
    private static final Set<String> STATUSES=Set.of("PENDING","PENDING_PAYMENT","CONFIRMED","PREPARING","SHIPPED","DELIVERING","DELIVERED","COMPLETED","CANCELLED","RETURN","RETURN_REQUESTED","RETURN_APPROVED","RETURN_REJECTED","RETURN_COMPLETED");
    public record Filter(String status,String q,String paymentStatus,Instant from,Instant to,String sort,String direction,int page,int size,Boolean paymentReview){
        public Filter(String status,String q,String paymentStatus,Instant from,Instant to,String sort,String direction,int page,int size){this(status,q,paymentStatus,from,to,sort,direction,page,size,null);}
    }
    public record Finance(Instant codCollectedAt,BigDecimal codCollectedAmount,boolean reviewRequired,String reviewReason,Instant reviewAt,BigDecimal refundedAmount,BigDecimal refundPendingAmount,BigDecimal refundableAmount,String refundStatus){}
    public record Refund(Long id,String reasonType,BigDecimal amount,String status,String method,String bankReference,String note,String processingNote,Instant requestedAt,Instant processedAt){};
    public record Customer(Long id,String fullName,String email){}
    public record Product(Long id,String name,String imageUrl){}
    public record Line(Long id,Product product,int quantity,String size,String color,BigDecimal unitPrice){}
    public record Event(Long id,String status,String note,String managerName,Instant createdAt){}
    public record ShipmentEventView(String status,String note,Instant occurredAt){}
    public record Shipment(String carrierCode,String carrierName,String trackingCode,String status,Instant shippedAt,Instant deliveredAt,List<ShipmentEventView> events){}
    public record ReturnItem(Long orderItemId,Long productId,String name,int quantity,String resolution,Instant restockedAt){}
    public record View(Long id,Long orderCode,String status,String paymentStatus,String paymentMethod,String returnStatus,BigDecimal totalAmount,BigDecimal discountAmount,String voucherCode,Instant createdAt,Instant updatedAt,Customer customer,String deliveryAddress,String trackingCode,String cancelReason,String returnReason,Instant returnRequestedAt,List<Line> items,List<Event> returnHistory,Finance finance,Shipment shipment,List<ReturnItem> returnItems,List<Refund> refunds){}
    public record Page<T>(List<T> content,long totalElements,long totalPages,int number,int size){}
    private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","Bộ lọc, ngày hoặc phân trang không hợp lệ.");}
    private String where(Filter f,Map<String,Object> args,boolean includeStatus){
        if(f.page()<0||f.size()<1||f.size()>100||f.q()!=null&&f.q().length()>120||f.from()!=null&&f.to()!=null&&!f.from().isBefore(f.to()))throw invalid();
        var parts=new ArrayList<String>();parts.add("1=1");
        if(f.paymentReview()!=null){args.put("review",f.paymentReview());parts.add("o.payment_review_required=:review");}
        if(f.q()!=null&&!f.q().isBlank()){args.put("q","%"+f.q().trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");parts.add("(lower(c.full_name) LIKE :q ESCAPE '!' OR lower(coalesce(c.email,'')) LIKE :q ESCAPE '!' OR CAST(o.order_code AS varchar) LIKE :q ESCAPE '!')");}
        if(f.paymentStatus()!=null&&!f.paymentStatus().isBlank()){if(!Set.of("COD","PENDING","PAID","EXPIRED","FAILED","CANCELLED").contains(f.paymentStatus()))throw invalid();args.put("payment",f.paymentStatus());parts.add("o.payment_status=:payment");}
        if(f.from()!=null){args.put("from",Timestamp.from(f.from()));parts.add("o.created_at>=:from");}
        if(f.to()!=null){args.put("to",Timestamp.from(f.to()));parts.add("o.created_at<:to");}
        if(includeStatus&&f.status()!=null&&!f.status().isBlank()){
            if(!STATUSES.contains(f.status()))throw invalid();
            if(f.status().startsWith("RETURN_")){args.put("return",f.status().substring(7));parts.add("o.status<>'CANCELLED' AND o.return_status=:return");}
            else {args.put("status",switch(f.status()){case "PENDING_PAYMENT"->"PENDING";case "DELIVERING"->"SHIPPED";default->f.status();});parts.add("("+GROUP+")=:status");}
        }
        return " WHERE "+String.join(" AND ",parts);
    }
    public Page<View> list(Filter f){
        var args=new HashMap<String,Object>();String where=where(f,args,true);
        String sort=f.sort()==null||f.sort().isBlank()?"createdAt":f.sort();String column=Map.of("createdAt","o.created_at","totalAmount","o.total_amount","orderCode","o.order_code").get(sort);
        String dir=f.direction()==null?"DESC":f.direction().toUpperCase(Locale.ROOT);if(column==null||!Set.of("ASC","DESC").contains(dir))throw invalid();
        long count=db.queryForObject("SELECT count(*)"+JOIN+where,args,Long.class);
        args.put("limit",f.size());args.put("offset",(long)f.page()*f.size());
        var rows=db.query("SELECT o.*,c.full_name,c.email"+JOIN+where+" ORDER BY "+column+" "+dir+",o.order_id DESC LIMIT :limit OFFSET :offset",args,(r,n)->view(r,List.of(),List.of(),null,List.of(),List.of()));
        return new Page<>(rows,count,(count+f.size()-1)/f.size(),f.page(),f.size());
    }
    public Map<String,Long> summary(Filter f){
        var args=new HashMap<String,Object>();String where=where(f,args,false);var result=new LinkedHashMap<String,Long>();
        for(String key:List.of("PENDING","CONFIRMED","PREPARING","SHIPPED","DELIVERED","COMPLETED","CANCELLED","RETURN"))result.put(key,0L);
        db.query("SELECT "+GROUP+" AS bucket,count(*) AS amount"+JOIN+where+" GROUP BY "+GROUP,args,r->{result.put(r.getString("bucket"),r.getLong("amount"));});
        result.put("ALL",result.values().stream().mapToLong(Long::longValue).sum());return result;
    }
    public View detail(Long id){
        Shipment shipment=shipment(id);
        var args=Map.of("id",id);
        var lines=db.query("SELECT i.*,p.name,p.image_url FROM order_items i JOIN products p ON p.product_id=i.product_id WHERE i.order_id=:id ORDER BY i.order_item_id",args,(r,n)->new Line(r.getLong("order_item_id"),new Product(r.getLong("product_id"),r.getString("name"),r.getString("image_url")),r.getInt("quantity"),r.getString("size"),r.getString("color"),r.getBigDecimal("unit_price")));
        var history=db.query("SELECT * FROM order_return_events WHERE order_id=:id ORDER BY id",args,(r,n)->new Event(r.getLong("id"),r.getString("status"),r.getString("note"),r.getString("manager_name"),instant(r,"created_at")));
        var returnItems=db.query("SELECT ri.order_item_id,ri.quantity,ri.resolution,ri.restocked_at,i.product_id,p.name FROM order_return_items ri JOIN order_items i ON i.order_item_id=ri.order_item_id JOIN products p ON p.product_id=i.product_id WHERE ri.order_id=:id ORDER BY ri.id",args,(r,n)->new ReturnItem(r.getLong("order_item_id"),r.getLong("product_id"),r.getString("name"),r.getInt("quantity"),r.getString("resolution"),instant(r,"restocked_at")));
        var refunds=db.query("SELECT * FROM order_refunds WHERE order_id=:id ORDER BY id",args,(r,n)->new Refund(r.getLong("id"),r.getString("reason_type"),r.getBigDecimal("amount"),r.getString("status"),r.getString("method"),r.getString("bank_reference"),r.getString("note"),r.getString("processing_note"),instant(r,"requested_at"),instant(r,"processed_at")));
        return db.query("SELECT o.*,c.full_name,c.email"+JOIN+" WHERE o.order_id=:id",args,(r,n)->view(r,lines,history,shipment,returnItems,refunds)).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng."));
    }
    private Shipment shipment(Long orderId){
        var row=db.query("SELECT s.*,c.name AS carrier_name FROM shipments s LEFT JOIN shipping_carriers c ON c.code=s.carrier_code WHERE s.order_id=:id",
            Map.of("id",orderId),(r,n)->new Object[]{r.getLong("id"),new Shipment(r.getString("carrier_code"),r.getString("carrier_name"),r.getString("tracking_code"),r.getString("status"),instant(r,"shipped_at"),instant(r,"delivered_at"),List.<ShipmentEventView>of())}).stream().findFirst().orElse(null);
        if(row==null)return null;
        Long sid=(Long)row[0]; Shipment base=(Shipment)row[1];
        var timeline=db.query("SELECT * FROM shipment_events WHERE shipment_id=:sid ORDER BY id",Map.of("sid",sid),(r,n)->new ShipmentEventView(r.getString("status"),r.getString("note"),instant(r,"occurred_at")));
        return new Shipment(base.carrierCode(),base.carrierName(),base.trackingCode(),base.status(),base.shippedAt(),base.deliveredAt(),timeline);
    }
    private Instant instant(ResultSet r,String name)throws SQLException{var t=r.getTimestamp(name);return t==null?null:t.toInstant();}
    private View view(ResultSet r,List<Line> lines,List<Event> history,Shipment shipment,List<ReturnItem> returnItems,List<Refund> refunds)throws SQLException{return new View(r.getLong("order_id"),r.getLong("order_code"),r.getString("status"),r.getString("payment_status"),r.getString("payment_method"),r.getString("return_status"),r.getBigDecimal("total_amount"),r.getBigDecimal("discount_amount"),r.getString("voucher_code"),instant(r,"created_at"),instant(r,"updated_at"),new Customer(r.getLong("customer_id"),r.getString("full_name"),r.getString("email")),r.getString("delivery_address"),r.getString("tracking_code"),r.getString("cancel_reason"),r.getString("return_reason"),instant(r,"return_requested_at"),lines,history,finance(r),shipment,returnItems,refunds);}
    /** Refundable = paid (PAYOS PAID total, COD cash collected) minus completed and pending refunds; positive only. */
    private Finance finance(ResultSet r)throws SQLException{
        BigDecimal cod=r.getBigDecimal("cod_collected_amount");BigDecimal total=r.getBigDecimal("total_amount");BigDecimal refunded=r.getBigDecimal("refunded_amount");BigDecimal pending=r.getBigDecimal("refund_pending_amount");
        BigDecimal paid="PAYOS".equals(r.getString("payment_method"))&&"PAID".equals(r.getString("payment_status"))?nz(total):nz(cod);
        BigDecimal refundable=paid.subtract(nz(refunded)).subtract(nz(pending)).max(BigDecimal.ZERO);
        String status=r.getString("refund_status");
        return new Finance(instant(r,"cod_collected_at"),nz(cod),r.getBoolean("payment_review_required"),r.getString("payment_review_reason"),instant(r,"payment_review_at"),nz(refunded),nz(pending),refundable,status==null?"NONE":status);
    }
    private static BigDecimal nz(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
}
