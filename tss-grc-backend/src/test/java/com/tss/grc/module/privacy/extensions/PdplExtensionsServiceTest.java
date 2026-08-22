package com.tss.grc.module.privacy.extensions;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.*;
import com.tss.grc.module.privacy.extensions.entity.*;
import com.tss.grc.module.privacy.extensions.repository.*;
import com.tss.grc.module.privacy.extensions.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Focused state-machine + tenant-scoping tests for the three PDPL
 * extension services (cross-border transfer, disposal certificate,
 * privacy notice).
 */
@ExtendWith(MockitoExtension.class)
class PdplExtensionsServiceTest {

    private static final Long ORG_ID = 1L;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Cross-border transfer ─────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CrossBorderTransferTests {

        @Mock CrossBorderTransferRepository repo;
        @Mock AuditLogService audit;
        @Mock ApplicationEventPublisher publisher;
        @InjectMocks CrossBorderTransferService service;

        @BeforeEach void cbtSetup() { TenantContext.setCurrentTenant(ORG_ID); }

        private CrossBorderTransfer cbt(String status) {
            CrossBorderTransfer t = new CrossBorderTransfer();
            t.setId(10L);
            t.setOrganisationId(ORG_ID);
            t.setRopaCode("ROPA-2026-007");
            t.setDestinationCountry("USA");
            t.setDestinationOrgName("AWS Inc.");
            t.setDataCategories("CONTACT,FINANCIAL");
            t.setTransferMechanism("SCC");
            t.setJustificationEn("Hosting analytics workload");
            t.setStatus(status);
            return t;
        }

        @Test void create_starts_REQUESTED() {
            when(repo.save(any(CrossBorderTransfer.class))).thenAnswer(inv -> {
                CrossBorderTransfer t = inv.getArgument(0);
                t.setId(10L);
                return t;
            });
            CrossBorderCreate body = new CrossBorderCreate(
                    "ROPA-2026-007", "Employee analytics",
                    "USA", "AWS Inc.", "PROCESSOR",
                    "CONTACT,FINANCIAL", 5_000L,
                    "SCC", "Encryption + access controls", null,
                    "Hosting analytics workload", null,
                    LocalDateTime.now(), LocalDateTime.now().plusYears(1));

            CrossBorderTransfer saved = service.create(body, 1L);
            assertThat(saved.getStatus()).isEqualTo("REQUESTED");
            assertThat(saved.getExternalId()).startsWith("CBT-");
            assertThat(saved.getDestinationCountry()).isEqualTo("USA");
        }

        @Test void assess_REQUESTED_to_ASSESSED_stamps_residency_check() {
            when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(cbt("REQUESTED")));
            when(repo.save(any(CrossBorderTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

            CrossBorderTransfer assessed = service.assess(10L, new AssessRequest(null, "DPO", "PASS"));
            assertThat(assessed.getStatus()).isEqualTo("ASSESSED");
            assertThat(assessed.getResidencyCheckOutcome()).isEqualTo("PASS");
            assertThat(assessed.getResidencyCheckAt()).isNotNull();
        }

        @Test void approve_requires_ASSESSED() {
            when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(cbt("REQUESTED")));
            assertThatThrownBy(() -> service.approve(10L, 1L, "admin"))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test void approve_ASSESSED_to_APPROVED() {
            when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(cbt("ASSESSED")));
            when(repo.save(any(CrossBorderTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

            CrossBorderTransfer approved = service.approve(10L, 1L, "admin");
            assertThat(approved.getStatus()).isEqualTo("APPROVED");
            assertThat(approved.getApproverName()).isEqualTo("admin");
            assertThat(approved.getApprovedAt()).isNotNull();
        }

        @Test void reject_allowed_from_REQUESTED_and_ASSESSED_only() {
            when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(cbt("APPROVED")));
            assertThatThrownBy(() -> service.reject(10L, new RejectRequest("changed mind")))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test void expire_idempotent_when_not_APPROVED() {
            CrossBorderTransfer rejected = cbt("REJECTED");
            when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(rejected));
            CrossBorderTransfer result = service.expire(10L);
            assertThat(result.getStatus()).isEqualTo("REJECTED"); // unchanged
        }
    }

    // ─── Disposal certificate ──────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class DisposalCertificateTests {

        @Mock DisposalCertificateRepository repo;
        @InjectMocks DisposalCertificateService service;

        @BeforeEach void dcSetup() { TenantContext.setCurrentTenant(ORG_ID); }

        private DisposalCertificate dc(String status) {
            DisposalCertificate d = new DisposalCertificate();
            d.setId(20L);
            d.setOrganisationId(ORG_ID);
            d.setRelatedEntityType("ropa");
            d.setRelatedEntityId("ROPA-2026-001");
            d.setScopeDescriptionEn("Retired customer accounts inactive > 7 years");
            d.setDisposalMethod("ANONYMIZE");
            d.setStatus(status);
            return d;
        }

        @Test void create_starts_SCHEDULED() {
            when(repo.save(any(DisposalCertificate.class))).thenAnswer(inv -> {
                DisposalCertificate d = inv.getArgument(0);
                d.setId(20L);
                return d;
            });
            DisposalCreate body = new DisposalCreate(
                    "ropa", "ROPA-2026-001", "Customer accounts",
                    "RET-7Y", "Inactive > 7 years", null, 1500L,
                    // F2-272 (V388) — methodDetailsAr. Deliberately non-null in this fixture: a
                    // null here would make the assertion below pass whether or not the writer
                    // copies the Arabic field, which is F2-179's rule that a fixture satisfying a
                    // condition twice cannot tell you which half is load-bearing.
                    "ANONYMIZE", "Hash + salt PII fields", "تجزئة وتمليح حقول البيانات الشخصية",
                    LocalDateTime.now(),
                    "DPO", "Sarah");
            DisposalCertificate saved = service.create(body, 1L);
            assertThat(saved.getStatus()).isEqualTo("SCHEDULED");
            assertThat(saved.getExternalId()).startsWith("DC-");
        }

        @Test void full_happy_path_SCHEDULED_to_VERIFIED() {
            when(repo.save(any(DisposalCertificate.class))).thenAnswer(inv -> inv.getArgument(0));
            DisposalCertificate stub = dc("SCHEDULED");
            when(repo.findByIdAndOrganisationId(20L, ORG_ID)).thenReturn(Optional.of(stub));

            DisposalCertificate started = service.start(20L, 5L);
            assertThat(started.getStatus()).isEqualTo("IN_PROGRESS");

            DisposalCertificate completed = service.complete(20L,
                    new DisposalCompleteRequest( "EV-2026-DC-001"), 5L);
            assertThat(completed.getStatus()).isEqualTo("COMPLETED");
            assertThat(completed.getEvidenceRef()).isEqualTo("EV-2026-DC-001");

            DisposalCertificate verified = service.verify(20L,
                    new DisposalVerifyRequest( 99L), 7L);
            assertThat(verified.getStatus()).isEqualTo("VERIFIED");
            assertThat(verified.getSignatureTxId()).isEqualTo(99L);
            assertThat(verified.getVerifiedAt()).isNotNull();
        }

        @Test void cancel_refuses_from_VERIFIED() {
            when(repo.findByIdAndOrganisationId(20L, ORG_ID)).thenReturn(Optional.of(dc("VERIFIED")));
            assertThatThrownBy(() -> service.cancel(20L))
                    .isInstanceOf(IllegalTransitionException.class);
        }
    }

    // ─── Privacy notice ────────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class PrivacyNoticeTests {

        @Mock PrivacyNoticeRepository repo;
        @InjectMocks PrivacyNoticeService service;

        @BeforeEach void pnSetup() { TenantContext.setCurrentTenant(ORG_ID); }

        private PrivacyNotice pn(String code, int version, String status) {
            PrivacyNotice n = new PrivacyNotice();
            n.setId(30L);
            n.setOrganisationId(ORG_ID);
            n.setNoticeCode(code);
            n.setVersion(version);
            n.setTitleEn("Employee notice");
            n.setTitleAr("إشعار الموظفين");
            n.setPurposeEn("HR processing");
            n.setBodyEn("Full notice body…");
            n.setStatus(status);
            return n;
        }

        @Test void create_uses_next_version_when_code_already_exists() {
            when(repo.findByOrganisationIdAndNoticeCodeOrderByVersionDesc(ORG_ID, "PN-EMP"))
                    .thenReturn(List.of(pn("PN-EMP", 2, "PUBLISHED")));
            when(repo.save(any(PrivacyNotice.class))).thenAnswer(inv -> inv.getArgument(0));

            NoticeCreate body = new NoticeCreate(
                    "PN-EMP", "Employee notice v3", "إشعار الموظفين ٣",
                    "HR + payroll", null, "PAYROLL,CONTACT", "Payroll dept",
                    "While employed + 7y", null, "dpo@tss.sa", "CONTRACT",
                    "Full body v3…", null, null, null, null);

            PrivacyNotice saved = service.create(body, 1L);
            assertThat(saved.getVersion()).isEqualTo(3);
            assertThat(saved.getStatus()).isEqualTo("DRAFT");
        }

        @Test void publish_DRAFT_to_PUBLISHED_stamps_effective_from() {
            when(repo.findByIdAndOrganisationId(30L, ORG_ID)).thenReturn(Optional.of(pn("PN-EMP", 1, "DRAFT")));
            when(repo.save(any(PrivacyNotice.class))).thenAnswer(inv -> inv.getArgument(0));

            PrivacyNotice published = service.publish(30L, 1L);
            assertThat(published.getStatus()).isEqualTo("PUBLISHED");
            assertThat(published.getPublishedAt()).isNotNull();
            assertThat(published.getEffectiveFrom()).isNotNull();
        }

        @Test void update_refuses_after_publish() {
            when(repo.findByIdAndOrganisationId(30L, ORG_ID)).thenReturn(Optional.of(pn("PN-EMP", 1, "PUBLISHED")));
            NoticeUpdate body = new NoticeUpdate(
                    null, null, "new purpose", null, null, null, null, null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.update(30L, body))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test void supersede_copies_content_and_links() {
            PrivacyNotice prev = pn("PN-EMP", 2, "PUBLISHED");
            when(repo.findByIdAndOrganisationId(30L, ORG_ID)).thenReturn(Optional.of(prev));
            when(repo.save(any(PrivacyNotice.class))).thenAnswer(inv -> inv.getArgument(0));

            PrivacyNotice next = service.supersede(30L, 1L);
            assertThat(next.getVersion()).isEqualTo(3);
            assertThat(next.getStatus()).isEqualTo("DRAFT");
            assertThat(next.getSupersedesId()).isEqualTo(30L);
            assertThat(next.getTitleEn()).isEqualTo(prev.getTitleEn());
            assertThat(next.getBodyEn()).isEqualTo(prev.getBodyEn());
        }
    }
}
