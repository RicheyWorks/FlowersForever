package com.flowerfarm.controller;

import com.flowerfarm.model.KnowledgeEntry;
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
    public List<KnowledgeEntry> search(
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
                query, crop, bed, tag, from, to, followUpDue, limit));
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return knowledgeService.distinctTags();
    }

    @GetMapping("/{id}")
    public KnowledgeEntry get(@PathVariable Long id) {
        return knowledgeService.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No knowledge entry with id=" + id));
    }

    @PostMapping
    public ResponseEntity<KnowledgeEntry> create(@RequestBody KnowledgeEntry entry) {
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledgeService.create(entry));
    }

    @PutMapping("/{id}")
    public KnowledgeEntry update(@PathVariable Long id, @RequestBody KnowledgeEntry entry) {
        return knowledgeService.update(id, entry);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
