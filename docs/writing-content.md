# Writing content

A content file is a plain `.scd` holding one thing, named after it, in one of four
folders. It is `.load`ed with `~dirt` and `~sdg` set, so it must parse as one expression:
wrap it in `( )`. Each folder has an `example-*.scd` to copy, which is also what
**new...** copies.

The name is the Tidal word. Start yours with a prefix of your own, the way these use `sdg`:
**new...** refuses any word SuperDirt already means something by, because a module named
`speed` would switch on with every event that has one.

| folder | one file holds | switched on by | runs |
|--------|----------------|----------------|------|
| `synths/` | a SynthDef | `s "name"` | per event, as the sound |
| `modules/` | `~dirt.addModule` and its SynthDef | a parameter | per event, behind the sound |
| `players/` | `~sdg.addSamplePlayer` | a parameter, or `# pS "player" "name"` | per sample event, instead of `dirt_sample` |
| `effects/` | a SynthDef and `~sdg.addGlobalEffect` | a parameter | per orbit, always |

## A synth

```supercollider
(
SynthDef(\sdgsine, { |out, sustain = 1, pan = 0.5, freq = 440, accelerate = 0, sdgshape = 0.2|
	var env = EnvGen.ar(Env.perc(0.01, 0.99, 1, -4), timeScale: sustain, doneAction: 2);
	var glide = Line.kr(1, 1 + accelerate, sustain);
	var sig = SinOsc.ar(freq * glide);
	sig = (sig * (1 + (sdgshape * 6))).tanh;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
```

SuperDirt hands it every argument it declares that the event carries: `out` always, `sustain`,
`pan`, `freq` (from `n` or `note`), `accelerate`, `speed`, `begin`, `end`, and any key you send
with `pF` or `pI`. There is no gain here: SuperDirt applies it afterwards. `DirtPan` spreads
the signal over the orbit's channels, so the output is always `~dirt.numChannels` wide, whatever
your sound is.

`attack` and `release` are taken: they switch on SuperDirt's own `envelope` module. Voices here
use `sdgatk` and `sdgrel`. SuperDirt frees the event's group at the end of `sustain`, tail or no
tail, so a release has to fit inside it; `sdgpad`'s `fit` shortens attack and release together
when the note is too short for both.

### Presets

A synth's file can register presets after its SynthDef, events in SuperDirt's sound library that
Tidal's `n` picks (`s "mypad:2"`); `note` then plays the pitch. [presets.md](presets.md) has the
block to copy, and how the window's preview saves your own.

### A drum voice

A drum voice is a synth whose file also registers its presets in SuperDirt's sound library, one
event each, so that `n` picks a preset and the hit lasts as long as it rings rather than one step:

```supercollider
[
	(sdgfreq: 50, sdgdecay: 0.8),
	(sdgfreq: 58, sdgdecay: 0.25)
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\mykick, (instrument: \mykick, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
```

- **`sustain`'s default is a ceiling.** With `useSynthDefSustain` the event lasts the SynthDef's
  `sustain` default, so declare there the longest the voice can ring, and let its own envelope free
  it. `legato` and `sustain` from a pattern can only shorten it, like a choke.
- **`note: 0` in every preset**, or `n` transposes by n semitones as well as picking.
- **No `freq` in a preset**: it would beat the pitch SuperDirt works out from `note`. Keep the
  voice's pitch in `sdgfreq` and multiply it by `freq / 261.6256`.

[drums.md](drums.md) has the whole file, the parameter words the drum voices share, and the level
each kind of drum is tuned to.

## A module

```supercollider
(
~dirt.addModule('sdgcomb', { |dirtEvent|
	dirtEvent.sendSynth("sdgcomb" ++ ~dirt.numChannels, [
		sdgcomb: ~sdgcomb,
		sdgres: ~sdgres ? 1,
		out: ~out
	])
}, { ~sdgcomb.notNil });

SynthDef("sdgcomb" ++ ~dirt.numChannels, { |out, sdgcomb = 0.5, sdgres = 1|
	var sig = In.ar(out, ~dirt.numChannels);
	var wet = CombC.ar(sig, 0.05, sdgcomb.linexp(0, 1, 0.0005, 0.05), sdgres);
	ReplaceOut.ar(out, (sig + wet) * 0.5);
}).add;
)
```

It is named after the parameter that switches it on, and its test is `{ ~param.notNil }`. The
SynthDef is named `<name>` + the channel count, reads the event's bus at that width and replaces
it. SuperDirt puts it in the event's group behind the sound, so it hears what the sound made.

**`~sdgres ? 1`, never a bare `~sdgres`:** a nil in a hand-written argument list is sent as int 0,
not dropped, so a key the event lacks would set your control to 0.

## A sample player

A page of its own: [sample-players.md](sample-players.md).

## A global effect

```supercollider
(
var numChannels = ~dirt.numChannels;

SynthDef("global_sdgspace" ++ numChannels, { |dryBus, effectBus, gate = 1, sdgspace = 0, sdgtime = 0.3|
	var sig = In.ar(dryBus, numChannels);
	var wet = CombL.ar(sig, 1, sdgtime.clip(0.01, 1), 2);
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	DirtPause.ar(sig, graceTime: 4);
	Out.ar(effectBus, wet * sdgspace * env);
}, [\ir, \ir]).add;

~sdg.addGlobalEffect(\global_sdgspace, [\sdgspace, \sdgtime]);
)
```

- The def is `global_<name>` + the channel count. SuperDirt appends the count itself, so you
  register `\global_sdgspace`.
- It takes `dryBus`, `effectBus` and `gate`, writes to `effectBus` only, and calls
  `DirtPause.ar(sig, graceTime: 4)`, which is what lets it sleep. It is added first in each orbit,
  on a track: [the-mixer.md](the-mixer.md).
- The list is the event keys it listens to. With `alwaysRun` false, the default, only an event
  that carries one of them wakes it.

### An offered effect

`~sdg.offerGlobalEffect` instead registers an effect that goes in no orbit by itself and costs
nothing until someone adds it. Then it is on, and every event wakes it, so three things change:

```supercollider
(
var n = ~dirt.numChannels;

SynthDef("global_myverb" ++ n, { |dryBus, effectBus, gate = 1, myverb = 0.4, myverbtime = 0.5|
	var in = In.ar(dryBus, n);
	var wet = CombL.ar(in, 1, myverbtime.clip(0.01, 1), 2) * myverb.clip(0, 1).lag(0.05);
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_myverb, [\myverb, \myverbtime], (
	myverb: [0, 1, \lin],
	myverbtime: [0.01, 1, \exp]
));
)
```

- **The amount's default is not 0**, because no pattern has to send it.
- **Every control is `clip`ped:** a pattern that drops a parameter it was sending sets it to 0,
  through `GlobalDirtEffect:set`, and 0 must not break anything. It also switches the effect off at
  once, so keep the values you want to hold on the knobs.
- **`DirtPause` hears input + wet,** so a tail rings out before it sleeps.

The third argument is the knob ranges, anything `asSpec` takes.

A plugin UGen needs a line of its own, or the file would be a compile error for anyone without it:

```supercollider
// requires: MiClouds (mi-UGens)
```

Several are separated with commas, and anything in brackets is for the reader. Without the class,
the file is skipped with one line in the post window, and the modules tab lists it as needing the plugin.

## The header

The comments above the code are what the modules tab shows when a file is selected:

```supercollider
// synths/mybass.scd - what it does, in a line or two.
//
// parameters:
//   mycut    30 to 18000 Hz   the low pass
//   myq      0 to 1           resonance
//
//   d1 $ note "a2" # s "mybass" # pF "mycut" 400
```

The first paragraph is the description, without its `folder/file.scd - `. Under `// parameters:`,
each line is three columns with **two spaces or more** between them: name, range, what it does.
Ranges are what is useful, read from the def's clips and scaling, and nothing enforces them. The
parameter section ends at the first line that does not start with `//   `.

A listed name must exist as a control, be the file's switch, or be a word its `prepare` reads
(`sdghold`). Internal controls (`sdgbuf2`) stay out. A file with no such section shows its
SynthDef's controls and their defaults instead. [library.md](library.md) is made from these headers;
[make-library.scd](make-library.scd) reports any that disagree with their SynthDef.

## new... and saving

```supercollider
~sdg.newFile(\players, \mywarp);             // players/mywarp.scd from the example, loaded
~sdg.newFile(\effects, \myecho, \sdgspace);  // effects/myecho.scd, a copy of sdgspace's file
~sdg.nameProblem(\modules, \room);           // -> 'room' is taken - a parameter of dirt_reverb
~sdg.sourceFile(\players, \mywarp);          // the file a name came from
~sdg.loadFile("players/mywarp.scd");         // read one file again after saving it
```

`newFile` copies a file and replaces its old name wherever it stands as a whole word. `_` is a gap
and letters and digits are not, so `global_sdgspace` and `sdgwarp_1_2` have the name in them and
`sdgspacer` does not. Other parameters keep their names: rename them in the copy, or two effects
share `sdgtime` and one value sets both.

A name has to be a lowercase letter, then letters, digits or `_`. It is refused if the file exists,
or if the word means something already: one of your names, a SuperDirt module, a bank, a sound, a
default-event key, an effect's parameter, a SynthDef or one of its controls, or a key SuperDirt reads
from every event.

While the window is open, saving is enough:

- What the file no longer defines is taken out.
- A global effect starts again on its new SynthDef in every orbit, in its place, and the other effects
  keep their tails.
- A file that does not compile or throws is marked, with the reason in the post window, and the last
  version that loaded keeps playing until you save a fixed one.
- A synth stays on the server when its file stops defining it.

If you installed with `Quarks.install`, keep your edits to your copies: `Quarks.update` pulls with
git, and a changed example file can stop the pull.
