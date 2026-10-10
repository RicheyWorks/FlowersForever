package com.flowerfarm.repository;

import com.flowerfarm.model.IntegrationHandoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationHandoffJpaRepository extends JpaRepository<IntegrationHandoff, Long> {

    List<IntegrationHandoff> findAllByOrderByCreatedAtDescIdDesc();

    Optional<IntegrationHandoff>
            findFirstByHandoffTypeAndProviderIgnoreCaseAndExternalReferenceIgnoreCase(
                    String handoffType, String provider, String externalReference);
}
