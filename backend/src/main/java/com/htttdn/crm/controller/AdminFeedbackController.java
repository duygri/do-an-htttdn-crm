package com.htttdn.crm.controller;
import com.htttdn.crm.entity.Feedback; import com.htttdn.crm.dto.admin.AdminDtos.FeedbackRequest; import com.htttdn.crm.service.AdminFeedbackService; import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/feedback") public class AdminFeedbackController { private final AdminFeedbackService service; public AdminFeedbackController(AdminFeedbackService service){this.service=service;}
 @GetMapping public Page<Feedback> list(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(status,page,size);}
 @PatchMapping("/{id}") public Feedback update(@PathVariable Long id,@RequestBody FeedbackRequest r){return service.update(id,r);} }
