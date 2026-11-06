# What you need, and how to install it

SuperDirtGUI needs two things to run: **SuperCollider** and **SuperDirt**. One
`Quarks.install` line gets both of them, apart from SuperCollider itself. You
need **TidalCycles** too if you want to play it from Tidal, which is what the
workshop does. Everything else is optional. Each optional piece adds a menu
entry, an effect or a button. Without it, that entry is greyed out or the file
is skipped, and the rest works the same.

| | what | why | without it |
|---|---|---|---|
| **required** | SuperCollider 3.13 or 3.14 | runs everything | - |
| **required** | git | `Quarks.install` clones with it | the quark cannot be installed |
| **required** | SuperDirt (Oct 2025 or newer), with Vowel and Dirt-Samples | what SuperDirtGUI adds to | - |
| **to play from Tidal** | Tidal 1.9, GHC through ghcup, an editor (Pulsar or VS Code) | the patterns | you can still use the window, the explorer's play button and the sync window's preview |
| optional quark | BusManager | names the orbit busses, an output menu on each strip | a number box only |
| optional quark | BusRecorder | **recorder**: a file per orbit and return | the button is disabled |
| optional quark | DigitDrag | number boxes drag by the digit under the pointer | ordinary number boxes |
| optional quark | ParaEQ, Compressor, Insight | the eq, compressor and meter inserts | those kinds are greyed out in the insert menu |
| optional quark | Growl, Tunnel, Smear, Cloud, Stammer, Sympathy, Spiral | more kinds of insert | not listed |
| optional quark | Modality-toolkit | MIDI controllers on the mixer (`~sdg.midi`, in development) | no controllers; Tidal `cF` mappings still work |
| optional plugin | VSTPlugin | VST inserts | the kind is greyed out |
| optional plugin | sc3-plugins | `sdghole`, `sdgecho`, `sdgjverb`, `sdgroom`, `sdgshimmer` | those five files are skipped, one line each in the post window |
| optional plugin | mi-UGens | `sdgmiverb`, `sdgclouds`, `sdgrings`, `sdgwarps` | the same |

A *quark* is SuperCollider code, installed with one line from inside
SuperCollider. A *plugin* is compiled code for the audio server: you download
it and copy it into a folder by hand. None of the optional pieces is installed
along with SuperDirtGUI. Install only the ones you want.

All the synths, the drum machine, the sample players, the mixer, the sample
editor, tags, sync and sessions need nothing beyond the required pieces. The
drum voices use only SuperCollider's own UGens.

## Platforms

Written and tested on **macOS** with SuperCollider 3.13. Linux should behave
the same, but nobody has tried it. **SuperCollider 3.14** (Qt 6) works too:
3.14.1 on macOS compiled every class and passed the same checks as 3.13. Those
checks covered every synth and sample player played, inserts, a return,
sessions, MIDI mapping, the meters, and pictures of each window, which looked
the same in both versions. Nobody has played it by ear or in the 3.14 IDE
yet.

### Windows

Nobody has run it on Windows yet. The code was read through for it, every
optional piece has a Windows download, and these are the differences:

- **Install with the quark**, never `install.scd`: it makes a symlink with
  `ln -s`, which Windows does not have. The same goes for every optional quark.
- **Shortcuts use ctrl** where macOS uses cmd: ctrl-s saves in the sample
  editor, ctrl + scroll zooms the waveform.
- **The firewall asks** the first time SuperCollider and its server open a
  network port. Allow both on private networks, or Tidal's messages never
  reach SuperDirt.
- **Plugins**: take `sc3-plugins-<version>-Windows-64bit.zip`,
  `mi-UGens-Windows.zip` and VSTPlugin's Windows package. Windows sometimes
  marks a downloaded zip as blocked: right-click it, Properties, tick
  **Unblock**, *then* unzip it into the Extensions folder.
- **Show in Explorer**, **open in default app** and **copy path** (the
  explorer's right-click menu) and the sync window's **copy** go through
  `explorer`, `start` and PowerShell, which every Windows 10 and 11 has.
- A sample's date added is not known on Windows: the explorer's created and
  added columns both show the file's modification time.
- Tags, sessions and presets go in `%LOCALAPPDATA%\SuperCollider`
  (`Platform.userAppSupportDir`), recordings under your Documents folder.

## The required part, step by step

### 1. SuperCollider

Download it from [supercollider.github.io/downloads](https://supercollider.github.io/downloads)
and install it the usual way for your system. On macOS, drag it into
Applications, open it once, and allow the microphone when asked. The server
asks for it even if you never record.

### 2. git

`Quarks.install` runs `git clone`, so git has to be on the machine:

- **macOS**: type `git --version` in a terminal. If git is missing, macOS
  offers to install the Command Line Tools. Accept, or run
  `xcode-select --install`.
- **Windows**: [Git for Windows](https://git-scm.com/downloads). Restart
  SuperCollider afterwards so it finds git.
- **Linux**: your package manager, e.g. `sudo apt install git`.

### 3. SuperDirtGUI, and SuperDirt with it

In SuperCollider:

```supercollider
Quarks.install("https://github.com/fracnesco/superdirt-gui.git");
thisProcess.recompile;
```

SuperDirtGUI's manifest names SuperDirt as a dependency, so this also installs
**SuperDirt**, **Vowel** and **Dirt-Samples** if you don't have them.
Dirt-Samples is several hundred megabytes: install on a good connection
*before* the workshop. The recompile makes the classes exist. It is the same
as `Language -> Recompile Class Library` (cmd/ctrl + shift + L).

**If you already have SuperDirt**, keep it, unless it is from before October
2025. SuperDirtGUI warns at startup if it is. To refresh it:

```supercollider
Quarks.uninstall("SuperDirt");   // takes it off the class path, leaves the folder
Quarks.folder.openOS;            // move ONLY the SuperDirt folder to the trash
Quarks.install("SuperDirt");
thisProcess.recompile;
```

Never use `Quarks.update("SuperDirt")`: it checks out a branch from 2021. If
you once copied SuperDirt into the Extensions folder by hand, delete that copy,
or the recompile stops with a duplicate class error. For the same reason,
install SuperDirtGUI one way only, the quark or a clone linked with
`install.scd` (see the [README](../README.md#install)), never both.

### 4. A startup that works

Raise the server's limits before booting. Sample banks and the effects need
more than the defaults:

```supercollider
(
s.options.numBuffers = 1024 * 256;   // room for the sample banks
s.options.memSize = 8192 * 32;       // the reverbs and delays
s.options.maxNodes = 1024 * 32;
s.options.numWireBufs = 256;         // or one of SuperDirt's own synths fails to load
s.waitForBoot {
	~dirt = SuperDirt(2, s);
	~dirt.loadSoundFiles;
	s.sync;
	~dirt.start(57120, 0 ! 12);
	~sdg = SuperDirtGUI(~dirt);
	~sdg.gui;
};
)
```

[examples/superdirt-gui-demo.scd](../examples/superdirt-gui-demo.scd) is the
same startup, a block at a time.

## Tidal

SuperDirtGUI has been checked with **Tidal 1.9** (1.9.5), driven from a real
`ghci`. Follow Tidal's own installation guide for your system:
[macOS](https://tidalcycles.org/docs/getting-started/macos_install),
[Windows](https://tidalcycles.org/docs/getting-started/windows_install),
[Linux](https://tidalcycles.org/docs/getting-started/linux_install). In short,
on macOS:

1. Install GHC and cabal with [ghcup](https://www.haskell.org/ghcup/).
2. Install the Tidal library in a terminal: `cabal update`, then
   `cabal v1-install tidal`. The second step takes a while.
3. Install [Pulsar](https://pulsar-edit.dev) and its **tidalcycles** package,
   or VS Code with the TidalCycles extension.

Skip the guide's SuperDirt step: step 3 above did it. To check, start
SuperDirt as above, open a `.tidal` file in the editor, start Tidal, and
evaluate `d1 $ s "sdgkick*4"`. The orbit 0 meter in the window moves.

## The optional part

### Quarks

Each is one line, then a recompile. Install the ones you want, then recompile
once for all of them:

```supercollider
Quarks.install("https://github.com/fracnesco/sc-bus-manager.git");   // BusManager
Quarks.install("https://github.com/fracnesco/sc-recorder.git");      // BusRecorder
Quarks.install("https://github.com/fracnesco/sc-digit-drag.git");    // DigitDrag
Quarks.install("https://github.com/fracnesco/sc-eq.git");            // ParaEQ
Quarks.install("https://github.com/fracnesco/sc-compressor.git");    // Compressor (pulls ParaEQ)
Quarks.install("https://github.com/fracnesco/supercollider-insight-meter.git");   // Insight
Quarks.install("Modality-toolkit");                                  // MIDI controllers
thisProcess.recompile;
```

The seven creative inserts are built on FxKit, which is built on ParaEQ.
Each one's manifest pulls the other two in:

```supercollider
Quarks.install("https://github.com/fracnesco/sc-growl.git");
Quarks.install("https://github.com/fracnesco/sc-tunnel.git");
Quarks.install("https://github.com/fracnesco/sc-smear.git");
Quarks.install("https://github.com/fracnesco/sc-cloud.git");
Quarks.install("https://github.com/fracnesco/sc-stammer.git");
Quarks.install("https://github.com/fracnesco/sc-sympathy.git");
Quarks.install("https://github.com/fracnesco/sc-spiral.git");
thisProcess.recompile;
```

To update one later, use `Quarks.update("superdirt-gui")` with the quark's
repository name, then recompile. SuperCollider 3.13's updater always pulls a
branch called `master`, so it fails on the repositories whose branch is
`main`: sc-bus-manager, sc-eq, sc-compressor and supercollider-insight-meter.
For those, `Quarks.uninstall` and `Quarks.install` again instead.

### Plugins

A plugin is a folder of compiled code. Download the release for your system,
put the folder in the Extensions folder, and recompile.
`Platform.userExtensionDir.openOS` opens the Extensions folder.

- **sc3-plugins**: [github.com/supercollider/sc3-plugins/releases](https://github.com/supercollider/sc3-plugins/releases).
  Take the release that matches your SuperCollider version.
- **mi-UGens**: [github.com/v7b1/mi-UGens/releases](https://github.com/v7b1/mi-UGens/releases).
- **VSTPlugin**: [git.iem.at/pd/vstplugin/-/releases](https://git.iem.at/pd/vstplugin/-/releases),
  the SuperCollider package (not the Pd one). The first time you add a VST
  insert, it searches the machine for plugins, which can take a minute. After
  that it remembers.

On **macOS**, plugins downloaded from the web are blocked by Gatekeeper ("cannot
be opened because the developer cannot be verified"), and the server fails to
load them. Clear the quarantine flag once on the folder you copied, in a
terminal:

```sh
xattr -rd com.apple.quarantine ~/Library/Application\ Support/SuperCollider/Extensions/mi-UGens
```

Do the same with the folder name of each plugin you installed. A plugin from a
system package (Linux) or an installer needs none of this.

## Check what you have

Evaluate this after a recompile. It posts each piece and whether this
SuperCollider has it. The plugins show as present once their class files are
on the class path:

```supercollider
(
var have = { |names| names.every { |n| n.asSymbol.asClass.notNil } };
[
	["SuperDirt (required)", [\SuperDirt]],
	["SuperDirtGUI (required)", [\SuperDirtGUI]],
	["BusManager", [\BusManager]],
	["BusRecorder", [\BusRecorder]],
	["DigitDrag", [\DigitDrag]],
	["ParaEQ", [\ParaEQ]],
	["Compressor", [\Compressor]],
	["Insight", [\Insight]],
	["FxKit inserts", [\FxParamUnit]],
	["Modality", [\MKtl]],
	["VSTPlugin", [\VSTPlugin]],
	["sc3-plugins", [\Greyhole, \JPverb]],
	["mi-UGens", [\MiVerb, \MiClouds, \MiRings, \MiWarps]]
].do { |row| "%  %".format(row[0].padRight(26), if (have.(row[1])) { "yes" } { "-" }).postln };
if (\SuperDirt.asClass.notNil and: { SuperDirt.findMethod(\loadSoundFilesToBank).isNil }) {
	"SuperDirt is older than October 2025: refresh it (see above)".warn
};
)
```

When SuperDirtGUI starts, it posts one line per content file it loads. A file
whose plugin is missing posts `needs MiClouds, not installed - skipped`. That
line is informational, not an error.
