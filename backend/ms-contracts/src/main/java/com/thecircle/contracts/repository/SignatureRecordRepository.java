package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.SignatureRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignatureRecordRepository extends JpaRepository<SignatureRecord, String> {
    List<SignatureRecord> findByStoredContractIdOrderBySignedAtUtcAsc(String storedContractId);
    List<SignatureRecord> findByContractIdOrderBySignedAtUtcAsc(String contractId);
}
