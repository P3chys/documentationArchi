# documentationArchi

Turns an [Archi](https://www.archimatetool.com) model into readable documentation: HTML pages, a PDF document and
Confluence pages, one page per view with its picture and a description of every element on it.

- **Export the model, some views or some elements:** right-click in the Models tree or in a view, *Export
  Documentation...*, pick the folder and the formats.
- **One page per view**, plus an overview page when there is more than one. An element is described on the first page
  that shows it (documentation, properties, relationships); later pages link there.
- **Print order:** right-click a view, *Print Order...*, and put its elements in the order the page describes them
  (buttons, Alt+Up/Down or drag and drop). Kept in the model as a `PrintOrder` element with numbered links, the way
  modellers do it by hand, and left out of the export and its pictures. Undo takes it back.
- **Formatting keywords** in descriptions: `Open question: ...` becomes a warning panel, `#risk` a red label,
  `{variable}`, `[table]` and `'constant'` coloured tokens, SQL blocks highlighted. **Tools > Documentation
  Formatting...** or **Preferences > Documentation Export** to change or add them.
- **Confluence:** pages in storage format with their attachments and an `upload_to_confluence.py` script (Python 3.8+,
  no packages) that creates or updates them in a space of Confluence Data Center / Server.
- **Command line** for CI and scheduled builds, see below.

Requires Archi 4.9 or later. One plugin file for every Archi version, Windows and macOS.

---

## Install

1. Download `documentationArchi-<version>.archiplugin` from the
   [releases page](https://github.com/P3chys/documentationArchi/releases).
2. In Archi: **Help > Manage Plug-ins... > Install New...**, choose the file, restart Archi.

| Archi       | Tested in |
|-------------|-----------|
| 4.9.x       | 4.9.3     |
| 5.0 – 5.3   | 5.1.0, 5.3.0 |
| 5.4 – 5.9   | 5.4.3, 5.9.0 |
| 5.10 and up | 5.10.0    |

## Command line

```bash
Archi -application com.archimatetool.commandline.app -consoleLog -nosplash \
  --loadModel model.archimate --documentation.folder out [--documentation.formats html,pdf,confluence]
```

Then, for Confluence:

```bash
CONFLUENCE_TOKEN=<personal access token> python out/confluence/upload_to_confluence.py --url https://confluence.example.com --space ARCH
```

## For developers

Needs JDK 21 and a local Archi installation (its jars are the compile target; default `E:/Archi`, override with
`-ParchiHome=...` or in `gradle.properties`).

The plugin is compiled for Java 11 (`--release 11`), the Java of Archi 4.9, and uses only Archi and Eclipse API that
every Archi from 4.9 to 5.10 has, so one build serves them all. The PDF library (openhtmltopdf, PDFBox) is Java 8
bytecode and travels inside the plugin in `lib/`.

| Path | Content |
|------|---------|
| `core/` | Pure Java engine, no Eclipse: document model (`doc`), keyword formatting (`text`), HTML / PDF / Confluence rendering (`render`) |
| `plugin/` | Archi/Eclipse plugin: menus, export dialog, print order, preferences, command line provider |
| `sample/` | A sample model to try the export on |

```bash
just test        # core and plugin tests (plugin tests run headless on the Archi model jars)
just plugin      # build/dist/documentationArchi-<version>.archiplugin
just install     # copy the plugin into your Archi dropins folder (close Archi first, then start it again)
just check       # everything: tests + plugin
```

Archi 4.9 keeps its settings and dropins in `Archi4`: `gradlew :plugin:installDev -ParchiData=Archi4`.

**Windows: don't use PowerShell 7 from the Microsoft Store for `just install` or to start Archi.** Store apps run in a
sandbox that redirects writes to `%APPDATA%` (where Archi keeps dropins and settings) to a private copy, so Archi
would never see the plugin. Use Git Bash, cmd, Windows PowerShell, or PowerShell 7 installed with winget/MSI.

## License

MIT. The bundled libraries keep their own licenses (openhtmltopdf: LGPL 2.1, PDFBox and Commons Logging: Apache 2.0),
see `NOTICE`.
