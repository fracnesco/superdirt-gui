// synths/sdgacid.scd - a 303-like voice: one oscillator, a resonant low pass, and
// an envelope that opens the filter on each note. plucks and arpeggios too.
//
// parameters:
//   sdgcut     30 to 18000 Hz       the filter with its envelope closed
//   sdgq       0 to 1               resonance; near 1 it squelches
//   sdgenv     0 to 1               how far each note opens the filter (6 octaves at 1)
//   sdgdecay   0.01 to 2 s          how fast it closes again
//   sdgaccent  0 to 1               louder, brighter and shorter, the 303's accent
//   sdgslide   -24 to 24 semitones  a note starts this far away and glides home
//   sdgglide   0.01 to 1 s          how long that glide takes
//   sdgwave    0 to 1               0 a saw, 1 a square
//
// presets, by n: 0 303, 1 squelch, 2 square, 3 deep rubber, 4 plucky, 5 screamer,
// 6 open, 7 muted, 8 long decay, 9 slow glide. a pattern's n picks one of them and note
// plays the pitch: s "sdgacid:1" # note "0 7".
//
// a synth forgets the note before it, so a slide is written as the distance
// back to that note: after a2, a3 with sdgslide -12 glides up from a2.
//
//   d1 $ note "a2 a2 a3 a2" # s "sdgacid" # legato 0.6 # pF "sdgq" 0.8
//   d1 $ note "a2 a3" # s "sdgacid" # legato 1 # pF "sdgslide" "0 -12"
//
// examples/tidal/lessons/11-acid.tidal plays it.
(
SynthDef(\sdgacid, { |out, sustain = 0.2, pan = 0.5, freq = 110, accelerate = 0,
	sdgcut = 300, sdgq = 0.6, sdgenv = 0.6, sdgdecay = 0.15, sdgaccent = 0,
	sdgslide = 0, sdgglide = 0.06, sdgwave = 0|
	var env = EnvGen.ar(Env([0, 1, 1, 0], [0.002, (sustain - 0.012).max(0.001), 0.01]), doneAction: 2);
	var sweep = EnvGen.ar(Env.perc(0.001, sdgdecay * (1 - (sdgaccent * 0.5)), 1, -4));
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain) * XLine.kr(sdgslide.midiratio, 1, sdgglide);
	var cutoff = sdgcut * (2 ** ((sdgenv + (sdgaccent * 0.4)) * 6 * sweep));
	var sig = XFade2.ar(Saw.ar(pitch), Pulse.ar(pitch), sdgwave * 2 - 1);
	sig = MoogFF.ar(sig, cutoff.clip(30, 18000), sdgq.clip(0, 1) * 3.6);
	sig = (sig * (1.5 + sdgq + sdgaccent)).tanh * (1.65 + (sdgaccent * 0.9));
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "303"),	// 0 303: the def's defaults
	(presetName: "squelch", sdgcut: 200, sdgq: 0.85, sdgenv: 0.85, sdgdecay: 0.25),	// 1 squelch
	(presetName: "square", sdgcut: 350, sdgq: 0.7, sdgenv: 0.6, sdgdecay: 0.2, sdgwave: 1),	// 2 square
	(presetName: "deep rubber", sdgcut: 120, sdgq: 0.3, sdgenv: 0.35, sdgdecay: 0.3),	// 3 deep rubber
	(presetName: "plucky", sdgcut: 500, sdgq: 0.4, sdgenv: 0.7, sdgdecay: 0.06, sdgaccent: 0.2),	// 4 plucky
	(presetName: "screamer", sdgcut: 400, sdgq: 0.85, sdgenv: 1, sdgdecay: 0.4, sdgaccent: 0.4),	// 5 screamer
	(presetName: "open", sdgcut: 2500, sdgq: 0.5, sdgenv: 0.3, sdgdecay: 0.5, sdgwave: 0.5),	// 6 open
	(presetName: "muted", sdgcut: 180, sdgq: 0.2, sdgenv: 0.2, sdgdecay: 0.1, sdgwave: 0.3),	// 7 muted
	(presetName: "long decay", sdgcut: 250, sdgq: 0.8, sdgenv: 0.75, sdgdecay: 1.2),	// 8 long decay
	(presetName: "slow glide", sdgcut: 600, sdgq: 0.6, sdgenv: 0.5, sdgdecay: 0.3, sdgglide: 0.25, sdgwave: 0.2)	// 9 slow glide
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgacid, (instrument: \sdgacid, note: 0) ++ preset, appendToExisting: i > 0)
};
)
