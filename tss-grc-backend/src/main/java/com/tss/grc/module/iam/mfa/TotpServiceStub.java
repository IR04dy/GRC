package com.tss.grc.module.iam.mfa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stub implementation of {@link TotpService} — every method throws
 * {@link UnsupportedOperationException} with a pointer to Open Issue #008.
 *
 * <p>This stub exists so the API surface compiles and so callers can autowire
 * to {@code TotpService} without the build breaking. The real implementation
 * — backed by {@code dev.samstevens.totp} — will replace this class in
 * Sprint 13 (MFA enrollment UI).
 *
 * <p>Importantly: this is a {@code @Service}-annotated bean, so any accidental
 * call from a production code path will fail loudly at runtime rather than
 * silently no-op. The exception message includes the issue number for triage.
 */
@Service
public class TotpServiceStub implements TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpServiceStub.class);

    private static final String STUB_MSG =
        "MFA enrollment not yet implemented — see issue #008 " +
        "(docs/operations/mfa-totp-selection.md). Sprint 13 backlog.";

    @Override
    public TotpEnrollment enrollUser(Long userId) {
        log.warn("TotpService.enrollUser called for user={} — stub, throwing", userId);
        throw new UnsupportedOperationException(STUB_MSG);
    }

    @Override
    public boolean verifyCode(Long userId, String code) {
        log.warn("TotpService.verifyCode called for user={} — stub, throwing", userId);
        throw new UnsupportedOperationException(STUB_MSG);
    }

    @Override
    public List<String> generateBackupCodes(Long userId) {
        log.warn("TotpService.generateBackupCodes called for user={} — stub, throwing", userId);
        throw new UnsupportedOperationException(STUB_MSG);
    }
}
