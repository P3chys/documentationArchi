# documentationArchi

An [Archi](https://www.archimatetool.com) plugin that turns an ArchiMate model into readable documentation: **HTML
pages**, a **PDF document** and **Confluence pages**. You get one page per view, with its picture and a description of
every element on it, formatted from the descriptions you already write in Archi.

Requires Archi 4.9 or later. One plugin file works with every Archi version, on Windows and macOS.

- [Install](#install)
- [Export documentation](#export-documentation)
- [What a page contains](#what-a-page-contains)
- [Print order](#print-order)
- [Formatting descriptions](#formatting-descriptions)
- [Changing how things look](#changing-how-things-look)
- [Assets: pictures and files](#assets-pictures-and-files)
- [Confluence](#confluence)
- [Command line](#command-line)
- [For developers](#for-developers)

---

## Install

1. Download `documentationArchi-<version>.archiplugin` from the
   [releases page](https://github.com/P3chys/documentationArchi/releases).
2. In Archi: **Help > Manage Plug-ins... > Install New...**, choose the file, restart Archi. To upgrade, remove the old
   version in the same dialog first.

| Archi       | Tested in    |
|-------------|--------------|
| 4.9.x       | 4.9.3        |
| 5.0 – 5.3   | 5.1.0, 5.3.0 |
| 5.4 – 5.9   | 5.4.3, 5.9.0 |
| 5.10 and up | 5.10.0       |

## Export documentation

Right-click in the **Models** tree, or in a view (ArchiMate, sketch or canvas), and choose **Export Documentation...**.
What you right-clicked decides what is exported:

| Selected | Exported |
|----------|----------|
| the model | every view, plus an overview page and a page for elements that are in no view |
| a folder | everything in it |
| views | those views |
| elements | their own page ("Selected elements") |
| a relationship | the elements at both ends |
| objects in a view | their elements; a note or group means its view |

The dialog asks for a folder and the formats (HTML, PDF, Confluence). The export goes into a subfolder named after
the model, and you're asked before files in it are replaced. When it finishes, **Open Folder** shows the result:

```
<model>/
  html/index.html, html/<view>.html, html/images/, html/assets/, html/style.css
  <model>.pdf, assets/                 (assets/ holds the files the PDF links to)
  confluence/pages/, confluence/attachments/, confluence/manifest.json, confluence/upload_to_confluence.py
```

The export runs in the background, so you can keep working.

## What a page contains

Each view gets a page with, in this order: the view's description, the list of its elements (with their types), its
picture, its properties, the text of its notes, the views it refers to, and then each element described in full.
That means its description, its properties, its relationships to other elements of the view (in a table, with each
relationship's own description), the views it appears on, and its relationships to elements outside the view,
grouped by type.

- An element is described once, on the first page that shows it. Later pages link to that description, and links
  work in all three formats.
- With more than one page there is an overview page listing all of them. The model's purpose and properties go there.
- The pictures are drawn at twice the size, so they stay sharp in the PDF and when zooming in.
- The PDF has bookmarks for every page.

## Print order

Elements are described in alphabetical order unless you set an order. Right-click a view (in the Models tree or in
the view) and choose **Print Order...**. Move elements with the buttons, **Alt+Up / Alt+Down** or by dragging, then
**OK**. **Remove Order** goes back to alphabetical.

The order is kept in the model, the way modellers often do it by hand: a Grouping called `PrintOrder` on the view,
with associations named `001`, `002`, `003`... from each element to it. You can also draw these yourself. Elements
linked to a PrintOrder come first, by the number in the relationship's name, and the others follow alphabetically.
The PrintOrder element and its links never appear in the export or its pictures. **Undo** takes a change back.

## Formatting descriptions

Write these in any description (of the model, a view, an element or a relationship), in notes and in property
values. They look the same in HTML, the PDF and Confluence (as Confluence's own panels, labels and code blocks).

| Write | Result |
|-------|--------|
| `INFO: text`, `NOTE: text`, `TIP: text`, `WARNING: text` at the start of a line | a coloured panel with that title, up to the next blank line |
| `<#>a remark</#>` (or up to the next blank line) | a note panel without a title |
| `#risk` or `[[risk]]` | a label; built in: `risk` (red), `deprecated` (grey), `todo` and `draft` (yellow), `new` (green) |
| `Description:`, `Technically:` anywhere | bold |
| `[ORDERS]` | a table name, coloured |
| `'ACTIVE'` | a constant, coloured |
| `{client_id}` | a variable, coloured |
| `<sql>select ...</sql>` | an SQL code block (in Confluence: the code macro with SQL highlighting) |
| `https://...` | a link |
| `[[asset:file.png]]` | a picture or file from the Assets folder, see [Assets](#assets-pictures-and-files) |
| blank line | a new paragraph; single line breaks stay |

Keywords ignore case. Labels and panels only work for configured keywords (see below), so other `#words` stay as
they are.

## Changing how things look

**Tools > Documentation Formatting...** (the same list as **Preferences > Documentation Export**) lists everything you
can change:

- **Page parts:** the colour, size and effects of headings (levels 1–4), of the `[table]`, `'constant'` and `{variable}`
  tokens, and of SQL blocks, including Confluence's code theme and line numbers. **Reset** restores the default.
- **Keywords:** **Add**, **Edit**, **Remove** and reorder your own:
  - *Text style:* this text always gets a colour, a background, a size, bold, italic, underline or strikethrough.
  - *Label:* `#keyword` / `[[keyword]]` becomes a grey, red, yellow, green or blue label.
  - *Panel:* a line starting with `keyword:` becomes an info, note, tip or warning panel.

  Each style editor shows a live preview.
- **Edit as Text...** shows all rules as text, one per line, to copy between computers or change many at once:

```
panel warning Open question     a line "Open question: ..." becomes a warning panel
tag red risk                    #risk or [[risk]] becomes a red label
bold Description:               this text is always bold
style color=#bf2600 background=#ffebe6 size=120% bold italic Deadline
token variable color=#6554c0 italic   how {variable} looks; also table and constant
heading 2 color=#0052cc size=120%     how headings of a level (1-4) look
sql background=#f4f5f7 theme=midnight linenumbers
```

Confluence keeps text colour, bold, italic, underline, strikethrough and smaller text. It doesn't keep every
background or larger size in every version, and the style editors say so where it matters.

## Assets: pictures and files

Keep pictures and documents in a folder called `Assets` next to the `.archimate` file (subfolders are fine) and put
them into the documentation from any description, note or property value.

**The easy way:** right-click an element, relationship, view, note or the model (in the Models tree or in a view),
choose **Add Asset...**, and pick one or more files.
- Files from elsewhere are copied into `Assets`, which is created if it's missing. The same file picked again is
  reused; a different file with the same name gets `-2`.
- A `[[asset:...]]` line per file is added at the end of the description.
- **Undo** removes the lines; the copied files stay.
- The model has to be saved first, so the plugin knows where `Assets` is.

**By hand:**

| Write | Result |
|-------|--------|
| `[[asset:procesy/sjednani.png]]` | the picture, shown in the text |
| `[[asset:procesy/sjednani.png\|Průběh sjednání]]` | the same, with that alternative text |
| `[[asset:smlouvy/SLA 2026.pdf\|the SLA]]` | a link "the SLA" that opens the file |
| `asset:sazebnik.csv` | short form, for names without spaces |

A file name alone is enough when only one file in `Assets` has it; otherwise use its path inside `Assets`. Case and
`/` or `\` don't matter. PNG, JPEG, GIF, BMP and SVG are pictures; anything else is a file to open.

**Every export carries its own copies,** so you can zip, mail or move the export folder without breaking anything:

- **HTML:** in `html/assets/`.
- **PDF:** pictures are drawn into the PDF. Other files (and SVG, which the PDF can't draw) are links to the copy
  in `assets/` next to the PDF, which every PDF viewer opens, including Edge and Chrome. The files are also stored
  inside the PDF as attachments, so a PDF sent on its own still carries them (open them from the attachments list of
  Acrobat, Firefox and other viewers that have one).
- **Confluence:** as attachments of the page.

A reference that finds no file shows in red as *[missing asset: name]* on its page and is listed when the export
finishes (on the command line, as errors). Only files really inside `Assets` are used: a link from there to a file
elsewhere, or an `Assets` folder that is itself a link, is ignored and reported. That way a model shared with you
can't pull other files from your computer into an export.

## Confluence

The Confluence export is ready-made pages in Confluence's storage format: links between pages and to elements,
panels, labels, code blocks, pictures and assets as attachments. Upload it with the script it comes with (Python 3.8+,
no packages needed), for Confluence Data Center / Server:

```bash
CONFLUENCE_TOKEN=<personal access token> python <model>/confluence/upload_to_confluence.py --url https://confluence.example.com --space ARCH
```

- `--parent "Existing page"` puts the pages under an existing page.
- Without a token, it uses `CONFLUENCE_USER` and `CONFLUENCE_PASSWORD`.
- Pages are matched by title. Existing ones get a new version, so you can upload the same export again after
  changes.
- Page titles start with the model name, because Confluence titles must be unique in a space.

## Command line

For CI and scheduled documentation builds, through Archi's command line:

```bash
Archi -application com.archimatetool.commandline.app -consoleLog -nosplash \
  --loadModel model.archimate --documentation.folder out [--documentation.formats html,pdf,confluence]
```

This exports the whole model; formats default to all three. Missing assets are logged as errors.

## For developers

Needs JDK 21 and a local Archi installation (its jars are the compile target; default `E:/Archi`, override with
`-ParchiHome=...` or in `gradle.properties`).

The plugin is compiled for Java 11 (`--release 11`), the Java of Archi 4.9, and uses only Archi and Eclipse API that
every Archi from 4.9 to 5.10 has, so one build serves them all. The PDF library (openhtmltopdf, PDFBox) is Java 8
bytecode and travels inside the plugin in `lib/`.

| Path | Content |
|------|---------|
| `core/` | Pure Java engine, no Eclipse: document model and assets (`doc`), keyword formatting (`text`), HTML / PDF / Confluence rendering (`render`) |
| `plugin/` | Archi/Eclipse plugin: menus, export dialog, print order, add asset, preferences, command line provider |
| `sample/` | A sample model to try the export on, with its `Assets` folder |

```bash
just test        # core and plugin tests (plugin tests run headless on the Archi model jars)
just plugin      # build/dist/documentationArchi-<version>.archiplugin
just install     # copy the plugin into your Archi dropins folder (close Archi first, then start it again)
just check       # everything: tests + plugin
```

CI builds and tests `core` on Linux, Windows and macOS. Archi 4.9 keeps its settings and dropins in `Archi4`:
`gradlew :plugin:installDev -ParchiData=Archi4`.

**Windows: don't use PowerShell 7 from the Microsoft Store for `just install` or to start Archi.** Store apps run in a
sandbox that redirects writes to `%APPDATA%` (where Archi keeps dropins and settings) to a private copy, so Archi
would never see the plugin. Use Git Bash, cmd, Windows PowerShell, or PowerShell 7 installed with winget/MSI.

## License

MIT. The bundled libraries keep their own licenses (openhtmltopdf: LGPL 2.1, PDFBox and Commons Logging: Apache 2.0),
see `NOTICE`.
