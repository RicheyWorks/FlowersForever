package com.flowerfarm.controller;

import com.flowerfarm.controller.dto.KnowledgeApiDtos.KnowledgeRequest;
import com.flowerfarm.controller.dto.KnowledgeApiDtos.KnowledgeResponse;
import com.flowerfarm.service.KnowledgeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Plain-language REST access to the farm-owned Knowledge Log. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public List<KnowledgeResponse> search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "crop", required = false) String crop,
            @RequestParam(value = "bed", required = false) String bed,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "followUpDue", defaultValue = "false") boolean followUpDue,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return knowledgeService.search(new KnowledgeService.SearchRequest(
                query, crop, bed, tag, from, to, followUpDue, limit)).stream()
                .map(KnowledgeResponse::from).toList();
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return knowledgeService.distinctTags();
    }

    @GetMapping("/{id}")
    public KnowledgeResponse get(@PathVariable Long id) {
        return knowledgeService.findById(id).map(KnowledgeResponse::from)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No knowledge entry with id=" + id));
    }

    @PostMapping
    public ResponseEntity<KnowledgeResponse> create(@RequestBody KnowledgeRequest entry) {
        return ResponseEntity.status(HttpStatus.CREATED).body(KnowledgeResponse.from(
                knowledgeService.create(entry == null ? null : entry.toEntry())));
    }

    @PutMapping("/{id}")
    public KnowledgeResponse update(@PathVariable Long id, @RequestBody KnowledgeRequest entry) {
        return KnowledgeResponse.from(knowledgeService.update(id, entry == null ? null : entry.toEntry()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
