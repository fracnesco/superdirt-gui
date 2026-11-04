# Sync: long samples on one tempo and in one key

A drum loop at 128 bpm, the amen at 152, an arpeggio in E minor at 100: the sync window works out
how to play them together at one tempo, and in one key if they have one. It writes the Tidal code
that does it. It uses no time stretching, only what Tidal already does with a sample: `loopAt`
stretches it over whole cycles, `chop` cuts it into pieces that each start on the grid, and `speed`
moves its pitch.

```supercollider
y = ~sdg.sync;                    // an SDGSync, one per SuperDirtGUI
y.add(\breaks125, 0); y.add(\breaks152, 0);
y.bpm = 125;                      // nil: the first sample's own
y.key = "Am";                     // nil: the first melodic sample's, as the tempo leaves it
y.code.postln;
y.play; y.stop;                   // heard here, without Tidal
y.gui;                            // the window
```

In the explorer, **sync...** opens the window. `y`, or **add to sync** in the right-click menu,
adds the selected sample, or every sample of a selected bank.

## What the code does

A loop of `b` bpm that lasts `d` seconds holds `d × b / 60` beats. Tidal's `loopAt n` slows the
pattern by `n`, multiplies `speed` by `1/n` and sets `unit "c"`. SuperDirt then reads the sample at
`speed × cps × d` (`DirtEvent:calcTimeSpan`), so the whole file lasts exactly `n` cycles. With
`n = beats / 4` and `setcps (t/60/4)`, a loop of `b` bpm is read at `t / b` of its speed. Its beats
land on the grid, and its pitch moves by `12 log2(t / b)` semitones. That pitch change is the price
of keeping it a plain sample.

```haskell
-- SuperDirtGUI sync: 125 bpm, 4 beats a cycle
setcps (125/60/4)

-- breaks125:0: 128.3 bpm, 4 beats in 1.87 s, over 1 cycle, read at x0.975 (-0.45 st)
d1 $ loopAt 1 $ chop 8 $ s "breaks125:0"

-- breaks152:0: 152 bpm, 12 beats in 4.7 s, over 3 cycles, read at x0.816 (-3.52 st)
d2 $ loopAt 3 $ chop 24 $ s "breaks152:0"

-- breaks157:0: 158 bpm, 4 beats in 1.82 s, over 1 cycle, read at x0.791 (-4.06 st)
-- 4.78 beats long: the first 4 play (end 0.8363)
d3 $ loopAt 1 $ chop 8 $ s "breaks157:0" # end 0.8363 # speed 0.8363
```

That is what the window wrote for three of Dirt-Samples' breaks, from their analysis.

- **Half and double time.** A loop whose own bpm is nearer half or twice the tempo is counted that
  way. A 70 bpm loop at 140 is 8 beats where it would have been 4. So the tempo alone never moves a
  sample by more than a tritone.
- **Not a whole number of beats.** A loop with a pickup or a tail plays its whole beats only. `# end e`
  cuts it after them, and `# speed e` reads what is left at the tempo. Rounding up instead would
  stretch the extra part beat over all the others, and every beat inside would drift.
- **A first beat that is not at the start.** If the analysis or the editor's grid puts beat 1 more
  than 15 ms in, the line starts with `x <~ ( ... )`. That moves the loop earlier by the same amount,
  so beat 1 lands on the cycle. **first beat** switches this off.
- **No tempo.** A sample with no bpm, or every sample with **same tempo** off, is stretched to the
  whole number of cycles nearest its own length. It loops with the bars, but nothing inside it
  is matched.

## The key

With **same key**, a melodic sample is moved the rest of the way into one key by a speed inside the
`loopAt`. Melodic means tagged `melodic`, or with a key set by hand. A drum loop's "key" is noise, so
other samples keep their pitch. The speed is always less than a tritone, up or down. Its key is
named in the comment above the line:

```haskell
-- arp:1: 100 bpm, 8 beats in 4.8 s, over 2 cycles, read at x1.2 (+3.16 st), Am to Cm: -0.16 st by speed
d2 $ loopAt 2 $ chop 16 $ s "arp:1" # speed 0.991 # legato 1
```

The sample now reads a little faster or slower than the grid, and `chop` is what keeps it in step.
Every piece starts on its own beat or eighth, so a piece only ends a little early, a short gap, or
runs a little late, cut at the next piece by `legato 1`. The more pieces, the less either is heard:
**chop** sets how many a beat. A correction of a semitone or two is clean on most material. Near a
tritone the joins start to show on held notes.

Whatever key you pick, every melodic sample goes to it. A minor sample into a major key goes to its
relative minor, and a major one into a minor key to its relative major, so an A minor loop and a C
major one are already together. A one-shot's note goes to the key's tonic. **auto** takes the key of
the first melodic sample, as its tempo leaves it, rounded to a semitone. That sample then needs the
least speed, and the order of the list decides which one that is.

## The window

| control | does |
|---------|------|
| **tempo**, **auto**, **÷2**, **×2** | the bpm they play at; auto is the first sample's own bpm |
| **beats a cycle** | 4 is a bar of 4/4, what `setcps (bpm/60/4)` means |
| **same tempo** | every loop with a bpm on the tempo's beats; off, each just fills whole cycles |
| **same key**, the key menu | every melodic sample into one key; auto is the first melodic one's |
| the chop menu | pieces a beat: none, a beat, 1/8, 1/16, 1/32 |
| **first beat** | shift a loop whose first beat is late, with `<~` |
| the list | each sample: bpm (`?` when not sure), beats, `loopAt`, how far the tempo moves its pitch, its key and where it goes, the speed, the chop, and what to look out for |
| **bpm...**, `b` | the selected sample's bpm by hand, or from how many beats it holds |
| **add selected**, **remove**, **up**, **down**, **clear** | the samples, in the order of the code: the first is `d1`. Delete removes one |
| **play**, space | plays them together here, every piece as an event like Tidal's, into the orbit chosen |
| **copy code**, **post** | the code onto the clipboard, or into the post window |

**look out for** says what the numbers cannot: a bpm or key the analysis is not sure of, a loop counted
in half or double time, a loop trimmed to whole beats, one more than 7 semitones from its own tempo,
a sample not analysed yet. A wrong bpm in the analysis is a wrong loop here. Set the right one with **bpm...**, or in the
editor where the grid shows it, and the window follows.

## How it is known

`t_sync.scd` checks it with four synthetic loops whose bpm and key are set by hand. The generated
code is run in real Tidal 1.9.5, through ghci, against a check SuperDirt. A logging module reads
each piece's rate after SuperDirt has worked it out. Every rate is the planned one to four
decimals, and the pieces of all four samples arrive 0.25 s apart, within 2 ms, on one grid. The
preview passes the same test. Whether a correction sounds clean is for ears: see
[testing.md](testing.md).
