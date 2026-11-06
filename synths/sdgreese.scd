// synths/sdgreese.scd - the reese bass of drum and bass: detuned saws that beat
// against each other, a clean sine holding the bottom, some drive.
//
// parameters:
//   sdgdetune  0 to 1 semitones  how far apart the saws are (0.1 slow, 0.5 nasty)
//   sdgsub     0 to 1            how much of the sine
//   sdgcut     30 to 18000 Hz    the low pass with its envelope closed
//   sdgq       0 to 1            resonance
//   sdgenv     0 to 1            how far each note opens the filter (6 octaves at 1)
//   sdgdecay   0.01 to 2 s       how fast it closes again
//   sdgdrive   0 to 1            saturation
//
// presets, by n: 0 reese, 1 slow beat, 2 nasty, 3 sub heavy, 4 growl, 5 bright,
// 6 pluck, 7 wide, 8 clean, 9 acid. a pattern's n picks one of them and note plays the
// pitch: s "sdgreese:1" # note "0 7".
//
//   d1 $ note "a2 ~ ~ a2 ~ ~ c3 ~" # s "sdgreese" # legato 1
//   d1 $ note "e2" # s "sdgreese" # legato 1 # pF "sdgcut" (slow 4 $ range 200 2000 sine)
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdgreese, { |out, sustain = 0.5, pan = 0.5, freq = 55, accelerate = 0,
	sdgdetune = 0.2, sdgsub = 0.6, sdgcut = 500, sdgq = 0.2, sdgenv = 0.2, sdgdecay = 0.2, sdgdrive = 0.4|
	var env = EnvGen.ar(Env([0, 1, 1, 0], [0.004, (sustain - 0.024).max(0.001), 0.02]), doneAction: 2);
	var sweep = EnvGen.ar(Env.perc(0.001, sdgdecay, 1, -4));
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var cutoff = sdgcut * (2 ** (sdgenv * 6 * sweep));
	var saws = [
		Saw.ar(pitch * sdgdetune.midiratio) + Saw.ar(pitch * (sdgdetune * -0.6).midiratio),
		Saw.ar(pitch * sdgdetune.neg.midiratio) + Saw.ar(pitch * (sdgdetune * 0.6).midiratio)
	] * 0.5;
	var sig = MoogFF.ar(saws, cutoff.clip(30, 18000), sdgq.clip(0, 1) * 3.6);
	sig = sig + (SinOsc.ar(pitch) * sdgsub); // down the middle, so the low end stays mono
	sig = (sig * (1 + (sdgdrive * 3))).tanh * 0.8;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "reese"),	// 0 reese: the def's defaults
	(presetName: "slow beat", sdgdetune: 0.08, sdgsub: 0.7, sdgcut: 400, sdgq: 0.15, sdgenv: 0.1, sdgdrive: 0.3),	// 1 slow beat
	(presetName: "nasty", sdgdetune: 0.5, sdgsub: 0.4, sdgcut: 900, sdgq: 0.4, sdgenv: 0.3, sdgdecay: 0.3, sdgdrive: 0.9),	// 2 nasty
	(presetName: "sub heavy", sdgdetune: 0.15, sdgsub: 1, sdgcut: 250, sdgq: 0.1, sdgenv: 0.1, sdgdrive: 0.2),	// 3 sub heavy
	(presetName: "growl", sdgdetune: 0.35, sdgsub: 0.5, sdgcut: 300, sdgq: 0.75, sdgenv: 0.7, sdgdecay: 0.4, sdgdrive: 0.8),	// 4 growl
	(presetName: "bright", sdgdetune: 0.25, sdgsub: 0.3, sdgcut: 2500, sdgdrive: 0.5),	// 5 bright
	(presetName: "pluck", sdgdetune: 0.15, sdgcut: 200, sdgq: 0.4, sdgenv: 0.8, sdgdecay: 0.12),	// 6 pluck
	(presetName: "wide", sdgdetune: 1, sdgsub: 0.5, sdgcut: 800, sdgdrive: 0.5),	// 7 wide
	(presetName: "clean", sdgdetune: 0.1, sdgsub: 0.8, sdgcut: 600, sdgq: 0, sdgenv: 0, sdgdrive: 0),	// 8 clean
	(presetName: "acid", sdgsub: 0.4, sdgcut: 350, sdgq: 0.85, sdgenv: 0.6, sdgdecay: 0.25, sdgdrive: 0.6)	// 9 acid
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgreese, (instrument: \sdgreese, note: 0) ++ preset, appendToExisting: i > 0)
};
)
