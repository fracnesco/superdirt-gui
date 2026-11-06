// synths/sdgstrings.scd - a string machine: one bright saw an octave doubled,
// thickened by a chorus of three modulated delays, the sound of the Solina.
//
// parameters:
//   sdgchorus  0 to 1          how deep the chorus goes
//   sdgcut     30 to 18000 Hz  the low pass
//   sdgbright  0 to 1          how much of the octave above
//   sdgatk     0 to 4 s        attack
//   sdgrel     0 to 4 s        release
//
// presets, by n: 0 solina, 1 dark, 2 bright, 3 slow, 4 stab, 5 dry, 6 lush, 7 cello,
// 8 violins, 9 pad. a pattern's n picks one of them and note plays the pitch: s
// "sdgstrings:1" # note "0 7".
//
// attack and release happen inside sustain, shortened together if a note is too
// short for both.
//
//   d1 $ slow 2 $ note "c'maj7 a'min7" # s "sdgstrings" # legato 1
//   d1 $ slow 2 $ note "c'maj7 a'min7" # s "sdgstrings" # legato 1 # pF "sdgchorus" 1 # pF "sdgatk" 1
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdgstrings, { |out, sustain = 2, pan = 0.5, freq = 440, accelerate = 0,
	sdgchorus = 0.6, sdgcut = 5000, sdgbright = 0.5, sdgatk = 0.25, sdgrel = 0.5|
	var atk = sdgatk.max(0.001), rel = sdgrel.max(0.005);
	var fit = (sustain / (atk + rel)).min(1);
	var env = EnvGen.ar(Env([0, 1, 1, 0], [atk * fit, (sustain - ((atk + rel) * fit)).max(0), rel * fit], [2, 0, -2]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var dry = Saw.ar(pitch) + (Saw.ar(pitch * 2) * sdgbright);
	var lines = 3.collect { |i|
		// a slow sweep and a fast shimmer, a third of a turn apart
		var phase = i * 2pi / 3;
		var wobble = (SinOsc.kr(0.6, phase) * 0.002) + (SinOsc.kr(6.3, phase) * 0.0003);
		DelayL.ar(dry, 0.02, 0.009 + (wobble * sdgchorus))
	};
	var sig = [lines[0] + (lines[1] * 0.6), lines[2] + (lines[1] * 0.6)];
	sig = HPF.ar(sig, 180);
	sig = LPF.ar(sig, sdgcut.clip(30, 18000));
	sig = (sig * 0.5).tanh * 0.85;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "solina"),	// 0 solina: the def's defaults
	(presetName: "dark", sdgchorus: 0.5, sdgcut: 1500, sdgbright: 0.2, sdgatk: 0.6, sdgrel: 1),	// 1 dark
	(presetName: "bright", sdgchorus: 0.7, sdgcut: 10000, sdgbright: 0.9, sdgatk: 0.15, sdgrel: 0.6),	// 2 bright
	(presetName: "slow", sdgcut: 4000, sdgbright: 0.4, sdgatk: 2.5, sdgrel: 3),	// 3 slow
	(presetName: "stab", sdgchorus: 0.4, sdgcut: 6000, sdgbright: 0.6, sdgatk: 0.01, sdgrel: 0.2),	// 4 stab
	(presetName: "dry", sdgchorus: 0, sdgcut: 4000, sdgbright: 0.7, sdgatk: 0.01, sdgrel: 0.1),	// 5 dry
	(presetName: "lush", sdgchorus: 1, sdgbright: 0.6, sdgatk: 0.4, sdgrel: 1.5),	// 6 lush
	(presetName: "cello", sdgchorus: 0.3, sdgcut: 1200, sdgbright: 0.1, sdgatk: 0.3, sdgrel: 0.6),	// 7 cello
	(presetName: "violins", sdgchorus: 0.7, sdgcut: 7000, sdgbright: 1, sdgatk: 0.3, sdgrel: 0.8),	// 8 violins
	(presetName: "pad", sdgchorus: 0.8, sdgcut: 2500, sdgbright: 0.3, sdgatk: 1.2, sdgrel: 2)	// 9 pad
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgstrings, (instrument: \sdgstrings, note: 0) ++ preset, appendToExisting: i > 0)
};
)
