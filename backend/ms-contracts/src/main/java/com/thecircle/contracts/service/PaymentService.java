package com.thecircle.contracts.service;

import com.thecircle.contracts.client.CatalogClient;
import com.thecircle.contracts.client.UsersClient;
import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.dto.PaymentDto;
import com.thecircle.contracts.dto.PaymentRequestDto;
import com.thecircle.contracts.dto.PaymentStatus;
import com.thecircle.contracts.model.Contract;
import com.thecircle.contracts.model.Payment;
import com.thecircle.contracts.repository.ContractRepository;
import com.thecircle.contracts.repository.PaymentRepository;
import com.thecircle.contracts.util.CardValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Drives the simulated escrow lifecycle for symbolic payments:
 * <ol>
 *   <li>Receiver pays → {@link PaymentStatus#ESCROWED}, contract → AWAITING_COUNTERPARTY.</li>
 *   <li>Owner signs within the escrow window → {@link PaymentStatus#RELEASED}, contract → ACTIVE.</li>
 *   <li>Owner does not sign before {@code escrowExpiresAt} → {@link PaymentStatus#REFUNDED},
 *       contract → CANCELLED (via the {@link #refundExpiredEscrows()} sweep).</li>
 * </ol>
 *
 * No real money or PSP is involved — the row exists purely for the audit trail
 * and the UX. Only the masked card last 4 is persisted.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CURRENCY = "EUR";

    private final PaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final UsersClient usersClient;
    private final CatalogClient catalogClient;

    @Value("${payment.escrow.expiry-days:7}")
    private long escrowExpiryDays;

    public PaymentService(PaymentRepository paymentRepository,
                          ContractRepository contractRepository,
                          UsersClient usersClient,
                          CatalogClient catalogClient) {
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.usersClient = usersClient;
        this.catalogClient = catalogClient;
    }

    /**
     * Receiver pays. Validates the card, charges (simulated) the receiver, and
     * parks the funds in escrow until the owner signs. The contract is flipped
     * to {@link ContractStatus#AWAITING_COUNTERPARTY} so the UI can reflect the
     * waiting state.
     *
     * <p>Authorization: only the contract's {@code receiverId} (the buyer) may
     * trigger this — enforced by the caller through the JWT userId claim.
     */
    @Transactional
    public PaymentDto pay(String contractId, String callerUserId, PaymentRequestDto req) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));

        if (callerUserId == null || !callerUserId.equals(contract.getReceiverId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the buyer (contract receiver) can pay for this contract.");
        }
        if (contract.getStatus() == ContractStatus.AWAITING_COUNTERPARTY
                || contract.getStatus() == ContractStatus.ACTIVE
                || contract.getStatus() == ContractStatus.COMPLETED
                || contract.getStatus() == ContractStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This contract is not in a state that accepts a new payment.");
        }
        if (contract.getType() != ContractType.SALE && contract.getType() != ContractType.RENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payments only apply to SALE and RENT contracts.");
        }

        BigDecimal amount = resolveAmount(contract);
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This contract has no payable amount.");
        }

        if (req == null || req.cardNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card number is required.");
        }
        String number = CardValidator.normalize(req.cardNumber());
        if (!CardValidator.luhn(number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card number is invalid.");
        }
        if (!CardValidator.expiryNotPast(req.expiry())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card expiry is invalid or in the past.");
        }
        if (!CardValidator.cvc(req.cvc())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CVC is invalid.");
        }

        UsersClient.PayoutAccount payout = usersClient.getPayoutAccount(contract.getOwnerId());
        if (payout == null || !payout.hasIban()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The seller does not have a payout account configured. Please contact them before paying.");
        }

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setContractId(contract.getId());
        payment.setPayerUserId(contract.getReceiverId());
        payment.setPayeeUserId(contract.getOwnerId());
        payment.setAmount(amount);
        payment.setCurrency(CURRENCY);
        payment.setCardLast4(CardValidator.last4(number));
        payment.setCardBrand(CardValidator.brand(number));
        payment.setPayoutIbanLast4(payout.ibanLast4());
        payment.setSimulatedAt(LocalDateTime.now());

        if (CardValidator.isDemoFailureCard(number)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Demo failure: card ending in 0000.");
            return toDto(paymentRepository.save(payment));
        }

        payment.setStatus(PaymentStatus.ESCROWED);
        payment.setEscrowExpiresAt(payment.getSimulatedAt().plusDays(escrowExpiryDays));
        Payment saved = paymentRepository.save(payment);

        contract.setStatus(ContractStatus.AWAITING_COUNTERPARTY);
        contractRepository.save(contract);

        log.info("Payment {} escrowed for contract {}; expires {}", saved.getId(),
                contract.getId(), saved.getEscrowExpiresAt());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> listForContract(String contractId, String callerUserId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        if (callerUserId == null
                || (!callerUserId.equals(contract.getOwnerId()) && !callerUserId.equals(contract.getReceiverId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the contract parties can view its payments.");
        }
        return paymentRepository.findByContractIdOrderBySimulatedAtDesc(contractId)
                .stream().map(this::toDto).toList();
    }

    /**
     * Called from the signature workflow when both parties have signed. If a
     * payment is sitting in escrow for this contract, release it and let the
     * caller mark the contract ACTIVE. Idempotent: a second call after release
     * is a no-op so retried sign callbacks do not double-process.
     */
    @Transactional
    public PaymentDto releaseEscrowOnDualSign(String contractId) {
        return paymentRepository.findFirstByContractIdAndStatus(contractId, PaymentStatus.ESCROWED)
                .map(p -> {
                    p.setStatus(PaymentStatus.RELEASED);
                    p.setReleasedAt(LocalDateTime.now());
                    log.info("Payment {} released to seller {} for contract {}",
                            p.getId(), p.getPayeeUserId(), contractId);
                    return toDto(paymentRepository.save(p));
                })
                .orElse(null);
    }

    /**
     * Sweeps escrowed payments whose counterparty did not sign in time and
     * refunds them. Linked contract is cancelled so the seller cannot still
     * accept the funds afterwards.
     *
     * <p>Cadence is property-driven via {@code payment.escrow.scan-interval-ms}.
     * The default of 10 minutes is fine for a TFM demo; production would tune
     * it lower depending on how strict the 7-day SLA needs to be.
     */
    @Scheduled(fixedDelayString = "${payment.escrow.scan-interval-ms:600000}")
    @Transactional
    public void refundExpiredEscrows() {
        List<Payment> expired = paymentRepository.findByStatusAndEscrowExpiresAtBefore(
                PaymentStatus.ESCROWED, LocalDateTime.now());
        if (expired.isEmpty()) return;
        for (Payment p : expired) {
            p.setStatus(PaymentStatus.REFUNDED);
            p.setRefundedAt(LocalDateTime.now());
            p.setFailureReason("Counterparty did not sign within the escrow window.");
            paymentRepository.save(p);

            contractRepository.findById(p.getContractId()).ifPresent(c -> {
                c.setStatus(ContractStatus.CANCELLED);
                contractRepository.save(c);
                // Free the article up again so the seller can re-list it.
                catalogClient.setStatus(c.getItemId(), "AVAILABLE");
            });
            log.info("Payment {} refunded to buyer {} after escrow expiry on contract {}",
                    p.getId(), p.getPayerUserId(), p.getContractId());
        }
    }

    /**
     * Looks up the amount due. For SALE the buyer pays the agreed price; for
     * RENT the buyer locks the security deposit. The contract row already has
     * the canonical value (sourced from the article at creation time).
     */
    private BigDecimal resolveAmount(Contract contract) {
        if (contract.getType() == ContractType.SALE) {
            return contract.getPrice();
        }
        return contract.getGuaranteeAmount();
    }

    private PaymentDto toDto(Payment p) {
        return new PaymentDto(
                p.getId(), p.getContractId(), p.getPayerUserId(), p.getPayeeUserId(),
                p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getCardLast4(), p.getCardBrand(), p.getPayoutIbanLast4(),
                p.getSimulatedAt(), p.getEscrowExpiresAt(), p.getReleasedAt(),
                p.getRefundedAt(), p.getFailureReason());
    }
}
