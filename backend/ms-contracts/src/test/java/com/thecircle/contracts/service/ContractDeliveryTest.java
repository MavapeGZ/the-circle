package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.model.Contract;
import com.thecircle.contracts.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractDeliveryTest {

    @Mock ContractRepository repository;

    private ContractService service() {
        return new ContractService(repository, null, null, null, null, null, null, null);
    }

    private Contract active(String owner, String receiver, ContractType type) {
        Contract c = new Contract();
        c.setId("c1");
        c.setOwnerId(owner);
        c.setReceiverId(receiver);
        c.setType(type);
        c.setStatus(ContractStatus.ACTIVE);
        return c;
    }

    @Test
    void confirmDelivery_nonParty_forbidden() {
        when(repository.findById("c1")).thenReturn(Optional.of(active("1", "2", ContractType.SALE)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service().confirmDelivery("c1", "99"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void confirmDelivery_notSigned_conflict() {
        Contract c = active("1", "2", ContractType.SALE);
        c.setStatus(ContractStatus.PENDING_SIGNATURES);
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service().confirmDelivery("c1", "1"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void confirmDelivery_ownerOnly_staysActive() {
        Contract c = active("1", "2", ContractType.SALE);
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        when(repository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractDto dto = service().confirmDelivery("c1", "1");

        assertNotNull(dto.getOwnerDeliveredAt());
        assertNull(dto.getReceiverReceivedAt());
        assertEquals(ContractStatus.ACTIVE, dto.getStatus());
    }

    @Test
    void confirmDelivery_bothParties_becomesDelivered() {
        Contract c = active("1", "2", ContractType.SALE);
        when(repository.findById("c1")).thenReturn(Optional.of(c));
        when(repository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractService service = service();
        service.confirmDelivery("c1", "1"); // owner delivered
        ContractDto dto = service.confirmDelivery("c1", "2"); // receiver received

        assertEquals(ContractStatus.DELIVERED, dto.getStatus());
    }

    @Test
    void isReviewable_saleNeedsBothHandOver() {
        ContractService service = service();
        Contract c = active("1", "2", ContractType.SALE);
        assertFalse(service.isReviewable(c));
        c.setOwnerDeliveredAt(java.time.LocalDateTime.now());
        assertFalse(service.isReviewable(c));
        c.setReceiverReceivedAt(java.time.LocalDateTime.now());
        assertTrue(service.isReviewable(c));
    }

    @Test
    void isReviewable_rentalOnReturn() {
        ContractService service = service();
        Contract c = active("1", "2", ContractType.RENT);
        assertFalse(service.isReviewable(c)); // ACTIVE, not yet returned
        c.setStatus(ContractStatus.COMPLETED);
        assertTrue(service.isReviewable(c)); // deposit settled → returned
    }
}
