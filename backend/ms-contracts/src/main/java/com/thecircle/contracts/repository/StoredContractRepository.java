package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.StoredContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface StoredContractRepository extends JpaRepository<StoredContract, String> {

	void deleteByContractIdIn(Collection<String> contractIds);
}

