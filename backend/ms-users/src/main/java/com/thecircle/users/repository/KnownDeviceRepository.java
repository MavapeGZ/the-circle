package com.thecircle.users.repository;

import com.thecircle.users.model.KnownDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnownDeviceRepository extends JpaRepository<KnownDevice, Long> {
    Optional<KnownDevice> findByDeviceToken(String deviceToken);
    Optional<KnownDevice> findByUserIdAndDeviceToken(Long userId, String deviceToken);
    List<KnownDevice> findByUserId(Long userId);
}
