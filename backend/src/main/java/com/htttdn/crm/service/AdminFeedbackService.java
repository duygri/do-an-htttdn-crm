package com.htttdn.crm.service;

import com.htttdn.crm.dto.admin.AdminDtos.FeedbackRequest;
import com.htttdn.crm.entity.Feedback;
import com.htttdn.crm.repository.FeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class AdminFeedbackService extends AdminServiceSupport {
    private final FeedbackRepository feedback;
    public AdminFeedbackService(FeedbackRepository feedback) { this.feedback = feedback; }
    public Page<Feedback> list(String status, int page, int size) { return status == null ? feedback.findAll(page(page, size)) : feedback.findByStatus(status, page(page, size)); }
    @Transactional public Feedback update(Long id, FeedbackRequest request) { Feedback item = feedback.findById(id).orElseThrow(); if (request.status() != null) item.setStatus(request.status()); if (request.adminResponse() != null) item.setAdminResponse(request.adminResponse()); item.setProcessedAt(Instant.now()); return feedback.save(item); }
}
