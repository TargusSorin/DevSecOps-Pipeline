package com.csthesis.projectTracker.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {

    private static final String VALID_SECRET = "this-is-a-very-long-test-secret-key-1234567890";

    private JwtService jwtService;
    private UserDetails alice;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(jwtProperties(VALID_SECRET, 3_600_000L));
        alice = User.withUsername("alice")
                .password("password")
                .authorities("USER")
                .build();
    }

    @Test
    void generateTokenAndExtractUsernameRoundTrip() {
        String token = jwtService.generateToken(alice);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isTokenValidReturnsTrueForMatchingUser() {
        String token = jwtService.generateToken(alice);

        assertThat(jwtService.isTokenValid(token, alice)).isTrue();
    }

    @Test
    void isTokenValidReturnsFalseForDifferentUser() {
        String token = jwtService.generateToken(alice);
        UserDetails bob = User.withUsername("bob")
                .password("password")
                .authorities("USER")
                .build();

        assertThat(jwtService.isTokenValid(token, bob)).isFalse();
    }

    @Test
    void extractUsernameRejectsTamperedToken() {
        String token = jwtService.generateToken(alice);
        String tamperedToken = token.substring(0, token.length() - 2) + "aa";

        assertThatThrownBy(() -> jwtService.extractUsername(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateTokenFailsWhenSecretIsTooShort() {
        JwtService shortSecretService = new JwtService(jwtProperties("too-short-secret", 3_600_000L));

        assertThatThrownBy(() -> shortSecretService.generateToken(alice))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    private JwtProperties jwtProperties(String secret, long expirationMs) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(secret);
        jwtProperties.setExpirationMs(expirationMs);
        return jwtProperties;
    }
}
