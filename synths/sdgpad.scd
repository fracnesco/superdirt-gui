// synths/sdgpad.scd - a warm analog pad: saws and pulses that drift against each
// other, through a low pass that moves slowly and differently in each ear.
//
// parameters:
//   sdgdetune  0 to 1          how far the oscillators drift apart
//   sdgcut     30 to 18000 Hz  the low pass
//   sdgq       0 to 1          resonance
//   sdgmove    0 to 3 octaves  how far the filter moves, either way
//   sdgrate    0.01 to 2 Hz    how fast it moves
//   sdgatk     0 to 8 s        attack
//   sdgrel     0 to 8 s        release
//
// presets, by n: 0 warm, 1 dark, 2 bright, 3 sweep, 4 wide, 5 resonant motion, 6 slow
// swell, 7 stab, 8 ambient drift, 9 tight chords. a pattern's n picks one of them and
// note plays the pitch: s "sdgpad:1" # note "0 7".
//
// SuperDirt ends the event at the end of sustain, so attack and release happen
// inside it, shortened together if a note is too short for both. Chords want
// legato 1 and a slow pattern.
//
//   d1 $ slow 2 $ note "a'min7 f'maj7" # s "sdgpad" # legato 1
//   d1 $ slow 4 $ note "c'maj9" # s "sdgpad" # legato 1 # pF "sdgcut" (slow 8 $ range 400 2500 sine)
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdgpad, { |out, sustain = 2, pan = 0.5, freq = 440, accelerate = 0,
	sdgdetune = 0.3, sdgcut = 1400, sdgq = 0.15, sdgmove = 0.5, sdgrate = 0.15, sdgatk = 0.6, sdgrel = 0.8|
	var atk = sdgatk.max(0.001), rel = sdgrel.max(0.005);
	var fit = (sustain / (atk + rel)).min(1);
	var env = EnvGen.ar(Env([0, 1, 1, 0], [atk * fit, (sustain - ((atk + rel) * fit)).max(0), rel * fit], [2, 0, -2]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var side = { |spread|
		var drift = { LFNoise2.kr(0.3) * sdgdetune * 0.08 } ! 3;
		var osc = Saw.ar(pitch * (spread[0] * sdgdetune * 0.3 + drift[0]).midiratio)
		+ Saw.ar(pitch * (spread[1] * sdgdetune * 0.3 + drift[1]).midiratio)
		+ (Pulse.ar(pitch * drift[2].midiratio, SinOsc.kr(0.2, Rand(0, 2pi)).range(0.3, 0.7)) * 0.5);
		MoogFF.ar(osc * 0.5, // the filter closes a little with the release
			(sdgcut * (2 ** (SinOsc.kr(sdgrate, Rand(0, 2pi)) * sdgmove)) * (env * 0.5 + 0.5)).clip(30, 18000),
			sdgq.clip(0, 1) * 3.6)
	};
	var sig = [side.(#[-1, 0.5]), side.(#[1, -0.5])];
	sig = (sig * 1.2).tanh * 1.5;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "warm"),	// 0 warm: the def's defaults
	(presetName: "dark", sdgdetune: 0.25, sdgcut: 650, sdgq: 0.1, sdgmove: 0.3, sdgrate: 0.08, sdgatk: 1.2, sdgrel: 1.5),	// 1 dark
	(presetName: "bright", sdgdetune: 0.35, sdgcut: 5000, sdgq: 0.1, sdgmove: 0.4, sdgrate: 0.2, sdgatk: 0.4),	// 2 bright
	(presetName: "sweep", sdgcut: 800, sdgq: 0.5, sdgmove: 2, sdgrate: 0.05, sdgatk: 1, sdgrel: 1.5),	// 3 sweep
	(presetName: "wide", sdgdetune: 0.9, sdgcut: 2000, sdgmove: 0.5, sdgrate: 0.2),	// 4 wide
	(presetName: "resonant motion", sdgdetune: 0.2, sdgcut: 1500, sdgq: 0.65, sdgmove: 1.2, sdgrate: 0.6),	// 5 resonant motion
	(presetName: "slow swell", sdgcut: 1200, sdgmove: 0.6, sdgrate: 0.1, sdgatk: 6, sdgrel: 6),	// 6 slow swell
	(presetName: "stab", sdgcut: 2500, sdgq: 0.2, sdgmove: 0.1, sdgatk: 0.01, sdgrel: 0.25),	// 7 stab
	(presetName: "ambient drift", sdgdetune: 0.6, sdgcut: 800, sdgq: 0.3, sdgmove: 1.5, sdgrate: 0.03, sdgatk: 3, sdgrel: 5),	// 8 ambient drift
	(presetName: "tight chords", sdgdetune: 0.1, sdgcut: 1600, sdgq: 0.2, sdgmove: 0, sdgatk: 0.05, sdgrel: 0.4)	// 9 tight chords
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgpad, (instrument: \sdgpad, note: 0) ++ preset, appendToExisting: i > 0)
};
)
