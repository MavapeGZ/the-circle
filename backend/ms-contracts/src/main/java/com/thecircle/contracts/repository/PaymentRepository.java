package com.thecircle.contracts.repository;

import com.thecircle.contracts.dto.PaymentStatus;
import com.thecircle.contracts.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByContractIdOrderBySimulatedAtDesc(String contractId);

    Optional<Payment> findFirstByContractIdAndStatus(String contractId, PaymentStatus status);

    /** Drives the refund cron: payments still in escrow whose 7-day window elapsed. */
    List<Payment> findByStatusAndEscrowExpiresAtBefore(PaymentStatus status, LocalDateTime cutoff);

    void deleteByContractIdIn(Collection<String> contractIds);
}
