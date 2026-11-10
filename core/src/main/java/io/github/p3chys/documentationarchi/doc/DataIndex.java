package io.github.p3chys.documentationarchi.doc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.p3chys.documentationarchi.text.KeywordRules;
import io.github.p3chys.documentationarchi.text.KeywordRules.Token;
import io.github.p3chys.documentationarchi.text.RichText;
import io.github.p3chys.documentationarchi.text.RichText.Inline;
import io.github.p3chys.documentationarchi.text.RichText.InlineKind;

/**
 * The pages of the {@code index} rules: every table ([ORDERS]), variable ({client_id}) or constant ('ACTIVE') found in
 * the descriptions, notes and property values, with links to where it is used. The tokens come from {@link RichText}, so
 * the index never disagrees with how the text is coloured; in an SQL block only a table in brackets counts.
 */
public final class DataIndex {

    /** A table name in brackets inside an SQL block. */
    private static final Pattern SQL_TABLE = Pattern.compile("\\[([^\\[\\]\\n]+)]");

    private DataIndex() {
    }

    /** The pages, and a note per index that was left out because nothing was found. */
    public static Catalogs.Result build(Doc doc, List<Token> kinds, KeywordRules rules) {
        Catalogs.Result result = new Catalogs.Result();
        for (Token kind : kinds) {
            // by the lower case token: the spelling first seen, and the places in order
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, List<Link>> places = new LinkedHashMap<>();
            for (DocPage page : doc.pages) {
                Link pageLink = new Link(page.id, page.title);
                List<String> texts = new ArrayList<>();
                texts.add(page.documentation);
                texts.addAll(page.notes);
                page.texts.forEach(t -> texts.add(t.text));
                scan(texts, page.properties, kind, rules, pageLink, names, places);
                for (Entry entry : page.entries) {
                    List<String> own = new ArrayList<>();
                    own.add(entry.documentation);
                    entry.relations.forEach(r -> own.add(r.documentation));
                    scan(own, entry.properties, kind, rules, new Link(entry.id, entry.name, entry.type), names, places);
                }
            }
            String key = kind.name().toLowerCase(Locale.ROOT) + "s";
            if (names.isEmpty()) {
                result.notes.add("The index of " + key + " found none and was left out.");
                continue;
            }
            DocTable table = new DocTable(List.of(rules.texts().get("token"), rules.texts().get("used-in")));
            names.keySet().stream().sorted(Comparator.naturalOrder()).forEach(k -> {
                table.rows.add(List.of(DocTable.Cell.value(names.get(k)), DocTable.Cell.links(places.get(k))));
            });
            DocPage page = new DocPage("index-" + key, rules.texts().get(key + "-index"));
            page.tables.add(table);
            result.pages.add(page);
        }
        return result;
    }

    private static void scan(List<String> descriptions, List<Property> properties, Token kind, KeywordRules rules,
            Link where, Map<String, String> names, Map<String, List<Link>> places) {
        Consumer<String> found = name -> {
            String key = name.strip().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                return;
            }
            names.putIfAbsent(key, name.strip());
            List<Link> links = places.computeIfAbsent(key, k -> new ArrayList<>());
            if (links.stream().noneMatch(l -> l.id.equals(where.id))) {
                links.add(where);
            }
        };
        for (String text : descriptions) {
            if (text != null && !text.isBlank()) {
                RichText.parse(text, rules).forEach(block -> block(block, kind, found));
            }
        }
        for (Property p : properties) {
            if (p.value != null && !p.value.isBlank()) {
                inlines(RichText.inlines(p.value, rules), kind, found);
            }
        }
    }

    private static void block(RichText.Block block, Token kind, Consumer<String> found) {
        if (block.sql != null) {
            if (kind == Token.TABLE) {
                Matcher m = SQL_TABLE.matcher(block.sql);
                while (m.find()) {
                    found.accept(m.group(1));
                }
            }
            return;
        }
        List<List<Inline>> parts = new ArrayList<>();
        if (block.inlines != null) {
            parts.add(block.inlines);
        }
        if (block.items != null) {
            parts.addAll(block.items);
        }
        if (block.rows != null) {
            block.rows.forEach(parts::addAll);
        }
        parts.forEach(p -> inlines(p, kind, found));
        if (block.children != null) {
            block.children.forEach(b -> block(b, kind, found));
        }
    }

    private static void inlines(List<Inline> inlines, Token kind, Consumer<String> found) {
        for (Inline i : inlines) {
            if (i.kind == (kind == Token.TABLE ? InlineKind.TABLE : kind == Token.VARIABLE ? InlineKind.VARIABLE
                    : InlineKind.CONSTANT)) {
                found.accept(i.text.substring(1, i.text.length() - 1)); // without [ ], { } or ' '
            }
            inlines(i.children, kind, found);
        }
    }
}
