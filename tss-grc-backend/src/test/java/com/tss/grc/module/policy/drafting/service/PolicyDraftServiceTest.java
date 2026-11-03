package com.tss.grc.module.policy.drafting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.integrations.aigateway.service.AiGatewayService;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.AcceptPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.CreatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.PolicyDraftResponse;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.RejectPolicyDraftRequest;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.UpdatePolicyDraftRequest;
import com.tss.grc.module.policy.drafting.entity.PolicyAiDraft;
import com.tss.grc.module.policy.drafting.repository.PolicyAiDraftRepository;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentFrameworkMappingRepository;
import com.tss.grc.module.policy.service.PolicyManagementService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyDraftServiceTest {

    private PolicyAiDraftRepository draftRepo;
    private PolicyManagementService policyManagementService;
    private PolicyDocumentFrameworkMappingRepository frameworkMappingRepo;
    private FrameworkRepository frameworkRepo;
    private ControlRepository controlRepo;
    private OrganisationRepository orgRepo;
    private AiGatewayService llmGateway;
    private PolicyDraftAsyncWorker asyncWorker;
    private AuditLogService audit;
    private NotificationService notifications;
    private ObjectMapper objectMapper;
    private PlatformTransactionManager txManager;
    private PolicyDraftService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(1L);
        GrcUserDetails principal = new GrcUserDetails(
            77L, 1L, "compliance", "pw", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_COMPLIANCE_OFFICER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        draftRepo = mock(PolicyAiDraftRepository.class);
        policyManagementService = mock(PolicyManagementService.class);
        frameworkMappingRepo = mock(PolicyDocumentFrameworkMappingRepository.class);
        frameworkRepo = mock(FrameworkRepository.class);
        controlRepo = mock(ControlRepository.class);
        orgRepo = mock(OrganisationRepository.class);
        llmGateway = mock(AiGatewayService.class);
        audit = mock(AuditLogService.class);
        notifications = mock(NotificationService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        asyncWorker = mock(PolicyDraftAsyncWorker.class);
        service = new PolicyDraftService(
            draftRepo,
            policyManagementService,
            frameworkMappingRepo,
            frameworkRepo,
            controlRepo,
            llmGateway,
            asyncWorker,
            audit,
            objectMapper,
            txManager
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void requestDraftPersistsQueuedRowAndTriggersAsyncWorker() {
        when(draftRepo.save(any(PolicyAiDraft.class))).thenAnswer(inv -> {
            PolicyAiDraft d = inv.getArgument(0);
            d.setId(501L);
            return d;
        });

        CreatePolicyDraftRequest req = new CreatePolicyDraftRequest(
            "Data Retention Policy",
            "DATA_PRIVACY",
            "FORMAL_REGULATORY",
            List.of(1L),
            List.of(11L, 12L),
            "All business units",
            "Include PDPL Art. 18 disposal clauses"
        );

        PolicyDraftResponse resp = service.requestDraft(req);

        assertThat(resp.id()).isEqualTo(501L);
        assertThat(resp.status()).isEqualTo("REQUESTED");
        assertThat(resp.requestedBy()).isEqualTo("compliance");
        assertThat(resp.externalId()).startsWith("AID-");
        verify(asyncWorker).generateAsync(501L, 1L, "compliance", 77L);
    }

    @Test
    void asyncWorkerRepairsInvalidFirstJsonAndNotifiesRequester() throws Exception {
        PolicyDraftAsyncWorker realWorker = new PolicyDraftAsyncWorker(
            draftRepo,
            orgRepo,
            frameworkRepo,
            controlRepo,
            new PolicyDraftPromptBuilder(),
            new PolicyDraftOutputValidator(objectMapper),
            llmGateway,
            audit,
            notifications,
            objectMapper,
            txManager
        );

        CreatePolicyDraftRequest req = new CreatePolicyDraftRequest(
            "Access Control Policy", "ACCESS_CONTROL", "FORMAL_REGULATORY",
            List.of(), List.of(11L), "All systems", null
        );
        PolicyAiDraft stored = new PolicyAiDraft();
        stored.setId(600L);
        stored.setOrganisationId(1L);
        stored.setExternalId("AID-2026-TEST");
        stored.setStatus("REQUESTED");
        stored.setPolicyType("Access Control Policy");
        stored.setCategory("ACCESS_CONTROL");
        stored.setTone("FORMAL_REGULATORY");
        stored.setRequestJson(objectMapper.writeValueAsString(req));
        stored.setPromptTemplateId("policy_draft_bilingual");
        stored.setPromptTemplateVersion("v1");
        stored.setRequestedBy("compliance");
        stored.setRequestedByUserId(77L);

        Control ctl = new Control();
        ctl.setId(11L);
        ctl.setOrganisationId(1L);
        ctl.setCode("ECC-2-2-1");
        ctl.setFramework("NCA-ECC");
        ctl.setNameEn("Identity & Access Management");

        Organisation org = new Organisation();
        org.setNameEn("TSS Financial");

        when(draftRepo.findByIdAndOrganisationId(600L, 1L)).thenReturn(Optional.of(stored));
        when(draftRepo.save(any(PolicyAiDraft.class))).thenAnswer(i -> i.getArgument(0));
        when(orgRepo.findById(1L)).thenReturn(Optional.of(org));
        when(controlRepo.findByOrganisationIdAndIdIn(eq(1L), any())).thenReturn(List.of(ctl));

        when(llmGateway.complete(eq(1L), any()))
            .thenReturn(new LlmResponse("not-valid-json", "GEMINI", "gemini-2.5-flash", 100, 20, 50L, "STOP"))
            .thenReturn(new LlmResponse("""
                {
                  "titleEn": "Access Control Policy",
                  "titleAr": "سياسة التحكم في الوصول",
                  "summaryEn": "IAM summary",
                  "summaryAr": "ملخص إدارة الهوية",
                  "contentMdEn": "## Purpose\\nEnforce least privilege.",
                  "contentMdAr": "## الغرض\\nتطبيق مبدأ الحد الأدنى من الصلاحيات.",
                  "citedControlIds": [11, 8888],
                  "suggestedReviewCadence": "ANNUAL",
                  "assumptions": []
                }
                """, "GEMINI", "gemini-2.5-flash", 120, 200, 90L, "STOP"));

        realWorker.generateAsync(600L, 1L, "compliance", 77L);

        verify(llmGateway, times(2)).complete(eq(1L), any());
        assertThat(stored.getStatus()).isEqualTo("GENERATED");
        assertThat(stored.getInputTokens()).isEqualTo(220);
        assertThat(stored.getOutputTokens()).isEqualTo(220);
        assertThat(stored.getHallucinatedCitations()).isEqualTo("[8888]");
        verify(notifications).notifyUser(
            eq(1L), eq(77L), any(), any(), any(), eq("POLICY_AI_DRAFT_READY"), eq("PolicyAiDraft"), eq(600L)
        );
    }

    @Test
    void editAcceptAndRejectComposeSingleBilingualDocumentAndLinkControls() throws Exception {
        CreatePolicyDraftRequest origReq = new CreatePolicyDraftRequest(
            "Cryptography Policy", "CYBERSECURITY", "FORMAL_REGULATORY",
            List.of(3L), List.of(21L), "Enterprise-wide", null
        );
        PolicyAiDraft draft = new PolicyAiDraft();
        draft.setId(700L);
        draft.setOrganisationId(1L);
        draft.setExternalId("AID-2026-ACC");
        draft.setStatus("GENERATED");
        draft.setPolicyType("Cryptography Policy");
        draft.setCategory("CYBERSECURITY");
        draft.setTone("FORMAL_REGULATORY");
        draft.setRequestJson(objectMapper.writeValueAsString(origReq));
        draft.setOutputJson("""
            {
              "titleEn": "Cryptography Policy",
              "titleAr": "سياسة التشفير",
              "summaryEn": "Encryption rules",
              "summaryAr": "ضوابط التشفير",
              "contentMdEn": "## 1. Scope\\nTLS 1.3 and AES-256.",
              "contentMdAr": "## ١. النطاق\\nاستخدام معايير التشفير المعتمدة.",
              "citedControlIds": [21],
              "suggestedReviewCadence": "ANNUAL",
              "assumptions": []
            }
            """);
        draft.setPromptTemplateId("policy_draft_bilingual");
        draft.setPromptTemplateVersion("v1");
        draft.setRequestedBy("compliance");

        Control ctl = new Control();
        ctl.setId(21L);
        ctl.setOrganisationId(1L);

        Framework fw = new Framework();
        fw.setId(3L);
        fw.setCode("NCA-ECC");
        fw.setNameEn("NCA Essential Cybersecurity Controls");
        fw.setIsActive(true);

        when(draftRepo.findByIdAndOrganisationId(700L, 1L)).thenReturn(Optional.of(draft));
        when(draftRepo.save(any(PolicyAiDraft.class))).thenAnswer(i -> i.getArgument(0));
        when(controlRepo.findByOrganisationIdAndIdIn(eq(1L), any())).thenReturn(List.of(ctl));
        when(frameworkRepo.findAllById(any())).thenReturn(List.of(fw));
        when(policyManagementService.create(any(PolicyDocument.class), eq("compliance"))).thenAnswer(i -> {
            PolicyDocument doc = i.getArgument(0);
            doc.setId(901L);
            doc.setStatus("DRAFT");
            return doc;
        });

        UpdatePolicyDraftRequest editReq = new UpdatePolicyDraftRequest(
            "Enterprise Cryptography Policy",
            "السياسة المؤسسية للتشفير",
            "Updated EN summary",
            "ملخص محدث",
            "## 1. Purpose\nProtect data in transit and at rest.",
            "## ١. الغرض\nحماية البيانات أثناء النقل والتخزين.",
            List.of(21L, 999L),
            "ANNUAL",
            List.of(),
            "Reviewed by compliance officer"
        );
        PolicyDraftResponse edited = service.updateDraft(700L, editReq);
        assertThat(edited.edited()).isNotNull();
        assertThat(edited.edited().citedControlIds()).containsExactly(21L);

        PolicyDraftResponse accepted = service.acceptDraft(700L, new AcceptPolicyDraftRequest(
            "POL-CRYP-001", "1.0", "Compliance Officer", "compliance@tss.sa", "ANNUAL", null, "Approved as draft"
        ));
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.acceptedPolicyId()).isEqualTo(901L);

        ArgumentCaptor<PolicyDocument> docCap = ArgumentCaptor.forClass(PolicyDocument.class);
        verify(policyManagementService).create(docCap.capture(), eq("compliance"));
        PolicyDocument createdDoc = docCap.getValue();
        assertThat(createdDoc.getTitleEn()).isEqualTo("Enterprise Cryptography Policy");
        assertThat(createdDoc.getTitleAr()).isEqualTo("السياسة المؤسسية للتشفير");
        assertThat(createdDoc.getContentMd())
            .contains("# Enterprise Cryptography Policy")
            .contains("---")
            .contains("# السياسة المؤسسية للتشفير")
            .contains("حماية البيانات أثناء النقل والتخزين.");

        verify(policyManagementService).setControlLinks(901L, List.of(21L));
        verify(frameworkMappingRepo).saveAll(any());

        // Reject path on a separate GENERATED draft
        PolicyAiDraft rejectable = new PolicyAiDraft();
        rejectable.setId(702L);
        rejectable.setOrganisationId(1L);
        rejectable.setExternalId("AID-2026-REJ");
        rejectable.setStatus("GENERATED");
        rejectable.setPolicyType("Test");
        rejectable.setCategory("GOVERNANCE");
        rejectable.setTone("FORMAL_REGULATORY");
        rejectable.setRequestJson("{}");
        rejectable.setPromptTemplateId("policy_draft_bilingual");
        rejectable.setPromptTemplateVersion("v1");
        rejectable.setRequestedBy("compliance");
        when(draftRepo.findByIdAndOrganisationId(702L, 1L)).thenReturn(Optional.of(rejectable));

        PolicyDraftResponse rejected = service.rejectDraft(702L, new RejectPolicyDraftRequest("Scope superseded"));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.reviewerNote()).isEqualTo("Scope superseded");

        // Delete path
        PolicyAiDraft deletable = new PolicyAiDraft();
        deletable.setId(703L);
        deletable.setOrganisationId(1L);
        deletable.setExternalId("AID-2026-DEL");
        deletable.setIsDeleted(false);
        when(draftRepo.findByIdAndOrganisationId(703L, 1L)).thenReturn(Optional.of(deletable));

        service.deleteDraft(703L);
        assertThat(deletable.getIsDeleted()).isTrue();
        assertThat(deletable.getDeletedBy()).isEqualTo(77L);
        assertThat(deletable.getDeletedAt()).isNotNull();
        verify(draftRepo).save(deletable);
        verify(audit).log(eq("POLICY_AI_DRAFT_DELETED"), eq("PolicyAiDraft"), eq(703L), any());
    }
}
