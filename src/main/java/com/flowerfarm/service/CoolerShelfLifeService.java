package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Cooler shelf-life review over existing harvest lots: aging board, crop intervals,
 * count/quality/loss updates (inventory via {@link HarvestService#update}), QR labels.
 */
@Service
public class CoolerShelfLifeService {

    private final HarvestService harvestService;
    private final FarmProfile farmProfile;
    private final CoolerShelfLifePreferences preferences;

    @Autowired
    public CoolerShelfLifeService(HarvestService harvestService, FarmProfile farmProfile) {
        this(harvestService, farmProfile, new CoolerShelfLifePreferences());
    }

    CoolerShelfLifeService(HarvestService harvestService, FarmProfile farmProfile,
                           CoolerShelfLifePreferences preferences) {
        this.harvestService = Objects.requireNonNull(harvestService, "harvestService");
        this.farmProfile = Objects.requireNonNull(farmProfile, "farmProfile");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Transactional(readOnly = true)
    public CoolerShelfLife.Board board() {
        return board(farmProfile.today());
    }

    @Transactional(readOnly = true)
    public CoolerShelfLife.Board board(LocalDate asOf) {
        LocalDate day = asOf == null ? farmProfile.today() : asOf;
        CoolerShelfLife.Settings settings = preferences.load();
        return CoolerShelfLife.board(day, harvestService.getAll(), settings);
    }

    public CoolerShelfLife.Settings settings() {
        return preferences.load();
    }

    public CoolerShelfLife.Settings saveDefaultInterval(int days) {
        CoolerShelfLife.Settings updated = preferences.load().withDefaultInterval(days);
        preferences.save(updated);
        return updated;
    }

    public CoolerShelfLife.Settings saveCropInterval(String crop, int days,
                                                     CoolerShelfLife.Conditioning conditioning) {
        CoolerShelfLife.Settings updated = preferences.load().withCropInterval(crop, days);
        if (conditioning != null) {
            updated = updated.withCropConditioning(crop, conditioning);
        }
        preferences.save(updated);
        return updated;
    }

    @Transactional
    public HarvestEntry recordReview(long harvestId, CoolerShelfLife.ReviewAction action) {
        Objects.requireNonNull(action, "action");
        HarvestEntry existing = harvestService.findById(harvestId)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No harvest lot with id=" + harvestId));
        CoolerShelfLife.AppliedReview applied = CoolerShelfLife.applyReview(existing, action);
        HarvestEntry incoming = copyForUpdate(existing, applied);
        HarvestEntry saved = harvestService.update(harvestId, incoming);
        LocalDate today = farmProfile.today();
        preferences.save(preferences.load().withReviewed(saved.getLotCode(), today));
        return saved;
    }

    @Transactional(readOnly = true)
    public byte[] lotLabelPdf(long harvestId) {
        return harvestService.generateLotLabelPdf(harvestId);
    }

    private static HarvestEntry copyForUpdate(HarvestEntry existing,
                                              CoolerShelfLife.AppliedReview applied) {
        return new HarvestEntry(
                existing.getId(),
                existing.getHarvestDate(),
                existing.getCropName(),
                applied.marketableQuantity(),
                existing.getUnit(),
                existing.getBedOrField(),
                applied.notes(),
                existing.getLotCode(),
                applied.grade() == null ? HarvestGrade.UNGRADED : applied.grade(),
                applied.lossQuantity(),
                existing.getStorageLocation());
    }
}
