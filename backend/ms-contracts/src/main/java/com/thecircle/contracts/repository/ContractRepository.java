package com.thecircle.contracts.repository;

import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {

    List<Contract> findByOwnerIdOrReceiverIdOrderByCreatedAtDesc(String ownerId, String receiverId);

    // Delivery confirmation is done with single-column atomic updates so two
    // parties confirming concurrently cannot lose each other's timestamp (a
    // read-modify-write of the whole row would). Each only touches its own
    // column when still null, so they are also idempotent per party.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Contract c set c.ownerDeliveredAt = :now where c.id = :id and c.ownerDeliveredAt is null")
    int markOwnerDelivered(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Contract c set c.receiverReceivedAt = :now where c.id = :id and c.receiverReceivedAt is null")
    int markReceiverReceived(@Param("id") String id, @Param("now") LocalDateTime now);

    // Flip to DELIVERED only once both confirmations are present; guarded on the
    // current status so it runs exactly once regardless of which party is last.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Contract c set c.status = :delivered where c.id = :id and c.status = :active "
            + "and c.ownerDeliveredAt is not null and c.receiverReceivedAt is not null")
    int markDeliveredIfBothConfirmed(@Param("id") String id,
                                     @Param("active") ContractStatus active,
                                     @Param("delivered") ContractStatus delivered);

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
