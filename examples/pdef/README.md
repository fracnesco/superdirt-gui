# pdef

**SuperDirt and SuperDirtGUI sequenced from SuperCollider's own patterns, with no Tidal.**

```supercollider
(~sdg.path +/+ "examples/pdef/sdg-event.scd").load;
TempoClock.default.tempo = 124/60;
Pdef(\kick, Pbind(\type, \sdg, \s, \sdgkick, \n, 1, \dur, 1)).play;
Pdef(\hats, Pbind(\type, \sdg, \s, \sdghat, \n, Pseq([3, 3, 4, 3], inf), \dur, 0.25, \cut, 1)).play;
```

The sounds, orbits, effects and the mixer window are the same ones Tidal plays. Every Tidal word becomes a key of the event: `s "sn:3" # gain 0.9 # orbit 1` is `\s, \sn, \n, 3, \gain, 0.9, \orbit, 1`. Anything [docs/library.md](../../docs/library.md) lists for Tidal works here under the same name.

## Running them

You need SuperDirt with SuperDirtGUI loaded into it (`~sdg = SuperDirtGUI(~dirt)`) and Dirt-Samples. Start with [00-setup.scd](00-setup.scd). Every file is an editor document: evaluate it one block at a time, top to bottom. Do not `.load` it.

Load [sdg-event.scd](sdg-event.scd) once per session. It adds the `\sdg` event type, which every example uses. SuperDirt ships its own `\dirt` event type, but `\dirt` gets three things wrong when a Pbind drives it. `\sdg` puts them right, and that is the one thing it has to do:

- **time**: a Pbind counts in beats and SuperDirt counts in seconds. `\dirt` passes the beats on unchanged, so at 120 bpm every note lasted twice as long as written. `\sdg` divides by the clock's tempo, so `\legato` means what it means in Tidal.
- **cycles**: `\sdg` sets `\cps` to the tempo divided by `\beatsPerCycle` (4 by default: a cycle is a bar). Whatever counts in cycles then follows the clock: `unit "c"`, `sdgrolltime`, `sdghold`, a delay with `lock`.
- **level**: `\dirt` sends Event's default `amp` of 0.1, where an event from Tidal carries SuperDirt's 0.4. Played through `\dirt`, everything came out 12 dB quieter than from Tidal. `\sdg` sends 0.4. Use `\gain`, as in Tidal.

`\sdg` also turns `\degree`, `\scale`, `\root` and `\mtranspose` into a `\note`, the way any Pbind does. It plays `\timingOffset` (in beats) and `\lag` (in seconds) late, which is how swing is written.

## The files

| file | what |
|------|------|
| [00-setup.scd](00-setup.scd) | starting SuperDirt, loading the event type, the tempo, a first sound |
| [01-first-beat.scd](01-first-beat.scd) | Pbind and Pdef: names, rests, durations, `n`, pan, speed, legato |
| [02-synths.scd](02-synths.scd) | the synths: presets with `\n`, notes, scales, chords, a filter sweep, Ppar |
| [03-drum-machine.scd](03-drum-machine.scd) | the drum voices on sixteen-step grids: accents, ghosts, swing, choke, chance, fills, bars |
| [04-acid-bass.scd](04-acid-bass.scd) | sdgacid with Pbindef, accents and slides; sdgreese and sdgsub reading the roots from a Pdefn |
| [05-effects.scd](05-effects.scd) | modules on all events or on some, the sample players, orbits, global effects, the mixer from code |
| [06-live-changes.scd](06-live-changes.scd) | Pdefn, `set` + Pkey, effect layers with `<>`, Plazy, harmony that follows the clock, a canon |
| [07-polyrhythm.scd](07-polyrhythm.scd) | 3:4, 5:4, 7:4, polymeter, euclidean rhythms, rotations, phasing, tuplets |
| [08-generative.scd](08-generative.scd) | probability grids, Prand/Pxrand/Pwrand, Pbrown, Pseed, a Markov chain (Pfsm), degrading |
| [09-breaks.scd](09-breaks.scd) | breaks125 sliced: chop, reorder, shuffle, stutter, half time, the sdgonset player, amencutup |
| [10-arrangement.scd](10-arrangement.scd) | a piece in sections: Pfindur + Ppar, a Pseq of sections, Psym, Pspawner |
| [sdg-event.scd](sdg-event.scd) | the `\sdg` event type, `.load`able |

## Tidal and Pbind side by side

| Tidal | Pbind with `\type, \sdg` |
|-------|--------------------------|
| `d1 $ ...` | `Pdef(\name, Pbind(...)).play`. The name is yours; `\orbit, 0` is d1's strip |
| `d1 silence` / `hush` | `Pdef(\name).stop` / `Pdef.all.do(_.stop)` |
| `setcps (124/60/4)` | `TempoClock.default.tempo = 124/60` |
| `s "bd sn bd ~"` | `\s, Pseq([\bd, \sn, \bd, \r], inf), \dur, 1` |
| `s "hh*8"` | `\s, \hh, \dur, 0.5` |
| `s "bd [~ bd]"` | `\s, Pseq([\bd, \r, \bd], inf), \dur, Pseq([2, 1, 1], inf)` |
| `s "sn:3"` | `\s, \sn, \n, 3` |
| `s "sdgacid:1" # note "0 7"` | `\s, \sdgacid, \n, 1, \note, Pseq([0, 7], inf)` |
| `note "a2"` | `\octave, 2, \note, 9` |
| `note "c'maj"` | `\note, [0, 4, 7]` |
| `n (scale "minor" "0 2 4")` | `\scale, Scale.minor, \degree, Pseq([0, 2, 4], inf)` |
| `# gain 0.9 # pan 0.2` | `\gain, 0.9, \pan, 0.2`. Pan is 0 to 1, as in Tidal |
| `# legato 1` / `# sustain 0.2` | the same: legato is a part of the step, sustain is seconds |
| `# pF "sdgcut" 900` | `\sdgcut, 900` |
| `# sdgcut (range 200 2000 sine)` | `\sdgcut, Pseg([200, 2000, 200], [8, 8], \sine, inf)` |
| `# speed (rand + 0.5)` | `\speed, Pwhite(0.5, 1.5)` |
| `s "bd(3,8)"` | `\gain, ~euclid.(3, 8)`, with `\dur, 0.5` (07-polyrhythm) |
| `degradeBy 0.3` | `\gain, Pwrand([1, Rest(1)], [0.7, 0.3], inf)` |
| `sometimesBy 0.25 (# sdgroll 4)` | `.collect { \|e\| if(0.25.coin) { e[\sdgroll] = 4 }; e }` |
| `every 4 (fast 2)` | a Pseq of bars: `Pseq([Pn(main, 3), fill], inf)` (03-drum-machine) |
| `chop 8 $ s "breaks125"` + `loopAt 1` | `\begin, Pseq((0..7)/8, inf), \end, Pkey(\begin) + (1/8), \unit, \c, \speed, 1, \dur, 0.5` |
| `nudge` / swing | `\timingOffset, Pseq([0, 0.04], inf)` (beats) |
| `# cut 1` | `\cut, 1` |
| `# orbit 1` | `\orbit, 1` |

## Things that differ from Tidal

- **Every key counts on its own.** In Tidal the structure comes from the left and the values on the right are sampled at its events. In a Pbind each list goes round at its own length. `\s` with four values beside `\n` with five gives polymeter (07-polyrhythm, block 3). To keep them together, give them the same length.
- **nil ends a pattern.** A key whose value is nil stops the Pbind. Since a module or player switches on just by its word being in the event, you cannot switch one off for some events by giving it nil, or by giving it 0 (`sdgcomb 0` is still a comb). Add the word only to the events that want it with `.collect` (05-effects, block 3).
- **A rest is still an event.** `Rest(1)` or `\r` in any key silences that step, but every other key still takes its next value. A list of notes beside a list of gains with rests in it counts the rests too.
- **Orbit effects need setting on the orbit.** An event that lacks `sdgspace`, `room` or `delay` sends 0 for it, so two Pdefs on one orbit turn each other's echo off. `~sdg.setOrbitParam(0, \sdgspace, 0.4)` puts the value on the orbit, and every event carries it (05-effects, block 7).
- **Times in seconds stay in seconds.** `\sustain`, `sdgtime`, `sdgechotime` and `sdgdecay` do not follow the tempo. Write a synced time as `0.75 / TempoClock.default.tempo` (a dotted eighth).
- **Pdef's `fadeTime` scales `\amp`**, which it reads from Event's default of 0.1 rather than SuperDirt's 0.4. Leave it unset with these examples.
