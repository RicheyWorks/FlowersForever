package com.flowerfarm.gui;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JViewport;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Shared visual language for the farm-operations desktop UI.
 *
 * <p>The helpers intentionally return ordinary Swing components and use
 * FlatLaf client properties only as progressive enhancement. They therefore
 * remain safe to construct in headless tests and under another look and feel.
 */
public final class FarmUi {

    public static final int PAGE_INSET = 18;
    public static final int SECTION_GAP = 14;
    public static final int CONTROL_GAP = 8;
    public static final int TABLE_ROW_HEIGHT = 34;

    private static final String STYLE = "FlatLaf.style";
    private static final String INITIAL_FOCUS = "flowerfarm.initialFocus";
    private static final String TONE_PROPERTY = "flowerfarm.tone";
    private static final String ROLE_PROPERTY = "flowerfarm.visualRole";

    public enum Tone {
        NEUTRAL,
        INFO,
        SUCCESS,
        WARNING,
        DANGER
    }

    private FarmUi() {
    }

    /** Applies the farm brand to FlatLaf before the component tree is built. */
    public static void installThemeDefaults() {
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TabbedPane.tabHeight", 42);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.selectedBackground", surfaceColor());
        UIManager.put("TabbedPane.underlineColor", accentColor());
        UIManager.put("TabbedPane.inactiveUnderlineColor", borderColor());
        UIManager.put("TabbedPane.hoverColor", toneBackgroundColor(Tone.SUCCESS));
        UIManager.put("Table.selectionBackground",
                isDark() ? new Color(47, 91, 68) : new Color(213, 234, 220));
        UIManager.put("Table.selectionForeground",
                isDark() ? Color.WHITE : new Color(20, 62, 41));
        UIManager.put("TableHeader.background",
                isDark() ? new Color(48, 54, 50) : new Color(232, 237, 233));
        UIManager.put("TableHeader.foreground",
                isDark() ? new Color(238, 242, 239) : new Color(31, 48, 38));
        UIManager.put("Component.accentColor", accentColor());
    }

    /** Branded masthead used above every farm workflow. */
    public static JPanel brandHeader(String location, String dateText) {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setName("farm.brandHeader");
        header.setBackground(surfaceColor());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        JLabel mark = new JLabel(new FarmMarkIcon(40));
        mark.getAccessibleContext().setAccessibleName("Flowers Forever farm mark");
        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("FLOWERS FOREVER");
        name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
        name.setForeground(accentColor());
        JLabel purpose = mutedLabel("Flower farm operations");
        identity.add(name);
        identity.add(Box.createVerticalStrut(1));
        identity.add(purpose);
        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.add(mark, BorderLayout.WEST);
        left.add(identity, BorderLayout.CENTER);
        header.add(left, BorderLayout.WEST);

        JLabel locationBadge = statusBadge(location, Tone.NEUTRAL);
        JLabel date = new JLabel(dateText);
        date.setFont(date.getFont().deriveFont(Font.BOLD, 13f));
        JPanel context = toolbar(locationBadge, date);
        header.add(context, BorderLayout.EAST);
        header.getAccessibleContext().setAccessibleName("Flowers Forever application header");
        return header;
    }

    /** Compact semantic mark for the left-side farm workflow navigation. */
    public static Icon navigationIcon(String title) {
        String code = switch (title) {
            case "Dashboard" -> "TD";
            case "Inventory" -> "ST";
            case "Add Item" -> "+";
            case "Harvest Log" -> "HV";
            case "Season Planner" -> "SP";
            case "Customers & Orders" -> "CO";
            case "Market Day" -> "MK";
            case "Trend Analysis" -> "TR";
            case "Activity History" -> "AC";
            case "Knowledge Log" -> "KL";
            case "Reports" -> "RP";
            case "Rose Varieties" -> "RV";
            case "Rose Visualizer" -> "RO";
            case "Pricing Info" -> "$";
            case "Reference & Planning" -> "RF";
            case "Irrigation & Care" -> "IR";
            default -> "FF";
        };
        Tone tone = switch (title) {
            case "Harvest Log", "Rose Varieties", "Rose Visualizer" -> Tone.DANGER;
            case "Market Day", "Pricing Info" -> Tone.WARNING;
            case "Reference & Planning", "Knowledge Log" -> Tone.INFO;
            case "Customers & Orders", "Irrigation & Care" -> Tone.INFO;
            case "Dashboard", "Inventory", "Add Item", "Season Planner" -> Tone.SUCCESS;
            default -> Tone.NEUTRAL;
        };
        return new NavigationIcon(code, toneBackgroundColor(tone), toneForegroundColor(tone));
    }

    /** Creates the standard padded root used by feature tabs. */
    public static JPanel page() {
        JPanel page = new JPanel(new BorderLayout(0, SECTION_GAP));
        page.setBackground(pageBackgroundColor());
        page.setBorder(BorderFactory.createEmptyBorder(
                PAGE_INSET, PAGE_INSET, PAGE_INSET, PAGE_INSET));
        page.setName("farm.page");
        return page;
    }

    /** Re-applies custom farm surfaces after a runtime light/dark theme switch. */
    public static void refreshTheme(Component root) {
        if (root instanceof JComponent component) {
            if ("farm.page".equals(component.getName())) {
                component.setBackground(pageBackgroundColor());
            } else if ("farm.section".equals(component.getName())) {
                component.setBackground(surfaceColor());
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderColor()),
                        BorderFactory.createEmptyBorder(12, 12, 12, 12)));
            } else if ("farm.metricCard".equals(component.getName())) {
                Tone tone = componentTone(component);
                component.setBackground(surfaceColor());
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(3, 0, 0, 0,
                                toneForegroundColor(tone)),
                        BorderFactory.createEmptyBorder(13, 15, 14, 15)));
            } else if ("farm.insightCard".equals(component.getName())) {
                Tone tone = componentTone(component);
                component.setBackground(toneBackgroundColor(tone));
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(toneBorder(tone)),
                        BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            }
            Tone tone = componentTone(component);
            Object role = component.getClientProperty(ROLE_PROPERTY);
            if ("eyebrow".equals(role) || "insight-message".equals(role)) {
                component.setForeground(toneForegroundColor(tone));
            } else if ("metric-value".equals(role)) {
                component.setForeground(accentColor());
            } else if ("metric-detail".equals(role)) {
                component.setForeground(mutedColor());
            }
        }
        if (root instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                refreshTheme(child);
            }
        }
    }

    /** Builds a consistent title/subtitle row with optional right-side actions. */
    public static JPanel pageHeader(String title, String subtitle, JComponent actions) {
        JPanel header = new JPanel(new BorderLayout(CONTROL_GAP, 0));
        header.setOpaque(false);
        header.setName("farm.pageHeader");
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setName("farm.pageTitle");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.getAccessibleContext().setAccessibleName(title);
        copy.add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subtitleLabel = mutedLabel(subtitle);
            subtitleLabel.setName("farm.pageSubtitle");
            subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(13f));
            subtitleLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
            copy.add(subtitleLabel);
        } else {
            copy.add(Box.createVerticalStrut(12));
        }
        header.add(copy, BorderLayout.CENTER);
        if (actions != null) {
            header.add(actions, BorderLayout.EAST);
        }
        header.getAccessibleContext().setAccessibleName(title + " page header");
        return header;
    }

    /** Wraps content in a quiet surface with a strong section label. */
    public static JPanel section(String title, String description, JComponent content) {
        JPanel section = new JPanel(new BorderLayout(0, CONTROL_GAP));
        section.setName("farm.section");
        section.setOpaque(true);
        section.setBackground(surfaceColor());
        section.putClientProperty(STYLE, "arc: 16");
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        heading.add(label);
        if (description != null && !description.isBlank()) {
            JLabel help = mutedLabel(description);
            help.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            heading.add(help);
        }
        section.add(heading, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        section.getAccessibleContext().setAccessibleName(title + " section");
        return section;
    }

    /** Left-aligned, wrapping-friendly action or filter row. */
    public static JPanel toolbar(Component... components) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, CONTROL_GAP, 0));
        toolbar.setOpaque(false);
        toolbar.setName("farm.toolbar");
        for (Component component : components) {
            if (component != null) {
                toolbar.add(component);
            }
        }
        return toolbar;
    }

    public static JButton primaryButton(String text) {
        return button(text, "background: #176b4a; foreground: #ffffff; font: +1 bold");
    }

    public static JButton secondaryButton(String text) {
        return button(text, "font: +1");
    }

    public static JButton dangerButton(String text) {
        return button(text, "foreground: $Actions.Red; font: +1 bold");
    }

    private static JButton button(String text, String style) {
        JButton button = new JButton(text);
        button.putClientProperty(STYLE, style);
        button.setMargin(new Insets(7, 12, 7, 12));
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }

    public static JTextField searchField(String accessibleName, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("farm.searchField");
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.putClientProperty("JTextField.showClearButton", true);
        field.getAccessibleContext().setAccessibleName(accessibleName);
        field.getAccessibleContext().setAccessibleDescription(placeholder);
        return field;
    }

    /** Marks the preferred keyboard landing point when a workflow is opened. */
    public static <T extends JComponent> T initialFocus(T component) {
        if (component != null) {
            component.putClientProperty(INITIAL_FOCUS, Boolean.TRUE);
        }
        return component;
    }

    /** Moves focus to the visible marked target, or the first usable form control. */
    public static Component focusFirstInteractive(Container root) {
        Component target = findFocusTarget(root, true);
        if (target == null) {
            target = findFocusTarget(root, false);
        }
        if (target != null) {
            target.requestFocusInWindow();
        }
        return target;
    }

    private static Component findFocusTarget(Component component, boolean markedOnly) {
        if (!component.isVisible() || !component.isEnabled()) {
            return null;
        }
        if (component instanceof JComponent swingComponent
                && Boolean.TRUE.equals(swingComponent.getClientProperty(INITIAL_FOCUS))) {
            return component;
        }
        if (!markedOnly && (component instanceof JTextField
                || component instanceof JComboBox<?>
                || component instanceof AbstractButton
                || component instanceof JTable)) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component target = findFocusTarget(child, markedOnly);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    /** Applies shared density, selection, and accessibility defaults to a data table. */
    public static void configureTable(JTable table, String accessibleName) {
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setGridColor(borderColor());
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setSurrendersFocusOnKeystroke(true);
        table.setToolTipText("Use the arrow keys to move between rows. Actions apply to the selected row.");
        table.getAccessibleContext().setAccessibleName(accessibleName);
        table.getAccessibleContext().setAccessibleDescription(
                "Use the arrow keys to move between rows. Actions apply to the selected row.");
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setReorderingAllowed(false);
            header.setOpaque(true);
            header.setBackground(color("TableHeader.background",
                    isDark() ? new Color(48, 54, 50) : new Color(232, 237, 233)));
            header.setForeground(color("TableHeader.foreground",
                    isDark() ? new Color(238, 242, 239) : new Color(31, 48, 38)));
            header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
            header.setBorder(BorderFactory.createMatteBorder(
                    0, 0, 1, 0, borderColor()));
            header.setPreferredSize(new Dimension(
                    Math.max(1, header.getPreferredSize().width), 34));
            header.getAccessibleContext()
                    .setAccessibleName(accessibleName + " column headers");
        }
    }

    public static JScrollPane tableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setName("farm.tableScroll");
        scroll.setBorder(BorderFactory.createLineBorder(borderColor()));
        scroll.getViewport().setBackground(surfaceColor());
        scroll.getAccessibleContext().setAccessibleName(
                table.getAccessibleContext().getAccessibleName() + " scroll area");
        return scroll;
    }

    public static JTextArea displayArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(area.getFont().deriveFont(13f));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(mutedColor());
        label.setFont(label.getFont().deriveFont(12f));
        return label;
    }

    /** Small editorial label used above a primary metric or recommendation. */
    public static JLabel eyebrowLabel(String text, Tone tone) {
        JLabel label = new JLabel(text == null ? "" : text.toUpperCase(java.util.Locale.ROOT));
        label.setName("farm.eyebrow");
        label.putClientProperty(TONE_PROPERTY, tone == null ? Tone.NEUTRAL : tone);
        label.putClientProperty(ROLE_PROPERTY, "eyebrow");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setForeground(toneForegroundColor(tone == null ? Tone.NEUTRAL : tone));
        label.getAccessibleContext().setAccessibleName(text);
        return label;
    }

    /**
     * A quiet, left-aligned metric card shared by dashboards and analysis screens.
     * The caller owns the value/detail labels so their text can update in place.
     */
    public static JPanel metricCard(String title, JLabel valueLabel,
                                    JLabel detailLabel, Tone tone) {
        Tone safeTone = tone == null ? Tone.NEUTRAL : tone;
        JPanel card = new JPanel();
        card.setName("farm.metricCard");
        card.putClientProperty(TONE_PROPERTY, safeTone);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(surfaceColor());
        card.putClientProperty(STYLE, "arc: 16");
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0,
                        tone == null ? borderColor() : toneForegroundColor(safeTone)),
                BorderFactory.createEmptyBorder(13, 15, 14, 15)));

        JLabel titleLabel = eyebrowLabel(title, safeTone);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));
        valueLabel.setForeground(accentColor());
        valueLabel.putClientProperty(TONE_PROPERTY, safeTone);
        valueLabel.putClientProperty(ROLE_PROPERTY, "metric-value");
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(7));
        card.add(valueLabel);
        if (detailLabel != null) {
            detailLabel.setFont(detailLabel.getFont().deriveFont(12f));
            detailLabel.setForeground(mutedColor());
            detailLabel.putClientProperty(TONE_PROPERTY, safeTone);
            detailLabel.putClientProperty(ROLE_PROPERTY, "metric-detail");
            detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(Box.createVerticalStrut(5));
            card.add(detailLabel);
        }
        card.getAccessibleContext().setAccessibleName(title + " metric");
        return card;
    }

    /** A compact narrative card for explaining what changed and what to do next. */
    public static JPanel insightCard(String heading, JLabel message, Tone tone) {
        Tone safeTone = tone == null ? Tone.NEUTRAL : tone;
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setName("farm.insightCard");
        card.putClientProperty(TONE_PROPERTY, safeTone);
        card.setOpaque(true);
        card.setBackground(toneBackgroundColor(safeTone));
        card.putClientProperty(STYLE, "arc: 14");
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(toneBorder(safeTone)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.add(eyebrowLabel(heading, safeTone), BorderLayout.NORTH);
        message.setFont(message.getFont().deriveFont(12f));
        message.setForeground(toneForegroundColor(safeTone));
        message.putClientProperty(TONE_PROPERTY, safeTone);
        message.putClientProperty(ROLE_PROPERTY, "insight-message");
        card.add(message, BorderLayout.CENTER);
        card.getAccessibleContext().setAccessibleName(heading);
        return card;
    }

    /** Short, accessible guidance placed next to a form or destructive action. */
    public static JLabel formHint(String text) {
        JLabel hint = mutedLabel(text);
        hint.setName("farm.formHint");
        hint.getAccessibleContext().setAccessibleName("Form guidance");
        hint.getAccessibleContext().setAccessibleDescription(text);
        return hint;
    }

    /** Consistent accessible content for destructive or inventory-changing dialogs. */
    public static JPanel confirmationPanel(String heading, String consequence, Tone tone) {
        JPanel content = new JPanel(new BorderLayout(0, CONTROL_GAP));
        content.setName("farm.confirmationPanel");
        content.setBackground(surfaceColor());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel badge = statusBadge(heading, tone == null ? Tone.WARNING : tone);
        badge.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(badge, BorderLayout.NORTH);

        JTextArea detail = new JTextArea(consequence == null ? "" : consequence, 4, 42);
        detail.setName("farm.confirmationDetail");
        detail.setEditable(false);
        detail.setFocusable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);
        detail.setFont(detail.getFont().deriveFont(13f));
        detail.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        detail.getAccessibleContext().setAccessibleName("Confirmation consequences");
        detail.getAccessibleContext().setAccessibleDescription(detail.getText());
        content.add(detail, BorderLayout.CENTER);
        content.getAccessibleContext().setAccessibleName(heading + " confirmation");
        return content;
    }

    public static JLabel statusBadge(String text, Tone tone) {
        JLabel badge = new JLabel(text, SwingConstants.CENTER);
        badge.setName("farm.statusBadge");
        styleStatusBadge(badge, tone);
        badge.getAccessibleContext().setAccessibleName(text + " status");
        return badge;
    }

    /** Restyles an existing badge after a session or state transition. */
    public static void styleStatusBadge(JLabel badge, Tone tone) {
        badge.setOpaque(true);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
        badge.setForeground(toneForegroundColor(tone));
        badge.setBackground(toneBackgroundColor(tone));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(toneBorder(tone)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        String text = badge.getText();
        badge.getAccessibleContext().setAccessibleName(
                (text == null || text.isBlank() ? "Application" : text) + " status");
    }

    /** Updates both the visual badge and its screen-reader announcement. */
    public static void updateStatusBadge(JLabel badge, String text, Tone tone) {
        if (badge == null) {
            return;
        }
        String safe = text == null || text.isBlank() ? "Ready" : text;
        badge.setText(safe);
        styleStatusBadge(badge, tone);
        badge.setToolTipText(safe);
        badge.getAccessibleContext().setAccessibleDescription(safe);
    }

    /** Marks a text field invalid, explains why, and moves keyboard focus to it. */
    public static void markFieldError(JTextField field, String message) {
        if (field == null) {
            return;
        }
        String safe = message == null || message.isBlank()
                ? "Check this value and try again." : message;
        field.putClientProperty("JComponent.outline", "error");
        field.setToolTipText(safe);
        field.getAccessibleContext().setAccessibleDescription(safe);
        revealAndFocus(field);
        field.selectAll();
    }

    /** Reveals a control through any enclosing viewport and makes it the keyboard target. */
    public static void revealAndFocus(JComponent component) {
        if (component == null) {
            return;
        }
        Runnable reveal = () -> {
            Rectangle localBounds = new Rectangle(
                    0, 0, Math.max(1, component.getWidth()),
                    Math.max(1, component.getHeight()));
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(
                    JViewport.class, component);
            if (viewport != null && viewport.getView() instanceof JComponent view) {
                Rectangle viewBounds = SwingUtilities.convertRectangle(
                        component, localBounds, view);
                view.scrollRectToVisible(viewBounds);
            } else {
                component.scrollRectToVisible(localBounds);
            }
            component.requestFocusInWindow();
        };
        reveal.run();
        // Validation can arrive before a compact workspace has completed layout.
        SwingUtilities.invokeLater(reveal);
    }

    /** Clears validation styling while preserving the field's accessible name. */
    public static void clearFieldError(JTextField field) {
        if (field == null) {
            return;
        }
        field.putClientProperty("JComponent.outline", null);
        field.setToolTipText(null);
        field.getAccessibleContext().setAccessibleDescription(null);
    }

    public static JPanel emptyState(String title, String guidance) {
        JPanel state = new JPanel(new GridBagLayout());
        state.setOpaque(false);
        state.setName("farm.emptyState");
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel guidanceLabel = mutedLabel(guidance);
        guidanceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        copy.add(titleLabel);
        copy.add(Box.createVerticalStrut(5));
        copy.add(guidanceLabel);
        state.add(copy, new GridBagConstraints());
        state.getAccessibleContext().setAccessibleName(title);
        state.getAccessibleContext().setAccessibleDescription(guidance);
        return state;
    }

    /** Theme-aware stock tint used by tables without hard-coding light-only colors. */
    public static Color stockBackground(int quantity, Color normalBackground) {
        if (quantity <= 5) {
            return blend(normalBackground, toneBackgroundColor(Tone.DANGER), 0.72);
        }
        if (quantity <= 15) {
            return blend(normalBackground, toneBackgroundColor(Tone.WARNING), 0.72);
        }
        return normalBackground;
    }

    /** Produces a useful user-facing error without leaking a blank/null message. */
    public static String actionableError(String action, Throwable error) {
        String detail = error == null ? null : error.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "The application did not provide additional details.";
        }
        return action + " could not be completed. " + detail
                + " Check the entered values and try again.";
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                BorderFactory.createEmptyBorder(16, 16, 16, 16));
    }

    public static Color accentColor() {
        return isDark() ? new Color(120, 207, 154) : new Color(23, 107, 74);
    }

    /** Floral accent reserved for forecast points, highlights, and brand moments. */
    public static Color roseAccentColor() {
        return isDark() ? new Color(232, 139, 151) : new Color(182, 76, 93);
    }

    public static Color mutedColor() {
        return isDark() ? new Color(180, 190, 183) : new Color(96, 112, 102);
    }

    public static Color borderColor() {
        return isDark() ? new Color(82, 92, 86) : new Color(220, 226, 221);
    }

    public static Color surfaceColor() {
        return isDark() ? new Color(47, 53, 49) : new Color(255, 255, 252);
    }

    public static Color pageBackgroundColor() {
        return isDark() ? new Color(35, 41, 37) : new Color(247, 244, 238);
    }

    private static Tone componentTone(JComponent component) {
        Object tone = component.getClientProperty(TONE_PROPERTY);
        return tone instanceof Tone savedTone ? savedTone : Tone.NEUTRAL;
    }

    public static Color toneForegroundColor(Tone tone) {
        if (isDark()) {
            return switch (tone) {
                case SUCCESS -> new Color(164, 224, 176);
                case WARNING -> new Color(247, 211, 137);
                case DANGER -> new Color(255, 177, 166);
                case INFO -> new Color(171, 207, 247);
                case NEUTRAL -> new Color(210, 216, 212);
            };
        }
        return switch (tone) {
            case SUCCESS -> new Color(23, 103, 55);
            case WARNING -> new Color(125, 82, 10);
            case DANGER -> new Color(157, 42, 35);
            case INFO -> new Color(35, 86, 143);
            case NEUTRAL -> new Color(73, 83, 77);
        };
    }

    public static Color toneBackgroundColor(Tone tone) {
        Color base = switch (tone) {
            case SUCCESS -> new Color(86, 169, 115);
            case WARNING -> new Color(218, 163, 68);
            case DANGER -> new Color(204, 87, 91);
            case INFO -> new Color(79, 132, 186);
            case NEUTRAL -> new Color(132, 145, 136);
        };
        return blend(color("Panel.background", Color.WHITE), base, isDark() ? 0.28 : 0.16);
    }

    private static Color toneBorder(Tone tone) {
        return blend(toneBackgroundColor(tone), toneForegroundColor(tone), 0.35);
    }

    private static boolean isDark() {
        Color background = color("Panel.background", Color.WHITE);
        double luminance = 0.2126 * background.getRed()
                + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue();
        return luminance < 128;
    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    private static Color blend(Color first, Color second, double secondWeight) {
        double weight = Math.max(0, Math.min(1, secondWeight));
        int red = (int) Math.round(first.getRed() * (1 - weight) + second.getRed() * weight);
        int green = (int) Math.round(first.getGreen() * (1 - weight) + second.getGreen() * weight);
        int blue = (int) Math.round(first.getBlue() * (1 - weight) + second.getBlue() * weight);
        return new Color(red, green, blue);
    }

    private static final class NavigationIcon implements Icon {
        private final String code;
        private final Color background;
        private final Color foreground;

        private NavigationIcon(String code, Color background, Color foreground) {
            this.code = code;
            this.background = background;
            this.foreground = foreground;
        }

        @Override public int getIconWidth() {
            return 26;
        }

        @Override public int getIconHeight() {
            return 26;
        }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(background);
                copy.fillRoundRect(x + 1, y + 1, 24, 24, 9, 9);
                copy.setColor(foreground);
                copy.setFont(component.getFont().deriveFont(Font.BOLD,
                        code.length() > 1 ? 9f : 13f));
                int width = copy.getFontMetrics().stringWidth(code);
                int baseline = y + 13 + copy.getFontMetrics().getAscent() / 2 - 2;
                copy.drawString(code, x + (26 - width) / 2, baseline);
            } finally {
                copy.dispose();
            }
        }
    }

    private static final class FarmMarkIcon implements Icon {
        private final int size;

        private FarmMarkIcon(int size) {
            this.size = size;
        }

        @Override public int getIconWidth() {
            return size;
        }

        @Override public int getIconHeight() {
            return size;
        }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                double scale = size / 40d;
                copy.translate(x, y);
                copy.scale(scale, scale);
                copy.setColor(isDark() ? new Color(75, 120, 91) : new Color(223, 238, 227));
                copy.fillOval(0, 0, 40, 40);
                copy.setColor(accentColor());
                copy.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                copy.drawLine(20, 30, 20, 15);
                copy.drawArc(9, 18, 12, 9, 350, 150);
                copy.drawArc(20, 14, 12, 9, 40, 150);
                copy.setColor(isDark() ? new Color(233, 142, 157) : new Color(177, 60, 82));
                copy.fillOval(15, 8, 10, 10);
                copy.fillOval(11, 11, 9, 9);
                copy.fillOval(20, 11, 9, 9);
                copy.setColor(isDark() ? new Color(247, 203, 117) : new Color(212, 149, 31));
                copy.fillOval(18, 12, 5, 5);
            } finally {
                copy.dispose();
            }
        }
    }
}
