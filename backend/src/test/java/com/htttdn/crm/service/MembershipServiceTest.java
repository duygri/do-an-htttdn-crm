package com.htttdn.crm.service;
import java.math.BigDecimal;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MembershipServiceTest {
 @Test void thresholdsAndUncappedWholeDongDiscount(){
  String[] values={"0","1999999.99","2000000","4999999.99","5000000","9999999.99","10000000","999999999"};
  String[] tiers={"BRONZE","BRONZE","SILVER","SILVER","GOLD","GOLD","DIAMOND","DIAMOND"};
  for(int i=0;i<values.length;i++)assertEquals(tiers[i],MembershipService.calculate(new BigDecimal(values[i])).tier());
  assertEquals(new BigDecimal("2999"),MembershipService.discount(new BigDecimal("99999"),MembershipService.calculate(new BigDecimal("2000000"))));
  assertEquals(new BigDecimal("1000000"),MembershipService.discount(new BigDecimal("10000000"),MembershipService.calculate(new BigDecimal("10000000"))));
  assertNull(MembershipService.calculate(new BigDecimal("10000000")).nextThreshold());
 }
}
