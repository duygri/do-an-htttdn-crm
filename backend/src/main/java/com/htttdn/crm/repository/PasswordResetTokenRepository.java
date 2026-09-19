package com.htttdn.crm.repository;

import com.htttdn.crm.entity.PasswordResetToken; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long> { Optional<PasswordResetToken> findByTokenHash(String tokenHash); void deleteByCustomerId(Long customerId); }
