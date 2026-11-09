package io.github.p3chys.documentationarchi.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/**
 * A description from the model, split into blocks with tags, links and coloured tokens marked:
 * <pre>
 * &lt;sql&gt;select 1&lt;/sql&gt;     code block
 * &lt;#&gt;a remark&lt;/#&gt;       note panel (without the closing tag: up to the next blank line)
 * WARNING: ...         panel, see {@link KeywordRules}
 * #risk, [[risk]]      label, see {@link KeywordRules}
 * Description:         bold or another text style, see {@link KeywordRules}
 * [table] 'CONSTANT' {variable}   coloured text
 * [[asset:flow.png]], [[asset:docs/SLA.pdf|the SLA]], asset:flow.png   a file of the model's Assets folder
 * </pre>
 */
public final class RichText {

    public enum InlineKind { TEXT, BREAK, URL, TAG, STYLED, TABLE, CONSTANT, VARIABLE, ASSET }

    public static final class Inline {
        public final InlineKind kind;
        public final String text;
        public final TagColour colour;
        /** How styled text and tokens look. */
        public final TextStyle style;
        /** What an asset shows as: a picture's alternative text, a file link's text. Null for the file name. */
        public final String label;

        Inline(InlineKind kind, String text, TagColour colour) {
            this(kind, text, colour, null);
        }

        Inline(InlineKind kind, String text, TagColour colour, TextStyle style) {
            this(kind, text, colour, style, null);
        }

        Inline(InlineKind kind, String text, TagColour colour, TextStyle style, String label) {
            this.kind = kind;
            this.text = text;
            this.colour = colour;
            this.style = style;
            this.label = label;
        }
    }

    /** A paragraph, a panel when {@link #style} is set, or SQL when {@link #sql} is set. */
    public static final class Block {
        public final PanelStyle style;
        /** Heading of a panel, null for none. */
        public final String title;
        public final List<Inline> inlines;
        public final String sql;

        Block(PanelStyle style, String title, List<Inline> inlines, String sql) {
            this.style = style;
            this.title = title;
            this.inlines = inlines;
            this.sql = sql;
        }
    }

    private static final Pattern TAG_BLOCK = Pattern.compile(
            "(?is)<sql>(?<sql>.*?)(?:</sql>|\\z)|<#>(?<note>.*?)(?:</#>|\\n[ \\t]*\\n|\\z)");
    private static final Pattern PREFIX = Pattern.compile("^\\s*([^:]{1,40}):\\s*(.*)$");
    private static final Pattern INLINE = Pattern.compile("(?<url>https?://[^\\s<>\"]+)"
            + "|\\[\\[\\s*(?i:asset):(?<asset>[^\\]|\\n]+)(?:\\|(?<assetLabel>[^\\]\\n]*))?]]"
            + "|(?<![\\p{L}\\p{N}_/:])(?i:asset):(?<bareAsset>[^\\s<>\"\\[\\]|]+)"
            + "|\\[\\[(?<bracket>[^\\]\\n]+)]]"
            + "|(?<table>\\[[^\\[\\]\\n]+])"
            // not the apostrophes of don't or the '90s
            + "|(?<constant>(?<![\\p{L}\\p{N}'])'[^'\\n]+'(?![\\p{L}\\p{N}]))"
            + "|(?<variable>\\{[^{}\\n]+})"
            + "|(?<![\\p{L}\\p{N}_#&/])#(?<hash>[\\p{L}\\p{N}_-]+)");

    private RichText() {
    }

    public static List<Block> parse(String text, KeywordRules rules) {
        String source = text == null ? "" : text.replaceAll("\\R", "\n");
        List<Block> blocks = new ArrayList<>();
        Matcher m = TAG_BLOCK.matcher(source);
        int done = 0;
        while (m.find()) {
            lines(source.substring(done, m.start()), rules, blocks);
            if (m.group("sql") != null) {
                String sql = m.group("sql").replaceAll("^(?:[ \\t]*\\n)+|\\s+$", "");
                if (!sql.isEmpty()) {
                    blocks.add(new Block(null, null, List.of(), sql));
                }
            } else if (!m.group("note").isBlank()) {
                blocks.add(new Block(PanelStyle.NOTE, null, inlines(m.group("note").strip(), rules), null));
            }
            done = m.end();
        }
        lines(source.substring(done), rules, blocks);
        return blocks;
    }

    private static void lines(String text, KeywordRules rules, List<Block> blocks) {
        StringBuilder current = new StringBuilder();
        PanelStyle style = null;
        String title = null;
        for (String line : text.split("\n", -1)) {
            Matcher prefix = PREFIX.matcher(line);
            PanelStyle panel = prefix.matches() ? rules.panel(prefix.group(1).strip()) : null;
            if (panel != null || line.isBlank()) {
                flush(blocks, style, title, current, rules);
                style = panel;
                title = panel != null ? prefix.group(1).strip() : null;
                current.setLength(0);
                if (panel != null) {
                    current.append(prefix.group(2));
                }
            } else {
                current.append(current.length() > 0 ? "\n" : "").append(line);
            }
        }
        flush(blocks, style, title, current, rules);
    }

    private static void flush(List<Block> blocks, PanelStyle style, String title, CharSequence text, KeywordRules rules) {
        if (style != null || text.length() > 0) {
            blocks.add(new Block(style, title, inlines(text.toString(), rules), null));
        }
    }

    /** The configured labels the text carries, as they are configured: #RISK and [[ risk ]] are both "risk". */
    public static Set<String> labels(String text, KeywordRules rules) {
        Set<String> found = new LinkedHashSet<>();
        for (Block block : parse(text, rules)) {
            for (Inline i : block.inlines) {
                if (i.kind == InlineKind.TAG) {
                    rules.labels().stream().filter(i.text::equalsIgnoreCase).findFirst().ifPresent(found::add);
                }
            }
        }
        return found;
    }

    /** Tags, links and coloured tokens in one piece of text. Words that are not configured keywords stay as they are. */
    public static List<Inline> inlines(String text, KeywordRules rules) {
        List<Inline> inlines = new ArrayList<>();
        Matcher m = INLINE.matcher(text);
        int done = 0;
        while (m.find()) {
            String url = m.group("url");
            String keyword = m.group("bracket") != null ? m.group("bracket").strip() : m.group("hash");
            TagColour colour = keyword != null ? rules.tag(keyword) : null;
            InlineKind token = m.group("table") != null ? InlineKind.TABLE : m.group("constant") != null ? InlineKind.CONSTANT
                    : m.group("variable") != null ? InlineKind.VARIABLE : null;
            if (m.group("asset") != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                String label = m.group("assetLabel") == null || m.group("assetLabel").isBlank() ? null
                        : m.group("assetLabel").strip();
                inlines.add(new Inline(InlineKind.ASSET, m.group("asset").strip(), null, null, label));
                done = m.end();
            } else if (m.group("bareAsset") != null) {
                // like a URL: the full stop or bracket after it belongs to the sentence
                String name = m.group("bareAsset").replaceAll("[.,;:!?)]+$", "");
                if (name.isEmpty()) {
                    continue;
                }
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(new Inline(InlineKind.ASSET, name, null));
                done = m.start("bareAsset") + name.length();
            } else if (url != null) {
                url = url.replaceAll("[.,;:!?)\\]]+$", "");
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(new Inline(InlineKind.URL, url, null));
                done = m.start() + url.length();
            } else if (colour != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(new Inline(InlineKind.TAG, keyword, colour));
                done = m.end();
            } else if (token != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(new Inline(token, m.group(), null, rules.token(KeywordRules.Token.valueOf(token.name()))));
                done = m.end();
            }
        }
        addText(inlines, text.substring(done), rules);
        return inlines;
    }

    private static void addText(List<Inline> inlines, String text, KeywordRules rules) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                inlines.add(new Inline(InlineKind.BREAK, "", null));
            }
            addLine(inlines, lines[i], rules);
        }
    }

    private static void addLine(List<Inline> inlines, String line, KeywordRules rules) {
        Matcher m = rules.styled().matcher(line);
        int done = 0;
        while (m.find()) {
            if (m.start() > done) {
                inlines.add(new Inline(InlineKind.TEXT, line.substring(done, m.start()), null));
            }
            inlines.add(new Inline(InlineKind.STYLED, m.group(), null, rules.style(m)));
            done = m.end();
        }
        if (done < line.length()) {
            inlines.add(new Inline(InlineKind.TEXT, line.substring(done), null));
        }
    }
}
