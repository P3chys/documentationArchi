# documentationArchi

An [Archi](https://www.archimatetool.com) plugin that turns an ArchiMate model into readable documentation: **HTML
pages**, a **PDF document** and **Confluence pages**. You get one page per view, with its picture and a description of
every element on it, formatted from the descriptions you already write in Archi.

Requires Archi 4.9 or later. One plugin file works with every Archi version, on Windows and macOS.

**New here? Read the [User Guide](docs/user-guide.md)** ([PDF](docs/user-guide.pdf)) – installation and every
function, with screenshots.

- [Install](#install)
- [Export documentation](#export-documentation)
- [What a page contains](#what-a-page-contains)
- [Print order](#print-order)
- [Work packages (Jira tickets)](#work-packages-jira-tickets)
- [What changed since the last export](#what-changed-since-the-last-export)
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
| the model | every view, plus an overview page, a page for elements that are in no view, one for the work packages and one for the labels |
| work packages | only the elements they change, see [Work packages](#work-packages-jira-tickets) |
| a folder | everything in it |
| views | those views |
| elements | their own page ("Selected elements") |
| a relationship | the elements at both ends |
| objects in a view | their elements; a note or group means its view |

The dialog asks for a folder and the formats (HTML, PDF, Confluence). The export goes into a subfolder named after
the model (or the work package), and you're asked before files in it are replaced. When it finishes, **Open Folder** shows the result:

```
<model>/
  html/index.html, html/<view>.html, html/images/, html/assets/, html/style.css
  <model>.pdf, assets/                 (assets/ holds the files the PDF links to)
  confluence/pages/, confluence/attachments/, confluence/manifest.json, confluence/upload_to_confluence.py
```

The export runs in the background, so you can keep working; the Progress view shows the view being drawn and the
format being written, and **Cancel** there stops it. A re-export replaces the folders it owns (`html/`, `assets/`,
`confluence/` and the PDF, each only if that format is exported now), so files of renamed or deleted views do not
stay behind. The new files are written next to the old ones and swapped in at the end: a cancelled or failed export
leaves the previous one as it was, and the model tree marks do not change. Nothing else in the folder is touched.

## What a page contains

The overview page ends with one line, and the first page of the PDF as well: "Generated 2026-11-10 14:03 from
&lt;model&gt;". When the model sits in a cooperativeArchi workspace (a `.coarchi` folder next to the model file) it
continues with " – &lt;branch&gt; ＠ &lt;short commit&gt;", read from that repository's files; the Confluence pages
don't have the line.

Each view gets a page with, in this order: the view's description, the list of its elements (with their types), its
picture, its properties, the text of its notes, the views it refers to, and then each element described in full.
That means its description, its properties, its relationships to other elements of the view (in a table, with each
relationship's own description), the views it appears on, and its relationships to elements outside the view,
grouped by type.

- An element is described once, on the first page that shows it. Later pages link to that description, and links
  work in all three formats.
- To choose the page yourself, select the elements in a view, right-click and choose **Describe Here**: the export
  then describes them on that view's page whenever the view is part of the export (otherwise on the first page, as
  usual). **Describe Here** on elements already described there takes it back, and **Undo** does too. It is kept in
  the model as a `doc:home` property of the element (the view's id), which the export never shows.
- With more than one page there is an overview page listing all of them. The model's purpose and properties go there.
- The pictures are drawn at twice the size, so they stay sharp in the PDF and when zooming in.
- The PDF has bookmarks for every page.
- Elements that sit in a group of the view (Archi's visual *Group*) come under the group's name, in the list of
  elements and among the descriptions, group after group as they are on the view; the print order applies inside
  each group. Elements in no group come first.
- An element drawn inside another one, with no relationship between the two, says so in its description:
  *Part of: Core System*, and the other one *Contains: Billing Module*.
- An element or relationship with a specialization (Archi's **Specializations Manager**) shows it with its type:
  *<<Business Actor: Broker>>*.
- A sketch or canvas view has its picture and, under *Notes*, the texts of its objects: each sticky, actor, block
  and group with its name in bold and its text (a canvas sticky's notes too), formatted like any description.
- Junctions are never described or listed. A relationship to a junction is followed through it (and through
  junctions after it) to the elements on the other side, and shown as one relationship to all of them: *→ Triggering:
  Check claim or Reject claim* for an Or junction, *and* for an And junction.

## Print order

Elements are described in alphabetical order unless you set an order. Right-click a view (in the Models tree or in
the view) and choose **Print Order...**. Move elements with the buttons, **Alt+Up / Alt+Down** or by dragging, then
**OK**. **Remove Order** goes back to alphabetical.

The order is kept in the model, the way modellers often do it by hand: a Grouping called `PrintOrder` on the view,
with associations named `001`, `002`, `003`... from each element to it. You can also draw these yourself. Elements
linked to a PrintOrder come first, by the number in the relationship's name, and the others follow alphabetically.
The PrintOrder element and its links never appear in the export or its pictures. **Undo** takes a change back.

## Work packages (Jira tickets)

A work package is an ArchiMate **Work Package** element named after its Jira ticket, such as `RBWDP-20025`. Linking
an element to it says that the element changes within that ticket, and exporting the work package gives the
developers a specification of just those elements.

**Link elements:** select one or more elements (in the Models tree or in a view), right-click, **Link to Work
Package...**. Tick the work packages that change them, untick to take them off, or type a new ticket key under
**New work package** (it is created in *Implementation & Migration*). A grey tick means only some of the selected
elements are linked; it stays as it is unless you click it. **Undo** takes the change back.

The links are plain associations between the element and the work package, either way round, so you can also draw
them yourself. An element can be in several work packages, and the model can have as many as you need.

**Export a work package:** right-click it and choose **Export Documentation...** (several selected work packages go
into one export). The export holds:
- a first page named after the ticket, with the work package's description and properties (the specification) and
  the list of the elements it changes,
- the views that show any of these elements, each with its full picture but describing only those elements,
- the elements that are on no view.

**Overview:** right-click anything in a model and choose **Work Packages...**. It lists every work package with the
number of elements it changes and how many of them no export shows as they are now (see below). Select one or more
to see their elements, then **Export...** them or **Show in Model Tree**.

In every export, an element's description ends with the work packages that change it (*Work packages: RBWDP-20025*)
instead of listing the links among its relationships. A whole-model export has a *Work packages* page describing each
ticket with the elements it changes.

## What changed since the last export

The Models tree shows what no documentation export reflects yet, with a small mark after the name:

| Mark | Means |
|------|-------|
| orange dot | the element, relationship or view changed since the last export that showed it |
| blue dot | no export showed it yet (a new element, or one never exported) |
| orange / blue ring | a folder (or the model) holding such objects, so you find them with the tree collapsed |
| light orange dot / ring | only the layout of a view changed since (a box moved or resized, bend points, fonts, colours); what it says is as exported |

Tick **Ignore layout-only changes** in the preferences (default off) to hide the light orange marks.

Hover over a mark to see what it means and when the object was last exported. Any export counts: export a work
package and its elements are up to date again. A view counts as changed when anything on it moves or changes, or an
element on it is renamed, since its picture changes. PrintOrder and its links never get a mark.

The plugin remembers what each export showed in Archi's own settings folder (per model, per computer), not in the
model, so exporting doesn't change the model. To hide the marks, untick **Mark in the model tree what no export shows
yet** in **Preferences > Documentation Export**.

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
| `- item`, `* item`, `1. item` at the start of a line | a bullet or numbered list; an indented line goes on with the item |
| `**bold**`, `*italic*` | bold, italic (a `*` with a space or a digit beside it, as in `select *` or `2*3`, stays a `*`) |
| `[the spec](https://...)` | a link with that text (`[ORDERS]` alone stays a table name) |
| a `\| A \| B \|` line, then `\|---\|---\|`, then rows | a table with a header row; without the line of dashes the `\|` stays text |
| ```` ```java ```` ... ```` ``` ```` or `<code lang="java">...</code>` | a code block, the language optional (in Confluence: the code macro) |
| `[[ref:<id>]]`, `[[ref:<id>\|label]]` | a link to that element or view showing its current name, or the label; its plain name when the export leaves it out |
| blank line | a new paragraph; single line breaks stay |

Keywords ignore case. Labels and panels only work for configured keywords (see below), so other `#words` stay as
they are.

**Insert Reference...** (right-click one element, relationship, view, note or the model, in the Models tree or in a
view) picks an element or view and adds its `[[ref:...]]` at the end of the description; **Undo** takes it back.
Renaming the target later never leaves an old name in the export.

A whole-model export has a **Labels** page: for each configured label in use (`#risk`, `#todo`...), the views,
elements and relationships whose description, notes or property values carry it, each linking to where it is
described (a relationship, to the element it is described with).

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
exclude label draft             leave out what carries #draft
hide property internal*         never print these properties
language cs                     fixed texts of the output in Czech
text contents = Table of contents   replace one fixed text
page description picture elements   sections of a view's page, in this order
entry description relations     parts of an element's description, in this order
```

**Project rules:** put the same text in a file `documentation.rules` next to the model's `.archimate` file and every
export of that model uses it instead of the preferences, on every computer and in CI (order: project file, then
preferences, then the built-in defaults). **Save as Project Rules...** writes the current list there for an open, saved
model. An invalid file fails the export and names the file and line; it never falls back to other rules.

**Leaving things out:** a property `doc:exclude` (any value but `false`) on a view, element, relationship or folder
leaves it out of every export (a folder: everything in it), pictures included; links to it become plain text. The rule
line `exclude label draft` does the same for everything whose description carries `#draft`, and `hide property key`
(repeatable, case-insensitive, `*` at the end matches any ending) never prints properties with that key. The `doc:`
properties are never printed. What you select directly is still exported, and the final message says how many objects
the rules left out.

**Language:** the fixed texts of the output are English; the rule line `language cs` prints them in Czech, and
`text contents = Table of contents` replaces any single one (keys: `contents`, `elements`, `notes`, `related-views`, `other-related-elements`, `changed-elements`, `work-packages`, `property`, `value`, `relationship`, `element`, `description`, `views`, `part-of`, `contains`, `labels`, `group`, `elements-not-in-any-view`, `selected-elements`, `generated`, `missing-asset`, `missing-reference`).

**Page template:** `page description contents picture properties notes related elements` gives the sections of a
view's page to print, in that order (leave one out and it is not printed), and `entry description properties relations
views workpackages other` does the same for the parts of an element's description. Without the lines the order is as
shown. Unknown names are errors.

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

Large pictures are shown smaller, keeping their proportions: in the PDF a picture is never wider or taller than a
page (the pictures of views too), and on HTML and Confluence pages a very tall picture is shown at most about 860
pixels high; click it to see it at full size.

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

This exports the whole model; formats default to all three. Missing assets are logged as errors. More options:

| `--documentation.workPackages KEY[,KEY]` | only these work packages: names, or the ticket key a name starts with (`RBWDP-20025`) |
| `--documentation.views a,b` | only these views: names or ids |
| `--documentation.folders Views/Sales` | only the views and elements in these folders (paths from the top folder) |
| `--documentation.rules file` | formatting rules from this file, instead of `documentation.rules` next to the model |
| `--documentation.strict` | missing assets also fail the run (unknown names always do) |

Views and folders can be combined; work packages are a run of their own. A name that matches nothing (or a view name
that matches several) is an error that lists what was not found. A run fails the way Archi's command line providers
fail: it throws, and with Archi's own `--abortOnException` the process exits with a non-zero code. Add that option to
CI runs. The model's `documentation.rules` file is used automatically; an invalid one fails the run.

## For developers

Needs JDK 21 and a local Archi installation (its jars are the compile target; default `E:/Archi`, override with
`-ParchiHome=...` or in `gradle.properties`).

The plugin is compiled for Java 11 (`--release 11`), the Java of Archi 4.9, and uses only Archi and Eclipse API that
every Archi from 4.9 to 5.10 has, so one build serves them all. The PDF library (openhtmltopdf, PDFBox) is Java 8
bytecode and travels inside the plugin in `lib/`.

| Path | Content |
|------|---------|
| `core/` | Pure Java engine, no Eclipse: document model and assets (`doc`), keyword formatting (`text`), HTML / PDF / Confluence rendering (`render`) |
| `plugin/` | Archi/Eclipse plugin: menus, export dialog, print order, work packages, model tree marks, add asset, preferences, command line provider |
| `sample/` | A sample model to try the export on, with its `Assets` folder and a work package |

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
