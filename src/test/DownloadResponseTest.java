package com.flowerfarm.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("REST download response policy")
class DownloadResponseTest {

    @Test
    @DisplayName("builds consistent PDF and CSV attachments")
    void buildsAttachments() {
        byte[] body = {1, 2, 3};
        var pdf = DownloadResponse.pdf(body, "report.pdf");
        var csv = DownloadResponse.csv(body, "report.csv");

        assertThat(pdf.getBody()).isSameAs(body);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getHeaders().getContentLength()).isEqualTo(3);
        assertThat(pdf.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("report.pdf");
        assertThat(csv.getHeaders().getContentType()).isEqualTo(
                new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("rejects header injection and missing bodies")
    void rejectsUnsafeInputs() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DownloadResponse.pdf(new byte[0], "report.pdf\r\nX-Evil: true"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                DownloadResponse.pdf(null, "report.pdf"));
    }
}
