package com.wallettransfer.users.repository;

import com.wallettransfer.users.model.Role;
import com.wallettransfer.users.model.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {
    Optional<Role> findByName(RoleName name);
}
