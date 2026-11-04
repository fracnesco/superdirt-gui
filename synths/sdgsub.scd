// synths/sdgsub.scd - a sub bass: a sine, a quick drop in pitch to give it a
// front, and drive for the harmonics that let small speakers hear it.
//
// parameters:
//   sdgpunch  0 to 24 semitones  how far above the note it starts
//   sdgdecay  0.01 to 0.5 s      how fast it falls to the note
//   sdgdrive  0 to 1             saturation, 0 a clean sine, 1 nearly a square
//
//   d1 $ note "a2 ~ a2 c3" # s "sdgsub" # legato 0.9
//   d1 $ note "e2*2" # s "sdgsub" # legato 1 # pF "sdgpunch" 24 # pF "sdgdrive" 0.5
//
// examples/tidal/14-synths.tidal plays it.
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
)
