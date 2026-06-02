package com.thecircle.contracts.service;

import com.thecircle.contracts.model.StoredContract;
import com.thecircle.contracts.repository.StoredContractRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@EnableAutoConfiguration
@EntityScan("com.thecircle.contracts.model")
@EnableJpaRepositories("com.thecircle.contracts.repository")
@Import(ContractStorageService.class)
public class ContractStorageServiceTest {

    @Autowired
    private ContractStorageService service;

    @Autowired
    private StoredContractRepository repository;

    @Test
    public void save_persistsBytesAndMetadata() {
        byte[] pdf = "fake-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        StoredContract sc = service.save(pdf, "contract-123");

        assertNotNull(sc.getId());
        assertEquals(21, sc.getId().length());
        assertEquals(sc.getId() + ".pdf", sc.getFilename());
        assertEquals("contract-123", sc.getContractId());
        assertArrayEquals(pdf, sc.getData());
        assertNotNull(sc.getCreatedAt());

        StoredContract found = repository.findById(sc.getId()).orElseThrow();
        assertArrayEquals(pdf, found.getData());
    }

    @Test
    public void get_returnsNullWhenMissing() {
        assertNull(service.get("does-not-exist"));
    }

    @Test
    public void save_generatesUniqueIds() {
        StoredContract a = service.save(new byte[]{1}, "x");
        StoredContract b = service.save(new byte[]{2}, "x");
        assertNotEquals(a.getId(), b.getId());
    }
}
