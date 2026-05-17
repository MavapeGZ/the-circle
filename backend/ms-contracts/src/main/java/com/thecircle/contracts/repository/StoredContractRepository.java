package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.StoredContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredContractRepository extends JpaRepository<StoredContract, String> {
}

