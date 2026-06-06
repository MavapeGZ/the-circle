package com.thecircle.contracts.service;

import com.thecircle.contracts.client.UsersClient;
import com.thecircle.contracts.dto.ContractCreateRequest;
import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.GuaranteeStatus;
import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.dto.SignerRole;
import com.thecircle.contracts.model.Contract;
import com.thecircle.contracts.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final ContractRepository repository;
    private final UsersClient usersClient;

    public ContractService(ContractRepository repository, UsersClient usersClient) {
        this.repository = repository;
        this.usersClient = usersClient;
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
        contract.setGuaranteeAmount(request.guaranteeAmount());
        contract.setGuaranteeStatus(GuaranteeStatus.NONE);
        contract.setConditions(request.conditions());
        contract.setReturnDate(request.returnDate());
        contract.setCreatedAt(LocalDateTime.now());
        return toDto(repository.save(contract));
    }

    @Transactional(readOnly = true)
    public ContractDto get(String id) {
        return repository.findById(id).map(this::toDto).orElse(null);
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

        if (contract.getReceiverSignedAt() != null && contract.getOwnerSignedAt() != null) {
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setSignedAt(now);
        } else {
            contract.setStatus(ContractStatus.PENDING_SIGNATURES);
        }
        return toDto(repository.save(contract));
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
        dto.setGuaranteeStatus(c.getGuaranteeStatus());
        dto.setReceiverSignedAt(c.getReceiverSignedAt());
        dto.setOwnerSignedAt(c.getOwnerSignedAt());
        return dto;
    }
}
