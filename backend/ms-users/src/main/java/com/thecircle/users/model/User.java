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

    // Postal address and government ID number. Collected at registration and
    // editable from settings; required on signed contract PDFs (eIDAS identity).
    @Column(length = 255)
    private String address;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    // Encrypted IBAN of the user's payout account (AES-GCM, key derived from
    // JWT_SECRET via HKDF). Only sellers need it; receivers paying for symbolic
    // transactions never touch a stored IBAN. The plaintext IBAN is never
    // exposed via any API — only iban_last4 is returned for display.
    @Column(name = "iban_encrypted", columnDefinition = "text")
    private String ibanEncrypted;

    @Column(name = "iban_last4", length = 4)
    private String ibanLast4;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.UNVERIFIED;

    @Builder.Default
    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean not null default false")
    private boolean emailVerified = false;
    
    @Builder.Default
    @Column(name = "marketing_emails_opt_in", nullable = false, columnDefinition = "boolean not null default true")
    private boolean marketingEmailsOptIn = true;

    @Builder.Default
    @Column(name = "system_emails_opt_in", nullable = false, columnDefinition = "boolean not null default true")
    private boolean systemEmailsOptIn = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
        return deletedAt == null;
    }
}
