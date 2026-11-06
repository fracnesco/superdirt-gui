# The window

`~sdg.gui`, after `~dirt.start`. Evaluating it again fronts the window that is open.
Closing the window changes nothing on the server. What the controls control is in
[the-mixer.md](the-mixer.md); this page is what each one is and does.

Hover over anything and the bar along the bottom says what it does, as it does in the
sample editor. A parameter is a knob, dragged up and down, with its name and value beside
it; only a strip's fader is a slider. Number boxes drag by the digit under the pointer if
DigitDrag is installed.

The panel on the right, with the effects, the sound explorer and the modules, is as wide as
you drag it. Drag the dotted strip between it and the orbit strips; a double click puts it back
to 450 pixels. The strips scroll sideways when they no longer fit. A new window opens at the
last width, and a session keeps it.

## Along the top

| control | does |
|---------|------|
| the session menu | the saved sessions. Choosing one changes nothing |
| **load session** | replaces the mixer with the one in the menu |
| **save session** | asks for a name, and keeps the mixer under it. Over an existing name the button says **replace**. [sessions.md](sessions.md) |
| **midi** | the MIDI window: controllers, **midi learn** and every mapping ([midi.md](midi.md)). Lit while midi learn is on. Without Modality it still maps Tidal controls |
| **from tidal** | a window with every event Tidal sends, a line each, newest at the top ([below](#from-tidal)) |
| **bus manager** | BusManager's window; disabled when it is not installed |
| **recorder** | BusRecorder's window, ready to write a file per orbit and return; disabled when it is not installed |

## An orbit's strip

One per orbit, named after it: orbit 0 is where `d1` plays. Top to bottom:

| control | gesture | does |
|---------|---------|------|
| the name | click | selects the orbit for the effects tab |
| an insert's button | click | opens its window |
| | right click | a menu: open, bypass or switch on, move up, move down, remove; on a VST insert also its generic controls and *remember its state now* |
| **+ insert** | click | a menu of the kinds. One whose extension is missing is greyed out. It adds the insert after the ones above, before the fader, and opens its window |
| a send knob per return | drag | this orbit's send to that return, nothing to 0 dB |
| the meters | | peak and rms per channel, a tick every 5 dB, after the inserts and fader |
| the peak readout | click | clears it. The highest peak since the last click, in dB, red once it reached 0 |
| the fader | drag | the orbit's level, written in dB under it. The slider goes to 1.5 times, about +3.5 dB |
| **M**, **S** | click | mute, solo |
| the limit menu | | how the monitor guards the output: raw, limit, clip |
| out | type | the bus this orbit's monitor writes to. With BusManager, also a menu of named busses |

Strips side by side keep their faders level whatever each one holds: every strip leaves
room for the longest insert list in the row.

With Modality installed, a right click on a fader, **M**, **S** or a send knob gives its MIDI menu
(learn midi, edit mapping..., remove midi mapping, post its code), and so does one on a return's or
a track's fader and on a parameter's knob. A control a controller moves is marked in blue. While
**midi learn** is on, every control that can be mapped has a blue ground: click one, it turns
orange, and the next control moved on the controller is mapped to it. See [midi.md](midi.md).

## Return and master strips

Returns and the master stay on the right, as in Ableton. **+ return**
adds a return, named by the next free letter; **feedback off** beside it is the switch for
sends between returns. The master is always there, last.

| control | gesture | does |
|---------|---------|------|
| the name | click | shows its effects on the effects tab, in the orbit's place |
| **✕** | click | takes it out, with its effects and every send to it |
| **‹** | click | folds it thin (**›** unfolds it) |
| effect buttons | click, right click | as an orbit's inserts: its window; bypass, move, copy, paste, remove |
| **+ effect** | click | an effect at the end of the chain - eq, compressor, insight, an fx kit effect, a VST plugin - or what was copied |
| send knobs | drag | a return's: to each return, itself too, while feedback is on |
| the meters and fader | | what it plays, after its fader |
| **M**, **S** | click | mute; solo. A return's solo stops the orbits' direct sound and keeps what they send here |
| **post fader** | click | a return's **pre fader**: what is sent into it ignores the source's fader |
| the output menu, out | | the master, or direct to the hardware bus in the box; the master's box is where everything goes |
| the limit menu | | as on an orbit: a feedback loop can get loud, so keep the limiter on |

An orbit's own output menu offers the master and direct; a right click on an orbit's
name folds it, and copies or pastes its inserts or an effect track.

## The effects tab

The global effects of the selected orbit, as tracks. Clicking a return's or the master's
name puts it in the orbit's place: its effects as rows, with no track strips. A right click on a
track's name copies it; **paste**, beside **add**, puts it on the selected orbit.

| control | gesture | does |
|---------|---------|------|
| **↻** | click | reads the orbit's effects again: one that came from code gets a track |
| the add menu, **add** | | any SynthDef that takes `dryBus`, `effectBus` and `gate` and is not in this orbit yet, on a new track after the others. This is where offered effects wait |

Each track is a strip of its own:

| control | gesture | does |
|---------|---------|------|
| **load effect...** | menu | another global effect on this track. Its inserts and fader stay |
| **on** | click | off pauses the effect's synth, on resumes it |
| **◀**, **▶** | click | moves the track in the chain |
| **✕** | click | takes the effect and its inserts out |
| **+ pre**, **+ post** | click | an insert before the effect (on what goes in) or after it (on what comes out). Top goes first |
| the insert buttons | click, right click | as on an orbit's strip |
| the name | click | shows its parameters as knobs under the strips |
| the meters, fader | | what the track adds to the orbit. The dry signal is not touched |

Under the strips, a knob, a number box and an **x** per parameter. A value goes into the
orbit's default event as well as onto the synth, so the next event from Tidal carries it and
nothing flips back. **x** puts it back to what it was before the window touched it.

Under those, the same orbit's inserts: **on**, the name (its window), **▲**, **▼**, **✕**, and
a menu with **add** for a new one.

## The sound explorer

Every bank of the sound library as a folder that opens onto its samples, the synths in a
folder of their own, and a search over both. This is the tab a new window opens on
(`SDGWindow.explorerTab` changes that). It is a list with columns, as in the Finder's list view:
name, duration, bpm, key and tags to start with, and **n**, date added and date created on a right
click on the header. What the bpm, key and tags are, and how they are found, is in
[sample-tags.md](sample-tags.md).

| gesture | does |
|---------|------|
| a click on a column's header | sorts by it; again, the other way. Banks among banks, samples within each bank |
| a right click on the header | which columns show |
| typing in the search | narrows to banks whose name has every word in it, shown whole, and to samples whose bank and file name have every word between them (`808 bd`: the bd files of the 808 banks); `tag:kick`, `-tag:melodic`, `bpm:120` and `key:am` narrow by the analysis |
| the tag buttons under the search | the commonest tags: click shows only those samples, alt-click leaves them out. **tags** lists every one |
| click | selects; picking a sample sets the **n** box |
| the triangle, double click on a bank, left and right | opens and closes a bank; a bank opened goes first in the analysis |
| enter, double click on a sound, space, **play** | plays the selected sound into the orbit chosen below, with the speed, amp and pan knobs |
| `e`, **edit...** | opens the selected sample in the [editor](sample-editor.md) |
| `t` | edits the selected sample's tags; on a bank, adds tags to all its samples |
| `b`, **set bpm...** | sets the selected sample's bpm by hand, typed or from how many beats it holds; on a bank, one bpm for all, or beats, which gives each sample the bpm of its own length. ÷2, ×2, and back to the analysis |
| `y`, **sync...** | adds the selected sample, or every sample of a bank, to the [sync window](sync.md), which **sync...** opens |
| up, down, page up, page down, home, end | move through the list |
| right click | a menu: play, edit, add to sync, edit tags, set bpm, bpm ÷2 and ×2, bpm back to the analysis, remove a tag, tags back to the analysis, analyse again, show the file in the Finder or Explorer, open it in its default app, copy its path, open all, close all |
| **load folder...** | adds the samples of a folder you pick. [Extra samples](../README.md#extra-samples) |
| **open all**, **close all**, **↻** | every bank open or closed; read the library again |

A bpm or key the analysis is not sure of has a `?`. Tags set by hand are in the accent colour, the
analysis's dimmed, and the unsure ones red. `…` is a sample not analysed yet: the count at the top says
how far the analysis has got.

A sound is played through a `\dirt` event, so it goes through the orbit the way Tidal's
would. **n** is the sample in the bank, or the note for a synth. Banks loaded later appear on
their own within a second. The column sorted by and the columns shown are kept in a session.

## The modules tab

What the four folders gave, a list each: synths, effects (the folder `modules/`), players,
global effects.

| gesture | does |
|---------|------|
| click | selects; at the bottom, what it does and its parameters with their ranges, read from its [header](writing-content.md#the-header) |
| double click, enter, **edit...** | opens its file: in the IDE as a document whose environment holds `~dirt` and `~sdg`, or in the default app outside the IDE |
| **new...** | a dialog: a name, and which file to copy. It refuses a name that is taken and says why |
| right click | a menu: edit, new from it, new, show the file, copy its path |
| a synth selected | under its notes, a **preview**: a knob per parameter of its header and one for `sustain`, a note (0 is c5), the preset menu (`n`) and an orbit. **play** sends one event, as Tidal would; letting go of a knob, or changing the note or preset, plays too. **copy** puts it on the clipboard as a line of Tidal - `d1 $ s "sdgkick:1" # note (-2) # pF "sdgdecay" 0.3` - with only what you moved away from the preset. **random** throws every knob, **reset** goes back to the preset, **save as...** keeps the knobs as a new preset, **delete** (twice) takes one out, **file presets** brings back the synth's own: [presets.md](presets.md) |
| **reload** | reads every file again |
| **free** | takes the modules, players and global effects out of SuperDirt, and closes the window |
| **load folder...** | as on the explorer |

A file that failed is marked in its list, and what plays is the version that loaded last. One
that needs a plugin you do not have is listed as needing it. While the window is open, saving
a file reads it again within a second (`SDGWindow.reloadOnSave`).

## From tidal

`~sdg.tidalLog.gui`, or **from tidal** along the top. Every `/dirt/play` message that reaches
SuperDirt's port, as a line: the pattern (`d1`, or the name of a `p "name"`), the orbit, the cycle
the event starts on, the sound as `s:n`, and every other parameter in the order Tidal sent it. It
listens with an `OSCFunc` of its own beside SuperDirt's, only while the window is open, so nothing of
SuperDirt changes and every event plays whether or not it is shown.

| control | does |
|---------|------|
| **pause**, space | holds the lines still; events still play |
| **clear** | forgets every line so far |
| **post** | writes the lines into the post window, oldest first |
| the orbit menu | only one orbit's events |
| the filter | only lines with that text in them: a sound, a pattern (`d2`), a parameter |
| **timing** | adds `cps` and `delta` to each line |

It keeps the last 500 lines (`SDGTidalLog.maxEntries`), and draws the list again at most ten times a
second, so a busy pattern costs the language little.

## What the window keeps

The three panels are tabs along the right. All the window holds of its own is the tab, the
selected orbit, the orbit sounds play into and the speed, amp and pan knobs, the right panel's width,
the explorer's sort and columns, and a session keeps those too. The mixer itself is `SuperDirtGUI`'s.
