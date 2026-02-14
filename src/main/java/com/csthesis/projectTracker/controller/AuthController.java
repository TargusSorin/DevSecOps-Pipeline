package com.csthesis.projectTracker.controller;

import com.csthesis.projectTracker.dto.auth.AuthRequest;
import com.csthesis.projectTracker.dto.auth.AuthResponse;
import com.csthesis.projectTracker.dto.auth.RegisterRequest;
import com.csthesis.projectTracker.model.UserAccount;
import com.csthesis.projectTracker.repository.UserAccountRepository;
import com.csthesis.projectTracker.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().trim();
        if (username.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be blank");
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userAccountRepository.save(user);

        String token = jwtService.generateToken(toUserDetails(user));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String token;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            token = jwtService.generateToken(userDetails);
        } else {
            token = jwtService.generateToken(User.withUsername(authentication.getName())
                    .password("")
                    .authorities("USER")
                    .build());
        }
        return new AuthResponse(token);
    }

    private UserDetails toUserDetails(UserAccount user) {
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("USER")
                .build();
    }
}
