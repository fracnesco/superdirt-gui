# Testing

This is checked by headless sclang runs that nobody has to listen to: they compile the class library,
build SuperDirt with everything on a throwaway server, measure sound by reading busses back, parse the
server's node tree, and write and delete real files. What they cannot check is at the end.

The check files are not in the repository. They are written fresh where they are needed, in the shape below.

## Running sclang without the IDE

```bash
/Applications/SuperCollider.app/Contents/MacOS/sclang check.scd > check.log 2>&1
```

- sclang compiles the whole class library first, so a class error shows before the check runs. Your open IDE
  does not recompile: after editing a class there, press cmd + shift + L.
- Redirect to a log and read the log. A pipe into `tail` holds everything back until it exits.
- Give it a watchdog from outside, such as `perl -e 'alarm 200; exec @ARGV' -- sclang ...`: if the class library fails
  to compile, the check never runs and nothing inside it can end the process.
- Run one check at a time. Two sclangs compiling at once double the time.

## The harness

```supercollider
// on a server of its own, never 57110 or 57120, which the IDE uses
SynthDescLib.global.removeServer(Server.default); SynthDescLib.global.addServer(s);
{ Server.default = s }.defer; 0.5.wait;     // on AppClock: a quark's window reacts to it with Qt
d = SuperDirt(2, s); s.sync;
thisProcess.openUDPPort(57134); d.start(57134, [500, 500]); s.sync; 0.5.wait;   // orbits into a private bus
g = SuperDirtGUI(d); s.sync; 1.0.wait;      // applyGlobalEffects forks past a server sync
```

- **`Server.default` has to be the check server:** see [superdirt-notes.md](superdirt-notes.md). Without it the effects
  land on the IDE's server, the node tree shows orbit groups with nothing after them, and `/rms` never comes.
- **`d.start(port)`, the instance method:** the class-side `SuperDirt.start` reboots `Server.default`.
- **No `loadSoundFiles`.** Write two or three synthetic files to `Platform.defaultTempDir`, then `d.loadSoundFileFolder(dir, \chk)`.
- **Options:** `o.memSize = 8192 * 32; o.numWireBufs = 256; o.numBuffers = 4096; o.numInputBusChannels = 0`.
- **Give each check its own `port:` and OSC port,** if anything else may be running on the machine.

## What is measured, and how

**Chain order** by parsing `/g_queryTree` of `d.group`. The effect synths are the depth-1 siblings after each orbit's group, compared
against `orbit.globalEffects.collect { |fx| fx.synth.nodeID }`. **Leaks** the same way, after letting an effect run and sleep: an event,
then 5.5 s.

**Sound** by a steady test SynthDef played into an orbit (`Env.linen`, because `sdgsine`'s percussive envelope decays between readings), and
its output bus read by a `SendReply` probe at the tail of the root node. An eq's low pass at 300 Hz took a 2 kHz tone from 0.084 to 0.0019; a
-40 dB, 20:1 compressor took one from 0.085 to 0.012.

**That an event played** by catching its group's `/n_go`, then reading the children's def names: per-event synths report nothing themselves.
**The fader** through `dirt_rms`, reading the rms field, since the peak holds for seconds.

**Tuning** by recording a note and scanning it with a Goertzel. `Pitch.kr` is fooled by a strong second harmonic or a sub.

**Tidal** for real: ghci with Tidal 1.9.5 driven through a `Pipe`, a block at a time with `putStrLn "DONE id"` markers in its log, `startTidal` pointed
at the check SuperDirt, `cEnableLink = False` and `cCtrlListen = False` so a running Tidal is never touched, synthetic banks under the real names, and
a logging module last in `d.modules`.

**Sync** by running the code the sync window writes in real Tidal, with a logging module reading each piece's rate after SuperDirt has
worked it out (modules run after `calcTimeSpan`), and the time it plays (`Main.elapsedTime + ~latency`). The bpm and key of the test loops are
set by hand, so the expected numbers are exact.

**The pieces** (files 26 to 34) by the same route, one ghci for all of them, with synthetic banks under the names of the Dirt-Samples
they use. Every block of a file is evaluated in turn with a `MARK` line after it, and per block: the ghci log between the marks must hold no
error; every `d<k>` the block sets must have reached orbit k-1 (a warning only, since a sparse pattern may not fall inside the window);
and every `sdg…` key an event carries must be a control of the synth it reached, or a module's, a player's or a global effect's word.
That last one is a failure: a parameter that does nothing is a typo, or a word that belongs to another voice.

**The grooves** ([examples/grooves](../examples/grooves/)) by two halves: every block through a real Tidal 1.9.5 with nothing listening, for
syntax, types and each file's own words; then the events every running pattern produces, read back with `queryArc` after each block and checked
against [library.md](library.md) - every `s` a synth or a bank, every `sdg…` word a control of the voice it reached or of a player the event asked
for, a module's or an orbit effect's word, every value in its header's range. The SuperDirt run (the same as for the pieces, twelve orbits, with
recordings of the end sections) is the other half, when the server is up.

**Content files** are checked in the real folders, since they are found from the class file: a check writes files named `zzchk*.scd` and deletes
them in a `protect`. It must delete nothing else, and must assert with `includes`, not `==`, on what the folders hold, because your own files live there.

**Sessions** with `SDGSession.folder` set to a scratch folder, so your own sessions are never touched.

**MIDI** with a simulated controller: a virtual MKtl of a real description (an Akai Midimix), whose elements are moved
through Modality's own code path, so mappings, pickup, curves, buttons and learn are checked as the hardware would
drive them. Learn mode in the window is checked by sending clicks through the views' `mouseDownAction`, and the MIDI window
by setting every widget through `valueAction`.

**The window** with `{ g.gui }.defer`, asserting on `g.window`, `win.explorerModel`, `win.selected`, `win.loadedEntries` and `SDGSampleEditor.all`,
never on widgets, whose getters lie. Strips staying level is proved by the faders' `absoluteBounds`. `SDGWindow.codeEditor` catches a path instead of
opening a document. `Image.fromWindow(g.window)`, in a later `defer`, gets a picture to look at. **The window flashes on your real screen while this runs.**

A cmd-period is simulated as `CmdPeriod.run` does it, minus clearing SystemClock (the harness lives there): `AppClock.clear`, then
`CmdPeriod.run` with `clearClocks` and `freeServers` false (every CmdPeriod object told, every unfixed OSCFunc freed), then `/g_freeAll` to
group 0, `/clearSched` and `s.initTree` so `ServerTree` really runs (`t_cmdperiod.scd`, 22: analysis, meters, a return's send, the editor's
preview, a pending refresh, twice over). A real `s.freeAll` can crash a headless sclang. The `/n_free Node not found` lines after it are
SuperDirt's own `clearFlotsam`.

Plugins that need a search are opened by file path, since a full search probes every plugin on the machine. `VSTPlugin.search(s, [one folder])` keeps it short.

## The reference page

[library.md](library.md) is generated, and running [make-library.scd](make-library.scd) checks it as well: it builds every content file on a server and
reports, at the end of its log, a listed parameter with no control and a control no header lists. The expected ones are a file's switch, `sdghold`, which `prepare`
reads, and `sdgbuf2` and `sdglayerlen`, which are internal. A file needing a plugin that is not installed is not built, and the page says so.

## What is left for a person

No check listens. These need you, in the IDE, with a sound coming out:

- Recompile, `~sdg.gui`, and play the [Tidal examples](../examples/tidal/): do the synths and players sound the way their headers say?
- **+ insert** of each kind on a playing orbit: does its window work on the orbit, and does taking it out click?
- A return with `\dirt_reverb` and a send, then a loop with **feedback**: does it stay bounded at a tolerable level with the limiter on?
- **A real cmd-period** with a VST insert and a return loaded, since the checks only simulate one.
- A session saved, the IDE recompiled, and the session loaded again.
- A real controller in [midi learn](midi.md): does pickup feel right, and do the LEDs follow?
- The [sync window](sync.md) on your own loops: does a key correction of a semitone or two sound clean at 1/8 and 1/16 chop, and where do the joins start to show?
- The feel of the knobs and faders, and whether the hint bar's words help.

Windows has not been run at all. The shortcuts, clipboard and paths were fixed by reading the code; a student should try it before relying on it.
