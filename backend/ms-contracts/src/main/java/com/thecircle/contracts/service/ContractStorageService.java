package com.thecircle.contracts.service;

import com.thecircle.contracts.model.StoredContract;
import com.thecircle.contracts.repository.StoredContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class ContractStorageService {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ID_LENGTH = 21;

    private final StoredContractRepository repository;

    public ContractStorageService(StoredContractRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StoredContract save(byte[] pdfBytes, String originalContractId) {
        String id = generateNanoId();
        String filename = id + ".pdf";
        StoredContract sc = new StoredContract(id, filename, originalContractId, pdfBytes, LocalDateTime.now());
        return repository.save(sc);
    }

    @Transactional
    public StoredContract saveExisting(StoredContract sc) {
        return repository.save(sc);
    }

    @Transactional(readOnly = true)
    public StoredContract get(String id) {
        return repository.findById(id).orElse(null);
    }

    private String generateNanoId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            int idx = RANDOM.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(idx));
        }
        return sb.toString();
    }
}
