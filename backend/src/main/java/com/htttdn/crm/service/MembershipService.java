package com.htttdn.crm.service;
import java.math.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MembershipService {
 private final JdbcTemplate db;
 public MembershipService(JdbcTemplate db){this.db=db;}
 public record Membership(String tier,String label,int discountPercent,BigDecimal completedValue,BigDecimal nextThreshold,BigDecimal remainingValue){}
 public static Membership calculate(BigDecimal value){
  value=value==null?BigDecimal.ZERO:value.max(BigDecimal.ZERO);
  String tier="BRONZE", label="Đồng";int rate=0;BigDecimal next=new BigDecimal("2000000");
  if(value.compareTo(new BigDecimal("10000000"))>=0){tier="DIAMOND";label="Kim cương";rate=10;next=null;}
  else if(value.compareTo(new BigDecimal("5000000"))>=0){tier="GOLD";label="Vàng";rate=5;next=new BigDecimal("10000000");}
  else if(value.compareTo(new BigDecimal("2000000"))>=0){tier="SILVER";label="Bạc";rate=3;next=new BigDecimal("5000000");}
  return new Membership(tier,label,rate,value,next,next==null?BigDecimal.ZERO:next.subtract(value));
 }
 @Transactional(readOnly=true) public Membership forCustomer(Long id){
  return calculate(db.queryForObject("select coalesce(sum(total_amount),0) from orders where customer_id=? and status='COMPLETED'",BigDecimal.class,id));
 }
 public static BigDecimal discount(BigDecimal subtotal,Membership membership){
  return subtotal.multiply(BigDecimal.valueOf(membership.discountPercent())).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN);
 }
}
