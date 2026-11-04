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
//   d1 $ note "a4 c5 e5 g5" # s "sdgsync" # legato 1 # pF "sdgsweep" 5
//   d1 $ note "a4" # s "sdgsync" # legato 1 # pF "sdgsync" (range 1 6 sine)
//
// examples/tidal/14-synths.tidal plays it.
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
)
