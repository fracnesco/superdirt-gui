# The mixer

What an orbit's sound goes through on its way out, in order, and how each part is
made. Everything below is at SuperDirt's channel count, 2 unless you started it
otherwise: every bus and every synth of the mixer has that width, and there are no
mono inputs anywhere in it.

## An orbit's chain

Each event gets a group of its own inside the orbit's group. The sound and the
modules write to the orbit's `synthBus`, then SuperDirt's `dirt_gate` copies that to
the orbit's **dry bus**. After the orbit's group, as siblings of it on the server,
come the chain's synths:

```
 orbit's group        the events: sound or player, modules, dirt_gate
                                      │  dry bus
 ─────────────────────────────────────▼───────────────────────────────
 track 1              sdgtrack_send      copies dry to the track's inBus
                      pre inserts        process inBus in place
                      the effect         reads inBus, writes outBus
                      post inserts       process outBus in place
                      sdgtrack_return    adds outBus × fader to the effect bus
 track 2 ...
 orbit inserts        sum dry + effect, process, write dry, clear effect
 sends                sdgreturn_send     dry + effect × level, to each return
 global_sdgfader      dry and effect scaled in place, tap bus written
 dirt_rms             the meters read here
 dirt_monitor         dry + effect, limiter, out to the orbit's output bus
```

`prChainOrbit` writes this list into `orbit.globalEffects`, and `orderInserts` walks
every node into that order. They move synths rather than restart them, so adding or
moving something interrupts nothing. An effect that reached the list from code and has no
track yet goes first, until the window's `↻` or `applyGlobalEffects` makes it a track.

## Tracks

Every global effect of an orbit runs on a track (`SDGTrack`): inserts on what goes
into it, inserts on what comes out, and a fader on what it adds.

```supercollider
t = ~sdg.tracksOf(0).first;                  // orbit 0's first global effect
t.level = 0.5;                               // -6 dB of it; the dry signal is not touched
~sdg.setTrackEffect(t, \dirt_reverb);        // another effect on the same track
~sdg.addTrackInsert(t, \pre, \eq);           // an eq on what goes into it
~sdg.addTrackInsert(t, \post, \compressor);  // a compressor on what comes out
~sdg.moveTrack(t, 1);
~sdg.addTrack(0, \global_sdgecho);           // a new track
~sdg.removeTrack(t);
```

An effect file knows nothing of this. It reads `dryBus` and writes `effectBus` as ever,
but those two values are in the effect's `state`, which `GlobalDirtEffect:play` sends
after the orbit's, so the later value wins and the effect is on the track's busses. With
the fader at 0 dB and no insert, the sum is what it was without a track.

An effect that also writes its dry bus is *inline*: stock `dirt_leslie` takes its dry
share out that way. `SDGTrack.writesDry` finds it in the SynthDesc's outputs, and that
track's return copies the track's bus over the orbit's dry bus. It follows that the
pre inserts of an inline track are in the orbit's dry signal.

- An orbit holds **one effect of a name**: names are how `selectedEffectName`, `reload`
  and `prPlaceGlobalEffects` tell them apart, so `addTrack` refuses a second.
- `setTrackEffect` releases the old effect with its tail and starts the new one between
  the same inserts, at the same fader.
- A track's fader goes from 0 to 4 in code, as an orbit's does. The window's sliders
  stop at 1.5, about +3.5 dB.
- A track's return and send wake on every event and sleep with the orbit. A knob moved in
  the window also calls `wakeOrbit`, or an effect started without an event would run into a
  return that is paused.

## Inserts

```supercollider
i = ~sdg.addInsert(0, \eq);             // on orbit 0, after its other inserts
c = ~sdg.addInsert(0, \compressor, 0);  // in front of the eq
~sdg.moveInsert(c, 1);                  // behind it again
i.gui;                                  // the eq's own window
i.unit.preset = \warm;                  // the ParaEQ itself
i.enabled = false;                      // bypassed
~sdg.removeInsert(i);
v = ~sdg.addInsert(0, \vst, plugin: "ValhallaSupermassive");
```

| kind | extension | what its synth does |
|------|-----------|---------------------|
| `\eq` | ParaEQ | dry + effect, through `ParaEQ.ar`, onto the dry bus; effect bus cleared |
| `\compressor` | Compressor | the same through `Compressor.ar`, with a sidechain from `scBus` |
| `\insight` | Insight | only reads: copies dry + effect to a bus of its own that an Insight watches; nothing to bypass |
| `\vst` | VSTPlugin | the same as an eq through `VSTPlugin.ar`, on a synth of its own |
| any `FxParamUnit` subclass | sc fx kit (Growl and the rest) | as an eq, but it stays awake while its output sounds, so a tail outlives `DirtPause`'s grace |

An insert is a global effect of SuperDirt's own kind, named `sdgins_<kind>`, so SuperDirt
keeps it through every rebuild and cmd-period. On an orbit it sums the two busses into the
dry bus, the sum `dirt_monitor` would have made anyway, so nothing after it notices.
Its gate crossfades, so taking it out fades the busses back to what they were.

The DSP is the extension's own pseudo-UGen, and what drives it is the extension's own
object, so the window, the presets and the clipboard are that extension's. It drives
`SDGInsertTarget`, which answers `set` as a node does and puts each value in `fx.state` too,
so a synth started again begins with the settings the last one had.

On a track, an insert has the track's bus as dry and `sdg.silentBus`, which nothing writes
to, as effect.

### VST plugins

A plugin lives in the synth that hosts it, and freeing that synth closes the plugin with
everything set in it. SuperDirt replaces an orbit's effect synths on every rebuild, so a
VST insert has a long-lived synth outside `orbit.globalEffects`:

- `orderInserts` puts its node back in place after each rebuild. `rebuildOrbit`,
  `setOutBus` and `startMeters` all call it, so any new code that rebuilds an orbit must too.
- It has no `DirtPause`: a plugin can make sound from silence, and nothing would wake it. A
  plugin runs all the time, silent or not.
- A cmd-period does free it. A `ServerTree` hook, registered after SuperDirt's, makes the
  synth again and reopens the plugin with `stateData`. That state is read back 1.5 s after
  the last `parameterAutomated`, and the insert's right-click menu asks for it on demand.
- Bypass is VSTPlugin's soft bypass, a crossfade that lets a tail ring out.

`parameterAutomated` is not a complete picture: a preset picked in a plugin's editor need
not send it. That is why saving a session asks every plugin afresh.

## Return tracks

```supercollider
a = ~sdg.addReturn;                       // return A: a send knob on every orbit
~sdg.setReturnEffect(a, \dirt_reverb);    // or none, and only inserts
~sdg.setSend(0, a, 0.5);                  // orbit 0 into it, 0 to 1
a.level = 0.7;                            // what comes back
a.preFader = true;                        // sends into it ignore the source's fader
~sdg.addTrackInsert(a, \post, \eq);       // inserts work as on a track
~sdg.removeReturn(a);
```

An orbit's send is a global effect in the orbit's chain, right before the fader, so it
hears the effects and inserts and comes back with the orbit from every rebuild. The return
itself belongs to no orbit. It runs in a group after SuperDirt's, so everything the
orbits sent in this block is there to read:

```
 SuperDirt's group       ... inserts, sends, fader, rms, monitor
 the returns' group
   heads                 each return: its sends in + last block's feedback, onto lineBus
   return A              pre inserts, effect, mix, post inserts, links, out
   return B              ...
```

| bus | holds |
|-----|-------|
| `inBus` | what the orbits send in this block |
| `fbBus` | what returns sent it in the last block |
| `lineBus` | the return's signal, processed in place all along |
| `wetBus` | what its effect writes; `sdgreturn_mix` puts it on the line |
| `tapBus` | its output after the fader, for the recorder |

The effect comes out fully wet. With no effect, or with it off, the line passes as it came.
An inline effect such as leslie gets the line as well as its wet.

No event reaches a return, so two things differ from an orbit's effect:

- **Parameters** live in the return's own `defaultParentEvent`, set by the window. They start
  at values that are heard (`SDGReturn.wetDefaults`), because most effects are silent at their
  SynthDef's defaults: `dirt_delay` and `dirt_reverb` have their own values, and a content
  effect's switch is set to 1.
- **Sleep:** an effect's or insert's `DirtPause` would pause it after 4 s of silence with
  nothing to wake it, so a routine resumes them once a second. A return is always on, uses CPU
  when nothing plays, and never cuts a tail.

### Feedback

**feedback** in the window, or `~sdg.returnFeedback = true`, is the switch for sends between
returns. The knobs keep their levels while it is off, so a loop can be set up first.

```supercollider
a = ~sdg.addReturn;
b = ~sdg.addReturn;
~sdg.setReturnSend(a, b, 0.4);   // A into B
~sdg.setReturnSend(b, a, 0.4);   // and back
~sdg.setReturnSend(a, a, 0.3);   // A into itself
~sdg.returnFeedback = true;
```

Each link writes to the target's `fbBus`, which the target's head reads with `InFeedback`
before any return of this block has written. So every link, forward or back, arrives exactly
one block later (64 samples, 1.3 ms at 48 kHz), whatever the order of the returns and
whatever the shape of the loop.

It has to be a bus of its own. `Out.ar` overwrites a bus last touched in an earlier block, so
on a bus shared with the orbits' sends, the first send of the block would wipe what the last
block fed back.

The feedback input saturates softly from +6 dB (`(fb * 0.5).softclip * 2`), takes out DC, and
turns bad values into silence, so a loop above unity reaches a bounded level instead of
running away. It can still get loud: keep the limiter on.

## Levels

Everything that decides a level goes through `applyAmps`, which touches only the orbits whose
level changed, and wakes them. A fader drag sends a dozen updates a second.

| what | its level comes to |
|------|-------------------|
| an orbit's fader | 0 if muted; 0 if anything is soloed and this orbit is not; otherwise its fader |
| an orbit's send to a return | 0 if the knob is at 0, the orbit is muted, or another orbit is soloed and this one is not; otherwise knob × fader, or knob alone if the return is pre fader |
| a return's output | 0 if muted, or another return is soloed and this one is not; otherwise its fader |
| a return's send to another | 0 if feedback is off or the source is muted; otherwise knob × source's fader, or knob alone if the target is pre fader |

So a muted orbit sends nothing, pre fader or not. A soloed orbit keeps its returns. A soloed
return silences every orbit's direct sound but goes on hearing what they send it. These are
Ableton's rules.

The fader's value goes into the orbit's default event first and then through the effect's
change tracking, so the value the synth holds and the value every later event carries agree,
and nothing flips back. Parameters set with a knob work the same way.

The window's **limit** menu is the monitor's `limitertype`: 0 raw, 1 `Limiter.ar`, 2
`softclip(sig * 0.5) * 2`.

## Meters

`dirt_rms` sits after the fader, so a muted orbit shows nothing. `~sdg.startMeters` asks for 20
replies a second and a 1.5 s peak lag; SuperDirt's own `startSendRMS` cannot, for the reason in
[superdirt-notes.md](superdirt-notes.md). Track and return meters are `SendReply`s to
`/sdgtrack`, sent only while `meterOn` is set: a window shows one orbit's tracks.

## Recording

The fader also writes dry + effect, at its level, to the orbit's tap bus (`tapBusOf`). The bus
index lives in `fx.state`, so no event touches it. **recorder** fills BusRecorder through its
public `addBus` with these taps, one source per orbit and one per return:

```supercollider
~sdg.recorder;       // its window, on the preset unless it holds a list of yours
~sdg.recordOrbits;   // the preset again: rec_001_orbit0.wav, rec_001_orbit1.wav ...
```

The files hold what the meters show: after effects, inserts and fader, so a muted orbit
records silence. The monitor's limiter is not in them, which is why the preset writes 32-bit
float. A sleeping fader writes nothing to the tap, which reads as silence, so a take stays in
time.

## Bus names

With BusManager installed the window registers `orbit0_dry` and `orbit0_fx` for each orbit
(`SuperDirtGUI.registerBusses`). Orbits are numbered from 0 everywhere a person reads them,
the number `# orbit` takes: `d1` plays on orbit 0.
