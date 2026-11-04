// synths/sdgchoir.scd - a choir pad: three slightly different voices through the
// formants of a vowel, with some breath on top.
//
// parameters:
//   sdgvowel   0 to 4            which vowel, 0 a, 1 e, 2 i, 3 o, 4 u; in between it morphs
//   sdgbreath  0 to 1            how much breath
//   sdgdetune  0 to 1            how far the voices disagree
//   sdgvib     0 to 1 semitones  vibrato depth
//   sdgrate    0.1 to 12 Hz      vibrato speed
//   sdgatk     0 to 4 s          attack
//   sdgrel     0 to 4 s          release
//
// attack and release happen inside sustain, shortened together if a note is too
// short for both.
//
//   d1 $ slow 2 $ note "a'min7 f'maj7" # s "sdgchoir" # legato 1
//   d1 $ slow 2 $ note "a'min7" # s "sdgchoir" # legato 1 # pF "sdgvowel" "<0 3 4>"
//
// examples/tidal/14-synths.tidal plays it.
(
SynthDef(\sdgchoir, { |out, sustain = 2, pan = 0.5, freq = 440, accelerate = 0,
	sdgvowel = 0, sdgbreath = 0.15, sdgdetune = 0.3, sdgvib = 0.08, sdgrate = 4.8, sdgatk = 0.5, sdgrel = 0.7|
	var atk = sdgatk.max(0.001), rel = sdgrel.max(0.005);
	var fit = (sustain / (atk + rel)).min(1);
	var env = EnvGen.ar(Env([0, 1, 1, 0], [atk * fit, (sustain - ((atk + rel) * fit)).max(0), rel * fit], [2, 0, -2]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var which = sdgvowel.clip(0, 4);
	// three formants of a, e, i, o, u, in Hz
	var formants = #[[800, 400, 300, 450, 325], [1150, 1700, 1870, 800, 700], [2800, 2600, 3000, 2830, 2530]].collect { |row|
		LinSelectX.kr(which, row)
	};
	var side = {
		var voices = 3.collect {
			var wobble = (SinOsc.kr(sdgrate * Rand(0.9, 1.1), Rand(0, 2pi)) * sdgvib) + (LFNoise2.kr(1.5) * sdgdetune * 0.15);
			Saw.ar(pitch * wobble.midiratio)
		}.sum * 0.4;
		var source = voices + (PinkNoise.ar * sdgbreath);
		var sung = [1, 0.5, 0.2].collect { |level, i| BPF.ar(source, formants[i], 0.12, level) }.sum;
		sung * 3
	};
	var sig = [side.value, side.value];
	sig = HPF.ar(sig, 120);
	sig = sig.tanh * 1.3;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
