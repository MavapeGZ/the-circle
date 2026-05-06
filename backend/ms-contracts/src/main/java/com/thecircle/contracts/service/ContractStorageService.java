package com.thecircle.contracts.service;

import com.thecircle.contracts.model.StoredContract;
import com.thecircle.contracts.repository.StoredContractRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class ContractStorageService {

    private final StoredContractRepository repository;

    @Value("${contracts.storage.dir:contracts}")
    private String storageDir;

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ID_LENGTH = 21;

    public ContractStorageService(StoredContractRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void ensureDir() {
        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public StoredContract save(byte[] pdfBytes, String originalContractId) throws Exception {
        String id = generateNanoId();
        String filename = id + ".pdf";

        // save to disk
        File out = new File(storageDir, filename);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(pdfBytes);
        }

        // persist in DB
        StoredContract sc = new StoredContract(id, filename, originalContractId, pdfBytes, LocalDateTime.now());
        return repository.save(sc);
    }

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
