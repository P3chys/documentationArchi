package io.github.p3chys.documentationarchi.render;

/** Text for XHTML and Confluence storage format. */
final class Xml {

    private Xml() {
    }

    static String escape(String text) {
        return clean(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Model text can hold characters XML 1.0 forbids, which would make the whole page unreadable. */
    static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(c -> c == 0x9 || c == 0xA || c == 0xD || c >= 0x20 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000).forEach(out::appendCodePoint);
        return out.toString();
    }
}
