package com.flowerfarm.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one-page getting-started guide for new growers. The text is bundled as
 * {@value #RESOURCE} (and kept identical to docs/GETTING_STARTED.md for printing);
 * this class loads it, renders it for the Help dialog, and lists the menu paths it
 * names so tests can prove each one exists. Pure logic so tests and the desktop
 * window share one path.
 */
public final class GettingStartedGuide {

    public static final String TITLE = "Getting started guide";
    public static final String MENU_LABEL = "Getting started guide…";
    public static final String RESOURCE = "/help/getting-started.md";

    private static final Pattern MENU_PATH = Pattern.compile("\\*\\*([^*→]+?) → ([^*]+?)\\*\\*");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+\\.\\s+(.*)$");

    /** A menu path the guide tells growers to use, such as File → Back up now…. */
    public record MenuPath(String menu, String item) {
        @Override
        public String toString() {
            return menu + " → " + item;
        }
    }

    private GettingStartedGuide() {
    }

    public static String menuDescription() {
        return "Read a one-page guide: install, first ten minutes, a normal day, backups, and help.";
    }

    /** The guide as Markdown, exactly as bundled. */
    public static String markdown() {
        try (InputStream input = GettingStartedGuide.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("The getting-started guide is missing from the application.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("The getting-started guide could not be read.", e);
        }
    }

    /** The bundled guide as simple HTML for a read-only Swing text pane. */
    public static String html() {
        return toHtml(markdown());
    }

    /** Every bold "Menu → Item" path named in the guide, in order. */
    public static List<MenuPath> menuPaths(String markdown) {
        List<MenuPath> paths = new ArrayList<>();
        Matcher matcher = MENU_PATH.matcher(markdown);
        while (matcher.find()) {
            paths.add(new MenuPath(matcher.group(1).trim(), matcher.group(2).trim()));
        }
        return List.copyOf(paths);
    }

    /**
     * Renders the small Markdown subset the guide uses: # and ## headings, numbered
     * and bulleted lists, paragraphs, and **bold**. Everything else is escaped text.
     */
    static String toHtml(String markdown) {
        StringBuilder html = new StringBuilder("<html><body style='font-family: sans-serif; margin: 6px'>");
        String openList = null;
        StringBuilder paragraph = new StringBuilder();
        for (String raw : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String line = raw.strip();
            Matcher numbered = NUMBERED.matcher(line);
            String listTag = line.startsWith("- ") ? "ul" : numbered.matches() ? "ol" : null;
            if (line.isEmpty() || line.startsWith("#") || listTag != null) {
                flushParagraph(html, paragraph);
            }
            if (openList != null && !openList.equals(listTag)) {
                html.append("</").append(openList).append('>');
                openList = null;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("## ")) {
                html.append("<h2>").append(inline(line.substring(3))).append("</h2>");
            } else if (line.startsWith("# ")) {
                html.append("<h1>").append(inline(line.substring(2))).append("</h1>");
            } else if (listTag != null) {
                if (openList == null) {
                    html.append('<').append(listTag).append('>');
                    openList = listTag;
                }
                String item = "ul".equals(listTag) ? line.substring(2) : numbered.group(1);
                html.append("<li>").append(inline(item)).append("</li>");
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(inline(line));
            }
        }
        flushParagraph(html, paragraph);
        if (openList != null) {
            html.append("</").append(openList).append('>');
        }
        return html.append("</body></html>").toString();
    }

    private static void flushParagraph(StringBuilder html, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            html.append("<p>").append(paragraph).append("</p>");
            paragraph.setLength(0);
        }
    }

    private static String inline(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return BOLD.matcher(escaped).replaceAll("<b>$1</b>");
    }
}
