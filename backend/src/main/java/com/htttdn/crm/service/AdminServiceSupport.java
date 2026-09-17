package com.htttdn.crm.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

abstract class AdminServiceSupport {
    protected Pageable page(int number, int size) {
        return PageRequest.of(Math.max(0, number), Math.min(100, Math.max(1, size)),
                Sort.by("id").descending());
    }
}
