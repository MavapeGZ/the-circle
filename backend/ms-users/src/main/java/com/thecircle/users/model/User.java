package com.thecircle.users.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.UNVERIFIED;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "kyc_verified")
    private Boolean legacyKycVerified;

    @Builder.Default
    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        syncLegacyKycVerified();
    }

    @PreUpdate
    protected void onUpdate() {
        syncLegacyKycVerified();
    }

    @PostLoad
    protected void applyLegacyKycVerified() {
        if (Boolean.TRUE.equals(legacyKycVerified)
                && (normalizeKycStatus() == KycStatus.UNVERIFIED)) {
            this.kycStatus = KycStatus.VERIFIED;
        }
    }

    private void syncLegacyKycVerified() {
        normalizeKycStatus();
        legacyKycVerified = kycStatus == KycStatus.VERIFIED;
    }

    private KycStatus normalizeKycStatus() {
        if (kycStatus == null) {
            kycStatus = KycStatus.UNVERIFIED;
        }
        return kycStatus;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
