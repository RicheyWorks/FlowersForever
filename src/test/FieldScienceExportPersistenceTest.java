package com.flowerfarm.service;

import com.flowerfarm.model.FieldScienceExport;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.headless=true",
        "spring.datasource.url=jdbc:h2:mem:field-science-export-persistence;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "flowerfarm.inventory.seed-csv=target/no-field-science-export-seed.csv",
        "flowerfarm.irrigation.live-weather=false"
})
class FieldScienceExportPersistenceTest {

    @Autowired FieldScienceExportService service;
    @Autowired KnowledgeService knowledgeService;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void knowledgeFindingRetainsReviewedExportIdAcrossPersistence() {
        FieldScienceExport export = service.save("farm-scout", "West block walk",
                "walk.eco", "", "observed: field conditions");
        KnowledgeEntry finding = new KnowledgeEntry(LocalDate.now().minusDays(1),
                "Finding after west block walk", KnowledgeKind.PRACTICE,
                "Rose", "", "West block", "Growing",
                "Scouted the west block and recorded what I saw.", "", "",
                "", null, KnowledgeConfidence.NEEDS_REVIEW,
                List.of("field-science"));
        finding.setFieldScienceExportId(export.getId());

        KnowledgeEntry saved = knowledgeService.create(finding);
        entityManager.flush();
        entityManager.clear();

        assertThat(knowledgeService.findById(saved.getId()).orElseThrow()
                .getFieldScienceExportId()).isEqualTo(export.getId());
    }

    @Test
    @Transactional
    void savesAndReadsLongUnchangedExportThroughH2Clob() {
        String original = "\uFEFF# CSRBT export\r\n"
                + "data: rose,café\n".repeat(200)
                + "  final line  \r\n";

        FieldScienceExport saved = service.save("farm-scout", "Field walk",
                "october.csv", "Scouted west block", original);
        entityManager.flush();
        entityManager.clear();
        FieldScienceExportSummary listing = service.list().stream()
                .filter(entry -> entry.id().equals(saved.getId()))
                .findFirst().orElseThrow();
        FieldScienceExport loaded = service.find(saved.getId()).orElseThrow();

        assertThat(listing.title()).isEqualTo("Field walk");
        assertThat(listing.sourceUrl()).endsWith("/docs/farm-scout.html");
        assertThat(listing.byteLength()).isEqualTo(saved.getByteLength());
        assertThat(loaded.getContent()).isEqualTo(original);
        assertThat(loaded.getSha256()).isEqualTo(listing.sha256());
    }

    @Test
    @Transactional
    void archivePagesExposeExportsBeyondTheFirstTwoHundred() {
        for (int index = 0; index < 201; index++) {
            service.save("farm-scout", "Walk " + index, null, "", "data: " + index);
        }
        service.save("farm-scout", "Other field", null, "", "data: other");
        service.save("farm-scout", "Another field", null, "", "data: another");
        entityManager.flush();
        entityManager.clear();

        FieldScienceExportService.ExportPage first = service.page("walk", 0);
        FieldScienceExportService.ExportPage second = service.page("  WALK  ", 1);

        assertThat(first.exports()).hasSize(200);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.totalCount()).isEqualTo(201);
        assertThat(first.exports().get(0).title()).isEqualTo("Walk 200");
        assertThat(second.exports()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.totalCount()).isEqualTo(201);
        assertThat(second.exports().get(0).title()).isEqualTo("Walk 0");
        assertThat(service.page("missing", 0).exports()).isEmpty();
        assertThat(service.page("missing", 0).totalCount()).isZero();
        assertThat(service.page("", 0).totalCount()).isEqualTo(203);
        assertThat(service.page(null, 0).exports()).isEqualTo(service.list());
    }

    @Test
    @Transactional
    void metadataSearchTreatsWildcardsAndApostrophesAsLiteralText() {
        service.save("farm-scout", "Title Needle", null, "", "data: 1");
        service.save("soil-bench", "Bench", "needle-file.eco", "", "data: 2");
        service.save("soil-recipes", "Recipe", null, "noted needle", "data: 3");
        service.save("farm-scout", "Literal %_ O'Brien", null, "", "data: 4");
        service.save("farm-scout", "Unrelated", null, "", "needle only in body");
        entityManager.flush();
        entityManager.clear();

        FieldScienceExportService.ExportPage needle = service.page("NEEDLE", 0);
        assertThat(needle.totalCount()).isEqualTo(3);
        assertThat(needle.exports()).extracting(FieldScienceExportSummary::title)
                .containsExactlyInAnyOrder("Title Needle", "Bench", "Recipe");
        assertThat(service.page("soil bench", 0).exports())
                .extracting(FieldScienceExportSummary::title).containsExactly("Bench");
        assertThat(service.page("%_", 0).exports())
                .extracting(FieldScienceExportSummary::title)
                .containsExactly("Literal %_ O'Brien");
        assertThat(service.page("o'brien", 0).exports())
                .extracting(FieldScienceExportSummary::title)
                .containsExactly("Literal %_ O'Brien");
        assertThat(service.page("only in body", 0).exports()).isEmpty();
    }
}
