package com.thecircle.contracts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${signature.otp.length:6}")
    private int otpLength;

    public String generate() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public String hash(String otp) {
        return encoder.encode(otp);
    }

    public boolean matches(String otp, String hash) {
        if (otp == null || hash == null) return false;
        try {
            return encoder.matches(otp, hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
