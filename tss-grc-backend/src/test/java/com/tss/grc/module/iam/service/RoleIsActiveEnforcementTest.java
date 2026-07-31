package com.tss.grc.module.iam.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Permission;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.governance.repository.AccessRequestRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewCampaignRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewItemRepository;
import com.tss.grc.module.iam.governance.repository.SodViolationRepository;
import com.tss.grc.module.iam.governance.service.AccessEvidencePackageService;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * F2-62 — {@code iam.roles.is_active} was a dead control.
 *
 * <p>Five code paths wrote it; two read it. Neither reader was an enforcement point:
 * {@code CustomUserDetailsService} minted {@code ROLE_<name>} plus every permission of every
 * assigned role without consulting it, so deactivating a role removed no access whatsoever —
 * while {@code AccessEvidencePackageService} printed "Status: INACTIVE" for that same role into
 * the pack handed to a SAMA/NCA inspector. <b>The artefact leaving the building disagreed with
 * the system, and the artefact was the one an auditor could check.</b>
 *
 * <p>These tests pin the two halves and, critically, <b>their agreement</b>:
 *
 * <ul>
 *   <li>a user holding only an inactive role receives no {@code ROLE_*} and no permission
 *       authorities — the enforcement half;</li>
 *   <li>for the same {@code Role} instances, the pack's ACTIVE/INACTIVE column is ACTIVE
 *       <i>exactly</i> when that role contributed authorities — the agreement. Asserted as a
 *       biconditional over real generated XLSX bytes, so it fails if either side drifts, which
 *       is the failure mode that let the original defect live: each half looked defensible
 *       alone.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleIsActiveEnforcementTest {

    private static final Long ORG_ID = 91L;

    // ── enforcement half ────────────────────────────────────────────────────
    @Mock private UserRepository userRepository;
    @InjectMocks private CustomUserDetailsService userDetailsService;

    // ── evidence-pack half ──────────────────────────────────────────────────
    @Mock private RoleRepository roleRepo;
    @Mock private AccessRequestRepository assignments;
    @Mock private SodViolationRepository violationRepo;
    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewItemRepository itemRepo;
    @Mock private OrganisationRepository orgRepo;
    @Mock private BrandThemeService brandThemeService;
    @InjectMocks private AccessEvidencePackageService evidenceService;

    /** ACTIVE role: name + one permission. */
    private static Role active(String name, String perm) {
        return role(name, perm, true);
    }

    /** INACTIVE role — the control under test. */
    private static Role inactive(String name, String perm) {
        return role(name, perm, false);
    }

    private static Role role(String name, String perm, boolean isActive) {
        Permission p = new Permission();
        p.setPermissionName(perm);
        p.setModule(perm.split("_")[0]);
        p.setAction(perm.substring(perm.indexOf('_') + 1));
        Role r = new Role();
        r.setRoleName(name);
        r.setDisplayNameEn(name + " (display)");
        r.setDisplayNameAr("عرض " + name);
        r.setOrganisationId(ORG_ID);
        r.setIsSystemRole(false);
        r.setIsActive(isActive);
        r.setPermissions(Set.of(p));
        return r;
    }

    private static User userWith(Role... roles) {
        User u = new User();
        u.setId(7L);
        u.setOrganisationId(ORG_ID);
        u.setUsername("f262.tester");
        u.setPasswordHash("$2b$10$x");
        u.setIsActive(true);
        u.setIsLocked(false);
        u.setRoles(new LinkedHashSet<>(List.of(roles)));
        return u;
    }

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(orgRepo.findById(ORG_ID)).thenReturn(Optional.empty());
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            "F2-62 Test Org", null, null, null, "#094242", "#C9A977", null));
        when(assignments.listAssignments(ORG_ID)).thenReturn(List.of());
        when(violationRepo.findByOrganisationIdAndIsDeletedFalseOrderByDetectedAtDesc(ORG_ID))
            .thenReturn(List.of());
        when(campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── 1. enforcement: an inactive role confers nothing ────────────────────

    @Test void userHoldingOnlyAnInactiveRole_getsNoRoleAndNoPermissionAuthorities() {
        when(userRepository.findWithRolesByUsername("f262.tester"))
            .thenReturn(Optional.of(userWith(inactive("DORMANT_ROLE", "RISK_READ"))));

        UserDetails details = userDetailsService.loadUserByUsername("f262.tester");

        assertThat(authorities(details))
            .as("an inactive role must contribute neither its ROLE_ marker nor its permissions")
            .isEmpty();
        // The account itself is untouched: user.isActive still governs login, so the user can
        // still authenticate — they simply arrive carrying nothing. Deactivating a role is not
        // a way to disable an account.
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test void inactiveRoleIsSubtracted_whileActiveRolesOnTheSameUserSurvive() {
        when(userRepository.findWithRolesByUsername("f262.tester")).thenReturn(Optional.of(userWith(
            active("LIVE_ROLE", "COMPLIANCE_READ"),
            inactive("DORMANT_ROLE", "RISK_READ"))));

        Set<String> auths = authorities(userDetailsService.loadUserByUsername("f262.tester"));

        assertThat(auths).containsExactlyInAnyOrder("ROLE_LIVE_ROLE", "COMPLIANCE_READ");
        assertThat(auths)
            .as("the dormant role's permission must not leak in via another role's presence")
            .doesNotContain("ROLE_DORMANT_ROLE", "RISK_READ");
    }

    @Test void deactivatingPlatformAdmin_removesTheBypassAuthority() {
        // ModulePermissionEvaluator resolves the platform-admin bypass purely from this
        // authority, so its absence is the whole of the bypass being gone.
        when(userRepository.findWithRolesByUsername("f262.tester"))
            .thenReturn(Optional.of(userWith(inactive("PLATFORM_ADMIN", "RISK_READ"))));

        assertThat(authorities(userDetailsService.loadUserByUsername("f262.tester")))
            .doesNotContain("ROLE_PLATFORM_ADMIN");
    }

    @Test void defaultConstructedRoleGrants_butAnExplicitNullFlagDoesNot() {
        // Two separate claims, and the first is the one that matters for safety: the field
        // initialiser is `true` and the column is NOT NULL DEFAULT 1, so an unset flag keeps
        // meaning ACTIVE — otherwise this fix would have silently revoked every role in the
        // platform, including the 58 live ones that never set it explicitly.
        assertThat(new Role().grantsAuthority())
            .as("a freshly constructed Role is active — no silent mass revocation")
            .isTrue();

        // An *explicit* NULL, reachable only on a transient entity, reads as INACTIVE. That is
        // deliberate: it is the evidence pack's long-standing predicate, and the two must not
        // diverge for any input, including ones the database cannot currently produce.
        Role nulled = active("NULLED_ROLE", "RISK_READ");
        nulled.setIsActive(null);
        when(userRepository.findWithRolesByUsername("f262.tester"))
            .thenReturn(Optional.of(userWith(nulled)));

        assertThat(authorities(userDetailsService.loadUserByUsername("f262.tester"))).isEmpty();
    }

    // ── 2. agreement: the pack's column matches the authorities granted ─────

    @Test void evidencePackStatusColumn_agreesWithTheAuthoritiesActuallyGranted() throws Exception {
        Role live = active("LIVE_ROLE", "COMPLIANCE_READ");
        Role dormant = inactive("DORMANT_ROLE", "RISK_READ");
        List<Role> catalogue = List.of(live, dormant);

        // Same Role instances drive both halves — that is the point of the test.
        when(roleRepo.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID)).thenReturn(catalogue);
        when(userRepository.findWithRolesByUsername("f262.tester"))
            .thenReturn(Optional.of(userWith(live, dormant)));

        Set<String> auths = authorities(userDetailsService.loadUserByUsername("f262.tester"));
        Map<String, String> status = roleStatusFromPack(evidenceService.generate("auditor.tester").bytes());

        assertThat(status).containsOnlyKeys("LIVE_ROLE", "DORMANT_ROLE");
        for (Role r : catalogue) {
            boolean packSaysActive = "ACTIVE".equals(status.get(r.getRoleName()));
            boolean actuallyGranted = auths.contains("ROLE_" + r.getRoleName());
            assertThat(packSaysActive)
                .as("pack says ACTIVE=%s for %s but the auth path granted=%s — the evidence "
                    + "package must not describe access the platform does not enforce",
                    packSaysActive, r.getRoleName(), actuallyGranted)
                .isEqualTo(actuallyGranted);
        }
        // And the substantive direction, spelled out so a future refactor cannot satisfy the
        // biconditional by breaking both halves at once.
        assertThat(status).containsEntry("LIVE_ROLE", "ACTIVE").containsEntry("DORMANT_ROLE", "INACTIVE");
        assertThat(auths).containsExactlyInAnyOrder("ROLE_LIVE_ROLE", "COMPLIANCE_READ");
    }

    @Test void permissionMatrixSheet_marksAnInactiveRolesGrantsAsInactive() throws Exception {
        // Sheet 1 saying INACTIVE is not enough: the Permission Matrix is the sheet read as
        // "who can do what", and it listed an inactive role's grants in the same shape as a
        // live role's, with nothing to distinguish them.
        when(roleRepo.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID))
            .thenReturn(List.of(active("LIVE_ROLE", "COMPLIANCE_READ"),
                                inactive("DORMANT_ROLE", "RISK_READ")));

        List<List<String>> rows = sheetRows(
            evidenceService.generate("auditor.tester").bytes(), "Permission_Matrix.xlsx");

        assertThat(rows.get(0)).containsExactly("Role", "Module", "Action", "Role Status");
        Map<String, String> byRole = new HashMap<>();
        rows.subList(1, rows.size()).forEach(r -> byRole.put(r.get(0), r.get(3)));
        assertThat(byRole)
            .containsEntry("LIVE_ROLE", "ACTIVE")
            .containsEntry("DORMANT_ROLE", "INACTIVE");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Set<String> authorities(UserDetails d) {
        return d.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /** role name → Status cell, read out of the generated Role_Catalogue.xlsx. */
    private static Map<String, String> roleStatusFromPack(byte[] zipBytes) throws Exception {
        List<List<String>> rows = sheetRows(zipBytes, "Role_Catalogue.xlsx");
        List<String> header = rows.get(0);
        int roleCol = header.indexOf("Role");
        int statusCol = header.indexOf("Status");
        assertThat(roleCol).as("Role column present").isNotNegative();
        assertThat(statusCol).as("Status column present").isNotNegative();
        Map<String, String> out = new HashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            out.put(row.get(roleCol), row.get(statusCol));
        }
        return out;
    }

    private static List<List<String>> sheetRows(byte[] zipBytes, String entryName) throws Exception {
        byte[] xlsx = entry(zipBytes, entryName);
        assertThat(xlsx).as("%s present in the pack", entryName).isNotNull();
        List<List<String>> out = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    cells.add(row.getCell(c) == null ? "" : row.getCell(c).toString());
                }
                out.add(cells);
            }
        }
        return out;
    }

    private static byte[] entry(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(name)) return zin.readAllBytes();
                zin.closeEntry();
            }
        }
        return null;
    }
}
