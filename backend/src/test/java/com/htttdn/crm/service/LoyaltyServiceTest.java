package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LoyaltyServiceTest {
 @Test void archivedPointsCannotBeEarnedRedeemedOrAdjusted(){
  var transactions=mock(PointTransactionRepository.class);var vouchers=mock(StoreVoucherRepository.class);
  var service=new LoyaltyService(transactions,vouchers,mock(SettingsService.class),mock(NotificationService.class),mock(AuditTrail.class));
  var customer=new User();var order=new Order();order.setStatus("COMPLETED");order.setCustomer(customer);
  service.awardForCompletedOrder(order);service.awardForCompletedOrder(order);
  assertEquals("POINTS_RETIRED",assertThrows(ApiException.class,()->service.redeem(customer,"request")).code());
  assertEquals("POINTS_RETIRED",assertThrows(ApiException.class,()->service.adjust(customer,50,"note",new AuditTrail.Actor(1L,"Manager","MANAGER"))).code());
  verifyNoInteractions(transactions,vouchers);
 }
}
