package com.htttdn.crm.controller;
import com.htttdn.crm.entity.User; import com.htttdn.crm.repository.*; import org.springframework.web.bind.annotation.*; import java.math.BigDecimal; import java.util.*;
@RestController @RequestMapping("/api/admin/reports") public class AdminReportController { private final OrderRepository orders;private final UserRepository users;private final SurveyRepository surveys; public AdminReportController(OrderRepository o,UserRepository u,SurveyRepository s){orders=o;users=u;surveys=s;}
 @GetMapping("/revenue") public Map<String,Object> revenue(){return Map.of("totalRevenue",Optional.ofNullable(orders.revenue()).orElse(BigDecimal.ZERO),"orders",orders.count());}
 @GetMapping("/users") public Map<String,Object> users(){return Map.of("customers",users.countByRole("CUSTOMER"),"admins",users.countByRole("ADMIN"),"lockedCustomers",users.findAll().stream().filter(User::isLocked).count());}
 @GetMapping("/surveys/stats") public Map<String,Object> surveyStats(){return Map.of("total",surveys.count(),"draft",surveys.countByStatus("DRAFT"),"published",surveys.countByStatus("PUBLISHED"));} }
