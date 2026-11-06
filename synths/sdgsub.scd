// synths/sdgsub.scd - a sub bass: a sine, a quick drop in pitch to give it a
// front, and drive for the harmonics that let small speakers hear it.
//
// parameters:
//   sdgpunch  0 to 24 semitones  how far above the note it starts
//   sdgdecay  0.01 to 0.5 s      how fast it falls to the note
//   sdgdrive  0 to 1             saturation, 0 a clean sine, 1 nearly a square
//
// presets, by n: 0 sub, 1 clean sine, 2 808 bass, 3 punchy, 4 dirty, 5 square, 6 slow
// drop, 7 click, 8 warm, 9 dive. a pattern's n picks one of them and note plays the
// pitch: s "sdgsub:1" # note "0 7".
//
//   d1 $ note "a2 ~ a2 c3" # s "sdgsub" # legato 0.9
//   d1 $ note "e2*2" # s "sdgsub" # legato 1 # pF "sdgpunch" 24 # pF "sdgdrive" 0.5
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdgsub, { |out, sustain = 0.5, pan = 0.5, freq = 55, accelerate = 0,
	sdgpunch = 7, sdgdecay = 0.04, sdgdrive = 0.2|
	var env = EnvGen.ar(Env([0, 1, 1, 0], [0.002, (sustain - 0.022).max(0.001), 0.02]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain) * XLine.kr(sdgpunch.midiratio, 1, sdgdecay);
	var sig = SinOsc.ar(pitch);
	sig = (sig * (1 + (sdgdrive * 6))).tanh;
	sig = LPF.ar(sig, 1500) * 0.8;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "sub"),	// 0 sub: the def's defaults
	(presetName: "clean sine", sdgpunch: 0, sdgdrive: 0),	// 1 clean sine
	(presetName: "808 bass", sdgpunch: 12, sdgdecay: 0.08, sdgdrive: 0.3),	// 2 808 bass
	(presetName: "punchy", sdgpunch: 24, sdgdecay: 0.03, sdgdrive: 0.4),	// 3 punchy
	(presetName: "dirty", sdgpunch: 7, sdgdecay: 0.05, sdgdrive: 0.8),	// 4 dirty
	(presetName: "square", sdgpunch: 0, sdgdrive: 1),	// 5 square
	(presetName: "slow drop", sdgpunch: 12, sdgdecay: 0.3, sdgdrive: 0.2),	// 6 slow drop
	(presetName: "click", sdgpunch: 24, sdgdecay: 0.01, sdgdrive: 0.1),	// 7 click
	(presetName: "warm", sdgpunch: 3, sdgdecay: 0.06, sdgdrive: 0.5),	// 8 warm
	(presetName: "dive", sdgpunch: 24, sdgdecay: 0.5, sdgdrive: 0.3)	// 9 dive
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgsub, (instrument: \sdgsub, note: 0) ++ preset, appendToExisting: i > 0)
};
)
