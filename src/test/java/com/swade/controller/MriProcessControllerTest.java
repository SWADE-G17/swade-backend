package com.swade.controller;

import com.swade.dto.StudyResultResponse;
import com.swade.entity.EstudioEntity;
import com.swade.entity.ResultadoEntity;
import com.swade.security.AuthService;
import com.swade.service.MinioObjectInfo;
import com.swade.service.MinioObjectNotFoundException;
import com.swade.service.StreamedMinioObject;
import com.swade.service.StudyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MriProcessControllerTest {

    private static final String HEATMAP_BUCKET = "heatmaps";
    private static final String REPORT_BUCKET = "reports";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long STUDY_ID = 42L;
    private static final String HEATMAP_PATH = "heatmaps/42_heatmap.nii.gz";
    private static final String ORIG_PATH = "heatmaps/42_orig.mgz";
    private static final String REPORT_PATH = "reports/42_report.pdf";
    private static final String RAW_ETAG = "abc123def456";
    private static final String QUOTED_ETAG = "\"" + RAW_ETAG + "\"";

    private StudyService studyService;
    private AuthService authService;
    private MriProcessController controller;

    @BeforeEach
    void setUp() {
        studyService = mock(StudyService.class);
        authService = mock(AuthService.class);
        controller = new MriProcessController(studyService, authService, HEATMAP_BUCKET, REPORT_BUCKET);

        when(authService.getCurrentUserId()).thenReturn(USER_ID);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("api.example.com");
        request.setServerPort(443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── getResult ───────────────────────────────────────────────────────

    @Test
    void getResult_returnsBuiltUrls_whenPathsPresent() {
        EstudioEntity estudio = completedEstudio();
        ResultadoEntity resultado = resultadoWith(HEATMAP_PATH, ORIG_PATH);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.of(estudio));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));

        ResponseEntity<StudyResultResponse> response = controller.getResult(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StudyResultResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.heatmapUrl()).isEqualTo("https://api.example.com/estudios/" + STUDY_ID + "/heatmap");
        assertThat(body.origUrl()).isEqualTo("https://api.example.com/estudios/" + STUDY_ID + "/orig");
    }

    @Test
    void getResult_returnsNullUrls_whenPathsAreNull() {
        EstudioEntity estudio = completedEstudio();
        ResultadoEntity resultado = resultadoWith(null, null);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.of(estudio));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));

        ResponseEntity<StudyResultResponse> response = controller.getResult(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StudyResultResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.heatmapUrl()).isNull();
        assertThat(body.origUrl()).isNull();
    }

    @Test
    void getResult_returnsOnlyHeatmapUrl_whenOnlyHeatmapPathPresent() {
        EstudioEntity estudio = completedEstudio();
        ResultadoEntity resultado = resultadoWith(HEATMAP_PATH, null);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.of(estudio));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));

        ResponseEntity<StudyResultResponse> response = controller.getResult(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StudyResultResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.heatmapUrl()).isEqualTo("https://api.example.com/estudios/" + STUDY_ID + "/heatmap");
        assertThat(body.origUrl()).isNull();
    }

    @Test
    void getResult_returns404_whenEstudioMissing() {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<StudyResultResponse> response = controller.getResult(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getResult_returns409_whenStatusNotCompleted() {
        EstudioEntity estudio = new EstudioEntity();
        estudio.setStatus("procesando");
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.of(estudio));

        ResponseEntity<StudyResultResponse> response = controller.getResult(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getResult_returns404_whenResultadoMissing() {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.empty());

        ResponseEntity<StudyResultResponse> response = controller.getResult(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── streamHeatmap / streamOrig — full body ──────────────────────────

    @Test
    void streamHeatmap_returnsFullBody_whenNoConditionalHeaders() throws Exception {
        byte[] payload = "fake-nii-gz-bytes".getBytes();
        stubHeatmapAvailable(payload);

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.parseMediaType("application/gzip"));
        assertThat(headers.getContentLength()).isEqualTo(payload.length);
        assertThat(headers.getContentDisposition().getType()).isEqualTo("inline");
        assertThat(headers.getContentDisposition().getFilename()).isEqualTo("42_heatmap.nii.gz");
        assertThat(headers.getCacheControl()).contains("max-age=86400").contains("immutable");
        assertThat(headers.getETag()).isEqualTo(QUOTED_ETAG);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(out);
        assertThat(out.toByteArray()).isEqualTo(payload);
    }

    @Test
    void streamHeatmap_returns404_whenHeatmapPathNull() throws Exception {
        ResultadoEntity resultado = resultadoWith(null, ORIG_PATH);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamOrig_returns404_whenObjectMissingInMinio() throws Exception {
        ResultadoEntity resultado = resultadoWith(null, ORIG_PATH);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));
        when(studyService.statArtifact(anyString(), anyString()))
                .thenThrow(new MinioObjectNotFoundException("heatmaps", "42_orig.mgz", null));

        ResponseEntity<?> response = controller.streamOrig(STUDY_ID, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamOrig_returns409_whenStudyNotCompleted() throws Exception {
        EstudioEntity estudio = new EstudioEntity();
        estudio.setStatus("procesando");
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.of(estudio));

        ResponseEntity<?> response = controller.streamOrig(STUDY_ID, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void streamOrig_returns404_whenStudyMissing() throws Exception {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.streamOrig(STUDY_ID, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── ETag / 304 / 200 with conditional GET ───────────────────────────

    @Test
    void streamHeatmap_returns304_whenIfNoneMatchMatches() throws Exception {
        stubHeatmapAvailable("ignored".getBytes());

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, null, QUOTED_ETAG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getETag()).isEqualTo(QUOTED_ETAG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }

    @Test
    void streamHeatmap_returns304_whenIfNoneMatchIsWildcard() throws Exception {
        stubHeatmapAvailable("ignored".getBytes());

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, null, "*");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void streamHeatmap_returns200_whenIfNoneMatchDoesNotMatch() throws Exception {
        byte[] payload = "abc".getBytes();
        stubHeatmapAvailable(payload);

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, null, "\"different\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo(QUOTED_ETAG);
    }

    // ── Range / 206 / 416 ───────────────────────────────────────────────

    @Test
    void streamHeatmap_returns206_whenRangeIsValid() throws Exception {
        byte[] full = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] partial = Arrays.copyOfRange(full, 2, 6); // bytes 2..5 inclusive
        stubHeatmapStat(full.length);
        when(studyService.openArtifactStream(eq(HEATMAP_PATH), eq(HEATMAP_BUCKET), eq(2L), eq(4L)))
                .thenReturn(new StreamedMinioObject(new ByteArrayInputStream(partial), partial.length));

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, "bytes=2-5", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentLength()).isEqualTo(4L);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 2-5/" + full.length);
        assertThat(headers.getETag()).isEqualTo(QUOTED_ETAG);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(out);
        assertThat(out.toByteArray()).isEqualTo(partial);
    }

    @Test
    void streamHeatmap_returns206_whenRangeIsOpenEnded() throws Exception {
        byte[] full = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] partial = Arrays.copyOfRange(full, 7, 10); // bytes 7..9
        stubHeatmapStat(full.length);
        when(studyService.openArtifactStream(eq(HEATMAP_PATH), eq(HEATMAP_BUCKET), eq(7L), eq(3L)))
                .thenReturn(new StreamedMinioObject(new ByteArrayInputStream(partial), partial.length));

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, "bytes=7-", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 7-9/" + full.length);
    }

    @Test
    void streamHeatmap_returns206_whenRangeIsSuffix() throws Exception {
        byte[] full = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] partial = Arrays.copyOfRange(full, 7, 10); // last 3 bytes
        stubHeatmapStat(full.length);
        when(studyService.openArtifactStream(eq(HEATMAP_PATH), eq(HEATMAP_BUCKET), eq(7L), eq(3L)))
                .thenReturn(new StreamedMinioObject(new ByteArrayInputStream(partial), partial.length));

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, "bytes=-3", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 7-9/" + full.length);
    }

    @Test
    void streamHeatmap_returns416_whenRangeStartIsBeyondSize() throws Exception {
        stubHeatmapStat(10L);

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, "bytes=100-200", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
    }

    @Test
    void streamHeatmap_returns416_whenRangeIsMalformed() throws Exception {
        stubHeatmapStat(10L);

        ResponseEntity<?> response = controller.streamHeatmap(STUDY_ID, "not-a-valid-range", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    // ── streamReporte ───────────────────────────────────────────────────

    @Test
    void streamReporte_returns200_withPdfHeadersAndBody() throws Exception {
        byte[] payload = "%PDF-1.4 fake-pdf-bytes".getBytes();
        ResultadoEntity resultado = resultadoWithReport(REPORT_PATH);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));
        when(studyService.statArtifact(eq(REPORT_PATH), eq(REPORT_BUCKET)))
                .thenReturn(new MinioObjectInfo(payload.length, RAW_ETAG));
        when(studyService.openArtifactStream(eq(REPORT_PATH), eq(REPORT_BUCKET)))
                .thenReturn(new StreamedMinioObject(new ByteArrayInputStream(payload), payload.length, RAW_ETAG));

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(headers.getContentLength()).isEqualTo(payload.length);
        assertThat(headers.getContentDisposition().getType()).isEqualTo("inline");
        assertThat(headers.getContentDisposition().getFilename())
                .isEqualTo("estudio-" + STUDY_ID + "-report.pdf");
        assertThat(headers.getCacheControl())
                .contains("private")
                .contains("max-age=0")
                .contains("must-revalidate");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toByteArray()).isEqualTo(payload);
    }

    @Test
    void streamReporte_returns404_whenEstudioMissing() {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamReporte_returns404_whenResultadoMissing() {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.empty());

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamReporte_returns404_whenReportPathIsNull() {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultadoWithReport(null)));

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamReporte_returns404_whenReportPathIsBlank() {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultadoWithReport("   ")));

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamReporte_returns404_whenObjectMissingInMinio() throws Exception {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID))
                .thenReturn(Optional.of(resultadoWithReport(REPORT_PATH)));
        when(studyService.statArtifact(eq(REPORT_PATH), eq(REPORT_BUCKET)))
                .thenThrow(new MinioObjectNotFoundException(REPORT_BUCKET, "42_report.pdf", null));

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void streamReporte_returns502_onUpstreamFailure() throws Exception {
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID))
                .thenReturn(Optional.of(resultadoWithReport(REPORT_PATH)));
        when(studyService.statArtifact(eq(REPORT_PATH), eq(REPORT_BUCKET)))
                .thenThrow(new IOException("upstream boom"));

        ResponseEntity<StreamingResponseBody> response = controller.streamReporte(STUDY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Stub everything required for {@code /heatmap} to find the artifact and stat it. */
    private void stubHeatmapAvailable(byte[] body) throws Exception {
        ResultadoEntity resultado = resultadoWith(HEATMAP_PATH, ORIG_PATH);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));
        when(studyService.statArtifact(eq(HEATMAP_PATH), eq(HEATMAP_BUCKET)))
                .thenReturn(new MinioObjectInfo(body.length, RAW_ETAG));
        when(studyService.openArtifactStream(eq(HEATMAP_PATH), eq(HEATMAP_BUCKET)))
                .thenReturn(new StreamedMinioObject(new ByteArrayInputStream(body), body.length, RAW_ETAG));
    }

    /** Stat-only stub for tests that only exercise pre-stream branches (304, 416). */
    private void stubHeatmapStat(long size) throws Exception {
        ResultadoEntity resultado = resultadoWith(HEATMAP_PATH, ORIG_PATH);
        when(studyService.getByIdAndUsuario(STUDY_ID, USER_ID))
                .thenReturn(Optional.of(completedEstudio()));
        when(studyService.getResultado(STUDY_ID)).thenReturn(Optional.of(resultado));
        when(studyService.statArtifact(eq(HEATMAP_PATH), eq(HEATMAP_BUCKET)))
                .thenReturn(new MinioObjectInfo(size, RAW_ETAG));
        // Defensive fallback so a misrouted call surfaces as a clear test failure.
        when(studyService.openArtifactStream(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new AssertionError("openArtifactStream(range) should not be called for this test"));
    }

    private static EstudioEntity completedEstudio() {
        EstudioEntity e = new EstudioEntity();
        e.setId(STUDY_ID);
        e.setStatus("listo");
        return e;
    }

    private static ResultadoEntity resultadoWith(String heatmapPath, String origPath) {
        ResultadoEntity r = new ResultadoEntity();
        r.setHeatmapPath(heatmapPath);
        r.setOrigPath(origPath);
        return r;
    }

    private static ResultadoEntity resultadoWithReport(String reportPath) {
        ResultadoEntity r = new ResultadoEntity();
        r.setReportPath(reportPath);
        return r;
    }
}
