package com.flowerfarm.support;

import com.flowerfarm.gui.FarmBrowser;
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
        URI uri = SupportGuide.mailtoUri("Help@Example.com",
                SupportGuide.emailSubject("1.2.0"),
                SupportGuide.emailBody("FlowersForever version: 1.2.0"));
        assertThat(uri.getScheme()).isEqualTo("mailto");
        assertThat(uri.toString()).startsWith("mailto:help@example.com?")
                .contains("subject=FlowersForever%20pilot%20help")
                .contains("body=What%20I%20was%20trying%20to%20do");
    }

    @Test
    @DisplayName("invalid emails and the retired bouncing default are rejected")
    void emailValidation() {
        assertThat(SupportGuide.DEFAULT_EMAIL).isBlank();
        assertThat(SupportGuide.sanitizeStoredEmail("support@richeyworks.com")).isBlank();
        assertThat(SupportGuide.sanitizeStoredEmail("Help@Example.com")).isEqualTo("help@example.com");
        assertThat(SupportGuide.looksLikeEmail("help@example.com")).isTrue();
        assertThat(SupportGuide.looksLikeEmail("not-an-email")).isFalse();
        assertThat(SupportGuide.looksLikeEmail("a@b")).isFalse();
        assertThatThrownBy(() -> SupportGuide.mailtoUri("", "s", "b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupportGuide.mailtoUri("support@richeyworks.com", "s", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("copy payload prefers GitHub Issues when no support email is set")
    void clipboardPayload() {
        String blank = SupportGuide.clipboardPayload(
                "",
                SupportGuide.emailSubject("1.2.0"),
                SupportGuide.emailBody("FlowersForever version: 1.2.0"));
        assertThat(blank).contains("GitHub Issues: " + SupportGuide.ISSUES_URL)
                .doesNotContain("To:")
                .contains("Subject: FlowersForever pilot help (1.2.0)")
                .contains("FlowersForever version: 1.2.0");

        String withTo = SupportGuide.clipboardPayload(
                "help@example.com",
                SupportGuide.emailSubject("1.2.0"),
                SupportGuide.emailBody("FlowersForever version: 1.2.0"));
        assertThat(withTo).contains("To: help@example.com")
                .contains("GitHub Issues: " + SupportGuide.ISSUES_URL);
    }

    @Test
    @DisplayName("guidance points at GitHub Issues without a bouncing default inbox")
    void plainLanguage() {
        assertThat(SupportGuide.pageGuidance()).contains("GitHub Issues", "pilot", "Pacific");
        assertThat(SupportGuide.menuDescription()).containsIgnoringCase("GitHub");
        assertThat(SupportGuide.ISSUES_URL)
                .isEqualTo("https://github.com/RicheyWorks/FlowersForever/issues/new");
        assertThat(SupportGuide.TITLE).isEqualTo("Contact / Report a problem");
    }

    @Test
    @DisplayName("support panel opens GitHub Issues and can still mailto a typed address")
    void panelActions() {
        List<String> copied = new ArrayList<>();
        AtomicReference<URI> mailed = new AtomicReference<>();
        AtomicReference<URI> browsed = new AtomicReference<>();
        List<String> status = new ArrayList<>();
        SupportPanel panel = new SupportPanel(
                "", "1.2.0",
                SupportGuide.diagnostics("1.2.0", "Linux", "6.1", "amd64", "21"),
                copied::add, mailed::set, browsed::set, status::add);

        assertThat(panel.getName()).isEqualTo("support.panel");
        click(panel, "support.openIssues");
        assertThat(copied).hasSize(1);
        assertThat(copied.get(0)).contains(SupportGuide.ISSUES_URL);
        assertThat(browsed.get()).isEqualTo(SupportGuide.issuesUri());
        assertThat(status.get(status.size() - 1)).isEqualTo(SupportGuide.statusIssuesOpened());

        setEmail(panel, "pilot@example.com");
        click(panel, "support.openMail");
        assertThat(mailed.get()).isNotNull();
        assertThat(mailed.get().toString()).startsWith("mailto:pilot@example.com?");
        assertThat(status.get(status.size() - 1)).isEqualTo(SupportGuide.statusMailOpened());
    }

    @Test
    @DisplayName("open email with no address explains GitHub Issues / copy instead")
    void panelRejectsBadEmail() {
        AtomicReference<URI> mailed = new AtomicReference<>();
        List<String> status = new ArrayList<>();
        SupportPanel panel = new SupportPanel(
                "", "1.2.0", "FlowersForever version: 1.2.0",
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
                "help@example.com", "1.2.0", "FlowersForever version: 1.2.0",
                copied::add,
                uri -> {
                    throw new UnsupportedOperationException("no mail");
                },
                status::add);
        click(panel, "support.openMail");
        assertThat(copied).hasSize(1);
        assertThat(status).containsExactly(SupportGuide.statusMailUnavailable());
    }

    @Test
    @DisplayName("browser failure still leaves GitHub Issues details on the clipboard")
    void issuesFallbackCopies() {
        List<String> copied = new ArrayList<>();
        List<String> status = new ArrayList<>();
        FarmBrowser broken = uri -> {
            throw new UnsupportedOperationException("no browser");
        };
        SupportPanel panel = new SupportPanel(
                "", "1.2.0", "FlowersForever version: 1.2.0",
                copied::add, uri -> { }, broken, status::add);
        click(panel, "support.openIssues");
        assertThat(copied).hasSize(1);
        assertThat(copied.get(0)).contains(SupportGuide.ISSUES_URL);
        assertThat(status).containsExactly(SupportGuide.statusIssuesUnavailable());
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
