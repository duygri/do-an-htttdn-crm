package com.htttdn.crm.service;

import com.htttdn.crm.dto.admin.AdminDtos.LockRequest;
import com.htttdn.crm.dto.admin.AdminDtos.UserRequest;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService extends AdminServiceSupport {
    private final UserRepository users;
    public AdminUserService(UserRepository users) { this.users = users; }
    public Page<User> list(String q, int page, int size) { return users.findByRoleAndFullNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase("CUSTOMER", q, "CUSTOMER", q, page(page, size)); }
    public User get(Long id) { return users.findById(id).orElseThrow(); }
    @Transactional public User update(Long id, UserRequest request) { User user = get(id); user.setFullName(request.fullName()); user.setPhone(request.phone()); user.setPreferences(request.preferences()); return users.save(user); }
    @Transactional public User lock(Long id, LockRequest request) { User user = get(id); user.setLocked(request.locked()); return users.save(user); }
}
