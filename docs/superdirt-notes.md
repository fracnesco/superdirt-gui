# What is actually going on underneath

What SuperDirt, scsynth, Qt and the plugins do, as far as this extension had to find out. Each entry
says how it is known:

- **Measured**: read back from a server or a file, on SuperCollider 3.13 and macOS, on the date given where one
  was written down. Not measured again when this page was written.
- **Source**: read in the class library or in SuperDirt, in the file named.
- **Found in use**: hit while building this, with no number kept.

None of this is a complaint. It is what you need when something does not do what you expected.

## Events

**A sample's event cannot outlast the sample through `sustain` or `legato`.** `calcTimeSpan` ends with
`min(unitDuration, sustain)`: `sustain 4` on a 0.5 s file stays 0.499. The modules run before `sendGateSynth`
reads `~sustain`, so a player's `prepare` can set it. *Source: `DirtEvent.sc`. Measured 2026-11-01.*

**A sample made to last longer starts again.** SuperDirt's sample synth reads its file in a loop and counts on
the event's gate to stop it at the file's end. A module that lengthens `~sustain` for a tail of its own (it runs
before `sendGateSynth` reads it) hears the sample begin again at the old length: a 151 ms snare under a 3 s tail
came back at 151 ms. The drum modules close their own input 4 ms before the length they found
([drum-fx.md](drum-fx.md)). *Measured 2026-11-05 in the drum lab.*

**`XFade2` never quite reaches its ends.** At pan 1 it leaves about 1e-17 of the dry side, and a drum decaying
under that becomes denormal numbers on the bus, which SuperDirt's output replaces without a word. A mix of
`dry * sqrt(1 - a) + wet * sqrt(a)` is exactly 0 at both ends. *Measured 2026-11-05: `CheckBadValues` reported
code 3 (denormal) 1.5 s into a kick through `sdglatex 1`, and nothing once the mix was changed.*

**A two-channel `LocalBuf` read back in a module played buffer 0.** `RecordBuf` into a `LocalBuf(24000, 2)`,
`PlayBuf` from it: inside SuperDirt every event played the server's first loaded sample, with `Buffer UGen channel
mismatch: expected 2, yet buffer has 1 channels`, while the same SynthDef on a bare server played what it recorded.
`BufChannels` of the same buffer answered 2. Cause not found; `sdgroll` replays through a `DelayC` instead.
*Measured 2026-11-05.*

**A voice's release cannot outlast `sustain`.** `dirt_gate` frees the event's group at the end of sustain, tail
or no tail. So a pad's attack and release have to fit inside it. *Source: `core-synths.scd`, `doneAction: 14`.*

**A synth's event lasts one step, unless the synth has presets that say otherwise.** `calcTimeSpan` takes `delta` when the
event has no `unitDuration`, and `dirt_gate` frees the event's group at the end of it, so a 16th-note 808 kick at cps 0.5 is cut
at 0.125 s. Registered with `soundLibrary.addSynth(name, event, useSynthDefSustain: true)`, the event gets a `unitDuration` of the
SynthDef's `sustain` default, and lasts that whatever the step. It is a ceiling, because `calcTimeSpan` ends with
`min(unitDuration, sustain)`: `legato` or `sustain` from a pattern only shorten it (2 is held to 0.8 on a def whose default is 0.8),
which makes a choke. `speed` scales it as for a sample (`speed 2` halves it, 0.5 doubles it), and `accelerate`, `begin`, `end` and
`unit "c"` change it too. `cut` chokes as for samples. The drum voices are built on this: [drums.md](drums.md). *Source:
`DirtSoundLibrary.sc`, `DirtEvent.sc`. Measured 2026-11-03.*

**With presets, `n` picks one and transposes too**, by n semitones, unless the preset carries `note: 0`: `n 1` on a preset without
it played 277.18 Hz, `61.midicps`. A pattern's `note` still wins over the preset's. Past the last preset `n` wraps round. *Measured
2026-11-03.*

**A preset's `freq` beats the pitch SuperDirt works out from `note`.** A voice with `freq: 50` in its preset stayed at 50 Hz under
`note -5`. Keep a voice's own pitch in a control of its own (`sdgfreq`) and multiply it by `freq / 261.6256`. *Measured 2026-11-03.*

**At gain 1 a voice gets `amp 0.4`, and `dirt_gate` applies 0.4 anyway**: a SynthDef that used `amp` would be scaled twice. A mono
signal peaking at 1.0 inside a synth reaches the orbit at -11.0 dBFS on each side (0.4 times `DirtPan`'s 0.71 at the centre). `gain`
is steep, amp following gain to the 4th power: `gain 1.2` measured +6.3 dB, `gain 0.8` -7.7 dB. *Source: `core-synths.scd`.
Measured 2026-11-03.*

**A sample file plays at 0.4 times 0.71 on each side, mono or stereo.** A file peaking at 1.0 reaches the orbit at -11.0 dBFS per
side either way (stereo files measured -10.9 to -11.0): a stereo file is not 3 dB hotter. Its loudness on the orbit is what differs:
a mono file, one channel heard on two sides, plays 8.0 LU under its own measured loudness, a stereo one 11.0 under. *Measured
2026-11-03 on the rendered banks in `samples/`.*

**`attack` and `release` are taken.** They switch on the stock `envelope` module. *Source: `core-modules.scd`.*

**`~diversion` is how to take an event away from `sound`.** `DirtEvent:play` checks it once before the modules
run, and the stock `sound` module checks it again. Setting it from a module that runs before `sound` lets you
play a sample yourself without replacing anything. *Source: `DirtEvent.sc`, `core-modules.scd`.*

**The sound is looked up before any module runs.** `DirtEvent:play` calls `mergeSoundEvent`, which finds the
sample from `~s` and `~n`, before `playSynths` runs the modules, so a module cannot change which sample plays.
What runs earlier is `~diversion`, called once with the `DirtEvent` before the lookup: one in the orbit's
default event can rewrite `~s` and `~n`, and answers nil to let the event go on. The stock `sound` module and
the player module's test call `~diversion.value` again, with no argument, so it has to do its work only once.
That is the tag picker. *Source: `DirtEvent.sc`. Measured 2026-11-03 with Tidal 1.9.5.*

**An event with no `s` has `~s` of `'nil'`:** `splitName` reads `~sound.asString`. Tidal never sends one anyway: its
SuperDirt target lists `s` as a required argument and drops an event without it. *Source: `DirtEvent.sc`, and Tidal 1.9.5's
`Stream.hs`, `superdirtShape`. Measured 2026-11-03.*

**A nil in a hand-written `/s_new` list is sent as int 0**, not dropped. Hence `~x ? default` in a module, and
the SynthDesc's `msgFunc` in the player module. *Found in use.*

**Per-event synths never report `/n_go`.** `DirtEvent:sendSynth` sends `/s_new` with node id -1, the server assigns
a negative id, and scsynth sends no notifications for those. Only the event's group, which has a real id, reports.
To prove an event played, catch the group's `/n_go`, then `/g_queryTree` it. A half-cycle event is gone within about
0.3 s of starting, so give a test event `sustain 1.5`. *Measured 2026-09-11.*

## Global effects

**A pattern without a global effect's parameter switches it off at once.** Every event runs `GlobalDirtEffect:set`,
and a key the event lacks is nil, which differs from the state and is sent, as 0. Only a pattern that stops (`d1
silence`) leaves the last value, and the tail rings. A knob in this window goes into the default event, so it
does not have the problem. *Source: `GlobalDirtEffect.sc`. Measured on `sdgspace`.*

**`GlobalDirtEffect:play` always starts the synth `\addAfter` the orbit's group**, at the head of the chain.
`initNodeTree` plays them in reverse to get the order. Restarting one in place is `play`, then
`fx.synth.moveAfter(previousFx.synth)`. *Source: `GlobalDirtEffect.sc`, `DirtOrbit.sc`.*

**`SynthDef.add` is asynchronous.** A fresh effect in `initNodeTree` without a server sync in between never starts.
*Found in use.*

**`DirtOrbit:globalEffects_` does not rebuild anything.** It only sets channel counts. Call `initNodeTree` after
changing the array. *Source: `DirtOrbit.sc`.*

**A sleeping global effect never frees on `release`.** `DirtPause` is `DetectSilence` with `doneAction: pauseSelf`. Once
it has paused its synth, it pauses it again in the first block after any `/n_run` that comes without the `dirt_resumed`
trigger. `release`, and so `play` and `initNodeTree`, sends `/n_run` and the gate only, so the release never finishes
and the synth stays in the tree, paused, holding its memory, until a cmd-period. After a reload, every fader and monitor
that had been woken and gone quiet was still at the end of its orbit's chain. Stock SuperDirt leaks the same way on every
`initNodeTree`. `sdg.releaseEffect` sends `dirt_resumed 1` with the gate. *Measured 2026-10-17.*

**Effects sleep, and only a `set` wakes them.** `DirtPause` pauses the monitor, rms and fader after 4 s of silence, and
every event resumes the always-on ones. Anything that puts audio into an orbit *without* an event must wake it first:
the editor's preview does, and so does a knob. The fader watches its *input*, so a muted orbit with events playing stays
awake and unmutes cleanly. *Source: `GlobalDirtEffect.sc`, `SuperDirtUGens.sc`.*

**Meters: `startSendRMS`'s rate never lands.** `DirtOrbit:startSendRMS` calls `set` on `dirt_rms` before `active_(true)`,
and an inactive effect takes nothing, so the meters ran at the SynthDef defaults, 8 Hz with a 3 s peak lag.
`SuperDirtGUI:startMeters` puts the rate in the default event *and* the effect's state, then rebuilds. It has to be in the
default event, because an event without it would set it back to nil, sent as 0. 36 replies in 2 s. *Source: `DirtOrbit.sc`.
Measured 2026-10-17.*

**`SendPeakRMS`'s peak field holds for seconds.** A "muted" reading right after a burst shows a clean geometric decay
(0.025, 0.019, 0.014...) that is the follower, not signal. Read the rms field when you need silence to read as 0. *Measured.*

## Headless and server

**SuperDirt's global effects land on `Server.default`.** `GlobalDirtEffect:play` targets the orbit's group *id*, and
`Integer:asTarget` is `Group.basicNew(Server.default, id)`. In a fresh sclang with its own check server, the effects go
to localhost:57110. So a headless check must set `Server.default = s` before making SuperDirt, on AppClock, because a quark's
window may react to the change with a Qt call. *Source: `GlobalDirtEffect.sc`. Measured 2026-09-12.*

**A stock server drops one of SuperDirt's own defs.** `default-synths.scd` hits `exceeded number of interconnect buffers` at
the default 64 wire buffers. Harmless here; `numWireBufs = 256` keeps the log clean. *Found in use.*

**Feedback between returns needs a bus no orbit writes to.** `Out.ar` overwrites a bus last touched in an earlier block. See
[the-mixer.md](the-mixer.md). Sends from a return before and one after the target add up with this done. *Measured 2026-11-01.*

**Dante Virtual Soundcard holds UDP 57131, and `lsof` does not show it.** Its daemon, `dvsd`, keeps a *connected* UDP socket
on 127.0.0.1:57131, which `lsof -iUDP:57131` lists as nothing; a server booting there fails to bind. Only binding the port, or
`netstat -anv -p udp`, shows it. That is why [make-library.scd](make-library.scd) reads its port from `$SDG_LIBRARY_PORT`.
*Measured 2026-11-03.*

**Array arguments do travel in `/s_new` on 3.13.** `Synth.newPaused(def, [\arr, [1, 2, 3, 4]])` reaches a NamedControl array,
which is what lets `play` restore an insert's eq bands from `state.asPairs`. *Measured 2026-10-18.*

## The language

**`Interpreter:compile` answers nil on a parse error and throws nothing**, so `String:load` "succeeds" on a broken file. Hence
compiling first. *Found in use.*

**A caret inside a `{ }.defer` function returns from a dead frame.** By the time AppClock runs it the method has returned. Guard
with `if(this.isOpen)` instead. *Found in use.*

**A String literal over 8188 characters is cut off silently when compiled:** no error, just the first 8188, at 9000, 40000 and 2000000.
An Int8Array as code has no such limit. *Measured 2026-11-01.*

**A `^` in text given to `Interpreter:compile` ends the Routine that runs it, silently.** *Found in use.*

**Signal arithmetic is fast and its reductions are not.** `+`, `*`, `log`, `sqrt` and `squared` on a Signal are
primitives, about a microsecond for 512 values; `sum` and `maxItem` are loops in sclang, about 14 microseconds. A
1024-point `fft` with magnitudes runs 2500 times in 0.34 s. That is what makes the analysis possible in the language.
*Measured 2026-11-03.*

**`Object.readArchive` of 5000 small Events takes 5.6 s, and `interpret` of their compile string 2.4 s.** The tag store
is a tab-separated file instead. *Measured 2026-11-03.*

**`(0..(n - 2))` counts down when n is 1.** An SC range whose end is under its start runs backwards (0, -1); it is never
empty. The analysis failed with `binary operator '>=' failed` on every file shorter than one 256-sample hop, 26 of
Dirt-Samples' 2038 files (3 to 11 ms: `armora`, `chin`, `e`, `msg` ...). Loop with `(n - 1).do`, or check the ends before a
range. *Found in use, 2026-11-03.*

**`List:reject` (and `select`, `collect`) answers an Array, and `Array:add` answers a new Array once the old one is full.**
So `list = list.reject { ... }; list.add(x)` stops growing at its capacity: the window's MIDI registry held 4 of 19 controls.
Assign what `add` answers, or keep a List and use `removeAllSuchThat`. *Found in use, 2026-11-04.*

**`Date` has no constructor from seconds since 1970**, and `mdls -raw` with several files answers their values
separated by NUL characters. Both are worked out by hand in `SDGSampleTags` and `SDGWindow.dateString`. *Source:
`Date.sc`. Found in use.*

**`Document` and `ScIDE` do not exist in a headless sclang.** Look them up by name. `Document.open` on a file already open only fronts
it, and ignores the envir. *Found in use.*

## Qt

**A View hook answering `false` swallows the event as surely as `true`.** Answer nil to pass it to the widget. `TreeView` selection hooks
run a turn later, never inside the click. *Found in use.*

**A disabled Button keeps the colours of its states** and looks clickable, so change the states with `enabled`. *Found in use.*

**`ListView`'s `enterKeyAction` is the Return key only**, not a double click. A double click is `mouseDownAction` with `clickCount == 2`,
by which time the first press has made the row current. *Found in use.*

**On Windows and Linux, QtCollider reports Ctrl as `isCtrl`, never `isCmd`:** only macOS maps Qt's Control to the cmd mask. With Ctrl held,
Qt may also hand over a control character (19 for s), so check the key code too. *Source: `QKeyModifiers.toCocoa`, in the class library. Not run on Windows.*

**`s.freeAll` in a headless session can crash sclang** in a server view's scope painting while the tree is made again. To simulate a
cmd-period, send `/g_freeAll` to group 1, then remake the orbits. *Measured 2026-11-01.*

**`CmdPeriod.run` clears both clocks first, then tells its objects, then frees the servers.** So a `cmdPeriod` method may start a new Routine
on AppClock at once, but a Routine or a one-shot `AppClock.sched` from before is gone: a `pending` flag set for it stays true for ever, and
nothing is ever redrawn again (the explorer after a cmd-period during analysis). An `OSCFunc` not made with `.fix` is freed too (meters, the
editor's playhead); `SkipJack` restarts itself. The node tree is made again later, on `ServerTree`, after a sync. *Read in
`SystemActions.sc`, measured 2026-11-06.*

**A `TreeView` cannot say which items are open, nor open one, nor hear a click on its header.** `invokeMethod(\expandItem, item)`,
`\expand` and `\isItemExpanded` all fail as primitives, and connecting to `itemExpanded` gets nothing. Only `expandAll` and
`collapseAll` work, so a tree rebuilt to sort it loses what was open. The explorer is a `UserView` that draws its own list
(`SDGExplorerView`). *Measured 2026-11-03.*

**Qt sizes and mouse positions are Floats**, and `div:` of a Float answers a Float, so a row number worked out from them is
0.0, which has no `odd`: the explorer's row shading broke on the first scroll. `asInteger` anything that indexes rows.
*Found in use, 2026-11-03.*

**A Qt getter called from a Routine fails** (a TextField's `string`, a NumberBox's `value`): Qt calls belong on AppClock.
A check reads them inside a `defer`. *Found in use.*

**`Image.fromWindow` right after a layout change can hang**: the handle between the strips and the right panel is a
`UserView`, and the grab waits on its repaint. Take the picture a second after the last change. *Found in use, 2026-11-03.*

**A screen capture only shows a headless sclang's window if it is in front**, and it is not. `Image.fromWindow` in a later `defer` works.
*Found in use.*

## Windows paths

`+/+` writes `\` there, while a path from elsewhere may have `/`, and `dirname` and `standardizePath` are primitives whose Windows spelling
was not checked. So paths are compared with `SuperDirtGUI.samePath`, never `==`, and Explorer's `/select,` and `start` get `\`. *Found by
reading, 2026-10-19. Not run on Windows.*

**On Windows a folder from `pathMatch` ends in `\`, not `/`**, so `endsWith("/")` never spots one there: test `p.last.isPathSeparator`. The
same file can come back in several spellings, so tags are kept under `SDGSampleTags.keyOf(path)`, the normalised path there. *Source:
`SC_Filesystem_win.cpp`, `globNext`, SuperCollider 3.13. Not run on Windows.*

**On Windows `unixCmd` runs `cmd /c` with the string as its command line**, which ends at the first newline and whose quoting a `"` in the
text breaks. Tidal code has both, so the clipboard gets it through a temp file that PowerShell reads. *Source: `sc_popen.cpp`,
SuperCollider 3.13. Not run on Windows.*

## SuperDirt and Tidal

**`Quarks.update("SuperDirt")` checks out SuperDirt's 2021 `master`.** Refresh it by uninstalling, trashing the folder and installing.
*Found in use.*

**`SuperDirt:loadSoundFiles` drops `appendToExisting`:** an assignment in the argument list. `loadSamples` goes to the library directly.
*Source: `SuperDirt.sc`.*

**Dirt-Samples has no `break` bank.** For a long sample, `breaks125` (1.9 s), `breaks152` (4.7 s), and `numbers`, spoken words, good for
hearing time against pitch. *Found in use.*

**A word in Tidal's mini-notation takes letters, digits and `: . - _`**, and starts with a letter or a digit. So several tags
go in one word joined with `.`, and a tag cannot start with `-`. *Source: tidal-core 1.10.1, `ParseBP.hs`, `pString`.*

**`loopAt n` reads a sample at `speed / n × cps × duration`.** It sets `unit "c"`, which `DirtEvent:calcTimeSpan` turns into
that rate, so a `# speed` inside the `loopAt` is a pure rate factor, and `# end e # speed e` plays the first e of a file over the
same n cycles. Through Tidal 1.9.5, every piece of the sync window's code played at its planned rate to four decimals.
A check's ghci script needs `import Control.Concurrent` for `threadDelay`, or Tidal hushes at once. *Source: `DirtEvent.sc`.
Measured 2026-11-03.* [sync.md](sync.md).

**Tidal's `a1` is 27.5 Hz:** octave 5 is middle C (`note 0`, midi 60). Bass sits around `a2` (55 Hz) and `e2` (41 Hz). *Found in use.*

**`note "a'min9" - 12 # s "sdgpad" # gain 0.8` is not an octave down.** The `12` becomes a whole control map and the
subtraction runs over the event: the note stays, `n: -12` is added, and gain, legato and every other number come out
negative - the pad was silent. `+ 12` only works by luck (an extra `n: 12`, which a synth also takes as a transposition).
Transpose inside the brackets: `note ("a'min9" - 12)`. *Measured 2026-11-03 with Tidal 1.9.5 (`queryArc`, then played
through SuperDirt).*

**In Tidal 1.9, a target's `oLatency` is taken off, not added.** An event is worked out 0.3 s ahead (`cProcessAhead`) and
stamped, or sent, at its onset minus `oLatency`: a bigger latency is earlier. So a `Live` target - Tidal holds the message
back and sends it unstamped, for a program that acts on arrival, as TouchDesigner does - with the same `oLatency` as
SuperDirt's sends each message at the moment SuperDirt's bundle says the sound starts. Measured with a listener on both
ports: the TouchDesigner message 1 to 3.6 ms after the sound's time, the SuperDirt bundle 274 ms before it. A `{word}` in
a target's OSC path (`/tidal/{td}`) leaves out every event without that word, for that target only:
`examples/tidal/multi target tidal/` routes with it. *Measured 2026-11-06 with Tidal 1.9.5.*

**ghci runs the lines one `:def` macro returns out of order**: three `:script` lines from one macro ran last first. One
command per call - `examples/tidal/boot.hs` calls its `:sdgload` once per file. *Measured 2026-11-06, GHC 9.12.*

## DSP and plugins

**A file that starts on a hit wants its first frame twice over.** Its first spectral-flux frame rises from silence over
the whole spectrum. Left in the curve the tempo is read from, it pulled `breaks157` from 158 to 105.5 bpm and `breaks165`
from 166.75 to 132.5; taken out, the grid's phase lost the downbeat to the louder hats of a test loop. So the tempo reads a
curve without it, and the phase one with it. A kick's flux is also small beside a hat's (75 against 540 on that loop), which is
why the phase goes to where the sound starts whenever that phase scores a tenth of the best. *Measured 2026-11-03.*

**A flux against the frame two back finds attacks a flux against the one before misses**: kicks, plucked bass, the start
of a file, all rising more slowly than one hop. Against two back, `bd`'s onset curve peaks at about 400 rather than 9. A pad's
drift is the opposite problem: its curve is all small bumps, each tall beside the others. Held to a floor of 150
(`salienceFloor`), `alien-monolith-pad` went from 101 attacks to one. *Measured 2026-11-03 on Dirt-Samples.*

**`Warp1`'s pointer is 0 to 1 of the buffer.** Dividing by `BufSampleRate` is only right for a one-second file. Divide by `BufFrames`.
*Found in use.*

**`Warp1` runs about 4 times (12 dB) hotter than `dirt_sample`** at `overlaps: 8`: rms 0.51 against 0.13 on noise, 0.47 against 0.12 on a
sine. *Measured 2026-11-01.*

**A repeated grain has its pitch only while the content holds still.** A piece of sample moving as fast as it is read has no period: `sdgscan`
at 220 Hz measured 112 Hz until the position was latched in steps. *Measured 2026-11-01.*

**`Index` reads a buffer of any width**, as raw interleaved samples. Mono and stereo second samples both pass in `sdgcross`, with no
channel mismatch in the log. *Measured 2026-11-01.*

**`Pluck` goes flat as the note rises.** Its loop filter lengthens the string by its phase delay, `atan2(c sin w, 1 - c cos w) / w` samples: -32
cents at midi 96 on coef 0.3, before `sdgpluck` took it off the delay. The low-frequency shortcut `c / (1 - c)` overshoots by +40 cents at coef 0.7.
`Pitch.kr` reads a plucked note an octave high and a voice with a sub oscillator an octave low, so to measure tuning, scan the recorded note with a
Goertzel. *Measured 2026-11-01.*

**`Latch` only moves on a rising trigger.** `Impulse.ar(SampleRate.ir)` is 1 on every sample, so it never rises and `Latch` never
takes a new value: a sample-rate reducer whose "clean" end was the sample rate held one value instead. Its clock has to stay at or
under half the sample rate (`sdgratchet`, `sdgchaos`), or the crush be switched out at 0. Above half, the closer the clock to the
sample rate, the fewer the triggers: counting the new values per second, a clock at 0.5 of 44.1 kHz gave 22079, 0.6 gave 17664,
0.9 gave 4416 and 1.0 none. *Found in use, twice; measured offline 2026-11-03.*

**A whole-number FM ratio puts a sideband on 0 Hz**, DC under a short hit: 0.018 at `sdgratio 1` in `sdgping`. It lasts as long as
the ring, too short for `LeakDC`, which only smears it; a high pass under the pitch takes it out. *Measured 2026-11-03.*

**`SawDPW` is sc3-plugins**, though stock `supersaw` uses it. `Saw.ar` has no phase input, so `sdgsupersaw` gives each saw a random `DelayN` of up
to one period instead. *Found in use.*

**Greyhole's echoes come `delayTime` + 70 ms apart**, with the first at 70 ms whatever the time: at size 1 and diffusion 0.1, 0.25 gave 0.07, 0.391;
1.0 gave 0.07, 1.141, 2.211. `sdgecho` takes 70 ms off and delays its input by the rest, so echoes land on 0.25, 0.501. At high feedback a Greyhole
keeps its orbit awake long after it is inaudible: `sdghole`'s defaults slept after 49 s. *Measured 2026-11-02.*

**The Mi effects at their edges.** MiWarps is silent from `algo` 7.9 to 8 whatever its help says, so `sdgwarps` stops at 7.5;
its vocoder wants `vgain` near 5 to come near the other algorithms. MiRings' models 0 and 2 ring an octave above `pit` (60 peaks
at 72 and 84; model 4 has 60 itself). A frozen `sdgclouds` held in 24 of 24 runs of one check, but faded to about 0.001 within a
second in two other set-ups, for a reason not found: treat freeze as usually holding, not as proven. *Measured 2026-11-02 at
44.1 kHz.*

**mi-UGens need scsynth's plugin path, not sclang's.** To check them without installing, the class files go through a temp conf and the `.scx`
through `o.ugenPluginsPath`, which replaces the defaults, so list the app's plugins and the extensions folder too. *Found in use.*

**A VST plugin lives in its synth.** `VSTPluginController` opens it inside the `VSTPlugin` UGen of one synth, and freeing the synth closes the plugin
with its state. With Polyverse Wider, an insert's synth keeps its node id through `rebuildOrbit`, `reload`, `startMeters` and `setOutBus`, and a freed
one comes back with a remembered parameter (0.37) through the `ServerTree` hook. *Measured 2026-10-18.*

**A plugin path from `pathMatch` ends in a slash for a `.vst3`**, which is a folder, and VSTPlugin then says no such file: `withoutTrailingSlash`.
*Found in use.*

**A plugin saved by name opens by path in a fresh sclang.** `controller.open("Wider")` fails until VSTPlugin has searched in this sclang, and a session is
often the first thing loaded. A parameter of 0.63 came back across two sclangs with the path kept beside the name. *Measured 2026-11-01.*

**`parameterAutomated` is not a full picture of a plugin's state:** a preset picked in its editor need not send it. *Found in use.*
