package io.github.p3chys.documentationarchi.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.p3chys.documentationarchi.render.Exporter;
import io.github.p3chys.documentationarchi.text.KeywordRules.Kind;
import io.github.p3chys.documentationarchi.text.KeywordRules.Token;

/** The default look the preferences describe is the one the exporter's stylesheets give. */
class PageElementTest {

    @Test
    void headingAndSqlSizesAreTheStylesheets() throws IOException {
        String html = css("style.css");
        String pdf = css("pdf.css");
        for (PageElement element : PageElement.ALL) {
            if (element.kind == Kind.TOKEN) {
                continue;
            }
            String selector = element.kind == Kind.SQL ? "pre\\.sql" : "h" + element.key;
            assertThat(property(html, selector, "font-size")).as(element.name).isEqualTo(element.htmlPx + "px");
            assertThat(property(pdf, selector, "font-size")).as(element.name).isEqualTo(element.pdfPx + "px");
        }
    }

    @Test
    void textAndSqlColoursAreTheStylesheets() throws IOException {
        String html = css("style.css");

        assertThat(property(html, "body", "color")).isEqualTo(PageElement.TEXT_COLOUR);
        assertThat(property(html, "body", "font-size")).isEqualTo(PageElement.TEXT_PX + "px");
        assertThat(property(html, "pre\\.sql", "background")).isEqualTo(PageElement.SQL_BACKGROUND);
    }

    @Test
    void defaultLookIsSpelledOut() {
        assertThat(PageElement.ALL).allSatisfy(e -> assertThat(e.defaultLook).isNotBlank().doesNotContain("usual"));
        assertThat(PageElement.ALL.get(0).defaultLook).contains(Token.TABLE.builtIn.colour);
    }

    /** The value of a property in the first rule for a selector that sets it. */
    private static String property(String css, String selector, String name) {
        Matcher m = Pattern.compile("(?:^|[\\s,}])" + selector + " \\{([^}]*)}").matcher(css);
        Pattern value = Pattern.compile("(?:^|[;\\s])" + name + ":\\s*([^;}]+?)\\s*(?:;|$)");
        while (m.find()) {
            Matcher v = value.matcher(m.group(1));
            if (v.find()) {
                return v.group(1);
            }
        }
        return null;
    }

    private static String css(String name) throws IOException {
        try (InputStream in = Exporter.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
