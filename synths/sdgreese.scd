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
//   d1 $ note "a2 ~ ~ a2 ~ ~ c3 ~" # s "sdgreese" # legato 1
//   d1 $ note "e2" # s "sdgreese" # legato 1 # pF "sdgcut" (slow 4 $ range 200 2000 sine)
//
// examples/tidal/14-synths.tidal plays it.
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
)
