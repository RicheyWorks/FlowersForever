package com.flowerfarm.service;

import com.flowerfarm.model.FieldAttachment;
import com.flowerfarm.model.FieldAttachmentTargetType;
import com.flowerfarm.repository.FieldAttachmentJpaRepository;
import com.flowerfarm.repository.ProductionTaskJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Durable field attachment storage")
class FieldAttachmentServiceTest {

    @Mock FieldAttachmentJpaRepository attachments;
    @Mock ProductionTaskJpaRepository tasks;
    @TempDir Path storage;

    FieldAttachmentService service;
    AtomicReference<FieldAttachment> saved;

    @BeforeEach
    void setUp() {
        service = new FieldAttachmentService(
                attachments, tasks, storage.toString());
        saved = new AtomicReference<>();
        when(attachments.saveAndFlush(any(FieldAttachment.class))).thenAnswer(invocation -> {
            FieldAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 7L);
            ReflectionTestUtils.setField(
                    attachment, "createdAt", Instant.parse("2026-09-02T15:00:00Z"));
            saved.set(attachment);
            return attachment;
        });
    }

    @Test
    @DisplayName("stores by digest and returns verified bounded chunks")
    void storesAndReadsVerifiedContent() throws Exception {
        byte[] png = tinyPng();
        when(tasks.existsById(42L)).thenReturn(true);

        FieldAttachmentService.AttachmentRecord record = service.add(
                "production-task", 42L, "../north-bed.png", "image/png",
                "Aphids below threshold", Instant.parse("2026-09-02T14:30:00Z"), png);

        assertThat(record.id()).isEqualTo(7L);
        assertThat(record.targetType()).isEqualTo(FieldAttachmentTargetType.PRODUCTION_TASK);
        assertThat(record.originalFilename()).isEqualTo("north-bed.png");
        assertThat(record.sha256()).isEqualTo(FieldAttachmentService.sha256(png));
        Path content = storage.resolve(record.sha256().substring(0, 2))
                .resolve(record.sha256());
        assertThat(content).hasBinaryContent(png);

        when(attachments.findById(7L)).thenReturn(Optional.of(saved.get()));
        FieldAttachmentService.AttachmentContentChunk first =
                service.readChunk(7L, 0, 12);
        FieldAttachmentService.AttachmentContentChunk rest =
                service.readChunk(7L, first.nextOffset(), png.length);

        assertThat(first.bytes()).containsExactly(java.util.Arrays.copyOfRange(png, 0, 12));
        assertThat(first.hasMore()).isTrue();
        assertThat(rest.nextOffset()).isEqualTo(-1);
        assertThat(service.read(7L).bytes()).containsExactly(png);
    }

    @Test
    @DisplayName("rejects missing targets, disguised content, and corrupt stored bytes")
    void rejectsInvalidOrCorruptContent() throws Exception {
        byte[] png = tinyPng();
        when(tasks.existsById(42L)).thenReturn(true);

        assertThatThrownBy(() -> service.add(
                "ORDER", 999L, "proof.png", "image/png", "", null, png))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRODUCTION_TASK");
        assertThatThrownBy(() -> service.add(
                "PRODUCTION_TASK", 42L, "proof.jpg", "image/jpeg", "", null, png))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        byte[] oversized = new byte[FieldAttachmentService.MAX_ATTACHMENT_BYTES + 1];
        assertThatThrownBy(() -> service.add(
                "PRODUCTION_TASK", 42L, "proof.png", "image/png", "", null, oversized))
                .isInstanceOf(AttachmentTooLargeException.class)
                .hasMessageContaining("15 MiB");

        FieldAttachmentService.AttachmentRecord record = service.add(
                "PRODUCTION_TASK", 42L, "proof.png", "image/png", "", null, png);
        when(attachments.findById(7L)).thenReturn(Optional.of(saved.get()));
        Path content = storage.resolve(record.sha256().substring(0, 2))
                .resolve(record.sha256());
        byte[] changed = Files.readAllBytes(content);
        changed[changed.length - 1] ^= 1;
        Files.write(content, changed);

        assertThatThrownBy(() -> service.read(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash does not match");
    }

    private static byte[] tinyPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
