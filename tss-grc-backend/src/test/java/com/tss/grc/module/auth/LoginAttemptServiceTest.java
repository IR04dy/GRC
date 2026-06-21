package com.tss.grc.module.auth;

import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SEC-AUTH-001 — brute-force lockout bookkeeping. */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private UserRepository userRepository;
    private LoginAttemptService service;

    @BeforeEach void setUp() {
        service = new LoginAttemptService(userRepository);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "lockoutMinutes", 15L);
    }

    private static User user(int attempts) {
        User u = new User();
        u.setId(1L); u.setUsername("bob");
        u.setFailedLoginAttempts(attempts); u.setIsLocked(false);
        return u;
    }

    @Test void onFailure_belowThreshold_incrementsWithoutLocking() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(0)));
        service.onFailure("bob");
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(cap.getValue().getIsLocked()).isFalse();
    }

    @Test void onFailure_atThreshold_locksWithFutureExpiry() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2))); // 3rd attempt
        service.onFailure("bob");
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getFailedLoginAttempts()).isEqualTo(3);
        assertThat(cap.getValue().getIsLocked()).isTrue();
        assertThat(cap.getValue().getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test void onFailure_unknownUser_doesNothing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        service.onFailure("ghost");
        verify(userRepository, never()).save(any());
    }

    @Test void onSuccess_resetsCounterUnlocksAndStampsLastLogin() {
        User u = user(3);
        u.setIsLocked(true);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        service.onSuccess(1L);
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getFailedLoginAttempts()).isZero();
        assertThat(cap.getValue().getIsLocked()).isFalse();
        assertThat(cap.getValue().getLockedUntil()).isNull();
        assertThat(cap.getValue().getLastLoginAt()).isNotNull();
    }
}
