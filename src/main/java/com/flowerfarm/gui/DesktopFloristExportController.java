package com.flowerfarm.gui;

import com.flowerfarm.service.FloristAvailabilityExport;
import com.flowerfarm.service.FloristAvailabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * File / Inventory → Share availability with florists (copy, CSV, mailto).
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopFloristExportController {

    public record Host(JFrame frame, FarmDialogs dialogs, FarmFileChooser files,
                       FarmClipboard clipboard, FarmMailer mailer, Consumer<String> status) {
    }

    private final FloristAvailabilityService availability;
    private Host host;

    @Autowired
    public DesktopFloristExportController(FloristAvailabilityService availability) {
        this.availability = Objects.requireNonNull(availability, "availability");
    }

    public void attach(Host frameHost) {
        this.host = Objects.requireNonNull(frameHost, "frameHost");
    }

    public void shareAvailability() {
        if (host == null) {
            return;
        }
        FloristAvailabilityExport.Sheet sheet = availability.buildForCurrentWeek();
        if (sheet.isEmpty()) {
            host.dialogs().showMessage(host.frame(), FloristAvailabilityExport.statusEmpty(),
                    FloristAvailabilityExport.TITLE, JOptionPane.INFORMATION_MESSAGE);
            host.status().accept(FloristAvailabilityExport.statusEmpty());
            return;
        }
        JTextArea preview = new JTextArea(FloristAvailabilityExport.shareText(sheet), 18, 64);
        preview.setEditable(false);
        preview.setCaretPosition(0);
        preview.setName("florist.availabilityPreview");
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setPreferredSize(new Dimension(640, 360));
        JPanel panel = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        panel.setName("florist.availabilityPanel");
        panel.add(FarmUi.mutedLabel(
                "Live cooler stock for florists, plus this week’s planned stems. "
                        + "CSV columns match a simple Florlist-style import."),
                BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(new JLabel(sheet.lines().size() + " product(s) · week "
                        + sheet.weekStart() + "–" + sheet.weekEnd()),
                BorderLayout.SOUTH);

        Object[] options = {"Copy for florists", "Save a spreadsheet (CSV)…", "Open email draft…", "Close"};
        int choice = host.dialogs().showOption(host.frame(), panel, FloristAvailabilityExport.TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            copyShare(sheet);
        } else if (choice == 1) {
            saveCsv(sheet);
        } else if (choice == 2) {
            openMail(sheet);
        }
    }

    private void copyShare(FloristAvailabilityExport.Sheet sheet) {
        host.clipboard().copyText(FloristAvailabilityExport.shareText(sheet));
        host.status().accept(FloristAvailabilityExport.statusCopied());
    }

    private void saveCsv(FloristAvailabilityExport.Sheet sheet) {
        String suggested = FloristAvailabilityExport.suggestedCsvFileName(sheet);
        host.files().chooseSave(host.frame(), "Save your florist stock list as a spreadsheet (CSV)",
                        new java.io.File(suggested))
                .ifPresent(file -> writeCsv(sheet, file.toPath()));
    }

    private void writeCsv(FloristAvailabilityExport.Sheet sheet, Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path fileName = path.getFileName();
            String label = fileName == null ? path.toString() : fileName.toString();
            Path temporary = path.resolveSibling(label + ".tmp");
            Files.writeString(temporary, FloristAvailabilityExport.csv(sheet), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            host.status().accept(FloristAvailabilityExport.statusSaved(label));
        } catch (IOException failed) {
            host.dialogs().showMessage(host.frame(),
                    "Could not save the spreadsheet: " + failed.getMessage(),
                    FloristAvailabilityExport.TITLE, JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openMail(FloristAvailabilityExport.Sheet sheet) {
        String body = FloristAvailabilityExport.shareText(sheet)
                + "\n---\nCSV columns: Product, Quantity, Unit, Price, Week start, Week end, "
                + "Planned stems, Notes. Attach the saved CSV when importing to Florlist.\n";
        String subject = FloristAvailabilityExport.emailSubject(sheet);
        try {
            // Blank To — the grower addresses florists; body carries the availability list.
            host.mailer().openMailto(URI.create(
                    "mailto:?subject=" + encode(subject) + "&body=" + encode(body)));
            host.clipboard().copyText(FloristAvailabilityExport.csv(sheet));
            host.status().accept(
                    "Email draft opened · CSV also copied for attaching or Florlist import.");
        } catch (Exception failed) {
            host.clipboard().copyText(FloristAvailabilityExport.shareText(sheet));
            host.status().accept("Could not open email — availability text was copied instead.");
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
