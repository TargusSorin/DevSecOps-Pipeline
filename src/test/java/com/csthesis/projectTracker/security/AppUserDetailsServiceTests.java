package com.csthesis.projectTracker.security;

import com.csthesis.projectTracker.model.UserAccount;
import com.csthesis.projectTracker.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    private AppUserDetailsService appUserDetailsService;

    @BeforeEach
    void setUp() {
        appUserDetailsService = new AppUserDetailsService(userAccountRepository);
    }

    @Test
    void loadUserByUsernameReturnsMappedUserDetails() {
        UserAccount user = new UserAccount();
        user.setUsername("alice");
        user.setPasswordHash("encoded-password");
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails userDetails = appUserDetailsService.loadUserByUsername("alice");

        assertThat(userDetails.getUsername()).isEqualTo("alice");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("USER");
    }

    @Test
    void loadUserByUsernameThrowsWhenUserDoesNotExist() {
        when(userAccountRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
    }
}
