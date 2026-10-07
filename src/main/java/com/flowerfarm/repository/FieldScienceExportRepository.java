package com.flowerfarm.repository;

import com.flowerfarm.model.FieldScienceExport;
import com.flowerfarm.model.FieldScienceExportSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** H2 store for captured CSRBT text; listings avoid loading every export body. */
public interface FieldScienceExportRepository extends JpaRepository<FieldScienceExport, Long> {

    /** Keep Spring Data paging objects at the persistence boundary. */
    default SummarySlice summaryPage(String query, int pageIndex, int pageSize) {
        Page<FieldScienceExportSummary> result = findSummaryPage(query,
                PageRequest.of(pageIndex, pageSize));
        return new SummarySlice(result.getContent(), result.hasNext(),
                result.getTotalElements());
    }

    @Query(value = """
            select new com.flowerfarm.model.FieldScienceExportSummary(
                e.id, e.createdAt, e.instrumentId, e.instrumentTitle,
                e.sourceUrl, e.sourceCommit, e.title, e.filename, e.note,
                e.byteLength, e.sha256)
            from FieldScienceExport e
            where :query = ''
               or locate(:query, lower(e.title)) > 0
               or locate(:query, lower(e.instrumentTitle)) > 0
               or locate(:query, lower(coalesce(e.filename, ''))) > 0
               or locate(:query, lower(coalesce(e.note, ''))) > 0
            order by e.createdAt desc, e.id desc
            """, countQuery = """
            select count(e) from FieldScienceExport e
            where :query = ''
               or locate(:query, lower(e.title)) > 0
               or locate(:query, lower(e.instrumentTitle)) > 0
               or locate(:query, lower(coalesce(e.filename, ''))) > 0
               or locate(:query, lower(coalesce(e.note, ''))) > 0
            """)
    Page<FieldScienceExportSummary> findSummaryPage(@Param("query") String query,
                                                     Pageable pageable);

    record SummarySlice(List<FieldScienceExportSummary> exports,
                        boolean hasMore, long totalCount) {
        public SummarySlice {
            exports = List.copyOf(exports);
        }
    }
}
