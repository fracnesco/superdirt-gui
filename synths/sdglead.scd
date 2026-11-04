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
// as on sdgacid a slide is the distance back to the note before.
//
//   d1 $ note "a5 c6 e6 d6" # s "sdglead" # legato 1 # pF "sdgvib" 0.3
//   d1 $ note "a5 e6" # s "sdglead" # legato 1 # pF "sdgslide" "0 -7"
//
// examples/tidal/14-synths.tidal plays it.
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
)
