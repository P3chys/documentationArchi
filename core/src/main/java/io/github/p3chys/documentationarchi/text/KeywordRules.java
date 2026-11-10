package io.github.p3chys.documentationarchi.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which keywords in descriptions get special formatting, and how the parts of every page look. Written as text, one
 * rule per line:
 * <pre>
 * panel warning Open question     a line "Open question: ..." becomes a warning panel
 * tag red risk                    #risk or [[risk]] becomes a red label
 * bold Description:               this text is always bold
 * style color=#bf2600 background=#ffebe6 size=120% bold italic Deadline   this text always looks like this
 * token variable color=#6554c0 italic   how {variable} looks; also table ([table]) and constant ('constant')
 * heading 2 color=#0052cc size=120%     how headings of a level (1-4) look
 * sql background=#f4f5f7 theme=midnight linenumbers   how SQL blocks look; theme and line numbers are Confluence's
 * exclude label draft             views, elements and relationships labelled #draft stay out of every export
 * hide property internal*         properties with this key are never printed; * at the end matches any ending
 * language cs                     the fixed texts of the output in Czech (default: en)
 * text contents = Table of contents   replaces one fixed text, see {@link Texts}
 * profile developers folders=Views/Sales formats=html,pdf   a named export set-up, see {@link ExportProfile}
 * catalog "Applications" type=Application Component columns=name,description   a table page, see {@link Catalog}
 * index tables                  a page listing every [table] (or variables, constants) of the descriptions
 * page description picture notes  the sections of a view's page to print, in this order ({@link #PAGE_SECTIONS})
 * entry description relations     the parts of an element's description to print, in this order ({@link #ENTRY_PARTS})
 * pdf landscape off               leaves out a part of the PDF's layout ({@link #PDF_PARTS})
 * </pre>
 * Keywords ignore case. Panel styles and tag colours are the ones Confluence offers, so all outputs look alike.
 */
public final class KeywordRules {

    public enum PanelStyle { INFO, NOTE, TIP, WARNING }

    public enum TagColour { GREY, RED, YELLOW, GREEN, BLUE }

    public enum Kind {
        PANEL(false), TAG(false), STYLE(false), TOKEN(false), HEADING(false), SQL(false),
        /** Settings: not about how text looks, so the style lists of the preferences leave them alone. */
        EXCLUDE_LABEL(true), HIDE_PROPERTY(true), LANGUAGE(true), TEXT(true), PAGE(true), ENTRY(true),
        PROFILE(true), CATALOG(true), INDEX(true);

        public final boolean setting;

        Kind(boolean setting) {
            this.setting = setting;
        }
    }

    /** Coloured tokens in descriptions, with how they look when no rule says otherwise. */
    public enum Token {
        TABLE("#0747a6"), CONSTANT("#006644"), VARIABLE("#6554c0");

        public final TextStyle builtIn;

        Token(String colour) {
            builtIn = new TextStyle(colour, null, 100, false, false, false, false);
        }
    }

    /** The themes of Confluence's code block macro. */
    public enum CodeTheme {
        DEFAULT("Default"), CONFLUENCE("Confluence"), DJANGO("DJango"), EMACS("Emacs"), FADETOGREY("FadeToGrey"),
        MIDNIGHT("Midnight"), RDARK("RDark"), ECLIPSE("Eclipse");

        /** The value of the macro's theme parameter. */
        public final String confluenceName;

        CodeTheme(String confluenceName) {
            this.confluenceName = confluenceName;
        }
    }

    public static final int HEADING_LEVELS = 4;

    /** The sections of a view's page, in the usual order. */
    /** The parts of the PDF's layout a {@code pdf} rule turns off or on. */
    public static final List<String> PDF_PARTS = List.of("cover", "contents", "landscape");
    public static final List<String> PAGE_SECTIONS = List.of("description", "contents", "picture", "properties", "notes",
            "related", "elements");

    /** The parts of an element's description, in the usual order. */
    public static final List<String> ENTRY_PARTS = List.of("description", "properties", "relations", "views",
            "workpackages", "other");

    private static final String HELP = String.join("\n",
            "# panel <info|note|tip|warning> <keyword>   line starting with \"keyword:\"",
            "# tag <grey|red|yellow|green|blue> <keyword>   #keyword or [[keyword]] in the text",
            "# bold <text>   this text is always bold",
            "# style <options> <text>   this text always looks like this; quote the text (\"bold\") if it starts like an option",
            "# token <table|constant|variable> <options>   how [table], 'constant' and {variable} look",
            "# heading <1-4> <options>   how headings look (1: page title)",
            "# sql <options> [theme=<confluence theme>] [linenumbers]   how SQL blocks look",
            "# exclude label <keyword>   views, elements and relationships labelled #keyword stay out of every export",
            "# hide property <key>   properties with this key are never printed; * at the end matches any ending",
            "# language <en|cs>   the language of the fixed texts of the output (default: en)",
            "# text <key> = <replacement>   replaces one fixed text, e.g. text contents = Table of contents",
            "# page <section>...   the sections of a view's page to print, in this order: description contents picture properties notes related elements",
            "# entry <part>...   the parts of an element's description to print, in this order: description properties relations views workpackages other",
            "# profile <name> <setting>...   a named export set-up: folders= views= workpackages= formats= exclude= hide= language= output= publish",
            "# catalog \"<title>\" type=<element type>[|<type>] [folder=<path>] columns=name,<property>,description   a table page of the elements of a type",
            "# index <tables|variables|constants>   a page listing every [table], {variable} or 'constant' of the descriptions, with where it is used",
            "# options: [color=#rrggbb] [background=#rrggbb] [size=<50-300>%] [bold] [italic] [underline] [strike]",
            "");

    public static final String DEFAULTS = HELP + String.join("\n",
            "panel info INFO",
            "panel note NOTE",
            "panel tip TIP",
            "panel warning WARNING",
            "tag red risk",
            "tag grey deprecated",
            "tag yellow todo",
            "tag yellow draft",
            "tag green new",
            "bold Description:",
            "bold Technically:",
            "");

    /** How a keyword, token, heading or SQL block looks. Colours are {@code #rrggbb}, null for unchanged. */
    public static final class TextStyle {
        private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})");

        public static final int MIN_SIZE = 50;
        public static final int MAX_SIZE = 300;
        public static final TextStyle PLAIN = new TextStyle(null, null, 100, false, false, false, false);
        public static final TextStyle BOLD = new TextStyle(null, null, 100, true, false, false, false);

        public final String colour;
        public final String background;
        /** Font size in percent of the usual size. */
        public final int size;
        public final boolean bold;
        public final boolean italic;
        public final boolean underline;
        public final boolean strike;

        /** @throws IllegalArgumentException for a colour that is not {@code #rgb} or {@code #rrggbb}, or a size out of range */
        public TextStyle(String colour, String background, int size, boolean bold, boolean italic, boolean underline,
                boolean strike) {
            if (size < MIN_SIZE || size > MAX_SIZE) {
                throw new IllegalArgumentException("size must be " + MIN_SIZE + "-" + MAX_SIZE + " %: " + size);
            }
            this.colour = hex(colour);
            this.background = hex(background);
            this.size = size;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strike = strike;
        }

        private static String hex(String colour) {
            if (colour == null) {
                return null;
            }
            if (!HEX.matcher(colour).matches()) {
                throw new IllegalArgumentException("not a colour like #ff0000: " + colour);
            }
            String digits = colour.startsWith("#") ? colour.substring(1) : colour;
            if (digits.length() == 3) {
                digits = digits.replaceAll("(.)", "$1$1");
            }
            return "#" + digits.toLowerCase(Locale.ROOT);
        }

        /** A {@code #rrggbb} colour as {@code rgb(r,g,b)}, the form Confluence keeps in storage format. */
        public static String rgb(String hex) {
            int value = Integer.parseInt(hex.substring(1), 16);
            return "rgb(" + (value >> 16) + "," + (value >> 8 & 0xff) + "," + (value & 0xff) + ")";
        }

        /** The same style without its background, for where the background goes on a whole block. */
        public TextStyle withoutBackground() {
            return new TextStyle(colour, null, size, bold, italic, underline, strike);
        }

        boolean onlyBold() {
            return equals(BOLD);
        }

        /** The options as written in a {@code style} rule, e.g. {@code color=#ff0000 size=120% bold}. */
        public String options() {
            List<String> options = new ArrayList<>();
            if (colour != null) {
                options.add("color=" + colour);
            }
            if (background != null) {
                options.add("background=" + background);
            }
            if (size != 100) {
                options.add("size=" + size + "%");
            }
            if (bold) {
                options.add("bold");
            }
            if (italic) {
                options.add("italic");
            }
            if (underline) {
                options.add("underline");
            }
            if (strike) {
                options.add("strike");
            }
            return String.join(" ", options);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TextStyle)) {
                return false;
            }
            TextStyle s = (TextStyle) o;
            return Objects.equals(colour, s.colour) && Objects.equals(background, s.background) && size == s.size
                    && bold == s.bold && italic == s.italic && underline == s.underline && strike == s.strike;
        }

        @Override
        public int hashCode() {
            return Objects.hash(colour, background, size, bold, italic, underline, strike);
        }

        @Override
        public String toString() {
            return options();
        }
    }

    /**
     * One line of the rules. {@link #panel} is set for a panel, {@link #tag} for a label, {@link #style} for the rest;
     * {@link #keyword} is the token name, the heading level or {@code sql} for those.
     */
    public static final class Rule {
        public final Kind kind;
        public final String keyword;
        public final PanelStyle panel;
        public final TagColour tag;
        public final TextStyle style;
        /** Confluence's theme for an SQL block, null for Confluence's default. */
        public final CodeTheme theme;
        public final boolean lineNumbers;
        /** For a {@code text} rule: what replaces the text. */
        public final String value;
        /** For a {@code profile} rule: the set-up. */
        public final ExportProfile profile;
        /** For a {@code catalog} rule: the table. */
        public final Catalog catalog;

        private Rule(Kind kind, String keyword, PanelStyle panel, TagColour tag, TextStyle style, CodeTheme theme,
                boolean lineNumbers) {
            this(kind, keyword, panel, tag, style, theme, lineNumbers, null);
        }

        private Rule(Kind kind, String keyword, PanelStyle panel, TagColour tag, TextStyle style, CodeTheme theme,
                boolean lineNumbers, String value) {
            this(kind, keyword, panel, tag, style, theme, lineNumbers, value, null);
        }

        private Rule(Kind kind, String keyword, PanelStyle panel, TagColour tag, TextStyle style, CodeTheme theme,
                boolean lineNumbers, String value, ExportProfile profile) {
            this(kind, keyword, panel, tag, style, theme, lineNumbers, value, profile, null);
        }

        private Rule(Kind kind, String keyword, PanelStyle panel, TagColour tag, TextStyle style, CodeTheme theme,
                boolean lineNumbers, String value, ExportProfile profile, Catalog catalog) {
            if (keyword == null || keyword.isBlank()) {
                throw new IllegalArgumentException("the keyword is empty");
            }
            if (keyword.contains("\n") || keyword.contains("\r")) {
                throw new IllegalArgumentException("the keyword must be on one line");
            }
            this.kind = kind;
            this.keyword = keyword.strip();
            this.panel = panel;
            this.tag = tag;
            this.style = style;
            this.theme = theme;
            this.lineNumbers = lineNumbers;
            this.value = value;
            this.profile = profile;
            this.catalog = catalog;
        }

        public static Rule panel(String keyword, PanelStyle style) {
            return new Rule(Kind.PANEL, keyword, Objects.requireNonNull(style), null, null, null, false);
        }

        public static Rule tag(String keyword, TagColour colour) {
            return new Rule(Kind.TAG, keyword, null, Objects.requireNonNull(colour), null, null, false);
        }

        public static Rule style(String keyword, TextStyle style) {
            return new Rule(Kind.STYLE, keyword, null, null, Objects.requireNonNull(style), null, false);
        }

        public static Rule token(Token token, TextStyle style) {
            return new Rule(Kind.TOKEN, lower(token), null, null, Objects.requireNonNull(style), null, false);
        }

        /** @param level 1 (page title) to {@link #HEADING_LEVELS} */
        public static Rule heading(int level, TextStyle style) {
            if (level < 1 || level > HEADING_LEVELS) {
                throw new IllegalArgumentException("heading level must be 1-" + HEADING_LEVELS + ": " + level);
            }
            return new Rule(Kind.HEADING, String.valueOf(level), null, null, Objects.requireNonNull(style), null, false);
        }

        /** @param theme Confluence's theme, null for its default */
        public static Rule sql(TextStyle style, CodeTheme theme, boolean lineNumbers) {
            return new Rule(Kind.SQL, "sql", null, null, Objects.requireNonNull(style), theme, lineNumbers);
        }

        /** @param label the keyword, without # */
        public static Rule excludeLabel(String label) {
            return new Rule(Kind.EXCLUDE_LABEL, label.startsWith("#") ? label.substring(1) : label, null, null, null,
                    null, false);
        }

        /** @param key a property key; {@code *} at the end stands for any ending */
        public static Rule hideProperty(String key) {
            return new Rule(Kind.HIDE_PROPERTY, key, null, null, null, null, false);
        }

        /** @param language one of {@link Texts#LANGUAGES} */
        public static Rule language(String language) {
            if (!Texts.LANGUAGES.contains(language.strip().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("unknown language \"" + language + "\", use "
                        + String.join(" or ", Texts.LANGUAGES));
            }
            return new Rule(Kind.LANGUAGE, language.strip().toLowerCase(Locale.ROOT), null, null, null, null, false);
        }

        /** @param key one of {@link Texts#keys()} @param replacement the text to print instead */
        public static Rule text(String key, String replacement) {
            if (!Texts.exists(key.strip())) {
                throw new IllegalArgumentException("unknown text \"" + key + "\", use one of "
                        + String.join(", ", Texts.keys()));
            }
            if (replacement.isBlank() || replacement.contains("\n") || replacement.contains("\r")) {
                throw new IllegalArgumentException("the replacement must be one line of text");
            }
            return new Rule(Kind.TEXT, key.strip().toLowerCase(Locale.ROOT), null, null, null, null, false,
                    replacement.strip());
        }

        /** @param token the kind of token to list; the rule is written {@code index tables}, {@code variables} or {@code constants} */
        public static Rule index(Token token) {
            return new Rule(Kind.INDEX, lower(token) + "s", null, null, null, null, false);
        }

        public static Rule catalog(Catalog catalog) {
            return new Rule(Kind.CATALOG, catalog.title, null, null, null, null, false, null, null, catalog);
        }

        public static Rule profile(ExportProfile profile) {
            return new Rule(Kind.PROFILE, profile.name, null, null, null, null, false, null, profile);
        }

        /** @param sections names out of {@link KeywordRules#PAGE_SECTIONS}, in the order to print them */
        public static Rule page(List<String> sections) {
            return new Rule(Kind.PAGE, names(sections, PAGE_SECTIONS, "section"), null, null, null, null, false);
        }

        /** @param parts names out of {@link KeywordRules#ENTRY_PARTS}, in the order to print them */
        public static Rule entry(List<String> parts) {
            return new Rule(Kind.ENTRY, names(parts, ENTRY_PARTS, "part"), null, null, null, null, false);
        }

        private static String names(List<String> names, List<String> known, String what) {
            List<String> checked = new ArrayList<>();
            for (String name : names) {
                String n = name.toLowerCase(Locale.ROOT);
                if (!known.contains(n)) {
                    throw new IllegalArgumentException("unknown " + what + " \"" + name + "\", use "
                            + String.join(", ", known));
                }
                if (checked.contains(n)) {
                    throw new IllegalArgumentException("the " + what + " \"" + name + "\" is there twice");
                }
                checked.add(n);
            }
            if (checked.isEmpty()) {
                throw new IllegalArgumentException("expected the " + what + "s to print: " + String.join(" ", known));
            }
            return String.join(" ", checked);
        }

        /** The rule as one line of text, which {@link KeywordRules#parse} reads back. */
        public String line() {
            switch (kind) {
                case PAGE: return "page " + keyword;
                case ENTRY: return "entry " + keyword;
                case LANGUAGE: return "language " + keyword;
                case PROFILE: return "profile " + profile.text();
                case CATALOG: return "catalog " + catalog.text();
                case INDEX: return "index " + keyword;
                case TEXT: return "text " + keyword + " = " + value;
                case EXCLUDE_LABEL: return "exclude label " + keyword;
                case HIDE_PROPERTY: return "hide property " + keyword;
                case PANEL: return "panel " + lower(panel) + " " + keyword;
                case TAG: return "tag " + lower(tag) + " " + keyword;
                case TOKEN: return join("token " + keyword, style.options());
                case HEADING: return join("heading " + keyword, style.options());
                case SQL:
                    return join(join("sql", style.options()), join(theme == null ? "" : "theme=" + lower(theme),
                            lineNumbers ? "linenumbers" : ""));
                default:
                    if (style.onlyBold()) {
                        return "bold " + keyword;
                    }
                    return join(join("style", style.options()), needsQuotes(keyword) ? "\"" + keyword + "\"" : keyword);
            }
        }

        private static String join(String a, String b) {
            return a.isEmpty() ? b : b.isEmpty() ? a : a + " " + b;
        }

        private static boolean needsQuotes(String keyword) {
            return isOption(keyword.split("\\s+", 2)[0]) || keyword.startsWith("\"") && keyword.endsWith("\"");
        }

        String key() {
            if (kind == Kind.LANGUAGE || kind == Kind.PAGE || kind == Kind.ENTRY) {
                return kind.name(); // one language
            }
            return kind + " " + keyword.toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Rule && line().equals(((Rule) o).line());
        }

        @Override
        public int hashCode() {
            return line().hashCode();
        }

        @Override
        public String toString() {
            return line();
        }
    }

    private final List<Rule> rules;
    private final Map<String, PanelStyle> panels = new LinkedHashMap<>();
    private final Map<String, TagColour> tags = new LinkedHashMap<>();
    private final List<String> excludedLabels = new ArrayList<>();
    private final List<String> hiddenProperties = new ArrayList<>();
    private final Map<String, ExportProfile> profiles = new LinkedHashMap<>();
    private final List<Catalog> catalogs = new ArrayList<>();
    private final List<Token> indexes = new ArrayList<>();
    private Texts fixedTexts = Texts.english();
    private List<String> pageSections = PAGE_SECTIONS;
    private List<String> entryParts = ENTRY_PARTS;
    private final Set<String> pdfOff = new HashSet<>();
    private final Map<Token, TextStyle> tokens = new LinkedHashMap<>();
    private final TextStyle[] headings = new TextStyle[HEADING_LEVELS + 1];
    private final List<TextStyle> styles = new ArrayList<>();
    private Rule sql;
    private Pattern styled;

    private KeywordRules(List<Rule> rules) {
        this.rules = Collections.unmodifiableList(rules);
        List<Rule> texts = new ArrayList<>();
        for (Token token : Token.values()) {
            tokens.put(token, token.builtIn);
        }
        String language = "en";
        Map<String, String> replaced = new LinkedHashMap<>();
        for (Rule rule : rules) {
            switch (rule.kind) {
                case LANGUAGE: language = rule.keyword; break;
                case PAGE: pageSections = List.of(rule.keyword.split(" ")); break;
                case ENTRY: entryParts = List.of(rule.keyword.split(" ")); break;
                case PDF:
                    if (rule.value.equals("off")) {
                        pdfOff.add(rule.keyword);
                    } else {
                        pdfOff.remove(rule.keyword);
                    }
                    break;
                case TEXT: replaced.put(rule.keyword, rule.value); break;
                case PANEL: panels.put(rule.keyword.toLowerCase(Locale.ROOT), rule.panel); break;
                case TAG: tags.put(rule.keyword.toLowerCase(Locale.ROOT), rule.tag); break;
                case TOKEN: tokens.put(Token.valueOf(rule.keyword.toUpperCase(Locale.ROOT)), rule.style); break;
                case HEADING: headings[Integer.parseInt(rule.keyword)] = rule.style; break;
                case SQL: sql = rule; break;
                case EXCLUDE_LABEL: excludedLabels.add(rule.keyword.toLowerCase(Locale.ROOT)); break;
                case HIDE_PROPERTY: hiddenProperties.add(rule.keyword.toLowerCase(Locale.ROOT)); break;
                case PROFILE: profiles.put(rule.keyword.toLowerCase(Locale.ROOT), rule.profile); break;
                case CATALOG: catalogs.add(rule.catalog); break;
                case INDEX: indexes.add(Token.valueOf(rule.keyword.substring(0, rule.keyword.length() - 1).toUpperCase(Locale.ROOT))); break;
                default: texts.add(rule);
            }
        }
        fixedTexts = new Texts(language, replaced);
        // the longest text first, so "Key point" wins over "Key"; one numbered group per text tells which one matched
        texts.sort(Comparator.comparingInt((Rule r) -> r.keyword.length()).reversed());
        StringBuilder alternatives = new StringBuilder();
        for (Rule rule : texts) {
            alternatives.append(styles.isEmpty() ? "" : "|").append("(?<k").append(styles.size()).append(">")
                    .append(wholeWords(rule.keyword)).append(")");
            styles.add(rule.style);
        }
        styled = Pattern.compile(texts.isEmpty() ? "(?!)" : "(?iu)(?<![\\p{L}\\p{N}])(?:" + alternatives + ")");
    }

    public static KeywordRules defaults() {
        return parse(DEFAULTS);
    }

    /** Rules in this order; a later rule for the same keyword and kind replaces an earlier one. */
    public static KeywordRules of(List<Rule> rules) {
        Map<String, Rule> unique = new LinkedHashMap<>();
        for (Rule rule : rules) {
            unique.put(rule.key(), rule);
        }
        return new KeywordRules(new ArrayList<>(unique.values()));
    }

    /** @throws IllegalArgumentException naming the first line that is not a valid rule */
    public static KeywordRules parse(String text) {
        List<Rule> rules = new ArrayList<>();
        int number = 0;
        for (String line : text.split("\\R")) {
            number++;
            String rule = line.strip();
            if (rule.isEmpty() || rule.startsWith("#")) {
                continue;
            }
            try {
                Rule parsed = rule(rule);
                rules.add(parsed.kind == Kind.PROFILE ? Rule.profile(parsed.profile.atLine(number)) : parsed);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + number + ": " + e.getMessage() + ": " + rule, e);
            }
        }
        return of(rules);
    }

    private static Rule rule(String rule) {
        String[] start = rule.split("\\s+", 2);
        String type = start[0].toLowerCase(Locale.ROOT);
        String rest = start.length == 2 ? start[1] : "";
        switch (type) {
            case "bold":
                if (!rest.isEmpty()) {
                    return Rule.style(rest, TextStyle.BOLD);
                }
                break;
            case "style": {
                Options o = Options.read(rest, false);
                if (o.rest.length() >= 2 && o.rest.startsWith("\"") && o.rest.endsWith("\"")) {
                    o.rest = o.rest.substring(1, o.rest.length() - 1);
                }
                if (o.rest.isBlank()) {
                    throw new IllegalArgumentException("expected style options and then the text to style");
                }
                return Rule.style(o.rest, o.style());
            }
            case "token": {
                String[] name = rest.split("\\s+", 2);
                Token token;
                try {
                    token = Token.valueOf(name[0].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("token must be table, constant or variable, not \"" + name[0] + "\"");
                }
                return Rule.token(token, Options.read(name.length == 2 ? name[1] : "", false).only().style());
            }
            case "heading": {
                String[] level = rest.split("\\s+", 2);
                if (!level[0].matches("[1-9]")) {
                    throw new IllegalArgumentException("heading level must be 1-" + HEADING_LEVELS + ", not \"" + level[0] + "\"");
                }
                return Rule.heading(Integer.parseInt(level[0]),
                        Options.read(level.length == 2 ? level[1] : "", false).only().style());
            }
            case "sql": {
                Options o = Options.read(rest, true).only();
                return Rule.sql(o.style(), o.theme, o.lineNumbers);
            }
            case "page":
                return Rule.page(words(rest));
            case "entry":
                return Rule.entry(words(rest));
            case "language":
                return Rule.language(rest);
            case "pdf": {
                String[] w = rest.strip().toLowerCase(Locale.ROOT).split("\\s+");
                if (w.length != 2 || !w[1].equals("on") && !w[1].equals("off")) {
                    throw new IllegalArgumentException("expected: pdf <" + String.join("|", PDF_PARTS) + "> <on|off>");
                }
                return Rule.pdf(w[0], w[1].equals("on"));
            }
            case "text": {
                int equals = rest.indexOf('=');
                if (equals < 0) {
                    throw new IllegalArgumentException("expected: text <key> = <replacement>");
                }
                return Rule.text(rest.substring(0, equals).strip(), rest.substring(equals + 1).strip());
            }
            case "exclude": {
                String[] label = rest.split("\\s+", 2);
                if (!label[0].equalsIgnoreCase("label") || label.length < 2 || label[1].isBlank()) {
                    throw new IllegalArgumentException("expected: exclude label <keyword>");
                }
                return Rule.excludeLabel(oneWord(label[1], "keyword"));
            }
            case "index": {
                Token token = null;
                for (Token t : Token.values()) {
                    if (rest.strip().equalsIgnoreCase(lower(t) + "s")) {
                        token = t;
                    }
                }
                if (token == null) {
                    throw new IllegalArgumentException("expected: index tables, index variables or index constants");
                }
                return Rule.index(token);
            }
            case "catalog":
                return Rule.catalog(Catalog.parse(rest));
            case "profile":
                return Rule.profile(ExportProfile.parse(rest));
            case "hide": {
                String[] key = rest.split("\\s+", 2);
                if (!key[0].equalsIgnoreCase("property") || key.length < 2 || key[1].isBlank()) {
                    throw new IllegalArgumentException("expected: hide property <key>");
                }
                return Rule.hideProperty(key[1].strip());
            }
            default:
        }
        String[] parts = rule.split("\\s+", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("expected panel|tag, a style and a keyword");
        }
        String style = parts[1].toUpperCase(Locale.ROOT);
        try {
            if (type.equals("panel")) {
                return Rule.panel(parts[2], PanelStyle.valueOf(style));
            } else if (type.equals("tag")) {
                return Rule.tag(parts[2], TagColour.valueOf(style));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown style \"" + parts[1] + "\"");
        }
        throw new IllegalArgumentException("must start with panel, tag, bold, style, token, heading, sql, exclude, hide, language, text, page, entry, profile, catalog or index");
    }

    /** The options in front of what a rule is about, and what follows them. */
    private static final class Options {
        String colour;
        String background;
        int size = 100;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        CodeTheme theme;
        boolean lineNumbers;
        String rest;

        /** @param code whether the SQL block options are allowed too */
        static Options read(String text, boolean code) {
            Options o = new Options();
            String remaining = text.strip();
            while (!remaining.isEmpty()) {
                String[] split = remaining.split("\\s+", 2);
                String option = split[0].toLowerCase(Locale.ROOT);
                if (!isOption(option) && !(code && option.equals("linenumbers"))) {
                    break;
                }
                String value = option.contains("=") ? option.substring(option.indexOf('=') + 1) : null;
                if (option.startsWith("color=") || option.startsWith("colour=")) {
                    o.colour = value;
                } else if (option.startsWith("background=")) {
                    o.background = value;
                } else if (option.startsWith("size=")) {
                    try {
                        o.size = Integer.parseInt(value.endsWith("%") ? value.substring(0, value.length() - 1) : value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("size is a percentage like 120%, not \"" + value + "\"");
                    }
                } else if (option.equals("bold")) {
                    o.bold = true;
                } else if (option.equals("italic")) {
                    o.italic = true;
                } else if (option.equals("underline")) {
                    o.underline = true;
                } else if (option.equals("strike")) {
                    o.strike = true;
                } else if (code && option.startsWith("theme=")) {
                    try {
                        o.theme = CodeTheme.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("unknown theme \"" + value + "\"");
                    }
                } else if (code && option.equals("linenumbers")) {
                    o.lineNumbers = true;
                } else {
                    throw new IllegalArgumentException("unknown option \"" + split[0] + "\"");
                }
                remaining = split.length == 2 ? split[1] : "";
            }
            o.rest = remaining;
            return o;
        }

        Options only() {
            if (!rest.isEmpty()) {
                throw new IllegalArgumentException("unknown option \"" + rest.split("\\s+", 2)[0] + "\"");
            }
            return this;
        }

        TextStyle style() {
            return new TextStyle(colour, background, size, bold, italic, underline, strike);
        }
    }

    private static List<String> words(String text) {
        return text.isBlank() ? List.of() : List.of(text.strip().split("\\s+"));
    }

    private static String oneWord(String text, String what) {
        String word = text.strip();
        if (word.startsWith("#")) {
            word = word.substring(1);
        }
        if (word.isEmpty() || word.matches(".*\\s.*")) {
            throw new IllegalArgumentException("the " + what + " must be one word");
        }
        return word;
    }

    private static boolean isOption(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return w.contains("=") || w.equals("bold") || w.equals("italic") || w.equals("underline") || w.equals("strike");
    }

    private static String wholeWords(String text) {
        boolean endsInWord = Character.isLetterOrDigit(text.charAt(text.length() - 1));
        return Pattern.quote(text) + (endsInWord ? "(?![\\p{L}\\p{N}])" : "");
    }

    /** All rules, in order. */
    public List<Rule> rules() {
        return rules;
    }

    /** The rules as text for {@link #parse}, with a short explanation of the syntax at the top. */
    public String format() {
        StringBuilder text = new StringBuilder(HELP);
        rules.forEach(r -> text.append(r.line()).append("\n"));
        return text.toString();
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** How a token looks: its rule, or its built-in colour. */
    public TextStyle token(Token token) {
        return tokens.get(token);
    }

    /** How headings of a level look, null for unchanged. */
    public TextStyle heading(int level) {
        return level >= 1 && level <= HEADING_LEVELS ? headings[level] : null;
    }

    /** How SQL blocks look, null for unchanged. */
    public Rule sql() {
        return sql;
    }

    PanelStyle panel(String keyword) {
        return panels.get(keyword.toLowerCase(Locale.ROOT));
    }

    /** The colour of a label, null when the keyword is no label. */
    public TagColour tag(String keyword) {
        return tags.get(keyword.toLowerCase(Locale.ROOT));
    }

    /** The configured labels, in the order of the rules. */
    public List<String> labels() {
        List<String> labels = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.kind == Kind.TAG && labels.stream().noneMatch(rule.keyword::equalsIgnoreCase)) {
                labels.add(rule.keyword);
            }
        }
        return labels;
    }

    /** The sections of a view's page to print, in order: all of them in the usual order unless a rule says so. */
    public List<String> pageSections() {
        return pageSections;
    }

    /** Whether the PDF has this part of its layout, one of {@link #PDF_PARTS}: unless a rule turns it off. */
    public boolean pdf(String part) {
        return !pdfOff.contains(part);
    }

    /** The parts of an element's description to print, in order. */
    public List<String> entryParts() {
        return entryParts;
    }

    /** The fixed texts of the output: the language of the rules, with the replacements. */
    public Texts texts() {
        return fixedTexts;
    }

    /** The labels (without #) that leave a view, element or relationship out of every export, lower case. */
    public List<String> excludedLabels() {
        return Collections.unmodifiableList(excludedLabels);
    }

    /** Whether properties with this key are never printed: a {@code hide property} rule matches it. */
    public boolean hidesProperty(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        for (String hidden : hiddenProperties) {
            if (hidden.endsWith("*") ? k.startsWith(hidden.substring(0, hidden.length() - 1)) : k.equals(hidden)) {
                return true;
            }
        }
        return false;
    }

    /** The export profiles of the rules, in the order of the rules. */
    public List<ExportProfile> profiles() {
        return Collections.unmodifiableList(new ArrayList<>(profiles.values()));
    }

    /** The catalogs of the rules, in the order of the rules. */
    public List<Catalog> catalogs() {
        return Collections.unmodifiableList(catalogs);
    }

    /** The kinds of token that get an index page, in the order of the rules. */
    public List<Token> indexes() {
        return Collections.unmodifiableList(indexes);
    }

    /** The profile with this name (ignoring case), null if there is none. */
    public ExportProfile profile(String name) {
        return profiles.get(name.strip().toLowerCase(Locale.ROOT));
    }

    /** These rules with what a profile adds: the labels to leave out, the properties to hide, its language. */
    public KeywordRules with(ExportProfile profile) {
        List<Rule> all = new ArrayList<>(rules);
        profile.exclude.forEach(label -> all.add(Rule.excludeLabel(label)));
        profile.hide.forEach(key -> all.add(Rule.hideProperty(key)));
        if (profile.language != null) {
            all.add(Rule.language(profile.language));
        }
        return of(all);
    }

    /** Matches any styled text; {@link #style} tells which one. */
    Pattern styled() {
        return styled;
    }

    TextStyle style(Matcher match) {
        for (int i = 0; i < styles.size(); i++) {
            if (match.group("k" + i) != null) {
                return styles.get(i);
            }
        }
        throw new IllegalStateException("no styled text matched");
    }
}
