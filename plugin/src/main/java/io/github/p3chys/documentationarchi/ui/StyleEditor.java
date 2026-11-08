package io.github.p3chys.documentationarchi.ui;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.ColorSelector;
import org.eclipse.jface.resource.ColorDescriptor;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;

import io.github.p3chys.documentationarchi.text.KeywordRules.TextStyle;

/**
 * Colours, size and effects of a {@link TextStyle}, with a preview of a sample text: first how the export draws it
 * by default ({@link Base}), then the style on top.
 */
final class StyleEditor {

    /** How the export draws a sample without any rule. Colours are {@code #rrggbb}, null for the text's. */
    static final class Base {
        static final Base TEXT = new Base(false, 1, null, false);

        final boolean bold;
        /** Size relative to body text. */
        final float scale;
        final String background;
        final boolean monospace;

        Base(boolean bold, float scale, String background, boolean monospace) {
            this.bold = bold;
            this.scale = scale;
            this.background = background;
            this.monospace = monospace;
        }
    }

    private static final RGB DEFAULT_COLOUR = new RGB(0xbf, 0x26, 0x00);
    private static final RGB DEFAULT_BACKGROUND = new RGB(0xff, 0xf0, 0xb3);

    private final Composite control;
    private final Runnable changed;
    private final LocalResourceManager resources;
    private final Button colourOn;
    private final ColorSelector colour;
    private final Button backgroundOn;
    private final ColorSelector background;
    private final Spinner size;
    private final Button bold;
    private final Button italic;
    private final Button underline;
    private final Button strike;
    private final StyledText preview;
    private Base base = Base.TEXT;
    private String before = "";
    private String sample = "Sample";
    private String after = "";

    /**
     * @param intro how the export draws the sample without a rule, in words, above the settings
     * @param note what the outputs keep of the style, below the preview
     */
    StyleEditor(Composite parent, String intro, String note, Runnable changed) {
        this.changed = changed;
        control = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(4).applyTo(control);
        resources = new LocalResourceManager(JFaceResources.getResources(), control);

        Label look = new Label(control, SWT.WRAP);
        look.setText(intro);
        GridDataFactory.fillDefaults().grab(true, false).span(4, 1).hint(380, SWT.DEFAULT).applyTo(look);

        colourOn = check(control, "Text &colour:");
        colour = new ColorSelector(control);
        colour.setColorValue(DEFAULT_COLOUR);
        colour.addListener(e -> {
            colourOn.setSelection(true);
            update();
        });
        backgroundOn = check(control, "&Highlight:");
        background = new ColorSelector(control);
        background.setColorValue(DEFAULT_BACKGROUND);
        background.addListener(e -> {
            backgroundOn.setSelection(true);
            update();
        });

        new Label(control, SWT.NONE).setText("Si&ze (%):");
        size = new Spinner(control, SWT.BORDER);
        size.setValues(100, TextStyle.MIN_SIZE, TextStyle.MAX_SIZE, 0, 10, 50);
        size.addListener(SWT.Modify, e -> update());
        GridDataFactory.swtDefaults().span(3, 1).applyTo(size);

        Composite effects = new Composite(control, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(4).applyTo(effects);
        GridDataFactory.fillDefaults().span(4, 1).applyTo(effects);
        bold = check(effects, "&Bold");
        italic = check(effects, "&Italic");
        underline = check(effects, "&Underline");
        strike = check(effects, "&Strikethrough");

        Label previewLabel = new Label(control, SWT.NONE);
        previewLabel.setText("Preview:");
        GridDataFactory.fillDefaults().span(4, 1).indent(0, 8).applyTo(previewLabel);
        preview = new StyledText(control, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
        preview.setMargins(6, 6, 6, 6);
        preview.setForeground(resources.create(ColorDescriptor.createFrom(rgb(PageElement.TEXT_COLOUR))));
        GridDataFactory.fillDefaults().grab(true, false).span(4, 1).hint(SWT.DEFAULT, 70).applyTo(preview);

        Label text = new Label(control, SWT.WRAP);
        text.setText(note);
        GridDataFactory.fillDefaults().grab(true, false).span(4, 1).hint(380, SWT.DEFAULT).applyTo(text);
    }

    private Button check(Composite parent, String text) {
        Button b = new Button(parent, SWT.CHECK);
        b.setText(text);
        b.addListener(SWT.Selection, e -> update());
        GridDataFactory.swtDefaults().applyTo(b);
        return b;
    }

    static RGB rgb(String hex) {
        int value = Integer.parseInt(hex.substring(1), 16);
        return new RGB(value >> 16, value >> 8 & 0xff, value & 0xff);
    }

    static String hex(RGB rgb) {
        return String.format("#%02x%02x%02x", rgb.red, rgb.green, rgb.blue);
    }

    Composite control() {
        return control;
    }

    void set(TextStyle style) {
        colourOn.setSelection(style.colour != null);
        if (style.colour != null) {
            colour.setColorValue(rgb(style.colour));
        }
        backgroundOn.setSelection(style.background != null);
        if (style.background != null) {
            background.setColorValue(rgb(style.background));
        }
        size.setSelection(style.size);
        bold.setSelection(style.bold);
        italic.setSelection(style.italic);
        underline.setSelection(style.underline);
        strike.setSelection(style.strike);
        updatePreview();
    }

    TextStyle get() {
        return new TextStyle(colourOn.getSelection() ? hex(colour.getColorValue()) : null,
                backgroundOn.getSelection() ? hex(background.getColorValue()) : null, size.getSelection(),
                bold.getSelection(), italic.getSelection(), underline.getSelection(), strike.getSelection());
    }

    /** The preview shows {@code styled} in the base look and the style, between plain {@code before} and {@code after}. */
    void sample(Base look, String before, String styled, String after) {
        this.base = look;
        this.before = before;
        this.sample = styled.isEmpty() ? "Sample" : styled;
        this.after = after;
        updatePreview();
    }

    private void update() {
        updatePreview();
        changed.run();
    }

    private void updatePreview() {
        preview.setText(before + sample + after);
        TextStyle s = get();
        StyleRange range = new StyleRange();
        range.start = before.length();
        range.length = sample.length();
        if (s.colour != null) {
            range.foreground = resources.create(ColorDescriptor.createFrom(rgb(s.colour)));
        }
        String highlight = s.background != null ? s.background : base.background;
        if (highlight != null) {
            range.background = resources.create(ColorDescriptor.createFrom(rgb(highlight)));
        }
        range.underline = s.underline;
        range.strikeout = s.strike;
        Font font = base.monospace ? JFaceResources.getTextFont() : preview.getFont();
        FontData data = font.getFontData()[0];
        int height = Math.max(1, Math.round(data.getHeight() * base.scale * s.size / 100f));
        int bits = (s.bold || base.bold ? SWT.BOLD : 0) | (s.italic ? SWT.ITALIC : 0);
        range.font = resources.create(FontDescriptor.createFrom(font).setHeight(height).setStyle(bits));
        preview.setStyleRange(range);
    }
}
