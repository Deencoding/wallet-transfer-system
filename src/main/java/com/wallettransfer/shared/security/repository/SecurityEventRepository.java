package com.wallettransfer.shared.security.repository;

import com.wallettransfer.shared.security.model.SecurityEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {}
