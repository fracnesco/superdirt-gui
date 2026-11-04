# The drum machine

Fifteen drum voices in `synths/`, a sample player that layers a drum under any sample, and six
banks of samples rendered from the voices. Each voice is a kit by itself: `n` picks a preset, `note`
tunes it, and every knob is a Tidal parameter, the same word doing the same job in every voice. They
use only the UGens that come with SuperCollider 3.13, so they play on any install, without
sc3-plugins.

```haskell
d1 $ s "sdgkick:1*4"                                   -- the 909 kick
d2 $ s "~ sdgsnare ~ sdgclap:1"
d3 $ s "sdghat*8" # n "0 0 2 0 0 1 2 0" # cut 1          -- closed, pedal and open hats
d4 $ s "sdgfmdrum*8" # n "0 2 1 4" # pF "sdgcolor" (range 0.1 0.9 sine)
```

| family | voices | after |
|--------|--------|-------|
| classic | `sdgkick`, `sdgsnare`, `sdgclap`, `sdgtom`, `sdghat`, `sdgrim`, `sdgcowbell` | the Roland TR-808, 909 and 606 |
| FM and Elektron | `sdgfmdrum`, `sdgcymbal`, `sdgmodal` | the Machinedrum, Digitone, Model:Cycles and Syntakt: FM drums and physical models |
| hyper-real | `sdgplastic`, `sdgslam` | SOPHIE's habits: latex, plastic, bubbles, a kick limited flat |
| small machines | `sdgping`, `sdgratchet`, `sdgchaos` | Autechre's habits: pinged filters, rolls in one event, chaotic noise |
| hybrid | `sdglayer` (a sample player) | any drum sample with a synthesized drum under it |
| rendered | the banks `sdgbd`, `sdgsd`, `sdghh`, `sdgperc`, `sdgfx`, `sdgresamp` | the voices played once each into files, stacked, reversed, crushed |

The Tidal files [16-drums-classic](../examples/tidal/16-drums-classic.tidal),
[17-drums-fm](../examples/tidal/17-drums-fm.tidal),
[18-drums-sophie](../examples/tidal/18-drums-sophie.tidal) and
[19-drums-autechre](../examples/tidal/19-drums-autechre.tidal) play each family,
[20-drums-hybrid](../examples/tidal/20-drums-hybrid.tidal) the banks and `sdglayer`, and
[library.md](library.md) lists every parameter of every voice with its default.

## How a drum voice differs from the other synths

The other synths in `synths/` are one SynthDef each: `s "sdgacid"` plays it, `n` is its pitch,
and the note lasts one step. A drum voice is a SynthDef *and* a list of events in SuperDirt's
sound library, one per preset, registered at the end of its file:

```supercollider
(
var name = \mykick;
[(sdgdecay: 0.8), (sdgdecay: 0.25)].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
```

That one call changes four things.

**`n` picks a preset.** `s "sdgkick:1"` and `s "sdgkick" # n 1` are the 909 kick. The list is in
the voice's header and in [the table below](#the-voices). Past the last preset `n` wraps round:
`sdgkick` has five, so `sdgkick:5` is `sdgkick:0` again.

**`note` tunes it.** Every voice has a pitch of its own (`sdgfreq`, at note 0), and `note` moves it
in semitones from there: `s "sdgtom:1" # note "0 -5 -7"` is one tom played as three. A preset carries
`note: 0` because SuperDirt would otherwise take `n` as the note too: without it, `n 1` picked the
second preset *and* played it a semitone up. A `note` in the pattern still wins over the preset's.

**The hit lasts as long as the voice rings, not one step.** A plain synth's event lasts one step
(`delta`), and SuperDirt frees the event at the end of it, tail or no tail: a 16th-note 808 kick at
cps 0.5 would be cut at 0.125 s. `useSynthDefSustain` makes the event last the SynthDef's `sustain`
default instead, so a voice declares there the longest it can ring (`sdgkick` 3.1 s, `sdgcymbal`
10.5 s) and frees itself as soon as it is quiet, well before that.

**`legato` and `sustain` choke, they never lengthen.** The `sustain` default is a ceiling: a pattern's
`legato` or `sustain` shortens the hit, like a hand on a cymbal, and anything longer is held to the
ceiling. `cut` works as for samples, so a closed hat in the same cut group stops a ringing open hat:

```haskell
d1 $ s "sdgcymbal:1*4" # legato "1 0.25"            -- the second crash grabbed
d2 $ s "sdghat*8" # n "0 0 2 0" # cut 1             -- the closed hat chokes the open one
```

Three of SuperDirt's sample words also move the ceiling, because SuperDirt treats the sustain as the
length of a sample: `speed 2` halves it (and does not tune the voice: use `note`), `accelerate`
shortens it as it raises the pitch (`accelerate 4` held `sdgkick` to 1.03 s of its 3.1), and `begin`
and `end` cut it in proportion.

Inside the voice, nothing scales with `sustain`: the envelope is the voice's own (`sdgdecay`), and
`accelerate` bends the pitch over that, `Line.kr(1, 1 + accelerate, decay)`.

## The parameter words

The same word means the same thing in every voice that has it. Ranges vary a little per voice; the
header of each file and [library.md](library.md) give the exact ones.

| word | range | what it does | in |
|------|-------|--------------|----|
| `sdgfreq` | Hz | the pitch at note 0. Never a preset's `freq`: see below | all but `sdghat`, `sdgcowbell` (tuned by `note` alone) and `sdgrim` (`sdgcut`) |
| `sdgdecay` | s | how long the body rings, to -60 dB | every voice |
| `sdgpunch` | semitones | how far above its pitch the hit starts, then drops | kick, snare, tom, fmdrum, modal, plastic, slam, ping (-24 to 48: below, it rises), chaos |
| `sdgbend` | 0.001 to 1 s | how long that drop takes | kick, tom, fmdrum, modal, plastic, slam, ping, chaos |
| `sdgclick` | 0 to 1 | the transient: beater, stick, tick | kick, tom, hat, cowbell, fmdrum, cymbal, plastic, slam, ping |
| `sdgtone` | 0 to 1 | dark to bright: a filter, the noise's band, the mallet's hardness | every voice |
| `sdgdrive` | 0 to 1 | saturation; in `sdgchaos` folding past 0.5 | kick, snare, rim, fmdrum, cymbal, plastic, slam, chaos |
| `sdgsnap` | 0 to 1 | noise against the tonal body, the 808's snappy | snare, tom, rim, fmdrum, modal (snare wires), slam, chaos |
| `sdgnoise` | s | how long the noise part lasts, where it differs from the body | snare, fmdrum, slam, chaos |
| `sdgmetal` | 0 to 1 | harmonic to inharmonic: toward bells, bars and plates | hat, cowbell, fmdrum, cymbal, modal (the material), slam, ping |
| `sdgratio`, `sdgindex` | x, 0 to n | FM: the modulator against the pitch, and how hard it modulates | fmdrum, cymbal, plastic, ping; `sdgindex` alone in chaos |
| `sdgfb` | 0 to 1 | the modulator's feedback, noise from about 0.9 | fmdrum |
| `sdgbits` | 0 to 1 | bit and sample-rate crush, 0 clean | hat, ratchet, chaos |
| `sdgwavefold` | 0 to 1 | folding, a shine on top | plastic, slam |
| `sdgcount` | 1 to 8, 1 to 32 | hits inside one event: a clap's hands, a roll's clicks | clap, fmdrum, ratchet |
| `sdgcurve` | -1 to 1 | a roll's timing: 0 even, above 0 faster like a dropped ball, below 0 slowing | ratchet |
| `sdgspread` | 0 to 1 | stereo width | clap, cymbal, ratchet |
| `sdgtail` | 0 to 1 | a room or a ring after the hit | clap, chaos |
| `sdgcut`, `sdgq` | Hz, 0 to 1 | a filter's frequency and narrowness | rim (`sdgcut`), clap and plastic (`sdgq`) |
| `sdgcolor` | 0 to 1 | the voice's own character knob, as COLOR on the Model:Cycles: below | every voice |

A few words belong to one voice: `sdgbloom` (the cymbal's swell), `sdgatk`, `sdgrise`,
`sdgwobble`, `sdgslip` (the plastic's attack, rise, wobble and stick-slip), `sdglen` and `sdgwalk`
(the roll's length and the pitch it walks).

`sdgcolor`, voice by voice:

| voice | `sdgcolor` 0 to 1 |
|-------|-------------------|
| `sdgkick` | the 808's sine to the 909's rounded triangle |
| `sdgsnare` | the 808 (shells nearly an octave apart, clean) to the 909 (a fifth apart, shaped, noise held a moment) |
| `sdgclap` | how loose the hands are: 4 to 24 ms apart, more uneven |
| `sdgtom` | the 808's sine to the 909's rounded triangle |
| `sdghat` | the six squares' spread: the 808's 205 to 800 Hz to the 606's 245 to 619 |
| `sdgrim` | where the low body sits under the ping, 0.1 to 0.8 of it |
| `sdgcowbell` | the upper tone, 1 + color times the lower: 0.476 the 808, 1 an octave |
| `sdgfmdrum` | how long the modulation lasts: 0 only the strike, past 0.5 it colours the tail |
| `sdgcymbal` | wash against bell: 0 a crash, 0.4 a ride, 1 a bell |
| `sdgmodal` | where it is hit: 0 the centre, round and dull, 1 near the edge, ringing |
| `sdgplastic` | how fast the wobble and the slips go, 6 to 160 Hz |
| `sdgslam` | the clang's pitch, a low gong to thin steel |
| `sdgping` | rattle: the resonators hit again and again while they ring |
| `sdgratchet` | each click's pitch scattered at random, up to an octave |
| `sdgchaos` | the source: 0 crackle, 0.33 logistic map, 0.67 stepped noise, 1 gendy |

## The voices

Every preset, by `n`, with the role it was levelled as and its loudness at gain 1 (see
[Levels](#levels)). Every voice puts out `~dirt.numChannels` channels through `DirtPan`; `sdgclap`,
`sdgcymbal` and `sdgratchet` make two sides of their own before it, for `sdgspread`.

| voice | presets by n: name (role, LUFS) |
|-------|---------------------------------|
| [`sdgkick`](library.md#sdgkick) | 0 808 long boom (kick, -24.0) · 1 909 (kick, -25.2) · 2 606 tight (kick, -25.7) · 3 hard techno (kick, -22.7) · 4 deep sub (kick, -24.8) |
| [`sdgsnare`](library.md#sdgsnare) | 0 808 (snare, -27.0) · 1 909 (snare, -24.8) · 2 tight (snare, -26.5) · 3 piccolo (snare, -25.8) · 4 fat (snare, -24.9) |
| [`sdgclap`](library.md#sdgclap) | 0 808 (clap, -27.8) · 1 909 (clap, -25.9) · 2 tight (clap, -29.6) · 3 big room (clap, -27.0) · 4 snap (perc, -30.7) |
| [`sdgtom`](library.md#sdgtom) | 0 low tom (tom, -26.8) · 1 mid tom (tom, -26.7) · 2 high tom (tom, -26.8) · 3 conga (tom, -27.0) · 4 bongo (tom, -27.3) · 5 808 deep (tom, -26.5) |
| [`sdghat`](library.md#sdghat) | 0 808 closed (closed hat, -34.3) · 1 pedal (closed hat, -36.6) · 2 808 open (open hat, -28.8) · 3 909 closed (closed hat, -32.8) · 4 909 open (open hat, -27.9) · 5 606 closed (closed hat, -36.5) |
| [`sdgrim`](library.md#sdgrim) | 0 808 rim (perc, -32.0) · 1 claves (perc, -30.5) · 2 woodblock (perc, -30.8) · 3 909 rim (perc, -29.5) · 4 stick (perc, -32.5) |
| [`sdgcowbell`](library.md#sdgcowbell) | 0 808 (metal, -29.5) · 1 808 short (metal, -29.9) · 2 agogo (metal, -28.4) · 3 bell (metal, -28.8) · 4 clank (metal, -30.7) |
| [`sdgfmdrum`](library.md#sdgfmdrum) | 0 FM kick (kick, -24.3) · 1 FM snare (snare, -27.3) · 2 FM tom (tom, -25.2) · 3 FM clap (clap, -27.4) · 4 FM metal (metal, -29.2) · 5 perc, a struck signpost (perc, -30.9) |
| [`sdgcymbal`](library.md#sdgcymbal) | 0 ride (cymbal, -28.1) · 1 crash (cymbal, -26.7) · 2 china (cymbal, -28.7) · 3 gong (cymbal, -27.9) · 4 bell (metal, -28.7) |
| [`sdgmodal`](library.md#sdgmodal) | 0 drum (tom, -28.0) · 1 marimba (perc, -30.9) · 2 glass (metal, -28.1) · 3 metal plate (metal, -28.7) · 4 tabla (tom, -26.8) · 5 wood (perc, -35.9) |
| [`sdgplastic`](library.md#sdgplastic) | 0 bubble (perc, -32.4) · 1 latex snap (perc, -29.3) · 2 squeak (fx, -25.6) · 3 plastic clank (perc, -30.3) · 4 drip (perc, -32.1) · 5 rubber (fx, -25.4) |
| [`sdgslam`](library.md#sdgslam) | 0 slam (kick, -24.5) · 1 dive (kick, -21.6) · 2 clank (kick, -25.2) · 3 gated (kick, -25.6) · 4 crunch (kick, -24.4) · 5 boom (kick, -25.1) |
| [`sdgping`](library.md#sdgping) | 0 blip (perc, -29.9) · 1 droplet (perc, -33.5) · 2 metal tick (perc, -31.4) · 3 wood (perc, -34.7) · 4 zap (fx, -24.6) · 5 rubber (perc, -32.4) |
| [`sdgratchet`](library.md#sdgratchet) | 0 bounce (perc, -29.9) · 1 ratchet (perc, -29.6) · 2 slowdown (fx, -27.0) · 3 zipper (fx, -26.0) · 4 crushed (perc, -28.4) |
| [`sdgchaos`](library.md#sdgchaos) | 0 glitch snare (snare, -27.5) · 1 digital hat (closed hat, -32.8) · 2 broken kick (kick, -23.0) · 3 crackle (perc, -34.7) · 4 gendy tom (tom, -25.4) · 5 static (fx, -24.4) |

`sdgslam` is louder than its peak suggests: it is driven, equalised and limited inside, so preset 0
reads -24.5 LUFS at a peak of only -21.3 dBFS, a few dB between peak and body like a mastered hit.

## Levels

A drum voice at gain 1 sounds as loud as the stock sample of its role: `sdgkick` in place of `bd`
is no jump, and a closed hat sits about 10 LU under the kick, as in a mix. Each role has a target,
the loudest 400 ms of the hit (momentary loudness, BS.1770) on the orbit at gain 1, preset as it is:

| role | target, LUFS | tolerance | drawn from, at gain 1 |
|------|--------------|-----------|-----------------------|
| kick | -24 | ±3 | `bd` -24.9 and -24.4, `909` -24.8, `super808` at a 16th -25.8 |
| snare | -26 | ±3 | `sn` -24.5, `supersnare` -24.9 |
| clap | -28 | ±3 | `cp` -27.2, the 808's clap -30.9 |
| closed hat | -34 | ±3 | the 808's closed hat (`808:1`) -36.7, `superhat` -35.2 |
| open hat | -28 | ±3 | `808oh` -27.8 |
| cymbal | -27 | ±3 | `808cy` -27.2 and -25.7 |
| tom | -27 | ±3 | the 808's toms -28.8 to -29.3, its conga -29.7 |
| perc (rim, claves, clicks) | -32 | ±4 | claves -31.4, maracas -31.0, rimshot -35.2 |
| metal (cowbell, bell) | -30 | ±3 | the 808's cowbell -31.9 |
| fx | -28 | ±5 | none: under the kick, wide on purpose |

±3 LU is about one notch of `gain`: SuperDirt's gain is steep (amp follows gain to the 4th power),
so `gain 1.2` measured +6.3 dB and `gain 0.8` -7.7 dB, about 3.5 dB per 0.1. Short sounds get more
room because a 400 ms window averages a 13 ms rimshot over 400 ms: the 808's reads -35.2 LUFS at a
-13.5 dBFS peak.

All 83 presets measured inside their role's tolerance. The three voices whose preset 0 is a kick
read -24.0 (`sdgkick`), -24.3 (`sdgfmdrum`) and -24.5 (`sdgslam`), level with stock `bd` at -24.9.
Every preset alone peaks at -8.4 dBFS or under on each side (the hottest, the 606 kick), against
-11.0 for every normalized stock sample: the loudness comes from the body and its length, not from
the peak. So the voices stack with room to spare. A bar of each family's own kit, everything on the
beats stacked, peaked at -4.0 to -6.1 dBFS (classic), -7.9 to -8.5 (FM), -7.9 to -8.9
(hyper-real) and -8.2 to -8.6 (small machines), against -4.3 for a like bar of stock samples (`bd`, `sn`,
`cp`, `hh`, `ho`).
Four of the new kicks on one beat (`sdgkick`, `sdgfmdrum`, `sdgslam`, `sdgchaos:2`) peaked at
-4.3 to -5.6 dBFS, four stock kicks (`bd`, `909`, `808bd`, `super808`) at -0.2. All four families
at once on one orbit, 96 events a bar, reached -1.2 dBFS, and all fifteen voices on one downbeat
+0.6: that is where SuperDirt's default limiter (`limitertype 1`, ceiling 1.0) earns its place.

### How they were measured

On 2026-11-03, by a lab that plays exactly what Tidal sends: a SuperDirt and a `SuperDirtGUI` on a
private server, one `/dirt/play` OSC event at a time, at cps 0.5, gain 1, orbit 0 and pan centre,
with the orbit's limiter off (`limitertype 0`) so peaks are true. The orbit's output bus is
recorded to a file, and the file is read in the language: sample peak, true peak (4x interpolated),
energy per band, length to -60 dB, and loudness with `SDGSampleAnalysis`, the sample editor's own
BS.1770 meter (K-weighting, 400 ms blocks, 100 ms hop, gates). The stock samples were measured the
same way, file by file, which is where the targets come from. The voices were tuned until preset
0 sat on its target and every preset inside the tolerance and under a peak ceiling of -7 dBFS
(-6 dBTP), and a kit bar under -3 dBFS.

Two things about the numbers. Voices made of noise move 1 to 2 LU from one run to the next, and a
kit bar's peak with a noise voice in it about 1 dB, so a borderline one was measured three times.
And the first hit of a run reads its peak 0.7 to 0.8 dB hot (a stock `bd`: -10.2 dBFS where every
later one reads -11.0), so a run starts with a hit that is thrown away.

## Your own drum voice

**new...** in the window's modules tab, with a drum voice selected in the synths list, copies it
under a name of yours: the presets come along. Or start from this, the smallest file that follows
every rule:

```supercollider
// synths/mykick.scd - a kick: a sine that drops onto its pitch, and a click.
//
// parameters:
//   sdgfreq    30 to 120 Hz       its pitch at note 0
//   sdgdecay   0.05 to 2 s        how long it rings (to -60 dB)
//   sdgpunch   0 to 36 semitones  how far above its pitch the hit starts
//   sdgclick   0 to 1             the beater
//
// presets, by n: 0 long, 1 short. note tunes it in semitones from its own pitch;
// legato or sustain shorten it, like a choke.
//
//   d1 $ s "mykick*4" # n "<0 1>"
(
var name = \mykick;
SynthDef(name, { |out, sustain = 2.1, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 50, sdgdecay = 0.8, sdgpunch = 12, sdgclick = 0.3|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.05, 2);
	var f0 = sdgfreq.clip(30, 120) * tune * Line.kr(1, 1 + accelerate, decay);
	var drop = EnvGen.ar(Env([1, 0], [0.04], -7));
	var pitch = f0 * (1 + ((sdgpunch.clip(0, 36).midiratio - 1) * drop));
	// the voice's own envelope frees it, long before the sustain ceiling
	var env = EnvGen.ar(Env([0, 1, 0.001, 0], [0.0005, decay, 0.005], [0, \exp, 0]), doneAction: 2);
	var click = HPF.ar(Trig1.ar(Impulse.ar(0), 0.0006), 600) * sdgclick.clip(0, 1) * 0.4;
	var sig = (SinOsc.ar(pitch, 0.5pi) * env) + click;
	Out.ar(out, DirtPan.ar(sig * 0.7, ~dirt.numChannels, pan));
}).add;

// presets: useSynthDefSustain makes an event last the def's sustain, not one step;
// note: 0 keeps n from also transposing
[
	(sdgfreq: 50, sdgdecay: 0.8),
	(sdgfreq: 58, sdgdecay: 0.25, sdgclick: 0.7)
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
```

The rules, each one there for a reason that was measured:

- **`sustain`'s default is the ceiling**: the longest the voice can ring, a little over its longest
  `sdgdecay`. The envelope does not scale with `sustain`, and frees the synth itself
  (`doneAction: 2`).
- **Every preset has `note: 0`**, or `n` transposes as well as picks.
- **No `freq` in a preset.** A preset's `freq` beats the pitch SuperDirt works out from `note`: a test
  voice with `freq: 50` in its preset stayed at 50 Hz under `note -5`. Keep the pitch in a control of
  the voice's own, `sdgfreq`, and multiply it by `tune = freq / 261.6256`, which is 1 at note 0.
- **No `amp` or `gain` in the SynthDef.** SuperDirt hands the synth `amp 0.4` at gain 1 and applies
  0.4 again in `dirt_gate`: a voice that used `amp` would be scaled twice.
- **`appendToExisting: i > 0`**: the first preset replaces what the name held, so saving the file
  again keeps the count of presets right instead of adding five more.
- **Every control is `clip`ped** to the range in its header, so a pattern can send anything.
- **Core UGens only**, so every student can play it.
- **Level it to its role.** A mono signal peaking at 1.0 inside the SynthDef reaches the orbit at
  -11.0 dBFS on each side at gain 1 (0.4 times `DirtPan`'s 0.71 at the centre); the scale at the end
  (`* 0.7` above) is the knob. Then A/B it at gain 1 against the stock bank of its role (`bd` for a
  kick, `sn` for a snare): the targets were drawn from them.

`attack`, `release`, `cut`, `shape`, `coarse`, `crush`, `cutoff`, `delay`, `room`, `size`, `speed`,
`begin`, `end`, `legato`, `n`, `note`, `gain`, `amp`, `pan` and `orbit` all mean something to
SuperDirt already: start the voice's own words with `sdg` or a prefix of yours.

What was learnt building them, for anyone writing one:

- **`Latch` only moves on a rising trigger**, so a sample-rate reducer's clock has to stay at or
  under half the sample rate. `Impulse.ar(SampleRate.ir)` is 1 on every sample, never rises, and
  never fires: a "clean" setting that held one value instead, silence.
- **A whole-number FM ratio puts a sideband on 0 Hz**: DC under the hit (0.018 at `sdgratio 1` in
  `sdgping`). It lasts as long as the ring, which is too short for `LeakDC` to do more than smear it;
  a high pass under the pitch takes it out.
- **`LeakDC` on a sub kick overshoots.** Its default coefficient is a high pass around 35 Hz: a
  driven 45 Hz near-square went from 0.8 to 1.32. `sdgslam` uses a 24 Hz high pass instead.
- **A struck `Ringz` peaks at about 2.0 at any pitch** (1.88 to 2.0 from 55 Hz to 4 kHz, decays of
  30 ms to 1 s), so halve it. A pinged `RLPF` does not: at rq 0.02 it peaked at 0.015 at 55 Hz and 1.0
  at 4 kHz, 36 dB apart.

## The rendered banks

`samples/` holds 88 short files in six banks, 5.7 MB, rendered from the voices. Load them like any
folder of banks:

```supercollider
~sdg.loadSamples(~sdg.path +/+ "samples");
```

```haskell
d1 $ s "sdgbd sdgsd:7 sdgbd sdgsd"
d2 $ chop 16 $ s "sdgresamp:12"
```

| bank | what | files |
|------|------|-------|
| `sdgbd` | kicks: the five `sdgkick` presets, longer and shorter ones, FM, slam, dive, boom, broken | 12 |
| `sdgsd` | snares and claps, the FM and glitch ones, a latex snap | 14 |
| `sdghh` | hats closed, pedal and open, digital hats, ride, crash, china, bells | 14 |
| `sdgperc` | toms, congas, bongos, rims, claves, woodblock, cowbell, agogo, marimba, glass, tabla | 16 |
| `sdgfx` | bubbles, squeaks, drips, rubber, slams, droplets, zaps, rolls, crackle | 16 |
| `sdgresamp` | what only rendering gives: three voices stacked into one hit, cymbals and a gong backwards, hits through long feedback, crushed and re-pitched copies, four one-bar loops at 120 bpm | 16 |

A file's name starts with its index, so `sdgbd:3` is `03_hard.wav`. As samples they cost next to
nothing to play, `chop` and `striate` cut them like a break, and the sample players read them.

Each one-shot is written as loud as its role, to the same targets as the voices, so `sdgbd` against
`sdgsd` sits like `bd` against `sn`. No file goes past full scale, so a kick that would need more
stays at full scale, where every normalized Dirt-Samples kick is: the short 909 and 606 kicks
read -26.4 and -28.2 LUFS there, under the kick target. The four loops are normalized to 1 dB under full
scale, as the breaks are. Played back through SuperDirt, file by file, 83 of the 84 one-shots landed
inside their role's tolerance; the 606 kick is the one.

A sample file plays at 0.4 times 0.71 on each side at gain 1, mono or stereo: a file peaking at 1.0
reaches the orbit at -11.0 dBFS per side either way (measured -10.9 to -11.0 for the stereo files).
What differs is loudness: a mono file is one channel heard on two sides, so it plays 8.0 LU under
the loudness of the file itself, a stereo file 11.0 under. `render.scd` levels with those two
numbers.

[samples/render.scd](../samples/render.scd) is how they were made, and how to make more. It is an
editor document, evaluated a block at a time: block 2 holds the tools, block 3 the list of banks
(change a line, add one), block 4 renders all of them in a minute or two, block 5 renders a bank of
your own into a folder of yours and loads it. Rendering is offline, with a server of its own
(`Score.recordNRT`): it does not touch your server or your orbits. The voices and their presets are
read from `~dirt`, so SuperDirt and SuperDirtGUI have to be running.

## sdglayer: a drum under a sample

[players/sdglayer.scd](../players/sdglayer.scd) is a sample player: it plays any sample as it is,
and starts a synthesized layer at its first hit, a sine that drops onto its pitch, for the weight a
thin sample lacks, and a burst of noise, for a tail it does not have.

```haskell
d1 $ s "bd*4" # pF "sdglayer" 0.6 # pF "sdgfreq" 48 # pF "sdgdecay" 0.6
d2 $ s "~ sn" # pF "sdglayer" 0.5 # pF "sdgsnap" 0.8 # pF "sdgfreq" 180
d3 $ s "hh*8" # pF "sdglayer" 0 # pF "sdgclick" "-1 1"     -- the attack alone
```

`sdglayer` is how much of the layer, against the sample's own peak so far, so it sits as well under
a quiet 808 file as under a normalized one. At 0 the sample is heard alone, level for level as
SuperDirt plays it (measured on `bd`, `808bd`, `sn`, `cp` and `hh`: the same peak and loudness to 0.1
dB). `sdgclick` shapes the sample's own attack, below 0 softer, above 0 sharper. The event lasts as
long as the layer rings, even past a short sample, unless `legato` chokes it. `note`, `speed` and
`accelerate` move the sample and the layer's pitch together, as for a sample played faster. Its
words are the drum voices' words: `sdgfreq`, `sdgpunch`, `sdgbend`, `sdgdecay`, `sdgsnap`,
`sdgnoise`, `sdgtone`, `sdgclick`.

## Where the ideas came from

- Kurt James Werner, Jonathan Abel, Julius Smith, [A Physically-Informed, Circuit-Bendable, Digital
  Model of the Roland TR-808 Bass Drum Circuit](https://dafx14.fau.de/papers/dafx14_kurt_james_werner_a_physically_informed,_ci.pdf),
  DAFx-14: the 808 kick's 49.5 Hz, its attack and its pitch sigh.
- The same authors, [The TR-808 Cymbal: a Physically-Informed, Circuit-Bendable, Digital
  Model](https://speech.di.uoa.gr/ICMC-SMC-2014/images/VOL_2/1453.pdf), ICMC/SMC 2014: the six
  oscillators, their duty cycle, the bands at 3440 and 7100 Hz, the swing VCA, in `sdghat`.
- [Roland TR-808 service notes](https://www.manualslib.com/manual/1294765/Roland-Tr-808.html?page=5):
  the snare, toms, clap, cowbell and hat circuits.
- Mutable Instruments Plaits, [the drum engines](https://github.com/pichenettes/eurorack/tree/master/plaits/dsp/drums):
  every constant of its 808- and 909-style kick, snare and hat, and its FM kick.
- Michael Fischer's TR-808 samples, the `808*` banks of Dirt-Samples: measured here for the real
  pitches, decays and clap timing of one 808.
- norgatronics, [808 Snare - Mutations](https://norgatronics.blogspot.com/2021/11/808-snare-mutations.html):
  the snare's two shells.
- Elektron's manuals: [Model:Cycles](https://elektron.se/wp-content/uploads/2024/09/Model-Cycles-User-Manual_ENG_OS1.13_210602.pdf)
  (COLOR, and the sequencer's habits in `17-drums-fm`), the
  [Machinedrum's machines](https://www.manualsdir.com/manuals/657170/elektron-machinedrum.html?page=97),
  [Digitone II's FM drum](https://www.knobwerk.com/en/digitone2/parameters) and
  [Syntakt's machines](https://www.knobwerk.com/en/syntakt/parameters).
- [FM, additive and Karplus-Strong drums](https://ccrma.stanford.edu/~sdill/220A-project/drums.html)
  at CCRMA: Chowning's wood drum at 1:1.4, chimes, sheet metal.
- Dan Stowell, [cymbal synthesis in SuperCollider](https://mcld.co.uk/cymbalsynthesis/).
- SOPHIE in her own words, [on Google Arts & Culture](https://artsandculture.google.com/story/2wWx4L63W07OIQ),
  and [Elektronauts on her sound design](https://www.elektronauts.com/t/sophies-sound-design-digitone-or-monomachine/60043?page=8).
- Moss and others, [Sounding Liquids](https://gamma.cs.unc.edu/SoundingLiquids/soundingliquids.pdf):
  a bubble's rising pitch, in `sdgplastic`.
- Autechre on their methods: [Confield](https://aepages.org/wiki/Confield) and
  [Sean Booth, interviewed by Peter Hollo in 2005](https://aepages.org/wiki/Sean_Booth_Interview_taken_by_Peter_Hollo,_March_2005).

None of the voices copies a track or a sample. What came from the hardware is numbers, from its
circuits and from recordings; what came from the artists is habits.
