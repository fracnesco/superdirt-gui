# The sample editor

One sample of the library in a window: trim it, gain it, take the DC out, fade the edges,
read its numbers, save it back. Tidal plays the result at once under the same name and
number.

```supercollider
~sdg.edit(\bd, 3);                         // bank, index, and optionally the orbit it plays through
SDGSampleEditor.forPath("~/x.wav", ~sdg);  // a file that is not in the library
SDGSampleEditor.all;                       // the open editors
```

Opening a sample that is already open fronts its window. **edit...** in the sound explorer, or `e`,
does the same.

## In the window

The waveform has one lane per channel, with three kinds of drag:

| where | drag |
|-------|------|
| top strip | the **S** and **E** markers: the trim |
| bottom strip | the fade in and fade out handles |
| middle | the whole trim along |

Everything outside the trim is darkened: it will not be in the file. The gain is drawn into the wave,
the DC of each channel is a dashed line until it is taken out, and the fades are the envelope over the
top.

| key | does |
|-----|------|
| space | play, stop |
| `0` | the whole file |
| `-`, `=` | zoom out, in |
| `f` | zoom to the trim |
| `g` | the beat grid, on and off |
| left, right | slide |
| cmd-s (ctrl-s on Windows and Linux) | save |

| gesture | does |
|---------|------|
| cmd + two fingers up and down (or the wheel) | zoom at the pointer |
| two fingers, any direction | slide |
| double click | the whole file |
| alt + double click | zoom to the trim |
| alt + drag | slide the beat grid along |
| shift + click | a cut for the slicer, or, on a cut, take it away |

The other controls are the start, end and fade boxes in milliseconds, the gain knob and box, **normalize
to** with its dBFS box, **remove DC**, the orbit menu, **reset**, **revert**, **save**, **save as...**.

## The bpm, the key, the tags

Under the header is what the analysis says about the file: its bpm, sure, unsure or set by hand; where
a beat falls; its key, or its note; its tags; and below them the numbers it came from - how many attacks,
the attack time, how long it sounds, its centroid, how tonal it is, and the analysis's own bpm and key with
their confidence. The window analyses the whole file when it opens, in the background. **analyse** does it
again. How it works is in [sample-tags.md](sample-tags.md).

With a bpm, the **beat grid** is drawn over the wave: bars in the accent colour and numbered, beats
lighter, and the slicer's division lighter still, each only when there is room for its lines. To line it
up with the music:

- **÷2** and **×2** halve and double the bpm, when the analysis heard the tempo an octave off.
- **to 1st hit** puts a beat on the first attack of the file, to the sample.
- **alt + drag** on the wave slides the grid; **beat 1 ms** says, and takes, where a beat falls.
- typing a bpm sets it, and 0 takes the grid away.

Whatever is set here is the file's bpm from then on, by hand: the explorer and Tidal's `sdgbpm` see it.
The key field and the tags field work the same way. Leave the key empty to go back to the analysis's.

## The slicer

Tick **slicer** and the trim is cut into slices, shown on the wave as green lines numbered along the
bottom, every other slice shaded. They move as you change what makes them:

| | cuts |
|-|------|
| **on the grid** | on every line of the beat grid at the division in the menu: a bar, 1/2 to 1/32, or triplets |
| **on attacks** | wherever the analysis hears an attack. **sensitivity** is how weak an attack still makes a cut: more is more slices. The onset curve the cuts come from is drawn in pink under them, with a dashed line for the sensitivity |

Each cut on an attack is made exact: the first sample within 15 ms that reaches 30% of the loudest
there, a millisecond earlier, then back to where the wave crosses zero. **min ms** keeps any slice from
being shorter. **shift + click** adds a cut where there is none, or takes away the one under the pointer.
These cuts by hand stay through any change of mode or sensitivity, until **clear cuts**.

The first slice starts at the trim's start, and the last ends at its end.

**export slices...** asks where and under what name. The Finder's folder dialog opens first, at
Dirt-Samples the first time (the folder `~dirt.loadSoundFiles` reads, so the bank is there at every
start) and where you exported last after that. Then a small window asks for the folder's name,
`<file>_slices` to start with, and shows the files it will write: **change...** picks another place,
return or **export** writes, escape cancels. Every slice goes to its own file, named after the folder
with the slice's index from 0, which is the `n` Tidal plays it as: `brk/brk_00.wav`, `brk/brk_01.wav`
... A name that is taken there gets `_2`, `_3`. A slice is written in the file's own format, with the gain and the DC as set, and fades of 1 ms in and 4 ms
out, so no cut clicks. The folder is then loaded as a bank under its own name, every slice tagged `slice`,
and on the grid given the grid's bpm. The sample itself is never written to: export is not save.

```haskell
d1 $ s "brk*8" # n "0 3 1 2 5 4 7 6"
```

## What you hear

**play** starts a synth at the head of the orbit's group, writing into the orbit's dry bus, so it goes
through that orbit's effects, inserts and fader. It reads the library's own buffer (a temporary one for a file
outside the library): the trim, gain, fades and DC are all applied live in the synth, and no samples change until
you save. The playhead comes back on
`/sdg_editpos`.

Nothing else wakes an orbit that has gone quiet, so the preview calls `sdg.wakeOrbit` first and starts
its synth in a bundle at `server.latency`, in line with the resume bundle. Otherwise the start of the
preview would be lost.

## The numbers

Two columns, *original* and *result*: the file as it is, and what a save would write. All of it runs in
the language (`SDGSampleAnalysis`), with no server and no plugins, on the samples the file holds.

| reading | is |
|---------|----|
| duration | seconds and frames |
| peak | the loudest sample, dBFS. Over 0 and an integer file clips |
| rms | the mean level over every channel, dBFS |
| crest | peak minus rms, dB |
| loudness | integrated, K-weighted and gated as ITU-R BS.1770 says, in LUFS |
| loudest 400 ms | the highest momentary block, LUFS |
| dc offset | the mean of each channel. **remove DC** takes out the whole file's mean |
| clipped | samples already at full scale in the file, and whether the result would pass it |

They stay cheap as you drag because two passes over the audio are kept and the rest is arithmetic. `stats`
holds, per channel, the min, max, sum, sum of squares and clipped count, which give peak, rms, crest and
DC for any gain, with or without the DC, without touching the samples again. `kHops` holds the mean
square of the K-weighted signal in every 20 ms hop, from which the loudness of any stretch is a gated
average of 400 ms blocks at a 100 ms step, with gates at -70 LUFS and 10 LU under the mean. The K-weighting
pass stops at 120 s.

A stretch shorter than a block is measured as one block over what there is, and says so: a drum hit has
a loudness too, just not the spec's.

## What a save writes

The result is, in this order: the trim, the DC out, the gain, then sine fades on the edges so a trim cannot
click. The fades together never cover more than the trim, and the gain is clipped to -60 to +24 dB.

| action | does |
|--------|------|
| **save** | Writes over the file, in its own header and sample format. The **first** save copies the original beside it as `<file>.bak`, and refuses to save if that fails. Later saves leave the `.bak` alone, so revert always means the file as it was before the editor touched it. The library's buffer is then read again |
| **revert** | Puts the `.bak` back over the file and reads it again. The `.bak` stays |
| **save as...** | Writes a new file. Into the same folder as the sample, it is appended to the bank as its last index, whatever its name. Elsewhere, nothing in the library changes |
| **reset** | Settings back to none. No file is touched |

`.bak` is not a sound file extension, so the library skips those files when it reads the folder.

An integer format is clipped just under full scale first (0.9999), so a gain past 0 dBFS clips rather than
wraps. A float format is not clipped. If the file's own format cannot be written, it warns and writes WAV
int24.

After a save, `sdg.reloadSample` reads the header first, so the event's frame count is right before the server
has even read the file, then `allocRead`s into **the same Buffer** and rebuilds the event. So every event
keeps its bufnum, and nothing needs restarting.

A file over 24,000,000 samples (frames × channels, `SDGSampleEdit.maxSamples`) is refused: the whole file is
read into the language.

## Without the window

`SDGSampleEdit` is the model and needs no server. Reading, the numbers and the writing are all in the language.

```supercollider
e = SDGSampleEdit(~sdg, \bd, 3);       // bank name, index in it
e.setRegion(0.1, 0.9);                 // fractions of the file
e.gainDb = -3;
e.removeDC = true;
e.fadeIn = 0.005;                      // seconds
e.normalize(-1);                       // the gain that brings the peak to -1 dBFS
e.metrics;                             // (peak:, rms:, crest:, lufs: ...) of the result
e.metrics(false);                      // of the file as it is
e.save;                                // over the file, .bak kept
e.revert;                              // the .bak back
e.saveAs("~/kick-short.wav");
SDGSampleEdit.fromPath("~/x.wav");     // a file that is not in the library
```

Adding samples without the editor is in the README's [Extra samples](../README.md#extra-samples).
