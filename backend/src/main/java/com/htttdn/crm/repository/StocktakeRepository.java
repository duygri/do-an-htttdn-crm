package com.htttdn.crm.repository;

import com.htttdn.crm.entity.Stocktake;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
    Page<Stocktake> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
