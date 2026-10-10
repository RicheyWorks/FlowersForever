package com.flowerfarm.gui;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.CoolerShelfLife;
import com.flowerfarm.service.CoolerShelfLifeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Help / Inventory → Cooler lot review: aging board, crop intervals, count/quality/loss, QR labels.
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopCoolerReviewController {

    public record Host(JFrame frame, FarmDialogs dialogs, FarmFileChooser files,
                       Consumer<String> status) {
    }

    private final CoolerShelfLifeService cooler;
    private Host host;

    @Autowired
    public DesktopCoolerReviewController(CoolerShelfLifeService cooler) {
        this.cooler = Objects.requireNonNull(cooler, "cooler");
    }

    public void attach(Host frameHost) {
        this.host = Objects.requireNonNull(frameHost, "frameHost");
    }

    public void openReview() {
        if (host == null) {
            return;
        }
        CoolerReviewPanel panel = new CoolerReviewPanel(host.status());
        Runnable refresh = () -> panel.showBoard(cooler.board());
        refresh.run();

        JButton review = FarmUi.primaryButton("Record review…");
        review.setName("cooler.recordReview");
        review.addActionListener(event -> recordReview(panel, refresh));

        JButton label = FarmUi.secondaryButton("Save QR lot label…");
        label.setName("cooler.saveLabel");
        label.addActionListener(event -> saveLabel(panel));

        JButton interval = FarmUi.secondaryButton("Crop review interval…");
        interval.setName("cooler.cropInterval");
        interval.addActionListener(event -> editInterval(panel, refresh));

        JButton refreshBtn = FarmUi.secondaryButton("Refresh");
        refreshBtn.setName("cooler.refresh");
        refreshBtn.addActionListener(event -> refresh.run());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        actions.setOpaque(false);
        actions.add(review);
        actions.add(label);
        actions.add(interval);
        actions.add(refreshBtn);

        JPanel wrap = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        wrap.setName("cooler.dialog");
        wrap.add(panel, BorderLayout.CENTER);
        wrap.add(actions, BorderLayout.SOUTH);

        Object[] options = {"Close"};
        host.dialogs().showOption(host.frame(), wrap, CoolerShelfLife.TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }

    private void recordReview(CoolerReviewPanel panel, Runnable refresh) {
        CoolerShelfLife.AgedLot lot = panel.selectedLot().orElse(null);
        if (lot == null) {
            panel.announce("Select a cooler lot before recording a review.");
            return;
        }
        JPanel form = panel.buildReviewForm(lot);
        Object[] options = {"Save review", "Cancel"};
        int choice = host.dialogs().showOption(host.frame(), form, "Record cooler review",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            return;
        }
        try {
            CoolerShelfLife.ReviewAction action = panel.readReviewForm(form);
            HarvestEntry saved = cooler.recordReview(lot.harvestId(), action);
            String message = CoolerShelfLife.statusReviewRecorded(saved.getLotCode());
            panel.announce(message);
            refresh.run();
        } catch (RuntimeException failed) {
            host.dialogs().showMessage(host.frame(), failed.getMessage(), CoolerShelfLife.TITLE,
                    JOptionPane.WARNING_MESSAGE);
            panel.announce(failed.getMessage());
        }
    }

    private void saveLabel(CoolerReviewPanel panel) {
        CoolerShelfLife.AgedLot lot = panel.selectedLot().orElse(null);
        if (lot == null) {
            panel.announce("Select a cooler lot before saving its QR label.");
            return;
        }
        String suggested = "harvest-lot-" + lot.lotCode() + ".pdf";
        host.files().chooseSave(host.frame(), "Save printable harvest lot label", new File(suggested))
                .ifPresent(file -> writeLabel(panel, lot, file));
    }

    private void writeLabel(CoolerReviewPanel panel, CoolerShelfLife.AgedLot lot, File file) {
        try {
            byte[] pdf = cooler.lotLabelPdf(lot.harvestId());
            com.flowerfarm.safety.SafeFiles.write(file.toPath(), pdf);
            panel.announce(CoolerShelfLife.statusLabelSaved(lot.lotCode()));
        } catch (IOException | RuntimeException failed) {
            host.dialogs().showMessage(host.frame(),
                    com.flowerfarm.gui.FarmUi.actionableError("Saving the lot label", failed),
                    CoolerShelfLife.TITLE, JOptionPane.WARNING_MESSAGE);
            panel.announce("Could not save the QR lot label.");
        }
    }

    private void editInterval(CoolerReviewPanel panel, Runnable refresh) {
        CoolerShelfLife.AgedLot selected = panel.selectedLot().orElse(null);
        CoolerShelfLife.Settings settings = cooler.settings();
        String crop = selected == null ? "" : selected.cropName();
        int days = selected == null
                ? settings.defaultIntervalDays()
                : CoolerShelfLife.intervalDays(settings, crop);
        CoolerShelfLife.Conditioning conditioning = selected == null
                ? CoolerShelfLife.Conditioning.FRESH_CUT
                : selected.conditioning();
        JPanel form = panel.buildIntervalForm(crop, days, conditioning);
        Object[] options = {"Save interval", "Cancel"};
        int choice = host.dialogs().showOption(host.frame(), form, "Crop cooler review interval",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            return;
        }
        try {
            CoolerReviewPanel.IntervalChoice values = panel.readIntervalForm(form);
            cooler.saveCropInterval(values.crop(), values.days(), values.conditioning());
            String message = CoolerShelfLife.statusSavedInterval(values.crop(), values.days());
            panel.announce(message);
            refresh.run();
        } catch (RuntimeException failed) {
            host.dialogs().showMessage(host.frame(), failed.getMessage(), CoolerShelfLife.TITLE,
                    JOptionPane.WARNING_MESSAGE);
            panel.announce(failed.getMessage());
        }
    }
}
