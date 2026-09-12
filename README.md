# SuperDirtGUI

**Extra synths, effects and sample helpers for SuperDirt, loaded into the
instance you already run.** A SuperCollider extension.

Three folders of plain `.scd` files — `synths/`, `modules/`, `effects/` — go
into a running SuperDirt with one line. SuperDirt itself stays untouched, so
this sits next to a normal install and updates on its own.

```supercollider
~dirt = SuperDirt(2, s);
~dirt.loadSoundFiles;
~dirt.start(57120, 0 ! 12);
~sdg = SuperDirtGUI(~dirt);     // reads synths/, modules/, effects/
~sdg.gui;                       // what got loaded; reload; free
```

Then, from Tidal: `d1 $ s "sdgsine" # n "0 7"`.

It depends on **SuperDirt** — and through it on Vowel and Dirt-Samples — and on
nothing else. sc3-plugins are only needed if a content file uses one of them;
the templates do not.

Written for SuperCollider 3.13 on macOS. Nothing in it is platform specific
apart from the symlink in `install.scd`, which Linux shares and Windows does not.

## Install

### As a quark

```supercollider
Quarks.install("https://github.com/fracnesco/superdirt-gui.git");
thisProcess.recompile;
```

`Quarks.install` clones the repository into your `downloaded-quarks` folder and
puts it on the class path. Because the manifest names SuperDirt as a dependency,
it installs SuperDirt, Vowel and Dirt-Samples first if you do not have them.
Dirt-Samples is several hundred megabytes: do this on a good connection before
the workshop, not during it. The recompile is what makes the classes exist, and
is the same thing as `Language -> Recompile Class Library` (ctrl/cmd + shift + L).

Afterwards:

```supercollider
Quarks.update("superdirt-gui");      // pull the newest version, then recompile
Quarks.uninstall("superdirt-gui");   // take it off the class path
Quarks.gui;                          // see what is installed
```

The quark goes by the name of the repository, `superdirt-gui`; the class inside
it is `SuperDirtGUI`.

### Already have SuperDirt?

Keep it. SuperDirtGUI works with the SuperDirt you have, as long as it is
recent: it warns at startup if yours predates October 2025. To refresh
SuperDirt:

```supercollider
Quarks.uninstall("SuperDirt");   // takes it off the class path, leaves the folder
Quarks.folder.openOS;            // move ONLY the SuperDirt folder to the trash
Quarks.install("SuperDirt");     // the current version
thisProcess.recompile;
```

Do not use `Quarks.update("SuperDirt")`: SuperDirt's own updater checks out a
branch from 2021. Leave Dirt-Samples and Vowel where they are. If you once
copied SuperDirt into your Extensions folder by hand, remove that copy, or the
recompile stops with a duplicate class error.

### From a clone

If you would rather have a working copy you can edit, clone it wherever you keep
things and link it into the extensions folder:

```sh
git clone https://github.com/fracnesco/superdirt-gui.git
```

There is an `install.scd` in the repository that does the linking for you: open
it and evaluate it, then recompile the class library. It symlinks the folder
into `Platform.userExtensionDir` and tells you what it did.

On Windows there is no `ln -s`, so use the quark, or copy the folder into the
extensions folder by hand — `Platform.userExtensionDir.openOS` opens it.

Install it one way or the other, **not both**: two copies on the class path is a
duplicate class error at compile time.

### Check that it took

```supercollider
SuperDirtGUI.filenameSymbol;    // -> .../superdirt-gui/classes/SuperDirtGUI.sc
~sdg = SuperDirtGUI(~dirt);     // after ~dirt.start; posts one line per file loaded
~sdg.synthNames;                // -> [ sdgsine ]
```

Select `SuperDirtGUI` and press cmd/ctrl + d for the help file, and see
[examples/superdirt-gui-demo.scd](examples/superdirt-gui-demo.scd) for a
startup you can evaluate a block at a time.

## What gets loaded

`SuperDirtGUI(~dirt)` reads every `.scd` file in three folders next to
`classes/`, in alphabetical order, with `~dirt` and `~sdg` set — exactly the
way SuperDirt reads its own `synths/` folder. Each folder holds one example
file that is a template: copy it, rename it, edit it.

| folder | one file holds | switched on from Tidal by |
|--------|----------------|---------------------------|
| `synths/` | a SynthDef in SuperDirt's argument convention: `out`, `sustain`, `pan`, `freq`, `accelerate`, and any key you send with `pF` | `s "sdgsine"` |
| `modules/` | `~dirt.addModule(name, func, test)` and its SynthDef, named `"name" ++ ~dirt.numChannels`, reading and replacing the event's bus | the parameter its test looks for: `# pF "sdgcomb" 0.4` |
| `effects/` | `SynthDef("global_name" ++ ~dirt.numChannels)` reading the orbit's `dryBus` into its `effectBus`, then `~sdg.addGlobalEffect(\global_name, [\params])` | one of its parameters: `# pF "sdgspace" 0.5` |

A module runs once per event, behind the sound, and only when its test passes.
A global effect runs once per orbit for as long as the orbit lives, sleeps while
the orbit is silent, and wakes when one of its parameters arrives. Every orbit
gets its own instance, first in the chain, ahead of SuperDirt's delay and
reverb.

```supercollider
~sdg.synthNames;          // what synths/ added to the SynthDescLib
~sdg.moduleNames;         // what modules/ added to ~dirt.modules
~sdg.globalEffectNames;   // what effects/ registered
~sdg.reload;              // edited a file? read them all again, no reboot
~sdg.free;                // take the modules and global effects out again
```

Synths stay on the server after `free`: they do no harm, and Tidal may still be
playing them.

### Order matters once

Global effects can only be added to orbits that exist, and orbits exist after
`~dirt.start`. Make the `SuperDirtGUI` after `start`, as above. If you make it
before, synths and modules load at once and the global effects follow on their
own as soon as `start` has run. After a later `~dirt.stop; ~dirt.start(...)`,
call `~sdg.applyGlobalEffects` yourself.

The one thing this extension has to get right is to **add to SuperDirt without
ever redefining it**: no SuperDirt method is overridden and no SuperDirt file is
touched, so it cannot break the SuperDirt next to it and needs no change when
SuperDirt updates.

## Extra samples

```supercollider
~sdg.loadSamples("~/more-samples");   // one bank per subfolder, appended to what is loaded
```

The folder is laid out like Dirt-Samples: a subfolder per bank, the files
inside numbered by name, so `s "bank:3"` picks the fourth. Banks already loaded
stay.

## The window

`~sdg.gui`.

```
 SuperDirtGUI
 -----------------------------------------------------
  synths          modules          global effects
  sdgsine         sdgcomb          global_sdgspace
 -----------------------------------------------------
  [ reload ]  [ free ]
```

Three lists of what is loaded, kept current, and two buttons: **reload** reads
every file again, **free** takes the modules and global effects out. What the
window grows into is being decided in the workshop.

## Files

| file | what it holds |
|------|---------------|
| [classes/SuperDirtGUI.sc](classes/SuperDirtGUI.sc) | the loader: folders, bookkeeping, the global effect chain, the window |
| [synths/example-synth.scd](synths/example-synth.scd) | template for a Tidal synth, `sdgsine` |
| [modules/example-module.scd](modules/example-module.scd) | template for a per-event effect, `sdgcomb` |
| [effects/example-global.scd](effects/example-global.scd) | template for a per-orbit effect, `global_sdgspace` |
| [HelpSource/Classes/SuperDirtGUI.schelp](HelpSource/Classes/SuperDirtGUI.schelp) | the help file, cmd/ctrl + d on the class name |
| [examples/superdirt-gui-demo.scd](examples/superdirt-gui-demo.scd) | a startup you can evaluate a block at a time |
| [install.scd](install.scd) | symlinks the repository into the extensions folder |
| [superdirt-gui.quark](superdirt-gui.quark) | the quark manifest, named after the repository so `Quarks` can find it |

## Issues

Bug reports and questions are welcome in
[Issues](https://github.com/fracnesco/superdirt-gui/issues). If the recompile
after an update fails, the first `ERROR` line in the post window names the
class at fault; if it is not one of ours, another quark is.

## License

MIT — see [LICENSE](LICENSE). SuperDirt itself is GPL; nothing of it is copied
here, this only calls it.
