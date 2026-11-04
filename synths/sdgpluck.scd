// synths/sdgpluck.scd - a plucked string: a burst of noise rings in a short delay
// (Karplus-Strong). guitars, harps, kalimbas, depending on the two knobs.
//
// parameters:
//   sdgdecay   0.1 to 10 s  how long the string rings
//   sdgdamp    0 to 1       how fast the highs die, 0 bright to 1 dull
//   sdgbright  0 to 1       how sharp the pluck is, 0 soft to 1 hard
//
// a note ends at the end of its sustain whatever is still ringing, so let it
// have some legato.
//
//   d1 $ note (arp "up" "a'min7'8") # s "sdgpluck" # legato 2
//   d1 $ note (arp "updown" "a'min7'8") # s "sdgpluck" # legato 2 # pF "sdgdamp" 0.7
//
// examples/tidal/14-synths.tidal plays it.
(
SynthDef(\sdgpluck, { |out, sustain = 1, pan = 0.5, freq = 440, accelerate = 0,
	sdgdecay = 2.5, sdgdamp = 0.3, sdgbright = 0.6|
	var env = EnvGen.ar(Env([1, 1, 0], [(sustain - 0.02).max(0.001), 0.02]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var damp = sdgdamp.clip(0, 0.99);
	// the string's filter makes it longer by its phase delay at the note: take that off or it goes flat
	var w = 2pi * pitch.max(20) * SampleDur.ir;
	var lag = atan2(damp * sin(w), 1 - (damp * cos(w))) / w;
	var delay = (pitch.max(20).reciprocal - (lag * SampleDur.ir)).max(SampleDur.ir * 2);
	var burst = WhiteNoise.ar * EnvGen.ar(Env.perc(0.001, freq.reciprocal * 2, 1, -2));
	var sig = LPF.ar(burst, 800 * (2 ** (sdgbright.clip(0, 1) * 4.5)));
	sig = Pluck.ar(sig, Impulse.kr(0), 0.05, delay, sdgdecay, damp);
	sig = LeakDC.ar(sig);
	sig = (sig * 5).tanh * 0.9; // the pluck's edge saturates, the string underneath comes up
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
