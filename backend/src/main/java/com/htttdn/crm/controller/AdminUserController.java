package com.htttdn.crm.controller;
import com.htttdn.crm.dto.admin.AdminDtos.*; import com.htttdn.crm.entity.User; import com.htttdn.crm.service.AdminUserService; import jakarta.validation.Valid; import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/users") public class AdminUserController { private final AdminUserService service; public AdminUserController(AdminUserService service){this.service=service;}
 @GetMapping public Page<User> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(q,page,size);}
 @GetMapping("/{id}") public User get(@PathVariable Long id){return service.get(id);}
 @PutMapping("/{id}") public User update(@PathVariable Long id,@Valid @RequestBody UserRequest request){return service.update(id,request);}
 @PatchMapping("/{id}/lock") public User lock(@PathVariable Long id,@Valid @RequestBody LockRequest request){return service.lock(id,request);} }
