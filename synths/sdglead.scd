// synths/sdglead.scd - a mono synth lead: two saws and a square an octave down,
// a filter that opens on each note, vibrato that comes in late.
//
// parameters:
//   sdgdetune    0 to 1               the two saws apart (1 is a quarter tone each way)
//   sdgsquare    0 to 1               how much of the square an octave down
//   sdgcut       30 to 18000 Hz       the low pass with its envelope closed
//   sdgq         0 to 1               resonance
//   sdgenv       0 to 1               how far each note opens the filter (6 octaves at 1)
//   sdgdecay     0.01 to 2 s          how fast it closes again
//   sdgdrive     0 to 1               saturation
//   sdgvib       0 to 1 semitones     vibrato depth
//   sdgrate      0.1 to 12 Hz         vibrato speed
//   sdgvibdelay  0 to 2 s             how long a note holds before the vibrato comes in
//   sdgslide     -24 to 24 semitones  a note starts this far away and glides home
//   sdgglide     0.01 to 1 s          how long that glide takes
//
// presets, by n: 0 mono lead, 1 fat saw, 2 singing, 3 squelchy, 4 soft, 5 screaming,
// 6 bass lead, 7 brass, 8 pluck, 9 slow glide. a pattern's n picks one of them and note
// plays the pitch: s "sdglead:1" # note "0 7".
//
// as on sdgacid a slide is the distance back to the note before.
//
//   d1 $ note "a5 c6 e6 d6" # s "sdglead" # legato 1 # pF "sdgvib" 0.3
//   d1 $ note "a5 e6" # s "sdglead" # legato 1 # pF "sdgslide" "0 -7"
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdglead, { |out, sustain = 0.5, pan = 0.5, freq = 440, accelerate = 0,
	sdgdetune = 0.2, sdgsquare = 0.2, sdgcut = 1200, sdgq = 0.3, sdgenv = 0.4, sdgdecay = 0.25,
	sdgdrive = 0.3, sdgvib = 0, sdgrate = 5.5, sdgvibdelay = 0.25, sdgslide = 0, sdgglide = 0.06|
	var env = EnvGen.ar(Env([0, 1, 1, 0], [0.004, (sustain - 0.024).max(0.001), 0.02]), doneAction: 2);
	var sweep = EnvGen.ar(Env.perc(0.001, sdgdecay, 1, -4));
	var wobble = SinOsc.kr(sdgrate) * sdgvib * EnvGen.kr(Env([0, 0, 1], [sdgvibdelay, 0.2]));
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain) * XLine.kr(sdgslide.midiratio, 1, sdgglide) * wobble.midiratio;
	var cutoff = sdgcut * (2 ** (sdgenv * 6 * sweep));
	var sig = Saw.ar(pitch * (sdgdetune * 0.5).midiratio) + Saw.ar(pitch * (sdgdetune * -0.5).midiratio);
	sig = (sig * 0.5) + (Pulse.ar(pitch * 0.5) * sdgsquare);
	sig = MoogFF.ar(sig, cutoff.clip(30, 18000), sdgq.clip(0, 1) * 3.6);
	sig = (sig * (1 + (sdgdrive * 4))).tanh * 0.8;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "mono lead"),	// 0 mono lead: the def's defaults
	(presetName: "fat saw", sdgdetune: 0.6, sdgsquare: 0.4, sdgcut: 2500, sdgq: 0.2, sdgenv: 0.3, sdgdecay: 0.3, sdgdrive: 0.4),	// 1 fat saw
	(presetName: "singing", sdgdetune: 0.15, sdgsquare: 0.1, sdgcut: 1800, sdgq: 0.3, sdgenv: 0.2, sdgdecay: 0.5, sdgdrive: 0.2, sdgvib: 0.35, sdgrate: 5.5, sdgvibdelay: 0.3),	// 2 singing
	(presetName: "squelchy", sdgcut: 400, sdgq: 0.85, sdgenv: 0.8, sdgdecay: 0.2, sdgdrive: 0.5),	// 3 squelchy
	(presetName: "soft", sdgdetune: 0.05, sdgsquare: 0.3, sdgcut: 700, sdgq: 0.1, sdgenv: 0.15, sdgdecay: 0.4, sdgdrive: 0, sdgvib: 0.15, sdgrate: 4.5, sdgvibdelay: 0.4),	// 4 soft
	(presetName: "screaming", sdgdetune: 0.4, sdgsquare: 0.3, sdgcut: 3500, sdgq: 0.6, sdgenv: 0.4, sdgdecay: 0.25, sdgdrive: 0.7),	// 5 screaming
	(presetName: "bass lead", sdgdetune: 0.1, sdgsquare: 0.8, sdgcut: 600, sdgq: 0.3, sdgenv: 0.5, sdgdecay: 0.15, sdgdrive: 0.15),	// 6 bass lead
	(presetName: "brass", sdgdetune: 0.3, sdgsquare: 0.2, sdgcut: 900, sdgq: 0.15, sdgenv: 0.6, sdgdecay: 0.6, sdgdrive: 0.3),	// 7 brass
	(presetName: "pluck", sdgcut: 500, sdgq: 0.5, sdgenv: 0.9, sdgdecay: 0.08, sdgdrive: 0.3),	// 8 pluck
	(presetName: "slow glide", sdgdetune: 0.2, sdgglide: 0.35, sdgvib: 0.2, sdgvibdelay: 0.5, sdgcut: 1500)	// 9 slow glide
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdglead, (instrument: \sdglead, note: 0) ++ preset, appendToExisting: i > 0)
};
)
