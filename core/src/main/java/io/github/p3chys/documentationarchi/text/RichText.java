package io.github.p3chys.documentationarchi.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.p3chys.documentationarchi.text.KeywordRules.PanelStyle;
import io.github.p3chys.documentationarchi.text.KeywordRules.TagColour;
import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/**
 * A description from the model, split into blocks with tags, links and coloured tokens marked:
 * <pre>
 * &lt;sql&gt;select 1&lt;/sql&gt;     code block
 * &lt;#&gt;a remark&lt;/#&gt;       note panel (without the closing tag: up to the next blank line); with &lt;#&gt; and &lt;/#&gt;
 *                      on lines of their own the note holds everything between them, blank lines, SQL and tables too
 * &lt;tbl&gt;|a|b|, |1|2|&lt;/tbl&gt;   a table of lines starting with |, the first its header; other lines go on with the last cell
 *                      HaF form: &lt;tbl&gt;a | b |, 1 | 2 |&lt;/tbl&gt; one row per line, a line not ending in | goes on in the last cell
 * &lt;loop&gt; ... &lt;/loop&gt;   a line that is only a tag indents the lines after it by 20px; &lt;section end&gt; ends all indents
 * &lt;ass&gt;docs/SLA.pdf&lt;/ass&gt;   like [[asset:docs/SLA.pdf]]; a web address is a link, or a picture when it ends in .png, .jpg...
 * WARNING: ...         panel, see {@link KeywordRules}
 * #risk, [[risk]]      label, see {@link KeywordRules}
 * Description:         bold or another text style, see {@link KeywordRules}
 * [table] 'CONSTANT' {variable}   coloured text; a constant is capitals, digits, _ ( ) and : in quotes, with a capital
 * [[asset:flow.png]], [[asset:docs/SLA.pdf|the SLA]], asset:flow.png   a file of the model's Assets folder
 * - item, * item, 1. item   bullet and numbered lists, one item per line (indented lines go on with the item)
 * | a | b |, then |---|---|   a table, the first row its header; without the line of dashes it is text
 * **bold**, *italic*   emphasis; a * with a space, digit or letter on the wrong side stays a *, as in SQL or 2*3
 * [label](https://...)   a link with a text; [ORDERS] without "(" right after it stays a table name
 * ```java ... ```, &lt;code lang="java"&gt;...&lt;/code&gt;   a code block, the language optional
 * [[ref:id]], [[ref:id|label]]   a link to an element or view, showing its current name
 * </pre>
 */
public final class RichText {

    public enum InlineKind {
        TEXT, BREAK, URL, TAG, STYLED, TABLE, CONSTANT, VARIABLE, ASSET, BOLD, ITALIC, LINK, REF,
        /** A picture at a web address, from {@code <ass>}. */
        IMAGE_URL,
        /** An {@code <ass>} path that is absolute or leaves the assets folder. */
        BAD_ASSET
    }

    public static final class Inline {
        public final InlineKind kind;
        /** The text; the address of a URL or link, the id of a reference, the file of an asset. */
        public final String text;
        public final TagColour colour;
        /** How styled text and tokens look. */
        public final TextStyle style;
        /** What an asset, link or reference shows as. Null for the file name or the target's name. */
        public final String label;
        /** What bold and italic text holds. */
        public final List<Inline> children;

        Inline(InlineKind kind, String text, TagColour colour) {
            this(kind, text, colour, null);
        }

        Inline(InlineKind kind, String text, TagColour colour, TextStyle style) {
            this(kind, text, colour, style, null);
        }

        Inline(InlineKind kind, String text, TagColour colour, TextStyle style, String label) {
            this(kind, text, colour, style, label, List.of());
        }

        private Inline(InlineKind kind, String text, TagColour colour, TextStyle style, String label, List<Inline> children) {
            this.kind = kind;
            this.text = text;
            this.colour = colour;
            this.style = style;
            this.label = label;
            this.children = children;
        }
    }

    /**
     * A paragraph; a panel when {@link #style} is set; SQL when {@link #sql} is set, other code when {@link #code} is;
     * a list when {@link #items} is set, a table when {@link #rows} is.
     */
    public static final class Block {
        public final PanelStyle style;
        /** Heading of a panel, null for none. */
        public final String title;
        /** The text of a paragraph or panel. */
        public final List<Inline> inlines;
        public final String sql;
        public final String code;
        /** The language of {@link #code} as written, null for none. */
        public final String language;
        /** The items of a list, each its text. */
        public final List<List<Inline>> items;
        public final boolean numbered;
        /** The rows of a table, the first one its header; each a list of cells. */
        public final List<List<List<Inline>>> rows;
        /** A panel holding lists or tables: its blocks, to show instead of {@link #inlines}. Empty otherwise. */
        public final List<Block> children;
        /** How many nesting tags (a line of only {@code <name>}) are open where the block stands, 0 for none. */
        public int indent;

        Block(PanelStyle style, String title, List<Inline> inlines, String sql) {
            this(style, title, inlines, sql, null, null, null, false, null, List.of());
        }

        private Block(PanelStyle style, String title, List<Inline> inlines, String sql, String code, String language,
                List<List<Inline>> items, boolean numbered, List<List<List<Inline>>> rows, List<Block> children) {
            this.style = style;
            this.title = title;
            this.inlines = inlines;
            this.sql = sql;
            this.code = code;
            this.language = language;
            this.items = items;
            this.numbered = numbered;
            this.rows = rows;
            this.children = children;
        }

        static Block code(String code, String language) {
            return new Block(null, null, List.of(), null, code, language, null, false, null, List.of());
        }

        static Block list(List<List<Inline>> items, boolean numbered) {
            return new Block(null, null, List.of(), null, null, null, items, numbered, null, List.of());
        }

        static Block table(List<List<List<Inline>>> rows) {
            return new Block(null, null, List.of(), null, null, null, null, false, rows, List.of());
        }

        static Block panel(PanelStyle style, String title, List<Inline> inlines, List<Block> children) {
            return new Block(style, title, inlines, null, null, null, null, false, null, children);
        }
    }

    private static final Pattern TAG_BLOCK = Pattern.compile(
            "(?ism)<sql>(?<sql>.*?)(?:</sql>|\\z)|<tbl>(?<tbl>.*?)(?:</tbl>|\\z)"
            // <#> and </#> on lines of their own: the note is everything between them
            + "|^[ \\t]*<#>[ \\t]*\\n(?<longNote>.*?)^[ \\t]*</#>[ \\t]*$"
            + "|<#>(?<note>.*?)(?:</#>|\\n[ \\t]*\\n|\\z)"
            // a fence on a line of its own, up to the next one or the end
            + "|^[ \\t]*```[ \\t]*(?<fence>[\\w+#.-]*)[ \\t]*\\n(?<fenced>.*?)(?:^[ \\t]*```[ \\t]*$|\\z)"
            + "|<code(?:\\s+lang\\s*=\\s*\"(?<lang>[^\"\\n]*)\")?\\s*>(?<code>.*?)(?:</code>|\\z)");
    private static final Pattern PREFIX = Pattern.compile("^\\s*([^:]{1,40}):\\s*(.*)$");
    private static final Pattern ITEM = Pattern.compile("^[ \\t]*(?:(?<bullet>[-*])|(?<number>\\d{1,9})\\.)[ \\t]+(?<item>\\S.*)$");
    private static final Pattern TABLE_RULE = Pattern.compile("^[ \\t]*\\|?[ \\t]*:?-{3,}:?[ \\t]*(?:\\|[ \\t]*:?-{3,}:?[ \\t]*)*\\|?[ \\t]*$");
    /** A line of only a tag that opens or closes a level of indent, and the first word of its name. */
    private static final Pattern NESTING = Pattern.compile("^<(?<close>/)?(?<name>\\p{L}(?:[^<>]|<>)*)>$");
    private static final Pattern SECTION_END = Pattern.compile("(?i)^<section\\s+end>$");
    private static final Set<String> KNOWN_TAGS = Set.of("sql", "tbl", "code", "ass");
    /** A constant: capitals, digits, _ ( ) and : in single quotes, with a capital; not the apostrophes of don't or the '90s. */
    public static final String CONSTANT = "(?<![\\p{L}\\p{N}'])'(?=[A-Z0-9_():]*[A-Z])[A-Z0-9_():]+'(?![\\p{L}\\p{N}])";
    private static final Pattern INLINE = Pattern.compile("(?i:<ass>)(?<assTag>[^<]*\\S[^<]*)(?i:</ass>)"
            + "|(?<url>https?://[^\\s<>\"]+)"
            + "|\\[\\[\\s*(?i:asset):(?<asset>[^\\]|\\n]+)(?:\\|(?<assetLabel>[^\\]\\n]*))?]]"
            + "|(?<![\\p{L}\\p{N}_/:])(?i:asset):(?<bareAsset>[^\\s<>\"\\[\\]|]+)"
            + "|\\[\\[\\s*(?i:ref):\\s*(?<ref>[^\\]|\\s]+)\\s*(?:\\|(?<refLabel>[^\\]\\n]*))?]]"
            + "|\\[\\[(?<bracket>[^\\]\\n]+)]]"
            // only "](" makes a link; [ORDERS] alone stays a table name
            + "|\\[(?<linkLabel>[^\\[\\]\\n]+)]\\((?<link>https?://[^\\s()<>\"]+)\\)"
            + "|(?<table>\\[[^\\[\\]\\n]+])"
            + "|(?<constant>" + CONSTANT + ")"
            + "|(?<variable>\\{[^{}\\n]+})"
            + "|(?<![\\p{L}\\p{N}_#&/])#(?<hash>[\\p{L}\\p{N}_-]+)"
            // not the * of select *, count(*), a * b or 2*3: text right inside, nothing word-like outside
            + "|(?<![\\p{L}\\p{N}*\\\\])\\*\\*(?<bold>[^\\s*](?:[^\\n]*?[^\\s*])?)\\*\\*(?![\\p{L}\\p{N}*])"
            + "|(?<![\\p{L}\\p{N}*\\\\])\\*(?<italic>[\\p{L}\\p{N}\"'(\\[{#](?:[^*\\n]*?[\\p{L}\\p{N}.!?\"')\\]}])?)\\*"
            + "(?![\\p{L}\\p{N}*])");

    private RichText() {
    }

    public static List<Block> parse(String text, KeywordRules rules) {
        String source = text == null ? "" : text.replaceAll("\\R", "\n");
        List<Block> blocks = new ArrayList<>();
        Matcher m = TAG_BLOCK.matcher(source);
        Nesting nesting = new Nesting();
        int done = 0;
        while (m.find()) {
            text(source.substring(done, m.start()), rules, blocks, nesting);
            int from = blocks.size();
            if (m.group("sql") != null) {
                sql(m.group("sql"), blocks);
            } else if (m.group("tbl") != null) {
                table(m.group("tbl"), rules, blocks);
            } else if (m.group("longNote") != null) {
                List<Block> children = parse(m.group("longNote"), rules);
                if (!children.isEmpty()) {
                    blocks.add(Block.panel(PanelStyle.NOTE, null, List.of(), children));
                }
            } else if (m.group("note") != null) {
                if (!m.group("note").isBlank()) {
                    String note = m.group("note").strip();
                    blocks.add(Block.panel(PanelStyle.NOTE, null, inlines(note, rules), inner(note, rules)));
                }
            } else {
                boolean fenced = m.group("fenced") != null;
                String language = fenced ? m.group("fence") : m.group("lang");
                language = language == null || language.isBlank() ? null : language.strip();
                String code = fenced ? m.group("fenced") : m.group("code");
                if ("sql".equalsIgnoreCase(language)) {
                    sql(code, blocks); // looks like any other SQL block
                } else {
                    code = code.replaceAll("^(?:[ \\t]*\\n)+|\\s+$", "");
                    if (!code.isEmpty()) {
                        blocks.add(Block.code(code, language));
                    }
                }
            }
            indent(blocks, from, nesting.level);
            done = m.end();
        }
        text(source.substring(done), rules, blocks, nesting);
        return blocks;
    }

    /** How many nesting tags are open; the lines of a description are indented by it. */
    private static final class Nesting {
        int level;
    }

    private static void indent(List<Block> blocks, int from, int level) {
        blocks.subList(from, blocks.size()).forEach(b -> b.indent = level);
    }

    /**
     * Text between the tag blocks: the lines, with a line of only a tag opening or closing a level of indent. The tag
     * line is printed as text, an opening one at the outer level and a closing one too; {@code <section end>} closes
     * all levels and is not printed.
     */
    private static void text(String text, KeywordRules rules, List<Block> blocks, Nesting nesting) {
        List<String> chunk = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String tag = line.strip();
            Matcher m = NESTING.matcher(tag);
            boolean section = SECTION_END.matcher(tag).matches();
            boolean nest = !section && m.matches()
                    && !KNOWN_TAGS.contains(m.group("name").split("[\\s=]", 2)[0].toLowerCase(Locale.ROOT));
            if (!section && !nest) {
                chunk.add(line);
                continue;
            }
            int from = blocks.size();
            lines(String.join("\n", chunk), rules, blocks);
            indent(blocks, from, nesting.level);
            chunk.clear();
            if (section) {
                nesting.level = 0;
            } else if (m.group("close") == null) {
                from = blocks.size();
                paragraph(new ArrayList<>(List.of(tag)), rules, blocks);
                indent(blocks, from, nesting.level++);
            } else {
                nesting.level = Math.max(0, nesting.level - 1);
                from = blocks.size();
                paragraph(new ArrayList<>(List.of(tag)), rules, blocks);
                indent(blocks, from, nesting.level);
            }
        }
        int from = blocks.size();
        lines(String.join("\n", chunk), rules, blocks);
        indent(blocks, from, nesting.level);
    }

    /**
     * A {@code <tbl>}. When the first line starts with | rows are the lines starting with |, any other line goes on with
     * the last cell. Otherwise it is the HaF form, see {@link #hafRows}.
     */
    private static void table(String text, KeywordRules rules, List<Block> blocks) {
        String first = text.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        if (!first.startsWith("|")) {
            List<List<List<Inline>>> table = new ArrayList<>();
            for (List<String> row : hafRows(text)) {
                List<List<Inline>> cells = new ArrayList<>();
                row.forEach(cell -> cells.add(inlines(cell, rules)));
                table.add(cells);
            }
            if (!table.isEmpty()) {
                blocks.add(Block.table(table));
            }
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> before = new ArrayList<>();
        for (String line : text.split("\n")) {
            String row = line.strip();
            if (row.isEmpty() || row.startsWith("|") && TABLE_RULE.matcher(row).matches()) {
                continue;
            }
            if (row.startsWith("|")) {
                rows.add(rawCells(row));
            } else if (rows.isEmpty()) {
                before.add(row);
            } else {
                // goes on with the last cell; after a | come the next cells of the same row
                List<String> last = rows.get(rows.size() - 1);
                List<String> more = rawCells(row);
                last.set(last.size() - 1, last.get(last.size() - 1) + "\n" + more.get(0));
                last.addAll(more.subList(1, more.size()));
            }
        }
        paragraph(before, rules, blocks);
        if (!rows.isEmpty()) {
            List<List<List<Inline>>> table = new ArrayList<>();
            for (List<String> row : rows) {
                List<List<Inline>> cells = new ArrayList<>();
                row.forEach(cell -> cells.add(inlines(cell, rules)));
                table.add(cells);
            }
            blocks.add(Block.table(table));
        }
    }

    /**
     * The rows of a HaF table: {@code cell | cell |} with no | at the start. A line not ending in | goes on with its
     * last cell on the next line, joined with a line break; after the break the text up to the next | still belongs to
     * that cell and the parts after it are further cells of the row. The last line is closed without a |.
     */
    private static List<List<String>> hafRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        boolean open = false;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            List<String> parts = splitCells(line.strip());
            boolean closed = parts.size() > 1 && parts.get(parts.size() - 1).isEmpty();
            if (closed) {
                parts.remove(parts.size() - 1);
            }
            if (open) {
                List<String> last = rows.get(rows.size() - 1);
                last.set(last.size() - 1, (last.get(last.size() - 1) + "\n" + parts.get(0)).strip());
                last.addAll(parts.subList(1, parts.size()));
            } else {
                rows.add(parts);
            }
            open = !closed;
        }
        return rows;
    }

    private static void sql(String text, List<Block> blocks) {
        String sql = text.replaceAll("^(?:[ \\t]*\\n)+|\\s+$", "");
        if (!sql.isEmpty()) {
            blocks.add(new Block(null, null, List.of(), sql));
        }
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
        if (style != null) {
            blocks.add(Block.panel(style, title, inlines(text.toString(), rules), inner(text.toString(), rules)));
        } else if (text.length() > 0) {
            structure(text.toString(), rules, blocks);
        }
    }

    /** The blocks of a panel's text when it holds lists or tables; none when it is just text. */
    private static List<Block> inner(String text, KeywordRules rules) {
        List<Block> blocks = new ArrayList<>();
        structure(text, rules, blocks);
        return blocks.size() == 1 && blocks.get(0).items == null && blocks.get(0).rows == null ? List.of() : blocks;
    }

    /** Paragraphs, lists and tables of text without blank lines. */
    private static void structure(String text, KeywordRules rules, List<Block> blocks) {
        String[] lines = text.split("\n", -1);
        List<String> paragraph = new ArrayList<>();
        for (int i = 0; i < lines.length;) {
            Matcher item = ITEM.matcher(lines[i]);
            if (i + 1 < lines.length && lines[i].contains("|") && lines[i + 1].contains("|")
                    && TABLE_RULE.matcher(lines[i + 1]).matches()) {
                paragraph(paragraph, rules, blocks);
                List<List<List<Inline>>> rows = new ArrayList<>();
                rows.add(cells(lines[i], rules));
                for (i += 2; i < lines.length && lines[i].contains("|"); i++) {
                    rows.add(cells(lines[i], rules));
                }
                blocks.add(Block.table(rows));
            } else if (item.matches()) {
                paragraph(paragraph, rules, blocks);
                boolean numbered = item.group("number") != null;
                List<StringBuilder> items = new ArrayList<>();
                for (; i < lines.length; i++) {
                    Matcher next = ITEM.matcher(lines[i]);
                    if (next.matches() && (next.group("number") != null) == numbered) {
                        items.add(new StringBuilder(next.group("item")));
                    } else if (!next.matches() && !lines[i].isBlank() && Character.isWhitespace(lines[i].charAt(0))) {
                        items.get(items.size() - 1).append('\n').append(lines[i].strip()); // goes on with the item
                    } else {
                        break;
                    }
                }
                List<List<Inline>> list = new ArrayList<>();
                items.forEach(s -> list.add(inlines(s.toString(), rules)));
                blocks.add(Block.list(list, numbered));
            } else {
                paragraph.add(lines[i++]);
            }
        }
        paragraph(paragraph, rules, blocks);
    }

    private static void paragraph(List<String> lines, KeywordRules rules, List<Block> blocks) {
        if (!lines.isEmpty()) {
            blocks.add(new Block(null, null, inlines(String.join("\n", lines), rules), null));
            lines.clear();
        }
    }

    /** The cells of a table row: split at each | that is not escaped as \| and not inside [[...]]. */
    private static List<List<Inline>> cells(String line, KeywordRules rules) {
        List<List<Inline>> cells = new ArrayList<>();
        rawCells(line).forEach(cell -> cells.add(inlines(cell, rules)));
        return cells;
    }

    /** The text of the cells of a table row, see {@link #cells}. */
    private static List<String> rawCells(String line) {
        String row = line.strip();
        row = row.startsWith("|") ? row.substring(1) : row;
        row = row.endsWith("|") && !row.endsWith("\\|") ? row.substring(0, row.length() - 1) : row;
        return splitCells(row);
    }

    /** Every part of the line between |, stripped; a | ending the line gives a last empty part. */
    private static List<String> splitCells(String row) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '\\' && i + 1 < row.length() && row.charAt(i + 1) == '|') {
                cell.append('|');
                i++;
                continue;
            }
            if (row.startsWith("[[", i)) {
                depth++;
            } else if (row.startsWith("]]", i) && depth > 0) {
                depth--;
            }
            if (c == '|' && depth == 0) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }

    /** The configured labels the text carries, as they are configured: #RISK and [[ risk ]] are both "risk". */
    public static Set<String> labels(String text, KeywordRules rules) {
        Set<String> found = new LinkedHashSet<>();
        for (Block block : parse(text, rules)) {
            visit(block, i -> {
                if (i.kind == InlineKind.TAG) {
                    rules.labels().stream().filter(i.text::equalsIgnoreCase).findFirst().ifPresent(found::add);
                }
            });
        }
        return found;
    }

    /** Every inline of the block: its text, list items and table cells, and what bold and italic text holds. */
    public static void visit(Block block, Consumer<Inline> visitor) {
        List<List<Inline>> parts = new ArrayList<>();
        parts.add(block.inlines);
        if (block.items != null) {
            parts.addAll(block.items);
        }
        if (block.rows != null) {
            block.rows.forEach(parts::addAll);
        }
        parts.forEach(p -> visit(p, visitor));
        block.children.forEach(b -> visit(b, visitor));
    }

    private static void visit(List<Inline> inlines, Consumer<Inline> visitor) {
        for (Inline i : inlines) {
            visitor.accept(i);
            visit(i.children, visitor);
        }
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
            if (m.group("assTag") != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(assetTag(m.group("assTag")));
                done = m.end();
            } else if (m.group("asset") != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(new Inline(InlineKind.ASSET, m.group("asset").strip(), null, null, label(m.group("assetLabel"))));
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
            } else if (m.group("ref") != null || m.group("link") != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                inlines.add(m.group("ref") != null ? new Inline(InlineKind.REF, m.group("ref"), null, null, label(m.group("refLabel")))
                        : new Inline(InlineKind.LINK, m.group("link"), null, null, m.group("linkLabel").strip()));
                done = m.end();
            } else if (m.group("bold") != null || m.group("italic") != null) {
                addText(inlines, text.substring(done, m.start()), rules);
                boolean bold = m.group("bold") != null;
                inlines.add(new Inline(bold ? InlineKind.BOLD : InlineKind.ITALIC, m.group(), null, null, null,
                        inlines(bold ? m.group("bold") : m.group("italic"), rules)));
                done = m.end();
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

    /**
     * The inline of {@code <ass>path</ass>}: lines of the path are joined, whitespace around them dropped. A relative
     * path is an asset like {@code [[asset:path]]}, a web address a link or a picture, anything else unsupported.
     */
    private static Inline assetTag(String written) {
        StringBuilder joined = new StringBuilder();
        for (String line : written.split("\n")) {
            joined.append(line.strip());
        }
        String path = joined.toString();
        if (path.matches("(?i)https?://.+")) {
            String bare = path.replaceAll("[?#].*$", "").toLowerCase(Locale.ROOT);
            boolean picture = bare.matches(".*\\.(png|jpe?g|gif|bmp|svg)");
            return new Inline(picture ? InlineKind.IMAGE_URL : InlineKind.URL, path, null);
        }
        return new Inline(unsupportedAsset(path) ? InlineKind.BAD_ASSET : InlineKind.ASSET, path, null);
    }

    /** Whether a path can't be a file of the assets folder: absolute, or going up out of it with {@code ..}. */
    public static boolean unsupportedAsset(String path) {
        String p = path.strip().replace('\\', '/');
        if (p.startsWith("/") || p.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) {
            return true;
        }
        int depth = 0;
        for (String part : p.split("/")) {
            if (part.equals("..")) {
                depth--;
            } else if (!part.isEmpty() && !part.equals(".")) {
                depth++;
            }
            if (depth < 0) {
                return true;
            }
        }
        return false;
    }

    private static String label(String label) {
        return label == null || label.isBlank() ? null : label.strip();
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
