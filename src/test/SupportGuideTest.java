package com.flowerfarm.support;

import com.flowerfarm.gui.FarmClipboard;
import com.flowerfarm.gui.FarmMailer;
import com.flowerfarm.gui.SupportPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pilot support — contact / report a problem")
class SupportGuideTest {

    @Test
    @DisplayName("diagnostics include version and OS lines growers can paste")
    void diagnosticsShape() {
        String text = SupportGuide.diagnostics("1.2.0", "Windows 11", "10.0", "amd64", "21.0.4");
        assertThat(text).contains("FlowersForever version: 1.2.0")
                .contains("Operating system: Windows 11 10.0 (amd64)")
                .contains("Java: 21.0.4");
    }

    @Test
    @DisplayName("mailto encodes subject and body for a valid support address")
    void mailtoEncoding() {
        URI uri = SupportGuide.mailtoUri("Support@RicheyWorks.com",
                SupportGuide.emailSubject("1.2.0"),
                SupportGuide.emailBody("FlowersForever version: 1.2.0"));
        assertThat(uri.getScheme()).isEqualTo("mailto");
        assertThat(uri.toString()).startsWith("mailto:support@richeyworks.com?")
                .contains("subject=FlowersForever%20pilot%20help")
                .contains("body=What%20I%20was%20trying%20to%20do");
    }

    @Test
    @DisplayName("invalid emails are rejected before opening mail")
    void emailValidation() {
        assertThat(SupportGuide.looksLikeEmail("support@richeyworks.com")).isTrue();
        assertThat(SupportGuide.looksLikeEmail("not-an-email")).isFalse();
        assertThat(SupportGuide.looksLikeEmail("a@b")).isFalse();
        assertThat(SupportGuide.looksLikeEmail("a @b.com")).isFalse();
        assertThatThrownBy(() -> SupportGuide.mailtoUri("nope", "s", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("copy payload carries To, subject, and diagnostics")
    void clipboardPayload() {
        String payload = SupportGuide.clipboardPayload(
                "support@richeyworks.com",
                SupportGuide.emailSubject("1.2.0"),
                SupportGuide.emailBody("FlowersForever version: 1.2.0"));
        assertThat(payload).contains("To: support@richeyworks.com")
                .contains("Subject: FlowersForever pilot help (1.2.0)")
                .contains("FlowersForever version: 1.2.0")
                .contains("What I was trying to do");
    }

    @Test
    @DisplayName("guidance promises same-day pilot reply without jargon")
    void plainLanguage() {
        assertThat(SupportGuide.pageGuidance()).contains("same day", "pilot", "Pacific");
        assertThat(SupportGuide.menuDescription()).containsIgnoringCase("report");
        assertThat(SupportGuide.TITLE).isEqualTo("Contact / Report a problem");
    }

    @Test
    @DisplayName("support panel copies details and opens mailto with the typed address")
    void panelActions() {
        List<String> copied = new ArrayList<>();
        AtomicReference<URI> mailed = new AtomicReference<>();
        List<String> status = new ArrayList<>();
        FarmClipboard clipboard = copied::add;
        FarmMailer mailer = mailed::set;
        SupportPanel panel = new SupportPanel(
                "old@example.com", "1.2.0",
                SupportGuide.diagnostics("1.2.0", "Linux", "6.1", "amd64", "21"),
                clipboard, mailer, status::add);

        assertThat(panel.getName()).isEqualTo("support.panel");
        setEmail(panel, "pilot@richeyworks.com");
        click(panel, "support.copy");
        assertThat(copied).hasSize(1);
        assertThat(copied.get(0)).contains("To: pilot@richeyworks.com")
                .contains("FlowersForever version: 1.2.0");
        assertThat(status.get(status.size() - 1)).isEqualTo(SupportGuide.statusCopied());

        click(panel, "support.openMail");
        assertThat(mailed.get()).isNotNull();
        assertThat(mailed.get().toString()).startsWith("mailto:pilot@richeyworks.com?");
        assertThat(status.get(status.size() - 1)).isEqualTo(SupportGuide.statusMailOpened());
    }

    @Test
    @DisplayName("open email with a bad address explains the problem and does not mail")
    void panelRejectsBadEmail() {
        AtomicReference<URI> mailed = new AtomicReference<>();
        List<String> status = new ArrayList<>();
        SupportPanel panel = new SupportPanel(
                "bad", "1.2.0", "FlowersForever version: 1.2.0",
                text -> { }, mailed::set, status::add);
        click(panel, "support.openMail");
        assertThat(mailed.get()).isNull();
        assertThat(status).containsExactly(SupportGuide.statusBadEmail());
    }

    @Test
    @DisplayName("mail failure falls back to clipboard copy")
    void mailFallbackCopies() {
        List<String> copied = new ArrayList<>();
        List<String> status = new ArrayList<>();
        SupportPanel panel = new SupportPanel(
                "support@richeyworks.com", "1.2.0", "FlowersForever version: 1.2.0",
                copied::add,
                uri -> {
                    throw new UnsupportedOperationException("no mail");
                },
                status::add);
        click(panel, "support.openMail");
        assertThat(copied).hasSize(1);
        assertThat(status).containsExactly(SupportGuide.statusMailUnavailable());
    }

    private static void setEmail(JPanel root, String value) {
        AtomicReference<JTextField> found = new AtomicReference<>();
        findField(root, "support.email", found);
        assertThat(found.get()).isNotNull();
        found.get().setText(value);
    }

    private static void click(JPanel root, String name) {
        AtomicReference<JButton> found = new AtomicReference<>();
        findButton(root, name, found);
        assertThat(found.get()).as(name).isNotNull();
        found.get().doClick();
    }

    private static void findButton(Container root, String name, AtomicReference<JButton> found) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && child instanceof JButton button) {
                found.set(button);
                return;
            }
            if (child instanceof Container container) {
                findButton(container, name, found);
            }
        }
    }

    private static void findField(Container root, String name, AtomicReference<JTextField> found) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && child instanceof JTextField field) {
                found.set(field);
                return;
            }
            if (child instanceof Container container) {
                findField(container, name, found);
            }
        }
    }
}
