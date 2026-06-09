package com.thecircle.contracts.service;

import com.thecircle.contracts.client.CatalogClient;
import com.thecircle.contracts.client.UsersClient;
import com.thecircle.contracts.dto.ContractCreateRequest;
import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.dto.GuaranteeStatus;
import com.thecircle.contracts.dto.SignerRole;
import com.thecircle.contracts.model.Contract;
import com.thecircle.contracts.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository repository;

    @Mock
    private UsersClient usersClient;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private ContractService service;

    // --- helpers ---

    private ContractCreateRequest buildRequest() {
        return new ContractCreateRequest("item-1", "10", "20", ContractType.RENT,
                new BigDecimal("5"), new BigDecimal("50"), "Rent: Bike", LocalDateTime.now().plusDays(7));
    }

    private Contract storedContract(String id) {
        Contract c = new Contract();
        c.setId(id);
        c.setItemId("item-1");
        c.setOwnerId("10");
        c.setReceiverId("20");
        c.setType(ContractType.RENT);
        c.setStatus(ContractStatus.PENDING_SIGNATURES);
        c.setGuaranteeStatus(GuaranteeStatus.NONE);
        c.setGuaranteeAmount(new BigDecimal("50"));
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private void echoSave() {
        when(repository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- create ---

    @Test
    void create_persistsPendingContractWithGeneratedId() {
        echoSave();
        ContractDto dto = service.create(buildRequest());

        assertNotNull(dto.getContractId());
        assertEquals("item-1", dto.getItemId());
        assertEquals(ContractStatus.PENDING_SIGNATURES, dto.getStatus());
        assertEquals(GuaranteeStatus.NONE, dto.getGuaranteeStatus());
        assertNull(dto.getReceiverSignedAt());
        assertNull(dto.getOwnerSignedAt());
        verify(repository).save(any(Contract.class));
    }

    // --- get / getByUser ---

    @Test
    void get_unknownId_returnsNull() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertNull(service.get("missing"));
    }

    @Test
    void getByUser_mapsAllContracts() {
        when(repository.findByOwnerIdOrReceiverIdOrderByCreatedAtDesc("10", "10"))
                .thenReturn(List.of(storedContract("c1"), storedContract("c2")));
        List<ContractDto> result = service.getByUser("10");
        assertEquals(2, result.size());
    }

    // --- markSigned (dual-party) ---

    @Test
    void markSigned_nullContractId_returnsNullAndDoesNotPersist() {
        assertNull(service.markSigned(null, "sc-1", SignerRole.RECEIVER));
        verify(repository, never()).save(any());
    }

    @Test
    void markSigned_receiverOnly_staysPending() {
        Contract c = storedContract("c1");
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        echoSave();

        ContractDto dto = service.markSigned("c1", "sc-1", SignerRole.RECEIVER);

        assertEquals(ContractStatus.PENDING_SIGNATURES, dto.getStatus());
        assertNotNull(dto.getReceiverSignedAt());
        assertNull(dto.getOwnerSignedAt());
        assertNull(dto.getSignedAt());
        assertEquals("sc-1", c.getStoredContractId());
    }

    @Test
    void markSigned_ownerAfterReceiver_becomesActive() {
        Contract c = storedContract("c1");
        c.setReceiverSignedAt(LocalDateTime.now().minusMinutes(5));
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        echoSave();

        ContractDto dto = service.markSigned("c1", "sc-2", SignerRole.OWNER);

        assertEquals(ContractStatus.ACTIVE, dto.getStatus());
        assertNotNull(dto.getOwnerSignedAt());
        assertNotNull(dto.getSignedAt());
    }

    @Test
    void markSigned_ownerOnly_staysPending() {
        Contract c = storedContract("c1");
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        echoSave();

        ContractDto dto = service.markSigned("c1", "sc-1", SignerRole.OWNER);

        assertEquals(ContractStatus.PENDING_SIGNATURES, dto.getStatus());
        assertNotNull(dto.getOwnerSignedAt());
        assertNull(dto.getReceiverSignedAt());
    }

    // --- guarantee lifecycle ---

    @Test
    void depositGuarantee_fromNone_becomesDeposited() {
        Contract c = storedContract("c1");
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        echoSave();

        ContractDto dto = service.depositGuarantee("c1");

        assertEquals(GuaranteeStatus.DEPOSITED, dto.getGuaranteeStatus());
    }

    @Test
    void depositGuarantee_alreadyDeposited_throws409() {
        Contract c = storedContract("c1");
        c.setGuaranteeStatus(GuaranteeStatus.DEPOSITED);
        when(repository.findById("c1")).thenReturn(Optional.of(c));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.depositGuarantee("c1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void depositGuarantee_unknownContract_throws404() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.depositGuarantee("missing"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void releaseGuarantee_fromDeposited_completesContract() {
        Contract c = storedContract("c1");
        c.setStatus(ContractStatus.ACTIVE);
        c.setGuaranteeStatus(GuaranteeStatus.DEPOSITED);
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        echoSave();

        ContractDto dto = service.releaseGuarantee("c1");

        assertEquals(GuaranteeStatus.RELEASED, dto.getGuaranteeStatus());
        assertEquals(ContractStatus.COMPLETED, dto.getStatus());
    }

    @Test
    void claimGuarantee_fromDeposited_completesContract() {
        Contract c = storedContract("c1");
        c.setStatus(ContractStatus.ACTIVE);
        c.setGuaranteeStatus(GuaranteeStatus.DEPOSITED);
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        echoSave();

        ContractDto dto = service.claimGuarantee("c1");

        assertEquals(GuaranteeStatus.CLAIMED, dto.getGuaranteeStatus());
        assertEquals(ContractStatus.COMPLETED, dto.getStatus());
    }

    @Test
    void releaseGuarantee_notDeposited_throws409() {
        Contract c = storedContract("c1"); // guarantee NONE
        when(repository.findById("c1")).thenReturn(Optional.of(c));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.releaseGuarantee("c1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // --- enrichSigners ---

    @Test
    void enrichSigners_fillsBothPartiesFromUsersService() {
        ContractDto dto = new ContractDto();
        dto.setOwnerId("10");
        dto.setReceiverId("20");
        when(usersClient.getProfile("20")).thenReturn(new UsersClient.UserProfile(20L, null, "Ana", "Buyer", "1 Main St", "X123"));
        when(usersClient.getProfile("10")).thenReturn(new UsersClient.UserProfile(10L, "owner@x.com", "Leo", "Owner", "2 Oak Ave", "Y456"));

        service.enrichSigners(dto, "buyer@x.com");

        assertEquals("Ana Buyer", dto.getPrimarySigner().getFullName());
        assertEquals("buyer@x.com", dto.getPrimarySigner().getEmail()); // profile email null → fallback
        assertEquals("1 Main St", dto.getPrimarySigner().getAddress());
        assertEquals("X123", dto.getPrimarySigner().getIdNumber());
        assertEquals("Leo Owner", dto.getSecondarySigner().getFullName());
        assertEquals("owner@x.com", dto.getSecondarySigner().getEmail());
        assertEquals("Y456", dto.getSecondarySigner().getIdNumber());
    }

    @Test
    void enrichSigners_usersLookupFails_leavesSignerWithFallbackEmailOnly() {
        ContractDto dto = new ContractDto();
        dto.setReceiverId("20");
        lenient().when(usersClient.getProfile("20")).thenReturn(null);

        service.enrichSigners(dto, "buyer@x.com");

        assertNotNull(dto.getPrimarySigner());
        assertNull(dto.getPrimarySigner().getFullName());
        assertEquals("buyer@x.com", dto.getPrimarySigner().getEmail());
    }
}
