package com.thecircle.contracts.service;

import com.thecircle.contracts.client.CatalogClient;
import com.thecircle.contracts.client.UsersClient;
import com.thecircle.contracts.dto.ContractCreateRequest;
import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.GuaranteeStatus;
import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.dto.SignerRole;
import com.thecircle.contracts.model.Contract;
import com.thecircle.contracts.repository.PaymentRepository;
import com.thecircle.contracts.repository.ContractRepository;
import com.thecircle.contracts.repository.SignatureRecordRepository;
import com.thecircle.contracts.repository.SignatureSessionRepository;
import com.thecircle.contracts.repository.StoredContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists and reads business contracts. The OTP signature workflow calls
 * {@link #markSigned} once a signed PDF has been produced.
 */
@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private final ContractRepository repository;
    private final UsersClient usersClient;
    private final CatalogClient catalogClient;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final StoredContractRepository storedContractRepository;
    private final SignatureRecordRepository signatureRecordRepository;
    private final SignatureSessionRepository signatureSessionRepository;

    /** TTL (hours) after which an unsigned PENDING contract is purged. */
    @Value("${contracts.pending-ttl-hours:72}")
    private long pendingTtlHours;

    public ContractService(ContractRepository repository, UsersClient usersClient,
                           CatalogClient catalogClient, PaymentService paymentService,
                           PaymentRepository paymentRepository,
                           StoredContractRepository storedContractRepository,
                           SignatureRecordRepository signatureRecordRepository,
                           SignatureSessionRepository signatureSessionRepository) {
        this.repository = repository;
        this.usersClient = usersClient;
        this.catalogClient = catalogClient;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.storedContractRepository = storedContractRepository;
        this.signatureRecordRepository = signatureRecordRepository;
        this.signatureSessionRepository = signatureSessionRepository;
    }

    @Transactional
    public ContractDto create(ContractCreateRequest request) {
        // Idempotency guard: a contract is created merely by opening the signing
        // screen, so a buyer who clicks "acquire" again — e.g. after being sent to
        // add a payout IBAN — would otherwise spawn a second identical PENDING row
        // for the same deal. Reuse the existing open, un-signed contract instead.
        Contract existing = repository
                .findFirstByItemIdAndOwnerIdAndReceiverIdAndTypeAndStatusAndOwnerSignedAtIsNullAndReceiverSignedAtIsNullOrderByCreatedAtDesc(
                        request.itemId(), request.ownerId(), request.receiverId(), request.type(),
                        ContractStatus.PENDING_SIGNATURES)
                .orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        Contract contract = new Contract();
        contract.setId(UUID.randomUUID().toString());
        contract.setItemId(request.itemId());
        contract.setOwnerId(request.ownerId());
        contract.setReceiverId(request.receiverId());
        contract.setType(request.type());
        contract.setStatus(ContractStatus.PENDING_SIGNATURES);
        contract.setPrice(request.price());
        // The rental deposit is owner-set at listing time (ms-catalog enforces the
        // ≤20€ cap). The receiver-provided value in the request is ignored so the
        // buyer cannot manipulate the deposit by tweaking the contract payload.
        contract.setGuaranteeAmount(resolveGuaranteeAmount(request));
        contract.setGuaranteeStatus(GuaranteeStatus.NONE);
        contract.setConditions(request.conditions());
        contract.setReturnDate(request.returnDate());
        contract.setCreatedAt(LocalDateTime.now());
        // NOTE: do NOT reserve the item here. A contract is created when the buyer
        // opens the signing screen; backing out without signing must leave the item
        // available. Reservation happens on the first signature (see markSigned).
        return toDto(repository.save(contract));
    }

    /**
     * SALE with a positive price, or any RENT (which always locks a deposit),
     * needs the escrow to settle before the contract becomes ACTIVE. Donations,
     * cessions and zero-priced sales do not.
     */
    private boolean requiresPayment(Contract contract) {
        if (contract.getType() == com.thecircle.contracts.dto.ContractType.SALE) {
            return contract.getPrice() != null && contract.getPrice().signum() > 0;
        }
        if (contract.getType() == com.thecircle.contracts.dto.ContractType.RENT) {
            return contract.getGuaranteeAmount() != null && contract.getGuaranteeAmount().signum() > 0;
        }
        return false;
    }

    private BigDecimal resolveGuaranteeAmount(ContractCreateRequest request) {
        if (request.type() != com.thecircle.contracts.dto.ContractType.RENT) {
            return null;
        }
        CatalogClient.ArticleSnapshot article = catalogClient.getArticle(request.itemId());
        // Fail closed: the deposit is owner-set at listing time and must come from the
        // catalog so the receiver cannot manipulate it via the contract payload. Any
        // ambiguity (catalog down, article gone, deposit missing on a RENT article) is
        // surfaced as 503 so the buyer retries rather than locking in an attacker-chosen
        // amount.
        if (article == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not verify the rental deposit right now. Please try again in a moment.");
        }
        if (article.guaranteeAmount() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This rental item has no security deposit configured. Ask the owner to update the listing.");
        }
        return BigDecimal.valueOf(article.guaranteeAmount());
    }

    /**
     * Deletes stale, never-signed PENDING_SIGNATURES contracts. {@code create()}
     * persists a contract as soon as the buyer opens the signing screen, so backing
     * out without signing leaves an orphan row. This periodic sweep removes those
     * once they exceed the TTL, keeping {@code getByUser} free of dead PENDING rows.
     * Only never-signed contracts are removed (see repository note), so no RESERVED
     * article is left dangling.
     */
    @Scheduled(fixedDelayString = "${contracts.pending-cleanup-interval-ms:3600000}")
    @Transactional
    public void purgeStalePendingContracts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(pendingTtlHours);
        List<Contract> stale = repository
                .findByStatusAndCreatedAtBeforeAndOwnerSignedAtIsNullAndReceiverSignedAtIsNull(
                        ContractStatus.PENDING_SIGNATURES, cutoff);
        if (stale.isEmpty()) return;
        repository.deleteAll(stale);
        log.info("Purged {} stale unsigned PENDING_SIGNATURES contracts older than {}h", stale.size(), pendingTtlHours);
    }

    @Transactional(readOnly = true)
    public ContractDto get(String id) {
        return repository.findById(id).map(this::toDto).orElse(null);
    }

    /** True if {@code userId} is this contract's owner or receiver. Used to authorize PDF access. */
    public boolean isParty(ContractDto dto, String userId) {
        if (dto == null || userId == null) return false;
        return userId.equals(dto.getOwnerId()) || userId.equals(dto.getReceiverId());
    }

    @Transactional(readOnly = true)
    public List<ContractDto> getByUser(String userId) {
        return repository.findByOwnerIdOrReceiverIdOrderByCreatedAtDesc(userId, userId)
                .stream().map(this::toDto).toList();
    }

    /**
     * Removes the open contracts owned by a deleted user together with their
     * payments, generated PDFs and signature evidence.
     *
     * <p>Completed and cancelled contracts are retained as history. Receiver-side
     * contracts are also retained, because they belong to the other party's
     * product lifecycle.
     */
    @Transactional
    public int deleteOpenOwnerContracts(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }

        List<Contract> openOwnerContracts = repository.findByOwnerIdOrReceiverIdOrderByCreatedAtDesc(userId, userId)
                .stream()
                .filter(contract -> userId.equals(contract.getOwnerId()))
                .filter(contract -> contract.getStatus() != ContractStatus.COMPLETED)
                .filter(contract -> contract.getStatus() != ContractStatus.CANCELLED)
                // DELIVERED is a finished deal; keep it as history like COMPLETED.
                .filter(contract -> contract.getStatus() != ContractStatus.DELIVERED)
                .toList();

        if (openOwnerContracts.isEmpty()) {
            return 0;
        }

        List<String> contractIds = openOwnerContracts.stream().map(Contract::getId).toList();
        Set<String> itemIds = openOwnerContracts.stream()
                .map(Contract::getItemId)
                .filter(itemId -> itemId != null && !itemId.isBlank())
                .collect(Collectors.toSet());

        signatureSessionRepository.deleteByContractIdIn(contractIds);
        signatureRecordRepository.deleteByContractIdIn(contractIds);
        storedContractRepository.deleteByContractIdIn(contractIds);
        paymentRepository.deleteByContractIdIn(contractIds);
        repository.deleteAll(openOwnerContracts);

        for (String itemId : itemIds) {
            catalogClient.setStatus(itemId, "AVAILABLE");
        }

        log.info("Removed {} open owner contract(s) for deleted user {}", openOwnerContracts.size(), userId);
        return openOwnerContracts.size();
    }

    /**
     * Records one party's signature and links the produced PDF. The contract only
     * becomes ACTIVE once both the receiver and the owner have signed. Returns the
     * updated contract, or {@code null} if the id is unknown (e.g. direct API use
     * with an ad-hoc contract payload).
     */
    @Transactional
    public ContractDto markSigned(String contractId, String storedContractId, SignerRole role) {
        return markSigned(contractId, storedContractId, role, LocalDateTime.now());
    }

    /**
     * As {@link #markSigned(String, String, SignerRole)} but stamps an explicit
     * signing instant, so the timestamp persisted here matches the one rendered
     * onto the signed PDF instead of drifting by the render duration.
     */
    public ContractDto markSigned(String contractId, String storedContractId, SignerRole role, LocalDateTime signedAt) {
        if (contractId == null) return null;
        Contract contract = repository.findById(contractId).orElse(null);
        if (contract == null) return null;

        LocalDateTime now = signedAt;
        if (role == SignerRole.OWNER) {
            contract.setOwnerSignedAt(now);
        } else {
            contract.setReceiverSignedAt(now);
        }
        contract.setStoredContractId(storedContractId); // latest signed artifact

        boolean nowActive = false;
        boolean bothSigned = contract.getReceiverSignedAt() != null && contract.getOwnerSignedAt() != null;
        if (bothSigned) {
            // For SALE/RENT with a real amount due, the escrow must clear before the
            // contract goes ACTIVE. If the buyer hasn't paid yet, hold at
            // AWAITING_COUNTERPARTY so the UI can prompt them to complete checkout.
            if (requiresPayment(contract)) {
                if (paymentService.releaseEscrowOnDualSign(contract.getId()) != null) {
                    contract.setStatus(ContractStatus.ACTIVE);
                    contract.setSignedAt(now);
                    nowActive = true;
                } else {
                    contract.setStatus(ContractStatus.AWAITING_COUNTERPARTY);
                }
            } else {
                contract.setStatus(ContractStatus.ACTIVE);
                contract.setSignedAt(now);
                nowActive = true;
            }
        } else {
            contract.setStatus(ContractStatus.PENDING_SIGNATURES);
        }
        ContractDto result = toDto(repository.save(contract));
        // First signature → reserve the item; both signatures → sold (hidden from catalog).
        // Run the catalog notification AFTER the DB transaction commits: it is a
        // synchronous cross-service HTTP call and must not be held inside the
        // transaction (row locks across a network round-trip). If no transaction is
        // active (e.g. direct call in tests), fall back to calling inline.
        String itemId = contract.getItemId();
        String newStatus = nowActive ? "SOLD" : "RESERVED";
        boolean sold = nowActive;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyCatalog(itemId, newStatus, sold);
                }
            });
        } else {
            notifyCatalog(itemId, newStatus, sold);
        }
        return result;
    }

    /**
     * Pushes the article availability to ms-catalog. Best-effort, but a failed SOLD
     * transition is logged at error level: it leaves a fully-signed item visible and
     * buyable in the catalog with no automatic retry, so it needs manual reconciliation.
     */
    private void notifyCatalog(String itemId, String status, boolean sold) {
        boolean ok = catalogClient.setStatus(itemId, status);
        if (!ok && sold) {
            log.error("Article {} stays visible: failed to flip it to SOLD in ms-catalog after both "
                    + "parties signed. Needs reconciliation (item is sold but still browsable/buyable).", itemId);
        }
    }

    // --- Security deposit (guarantee) lifecycle ---

    /** Receiver locks the security deposit. NONE → DEPOSITED. */
    @Transactional
    public ContractDto depositGuarantee(String contractId) {
        Contract contract = require(contractId);
        if (contract.getGuaranteeStatus() != GuaranteeStatus.NONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Guarantee already " + contract.getGuaranteeStatus());
        }
        contract.setGuaranteeStatus(GuaranteeStatus.DEPOSITED);
        return toDto(repository.save(contract));
    }

    /** Owner returns the deposit to the receiver (item returned OK). DEPOSITED → RELEASED, contract COMPLETED. */
    @Transactional
    public ContractDto releaseGuarantee(String contractId) {
        return settleGuarantee(contractId, GuaranteeStatus.RELEASED);
    }

    /** Owner keeps the deposit (damage / no return). DEPOSITED → CLAIMED, contract COMPLETED. */
    @Transactional
    public ContractDto claimGuarantee(String contractId) {
        return settleGuarantee(contractId, GuaranteeStatus.CLAIMED);
    }

    private ContractDto settleGuarantee(String contractId, GuaranteeStatus target) {
        Contract contract = require(contractId);
        if (contract.getGuaranteeStatus() != GuaranteeStatus.DEPOSITED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Guarantee must be DEPOSITED to be " + target + " (current: " + contract.getGuaranteeStatus() + ")");
        }
        contract.setGuaranteeStatus(target);
        contract.setStatus(ContractStatus.COMPLETED);
        return toDto(repository.save(contract));
    }

    // --- Delivery confirmation ---

    /**
     * Records the caller's hand-over confirmation. The owner confirms the item was
     * delivered, the receiver confirms it was received. Both confirmations are only
     * accepted once the contract is fully signed (ACTIVE/DELIVERED). When both sides
     * have confirmed the contract moves to DELIVERED, which opens reviews. Idempotent
     * per party: re-confirming is a no-op that returns the current state.
     */
    @Transactional
    public ContractDto confirmDelivery(String contractId, String callerId) {
        Contract contract = require(contractId);
        if (callerId == null
                || (!callerId.equals(contract.getOwnerId()) && !callerId.equals(contract.getReceiverId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a party to this contract");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Delivery can only be confirmed on a fully signed (ACTIVE) contract.");
        }
        // Deposit rentals settle via the guarantee return flow (→ COMPLETED), not
        // the hand-over handshake; reject so they can't be flipped to DELIVERED.
        if (contract.getType() == com.thecircle.contracts.dto.ContractType.RENT
                && contract.getGuaranteeAmount() != null && contract.getGuaranteeAmount().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This rental is settled by returning the deposit, not by confirming delivery.");
        }
        // Atomic per-column updates so concurrent confirmations by the two parties
        // cannot overwrite each other; then flip to DELIVERED once both are set.
        LocalDateTime now = LocalDateTime.now();
        if (callerId.equals(contract.getOwnerId())) {
            repository.markOwnerDelivered(contractId, now);
        } else {
            repository.markReceiverReceived(contractId, now);
        }
        repository.markDeliveredIfBothConfirmed(contractId, ContractStatus.ACTIVE, ContractStatus.DELIVERED);
        return toDto(require(contractId));
    }

    /**
     * Whether the deal has reached the point where the two parties may review each
     * other. For sales/donations/cessions that is a confirmed hand-over (DELIVERED);
     * for rentals it is the devolution of the item (guarantee settled → COMPLETED).
     * Used to gate reviews in ms-users.
     */
    public boolean isReviewable(Contract contract) {
        boolean handedOver = contract.getOwnerDeliveredAt() != null && contract.getReceiverReceivedAt() != null;
        boolean rentalReturned = contract.getType() == com.thecircle.contracts.dto.ContractType.RENT
                && contract.getStatus() == ContractStatus.COMPLETED;
        return handedOver || rentalReturned;
    }

    @Transactional(readOnly = true)
    public Contract getEntity(String contractId) {
        return repository.findById(contractId).orElse(null);
    }

    private Contract require(String contractId) {
        return repository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
    }

    /**
     * The two party profiles fetched while enriching a contract. Lets a caller
     * reuse the already-fetched profiles (e.g. to read a signer's language) instead
     * of hitting ms-users again. primarySigner = receiver, secondarySigner = owner.
     */
    public record SignerProfiles(UsersClient.UserProfile receiver, UsersClient.UserProfile owner) {
        /** Preferred language tag of the given role, or null if unknown. Mirrors {@link #getUserLanguage}. */
        public String languageFor(SignerRole role) {
            if (role == null) return null;
            UsersClient.UserProfile profile = role == SignerRole.OWNER ? owner : receiver;
            return profile != null ? profile.language() : null;
        }
    }

    /**
     * Fills the PDF signer blocks from ms-users so the rendered contract shows real
     * names instead of [N/A]. Best-effort: a failed lookup leaves that block empty.
     * primarySigner = receiver (the buyer who signs), secondarySigner = owner.
     * Returns the fetched profiles so the caller can reuse them (e.g. for locale)
     * without re-querying ms-users.
     */
    public SignerProfiles enrichSigners(ContractDto dto, String signerEmail) {
        if (dto == null) return new SignerProfiles(null, null);
        UsersClient.UserProfile receiver = null;
        UsersClient.UserProfile owner = null;
        if (dto.getReceiverId() != null) {
            receiver = usersClient.getProfile(dto.getReceiverId());
            dto.setPrimarySigner(buildSigner(receiver, signerEmail));
        }
        if (dto.getOwnerId() != null) {
            owner = usersClient.getProfile(dto.getOwnerId());
            dto.setSecondarySigner(buildSigner(owner, null));
        }
        return new SignerProfiles(receiver, owner);
    }

    /**
     * Best-effort lookup of a user's preferred language tag (e.g. "es"/"en") to
     * localize the rendered PDF or email. Returns null on any gap so the renderer
     * falls back to English; a users-service hiccup never blocks signing. Single
     * shared helper for the {@code getProfile → language} lookup.
     */
    public String getUserLanguage(String userId) {
        if (userId == null || userId.isBlank()) return null;
        UsersClient.UserProfile profile = usersClient.getProfile(userId);
        return profile != null ? profile.language() : null;
    }

    private SignerDto buildSigner(UsersClient.UserProfile profile, String fallbackEmail) {
        if (profile == null && fallbackEmail == null) return null;
        SignerDto signer = new SignerDto();
        if (profile != null) {
            signer.setFullName(profile.fullName());
            signer.setEmail(profile.email() != null ? profile.email() : fallbackEmail);
            signer.setAddress(profile.address());
            signer.setIdNumber(profile.idNumber());
        } else {
            signer.setEmail(fallbackEmail);
        }
        return signer;
    }

    private ContractDto toDto(Contract c) {
        ContractDto dto = new ContractDto(
                c.getId(),
                c.getItemId(),
                c.getOwnerId(),
                c.getReceiverId(),
                c.getType(),
                c.getStatus(),
                c.getGuaranteeAmount(),
                c.getConditions(),
                c.getReturnDate(),
                c.getCreatedAt(),
                c.getSignedAt()
        );
        dto.setPrice(c.getPrice());
        dto.setGuaranteeStatus(c.getGuaranteeStatus());
        dto.setReceiverSignedAt(c.getReceiverSignedAt());
        dto.setOwnerSignedAt(c.getOwnerSignedAt());
        dto.setOwnerDeliveredAt(c.getOwnerDeliveredAt());
        dto.setReceiverReceivedAt(c.getReceiverReceivedAt());
        dto.setStoredContractId(c.getStoredContractId());
        return dto;
    }
}
