package com.thecircle.contracts.repository;

import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {

    List<Contract> findByOwnerIdOrReceiverIdOrderByCreatedAtDesc(String ownerId, String receiverId);

    // Idempotency for contract creation: re-opening the signing screen for the same
    // item/parties/type must reuse the still-open contract instead of spawning a
    // duplicate. Only un-signed PENDING_SIGNATURES rows qualify (a half-signed one is
    // a live deal that must not be silently replaced); the latest is returned.
    Optional<Contract> findFirstByItemIdAndOwnerIdAndReceiverIdAndTypeAndStatusAndOwnerSignedAtIsNullAndReceiverSignedAtIsNullOrderByCreatedAtDesc(
            String itemId, String ownerId, String receiverId, ContractType type, ContractStatus status);

    // Stale, never-signed contracts: created before the cutoff and with neither party
    // having signed. These never reserved the item (reservation happens on first
    // signature) so deleting them leaves no orphaned RESERVED article behind.
    List<Contract> findByStatusAndCreatedAtBeforeAndOwnerSignedAtIsNullAndReceiverSignedAtIsNull(
            ContractStatus status, LocalDateTime cutoff);
}
