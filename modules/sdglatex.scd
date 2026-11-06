// modules/sdglatex.scd - a squelch on every hit: a resonant filter diving with it.
//
// SOPHIE's latex sounds: a low pass with a sharp resonance whose cutoff falls
// from high to sdglatexfreq in sdglatextime on every hit it finds, driven into
// a soft clip. short times (5 to 50 ms) are a rubbery click, longer ones a
// wet zap; sdglatexfreq is where the resonance lands, so it gives the hit a
// pitch.
//
// parameters:
//   sdglatex      0 to 1          how much: the squelch against the dry hit, and its drive
//   sdglatexfreq  40 to 2000 Hz   where the cutoff lands
//   sdglatextime  0.005 to 0.3 s  how long the dive takes
//
//   d1 $ s "bd*4" # pF "sdglatex" 0.7 # pF "sdglatexfreq" 80
//   d1 $ s "hh*8" # pF "sdglatex" 0.8 # pF "sdglatexfreq" "200 400" # pF "sdglatextime" 0.02
//   d1 $ s "cp*4" # pF "sdglatex" 1 # pF "sdglatextime" (range 0.01 0.2 sine)
(
var numChannels = ~dirt.numChannels;

~dirt.addModule('sdglatex', { |dirtEvent|
	dirtEvent.sendSynth("sdglatex" ++ numChannels, [
		sdglatex: ~sdglatex,
		sdglatexfreq: ~sdglatexfreq ? 120,
		sdglatextime: ~sdglatextime ? 0.04,
		out: ~out
	])
}, { ~sdglatex.notNil });

SynthDef("sdglatex" ++ numChannels, { |out, sdglatex = 0.5, sdglatexfreq = 120, sdglatextime = 0.04|
	var sig = In.ar(out, numChannels);
	var mono = sig.asArray.sum / numChannels;
	var fast = Amplitude.ar(mono, 0.0005, 0.03);
	var slow = Amplitude.ar(mono, 0.01, 0.2);
	var hit = Trig1.ar((fast > (slow * 2)) * (fast > 0.003), 0.06);
	var amount = sdglatex.clip(0, 1);
	var low = sdglatexfreq.clip(40, 2000);
	// the dive is in octaves, so it is heard: from 5 octaves up (at most 16 kHz) to the note
	var octaves = EnvGen.ar(Env([0, 5, 0], [0.0005, sdglatextime.clip(0.005, 0.3)], -3), hit);
	var cutoff = (low * (2 ** octaves)).clip(20, 16000);
	var wet = RLPF.ar(sig, cutoff, 0.08);
	var drive = 1 + (amount * 4);
	wet = (wet * drive).tanh / drive.sqrt;
	wet = LPF.ar(wet, 12000);
	// the resonance dies away into denormals after the hit: below -100 dB it is 0
	wet = wet * (slow > 1e-5);
	// equal power, and exactly 0 at the ends (XFade2 leaves 1e-17 of the other side: denormals)
	wet = (sig * (1 - amount).sqrt) + (wet * amount.sqrt);
	ReplaceOut.ar(out, (wet * 0.5).softclip * 2);
}).add;
)
