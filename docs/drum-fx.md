# Drum effects

Eight modules in `modules/` made for drums: effects on one event, like SuperDirt's own `lpf` or
`crush`. Any event that carries one of these words gets the effect, on a sample or on a synth, and
events without it are left alone. Each hit gets its own effect, so every step of a pattern can have
a different one.

```haskell
d1 $ s "bd*4" # pF "sdgboom" 0.4 # pF "sdgboomfreq" 41      -- a tuned sub under the kick
d2 $ s "~ sn" # pF "sdggated" 0.6                           -- an 80s snare
d3 $ s "hh*8" # pF "sdgchrome" 0.5 # pF "sdgchromefreq" 600  -- metal hats
d4 $ s "~ ~ ~ cp" # pF "sdgroll" 6 # pF "sdgrollcurve" 0.8    -- a roll that speeds up
```

| module | what it does | where the idea comes from |
|--------|---------------|---------------------------|
| `sdgtrans` | more or less attack (`sdgtrans`), more or less body after it (`sdgtransbody`) | SPL's Transient Designer |
| `sdgboom` | a sine at `sdgboomfreq` under every hit it hears, as loud as the hit | Ableton Drum Buss's Boom, the 808's tuned sub |
| `sdgbody` | the hit strikes a resonating object: a drum skin, a bar, a tube or a bell | Ableton Corpus, the Machinedrum's PI machines |
| `sdglatex` | a resonant low pass that dives from high to `sdglatexfreq` on every hit | SOPHIE's latex sounds |
| `sdgchrome` | four short combs, not in harmonic ratios, wobbling and driven: metal | SOPHIE's comb-and-squash metal |
| `sdgroll` | the start of the hit played again, each gap a factor of the last | Autechre's self-retriggered delays |
| `sdggated` | a dense bright room, cut off dead `sdggatedtime` after each hit | the 80s gated snare |
| `sdgdive` | a delay that grows as it plays, so the hit drops in pitch: a tape stop | the record stopped by hand |

[38-drum-fx.tidal](../examples/tidal/lessons/38-drum-fx.tidal) plays every one, and
[library.md](library.md) lists every parameter with its range and default. In
[boot.hs](../examples/tidal/boot.hs) the words are declared, so `sdgboom 0.4` works without
`pF`.

## How each one works

All of them read the event's own bus (the sound and any module before them) at the orbit's channel
count, two in stock SuperDirt, and replace it. Where one needs to know when a hit happens, it uses
two envelope followers: a hit is the fast one jumping past twice the slow one, at least 30 or 60 ms
apart. One event can hold many hits (a break), and each one is found.

**`sdgtrans`.** Two followers that fall alike but rise at different speeds (0.2 ms and 20 ms)
differ only during an attack; two that rise alike but fall at different speeds (30 ms and 400 ms)
differ only during the body. Each difference, divided by the louder follower, runs from 0 to 1
whatever the level, and sets a gain: `sdgtrans` 1 is +15 dB on the attack, `sdgtransbody` 1 is
+18 dB on the body, -1 the same down. SPL's Transient Designer works this way and calls it
differential envelopes. On a sample the body is what was recorded: a dry kick has little room to
bring up.

**`sdgboom`.** Each hit starts a sine at 1.4 times `sdgboomfreq`, falling onto the note in 20 ms,
dying to -60 dB in `sdgboomdecay`. Its level is the hit's level 3 ms in. On a hat it adds a kick
that was never there; on a kick already at that pitch it can cancel part of it (the stock 808 kick
at 50 Hz came out 1.5 dB lower in peak with a 50 Hz boom), so tune it a little away, or under.

**`sdgbody`.** A bank of resonators (`DynKlank`) on the hit, at the mode ratios of a real object:
a circular membrane (1, 1.59, 2.14, 2.30, 2.65, 2.92: Bessel zeros), a free bar (1, 2.76, 5.40,
8.93), a tube (whole numbers) or a church bell (hum 0.5, prime, tierce 1.18, quint 1.51,
nominal 2 ...). `sdgbodytype` picks one; each is its own SynthDef, so only one bank runs.
Higher modes ring for less time. How much rings depends on how much of the hit is near the modes:
a snare with its shell at 172 Hz rings a body at 180 Hz 9 LU louder than it was, a hat rings it
5 LU quieter. Move `sdgbodyfreq` into where the hit has its energy, or lower `sdgbody`.

**`sdglatex`.** The cutoff of an `RLPF` (rq 0.08) jumps five octaves above `sdglatexfreq` on every
hit and falls back in `sdglatextime`, on a curve in octaves, so the fall is heard as a dive. The
filtered sound is driven into `tanh`. Under 50 ms it is a rubbery click; above 100 ms, a zap.

**`sdgchrome`.** Four `CombC` at `sdgchromefreq` times 1, 1.41, 1.73 and 2.24 (square roots of
primes, so no two make a harmonic chord) ring for 50 ms at `sdgchromefb` 0 up to 3 s at 1. Each
delay wobbles by 0.2 ms, as a chorus would, and the sum is driven into `tanh`. Its level is divided
by the square root of pitch times ring time, which is how a comb's level grows on noise.

**`sdgroll`.** One delay line, no buffer. Repeat k starts at time t<sub>k</sub> and reads the input
at `rate x (now - t`<sub>k</sub>`)`, so it plays the hit from its start, `sdgrollpitch` semitones
moved per repeat. The first gap is `sdgrolltime` cycles (at the event's `cps`), every next one
`sdgrollcurve` times the last; each repeat is 0.85 of the one before; each plays the first
quarter second. A roll ends within 4 s.

**`sdggated`.** A `FreeVerb` (room 1, damping 0.15) on the hit above 150 Hz, and a gate opened by
each hit that shuts `sdggatedtime` later in 10 ms. The room's own tail is far longer than any gate,
so what is heard is a flat block of reverb that stops dead.

**`sdgdive`.** The delay time grows by `1 - ratio` every second, where ratio falls from 1 to
`sdgdive` semitones down in `sdgdivetime`: read slower and slower, the hit drops in pitch and is
stretched. The delay is at most half a second; the effect fades out just before it fills. A delay
can only make things later, so a dive only goes down.

## Tails, and samples that would start again

`sdgboom`, `sdgbody`, `sdgchrome`, `sdgroll`, `sdggated` and `sdgdive` ring past the hit. A module
runs inside the event's group, and SuperDirt frees that group when the event's `sustain` is over, so
each of them makes the event last as long as its tail: it sets `~sustain` before SuperDirt starts
the group's gate, which reads it last. `legato` and `sustain` from a pattern are then only a lower
limit.

That alone would make a sample play twice: SuperDirt's sample synth reads its file in a loop and
counts on the gate to stop it at the file's end. Each of these modules therefore closes its own
input 4 ms before the length the event had when it arrived, and only its tail goes on. Measured:
with the amount at 0 and the longest tail (`sdgbody 0 # sdgbodydecay 4`, 4 s), a 151 ms snare
sample comes out the same length and level as without the module.

Two of them on one event chain in the order they are listed in `~dirt.modules` (the order their
files were read). Each closes its input at the length it found, which includes the tail of the one
before, so a chain keeps every tail.

## Levels

Every module ends in a soft ceiling: up to 1 inside the event, which is a normalised sample, it
changes nothing, and above that it bends towards 2, never past it. The event's sound is already
panned there, so at gain 1 that is -1.9 dBFS per side on the orbit at most, from any setting.

Measured through SuperDirt at gain 1 (`limitertype` 0), on four drum voices (`sdgkick`,
`sdgsnare`, `sdghat`, `sdgclap`) and four stock samples (`bd`, `sn`, `hh`, `breaks125`), each
setting in the first column with the others at their defaults: the change in loudest-moment
loudness (LUFS-M) against the same hit dry.

| setting | lowest | middle | highest | loudest peak |
|---------|--------|--------|---------|--------------|
| `sdgtrans 1` | +4.7 LU | +9.2 | +12.3 | -3.0 dBFS |
| `sdgtrans -1` | -12.2 | -8.5 | -3.2 | |
| `sdgtransbody 1` | +4.3 | +6.4 | +8.2 | -7.7 |
| `sdgboom 0.7` | +0.5 | +3.4 | +4.4 | -5.8 |
| `sdgbody 0.6` (membrane, 180 Hz) | -4.1 | +4.4 | +9.5 | -5.6 |
| `sdgbody 0.6` (bar, 440 Hz) | -4.1 | -0.7 | +7.5 | -7.2 |
| `sdglatex 0.7` | -5.3 | +4.8 | +9.3 | -9.7 |
| `sdgchrome 0.6` | -2.3 | +1.6 | +3.0 | -10.9 |
| `sdgroll 6` | +1.8 | +4.4 | +4.6 | -10.2 |
| `sdggated 0.6` | +0.6 | +5.1 | +6.5 | -9.4 |
| `sdgdive 24` | -5.8 | +1.2 | +3.4 | -10.0 |

The dry hits peak at -9.8 to -13.3 dBFS (the hat sample at -24.9).
`sdgtrans 1` is a lot: +15 dB on every attack. 0.2 to 0.5 is where a kit sits.

Cost, sixteen at once on one server (an M-series Mac, 44.1 kHz): `sdgtrans`, `sdgboom`, `sdgroll`
and `sdgdive` 5 to 6 %, `sdglatex` 8 %, `sdggated` 11 %, `sdgbody` 13 to 15 %, `sdgchrome` 18 %. An
effect costs while its event lasts, and the ones with a tail make events last longer.

## Writing one like these

A module is a function and a SynthDef ([writing-content.md](writing-content.md#a-module)). Four
things these eight do that the template `sdgcomb` does not need:

- **A tail longer than the event**: remember `~sustain` before changing it, make it longer, and pass
  the old length to the SynthDef to close the input there (`sdgdrylen` in these files).
- **No `XFade2` to cross-fade dry and wet.** At its ends it leaves 1e-17 of the other side, and a
  decaying drum times 1e-17 becomes denormal numbers, which cost CPU and which SuperDirt replaces
  without a word. These use `dry * sqrt(1 - a) + wet * sqrt(a)`, exactly 0 at both ends.
- **A delay line, not a `LocalBuf`, to replay the input.** A two-channel `LocalBuf` written with
  `RecordBuf` and read with `PlayBuf` in a module played buffer 0 of the server - the first sample
  loaded - with `Buffer UGen channel mismatch` in the post window, while the same SynthDef on a bare
  server worked. Not explained; `sdgroll` uses `DelayC` instead.
- **Every control clipped**, and nothing nil in the argument list (`~sdgboomfreq ? 50`): a nil is
  sent as 0.

## How they were checked

Nobody listened during the build; it was measured instead (2026-11-05). The drum lab of the
`sdg-sound-kit` skill played each setting as a `/dirt/play` message into a SuperDirt with
SuperDirtGUI on a private server and read the orbit's output: loudness, peak, length, spectrum, and
a spectrogram that was looked at. Beside the table above: every module at its smallest, its largest,
1000 times past its range and -1000 times (no NaN, no denormals, peaks under the ceiling); the amount
at 0 against the dry hit; all eight on one event; 16 hats a bar with all eight; a kit with a
different module on each part. Then [38-drum-fx.tidal](../examples/tidal/lessons/38-drum-fx.tidal) ran
block by block in Tidal 1.9.5 against a check SuperDirt, every event's words checked against the
modules' headers.

## Sources

- SPL, Transient Designer: two envelopes per control, their difference driving the gain, level
  independent (UAD's manual and Sound On Sound's 2000 review).
- Ableton Drum Buss: Boom is a resonator at 30 to 90 Hz with its own decay, under the drums.
- Ableton Corpus, used on drums: a tuned sub under a kick, a rattle on a snare (MusicRadar,
  *5 futuristic drum tricks*).
- Mode ratios: membrane (Bessel zeros), free bar, church bell partials, as in
  [drums.md](drums.md)'s sources.
- SOPHIE's metal and latex, from the Elektronauts threads on her sound design: FM through short
  combs with feedback, squashed; a resonant low pass following the hit's pitch envelope.
- Autechre, in their own words (aepages.org): a drum machine into a delay that retriggers itself,
  rolls that speed up.
- Gated reverb, and delays on single drums as a rhythm of their own (MusicRadar, *How to use delay
  processing creatively on your drums*; Sound On Sound on gates).
