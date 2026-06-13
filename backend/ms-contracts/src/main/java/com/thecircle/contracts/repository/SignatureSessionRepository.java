package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.SignatureSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface SignatureSessionRepository extends JpaRepository<SignatureSession, String> {

	void deleteByContractIdIn(Collection<String> contractIds);
}
