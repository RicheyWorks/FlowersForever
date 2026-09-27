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
import javax.swing.text.JTextComponent;
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
import java.awt.GradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

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
    private static final String SURFACE_PROPERTY = "flowerfarm.surface";

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
        UIManager.put("TabbedPane.selectedBackground",
                blend(surfaceColor(), toneBackgroundColor(Tone.SUCCESS), 0.72));
        UIManager.put("TabbedPane.underlineColor", accentColor());
        UIManager.put("TabbedPane.inactiveUnderlineColor", borderColor());
        UIManager.put("TabbedPane.hoverColor", toneBackgroundColor(Tone.INFO));
        UIManager.put("TabbedPane.focusColor", toneBackgroundColor(Tone.WARNING));
        UIManager.put("Table.background", surfaceColor());
        UIManager.put("Table.alternateRowColor",
                blend(surfaceColor(), toneBackgroundColor(Tone.INFO), 0.34));
        UIManager.put("Table.gridColor", borderColor());
        UIManager.put("Table.selectionBackground",
                isDark() ? new Color(45, 103, 75) : new Color(202, 232, 213));
        UIManager.put("Table.selectionForeground",
                isDark() ? Color.WHITE : new Color(20, 62, 41));
        UIManager.put("TableHeader.background",
                blend(surfaceColor(), toneBackgroundColor(Tone.SUCCESS), 0.72));
        UIManager.put("TableHeader.foreground",
                isDark() ? new Color(238, 242, 239) : new Color(31, 48, 38));
        UIManager.put("TextField.background", surfaceColor());
        UIManager.put("TextField.inactiveBackground", surfaceColor());
        UIManager.put("FormattedTextField.background", surfaceColor());
        UIManager.put("FormattedTextField.inactiveBackground", surfaceColor());
        UIManager.put("TextArea.background", surfaceColor());
        UIManager.put("TextArea.inactiveBackground", surfaceColor());
        UIManager.put("ComboBox.background", surfaceColor());
        UIManager.put("ComboBox.buttonBackground", surfaceColor());
        UIManager.put("ComboBox.editableBackground", surfaceColor());
        UIManager.put("ScrollBar.thumb", blend(borderColor(), accentColor(), 0.42));
        UIManager.put("ScrollBar.hoverThumbColor", accentColor());
        UIManager.put("Component.accentColor", accentColor());
    }

    /** Branded masthead used above every farm workflow. */
    public static JPanel brandHeader(String location, String dateText) {
        return brandHeader("FlowersForever", "Cut-flower farm operations", location, dateText);
    }

    /** Customer-owned farm identity with quiet FlowersForever attribution. */
    public static JPanel brandHeader(String farmName, String farmTagline,
                                     String location, String dateText) {
        JPanel header = new FloralWashPanel(new BorderLayout(16, 0), true);
        header.setName("farm.brandHeader");
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        JLabel mark = new JLabel(new FarmMarkIcon(40));
        mark.getAccessibleContext().setAccessibleName("FlowersForever farm mark");
        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        String safeFarmName = farmName == null || farmName.isBlank()
                ? "YOUR FLOWER FARM" : farmName.trim().toUpperCase(java.util.Locale.ROOT);
        JLabel name = new JLabel(safeFarmName);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
        name.setForeground(accentColor());
        String safeTagline = farmTagline == null || farmTagline.isBlank()
                ? "Powered by FlowersForever" : farmTagline.trim() + " · Powered by FlowersForever";
        JLabel purpose = mutedLabel(safeTagline);
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
        header.getAccessibleContext().setAccessibleName(
                safeFarmName + " · FlowersForever application header");
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
            case "PNW Plant Guide" -> "PN";
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
            case "Reference & Planning", "Knowledge Log", "PNW Plant Guide" -> Tone.INFO;
            case "Customers & Orders", "Irrigation & Care" -> Tone.INFO;
            case "Dashboard", "Inventory", "Add Item", "Season Planner" -> Tone.SUCCESS;
            default -> Tone.NEUTRAL;
        };
        return new NavigationIcon(code, toneBackgroundColor(tone), toneForegroundColor(tone));
    }

    /** Creates the standard padded root used by feature tabs. */
    public static JPanel page() {
        JPanel page = new JPanel(new BorderLayout(0, SECTION_GAP));
        page.setBorder(BorderFactory.createEmptyBorder(
                PAGE_INSET, PAGE_INSET, PAGE_INSET, PAGE_INSET));
        page.setName("farm.page");
        return pageSurface(page);
    }

    /** Marks a custom workspace root so it follows runtime theme changes. */
    public static <T extends JComponent> T pageSurface(T component) {
        component.putClientProperty(SURFACE_PROPERTY, "page");
        component.setBackground(pageBackgroundColor());
        return component;
    }

    /** Re-applies custom farm surfaces after a runtime light/dark theme switch. */
    public static void refreshTheme(Component root) {
        if (root instanceof JComponent component) {
            if ("farm.page".equals(component.getName())
                    || "page".equals(component.getClientProperty(SURFACE_PROPERTY))) {
                component.setBackground(pageBackgroundColor());
            } else if ("farm.statusBar".equals(component.getName())) {
                component.setBackground(surfaceColor());
                component.setBorder(BorderFactory.createMatteBorder(
                        1, 0, 0, 0, borderColor()));
            } else if ("farm.connectorScroll".equals(component.getName())) {
                component.setBorder(BorderFactory.createMatteBorder(
                        1, 0, 0, 0, borderColor()));
            } else if ("farm.brandHeader".equals(component.getName())) {
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
                        BorderFactory.createEmptyBorder(12, 18, 12, 18)));
                component.repaint();
            } else if ("farm.pageHeader".equals(component.getName())) {
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                blend(borderColor(), roseAccentColor(), 0.22)),
                        BorderFactory.createEmptyBorder(0, 12, 5, 12)));
                component.repaint();
            } else if ("farm.section".equals(component.getName())) {
                Tone tone = componentTone(component);
                component.setBackground(sectionBackground(tone));
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderColor()),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(3, 0, 0, 0,
                                        toneForegroundColor(tone)),
                                BorderFactory.createEmptyBorder(10, 12, 12, 12))));
            } else if ("farm.metricCard".equals(component.getName())) {
                Tone tone = componentTone(component);
                component.setBackground(metricBackground(tone));
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(toneBorder(tone)),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(4, 0, 0, 0,
                                        toneForegroundColor(tone)),
                                BorderFactory.createEmptyBorder(11, 14, 13, 14))));
            } else if ("farm.insightCard".equals(component.getName())) {
                Tone tone = componentTone(component);
                component.setBackground(toneBackgroundColor(tone));
                component.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(toneBorder(tone)),
                        BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            } else if ("farm.displayArea".equals(component.getName())) {
                component.setBackground(surfaceColor());
                component.setForeground(controlForegroundColor());
            }
            Tone tone = componentTone(component);
            Object role = component.getClientProperty(ROLE_PROPERTY);
            if ("button-primary".equals(role)) {
                component.putClientProperty(STYLE, primaryButtonStyle());
            } else if ("button-secondary".equals(role)) {
                component.putClientProperty(STYLE, secondaryButtonStyle());
            } else if ("button-danger".equals(role)) {
                component.putClientProperty(STYLE, dangerButtonStyle());
            } else if ("eyebrow".equals(role) || "insight-message".equals(role)
                    || "section-title".equals(role)) {
                component.setForeground(toneForegroundColor(tone));
            } else if ("metric-value".equals(role)) {
                component.setForeground(toneForegroundColor(tone));
            } else if ("metric-detail".equals(role)) {
                component.setForeground(mutedColor());
            } else if ("muted".equals(role)) {
                component.setForeground(mutedColor());
            }
            if (component instanceof JTextComponent textComponent) {
                refreshTextComponentTheme(textComponent);
            }
            if (component instanceof JTable table) {
                refreshTableTheme(table);
            }
            if (component instanceof JScrollPane scrollPane
                    && "farm.tableScroll".equals(scrollPane.getName())) {
                scrollPane.setBorder(BorderFactory.createLineBorder(borderColor()));
            }
            if (component instanceof JViewport viewport
                    && (viewport.getView() instanceof JTable
                    || viewport.getView() instanceof JTextComponent)) {
                viewport.setBackground(surfaceColor());
            }
            if (component instanceof JComboBox<?> comboBox) {
                comboBox.setBackground(surfaceColor());
                comboBox.setForeground(controlForegroundColor());
                Component editor = comboBox.getEditor().getEditorComponent();
                if (editor != null) {
                    editor.setBackground(surfaceColor());
                    editor.setForeground(controlForegroundColor());
                }
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
        JPanel header = new FloralWashPanel(new BorderLayout(CONTROL_GAP, 0), false);
        header.setName("farm.pageHeader");
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        blend(borderColor(), roseAccentColor(), 0.22)),
                BorderFactory.createEmptyBorder(0, 12, 5, 12)));

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
        Tone tone = decorativeTone(title);
        JPanel section = new JPanel(new BorderLayout(0, CONTROL_GAP));
        section.setName("farm.section");
        section.putClientProperty(TONE_PROPERTY, tone);
        section.setOpaque(true);
        section.setBackground(sectionBackground(tone));
        section.putClientProperty(STYLE, "arc: 16");
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(3, 0, 0, 0,
                                toneForegroundColor(tone)),
                        BorderFactory.createEmptyBorder(10, 12, 12, 12))));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setForeground(toneForegroundColor(tone));
        label.putClientProperty(TONE_PROPERTY, tone);
        label.putClientProperty(ROLE_PROPERTY, "section-title");
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
        JButton button = button(text, primaryButtonStyle());
        button.putClientProperty(ROLE_PROPERTY, "button-primary");
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = button(text, secondaryButtonStyle());
        button.putClientProperty(ROLE_PROPERTY, "button-secondary");
        return button;
    }

    public static JButton dangerButton(String text) {
        JButton button = button(text, dangerButtonStyle());
        button.putClientProperty(ROLE_PROPERTY, "button-danger");
        return button;
    }

    private static String primaryButtonStyle() {
        return isDark()
                ? "background: #287a55; hoverBackground: #34966b; pressedBackground: #1d6042; "
                        + "foreground: #ffffff; borderColor: #52a77c; font: +1 bold"
                : "background: #176f4d; hoverBackground: #21845e; pressedBackground: #10583c; "
                        + "foreground: #ffffff; borderColor: #176f4d; font: +1 bold";
    }

    private static String secondaryButtonStyle() {
        return isDark()
                ? "background: #344b60; hoverBackground: #405f79; foreground: #d7eaff; "
                        + "borderColor: #587b99; font: +1"
                : "background: #e9f2fb; hoverBackground: #d9eafb; foreground: #23568f; "
                        + "borderColor: #a9c8e7; font: +1";
    }

    private static String dangerButtonStyle() {
        return isDark()
                ? "background: #643b43; hoverBackground: #7a4650; foreground: #ffd6d8; "
                        + "borderColor: #a45f69; font: +1 bold"
                : "background: #f9e8ea; hoverBackground: #f5d8dc; foreground: #a32f42; "
                        + "borderColor: #dfa6af; font: +1 bold";
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

    /** Keep keyboard targets visible in a completed, scrollable workflow. */
    public static void revealFocusedControls(JComponent root) {
        if ((root instanceof AbstractButton || root instanceof JTextComponent
                || root instanceof JComboBox<?> || root instanceof JTable)
                && !Boolean.TRUE.equals(root.getClientProperty("flowerfarm.revealFocus"))) {
            root.putClientProperty("flowerfarm.revealFocus", Boolean.TRUE);
            root.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) {
                    JComponent target = root;
                    // Reveal an inner reader/table without changing its own scroll position.
                    if (root.getParent() instanceof JViewport viewport
                            && viewport.getParent() instanceof JScrollPane scroll) {
                        target = scroll;
                    }
                    target.scrollRectToVisible(new Rectangle(0, 0,
                            target.getWidth(), target.getHeight()));
                }
            });
        }
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent component) {
                revealFocusedControls(component);
            }
        }
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
        refreshTableTheme(table);
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
            header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
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
        area.setName("farm.displayArea");
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(area.getFont().deriveFont(13f));
        area.setBackground(surfaceColor());
        area.setForeground(controlForegroundColor());
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(ROLE_PROPERTY, "muted");
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
        card.setBackground(metricBackground(safeTone));
        card.putClientProperty(STYLE, "arc: 16");
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(toneBorder(safeTone)),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(4, 0, 0, 0,
                                tone == null ? borderColor() : toneForegroundColor(safeTone)),
                        BorderFactory.createEmptyBorder(11, 14, 13, 14))));

        JLabel titleLabel = eyebrowLabel(title, safeTone);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));
        valueLabel.setForeground(toneForegroundColor(safeTone));
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

    /** A comfortable, clearly labelled entry target for everyday farm data. */
    public static <T extends JComponent> T entryControl(T field, String placeholder, String guidance) {
        field.setFont(field.getFont().deriveFont(15f));
        Dimension preferred = field.getPreferredSize();
        field.setPreferredSize(new Dimension(preferred.width, Math.max(36, preferred.height)));
        field.setMinimumSize(new Dimension(60, 36));
        if (field instanceof JTextField text) {
            text.putClientProperty("JTextField.placeholderText", placeholder);
            text.setMargin(new Insets(5, 9, 5, 9));
        }
        field.putClientProperty("flowerfarm.fieldGuidance", guidance);
        clearControlError(field);
        return field;
    }

    /** Keeps a field label close to its value and preserves keyboard mnemonics. */
    public static JPanel entryField(String text, JComponent field, JComponent labelTarget, char mnemonic) {
        JPanel group = new JPanel(new BorderLayout(0, 4));
        group.setOpaque(false);
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setLabelFor(labelTarget);
        if (mnemonic != '\0') {
            label.setDisplayedMnemonic(mnemonic);
        }
        group.add(label, BorderLayout.NORTH);
        group.add(field, BorderLayout.CENTER);
        return group;
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

    /** Full-width status text for a dedicated row, wrapping without changing its accessible text. */
    public static JLabel wrappingStatusBadge(String text, Tone tone) {
        JLabel badge = new WrappingStatusLabel(text);
        badge.setName("farm.statusBadge");
        styleStatusBadge(badge, tone);
        return badge;
    }

    private static final class WrappingStatusLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        private WrappingStatusLabel(String text) {
            super(text, SwingConstants.LEFT);
        }

        @Override public Dimension getPreferredSize() {
            int available = getParent() == null || getParent().getWidth() <= 0 ? 640
                    : getParent().getWidth() - getParent().getInsets().left
                    - getParent().getInsets().right;
            available = Math.max(120, available);
            int textWidth = Math.max(80, available - getInsets().left - getInsets().right);
            String plain = getText() == null ? "" : getText();
            String escaped = plain.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;");
            javax.swing.plaf.basic.BasicHTML.updateRenderer(this, "<html>" + escaped + "</html>");
            javax.swing.text.View content = (javax.swing.text.View)
                    getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            content.setSize(textWidth, 0);
            int height = (int) Math.ceil(content.getPreferredSpan(javax.swing.text.View.Y_AXIS));
            return new Dimension(available, height + getInsets().top + getInsets().bottom);
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
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
        clearControlError(field);
    }

    /** Restore normal field guidance when a validation error has been corrected. */
    public static void clearControlError(JComponent field) {
        if (field == null) {
            return;
        }
        field.putClientProperty("JComponent.outline", null);
        Object guidance = field.getClientProperty("flowerfarm.fieldGuidance");
        String text = guidance instanceof String value ? value : null;
        field.setToolTipText(text);
        field.getAccessibleContext().setAccessibleDescription(text);
    }

    public static JPanel emptyState(String title, String guidance) {
        return emptyStateView(title, guidance).component();
    }

    /** Builds an empty state whose copy can follow first-run and filtered-result context. */
    public static EmptyStateView emptyStateView(String title, String guidance) {
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
        return new EmptyStateView(state, titleLabel, guidanceLabel);
    }

    /** Mutable copy handles for one reusable CardLayout empty state. */
    public record EmptyStateView(JPanel component, JLabel title, JLabel guidance) {
        public void updateCopy(String nextTitle, String nextGuidance) {
            title.setText(nextTitle);
            guidance.setText(nextGuidance);
            component.getAccessibleContext().setAccessibleName(nextTitle);
            component.getAccessibleContext().setAccessibleDescription(nextGuidance);
        }
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
        return isDark() ? new Color(126, 218, 164) : new Color(20, 112, 72);
    }

    /** Floral accent reserved for forecast points, highlights, and brand moments. */
    public static Color roseAccentColor() {
        return isDark() ? new Color(240, 145, 160) : new Color(190, 67, 86);
    }

    public static Color mutedColor() {
        return isDark() ? new Color(180, 190, 183) : new Color(96, 112, 102);
    }

    public static Color borderColor() {
        return isDark() ? new Color(82, 99, 89) : new Color(211, 222, 214);
    }

    public static Color surfaceColor() {
        return isDark() ? new Color(45, 54, 48) : new Color(255, 255, 251);
    }

    public static Color pageBackgroundColor() {
        return isDark() ? new Color(32, 42, 35) : new Color(247, 243, 236);
    }

    private static Tone componentTone(JComponent component) {
        Object tone = component.getClientProperty(TONE_PROPERTY);
        return tone instanceof Tone savedTone ? savedTone : Tone.NEUTRAL;
    }

    public static Color toneForegroundColor(Tone tone) {
        if (isDark()) {
            return switch (tone) {
                case SUCCESS -> new Color(166, 232, 185);
                case WARNING -> new Color(250, 214, 137);
                case DANGER -> new Color(255, 178, 189);
                case INFO -> new Color(177, 214, 252);
                case NEUTRAL -> new Color(210, 216, 212);
            };
        }
        return switch (tone) {
            case SUCCESS -> new Color(20, 112, 62);
            case WARNING -> new Color(143, 86, 6);
            case DANGER -> new Color(171, 48, 66);
            case INFO -> new Color(37, 91, 157);
            case NEUTRAL -> new Color(73, 83, 77);
        };
    }

    public static Color toneBackgroundColor(Tone tone) {
        Color base = switch (tone) {
            case SUCCESS -> new Color(72, 177, 111);
            case WARNING -> new Color(229, 169, 55);
            case DANGER -> new Color(215, 86, 108);
            case INFO -> new Color(72, 137, 205);
            case NEUTRAL -> new Color(132, 145, 136);
        };
        return blend(color("Panel.background", Color.WHITE), base, isDark() ? 0.34 : 0.21);
    }

    /** Rich but quiet card surface for a semantic tone. */
    public static Color toneSurfaceColor(Tone tone) {
        return metricBackground(tone == null ? Tone.NEUTRAL : tone);
    }

    private static Color sectionBackground(Tone tone) {
        return blend(surfaceColor(), toneBackgroundColor(tone), isDark() ? 0.42 : 0.36);
    }

    private static Color metricBackground(Tone tone) {
        return blend(surfaceColor(), toneBackgroundColor(tone), isDark() ? 0.68 : 0.74);
    }

    private static Tone decorativeTone(String title) {
        String text = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        if (text.matches(".*(alert|problem|risk|delete|remove|validation|attention).*$")) {
            return Tone.DANGER;
        }
        if (text.matches(".*(plan|schedule|market|price|save|packing|readiness).*$")) {
            return Tone.WARNING;
        }
        if (text.matches(".*(search|find|filter|report|history|trend|detail|reference|customer|order|irrigation).*$")) {
            return Tone.INFO;
        }
        if (text.matches(".*(inventory|stock|harvest|plant|item|work|knowledge|overview).*$")) {
            return Tone.SUCCESS;
        }
        Tone[] palette = {Tone.SUCCESS, Tone.INFO, Tone.WARNING, Tone.DANGER};
        return palette[Math.floorMod(text.hashCode(), palette.length)];
    }

    private static Color toneBorder(Tone tone) {
        return blend(toneBackgroundColor(tone), toneForegroundColor(tone), 0.35);
    }

    /** Returns whether the active look and feel uses a dark base surface. */
    public static boolean isDarkTheme() {
        return isDark();
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

    private static Color controlForegroundColor() {
        return color("Label.foreground", isDark() ? new Color(238, 242, 239)
                : new Color(31, 48, 38));
    }

    private static void refreshTextComponentTheme(JTextComponent component) {
        boolean enabled = component.isEnabled();
        component.setBackground(enabled
                ? surfaceColor()
                : blend(surfaceColor(), pageBackgroundColor(), 0.42));
        component.setForeground(enabled ? controlForegroundColor() : mutedColor());
        component.setCaretColor(controlForegroundColor());
        component.setSelectionColor(color("TextComponent.selectionBackground",
                color("TextField.selectionBackground", toneBackgroundColor(Tone.INFO))));
        component.setSelectedTextColor(color("TextComponent.selectionForeground",
                color("TextField.selectionForeground", controlForegroundColor())));
    }

    private static void refreshTableTheme(JTable table) {
        table.setGridColor(borderColor());
        table.setBackground(surfaceColor());
        table.setForeground(controlForegroundColor());
        table.setSelectionBackground(color("Table.selectionBackground",
                isDark() ? new Color(45, 103, 75) : new Color(202, 232, 213)));
        table.setSelectionForeground(color("Table.selectionForeground",
                isDark() ? Color.WHITE : new Color(20, 62, 41)));
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setOpaque(true);
            header.setBackground(color("TableHeader.background",
                    blend(surfaceColor(), toneBackgroundColor(Tone.SUCCESS), 0.72)));
            header.setForeground(color("TableHeader.foreground",
                    isDark() ? new Color(238, 242, 239) : new Color(31, 48, 38)));
            header.setBorder(BorderFactory.createMatteBorder(
                    0, 0, 1, 0, borderColor()));
        }
    }

    private static Color blend(Color first, Color second, double secondWeight) {
        double weight = Math.max(0, Math.min(1, secondWeight));
        int red = (int) Math.round(first.getRed() * (1 - weight) + second.getRed() * weight);
        int green = (int) Math.round(first.getGreen() * (1 - weight) + second.getGreen() * weight);
        int blue = (int) Math.round(first.getBlue() * (1 - weight) + second.getBlue() * weight);
        return new Color(red, green, blue);
    }

    /** A subtle mint-to-blush wash that keeps brand color present behind headers. */
    private static final class FloralWashPanel extends JPanel {
        private final boolean masthead;

        private FloralWashPanel(LayoutManager layout, boolean masthead) {
            super(layout);
            this.masthead = masthead;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = masthead ? surfaceColor() : pageBackgroundColor();
                Color start = blend(base, toneBackgroundColor(Tone.SUCCESS),
                        masthead ? 0.78 : 0.68);
                Color end = blend(base, toneBackgroundColor(Tone.DANGER),
                        masthead ? 0.54 : 0.46);
                copy.setPaint(new GradientPaint(0, 0, start,
                        Math.max(1, getWidth()), 0, end));
                if (masthead) {
                    copy.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    copy.fillRoundRect(0, 0, Math.max(0, getWidth() - 1),
                            Math.max(0, getHeight() - 1), 18, 18);
                }
            } finally {
                copy.dispose();
            }
            super.paintComponent(graphics);
        }
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
            return 22;
        }

        @Override public int getIconHeight() {
            return 22;
        }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(background);
                copy.fillRoundRect(x + 1, y + 1, 20, 20, 8, 8);
                copy.setColor(foreground);
                copy.setFont(component.getFont().deriveFont(Font.BOLD,
                        code.length() > 1 ? 8f : 11f));
                int width = copy.getFontMetrics().stringWidth(code);
                int baseline = y + 11 + copy.getFontMetrics().getAscent() / 2 - 2;
                copy.drawString(code, x + (22 - width) / 2, baseline);
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
