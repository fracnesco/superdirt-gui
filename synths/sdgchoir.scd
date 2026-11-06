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
// presets, by n: 0 ahh, 1 ooh, 2 eeh, 3 cathedral, 4 whisper, 5 solo voice, 6 big
// chorus, 7 staccato, 8 slow swell, 9 robot. a pattern's n picks one of them and note
// plays the pitch: s "sdgchoir:1" # note "0 7".
//
// attack and release happen inside sustain, shortened together if a note is too
// short for both.
//
//   d1 $ slow 2 $ note "a'min7 f'maj7" # s "sdgchoir" # legato 1
//   d1 $ slow 2 $ note "a'min7" # s "sdgchoir" # legato 1 # pF "sdgvowel" "<0 3 4>"
//
// examples/tidal/lessons/14-synths.tidal plays it.
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
// the presets, by n: note plays the pitch, n picks one of these
[
	(presetName: "ahh"),	// 0 ahh: the def's defaults
	(presetName: "ooh", sdgvowel: 4, sdgbreath: 0.1, sdgdetune: 0.25, sdgvib: 0.06, sdgrate: 4.5, sdgatk: 0.6, sdgrel: 0.9),	// 1 ooh
	(presetName: "eeh", sdgvowel: 1, sdgbreath: 0.2, sdgdetune: 0.3, sdgvib: 0.08, sdgrate: 5),	// 2 eeh
	(presetName: "cathedral", sdgvowel: 3.7, sdgbreath: 0.05, sdgdetune: 0.5, sdgvib: 0.1, sdgrate: 4.2, sdgatk: 1.5, sdgrel: 2.5),	// 3 cathedral
	(presetName: "whisper", sdgvowel: 0.5, sdgbreath: 0.9, sdgdetune: 0.2, sdgvib: 0, sdgatk: 0.3, sdgrel: 0.6),	// 4 whisper
	(presetName: "solo voice", sdgvowel: 0, sdgbreath: 0.1, sdgdetune: 0, sdgvib: 0.35, sdgrate: 5.5, sdgatk: 0.15, sdgrel: 0.4),	// 5 solo voice
	(presetName: "big chorus", sdgvowel: 0, sdgbreath: 0.2, sdgdetune: 0.6, sdgvib: 0.12, sdgrate: 5, sdgatk: 0.5, sdgrel: 1),	// 6 big chorus
	(presetName: "staccato", sdgvowel: 0, sdgbreath: 0.25, sdgdetune: 0.3, sdgvib: 0, sdgatk: 0.01, sdgrel: 0.1),	// 7 staccato
	(presetName: "slow swell", sdgvowel: 3.8, sdgbreath: 0.3, sdgdetune: 0.4, sdgvib: 0.1, sdgrate: 3, sdgatk: 4, sdgrel: 4),	// 8 slow swell
	(presetName: "robot", sdgvowel: 1.5, sdgbreath: 0, sdgdetune: 0, sdgvib: 0, sdgatk: 0.05, sdgrel: 0.2)	// 9 robot
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\sdgchoir, (instrument: \sdgchoir, note: 0) ++ preset, appendToExisting: i > 0)
};
)
