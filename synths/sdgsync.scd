// synths/sdgsync.scd - a hard sync lead: one oscillator restarts another, and
// moving the second one's pitch makes the tearing, vocal sweep.
//
// parameters:
//   sdgsync   1 to 8 x             where the second oscillator sits, as a ratio of the note
//   sdgsweep  0 to 10 x            how far above that each note starts (0 is no sweep)
//   sdgdecay  0.01 to 2 s          how fast the sweep comes down
//   sdgcut    30 to 18000 Hz       a low pass, to take the edge off
//   sdgq      0 to 1               resonance
//   sdgvib    0 to 1 semitones     vibrato depth
//   sdgrate   0.1 to 12 Hz         vibrato speed
//   sdgslide  -24 to 24 semitones  a note starts this far away and glides home
//   sdgglide  0.01 to 1 s          how long that glide takes
//
// presets, by n: 0 sync lead, 1 tearing, 2 static, 3 vocal, 4 zap, 5 dark, 6 screamer,
// 7 slow sweep, 8 vibrato, 9 pluck. a pattern's n picks one of them and note plays the
// pitch: s "sdgsync:1" # note "0 7".
//
//   d1 $ note "a4 c5 e5 g5" # s "sdgsync" # legato 1 # pF "sdgsweep" 5
//   d1 $ note "a4" # s "sdgsync" # legato 1 # pF "sdgsync" (range 1 6 sine)
//
// examples/tidal/lessons/14-synths.tidal plays it.
(
SynthDef(\sdgsync, { |out, sustain = 0.5, pan = 0.5, freq = 440, accelerate = 0,
	sdgsync = 1.5, sdgsweep = 3, sdgdecay = 0.3, sdgcut = 7000, sdgq = 0.1,
	sdgvib = 0, sdgrate = 5.5, sdgslide = 0, sdgglide = 0.06|
	var env = EnvGen.ar(Env([0, 1, 1, 0], [0.003, (sustain - 0.023).max(0.001), 0.02]), doneAction: 2);
	var sweep = EnvGen.ar(Env.perc(0.001, sdgdecay, 1, -4));
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain) * XLine.kr(sdgslide.midiratio, 1, sdgglide) * (SinOsc.kr(sdgrate) * sdgvib).midiratio;
	var ratio = sdgsync.max(1) + (sdgsweep * sweep);
	var sig = SyncSaw.ar(pitch, pitch * ratio);
	sig = LeakDC.ar(sig);
	sig = RLPF.ar(sig, sdgcut.clip(30, 18000), 1 - (sdgq.clip(0, 1) * 0.9));
	sig = (sig * 1.2).tanh * 0.8;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "sync lead"),	// 0 sync lead: the def's defaults
	(presetName: "tearing", sdgsync: 1.2, sdgsweep: 8, sdgdecay: 0.6),	// 1 tearing
	(presetName: "static", sdgsync: 2.5, sdgsweep: 0),	// 2 static
	(presetName: "vocal", sdgsync: 1.8, sdgsweep: 2, sdgdecay: 0.8, sdgcut: 3000, sdgq: 0.3, sdgvib: 0.25, sdgrate: 5),	// 3 vocal
	(presetName: "zap", sdgsync: 1, sdgsweep: 10, sdgdecay: 0.08, sdgcut: 12000),	// 4 zap
	(presetName: "dark", sdgcut: 1500, sdgq: 0.4),	// 5 dark
	(presetName: "screamer", sdgsync: 4, sdgsweep: 4, sdgdecay: 0.5, sdgcut: 10000, sdgq: 0.5),	// 6 screamer
	(presetName: "slow sweep", sdgsync: 1.2, sdgsweep: 6, sdgdecay: 1.8, sdgcut: 5000),	// 7 slow sweep
	(presetName: "vibrato", sdgsync: 2, sdgsweep: 1, sdgvib: 0.4, sdgrate: 6),	// 8 vibrato
	(presetName: "pluck", sdgsync: 3, sdgsweep: 5, sdgdecay: 0.05, sdgcut: 4000, sdgq: 0.3)	// 9 pluck
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgsync, (instrument: \sdgsync, note: 0) ++ preset, appendToExisting: i > 0)
};
)
