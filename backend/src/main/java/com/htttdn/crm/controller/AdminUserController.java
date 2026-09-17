package com.htttdn.crm.controller;
import com.htttdn.crm.entity.User; import com.htttdn.crm.repository.UserRepository; import com.htttdn.crm.dto.admin.AdminDtos.*; import jakarta.validation.Valid; import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/users") public class AdminUserController extends AdminPageSupport { private final UserRepository users; public AdminUserController(UserRepository users){this.users=users;}
 @GetMapping public Page<User> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return users.findByRoleAndFullNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase("CUSTOMER",q,"CUSTOMER",q,page(page,size));}
 @GetMapping("/{id}") public User get(@PathVariable Long id){return users.findById(id).orElseThrow();}
 @PutMapping("/{id}") public User update(@PathVariable Long id,@Valid @RequestBody UserRequest request){User user=get(id);user.setFullName(request.fullName());user.setPhone(request.phone());user.setPreferences(request.preferences());return users.save(user);}
 @PatchMapping("/{id}/lock") public User lock(@PathVariable Long id,@Valid @RequestBody LockRequest request){User user=get(id);user.setLocked(request.locked());return users.save(user);} }
