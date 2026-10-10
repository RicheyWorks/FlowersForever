package com.flowerfarm.automation.farm;

import com.flowerfarm.model.FarmDataImportReceipt;
import com.flowerfarm.repository.FarmDataImportReceiptJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Persistence boundary for resumable AI and legacy-data imports. */
@Service
public class FarmDataImportReceiptService {

    private final FarmDataImportReceiptJpaRepository repository;

    public FarmDataImportReceiptService(FarmDataImportReceiptJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<FarmDataImportReceipt> findForUpdate(
            String sourceSystem, String recordType, String sourceKey) {
        return repository.findForUpdate(
                FarmDataImportReceipt.normalizedSourceSystem(sourceSystem),
                recordType, FarmDataImportReceipt.normalizedSourceKey(sourceKey));
    }

    @Transactional
    public FarmDataImportReceipt record(
            String sourceSystem, String recordType, String sourceKey, String action,
            String fingerprint, Long targetRecordId) {
        return repository.saveAndFlush(new FarmDataImportReceipt(
                sourceSystem, recordType, sourceKey, action, fingerprint, targetRecordId));
    }

    @Transactional
    public FarmDataImportReceipt update(
            FarmDataImportReceipt receipt, String action, String fingerprint,
            Long targetRecordId) {
        receipt.updateTarget(action, fingerprint, targetRecordId);
        return repository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Page<FarmDataImportReceipt> list(
            String sourceSystem, String recordType, int page, int limit) {
        String normalizedSource = sourceSystem == null || sourceSystem.isBlank()
                ? null : FarmDataImportReceipt.normalizedSourceSystem(sourceSystem);
        String normalizedType = recordType == null || recordType.isBlank()
                ? null : recordType.trim().toLowerCase(java.util.Locale.ROOT);
        return repository.findFiltered(
                normalizedSource, normalizedType, PageRequest.of(page, limit));
    }
}
