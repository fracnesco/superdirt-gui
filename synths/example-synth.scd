// synths/example-synth.scd - a template. copy it, rename it, edit it.
//
// one SynthDef per tidal sound. SuperDirt hands the synth every argument it
// declares that the event carries: out (the orbit's bus, always), sustain, pan,
// freq (from n or note), accelerate, speed, begin, end, and any custom key you
// send from tidal with pF or pI. no gain here: SuperDirt applies it after us.
// DirtPan spreads the signal over the orbit's channels and applies the envelope.
//
//   d1 $ s "sdgsine" # n "0 7 12" # sustain 0.4
//   d1 $ s "sdgsine" # pF "sdgshape" 0.8
(
SynthDef(\sdgsine, { |out, sustain = 1, pan = 0.5, freq = 440, accelerate = 0, sdgshape = 0.2|
	var env = EnvGen.ar(Env.perc(0.01, 0.99, 1, -4), timeScale: sustain, doneAction: 2);
	var glide = Line.kr(1, 1 + accelerate, sustain);
	var sig = SinOsc.ar(freq * glide);
	sig = (sig * (1 + (sdgshape * 6))).tanh; // more harmonics the higher sdgshape goes
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
