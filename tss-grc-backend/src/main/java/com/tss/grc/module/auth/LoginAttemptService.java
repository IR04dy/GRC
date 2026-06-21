package com.tss.grc.module.auth;

import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SEC-AUTH-001 — brute-force lockout enforcement. The lockout columns
 * (failedLoginAttempts / isLocked / lockedUntil) existed on {@link User} and
 * {@code CustomUserDetailsService} already REJECTS a locked account, but nothing
 * ever incremented the counter or set the lock. This service does that, driven
 * from {@code AuthController.login()}.
 *
 * <p>Every method is best-effort: any failure is swallowed so a bookkeeping
 * error can never turn a valid login into a 500 or block authentication.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;

    @Value("${grc.security.max-failed-login-attempts:5}")
    private int maxAttempts;

    @Value("${grc.security.lockout-duration-minutes:30}")
    private long lockoutMinutes;

    /** Record a failed login for {@code username}; lock the account once the
     *  attempt count reaches the configured threshold. */
    @Transactional
    public void onFailure(String username) {
        try {
            userRepository.findByUsername(username).ifPresent(u -> {
                int attempts = (u.getFailedLoginAttempts() == null ? 0 : u.getFailedLoginAttempts()) + 1;
                u.setFailedLoginAttempts(attempts);
                if (attempts >= maxAttempts) {
                    u.setIsLocked(true);
                    u.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
                    log.warn("Account '{}' locked after {} failed login attempts (until {})",
                        username, attempts, u.getLockedUntil());
                }
                userRepository.save(u);
            });
        } catch (Exception ex) {
            log.warn("login-attempt bookkeeping (failure) skipped for '{}': {}", username, ex.toString());
        }
    }

    /** Reset the failure counter + unlock on a successful login, and stamp lastLoginAt. */
    @Transactional
    public void onSuccess(Long userId) {
        try {
            userRepository.findById(userId).ifPresent(u -> {
                u.setFailedLoginAttempts(0);
                u.setIsLocked(false);
                u.setLockedUntil(null);
                u.setLastLoginAt(LocalDateTime.now());
                userRepository.save(u);
            });
        } catch (Exception ex) {
            log.warn("login-attempt bookkeeping (success) skipped for userId {}: {}", userId, ex.toString());
        }
    }
}
