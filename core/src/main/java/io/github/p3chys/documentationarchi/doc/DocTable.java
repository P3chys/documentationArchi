package io.github.p3chys.documentationarchi.doc;

import java.util.ArrayList;
import java.util.List;

/** A table of a generated page, such as a catalog or an index: a header and rows of cells. */
public final class DocTable {

    /** What a cell shows: links to where things are described, else a description, else plain text. */
    public static final class Cell {
        public final List<Link> links;
        public final String text;
        /** The text is a description (paragraphs, formatting); else a property value. */
        public final boolean description;

        private Cell(List<Link> links, String text, boolean description) {
            this.links = links;
            this.text = text;
            this.description = description;
        }

        public static Cell links(List<Link> links) {
            return new Cell(List.copyOf(links), "", false);
        }

        public static Cell description(String text) {
            return new Cell(List.of(), text == null ? "" : text, true);
        }

        public static Cell value(String text) {
            return new Cell(List.of(), text == null ? "" : text, false);
        }
    }

    public final List<String> header = new ArrayList<>();
    public final List<List<Cell>> rows = new ArrayList<>();

    public DocTable(List<String> header) {
        this.header.addAll(header);
    }
}
