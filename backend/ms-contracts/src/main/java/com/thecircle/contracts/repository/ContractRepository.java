package com.thecircle.contracts.repository;

import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {

    List<Contract> findByOwnerIdOrReceiverIdOrderByCreatedAtDesc(String ownerId, String receiverId);

    // Stale, never-signed contracts: created before the cutoff and with neither party
    // having signed. These never reserved the item (reservation happens on first
    // signature) so deleting them leaves no orphaned RESERVED article behind.
    List<Contract> findByStatusAndCreatedAtBeforeAndOwnerSignedAtIsNullAndReceiverSignedAtIsNull(
            ContractStatus status, LocalDateTime cutoff);
}
