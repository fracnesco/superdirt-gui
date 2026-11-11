# Playing the insert effects from Tidal

An insert built on sc fx kit (Growl, Tunnel, Smear, Cloud, Stammer, Sympathy,
Spiral, and the reverbs Hall, Plate, Coil and Shimmer) can listen to Tidal.
Once it listens, its Tidal name becomes a sound that moves it. Its parameters
are plain Tidal words, and a pattern sets them event by event. Its lfos can
also follow Tidal's cycle. The eq, the compressor, the insight and VST inserts
cannot listen.

```haskell
d9 $ s "growl*8" # drive1 (range 0 24 sine) # tone 2000
d9 $ s "growl" # n "<0 3 5>"
d1 $ s "bd*4" # fx "growl" (mix "0.2 0.8" # tone 900)
```

## Switching it on

Right click an insert in the mixer:

- **listen to tidal** switches it on. The insert's slot turns soft green and
  shows its Tidal name after a `~`, as in `Growl ~growl`.
- **tidal name...** renames it. A name is lower case letters and digits, with a
  letter first. It cannot start with `sdg`, cannot be `fx`, and no two inserts
  can share one. The first Growl is called `growl`, the next `growl2`, and so
  on.
- **copy a tidal line** puts a line for this insert on the clipboard, with its
  first parameters at their current values: `d9 $ s "growl" # input 0 # tone 20000 # squash 0`.

The effect's own window has the same switch at the top, **listen to tidal**.
While it is on, every parameter a pattern can set is labelled in green with its
Tidal word. Where the word is the same as the label (`tone`), only the colour
changes. The parameters a pattern cannot set keep their usual grey label.

From SuperCollider:

```supercollider
g = ~sdg.addInsert(0, \growl);
g.listening = true;
~sdg.setInsertTidalName(g, "dirt");   // answers nil, or what is wrong with the name
g.tidalKeys;                          // the parameters a pattern may set
```

A session saves each insert's name and whether it listens. A pasted insert
gets a new name, because the original still has the old one.

## `s "growl"`: the insert as a sound

While an insert listens, its name is a sound of SuperDirt's library. The sound
plays nothing. Its event moves that one insert:

| you write | it does |
|---|---|
| `# drive1 12`, `# tone 2000` | sets a knob |
| `# freezeOn 1` | a toggle: 0 or 1 |
| `# type1 "fold"`, `# type1 "3"` | a menu, by its item's name or its index as a string |
| `# n 3`, `s "growl:3"` | picks preset 3; the number wraps over the presets |

Within one event, the preset is applied first and the words override it.

Only the words the event itself carries count. The orbit's default event never
speaks for the effect. Words that are SuperDirt's own (`pan`, `speed`,
`legato`...) never reach an effect, even if one had a parameter of that name.

An `s "growl"` event leaves the orbit's other global effects alone. A plain
sound event sets every one of them from its own missing values, which means 0;
that is SuperDirt's behaviour. Whichever orbit the event plays on, the name
finds the insert. So a `d9` pattern can move a Growl on orbit 0, on a return or
on the master.

If an insert's name is already a sample bank or a synth, the insert does not
take it over. The console says so (`... is already a sound of the library`),
`s "<name>"` keeps playing the library's sound, and `fx "<name>" (...)` still
reaches the insert. Rename the insert to have both.

## `fx`: with a sound

On a kick, `# mix 0.5` would mean nothing to Growl. `fx` sends words to an
insert by name, from any sound:

```haskell
d1 $ s "bd*4" # fx "growl" (mix 0 # tone 800)
d2 $ s "~ sn" # fx "growl" (mix 1 # n 3)
```

The event plays its sound and moves the insert in the same moment. `fx` renames
each word to `<name>_<word>` (`growl_mix`), and a module of the add-on reads
those, so `pF "growl_mix"` by hand does the same. `s "sdgcontrol"` is a sound
that plays nothing and reads only these, for one pattern that moves several
inserts at once.

## The words

The boot file defines one Tidal word per parameter of every effect. The block
between `BEGIN insert effect words` and `END insert effect words` is written by
`examples/tidal/make-fx-words.scd` from the effects' own tables: a knob or a
toggle is a `pF`, a menu a `pS`. Run it again after an effect's table changes.
Six of the words are Tidal's own, with the same meaning: `hold`, `smear`,
`decay`, `detune`, `ring` and `delay`.

Each effect decides which of its parameters Tidal can move (`*tidalParams` in
its class). The list keeps the controls that make musical sense in steps, such
as drive, filter frequency, feedback, mix and pitch. It leaves out the output
level and anything that clicks or reallocates.

The parameters were named so that each word is free in Tidal. On 2026-11-08
seven of them were renamed in their effects, because Tidal, Haskell or the
boot's own functions already used the name. Old presets, sessions and code
still reach them by the old name (`*renamed`):

| effect | was | is |
|---|---|---|
| Smear | `scramble` | `jumble` |
| Smear | `stretch` | `warp` |
| Smear, Cloud | `frozen` | `freezeOn` |
| Cloud | `density` | `grains` |
| Cloud, Stammer | `reverse` | `backwards` |
| Spiral | `spread` | `sides` |
| Plate | `mod` | `modDepth` (as Hall's) |

Menus whose items have a slash, such as Tunnel's divisions (`1/8d`) and
Stammer's grid (`1/16`), take their index as a string (`divL "3"`). Tidal's
mininotation reads `1/16` as `1` slowed by 16. The sync menus were named in
words for that reason.

## Timing

Every value goes to the insert's synth inside the event's own OSC bundle, at
the time the event sounds. So a step lands exactly when the event's sound does.
Each knob still has its own short smoothing (`lag`, mostly 50 ms), so steps
are glided, not instantly switched.

The last value wins. The parameter stays where the pattern left it after the
pattern stops, the knob in the effect's window shows that value, and a session
saves it.

## The window stays cheap

A sequenced value is written into the effect's object without telling its
window. Then, at most ten times a second (`SDGFxControl.redrawRate`), the
window is told once that the settings changed. A pattern of 200 events a
second costs at most ten redraws, and nothing at all while the window is
closed. The check counted 10 redraws for 200 events.

## Lfos on Tidal's cycle

Growl's two lfos, Tunnel's lfo and Spiral's drift each have a **sync** menu.
`free` runs the lfo at its rate in Hz. The other items give one period every so
many cycles: `four`, `two`, `one`, `half`, `quarter`, `eighth`, `sixteenth`,
`third`, `sixth`, `twelfth`. They are Tidal words too: `# lfo1Sync "quarter"`.

The add-on gives these inserts Tidal's `cps` and `cycle` from the events of
every orbit, inside the event's bundle. It does this for those effects only,
whether or not they listen. It sends an update when the cps changes, or when
half a cycle has passed since the last one. Between updates the lfo runs on at
the cps it was given, so it keeps the cycle's phase as well as its speed. When
Tidal stops, the lfo carries on at the last cps.

How it is built: `FxLfo.kr(shape, rate, phase, sync)` in sc fx kit reads three
controls that every effect in one graph shares: `fxCps`, `fxCycle` and the
trigger `fxSync`. Its position is `fxCycle + Sweep.kr(fxSync) * fxCps`. The
cycle is sent modulo 4, the longest period, so a float32 keeps its fraction.
With `sync` left out (an Ndef that uses FxLfo), nothing changes.

Measured on 2026-11-08 (scratchpad `t_seqfx.scd`) by reading Growl's lfo
through its meter replies:

- at cps 1, the period was 1.000 s;
- at cps 0.5, it was 2.000 s;
- 0.6 s after a cycle of 0.75, the saw read -0.307 against the -0.3 expected;
- in free mode at 2 Hz, the period averaged 0.495 s.

## A sleeping insert

An insert sleeps after 4 s of silence, like every SuperDirt effect. A value
sent to it while it sleeps is kept and applies when it wakes. An `s "growl"`
event does not wake it: there is nothing to hear yet. The next sound on its
orbit does.

## How it was checked

Measured on 2026-11-08:

- `t_seqfx.scd`:
  - each value is in the object, in the effect's state and on the server
    (`/s_getn`);
  - no other global effect of the orbit changed;
  - the orbit's default event does not speak for the effect;
  - menus work by name, by index and by index as a string;
  - `n` picks a preset with wrap, as does `fx "growl" (n 2)` on a sound, and
    a word wins over the preset in the same event;
  - not listening leaves the insert untouched and takes the sound out;
  - the sound follows a rename, and a name the library already has is not
    taken over (the warning is in the log);
  - old parameter names still reach the renamed ones;
  - the throttle, the lfo clock, the window, and `free` all behave as
    described.
- `t_seqgui.scd` (8 assertions): the window's check switches listening both
  ways, and the labels show the Tidal words and come back.
- `t_fxtables.scd` (69 assertions): every effect's Tidal list is in its table,
  and every effect starts at one and two channels on a stock server.
- `t_seqtidal.scd` (5 assertions): real Tidal 1.9.5 in ghci, with the boot
  file's `fx` and word blocks exactly as written. `s "growl*4" # drive1 "0 10 20 30"`
  arrived as those four values, `type1 "fold"` set the menu by name, and
  `fx "growl" (mix 0.25 # tone 900)` from orbit 1 reached orbit 0's insert.
- Lesson 39: every block runs in ghci with the boot file's blocks.

Nobody has listened to it yet.
