package com.htttdn.crm.repository;

import com.htttdn.crm.entity.User; import org.springframework.data.jpa.repository.*; import org.springframework.data.domain.*; import java.util.*;

public interface UserRepository extends JpaRepository<User,Long> {
    Page<User> findByRoleAndFullNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(String r1,String n,String r2,String e,Pageable p);
    long countByRole(String role); Optional<User> findByEmailIgnoreCase(String email); boolean existsByEmailIgnoreCase(String email);
}
