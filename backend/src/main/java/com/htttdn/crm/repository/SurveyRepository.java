package com.htttdn.crm.repository;

import com.htttdn.crm.entity.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.domain.*; import java.util.*;

public interface SurveyRepository extends JpaRepository<Survey,Long> {
    Page<Survey> findByStatus(String status,Pageable p); long countByStatus(String status);
    Optional<Survey> findByIdAndStatus(Long id,String status); List<Survey> findByStatusOrderByCreatedAtDesc(String status);
}
