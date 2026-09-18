package com.htttdn.crm.controller;

import com.htttdn.crm.service.AdminReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/surveys")
public class AdminSurveyStatsController {
    private final AdminReportService reportService;

    public AdminSurveyStatsController(AdminReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return reportService.surveyStats();
    }
}
