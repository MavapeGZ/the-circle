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
import com.thecircle.contracts.repository.ContractRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    /** TTL (hours) after which an unsigned PENDING contract is purged. */
    @Value("${contracts.pending-ttl-hours:72}")
    private long pendingTtlHours;

    public ContractService(ContractRepository repository, UsersClient usersClient, CatalogClient catalogClient) {
        this.repository = repository;
        this.usersClient = usersClient;
        this.catalogClient = catalogClient;
    }

    @Transactional
    public ContractDto create(ContractCreateRequest request) {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID().toString());
        contract.setItemId(request.itemId());
        contract.setOwnerId(request.ownerId());
        contract.setReceiverId(request.receiverId());
        contract.setType(request.type());
        contract.setStatus(ContractStatus.PENDING_SIGNATURES);
        contract.setPrice(request.price());
        contract.setGuaranteeAmount(request.guaranteeAmount());
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
     * Records one party's signature and links the produced PDF. The contract only
     * becomes ACTIVE once both the receiver and the owner have signed. Returns the
     * updated contract, or {@code null} if the id is unknown (e.g. direct API use
     * with an ad-hoc contract payload).
     */
    @Transactional
    public ContractDto markSigned(String contractId, String storedContractId, SignerRole role) {
        if (contractId == null) return null;
        Contract contract = repository.findById(contractId).orElse(null);
        if (contract == null) return null;

        LocalDateTime now = LocalDateTime.now();
        if (role == SignerRole.OWNER) {
            contract.setOwnerSignedAt(now);
        } else {
            contract.setReceiverSignedAt(now);
        }
        contract.setStoredContractId(storedContractId); // latest signed artifact

        boolean nowActive = false;
        if (contract.getReceiverSignedAt() != null && contract.getOwnerSignedAt() != null) {
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setSignedAt(now);
            nowActive = true;
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

    private Contract require(String contractId) {
        return repository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
    }

    /**
     * Fills the PDF signer blocks from ms-users so the rendered contract shows real
     * names instead of [N/A]. Best-effort: a failed lookup leaves that block empty.
     * primarySigner = receiver (the buyer who signs), secondarySigner = owner.
     */
    public void enrichSigners(ContractDto dto, String signerEmail) {
        if (dto == null) return;
        if (dto.getReceiverId() != null) {
            dto.setPrimarySigner(buildSigner(usersClient.getProfile(dto.getReceiverId()), signerEmail));
        }
        if (dto.getOwnerId() != null) {
            dto.setSecondarySigner(buildSigner(usersClient.getProfile(dto.getOwnerId()), null));
        }
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
        dto.setStoredContractId(c.getStoredContractId());
        return dto;
    }
}
