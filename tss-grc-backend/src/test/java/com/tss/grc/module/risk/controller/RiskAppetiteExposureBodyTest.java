package com.tss.grc.module.risk.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.service.RiskAppetiteService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * F2-447 — the exposure route's raw {@code Map} body.
 *
 * <p>⚠ The controller had <b>no test of any kind</b>, which is not the same as a path somebody
 * decided was safe (F2-248). The route raises a CRO/board appetite-breach escalation.
 *
 * <p>⚠ Every leg asserts the MESSAGE, not just the exception type. The claim is <i>a 400 that
 * NAMES the field</i>: a bare {@code NumberFormatException} was already a 400 (it is an
 * {@code IllegalArgumentException}, which {@code GlobalExceptionHandler} maps), and its message
 * named nothing — so a type-only assertion would pass over the defect this closes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F2-447 — an absent or non-numeric exposure is a 400 that names the field")
class RiskAppetiteExposureBodyTest {

    @Mock
    private RiskAppetiteService appetiteService;

    @InjectMocks
    private RiskAppetiteController controller;

    private static Map<String, Object> body(Object value) {
        Map<String, Object> m = new HashMap<>();
        if (value != null) {
            m.put("value", value);
        }
        return m;
    }

    @Test
    @DisplayName("an omitted value is refused by name — it was an NPE, so an opaque 500")
    void anOmittedValueIsRefusedByName() {
        assertThatThrownBy(() -> controller.updateExposure(1L, body(null)))
            .as("""
                pre-fix this was `new BigDecimal(body.get("value").toString())` in the ELSE of an
                instanceof, so an absent key raised a NullPointerException — which matches no
                handler but @ExceptionHandler(Exception.class) and answers an opaque 500.""")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("value")
            .hasMessageContaining("required");
        verify(appetiteService, never()).updateExposure(any(), any(), any());
    }

    @Test
    @DisplayName("a null body is refused by name too")
    void aNullBodyIsRefusedByName() {
        assertThatThrownBy(() -> controller.updateExposure(1L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("value is required");
        verify(appetiteService, never()).updateExposure(any(), any(), any());
    }

    @Test
    @DisplayName("⚠ a non-numeric value names the field — it was already a 400 naming nothing")
    void aNonNumericValueNamesTheField() {
        assertThatThrownBy(() -> controller.updateExposure(1L, body("not a number")))
            .as("""
                NumberFormatException is an IllegalArgumentException, so this was ALREADY a 400 —
                with a message like "Character n is neither a decimal digit number…" that named no
                field. Asserting only the type would pass over the half being fixed.""")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("value")
            .hasMessageContaining("must be a number");
        verify(appetiteService, never()).updateExposure(any(), any(), any());
    }

    @Test
    @DisplayName("a JSON number still reaches the service unchanged")
    void aJsonNumberStillWorks() {
        when(appetiteService.updateExposure(eq(1L), any(), any()))
            .thenReturn(new RiskAppetiteStatement());
        controller.updateExposure(1L, body(17.5));
        ArgumentCaptor<BigDecimal> c = ArgumentCaptor.forClass(BigDecimal.class);
        verify(appetiteService).updateExposure(eq(1L), c.capture(), any());
        assertThat(c.getValue()).isEqualByComparingTo("17.5");
    }

    @Test
    @DisplayName("a numeric STRING still reaches the service unchanged — the ELSE branch's job")
    void aNumericStringStillWorks() {
        when(appetiteService.updateExposure(eq(1L), any(), any()))
            .thenReturn(new RiskAppetiteStatement());
        controller.updateExposure(1L, body("17.5"));
        ArgumentCaptor<BigDecimal> c = ArgumentCaptor.forClass(BigDecimal.class);
        verify(appetiteService).updateExposure(eq(1L), c.capture(), any());
        assertThat(c.getValue())
            .as("the ELSE branch is the one that dereferenced null; it must still do its work")
            .isEqualByComparingTo("17.5");
    }

    @Test
    @DisplayName("notes stays optional — an omitted notes is null, not a refusal")
    void notesStaysOptional() {
        when(appetiteService.updateExposure(eq(1L), any(), any()))
            .thenReturn(new RiskAppetiteStatement());
        controller.updateExposure(1L, body(3));
        verify(appetiteService).updateExposure(eq(1L), any(), eq(null));
    }
}
