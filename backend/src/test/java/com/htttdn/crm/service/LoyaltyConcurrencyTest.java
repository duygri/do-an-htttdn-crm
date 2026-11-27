package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;import com.htttdn.crm.repository.*;import com.htttdn.crm.exception.ApiException;
import org.junit.jupiter.api.Test;import java.util.concurrent.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class LoyaltyConcurrencyTest {
 @Test void concurrentLegacyCallsCannotCreateVouchersOrPointTransactions()throws Exception{
  var tx=mock(PointTransactionRepository.class);var vouchers=mock(StoreVoucherRepository.class);
  var service=new LoyaltyService(tx,vouchers,mock(SettingsService.class),mock(NotificationService.class),mock(AuditTrail.class));
  try(var executor=Executors.newFixedThreadPool(4)){
   var calls=new ArrayList<Callable<String>>();for(int i=0;i<12;i++)calls.add(()->assertThrows(ApiException.class,()->service.redeem(new User(),"legacy")).code());
   for(var result:executor.invokeAll(calls))assertEquals("POINTS_RETIRED",result.get());
  }verifyNoInteractions(tx,vouchers);
 }
}
