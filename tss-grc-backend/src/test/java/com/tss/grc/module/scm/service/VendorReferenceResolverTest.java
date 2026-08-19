package com.tss.grc.module.scm.service;

import com.tss.grc.module.scm.entity.Vendor;
import com.tss.grc.module.scm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * F2-246 — the behaviour of the one place that resolves an SCM vendor reference.
 *
 * <p>⚠ The structural gate ({@link VendorReferenceContractTest}) proves the four registers
 * <em>call</em> this. It cannot prove that calling it does anything — a reachability scan
 * measures whether a symbol is called, never whether the call has an effect (F2-202). That is
 * what these legs are for.
 */
@ExtendWith(MockitoExtension.class)
class VendorReferenceResolverTest {

    private static final Long OURS = 1L;
    private static final Long THEIRS = 2L;

    @Mock private VendorRepository vendors;
    @InjectMocks private VendorReferenceResolver resolver;

    private static Vendor vendor(Long id, Long org, String nameEn) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setOrganisationId(org);
        v.setNameEn(nameEn);
        return v;
    }

    // ── the derivation ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a resolved id derives the label, and the caller's label is discarded")
    void aResolvedIdDerivesTheLabel() {
        when(vendors.findById(17L)).thenReturn(Optional.of(vendor(17L, OURS, "STC Cloud Services")));

        var ref = resolver.resolve(17L, "Anything The Caller Fancied", OURS);

        assertThat(ref.id()).isEqualTo(17L);
        assertThat(ref.name())
                .as("only the vendor register knows what a vendor is called. Storing the "
                  + "caller's claim beside a resolved id is what let 11 of 11 rows in the risk "
                  + "register describe the wrong parent (F2-245), and it is reachable here too.")
                .isEqualTo("STC Cloud Services");
    }

    @Test
    @DisplayName("another tenant's vendor is refused, and so is one that does not exist")
    void aForeignOrAbsentVendorIsRefused() {
        when(vendors.findById(87L)).thenReturn(Optional.of(vendor(87L, THEIRS, "Someone Else's Vendor")));
        assertThatThrownBy(() -> resolver.resolve(87L, "whatever", OURS))
                .isInstanceOf(EntityNotFoundException.class)
                // One message for both cases, on purpose: distinguishing them lets a caller
                // enumerate foreign vendor ids from the error text (F2-244).
                .hasMessageContaining("Vendor not found");

        when(vendors.findById(999999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve(999999L, "whatever", OURS))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Vendor not found");
    }

    // ── the unlinked case, which must keep working ───────────────────────────────────────

    @Test
    @DisplayName("with no id the caller's label is kept, because it is the only identifier")
    void withNoIdTheLabelIsKept() {
        // 32 of 53 live rows were created this way and nothing refuses it today, so refusing
        // would break requests that work (F2-182). The row is disclosed as unlinked instead.
        var ref = resolver.resolve(null, "  Unregistered Regional Supplier  ", OURS);
        assertThat(ref.id()).isNull();
        assertThat(ref.name()).isEqualTo("Unregistered Regional Supplier");
    }

    @Test
    @DisplayName("neither an id nor a name names nobody, and is refused by name")
    void neitherHalfIsRefused() {
        assertThatThrownBy(() -> resolver.resolve(null, "   ", OURS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vendorId")
                .hasMessageContaining("vendorName");
        assertThatThrownBy(() -> resolver.resolve(null, null, OURS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── the update rule ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a linked row accepts an echo of its derived label and refuses a change")
    void relabelAcceptsAnEchoAndRefusesAChange() {
        // Accepting the echo is what keeps a form that round-trips the whole row working
        // (F2-231/F2-234); refusing the change is what stops an update re-opening the defect
        // the create just closed.
        assertThat(resolver.relabel(17L, "STC Cloud Services", "STC Cloud Services"))
                .isEqualTo("STC Cloud Services");
        assertThat(resolver.relabel(17L, "STC Cloud Services", "  STC Cloud Services  "))
                .as("an echo that differs only by whitespace is still an echo")
                .isEqualTo("STC Cloud Services");

        assertThatThrownBy(() -> resolver.relabel(17L, "STC Cloud Services", "Something Else"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("17");
    }

    @Test
    @DisplayName("an unlinked row's label stays editable, and cannot be blanked")
    void relabelKeepsAnUnlinkedRowEditable() {
        assertThat(resolver.relabel(null, "Old Supplier Name", "Corrected Supplier Name"))
                .isEqualTo("Corrected Supplier Name");
        assertThatThrownBy(() -> resolver.relabel(null, "Old Supplier Name", "   "))
                .as("with no id the label is the only identifier, so blanking it would leave "
                  + "a row naming nobody")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireOwn is the shared tenant check, and it is not merely an existence test")
    void requireOwnIsTenantScoped() {
        when(vendors.findById(17L)).thenReturn(Optional.of(vendor(17L, OURS, "Ours")));
        assertThat(resolver.requireOwn(17L, OURS).getId()).isEqualTo(17L);

        when(vendors.findById(87L)).thenReturn(Optional.of(vendor(87L, THEIRS, "Theirs")));
        assertThatThrownBy(() -> resolver.requireOwn(87L, THEIRS.equals(OURS) ? THEIRS : OURS))
                .as("mutation proved a resolver reduced to mere existence hides a missing "
                  + "tenant filter, so the tenant leg is asserted directly")
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> resolver.requireOwn(17L, null))
                .as("a null tenant must never satisfy the filter")
                .isInstanceOf(EntityNotFoundException.class);
    }
}
