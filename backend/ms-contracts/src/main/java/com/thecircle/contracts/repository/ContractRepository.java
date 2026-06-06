package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {

    List<Contract> findByOwnerIdOrReceiverIdOrderByCreatedAtDesc(String ownerId, String receiverId);
}
