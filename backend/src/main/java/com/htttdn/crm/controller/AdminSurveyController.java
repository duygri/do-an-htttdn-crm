package com.htttdn.crm.controller;
import com.htttdn.crm.entity.Survey; import com.htttdn.crm.dto.admin.AdminDtos.SurveyRequest; import com.htttdn.crm.service.AdminSurveyService; import jakarta.validation.Valid; import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/surveys") public class AdminSurveyController { private final AdminSurveyService service; public AdminSurveyController(AdminSurveyService service){this.service=service;}
 @GetMapping public Page<Survey> list(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(status,page,size);}
 @PostMapping public Survey create(@Valid @RequestBody SurveyRequest r){return service.create(r);}
 @PatchMapping("/{id}/publish") public Survey publish(@PathVariable Long id,@RequestParam(defaultValue="true")boolean value){return service.publish(id,value);}
 @DeleteMapping("/{id}") public void delete(@PathVariable Long id){service.delete(id);} }
