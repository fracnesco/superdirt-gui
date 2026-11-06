# SuperDirtGUI

**Extra synths, effects, sample players, a mixer window and a sample editor
for SuperDirt, loaded into the instance you already run.** A SuperCollider
extension.

Four folders of plain `.scd` files — `synths/`, `modules/`, `players/`,
`effects/` — go into a running SuperDirt with one line, and a window shows
what is playing. SuperDirt itself stays untouched, so this sits next to a
normal install and updates on its own.

```supercollider
~dirt = SuperDirt(2, s);
~dirt.loadSoundFiles;
~dirt.start(57120, 0 ! 12);
~sdg = SuperDirtGUI(~dirt);     // reads synths/, modules/, players/, effects/
~sdg.gui;                       // meters and faders, effects, the sound explorer
```

Then, from Tidal: `d1 $ s "sdgsine" # n "0 7"`.

It depends on **SuperDirt** — and through it on Vowel and Dirt-Samples — and on
nothing else. sc3-plugins are only needed if a content file uses one of them;
the templates do not. Other extensions are used when they happen to be
installed and ignored when they are not: **BusManager** names the orbit
busses so its window can meter and listen to them, **BusRecorder** writes the
orbits to disk, **DigitDrag** lets the
number boxes drag by the digit under the pointer, and **ParaEQ**,
**Compressor**, **Insight** and **VSTPlugin** are the four kinds of insert an
orbit can have — each is offered in the window only when it is installed.
[docs/requirements.md](docs/requirements.md) lists every piece, required and
optional, with how to install it.

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
~sdg.synthNames;                // -> [ sdgsine, sdgacid, sdgchoir, ... ]
~sdg.samplePlayerNames;         // -> [ sdgsmear, sdgself, ..., sdgwarp ]
```

Select `SuperDirtGUI` and press cmd/ctrl + d for the help file, and see
[examples/superdirt-gui-demo.scd](examples/superdirt-gui-demo.scd) for a
startup you can evaluate a block at a time.

## What gets loaded

`SuperDirtGUI(~dirt)` reads every `.scd` file in four folders next to
`classes/`, in alphabetical order, with `~dirt` and `~sdg` set — exactly the
way SuperDirt reads its own `synths/` folder. Each folder holds one example
file that is a template: **new...** in the window copies it under a name you
give and opens the copy in the IDE (see [Your own](#your-own)), or copy it by
hand.

| folder | one file holds | switched on from Tidal by |
|--------|----------------|---------------------------|
| `synths/` | a SynthDef in SuperDirt's argument convention: `out`, `sustain`, `pan`, `freq`, `accelerate`, and any key you send with `pF` | `s "sdgsine"` |
| `modules/` | `~dirt.addModule(name, func, test)` and its SynthDef, named `"name" ++ ~dirt.numChannels`, reading and replacing the event's bus | the parameter its test looks for: `# pF "sdgcomb" 0.4` |
| `players/` | `~sdg.addSamplePlayer(name, synthFunc, test)`: another way of reading a sample buffer, one SynthDef per sample channel count | the parameter its test looks for: `# pF "sdgwarp" 1` |
| `effects/` | `SynthDef("global_name" ++ ~dirt.numChannels)` reading the orbit's `dryBus` into its `effectBus`, then `~sdg.addGlobalEffect(\global_name, [\params])` | one of its parameters: `# pF "sdgspace" 0.5` |

[examples/tidal/](examples/tidal/README.md) plays each of them from Tidal. Make
[examples/tidal/boot.hs](examples/tidal/boot.hs) your Boot Tidal Path (Pulsar:
Settings, Packages, tidalcycles): it declares every word, so you write
`sdgcomb 0.4` for `pF "sdgcomb" 0.4`, and loads the extra functions. Then the
files in `lessons/`, a file at a time, starting with `00-setup.tidal`.
Files 26 to 34 are pieces: nine parts each, `d1` to `d9`, one style each -
drum and bass, ambient, electro, polyrhythms, big beat - in one
key and one tempo, meant to play at once, with variations to drop over them.

A module runs once per event, behind the sound, and only when its test passes.
A global effect runs once per orbit for as long as the orbit lives, sleeps while
the orbit is silent, and wakes when one of its parameters arrives. Every orbit
gets its own instance, first in the chain, ahead of SuperDirt's delay and
reverb.

Some global effects wait to be asked for. A file that registers with
`~sdg.offerGlobalEffect` instead of `addGlobalEffect` goes in no orbit by itself
and uses no CPU or memory: it sits in the add menu of the window's effects tab
until you add it, to one orbit at a time or to a return. Added, it is on: its
amount has a default, it wakes on every event, and an event need not carry any
of its parameters. The ones that come with this extension:

| effect | what it is | plugin |
|--------|------------|--------|
| `sdghole` | Greyhole as a reverb, a big diffuse cloud | [sc3-plugins](https://github.com/supercollider/sc3-plugins) |
| `sdgecho` | Greyhole as a delay, echoes exactly `sdgechotime` apart that blur as they repeat | sc3-plugins |
| `sdgjverb` | JPverb as a hall, separate decay for lows and highs | sc3-plugins |
| `sdgroom` | JPverb as a small room | sc3-plugins |
| `sdgshimmer` | JPverb with a pitch shifter in its feedback | sc3-plugins |
| `sdgmiverb` | MiVerb, the reverb of Mutable Instruments' Clouds | [mi-UGens](https://github.com/v7b1/mi-UGens) |
| `sdgclouds` | MiClouds, the granular processor | mi-UGens |
| `sdgrings` | MiRings, whatever comes in rings at a pitch | mi-UGens |
| `sdgwarps` | MiWarps, what comes in modulates an oscillator | mi-UGens |

Each file's header lists its parameters and ranges, and the window shows them.
A file names what it needs on a line of its own, `// requires: MiClouds
(mi-UGens)`. Without that plugin the file is skipped with one line in the post
window, and the modules tab lists it as needing the plugin; it is not an error.
To install mi-UGens, put the folder from the release for your system in the
extensions folder and recompile.

From Tidal a parameter works as for any global effect, with one catch: a pattern
that stops sending a parameter it was sending sets it to 0, so `sdghole 0.6`
followed by a pattern without it switches `sdghole` off. A knob in the window
goes into every event and does not have that problem. Keep the parameters you
want to hold on the knobs, or send them in every pattern.

A sample player takes the place of SuperDirt's own sample synth for the events
that ask for it. The template hands the buffer to `Warp1`, so `speed` stretches
time and `sdgpitch` sets the pitch on its own. The player is picked per event:
`# pS "player" "sdgwarp"` names one, otherwise the first player whose test
passes wins, and an event that asks for none is played by SuperDirt as always.
The SynthDef function declares SuperDirt's nine sample controls first — `out,
bufnum, sustain, begin, end, speed, endSpeed, freq, pan` — and anything after
them is sent only when the event carries it. `SDGBufRate` and `SDGBufPhase`
give the rate and the begin-to-end pointer `dirt_sample` uses, so the trim,
`speed`, `accelerate` and `n` mean what they always mean.

```supercollider
~sdg.synthNames;          // what synths/ added to the SynthDescLib
~sdg.moduleNames;         // what modules/ added to ~dirt.modules
~sdg.samplePlayerNames;   // what players/ registered
~sdg.globalEffectNames;   // what effects/ registered
~sdg.reload;              // edited a file? read them all again, no reboot
~sdg.free;                // take the modules, players and global effects out again
```

Synths stay on the server after `free`: they do no harm, and Tidal may still be
playing them.

### Your own

The window's **modules** tab has a list per folder - synths, effects (the
folder `modules/`), players, global effects - and under each list **new...** and
**edit...**:

- **new...** asks for a name and makes `<folder>/<name>.scd`, a copy of the
  example — or of the one selected in the list — with the old name replaced
  by yours wherever it stands as a word. The copy is loaded at once and opens
  in the IDE. The name is the word Tidal switches it on with, so the dialog
  refuses any word SuperDirt already means something by: a module named
  `speed` or `room` would wake on every event that has one, and one named
  `lpf` would replace SuperDirt's own. Start your names with a prefix of your
  own, the way the templates use `sdg`.
- **edit...**, a double click, or enter opens the file a name came from as a
  document in the IDE.
- **Save the file and it is read again** within a second, as long as the
  window is open. What the file no longer defines is taken out; a global
  effect starts again on its new SynthDef in every orbit, in its place in the
  chain, and the other effects keep playing, tails and all.
- A file that does not compile or throws is marked in the list and reported
  in the post window, and the version that loaded last keeps playing until you
  save a fixed one.

A copy keeps the template's other parameters — `sdgpitch`, `sdgtime` — under
their old names: rename them in the copy. Until you do, two global effects
share a parameter like `sdgtime`, and one value sets both.

The same from code, window or no window:

```supercollider
~sdg.newFile(\players, \mywarp);        // players/mywarp.scd from the example, loaded
~sdg.newFile(\effects, \myecho, \sdgspace);  // effects/myecho.scd, a copy of sdgspace's file
~sdg.nameProblem(\modules, \room);      // -> 'room' is taken - a parameter of dirt_reverb
~sdg.sourceFile(\players, \mywarp);     // the file a name came from
~sdg.loadFile("players/mywarp.scd");    // read one file again after saving it
```

If you installed with `Quarks.install`, keep your edits to the copies:
`Quarks.update` pulls with git, and a changed example file can stop the pull.

### Order matters once

Global effects can only be added to orbits that exist, and orbits exist after
`~dirt.start`. Make the `SuperDirtGUI` after `start`, as above. If you make it
before, synths, modules and players load at once and the global effects follow
on their own as soon as `start` has run. After a later
`~dirt.stop; ~dirt.start(...)`, call `~sdg.applyGlobalEffects` yourself.

The one thing this extension has to get right is to **add to SuperDirt without
ever redefining it**: no SuperDirt method is overridden and no SuperDirt file is
touched, so it cannot break the SuperDirt next to it and needs no change when
SuperDirt updates. The two places where it reaches furthest in are done the
same way. The players sit in a module of their own in front of SuperDirt's
`sound` module, and when one takes an event it marks it diverted — the flag
SuperDirt's own module checks before playing — so the sample is heard once.
The fader is a global effect of SuperDirt's own kind, put last before its
`dirt_rms` and `dirt_monitor`, scaling the orbit's dry and effect busses in
place; the monitor after it is SuperDirt's, untouched.

## Extra samples

```supercollider
~sdg.loadSamples("~/more-samples");   // a bank per subfolder, and one for the files lying in the folder itself
~sdg.addSample("~/one/kick.wav", \mykit);   // one file, appended to a bank (a new bank if the name is new)
```

A folder can be laid out like Dirt-Samples, a subfolder per bank, or just hold
sound files, which make a bank named after the folder; either way the files
are numbered by name, so `s "bank:3"` picks the fourth. Spaces in a folder's
name become `_`, so `my kit` is `s "my_kit"`. Banks already loaded stay, and a
file a bank already holds is not read again. The window's `sound explorer` and
`modules` tabs have a button for the same thing.

## Presets

Every synth but the `sdgsine` template has ten presets or more, and `n` picks one, the way it picks
a sample out of a bank. So on these synths **`n` picks the preset and `note` plays the pitch**:

```haskell
d1 $ s "sdgacid:1*8" # note "a2 a2 a3 a2"       -- the acid's squelch
d2 $ s "sdgfm:1" # note "c6 e6 g6"              -- the FM bell
```

Select a synth in the window's modules tab to try them: a preview with a knob per parameter, a
note, the preset menu, play, random, and copy, which puts what you dialed on the clipboard as a
line of Tidal. save as... keeps it as a preset of your own, on your machine.
[docs/presets.md](docs/presets.md) lists every preset and how loud it is.

## The drum machine

Fifteen of the synths are drums, each a kit by itself: `n` picks a preset, `note` tunes it, and
every knob is a Tidal parameter, the same word doing the same job in every voice (`sdgdecay`,
`sdgpunch`, `sdgtone`, `sdgcolor` ...). The classic ones follow the 808, 909 and 606 (`sdgkick`,
`sdgsnare`, `sdgclap`, `sdgtom`, `sdghat`, `sdgrim`, `sdgcowbell`); `sdgfmdrum`, `sdgcymbal` and
`sdgmodal` the Elektron machines; `sdgplastic` and `sdgslam` are hyper-real hits, materials
pushed past the real; `sdgping`, `sdgratchet` and `sdgchaos` small machines: pinged filters, rolls
in one event, chaotic noise. They need no plugins.

```haskell
d1 $ s "sdgkick:1*4"                                -- the 909 kick
d2 $ s "~ sdgsnare ~ sdgclap"
d3 $ s "sdghat*8" # n "0 0 2 0" # cut 1             -- the closed hat chokes the open one
d4 $ s "sdgtom:1 sdgtom:1" # note "0 -5"             -- one tom, tuned down
```

A hit rings as long as the drum does, not one step, and `legato` or `sustain` only shorten it,
like a hand on a cymbal. At gain 1 each one is as loud as the stock sample of its kind: swap `bd`
for `sdgkick` and nothing jumps.

`players/sdglayer.scd` puts a synthesized drum under any sample (`# pF "sdglayer" 0.5`), and
`samples/` holds six banks rendered from the voices, 88 files: kicks, snares and claps, hats and
cymbals, percussion, effects, and stacked, reversed and crushed hits with four one-bar loops.

```supercollider
// sdgbd, sdgsd, sdghh, sdgperc, sdgfx, sdgresamp are loaded with the rest when ~sdg is made;
// SuperDirtGUI.loadOwnSamples = false, before SuperDirtGUI(~dirt), leaves them out
```

[docs/drums.md](docs/drums.md) has every preset, the shared words, how the levels were measured
and how to write a drum voice of your own; `examples/tidal/lessons/16-drums-classic.tidal` to
`19-drums-small-machines.tidal` play each family, and `20-drums-hybrid.tidal` the banks
and `sdglayer`.

Eight modules process drums one hit at a time, on samples and voices alike: `sdgtrans` (more or
less attack and body), `sdgboom` (a tuned sub under each hit), `sdgbody` (the hit strikes a drum
skin, a bar, a tube or a bell), `sdglatex` (a resonant squelch), `sdgchrome` (metal), `sdgroll`
(a roll from one hit, speeding up), `sdggated` (gated reverb) and `sdgdive` (a tape stop).

```haskell
d1 $ s "bd*4" # pF "sdgboom" 0.4 # pF "sdgboomfreq" 41
d2 $ s "~ sn" # pF "sdggated" 0.6
d3 $ s "~ ~ ~ cp" # pF "sdgroll" 6 # pF "sdgrollcurve" 0.8
```

[docs/drum-fx.md](docs/drum-fx.md) says how each works and how loud it is;
`examples/tidal/lessons/38-drum-fx.tidal` plays them all.

## The window

`~sdg.gui`, after `~dirt.start`.

```
 SuperDirtGUI                                 12 orbits · 2 channels · port 57120
 --------------------------------------------------------------------------------
  orbit 0  orbit 1  orbit 2 ...  | effects | sound explorer | modules |
  [EQ    ] [Insight]  + insert   |  orbit 0 - effect tracks          |
  [(Comp)]  + insert             |  sdgspace  delay     reverb  ...  |
   + insert                      |  load...   load...   load...      |
  ▌▌│      ▌▌│       ▌▌│         |  on ◀▶✕    on ◀▶✕    on ◀▶✕       |
  -12.4    -18.9     -inf        |  + pre     [EQ]      + pre        |
  M S      M S       M S         |  ▼sdgspace ▼delay    ▼reverb      |
                                 |  + post    + post    [Comp]       |
                                 |  ▌▌│       ▌▌│       ▌▌│          |
                                 |  knobs for the selected effect    |
  limit    limit     limit       |  [on]  EQ · orbit 0    ▲ ▼ ✕      |
  out 0    out 0     out 0       |  [off] Comp · orbit 0  ▲ ▼ ✕      |
 --------------------------------------------------------------------------------
  hover over a control and this bar says what it does
```

One strip per orbit, named after it: **orbit 0** is where Tidal's `d1` plays,
**orbit 1** is `d2`, and so on, the numbers `# orbit` uses. On each strip, top
to bottom:

- **inserts** — an eq, a compressor, an insight or a VST plugin, in the order
  the signal goes through them, after SuperDirt's delay, reverb and leslie and
  before the fader, the way a channel of a desk has them. **+ insert** adds one
  and opens its window; a click on one opens its window again; a right click
  bypasses it, moves it up or down, or removes it. As many as you like, of any
  kind, twice over if you want. They are the ParaEQ, Compressor, Insight and
  VSTPlugin extensions, with their own windows and presets, and they stay on
  the orbit through a reload, the window closing, a cmd-period.
- **VST plugins** — **+ insert → VST plugin...** lists the VST effects on this
  computer, with a search; pick one and its own editor opens. The first search
  over many plugins can take a minute; after that VSTPlugin remembers them. A
  plugin keeps everything you set in it through anything SuperDirtGUI does; a
  cmd-period closes it, and it comes back as it was a moment after your last
  change in its editor. A plugin runs all the time, silent or not.
- **meters** — peak and rms per channel, 20 times a second, a tick every
  5 dB, reading after the inserts and the fader, so a muted orbit shows nothing.
  Under them the highest peak since you last clicked there, in dB, red once it
  reached 0.
- the **fader**, its level in dB written under it as it moves (`-1.0 dB`), with
  **mute** and **solo**, the monitor's **limiter** mode, and
  the orbit's output bus — as a number, and as a dropdown of BusManager names
  when that quark is installed.

The **recorder** button at the top opens BusRecorder's window, ready to write
one file per orbit: `rec_001_orbit0.wav`, `rec_001_orbit1.wav` and so on, each
as many channels as SuperDirt has (stereo unless you started it otherwise), all
starting on the same sample. Press record there. A file holds what the orbit's
meters show: after its effects, inserts and fader, so a muted orbit, or one
silenced by another's solo, records silence. The monitor's limiter is not in it -
which is why the files are 32-bit float, where going over 0 dB does not clip. The preset is only applied when the
recorder holds nothing else; a list you changed stays as you left it, and
`~sdg.recordOrbits` puts the preset back.

Three tabs on the right:

- **effects** — the global effects of the selected orbit (click its name), as
  tracks, a strip each. **load effect...** puts another global effect on that
  track, keeping its inserts and fader. **+ pre** adds an insert before the
  effect, on what goes into it, and **+ post** one after it, on what comes out.
  The fader and meters are on what the track adds to the orbit, so the dry
  signal is not touched. Switch one off and on, move it in the chain, take it
  out, add any SynthDef on the server that follows the global-effect convention
  (`dryBus`, `effectBus`, `gate`) on a new track, click a strip's name and set
  its parameters with knobs, dragged up and down. An orbit has one of each effect, so the menu
  lists the ones it does not have yet. A value set here goes into the
  orbit's default event too, so the next event from Tidal carries it and
  nothing flips back; the `x` puts it back to what it was. Under them the
  orbit's inserts, with on, the arrows and the cross.
- **sound explorer** — every bank of the sound library as a folder that opens
  onto its samples, the synths in a folder of their own, and a search over
  both. It is a list with columns, as in the Finder: name, duration, bpm, key
  and tags, with n, date added and date created a right click on the header
  away. Click a header to sort by it, again to sort the other way. The search
  also takes `tag:kick`, `-tag:melodic`, `bpm:120` (its halves and doubles
  too) and `key:am`, and the buttons under it are the commonest tags. Enter or
  a double click plays the selected sound into the orbit chosen below, with the
  speed, amp and pan knobs; `e` or **edit...** opens a sample in the editor;
  `t` edits its tags; a right click shows the file in the Finder, opens it, or
  copies its path. **load folder...** adds the samples of a folder you pick.
  Banks loaded later appear on their own.
- **modules** — what the four folders gave, a list each with **new...** and
  **edit...** (see [Your own](#your-own)), the reload and free buttons, and
  **load folder...**. A right click on a name offers the same, and shows
  its file in the Finder. Selecting a name shows, at the bottom, what it does
  and its parameters with their ranges, read from the comments at the top of
  its file:

  ```
  // synths/mybass.scd - what it does, in a line or two.
  //
  // parameters:
  //   mycut    30 to 18000 Hz   the low pass
  //   myq      0 to 1           resonance
  ```

  Two spaces or more between the columns. A file without that section shows its
  SynthDef's controls and their defaults instead.

The same from code, window or no window:

```supercollider
~sdg.setFader(0, 0.5);     // orbit 0 at half
~sdg.setMute(1, true);     // orbit 1 silent
~sdg.setSolo(2, true);     // only orbit 2
~sdg.effectiveAmp(0);      // -> 0, while orbit 2 is soloed

i = ~sdg.addInsert(0, \eq);           // an eq on orbit 0, before its fader
c = ~sdg.addInsert(0, \compressor);   // a compressor after it
~sdg.moveInsert(c, 0);                // now before it
i.unit.preset = \warm;                // the ParaEQ itself; i.gui for its window
~sdg.removeInsert(c);
v = ~sdg.addInsert(0, \vst, plugin: "ValhallaSupermassive");   // a VST plugin, v.gui for its editor
~sdg.vstPlugins({ |list| list.collect(_.name).postln });        // the VST effects there are
```

### Return tracks

**+ return**, on the right of the orbit strips, adds a return track, as in Ableton:
return A, then B, and a send knob for it on every orbit, from nothing to
0 dB. One reverb can then serve all your orbits.

```supercollider
a = ~sdg.addReturn;
~sdg.addBusInsert(a, \vst, plugin: "ValhallaSupermassive");   // its effects are inserts
~sdg.addBusInsert(a, \eq);               // after it; or \compressor, \insight, an fx kit effect, \hall
~sdg.setSend(0, a, 0.5);                 // orbit 0 into it
```

A return's strip has **+ effect** for its chain of effects - the same kinds as an
orbit's inserts: eq, compressor, insight, the sc fx kit effects, the four reverbs (Hall,
Plate, Coil, Shimmer from `sc-reverbs`) and VST plugins - send
knobs, a fader, mute, solo, the limiter and its output. SuperDirt's own reverb and
delay stay on the orbits, where Tidal's parameters drive them: no event reaches a return.

**post fader** on a return means what goes into it follows the source's fader; click
it for **pre fader**. A muted orbit sends nothing either way. A soloed orbit keeps
its returns, and a soloed return plays what is sent to it without the orbits' direct sound.

**feedback off**, next to **+ return**, is the switch for sends between returns. Turn
it on and the knobs on the return strips work: A into B, B into A, A into itself. Each
of those sends arrives one block later (64 samples, 1.3 ms at 48 kHz), in any order and any shape of
loop. A loop above unity saturates at a bounded level instead of running away, but it
can get loud, so keep the limiter on.

```supercollider
b = ~sdg.addReturn;
~sdg.returnFeedback = true;
~sdg.setReturnSend(a, b, 0.4);
~sdg.setReturnSend(b, a, 0.4);
```

### The master

An orbit's output menu (under its limiter) says where it goes: **master**, or
**direct** to the hardware bus in the box beside it. Everything that is not sent direct
ends in the **master**, the last strip on the right: a chain of effects for mastering,
a fader, a limiter, and the hardware bus it writes to - where orbit 0 wrote before.
With nothing on it, it changes nothing.

```supercollider
~sdg.addBusInsert(~sdg.master, \eq);     // the mastering chain
~sdg.master.level = 0.9;
~sdg.setOrbitDest(2, \direct, 2);        // orbit 2 past the master, to hardware 2-3
~sdg.setOrbitDest(2, \master);           // and back
```

`SuperDirtGUI.useMaster = false` before `SuperDirtGUI(~dirt)` leaves the orbits writing
to the hardware as stock SuperDirt does.

### Folding, copying, pasting

The **‹** beside a strip's name folds it thin: its name upright, one meter, mute and
solo. **›** unfolds it. Sessions keep what is folded.

A right click on an effect (an insert button) copies it, or its whole chain, with
every setting - a plugin with its state. **+ insert**, **+ effect** and the right click
on another effect paste it, on any orbit, effect track, return or the master. A
right click on an effect track's name copies the track; **paste** under the tracks, or
a right click on an orbit's name, puts it on another orbit.

```supercollider
~sdg.copyInserts(~sdg.insertsOf(0));
~sdg.pasteInserts(nil, ~sdg.master);     // orbit index, or nil and a track
~sdg.copyTrack(~sdg.tracksOf(0).first);
~sdg.pasteTrack(3);
```

Returns and the master are always on, so they use CPU even when nothing plays, and
keep tails ringing. The recorder writes a file per return as well. Sessions save
returns, the master, every send and route.

### Sessions

Along the top of the window: a menu of the saved sessions, **load session** for
the one it shows, **save session** to keep the mixer under a name. Choosing in
the menu changes nothing until you press load.

A session holds every orbit's fader, mute, solo, limiter and output, the tracks
with their levels and the parameters the knobs set, the return tracks with every
send, and every insert in its
place with its settings: a VST plugin with its own state, read from the plugin
when you save. It does not hold content files, sample banks or Tidal's patterns.

```supercollider
~sdg.saveSession("gig");
~sdg.sessionNames;
~sdg.loadSession("gig", { |ok| ok.postln });    // in place of what the orbits hold
SDGSession.folder.openOS;                       // a folder per session
```

Each session is a folder in your SuperCollider support directory, so it survives
an update. Copy the folder to give a session to someone, but `session.scd` is
code and runs when loaded, so only load ones you trust. What cannot come back on
another machine (a plugin it does not have, say) is left out with a warning, and
the rest loads. See [SDGSession](HelpSource/Classes/SDGSession.schelp).

## Tags, bpm and key

Every sample in the library is analysed in the background once the window is
open, and remembered: how long it sounds, where its attacks are, its bpm if it
has one, its key or its note if it has a pitch, and tags made from those -
`short`, `percussive`, `melodic`, `kick`, `loop` and more. A bpm or key the
analysis is not sure of is marked, with a `?` and a tag. Add tags, take them
away, set a bpm (`b` in the explorer: typed, or from how many beats the file
holds) or a key: what you set is kept with the file and survives
the next analysis. Nothing is written into a bank's folder.

From Tidal, a sample can be picked by them on every event:

```haskell
let sdgtag = pS "sdgtag"
    sdgnot = pS "sdgnot"
    sdgbpm = pF "sdgbpm"
    sdgkey = pS "sdgkey"

d1 $ s "sdgany*8" # sdgtag "percussive.short" # sdgnot "melodic"
d2 $ s "sdgany" # sdgtag "loop" # sdgbpm 120     -- 60 and 240 too
d3 $ s "sdgany(3,8)" # sdgkey "am" # n (irand 8)
```

`sdgany` is the whole library; a bank's name picks inside it. Without an `n`
the pick is random. See [docs/sample-tags.md](docs/sample-tags.md) and
[examples/tidal/lessons/21-tags.tidal](examples/tidal/lessons/21-tags.tidal).

## Sync

Longer samples - drum loops, arpeggios, pads - on one tempo and in one key,
written as Tidal code. **sync...** in the explorer opens the window; `y`, or
**add to sync** in the right-click menu, adds the selected sample. It reads
each sample's bpm, first beat and key, and writes `setcps`, then a line a
sample:

```haskell
setcps (125/60/4)

d1 $ loopAt 1 $ chop 8 $ s "breaks125:0"

d2 $ loopAt 3 $ chop 24 $ s "breaks152:0"

d3 $ loopAt 2 $ chop 16 $ s "myarp:0" # speed 0.991 # legato 1
```

`loopAt` stretches each one over the cycles its beats fill, so every loop lands
on the grid, read faster or slower, which also moves its pitch. A melodic
sample gets a `speed` that takes it the rest of the way into the key, and
`chop` keeps it in time: every piece starts on its own beat. **play** hears
them together before Tidal does; **copy code** puts the code on the clipboard.
See [docs/sync.md](docs/sync.md) and
[examples/tidal/lessons/23-sync.tidal](examples/tidal/lessons/23-sync.tidal).

## The sample editor

`~sdg.edit(\bd, 3)`, or **edit...** in the explorer, opens one sample of the
library: the waveform, one lane per channel, with the trim markers on the top
strip and the fade handles on the bottom. Drag the markers or type
milliseconds; set a gain or **normalize** to a ceiling; tick **remove DC**.
Every setting is applied on paper and heard in the preview — the library's own
buffer, played through the orbit you pick, so its effects and fader apply — and
the numbers on the right say what the file is and what the save would make of
it: peak, rms, crest, integrated loudness, DC offset, clipping.

**save** writes over the file and keeps the original beside it as `.bak` the
first time, then reads the new file back into the same buffer, so Tidal plays
it at once under the same name and number. **revert** puts the `.bak` back.
**save as...** writes a new file; into the bank's own folder and it is appended
to the bank as its last index.

Above the wave is what the analysis says about the file: its bpm, key and
tags, to change there. With a bpm the beat grid is drawn over the wave: halve
or double it, put its first beat on the first attack with **to 1st hit**, or
slide it with alt + drag. The **slicer** cuts the trim on the grid (a bar down
to 1/32, or triplets) or on the attacks at a **sensitivity**, and draws the
cuts as they move; shift + click adds or takes away one. **export slices...**
asks for a place (the Finder's dialog, at Dirt-Samples the first time) and a
name, writes every slice to a new folder of that name, each file named after it
with its index (`brk_00.wav` is `n 0`), and loads it as a bank. The file itself
is never touched by that.

Keys: space plays and stops, `0` shows the whole file, `-` and `=` zoom, `f`
zooms to the trim, `g` shows the grid, left and right slide, cmd-s saves. cmd + two fingers up and
down zooms at the pointer, two fingers on their own slide, a double click shows
the whole file. On Windows and Linux it is ctrl where it says cmd, and the
mouse wheel where it says two fingers.

## Files

| file | what it holds |
|------|---------------|
| [classes/SuperDirtGUI.sc](classes/SuperDirtGUI.sc) | the owner: folders, bookkeeping, the player module, the fader and the global effect chain, the mixer state, the library helpers |
| [classes/SDGSamplePlayer.sc](classes/SDGSamplePlayer.sc) | one way of reading a sample, as a registry entry: name, test, one SynthDef per channel count |
| [classes/SDGUGens.sc](classes/SDGUGens.sc) | `SDGBufRate` and `SDGBufPhase`, the sample players' rate and read pointer |
| [classes/SDGWindow.sc](classes/SDGWindow.sc) | the control window: strips, effects, sound explorer, modules |
| [classes/SDGSampleEditor.sc](classes/SDGSampleEditor.sc) | the sample editor window and its preview synth |
| [classes/SDGSampleEdit.sc](classes/SDGSampleEdit.sc) | the editor's model: the file in the language, trim, gain, DC, fades, save, revert |
| [classes/SDGSampleAnalysis.sc](classes/SDGSampleAnalysis.sc) | the numbers: peak, rms, crest, DC, BS.1770 loudness, in the language |
| [classes/SDGWaveView.sc](classes/SDGWaveView.sc) | the editor's waveform view, with the beat grid and the slicer's cuts |
| [classes/SDGSampleFeatures.sc](classes/SDGSampleFeatures.sc) | what a sample sounds like as numbers: envelope, onsets, tempo, key, in the language |
| [classes/SDGSampleTags.sc](classes/SDGSampleTags.sc) | every sample's tags, bpm and key: the analysis queue, what was set by hand, the queries Tidal's picks use |
| [classes/SDGSlicer.sc](classes/SDGSlicer.sc) | a sample cut on the grid or on its attacks, written as a bank of its own |
| [classes/SDGExplorerView.sc](classes/SDGExplorerView.sc) | the sound explorer's list: columns, sorting, banks that open |
| [classes/SDGSync.sc](classes/SDGSync.sc) | long samples on one tempo and in one key: the plan, the Tidal code, the preview |
| [classes/SDGSyncWindow.sc](classes/SDGSyncWindow.sc) | the sync window |
| [classes/SDGSynthPresets.sc](classes/SDGSynthPresets.sc) | a synth's presets as changed in the window, kept on this machine outside the repo |
| [classes/SDGSession.sc](classes/SDGSession.sc) | the whole mixer saved under a name and brought back, VST plugin states included |
| [classes/SDGTrack.sc](classes/SDGTrack.sc) | a global effect of an orbit as a track: inserts before and after it, a fader on what it adds |
| [classes/SDGBusTrack.sc](classes/SDGBusTrack.sc) | what returns and the master share: a chain of effects, sends, fader, mute, solo, output |
| [classes/SDGReturn.sc](classes/SDGReturn.sc) | a return track: sends from every orbit, a chain of effects, sends to other returns for feedback |
| [classes/SDGMaster.sc](classes/SDGMaster.sc) | the master track: everything not sent direct, a mastering chain, the hardware output |
| [classes/SDGInsert.sc](classes/SDGInsert.sc) | an insert on an orbit - an eq, a compressor, an insight or a VST plugin - and the target its ParaEQ or Compressor drives |
| [classes/SDGHintBar.sc](classes/SDGHintBar.sc) | the strip along the bottom of both windows that says what the pointer is over |
| [synths/example-synth.scd](synths/example-synth.scd) | template for a Tidal synth, `sdgsine` |
| [synths/sdgacid.scd](synths/sdgacid.scd) | a 303-like voice with a filter envelope, accent and slide, `sdgacid` |
| [synths/sdgsupersaw.scd](synths/sdgsupersaw.scd) | seven detuned saws, wide, for chords and stabs, `sdgsupersaw` |
| [synths/sdglead.scd](synths/sdglead.scd) | a mono lead: two saws, a square an octave down, late vibrato, slides, `sdglead` |
| [synths/sdgsync.scd](synths/sdgsync.scd) | a hard sync lead with a sweep on each note, `sdgsync` |
| [synths/sdgpad.scd](synths/sdgpad.scd) | a warm pad of drifting saws and pulses under a slowly moving filter, `sdgpad` |
| [synths/sdgstrings.scd](synths/sdgstrings.scd) | a string machine: saws through a three-line chorus, `sdgstrings` |
| [synths/sdgchoir.scd](synths/sdgchoir.scd) | a choir pad with a vowel you can morph, `sdgchoir` |
| [synths/sdgpluck.scd](synths/sdgpluck.scd) | a plucked string, Karplus-Strong, `sdgpluck` |
| [synths/sdgfm.scd](synths/sdgfm.scd) | two-operator FM: electric piano, bells, metal, `sdgfm` |
| [synths/sdgreese.scd](synths/sdgreese.scd) | a reese bass: beating saws over a clean sine, `sdgreese` |
| [synths/sdgsub.scd](synths/sdgsub.scd) | a sub bass with a drop in pitch at the front, `sdgsub` |
| [synths/sdgkick.scd](synths/sdgkick.scd), [sdgsnare](synths/sdgsnare.scd), [sdgclap](synths/sdgclap.scd), [sdgtom](synths/sdgtom.scd), [sdghat](synths/sdghat.scd), [sdgrim](synths/sdgrim.scd), [sdgcowbell](synths/sdgcowbell.scd) | drum voices after the 808, 909 and 606, with presets |
| [synths/sdgfmdrum.scd](synths/sdgfmdrum.scd), [sdgcymbal](synths/sdgcymbal.scd), [sdgmodal](synths/sdgmodal.scd) | an FM drum kit, cymbals and bells, a struck object whose material is a knob |
| [synths/sdgplastic.scd](synths/sdgplastic.scd), [sdgslam](synths/sdgslam.scd) | hyper-real hits: bubbles, latex, squeaks, and a kick limited flat |
| [synths/sdgping.scd](synths/sdgping.scd), [sdgratchet](synths/sdgratchet.scd), [sdgchaos](synths/sdgchaos.scd) | pinged resonators, a roll in one event, chaotic noise percussion |
| [modules/example-module.scd](modules/example-module.scd) | template for a per-event effect, `sdgcomb` |
| [modules/sdgtrans.scd](modules/sdgtrans.scd), [sdgboom](modules/sdgboom.scd), [sdgbody](modules/sdgbody.scd), [sdglatex](modules/sdglatex.scd), [sdgchrome](modules/sdgchrome.scd), [sdgroll](modules/sdgroll.scd), [sdggated](modules/sdggated.scd), [sdgdive](modules/sdgdive.scd) | effects for drums, one hit at a time: attack and body, a sub, a resonating body, a squelch, metal, a roll, gated reverb, a tape stop |
| [players/example-player.scd](players/example-player.scd) | template for a sample player, `sdgwarp` |
| [players/sdgscan.scd](players/sdgscan.scd) | the sample as an oscillator, `sdgscan` |
| [players/sdgscratch.scd](players/sdgscratch.scd) | a wobbling read pointer, from tape wow to a scratch, `sdgscratch` |
| [players/sdgfreeze.scd](players/sdgfreeze.scd) | locks the spectrum and rings on, `sdgfreeze` |
| [players/sdgself.scd](players/sdgself.scd) | the sound moves its own read pointer, `sdgself` |
| [players/sdgfold.scd](players/sdgfold.scd) | back and forth inside a window, `sdgfold` |
| [players/sdgcrush.scd](players/sdgcrush.scd) | a staircase read pointer, `sdgcrush` |
| [players/sdgsmear.scd](players/sdgsmear.scd) | a spectral wash and long stretches, `sdgsmear` |
| [players/sdgcloud.scd](players/sdgcloud.scd) | grains with a place, a pan and a direction of their own, `sdgcloud` |
| [players/sdgonset.scd](players/sdgonset.scd) | slices at the file's real hits, `sdgonset` |
| [players/sdgcross.scd](players/sdgcross.scd) | one sample's spectrum through another's, `sdgcross` |
| [players/sdglayer.scd](players/sdglayer.scd) | any drum sample with a synthesized drum under it, `sdglayer` |
| [effects/example-global.scd](effects/example-global.scd) | template for a per-orbit effect, `global_sdgspace` |
| [effects/sdghole.scd](effects/sdghole.scd), [sdgecho](effects/sdgecho.scd) | offered effects on Greyhole |
| [effects/sdgjverb.scd](effects/sdgjverb.scd), [sdgroom](effects/sdgroom.scd), [sdgshimmer](effects/sdgshimmer.scd) | offered effects on JPverb |
| [effects/sdgmiverb.scd](effects/sdgmiverb.scd), [sdgclouds](effects/sdgclouds.scd), [sdgrings](effects/sdgrings.scd), [sdgwarps](effects/sdgwarps.scd) | offered effects on mi-UGens |
| [samples/](samples/) | six banks rendered from the drum voices, loaded into the library when `~sdg` is made (`SuperDirtGUI.loadOwnSamples`) |
| [samples/render.scd](samples/render.scd) | renders those banks, and banks of your own, a block at a time |
| [HelpSource/Classes/](HelpSource/Classes/) | the help files, cmd/ctrl + d on a class name |
| [docs/](docs/README.md) | the long version: how it is built, the mixer, the window, every parameter, the drum machine, what SuperDirt does underneath |
| [examples/superdirt-gui-demo.scd](examples/superdirt-gui-demo.scd) | a startup you can evaluate a block at a time |
| [examples/tidal/](examples/tidal/README.md) | `boot.hs`, the Tidal boot file with every word and the functions (`functions/`); `multi target tidal/`, a boot that sends to TouchDesigner too; then `lessons/`, the same from Tidal, a file per thing: the parameter shortcuts, the synth, the module, choosing a player, the warp player, the global effect, your own copies, all of it together; then music: IDM in two manners, acid, arpeggios, stuttery beats, every voice in `synths/`, ten sample readers, samples picked by their tags, the drum machine family by family, effects for drums, pointillistic trance arpeggios after Lorenzo Senni, loops on one tempo with loopAt and chop; then five pieces of nine parts each, meant to play at once - drum and bass, ambient, electro, polyrhythms, big beat |
| [examples/grooves/](examples/grooves/) | a Tidal study in rhythm on the library - the Egyptian maqsum at jungle speed, chopped like the Amen, in the manner of ABADIR - with variations, a section that pushes it, and lines that drift by themselves; the Haskell functions that change a running pattern in place, crossfade scenes as an Octatrack does, and pick sample players are in `examples/tidal/functions/` |
| [examples/pdef/](examples/pdef/) | the same sounds sequenced from SuperCollider with Pdefs and no Tidal: an `\sdg` event type that keeps a Pbind's beats, cycles and levels in line with Tidal's, then first beats, synths and scales, a step-grid drum machine, acid with slides, effects and the mixer from code, live changes, polyrhythm and euclid, chance and Markov chains, sliced breaks, a piece in sections; its README sets Tidal and Pbind side by side |
| [install.scd](install.scd) | symlinks the repository into the extensions folder |
| [install-quarks.scd](install-quarks.scd) | installs every optional quark (BusManager, BusRecorder, DigitDrag, ServerPanel, the inserts, Modality) in one go, and checks what you have |
| [superdirt-gui.quark](superdirt-gui.quark) | the quark manifest, named after the repository so `Quarks` can find it |

## More

[docs/](docs/README.md) goes further than this page: [how it is built](docs/architecture.md),
[the mixer's signal path](docs/the-mixer.md), [every control of the window](docs/the-window.md),
[every synth, player and effect, parameter by parameter](docs/library.md),
[presets](docs/presets.md), [the drum machine](docs/drums.md), [drum effects](docs/drum-fx.md), [writing your own](docs/writing-content.md), [sample players](docs/sample-players.md),
[the sample editor](docs/sample-editor.md), [tags, bpm and key](docs/sample-tags.md), [sync](docs/sync.md), [sessions](docs/sessions.md),
[what SuperDirt actually does](docs/superdirt-notes.md) and [how it is tested](docs/testing.md).

## Issues

Bug reports and questions are welcome in
[Issues](https://github.com/fracnesco/superdirt-gui/issues). If the recompile
after an update fails, the first `ERROR` line in the post window names the
class at fault; if it is not one of ours, another quark is.

## License

MIT — see [LICENSE](LICENSE). SuperDirt itself is GPL; nothing of it is copied
here, this only calls it.
