package com.tss.grc.module.attachment.controller;

import com.tss.grc.module.attachment.service.AttachmentAccessPolicy;
import com.tss.grc.module.attachment.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * F2-388 — {@code AttachmentController.download} reflects the uploader's original filename
 * ({@code p.fileName()}, stored verbatim by {@code LocalFileStorageSupport.validate}, which strips
 * path components but NOT quotes/control chars) into the {@code Content-Disposition} header. A
 * hand-built {@code filename="<name>"} let a caller who uploads a file named
 * {@code x"; filename="pwn.html} break out of the quoted parameter and spoof the download filename
 * for whoever later reads the attachment. The fix builds the header through {@link ContentDisposition}
 * (as {@code CommitteeCatalogController} already does), which escapes/encodes the name.
 *
 * <p>Hermetic: the controller's {@code download} uses only the mocked service, so a direct call and a
 * {@link ContentDisposition#parse} round-trip prove the property without a Spring context or a DB.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock AttachmentService service;
    @Mock AttachmentAccessPolicy access;

    private String downloadHeader(String storedFileName) {
        AttachmentController controller = new AttachmentController(service, access);
        when(service.download(1L)).thenReturn(
            new AttachmentService.DownloadPayload(storedFileName, "application/pdf", new byte[]{1, 2, 3}));
        ResponseEntity<byte[]> resp = controller.download(1L);
        return resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    }

    /**
     * Load-bearing (discriminating): a filename crafted to break out of {@code filename="..."} must
     * not inject a second Content-Disposition parameter — the header round-trips to the original
     * name, proving the quote is escaped/encoded rather than reflected raw. Pre-fix the hand-built
     * header parses back to the injected {@code pwn.html} (the injection wins), so this leg is RED.
     */
    @Test
    void download_neutralisesFilenameParameterInjection() {
        String malicious = "q3-report\"; filename=\"pwn.html";
        String header = downloadHeader(malicious);
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(malicious);
    }

    /**
     * House rule 7 (correctness, non-discriminating in a unit test — the ISO-8859-1 wire-encoding
     * problem is only visible on the actual HTTP wire): a non-ASCII (Arabic) filename must be
     * RFC 6266-conformant and round-trip to the original.
     */
    @Test
    void download_encodesNonAsciiFilename() {
        String arabic = "تقرير-الامتثال.pdf";
        String header = downloadHeader(arabic);
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(arabic);
    }

    /** The common case is unchanged: a plain filename still downloads under its own name. */
    @Test
    void download_plainFilenameRoundTrips() {
        String header = downloadHeader("evidence.pdf");
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("evidence.pdf");
    }
}
