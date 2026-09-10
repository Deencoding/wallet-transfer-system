package com.wallettransfer.providers.repository;

import com.wallettransfer.providers.model.ProviderInteraction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderInteractionRepository extends JpaRepository<ProviderInteraction, UUID> {}
