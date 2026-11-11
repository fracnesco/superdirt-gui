# How it is built

## The idea

SuperDirt is installed next to this and stays as it is: no method of it is
overridden, no file of it is changed, no module of it is replaced. So this cannot
break the SuperDirt beside it, and needs no change when SuperDirt updates. That is
the one rule everything else follows from.

It works because SuperDirt already has places to add things:

| what this adds | how it gets in |
|----------------|----------------|
| a synth | `SynthDef.add`: SuperDirt plays any SynthDef by name |
| a module | `dirt.addModule`, the way SuperDirt adds its own |
| the sample players | one module, `sdgplayer`, put in front of `sound` by reordering `dirt.modules`; when a player takes an event it sets `~diversion`, the key `sound` checks before playing |
| a global effect, the fader, an insert, a track's send and return, a send to a return | a `GlobalDirtEffect` in `orbit.globalEffects`, then `initNodeTree` |
| a value from the window | `orbit.set`, into the orbit's default event, so every later event carries it |
| extra samples | the sound library's public `loadSoundFilePaths`, `loadSoundFile`, `makeEventForBuffer` |
| the meters | SuperDirt's own `dirt_rms`, with the reply rate put where it lands (see [superdirt-notes.md](superdirt-notes.md)) |

The two things that do not fit are outside SuperDirt's lists on purpose, and are
walked back into place after every rebuild: a VST insert's synth, because a plugin
lives in its synth, and a return track, because it belongs to no orbit. See
[the-mixer.md](the-mixer.md).

## The pieces

| class | is | keeps |
|-------|----|-------|
| `SuperDirtGUI` | the owner: reads the folders, installs the player module and the fader, holds the mixer, the library helpers | `fileRecords` (per file: its kind, what it defined, its hash, whether it failed), the mixer state (`faderValues`, `muteStates`, `soloStates`), the inserts, tracks and returns |
| `SDGSamplePlayer` | one way of reading a sample: name, test, one SynthDef per sample width | nothing that changes |
| `SDGBufRate`, `SDGBufPhase` | the players' rate and read pointer, the arithmetic of SuperDirt's `dirt_sample` taken out | nothing |
| `SDGTrack` | one global effect of one orbit as a track | its two busses, send, return, level, pre and post inserts |
| `SDGInsert`, `SDGInsertTarget` | an insert, and what its ParaEQ or Compressor drives | the effect or synth, its object, a VST plugin's last state |
| `SDGBusTrack` | what a return and the master share | its in and tap busses, its insert chain, sends, out synth |
| `SDGReturn` | a return track | feedback and line busses, its head, a send per orbit, a link to every return |
| `SDGMaster` | the master track | everything not routed direct; writes to the hardware |
| `SDGSession` | the mixer under a name | what the last load had to leave out, the window's part of the last session |
| `SDGWindow` | the control window | only what is on screen: the mixer state is `SuperDirtGUI`'s, and it follows. The explorer's model, its sort and which banks are open |
| `SDGExplorerView` | the explorer's list: header, rows, scroll bar | the rows it was handed, the selected row |
| `SDGSampleTags` | every sample's analysis and what was set by hand, the queries, the tag picker's matches | two files in the support directory, the analysis queue |
| `SDGSync`, `SDGSyncWindow` | long samples on one tempo and in one key, as Tidal code; its window | the samples and the settings, the preview's routines (stopped by `free`) |
| `SDGHintBar` | the strip along the bottom of both windows | the text |
| `SampleEditor`, `SampleEdit`, `SampleCutter`, `SampleFeatures` ... | the sample editor and the analysis: the [sc-sample-editor](https://github.com/fracnesco/sc-sample-editor) quark, looked up by name (`SuperDirtGUI.editorClass`, `SDGSampleTags.features`) | the `editorHost` this loader makes (a `SampleEditorDirtHost` with it as owner), set as `SampleEditor.defaultHost` while it runs |
| `SDGMidi` | controllers on the mixer, through the Modality toolkit, and Tidal's `cF` controls: `~sdg.midi` | the mappings, the controllers they use, what learn is waiting for |
| `SDGMidiMap`, `SDGMidiTarget` | one element of one controller moving one thing; that thing, by an address that survives rebuilds and sessions | the range, curve, takeover and button mode; nothing |
| `SDGMidiWindow` | the controllers and the mappings, one at a time in depth | nothing of its own |
| `SDGTidalLog`, `SDGTidalLogWindow` | every event Tidal sends, as a line of text, and its window: `~sdg.tidalLog` | the last lines, the filter; an `OSCFunc` of its own while listening |

`SuperDirtGUI` knows nothing of the window: all it does is `changed`. Everything
the window does is a method of `SuperDirtGUI` you could call from code, and the
README's code blocks are those calls.

## Reading a file

`SuperDirtGUI(~dirt)` reads `synths/`, `modules/`, `players/` and `effects/` in that
order, each alphabetically, with `~dirt` and `~sdg` set. The folders are found from
the class file, so a symlink and a quark install work alike. For each file
(`prReadFile`):

1. A `// requires:` line naming a class this sclang lacks skips it, before
   compiling: an unknown class would be a compile error, and a plugin you do not
   have is not an error. The window lists the file as needing it.
2. It compiles, with the interpreter's preprocessor. A file that does not compile
   is marked failed and what it loaded before keeps playing.
3. It runs. An error while running is treated the same way.
4. What it defined is noted, then whatever it defined last time and no longer does
   is taken out, unless another file defines it too.

Compiling first matters: `String:load` answers quietly on a parse error, so it could
not tell "does not compile" from "ran".

What a file defined is found in two ways. Synths and modules go straight into
SuperDirt and the SynthDescLib, where defining a name again replaces the object
under it, so the objects from before and after are compared by identity. Players
and global effects say their names as they register, while `loadingNames` is set.

`loadFile` reads one file again on its own, and `reloadChangedFiles` does that to
every file whose text hash differs from what was read last, or that is new, or that
is gone. A global effect read again starts on its new SynthDef in every orbit, in its
place in the chain, and the rest of the chain plays on. It is not put back into an
orbit it was taken out of, and an offered effect goes into no orbit by itself.

## How a change reaches the window

```
 your code, Tidal's events, the window's own controls
         │
         ▼
 SuperDirtGUI ── changed(\mixer) ───────────► SDGWindow.update
         ▲                                         │
         │ once a second, while the window is open │ rebuilds or refreshes
         │   the size of the sound library         │ what that is about
         └── the saved sessions                    ▼
             reloadChangedFiles                 the screen
```

| notification | sent when | with |
|--------------|-----------|------|
| `\loaded` | files were read, or the loader was freed | the kind and path, after one file |
| `\samples` | banks were added | the bank, for one file |
| `\reloaded` | a sample was read back into its buffer | the name and index |
| `\mixer` | a fader, mute, solo or send changed | |
| `\param` | an effect's parameter was set, by a knob, a controller or code (`setEffectParam`) | the orbit or return, the parameter |
| `\inserts` | an insert came, went, moved or was bypassed | the orbit |
| `\tracks` | a track came, went, moved or took another effect | the orbit |
| `\trackLevel` | a track's fader moved | the orbit |
| `\returns` | a return came, went or changed | |
| `\session` | a session was loaded | its name |
| `\sessions` | a session was saved | its name |

The pieces `SuperDirtGUI` owns notify on their own: `sdg.tags` sends `\tags` and `\analysis`,
`sdg.sync` `\sync`, `sdg.midi` `\maps`, `\map`, `\learn` and the rest (its help says which).

Stock SuperDirt's sound library says nothing when it changes, so the window compares
its size once a second, for banks loaded straight through `~dirt.loadSoundFiles`. The
same routine is what makes saving a file in the IDE enough to hear it: with the window
closed nothing watches the folders, and `~sdg.loadFile` or `~sdg.reload` does it.

## Order at startup

Global effects can only go into orbits that exist, and orbits appear in `~dirt.start`.
Made after `start`, everything happens at once. Made before, synths, modules and players
load at once, and a routine polls for orbits for up to two minutes and then does the
global effects. After `~dirt.stop; ~dirt.start(...)`, call `~sdg.applyGlobalEffects`.

`SynthDef.add` is asynchronous, so `applyGlobalEffects` syncs the server before it
rebuilds an orbit, or a fresh effect would never start. It can be called twice: an
orbit that has an effect is left alone.

## Who frees what

| | what happens |
|-|--------------|
| **`~sdg.reload`** | Modules, players and content effects out, every file read again. Inserts, returns and the mixer state stay. A track keeps its inserts and fader and waits for its effect's name to come back; one still waiting afterwards goes. A return's effect starts again on its new SynthDef. |
| **a file saved** | That file only, as above. |
| **the window closed** | Its meters, listeners and poll stop. Everything on the server stays and goes on playing; `~sdg.gui` shows it again. |
| **`~sdg.free`** | Returns, inserts, tracks, modules, players, global effects and the fader out, the tap busses freed, the window closed, the MIDI mappings and the Tidal log stopped. SynthDefs stay on the server: they do no harm, and Tidal may still be playing them. Modality's MKtls stay too: they are Modality's. |
| **cmd-period** | SuperDirt makes its orbits again on `ServerTree`, with every effect in `globalEffects` and the state it held, so tracks and ordinary inserts come back by themselves. A hook registered after SuperDirt's makes VST inserts' synths again, reopens each plugin with its last state, and restarts the returns and master. The language side comes back through `CmdPeriod`: `SuperDirtGUI` restarts the background analysis and notifies `\cmdPeriod`, on which the window starts its meter and library routines again and drops any refresh that was on its way; the sample editor, the MIDI, sync and Tidal-log windows register themselves. The meter and playhead `OSCFunc`s are fixed. Nothing of the mixer's state is touched. |
| **a sample bank** | Stays in SuperDirt's library. `free` does not unload it. |

A session on disk and the files in the four folders are yours; nothing here
deletes them. A `.bak` beside a sample is only ever written once, and is only read by
**revert**.

## What is optional

Only SuperDirt is a dependency of the quark. Everything else is looked up by name at
run time, never compiled against, so its absence costs a menu entry and nothing more:

| extension | used for | without it |
|-----------|----------|------------|
| BusManager | names the orbit busses `orbit0_dry`, `orbit0_fx`, and gives each strip a menu of named outputs | a number box only, and its button is disabled |
| BusRecorder | **recorder**: a file per orbit and return | the button is disabled |
| DigitDrag | number boxes drag by the digit under the pointer | ordinary number boxes |
| ParaEQ, Compressor, Insight, VSTPlugin | the four standard kinds of insert | the kind is greyed out in the menu |
| Modality-toolkit | MIDI and HID controllers on the mixer (`~sdg.midi`) | no controllers; Tidal `cF` mappings still work |
| anything subclassing `FxParamUnit` (Growl, Tunnel, Smear, Cloud, Stammer, Sympathy, Spiral; Hall, Plate, Coil, Shimmer from `sc-reverbs`) | more kinds of insert, found at class init, named by nothing here | not listed |
| sc3-plugins | `sdghole`, `sdgecho`, `sdgjverb`, `sdgroom`, `sdgshimmer` | those files are skipped with one line in the post window |
| mi-UGens | `sdgmiverb`, `sdgclouds`, `sdgrings`, `sdgwarps` | the same |

None of these is pulled in by `Quarks.install`: a student who wants one installs it.
