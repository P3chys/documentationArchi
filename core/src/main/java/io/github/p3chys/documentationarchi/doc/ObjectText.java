package io.github.p3chys.documentationarchi.doc;

/** What an object of a sketch or canvas view says, such as a sticky or a block: its name and its text. */
public final class ObjectText {

    /** Shown in bold above the text; empty for none. */
    public final String name;
    public final String text;

    public ObjectText(String name, String text) {
        this.name = name == null ? "" : name;
        this.text = text == null ? "" : text;
    }
}
