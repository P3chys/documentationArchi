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
- [Documentation Preview](#documentation-preview)
- [Print order](#print-order)
- [Check Documentation](#check-documentation)
- [Model conventions (HaF)](#model-conventions-haf)
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
  html/index.html, html/<view>.html, html/elements_a-z.html, html/elements_by-type.html, html/search.js,
  html/images/, html/assets/, html/style.css
  <model>.pdf, assets/                 (assets/ holds the files the PDF links to)
  confluence/pages/, confluence/attachments/, confluence/manifest.json, confluence/upload_to_confluence.py
```

The export runs in the background, so you can keep working; the Progress view shows the view being drawn and the
format being written, and **Cancel** there stops it. A re-export replaces the folders it owns (`html/`, `assets/`,
`confluence/` and the PDF, each only if that format is exported now), so files of renamed or deleted views do not
stay behind. The new files are written next to the old ones and swapped in at the end: a cancelled or failed export
leaves the previous one as it was, and the model tree marks do not change. Nothing else in the folder is touched.
While it runs, the pictures of the views wait in temporary files instead of memory; they are deleted when the export
ends, however it ends.

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
  An export of the whole model or of a folder lists the views under their folders (below *Views*) as headings, with
  each folder's description, sorted by name as the Models tree shows them; the PDF bookmarks nest the same way, and
  in Confluence each folder becomes a page that holds its views. A model without such folders is listed as before.
- The pictures are drawn at twice the size, so they stay sharp in the PDF and when zooming in.
- Click an element in a view's picture to go to its description, on the same page or the one that describes it, in
  the HTML pages and the PDF (not in Confluence). Elements that the export describes nowhere aren't clickable. On a
  web page such a picture is shown at its own width, and a wide one scrolls sideways instead of shrinking.
- The PDF has bookmarks for every page. With more than one page it starts with a cover (the model's name, the
  "Generated" line and the start of its purpose) and a table of contents with page numbers, nested as the folders.
  Each page has its title at the top and the model's name and "Page n of m" at the bottom, and a view with a picture
  clearly wider than tall gets landscape pages. The rule lines `pdf cover off`, `pdf contents off` and
  `pdf landscape off` switch these off.
- Every HTML page has a sidebar with the pages under their folders (the current one highlighted, folders fold), an
  **Elements A–Z** page and an **Elements by type** page listing every described element, and a search box that
  finds views and elements by name, type and description as you type. It works from the disk, without a server; with
  JavaScript off the sidebar is a plain list. The sidebar is never printed and moves above the page on narrow
  screens.
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

## Documentation Preview

See how a description comes out without exporting: **Window > Documentation Preview** opens a view that shows the
selected element, relationship or view as the HTML export shows it, using the same formatting rules (including the
model's `documentation.rules`) and the same pictures and assets. Right-click anything in the **Models** tree or in a
view and choose **Show in Documentation Preview** to open it on that object.

- A view shows its whole page, with its picture; an element its description, saying which page it is on; a
  relationship the descriptions of the elements at both ends; the model its overview page.
- It follows the selection in the Models tree and in the views, and shows a change (a description typed in the
  Properties view, a property, a rename) half a second after you stop typing. The picture of a view is only drawn
  again when that view changes.
- A link to another element or view shows that one in the preview. Web links open in your browser, assets with
  their program if they are documents or pictures (never a program, script or macro file). Scripts never run.
- Missing assets show in red, as in the export. Previewing never changes the model and never counts as an export
  for the marks in the Models tree.

## Print order

Elements are described in alphabetical order unless you set an order. Right-click a view (in the Models tree or in
the view) and choose **Print Order...**. Move elements with the buttons, **Alt+Up / Alt+Down** or by dragging, then
**OK**. **Remove Order** goes back to alphabetical.

The order is kept in the model, the way modellers often do it by hand: a Grouping called `PrintOrder` on the view,
with associations named `001`, `002`, `003`... from each element to it. You can also draw these yourself, and any
element with the specialization `PrintOrder` (of any type and name) is such a marker too. Elements linked to the
view's PrintOrder come first, by the first number in the relationship's name (`pos 20` is 20; of several, the
smallest), and the others follow alphabetically. A PrintOrder that is not on the view (one of another view) does not change its order.
The PrintOrder element and its links never appear in the export or its pictures. **Undo** takes a change back.

## Model conventions (HaF)

Models written for HaF, the earlier documentation scripts, use these conventions; the export reads them the same way.

- **Relationships in the text:** `` `NAME` `` (or ``` ``NAME`` ```) in an element's description, where `NAME` is the
  name of one of its relationships (either way round, ignoring case), becomes a link to the element at the other end,
  and that relationship is not listed again among the outgoing ones. When it is a composition to a part of the same
  type, the part's whole description takes its place (with the part's own tokens, each part once). A name that no
  relationship has stays as it is.
- **How relationships read:** the relationship column says what it does, with the type (and specialization) under
  it: *triggers* / *is triggered by*, *is used by* / *use* (serving), *composed of* / *is a part of*, and for an
  assignment from a component *provides* (a service), *publishes* (an interface), *generates* (an event), *implements*
  (a process or function), from an interface to a service *publishes*, read backwards *is provided by* and so on.
  Serving and triggering show the other element's business description (its description up to a line
  `Technically:`, without `Description:`) in grey italics; triggering only when the relationship has no description.
- **Access:** *read from* / *write into* (backwards *is read by* / *is written by*), from the specialization
  (`select` reads; `update`, `insert`, `delete` write) or else Archi's access type. A `select` access with
  `<sellist>ID, STATE</sellist>` in its description shows `SELECT ID, STATE FROM <table>`, an `update` access with
  `<updList>STATE = 'DONE'</updList>` shows `UPDATE <table> SET ...` (the table is the target's name without a
  `[table]`-like prefix). An access to an element named `[select] ...` shows that element's description as its query.
- **Parameters:** what an element reads from an element named `[parameters] ...` becomes its *Input parameters*, what
  it writes its *Output* (*Request* and *Response* for an element named `[api] ...`), with that element's description;
  `[parameters]` elements are never listed among the related elements.
- **Roles:** an association to a Business Role named `[auth] DEV` (any case) prints *Available only for role DEV and
  higher*, in the order VIEW, DEV, OPE, ADMIN (for ADMIN, without "and higher"; of several, the lowest); it is not
  listed as an association.
- **Confluence pages:** an element with a property `confluenceUrl` is documented there: every link to it goes to
  `<url>#id-<PageTitle>-<«Type»Name>`, Confluence's anchor of its heading (spaces and `-` left out, `+` and `%20` out
  of the title, other than ASCII percent-encoded, the type without Business/Application/Technology). An address with
  a `#` is used as it is; one that names the page only by its id (`.../pages/12345`) links to the page, and the export
  says so once. Only `http(s)` addresses are used.
- **Jira:** with the rule line `jira https://jira.example.com/browse/`, a work package named exactly like a ticket
  (`RBWDP-20025`, not `RBWDP-20025 portal`) has its heading and every link to it go to the ticket.

Elements with neither a Confluence page nor a ticket link to their description in the export.

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

**Kind of change:** in **Link to Work Package...** choose the **Kind of change** (*new*, *change*, *remove*). It
becomes the name of the link, so you can also write it on a link you draw yourself, and the documentation shows it
next to the ticket and the element: *Work packages: RBWDP-20025 (new)*. *(not said)* leaves the links as they are.

**Closing a ticket:** when a ticket is done, select it in **Work Packages...** and press **Close**. The work package
stays in the model with its links, but the dialogs, the *Work packages* page and the elements' descriptions no
longer list it; exporting it directly still gives its specification. Tick **Show closed work packages** to see them
and **Reopen** one. Closing sets the property `doc:closed` on the work package, and **Undo** takes it back.

**What changed without a ticket:** **Changed Since Publish...** in **Work Packages...** finds the elements that the
documentation shows differently from what was published (see *Publishing*) and that no open work package changes
yet, and opens **Link to Work Package...** on them, with the kind of change it found: *new* for an element added
since, *change* for one that says something else.

## Check Documentation

Right-click anything in a model and choose **Check Documentation...** to list problems without exporting:

- elements and views without a description,
- elements that are on no view (work packages are not expected on one),
- views (ArchiMate) without elements,
- `[[asset:...]]` references whose file is missing or ambiguous (several files have that name),
- `[[ref:...]]` references to objects that no longer exist,
- `#words` that look like a label but are not a configured one: they differ from one by one or two characters
  (`#rsik` for `#risk`),
- work packages that change no element.

Objects the rules leave out of the export are not checked.

The dialog groups the findings by kind with counts, and **Show in Model Tree** selects the objects of the chosen ones.

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

### Publishing: what the readers have

Tick **Publish** in the export dialog when an export is the documentation your readers get. The plugin then notes
what that export shows in a file `documentation.published` next to the model file, and two things follow:

- **Marks for the whole team.** An object counts as exported if your own exports show it as it is, or the published
  documentation does. Share the file with the model (cooperativeArchi 1.6.1 and later does it for you; otherwise copy
  it) and a colleague who never exported anything sees the same marks as you: only what the readers don't have yet.
- **A page "Changes since ...".** Every later export of the whole model starts with a page listing the views and
  elements added, changed and removed since the documentation was published, with each changed name, description,
  property and relationship before and after, and links to where they are described now.

Publishing a part (a work package, some views) keeps what was published of the rest. An export without **Publish**
is a preview: it changes nothing in that file. The model must have been saved, so the plugin knows where the file
goes. The file is text and is written in the same order each time; it is not meant to be edited.

## Formatting descriptions

Write these in any description (of the model, a view, an element or a relationship), in notes and in property
values. They look the same in HTML, the PDF and Confluence (as Confluence's own panels, labels and code blocks).

| Write | Result |
|-------|--------|
| `INFO: text`, `NOTE: text`, `TIP: text`, `WARNING: text` at the start of a line | a coloured panel with that title, up to the next blank line |
| `<#>a remark</#>` (or up to the next blank line) | a note panel without a title |
| `<#>` and `</#>` on lines of their own | a note panel up to `</#>`, across blank lines; it may hold SQL, tables, lists |
| `#risk` or `[[risk]]` | a label; built in: `risk` (red), `deprecated` (grey), `todo` and `draft` (yellow), `new` (green) |
| `Description:`, `Technically:` anywhere | bold |
| `[ORDERS]` | a table name, coloured |
| `'ACTIVE'` | a constant, coloured: capitals, digits, `_ ( ) :` in single quotes, with a capital (`'TYPE_1'`, `'NVL(X)'`; not `'hello'`, `'2024'`) |
| `{client_id}` | a variable, coloured |
| `<sql>select ...</sql>` | an SQL code block; in HTML and PDF keywords at the start of a line (`SELECT`, `FROM`, `WHERE`, `LEFT JOIN`...) are bold and `'CONSTANT'`, `{variable}`, `[object]` coloured (in Confluence: the code macro with SQL highlighting) |
| `https://...` | a link |
| `[[asset:file.png]]`, `<ass>file.png</ass>` | a picture or file from the Assets folder, see [Assets](#assets-pictures-and-files) |
| `<tbl>` ... `</tbl>` | a table of the lines starting with `\|`, the first its header; no line of dashes needed; a line without `\|` goes on in the last cell |
| a line of only `<name>` ... `</name>` | indents the lines in between by 20px per level; the tag lines are printed; `<section end>` ends all levels |
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
pdf landscape off               no landscape pages in the PDF; also pdf cover off, pdf contents off
assets Files                    the folder assets are read from (default: Assets)
defaults off                    in documentation.rules: without the built-in panels, labels and bold texts
jira https://jira.example.com/browse/   work packages named like a ticket link to it
```

**Project rules:** put the same text in a file `documentation.rules` next to the model's `.archimate` file and every
export of that model uses it instead of the preferences, on every computer and in CI (order: project file, then
preferences, then the built-in defaults). The file adds to the built-in rules (the `INFO:`... panels, the `#risk`...
labels, `Description:` and `Technically:` in bold), so it only needs your own lines, and a line for the same keyword
replaces the built-in one; a line `defaults off` makes the file hold all its rules itself. **Save as Project
Rules...** writes the current list there for an open, saved model, starting with `defaults off` when the list leaves
out a built-in rule. An invalid file fails the export and names the file and line; it never falls back to other rules.

**Leaving things out:** a property `doc:exclude` (any value but `false`) on a view, element, relationship or folder
leaves it out of every export (a folder: everything in it), pictures included; links to it become plain text. The rule
line `exclude label draft` does the same for everything whose description carries `#draft`, and `hide property key`
(repeatable, case-insensitive, `*` at the end matches any ending) never prints properties with that key. The `doc:`
properties are never printed. What you select directly is still exported, and the final message says how many objects
the rules left out.

**Language:** the fixed texts of the output are English; the rule line `language cs` prints them in Czech, and
`text contents = Table of contents` replaces any single one (keys: `contents`, `elements`, `notes`, `related-views`, `other-related-elements`, `changed-elements`, `work-packages`, `property`, `value`, `relationship`, `element`, `description`, `views`, `part-of`, `contains`, `labels`, `group`, `elements-not-in-any-view`, `selected-elements`, `other-pages`, `elements-a-z`, `elements-by-type`, `search`, `nothing-found`, `view`, `page`, `page-of`, `generated`, `missing-asset`, `unsupported-asset`, `missing-reference`, `name`, `token`, `used-in`, `tables-index`, `variables-index`, `constants-index`, `changes-since`, `changes-summary`, `changes-none`, `added`, `changed`, `removed`, `before`, `after`, `new`, `triggers`, `triggered-by`, `used-by`, `uses`, `composed-of`, `part-of-whole`, `provides`, `provided-by`, `publishes`, `published-by`, `generates`, `generated-by`, `implements`, `implemented-by`, `reads`, `writes`, `reads-writes`, `read-by`, `written-by`, `read-written-by`, `role-and-higher`, `role-only`, `input-parameters`, `output`, `request`, `response`).

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

A file name alone is enough when only one file in `Assets` has it; otherwise use its path inside `Assets`. A file
directly in `Assets` has its name as its path, so `readme.txt` means `Assets/readme.txt` even when a subfolder has
another `readme.txt` (that one is `dup/readme.txt`). Case and `/` or `\` don't matter. PNG, JPEG, GIF, BMP and SVG
are pictures; anything else is a file to open.

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

`<ass>path</ass>` works like `[[asset:path]]` and may be broken over lines (they are joined). A web address in it is a
link, or a picture when it ends in `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp` or `.svg` (a query string is ignored). An
absolute path, or one that leaves the folder with `..`, shows in red as *[Unsupported asset: path]* and is reported
like a missing asset.

A web picture shows on the HTML and Confluence pages; the Documentation Preview and the PDF link to it instead, so
nothing is fetched from the web while you work or export.

**Another folder:** the rule line `assets <folder>` (relative to the model file's folder; in quotes when it has
spaces) says where assets are read from instead of `Assets`; Add Asset copies there too. The folder must be inside the
model's folder, since the rules file travels with the model. On the command line `--documentation.assets <folder>`
overrides it and may name any folder, absolute too.

## Confluence

The Confluence export is ready-made pages in Confluence's storage format: links between pages and to elements,
panels, labels, code blocks, pictures and assets as attachments. Upload it with the script it comes with (Python 3.8+,
no packages needed), for Confluence Data Center / Server:

```bash
CONFLUENCE_TOKEN=<personal access token> python <model>/confluence/upload_to_confluence.py --url https://confluence.example.com --space ARCH
```

- `--parent "Existing page"` puts the pages under an existing page.
- Without a token, it uses `CONFLUENCE_USER` and `CONFLUENCE_PASSWORD`.
- Views in folders go under a page per folder, as in the Models tree.
- Pages are matched by title. Existing ones get a new version, so you can upload the same export again after
  changes.
- Page titles start with the model name, because Confluence titles must be unique in a space.
- The pictures of views are plain pictures there: clicking an element in them leads nowhere (it does in the HTML
  pages and the PDF).

## Export profiles

An export is set up by hand: what to select, which formats, which folder. A **profile** is a named set-up kept with
the model, so everyone and the command line export the same. It is a rule line in `documentation.rules`:

```text
profile developers folders=Views/Sales,Views/Claims formats=html,pdf exclude=#internal hide=cost* language=en output=docs/dev publish
profile "Sales team" views="Order flow",Overview output=docs/sales
```

| Setting | Meaning |
|---------|---------|
| `folders=` `views=` `workpackages=` | what to export, comma separated, as on the command line; none: the whole model. Work packages cannot be combined with folders or views |
| `formats=` | `html`, `pdf`, `confluence` |
| `exclude=` | more labels to leave out, on top of the `exclude label` lines |
| `hide=` | more property keys never to print (`*` at the end matches any ending) |
| `language=` | `en` or `cs` |
| `output=` | the export folder; relative to the folder of the model file unless absolute. The export goes straight into it, without a subfolder |
| `publish` | the export is published, see [Publishing](#publishing-what-the-readers-have) |

Names and values with spaces are quoted. A mistake is reported with the file and line, like the other rule lines, and so
is a folder or view that does not exist, when the export runs.

In Archi, the export dialog has a **Profile** list ("(none)" first). Choosing one fills in and locks what the profile
sets. The context menu of the model has **Export Profile**, with one entry per profile; it runs the profile without
the dialog (what the profile does not set comes from the last export dialog), and replaces the files in the folder.
On the command line: `--documentation.profile <name>`; `--documentation.folder` is then optional when the profile has
`output=`, and any option given explicitly overrides the profile's setting.

## Catalogs

A catalog is a page with a table of the elements of one type. A rule line adds it to exports of the whole model:

```text
catalog "Applications" type=Application Component|Application Service folder=Application/Core columns=name,Owner,description
```

| Setting | Meaning |
|---------|---------|
| the title (quoted) | the title of the page |
| `type=` | the element types as the export prints them (`Application Component`); `*` at the end matches any ending (`Application*`); several are separated by `|` |
| `folder=` | optional: only elements in this model folder and below, a path like `Application/Core` |
| `columns=` | `name` (links to the element's description), `description` (its first paragraph), or a property key |

There is one row per described element, sorted by name. The page is listed on the overview page. A catalog without
any row is left out, and the message at the end of the export says so. Catalogs are not part of exports of selected
views, folders or work packages.

## Data usage index

Descriptions mark database tables as `[ORDERS]`, variables as `{client_id}` and constants as `'ACTIVE'` (see
[Formatting descriptions](#formatting-descriptions)). A rule line adds a page to exports of the whole model that lists
every one of them:

```text
index tables
index variables
index constants
```

The page lists each token once, sorted by name, with links to where it is used: the descriptions, notes and property
values of the views, elements and relationships. Upper and lower case count as the same token. The tokens are found
the way the text is coloured, so the index never disagrees with the pages. In an SQL block only a table written as
`[ORDERS]` counts. An index that finds nothing is left out, and the message at the end of the export says so. Without
the lines nothing changes.

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
| `--documentation.profile name` | export as this profile of the rules says, see [Export profiles](#export-profiles); `--documentation.folder` is then optional, explicit options win |
| `--documentation.check` | print what is missing or wrong in the documentation, see [Check Documentation](#check-documentation); with `--documentation.strict`, findings fail the run |
| `--documentation.assets folder` | read assets from this folder (relative to the model's folder, or absolute), instead of the `assets` rule |
| `--documentation.strict` | missing assets also fail the run (unknown names always do) |
| `--documentation.publish` | note this export as the published documentation, in `documentation.published` next to the model |

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
| `plugin/` | Archi/Eclipse plugin: menus, export dialog, documentation preview, print order, work packages, model tree marks, add asset, preferences, command line provider |
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
