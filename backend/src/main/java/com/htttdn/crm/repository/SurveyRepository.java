package com.htttdn.crm.repository; import com.htttdn.crm.entity.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.domain.Page; import java.util.*;
public interface SurveyRepository extends JpaRepository<Survey,Long> { Page<Survey> findByStatus(String status,org.springframework.data.domain.Pageable p); long countByStatus(String status); }
