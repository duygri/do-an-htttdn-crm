package com.htttdn.crm.repository;

import com.htttdn.crm.entity.InternalNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InternalNoticeRepository extends JpaRepository<InternalNotice, Long> {
    boolean existsByRecipientIdAndDedupeKey(Long recipientId, String dedupeKey);
    long countByRecipientId(Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
    Page<InternalNotice> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);
    Optional<InternalNotice> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("update InternalNotice n set n.readAt=:now where n.recipient.id=:recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
