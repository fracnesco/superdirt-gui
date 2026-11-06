// synths/sdgsupersaw.scd - the trance and big-room stack: seven saws, detuned
// unevenly the way Roland's JP-8000 did it, spread across the stereo field.
//
// parameters:
//   sdgdetune  0 to 1          how far apart the saws are (1 is almost two semitones)
//   sdgmix     0 to 1          the six outer saws against the middle one
//   sdgwidth   0 to 1          0 mono, 1 as wide as it goes
//   sdgcut     30 to 18000 Hz  the low pass with its envelope closed
//   sdgq       0 to 1          resonance
//   sdgenv     0 to 1          how far each note opens the filter (6 octaves at 1)
//   sdgdecay   0.01 to 2 s     how fast it closes again
//   sdgatk     0 to 4 s        attack
//   sdgrel     0 to 4 s        release
//
// presets, by n: 0 trance, 1 big room, 2 subtle, 3 pluck, 4 pad, 5 mono, 6 filtered,
// 7 wide, 8 stab, 9 lead. a pattern's n picks one of them and note plays the pitch: s
// "sdgsupersaw:1" # note "0 7".
//
// SuperDirt ends the event at the end of sustain, so attack and release happen
// inside it: a chord that should ring out wants a long legato.
//
//   d1 $ note "a'min7 f'maj7" # s "sdgsupersaw" # legato 1
//   d1 $ note (arp "up" "a'min7'8") # s "sdgsupersaw" # pF "sdgdetune" 0.6 # pF "sdgrel" 0.3
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdgsupersaw, { |out, sustain = 1, pan = 0.5, freq = 440, accelerate = 0,
	sdgdetune = 0.35, sdgmix = 0.75, sdgwidth = 0.9, sdgcut = 7000, sdgq = 0.1, sdgenv = 0,
	sdgdecay = 0.3, sdgatk = 0.005, sdgrel = 0.05|
	var atk = sdgatk.max(0.001), rel = sdgrel.max(0.005);
	var fit = (sustain / (atk + rel)).min(1); // both have to fit inside sustain
	var env = EnvGen.ar(Env([0, 1, 1, 0], [atk * fit, (sustain - ((atk + rel) * fit)).max(0), rel * fit], [2, 0, -2]), doneAction: 2);
	var sweep = EnvGen.ar(Env.perc(0.001, sdgdecay, 1, -4));
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var cutoff = sdgcut * (2 ** (sdgenv * 6 * sweep));
	var sig = #[-0.11002313, -0.06288439, -0.01952356, 0, 0.01991221, 0.06216538, 0.10745242].collect { |offset, i|
		var saw = Saw.ar(pitch * (1 + (offset * sdgdetune)));
		// Saw has no phase input: without this every note starts with all seven in line
		saw = DelayN.ar(saw, 0.025, Rand(0, freq.reciprocal.min(0.025)));
		saw * if(i == 3) { 1 - (sdgmix * 0.5) } { sdgmix };
	};
	sig = Splay.ar(sig, sdgwidth);
	sig = HPF.ar(sig, pitch * 0.7); // what makes it a supersaw and not a mud of saws
	sig = MoogFF.ar(sig, cutoff.clip(30, 18000), sdgq.clip(0, 1) * 3.6);
	sig = (sig * 1.2).tanh * 1.3;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "trance"),	// 0 trance: the def's defaults
	(presetName: "big room", sdgdetune: 0.5, sdgmix: 0.9, sdgwidth: 1, sdgcut: 9000, sdgrel: 0.2),	// 1 big room
	(presetName: "subtle", sdgdetune: 0.12, sdgmix: 0.5, sdgwidth: 0.6, sdgcut: 5000),	// 2 subtle
	(presetName: "pluck", sdgdetune: 0.3, sdgmix: 0.7, sdgwidth: 0.8, sdgcut: 700, sdgq: 0.3, sdgenv: 0.8, sdgdecay: 0.15),	// 3 pluck
	(presetName: "pad", sdgdetune: 0.3, sdgmix: 0.8, sdgwidth: 1, sdgcut: 3000, sdgatk: 1, sdgrel: 1.5),	// 4 pad
	(presetName: "mono", sdgdetune: 0.4, sdgmix: 0.8, sdgwidth: 0, sdgcut: 6000),	// 5 mono
	(presetName: "filtered", sdgcut: 2000, sdgq: 0.4, sdgenv: 0.3, sdgdecay: 0.5),	// 6 filtered
	(presetName: "wide", sdgdetune: 1, sdgmix: 1, sdgwidth: 1, sdgcut: 4000, sdgq: 0.2),	// 7 wide
	(presetName: "stab", sdgdetune: 0.4, sdgmix: 0.8, sdgcut: 2000, sdgq: 0.2, sdgenv: 0.6, sdgdecay: 0.12, sdgatk: 0.002),	// 8 stab
	(presetName: "lead", sdgdetune: 0.25, sdgmix: 0.6, sdgwidth: 0.7, sdgcut: 8000, sdgq: 0.2, sdgenv: 0.2, sdgdecay: 0.4, sdgatk: 0.01, sdgrel: 0.15)	// 9 lead
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgsupersaw, (instrument: \sdgsupersaw, note: 0) ++ preset, appendToExisting: i > 0)
};
)
