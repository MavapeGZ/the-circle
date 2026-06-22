package com.thecircle.users.repository;

import com.thecircle.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(String publicId);

    boolean existsByEmail(String email);
}
