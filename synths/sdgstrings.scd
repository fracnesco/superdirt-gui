// synths/sdgstrings.scd - a string machine: one bright saw an octave doubled,
// thickened by a chorus of three modulated delays, the sound of the Solina.
//
// parameters:
//   sdgchorus  0 to 1          how deep the chorus goes
//   sdgcut     30 to 18000 Hz  the low pass
//   sdgbright  0 to 1          how much of the octave above
//   sdgatk     0 to 4 s        attack
//   sdgrel     0 to 4 s        release
//
// attack and release happen inside sustain, shortened together if a note is too
// short for both.
//
//   d1 $ slow 2 $ note "c'maj7 a'min7" # s "sdgstrings" # legato 1
//   d1 $ slow 2 $ note "c'maj7 a'min7" # s "sdgstrings" # legato 1 # pF "sdgchorus" 1 # pF "sdgatk" 1
//
// examples/tidal/14-synths.tidal plays it.
(
SynthDef(\sdgstrings, { |out, sustain = 2, pan = 0.5, freq = 440, accelerate = 0,
	sdgchorus = 0.6, sdgcut = 5000, sdgbright = 0.5, sdgatk = 0.25, sdgrel = 0.5|
	var atk = sdgatk.max(0.001), rel = sdgrel.max(0.005);
	var fit = (sustain / (atk + rel)).min(1);
	var env = EnvGen.ar(Env([0, 1, 1, 0], [atk * fit, (sustain - ((atk + rel) * fit)).max(0), rel * fit], [2, 0, -2]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var dry = Saw.ar(pitch) + (Saw.ar(pitch * 2) * sdgbright);
	var lines = 3.collect { |i|
		// a slow sweep and a fast shimmer, a third of a turn apart
		var phase = i * 2pi / 3;
		var wobble = (SinOsc.kr(0.6, phase) * 0.002) + (SinOsc.kr(6.3, phase) * 0.0003);
		DelayL.ar(dry, 0.02, 0.009 + (wobble * sdgchorus))
	};
	var sig = [lines[0] + (lines[1] * 0.6), lines[2] + (lines[1] * 0.6)];
	sig = HPF.ar(sig, 180);
	sig = LPF.ar(sig, sdgcut.clip(30, 18000));
	sig = (sig * 0.5).tanh * 0.85;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
