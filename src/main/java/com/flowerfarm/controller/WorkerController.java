package com.flowerfarm.controller;

import com.flowerfarm.service.WorkerWorkflowService;
import com.flowerfarm.service.FieldAttachmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

/** Compact field-worker API used by the phone/tablet web surface. */
@RestController
@RequestMapping("/api/worker")
public class WorkerController {

    private final WorkerWorkflowService service;
    private final FieldAttachmentService attachments;

    public WorkerController(
            WorkerWorkflowService service, FieldAttachmentService attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    @GetMapping("/today")
    public ResponseEntity<?> today(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.today(date));
    }

    @GetMapping("/inventory")
    public ResponseEntity<?> inventory(
            @RequestParam(value = "query", defaultValue = "") String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(service.inventory(query, limit));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(
            @RequestParam(value = "through", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(service.orders(through, limit));
    }

    @PostMapping("/harvests")
    public ResponseEntity<?> logHarvest(
            @RequestBody WorkerWorkflowService.WorkerHarvestRequest request) {
        WorkerWorkflowService.WorkerActionResult result = service.logHarvest(request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @GetMapping("/field-notes")
    public ResponseEntity<?> fieldNotes(
            @RequestParam(value = "through", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.fieldNotes(through, limit));
    }

    @PostMapping("/field-notes")
    public ResponseEntity<?> addFieldNote(
            @RequestBody WorkerWorkflowService.WorkerFieldNoteRequest request) {
        WorkerWorkflowService.WorkerActionResult result = service.addFieldNote(request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<?> completeTask(
            @PathVariable Long taskId,
            @RequestBody WorkerWorkflowService.WorkerTaskCompletionRequest request) {
        WorkerWorkflowService.WorkerActionResult result =
                service.completeTask(taskId, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping("/inventory/{itemId}/count")
    public ResponseEntity<?> countInventory(
            @PathVariable Long itemId,
            @RequestBody WorkerWorkflowService.WorkerInventoryCountRequest request) {
        WorkerWorkflowService.WorkerActionResult result =
                service.countInventory(itemId, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<?> completeOrder(
            @PathVariable Long orderId,
            @RequestBody WorkerWorkflowService.WorkerOrderCompletionRequest request) {
        WorkerWorkflowService.WorkerActionResult result =
                service.completeOrder(orderId, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addAttachment(
            @RequestParam("operationKey") String operationKey,
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId,
            @RequestParam(value = "caption", defaultValue = "") String caption,
            @RequestParam(value = "capturedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant capturedAt,
            @RequestParam("file") MultipartFile file) throws IOException {
        WorkerWorkflowService.WorkerAttachmentRequest request =
                new WorkerWorkflowService.WorkerAttachmentRequest(
                        operationKey, targetType, targetId,
                        file.getOriginalFilename(), file.getContentType(),
                        caption, capturedAt);
        WorkerWorkflowService.WorkerActionResult result =
                service.addAttachment(request, file.getBytes());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @GetMapping("/attachments")
    public ResponseEntity<?> attachments(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId) {
        return ResponseEntity.ok(attachments.listForTarget(targetType, targetId));
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> attachmentContent(@PathVariable Long attachmentId) {
        FieldAttachmentService.AttachmentContent content = attachments.read(attachmentId);
        FieldAttachmentService.AttachmentRecord metadata = content.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.mediaType()))
                .contentLength(metadata.byteSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(metadata.originalFilename(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-SHA256", metadata.sha256())
                .body(content.bytes());
    }

    @GetMapping("/operations/{operationKey}")
    public ResponseEntity<?> recover(@PathVariable String operationKey) {
        return ResponseEntity.ok(service.recover(operationKey));
    }
}
