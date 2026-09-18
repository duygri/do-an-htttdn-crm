package com.htttdn.crm.controller;
import com.htttdn.crm.entity.Order; import com.htttdn.crm.dto.admin.AdminDtos.StatusRequest; import com.htttdn.crm.service.AdminOrderService; import jakarta.validation.Valid; import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/orders") public class AdminOrderController { private final AdminOrderService service; public AdminOrderController(AdminOrderService service){this.service=service;}
 @GetMapping public Page<Order> list(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(status,page,size);}
 @GetMapping("/{id}") public Order get(@PathVariable Long id){return service.get(id);}
 @PatchMapping("/{id}/status") public Order updateStatus(@PathVariable Long id,@Valid @RequestBody StatusRequest r){return service.updateStatus(id,r);} }
