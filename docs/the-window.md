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

## Return strips

After the orbits. **+ return** adds one, named by the next free letter. **feedback off**
beside it is the switch for sends between returns.

| control | gesture | does |
|---------|---------|------|
| the name | click | shows its effect's parameters on the effects tab, in the orbit's place |
| **✕** | click | takes it out, with its effect, inserts and every send to it |
| the effect menu | | the global effect on this return, or none. It comes out fully wet |
| **on** | click | off bypasses the effect: the line passes as if it were not there |
| **+ pre**, **+ post** | click | an insert before or after the effect |
| send knobs | drag | to each return, itself too. They work while feedback is on |
| the meters and fader | | what the return plays, after its fader |
| **M**, **S** | click | mute; solo, which stops the orbits' direct sound and keeps what they send here |
| **post fader** | click | **pre fader**: what is sent into this return ignores the source's fader |
| the limit menu, out | | as on an orbit: a feedback loop can get loud, so keep the limiter on |

## The effects tab

The global effects of the selected orbit, as tracks. Clicking a return's name puts that return in
the orbit's place: its parameters as knobs, with no track strips, since its own strip has those controls.

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
| **reload** | reads every file again |
| **free** | takes the modules, players and global effects out of SuperDirt, and closes the window |
| **load folder...** | as on the explorer |

A file that failed is marked in its list, and what plays is the version that loaded last. One
that needs a plugin you do not have is listed as needing it. While the window is open, saving
a file reads it again within a second (`SDGWindow.reloadOnSave`).

## What the window keeps

The three panels are tabs along the right. All the window holds of its own is the tab, the
selected orbit, the orbit sounds play into and the speed, amp and pan knobs, the right panel's width,
and a session keeps those too. The mixer itself is `SuperDirtGUI`'s.
