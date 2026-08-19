package com.tss.grc.module.tprm.service;

import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behaviour of {@link TprmVendorReferenceResolver} (F2-247).
 *
 * <p>The structural gate next door proves the assessment service asks this class; these legs prove
 * the answers. F2-237's rule is why they are separate: a mocked collaborator returns null and every
 * refusal leg then goes green asserting nothing, so the thing being tested has to be the real one.
 */
class TprmVendorReferenceResolverTest {

    private static final long ORG = 1L;
    private static final long OTHER_ORG = 2L;

    private VendorRepository vendors;
    private TprmVendorReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        vendors = Mockito.mock(VendorRepository.class);
        resolver = new TprmVendorReferenceResolver(vendors);
    }

    private Vendor vendor(long id, long org, String name) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setOrganisationId(org);
        v.setVendorName(name);
        return v;
    }

    // ------------------------------------------------------------- the linked case ------------

    @Test
    @DisplayName("a resolved id derives the label from the row, discarding whatever the caller sent")
    void aResolvedIdDerivesTheLabel() {
        Mockito.when(vendors.findById(7L))
                .thenReturn(Optional.of(vendor(7L, ORG, "Solutions by STC")));

        var ref = resolver.resolve(7L, "A LIE about who this vendor is", ORG);

        assertThat(ref.id()).isEqualTo(7L);
        assertThat(ref.name())
                .as("the label a human reads and the id a coverage query joins on must describe the "
                  + "same row — probed live pre-fix, a lying label beside a real id was stored")
                .isEqualTo("Solutions by STC");
    }

    @Test
    @DisplayName("another tenant's vendor id is refused, and says nothing about it")
    void anotherTenantsVendorIsRefused() {
        Mockito.when(vendors.findById(9L))
                .thenReturn(Optional.of(vendor(9L, OTHER_ORG, "Someone else's supplier")));

        assertThatThrownBy(() -> resolver.resolve(9L, "whatever", ORG))
                .isInstanceOf(EntityNotFoundException.class)
                // One message for "no such vendor" and "another tenant's vendor" deliberately:
                // distinguishing them lets a caller enumerate foreign ids from the error text
                // (F2-244).
                .hasMessage("Vendor not found: 9")
                .hasMessageNotContaining("Someone else's supplier");
    }

    @Test
    @DisplayName("a vendor id that exists nowhere is refused identically")
    void anUnknownVendorIsRefused() {
        Mockito.when(vendors.findById(999_999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(999_999L, "whatever", ORG))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Vendor not found: 999999");
    }

    @Test
    @DisplayName("a null tenant can never satisfy the filter, however real the vendor is")
    void aNullTenantResolvesNothing() {
        Mockito.when(vendors.findById(7L)).thenReturn(Optional.of(vendor(7L, ORG, "Aramex")));

        // This is the failure mode F2-246 shipped: the resolver ran before the org was stamped, so
        // every LINKED create answered 404 and both refusal legs passed on the broken code. Asserted
        // here so the behaviour is a decision rather than an accident of statement order.
        assertThatThrownBy(() -> resolver.resolve(7L, "Aramex", null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ----------------------------------------------------------- the unlinked case ------------

    @Test
    @DisplayName("no id keeps the caller's trimmed label, because the register allows that")
    void noIdKeepsTheSuppliedLabel() {
        var ref = resolver.resolve(null, "  Sentry IO  ", ORG);

        assertThat(ref.id()).isNull();
        assertThat(ref.name()).isEqualTo("Sentry IO");
        // 8 of the 16 live rows in dev and prod were made this way, so refusing would break working
        // requests — F2-182's mechanical test, and an assessment of an unregistered supplier is a
        // real thing to want.
        Mockito.verify(vendors, Mockito.never()).findById(Mockito.anyLong());
    }

    @Test
    @DisplayName("neither an id nor a label names nobody, and is refused")
    void neitherHalfIsRefused() {
        assertThatThrownBy(() -> resolver.resolve(null, "   ", ORG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A vendor must be identified")
                .hasMessageContaining("names nobody");
    }

    // ----------------------------------------------------------------- relabel ----------------

    @Test
    @DisplayName("an update may echo a linked row's label but not change it")
    void relabelAcceptsAnEchoAndRefusesAChange() {
        assertThat(resolver.relabel(7L, "Aramex", "Aramex"))
                .as("a form that round-trips the whole row must keep working (F2-231/F2-234)")
                .isEqualTo("Aramex");
        assertThat(resolver.relabel(7L, "Aramex", null))
                .as("an omitted label leaves the derived value alone rather than blanking a NOT "
                  + "NULL column — a reset is worse than a null (F2-213)")
                .isEqualTo("Aramex");
        assertThatThrownBy(() -> resolver.relabel(7L, "Aramex", "Something Else"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("derived from the vendor register");
    }

    @Test
    @DisplayName("an unlinked row's label stays editable, and may not be blanked")
    void relabelKeepsAnUnlinkedLabelEditable() {
        assertThat(resolver.relabel(null, "Sentry IO", "Sentry IO Inc."))
                .as("with no id the label is the only identification the row has")
                .isEqualTo("Sentry IO Inc.");
        assertThat(resolver.relabel(null, "Sentry IO", "   "))
                .as("and it may not be blanked away")
                .isEqualTo("Sentry IO");
    }
}
