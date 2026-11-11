# Tags, bpm and key

Every sample in the library is analysed once: how long it sounds, how it starts and ends, where its
attacks are, its tempo if it has one, its key or its note if it has a pitch. From those numbers it
gets tags, such as `short`, `percussive` and `kick`. You can change any of it by hand. The explorer
sorts and narrows by it, and Tidal can pick a sample by it on every event.

```supercollider
t = ~sdg.tags;                                  // an SDGSampleTags
t.analyseLibrary;                               // the window does this when it opens
p = ~sdg.bufferAt(\bd, 0).path;
t.infoOf(p);                                    // (tags:, bpm:, bpmSure:, key:, keySure: ...)
t.query([\percussive, \short], [\melodic]);     // [[\bd, 0], [\sn, 3] ...]
t.query(bpm: 120);                              // 120, and 60, 240, 30, 480, within 3%
t.addTag(p, "dusty"); t.removeTag(p, \kick);
t.setBpm(p, 92); t.setKey(p, "Am");             // by hand
```

## What the analysis measures

All of it runs in sclang, in `SampleFeatures`, with no server and no plugin. A file is mixed to
mono and brought down to about 22 kHz. Only its first 30 seconds are read (`SampleFeatures.maxSeconds`).

| measure | how |
|---------|-----|
| attack | from 10% to 90% of the peak of the envelope |
| decay | from the peak to 20 dB under it |
| length | from the first point within 40 dB of the peak to the last within 20 dB |
| sustain | the share of the sound within 15 dB of the peak |
| onsets | the peaks of the spectral flux of the log magnitude, 1024-point frames every 256 samples, each with how far it stands out from the half second around it |
| centroid, flatness, low, high | the spectrum's centre, how close it is to noise, its share under 150 Hz and over 5 kHz |
| sweep | how far the strongest bin under 400 Hz falls in the 250 ms after the loudest frame: a kick's pitch drop |
| tempo | an autocorrelation of the onset curve, scored as a comb over 60 to 200 bpm. A bpm that fits the file's length in whole beats wins when it scores nearly as well, since a loop is cut on the beat |
| key | 4096-point spectral peaks, 55 Hz to 4 kHz, folded into 12 pitch classes after the file's tuning is taken out, then matched against the Krumhansl-Kessler profiles |

A one-shot that is mostly one pitch class gets a note instead of a key, such as `A4`. Its octave
comes from its lowest strong partial. A sound without a pitch gets no key, and a sound with fewer than
four attacks or under a second of sound gets no bpm. Having no tempo is not the same as an unsure one.

A short file takes a few milliseconds. A 30-second loop takes about half a second. The library is
analysed in the background, and the analysis gives the rest of sclang a turn every 12 ms, so it never
holds up an event longer than that.

## Sure and unsure

A bpm is **sure** when its score stands well above the median of the comb, and no bpm unrelated to it
(not its half, double, triple or two thirds) comes within 75% of it. A key is sure when its
correlation passes 0.7 and beats every key but its relative by 0.08. When the analysis is not sure, the
explorer and the editor show the value with a `?`, and the sample gets the tag `bpm-unsure` or
`key-unsure`. A bpm or key set by hand is always sure.

These were calibrated on Dirt-Samples. Of the breaks, `breaks152` read 152, `breaks157` read 158,
`breaks125` read 126 and 128, and `breaks165` read 166.75, marked unsure. The `arpy`, `pluck`, `jvbass`
and `juno` notes were named, and `pluck`'s file names agree. Pads got no tempo, or an unsure one.

## The tags

| tag | when |
|-----|------|
| `short`, `medium`, `long` | its length is under 0.25 s, under 1.5 s, or longer |
| `percussive` | an attack under 30 ms and a decay under 350 ms in under 0.6 s of sound; or three attacks a second with little pitch |
| `sustained` | 60% of its sound is within 15 dB of its peak, it lasts half a second or more, and it is not percussive |
| `oneshot` | one attack, under two seconds |
| `loop` | a sure bpm, and the file is a whole number of beats long within 4% |
| `kick` | percussive, mostly under 150 Hz, a dark centroid, and short or falling in pitch |
| `hat` | percussive, noisy, its energy high up |
| `snare` | percussive, neither of those, its centroid between 150 Hz and 2.5 kHz |
| `melodic` | it has a key or a note, enough pitch, and is not a kick |
| `noisy` | little pitch, and a spectrum close to noise or bright |
| `bass` | mostly under 150 Hz, with pitch, not a kick |
| `bright`, `dark` | a centroid over 3 kHz, or under 250 Hz |
| `bpm-unsure`, `key-unsure` | see above |
| `slice` | written by the editor's slicer |

These are good guesses, not truths: a tuned 909 snare may well come out `melodic`. Change what is wrong.

## By hand

A tag you add is kept with the sample, and so is a tag you take away. Analysing the file again changes
neither: what counts is the analysis's tags, less those you took away, plus those you added. The same
goes for a bpm, a beat offset and a key set by hand. **tags back to the analysis** in the explorer's
right-click menu forgets all of it for that sample.

In the explorer, `b` or **set bpm...** sets a bpm by hand: type it, or type how many beats the file
holds and the bpm comes from its length. On a bank, one bpm goes to every sample, or a number of beats
gives each sample the bpm its own length makes of them, which is what a folder of 4-beat loops at
different tempos wants. **bpm ÷2** and **bpm ×2** in the right-click menu fix a tempo heard at half or
double, and **bpm back to the analysis** forgets the one set by hand. The sync window's **bpm...** (`b`)
opens the same dialog.

In the explorer, `t` or **edit tags...** opens the tags of a sample as one line to edit. On a bank, it
adds tags to every sample of the bank, and `-name` takes a tag away. In the editor, the tags field does
the same for the file it shows.

## Where it is kept

Two files in `SDGSampleTags.folder`, in your SuperCollider support directory, never inside a bank's own folder:

| file | holds |
|------|-------|
| `analysis.tsv` | one line per file: the numbers, the tags the analysis gave, the onsets. Delete it to analyse everything again |
| `tags.tsv` | what was set by hand. A copy of the last version is kept beside it as `.bak` |

A sample is known by its path. Moving a file loses what was set for it. An analysis is redone when the
file's size or modification time changes, which is what a save in the editor does.

## In the explorer

The explorer is a list with columns, like the Finder's list view. Click a column's header to sort by it,
and click again to sort the other way. Banks are sorted among banks, and each bank's samples within it,
by the same column. For a bank, duration is its shortest to its longest sample (sorted by the longest), **n** is how many samples it holds, bpm is the middle
one, key the commonest, and the dates the newest. Right-click a header to choose the columns.

| column | is |
|--------|----|
| name | file names in natural order, so `file10` comes after `file9` |
| dur | seconds |
| n | the number Tidal plays it as |
| bpm | sorted folded into 80 to 160, so 70 sits next to 140. With `bpm:` in the search, sorted by how close it is, its halves and doubles counted |
| key | C to B, major, then minor, then a note |
| tags | alphabetically |
| added | when the file came into its folder (Spotlight's date added on macOS, the first time the library saw it elsewhere) |
| created | when the file was made (its birth time on macOS and Linux, its modification time on Windows) |

The search takes words for names: a bank whose name has them all shows whole, and a sample shows when its
bank's name and its own have them all between them, so `808 bd` finds the bd files of the 808 banks. And these:

| term | narrows to |
|------|------------|
| `tag:kick` | samples with the tag |
| `-tag:melodic` | samples without it |
| `bpm:120` | a bpm within 3% of 120, 60, 240, 30 or 480 |
| `key:am` | that key, or a note on its tonic. `fs` is F sharp, `bf` B flat, as in Tidal |

The buttons under the search are the commonest tags. Click one to narrow to it, or alt-click to leave
it out. **tags** lists them all.

## From Tidal

Tidal sends nothing without an `s` (its SuperDirt target requires one), so the pattern names `sdgany`
for the whole library, or a bank to pick inside it:

```haskell
let sdgtag = pS "sdgtag"
    sdgnot = pS "sdgnot"
    sdgbpm = pF "sdgbpm"
    sdgkey = pS "sdgkey"

d1 $ s "sdgany*8" # sdgtag "percussive.short" # sdgnot "melodic"
d2 $ s "sdgany" # sdgtag "loop" # sdgbpm 120 # legato 1
d3 $ s "sdgany(3,8)" # sdgtag "melodic" # sdgkey "am" # n (irand 8)
d4 $ s "808*4" # sdgnot "bpm-unsure.dark"
```

Several tags go in one word, joined with `.`: Tidal's words take letters, digits and `. - _ :`, and a
space would make two events. Without an `n` the pick is random on every event. With one, it is the
n-th match, wrapping round, so a pattern can step through them. Nothing matching plays nothing, and the
post window says so once.

This works through each orbit's `diversion`, which SuperDirt calls before it looks the sound up. See
[superdirt-notes.md](superdirt-notes.md#events). The matches are worked out once per query and kept until
a tag or the library changes, so asking on every event costs a lookup.
