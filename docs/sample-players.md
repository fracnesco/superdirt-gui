# Sample players

SuperDirt plays every sample with its own `dirt_sample` synth. A player is another way of
reading the same buffer, for the events that ask. The rest of the event - modules, gain,
the orbit - goes on as if nothing had happened. [library.md](library.md) lists the ones that
come with this.

```haskell
d1 $ s "breaks152" # pF "sdgwarp" 1 # speed 0.5     -- chosen by its test
d1 $ s "breaks152" # pS "player" "sdgwarp"          -- chosen by name
d1 $ s "breaks152"                                  -- SuperDirt, as always
```

## How an event gets to one

`SuperDirtGUI` installs one module, `sdgplayer`, in front of SuperDirt's `sound` module, and only
when there is a player. Its test is that the event has a buffer and is not diverted already, so
synth events never reach it. For each sample event:

1. **Choosing.** `~player` wins if it names a player. A name that does not exist warns and falls
   through. Otherwise the first player in the list whose test passes wins. `addSamplePlayer` puts a
   new player at the front, so the most recently added takes precedence, and re-adding a name
   replaces it in place. No player wanting it means SuperDirt plays the sample, and nothing has
   changed.
2. **`prepare`** runs, if the player has one.
3. **The SynthDef** is `<prefix>_<bufNumChannels>_<numChannels>`. If there is none, it warns once and
   SuperDirt plays the sample.
4. **The arguments** come from the SynthDesc's `msgFunc`, exactly as for `sound`: every control the
   event has a non-nil value for. `\bufnum, ~buffer` is appended, because the stock buffer event has
   `buffer` and not `bufnum`, and `freq` is scaled by `~metaDataTuneRatio`.
5. **`~diversion = true`.** `sound` checks it and skips the event, so the sample is heard once.

## Writing one

```supercollider
(
~sdg.addSamplePlayer(\sdgwarp, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgpitch = 1|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var sound = Warp1.ar(
			numChannels: sampleNumChannels,
			bufnum: bufnum,
			pointer: phase / max(BufFrames.ir(bufnum), 1),
			freqScale: sdgpitch,
			windowSize: 0.1,
			overlaps: 8,
			windowRandRatio: 0.1,
			interp: 2
		);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgwarp.notNil });
)
```

The function is called once per sample width (`1..SuperDirt.maxSampleNumChannels`, 2 in stock) and
answers the SynthDef function for that width. So `sdgwarp` builds `sdgwarp_1_2` and `sdgwarp_2_2`.
**The input is mono or stereo, and the output is `numChannels` wide:** `DirtPan` sees to that.

**The nine standard controls come first, in this order:** `out, bufnum, sustain, begin, end, speed,
endSpeed, freq, pan`. `SDGSamplePlayer.defaultRates` maps onto them by position: all `\ir`, `pan`
`\kr`. Anything after them is control rate, sent only when the event carries it, and can be set while
the synth runs.

`SDGBufRate` and `SDGBufPhase` are the arithmetic of `dirt_sample` taken out, so a player can spend
the rate on pitch and the phase on position independently:

```supercollider
{ |bufnum, speed = 1, endSpeed = 1, sustain = 1, freq = 440, begin = 0, end = 1|
	var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);    // speed to endSpeed, 60.midicps is unity
	var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed); // frames, from begin to end
	BufRd.ar(1, bufnum, phase, 0, 4)
}
```

With those two, the trim, `speed`, `accelerate` and `n` mean what they always mean.

## prepare

An optional function run inside the event, just before its arguments are read, for what a SynthDef
cannot do alone. SuperDirt has worked out `~sustain` by then but has not sent it, so what `prepare`
leaves there is how long the event lasts.

**Making an event outlast its sample.** `DirtEvent:calcTimeSpan` ends with `min(unitDuration, sustain)`
for a sample, so `sustain 4` on a 0.5 s file stays 0.499, and `legato` cannot help either. `sdghold`,
in cycles, on `sdgscan`, `sdgfreeze`, `sdgfold` and `sdgsmear`:

```supercollider
{ ~sdgfold.notNil }; // the test, and then, as prepare:
{ ~sdghold !? { ~sustain = ~sdghold / ~cps } };
```

**Moving the trim.** `sdgonset` reads a file for its hits in the background the first time it is
asked, then sets `~begin`, `~end` and `~sustain` to a slice. Until that is done, events play the whole
sample.

**Another buffer.** `sdgcross` looks up a second sample by name, puts it in `~sdgbuf2`, and the
SynthDesc's `msgFunc` picks it up from there, since `sdgbuf2` is a control.

## Things that will surprise you

- **On a player, `note` and `accelerate` are time, not pitch.** A sample's sound event carries
  `note: 0`, and SuperDirt shortens `sustain` by the note's ratio, as for resampling. The rate keeps
  in step by having `freq` in it. Pitch on `sdgwarp` is `sdgpitch` only.
- **`Warp1`'s pointer is 0 to 1 of the buffer,** so divide the frame phase by `BufFrames`, not by
  `BufSampleRate`, which is only right for a one-second file.
- **`Warp1` runs about 12 dB hotter than `dirt_sample`** at `overlaps: 8` (rms 0.51 against 0.13 on
  noise). `sdgwarp` and `playGrain` carry that.
- **`GrainBuf` reads mono files only,** so `sdgcloud` is made of `BufRd`.
- **`Index` reads a buffer of any width** as raw interleaved samples, which is how `sdgcross` reads a
  second file of unknown width without a channel mismatch. `BufRd` wants the width in advance.
- **A repeated grain has a pitch only while the content holds still.** `sdgscan` at 220 Hz measured
  112 Hz until its position moved in steps (`sdgstep`).
- **An FFT adds its window's length as latency:** about 46 ms on `sdgfreeze` and `sdgcross`, about 93 ms
  on `sdgsmear`. A player comes out that late, against a dry sample on the same step.

How these are known is in [superdirt-notes.md](superdirt-notes.md).

## From code

```supercollider
~sdg.samplePlayerNames;           // in the order they are tried
~sdg.getSamplePlayer(\sdgwarp);
~sdg.removeSamplePlayer(\sdgwarp);
~sdg.reload;                      // the files again
```

A player without a `synthFunc` only names SynthDefs defined elsewhere: `addSamplePlayer(\mine, nil,
{ ~mine.notNil }, \otherdefs)` reads `otherdefs_<1..2>_<channels>`.
