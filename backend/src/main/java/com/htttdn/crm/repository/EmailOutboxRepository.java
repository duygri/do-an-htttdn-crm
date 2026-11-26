package com.htttdn.crm.repository;

import com.htttdn.crm.entity.EmailOutbox;
import com.htttdn.crm.entity.EmailOutbox.Status;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox,Long> {
    Page<EmailOutbox> findByStatus(Status status,Pageable page);
    List<EmailOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderById(Status status,Instant now,Pageable page);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update EmailOutbox e set e.status=:processing, e.attempts=e.attempts+1, e.claimToken=:token, e.leaseUntil=:lease, e.updatedAt=:now where e.id=:id and e.status=:pending and e.nextAttemptAt<=:now and e.attempts<5")
    int claim(@Param("id") Long id,@Param("token") String token,@Param("now") Instant now,@Param("lease") Instant lease,@Param("pending") Status pending,@Param("processing") Status processing);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update EmailOutbox e set e.status=:unknown, e.reason='EXPIRED_LEASE', e.claimToken=null, e.leaseUntil=null, e.updatedAt=:now where e.status=:processing and e.leaseUntil<=:now")
    int recover(@Param("now") Instant now,@Param("processing") Status processing,@Param("unknown") Status unknown);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update EmailOutbox e set e.status=:status, e.reason=:reason, e.nextAttemptAt=:next, e.updatedAt=:now, e.claimToken=null, e.leaseUntil=null where e.id=:id and e.status=:processing and e.claimToken=:token and e.leaseUntil>:now")
    int finish(@Param("id") Long id,@Param("token") String token,@Param("status") Status status,@Param("reason") String reason,@Param("next") Instant next,@Param("now") Instant now,@Param("processing") Status processing);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailOutbox e where e.id=:id")
    java.util.Optional<EmailOutbox> lockById(@Param("id") Long id);
}
