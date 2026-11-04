// synths/sdgpad.scd - a warm analog pad: saws and pulses that drift against each
// other, through a low pass that moves slowly and differently in each ear.
//
// parameters:
//   sdgdetune  0 to 1          how far the oscillators drift apart
//   sdgcut     30 to 18000 Hz  the low pass
//   sdgq       0 to 1          resonance
//   sdgmove    0 to 3 octaves  how far the filter moves, either way
//   sdgrate    0.01 to 2 Hz    how fast it moves
//   sdgatk     0 to 8 s        attack
//   sdgrel     0 to 8 s        release
//
// SuperDirt ends the event at the end of sustain, so attack and release happen
// inside it, shortened together if a note is too short for both. Chords want
// legato 1 and a slow pattern.
//
//   d1 $ slow 2 $ note "a'min7 f'maj7" # s "sdgpad" # legato 1
//   d1 $ slow 4 $ note "c'maj9" # s "sdgpad" # legato 1 # pF "sdgcut" (slow 8 $ range 400 2500 sine)
//
// examples/tidal/14-synths.tidal plays it.
(
SynthDef(\sdgpad, { |out, sustain = 2, pan = 0.5, freq = 440, accelerate = 0,
	sdgdetune = 0.3, sdgcut = 1400, sdgq = 0.15, sdgmove = 0.5, sdgrate = 0.15, sdgatk = 0.6, sdgrel = 0.8|
	var atk = sdgatk.max(0.001), rel = sdgrel.max(0.005);
	var fit = (sustain / (atk + rel)).min(1);
	var env = EnvGen.ar(Env([0, 1, 1, 0], [atk * fit, (sustain - ((atk + rel) * fit)).max(0), rel * fit], [2, 0, -2]), doneAction: 2);
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var side = { |spread|
		var drift = { LFNoise2.kr(0.3) * sdgdetune * 0.08 } ! 3;
		var osc = Saw.ar(pitch * (spread[0] * sdgdetune * 0.3 + drift[0]).midiratio)
		+ Saw.ar(pitch * (spread[1] * sdgdetune * 0.3 + drift[1]).midiratio)
		+ (Pulse.ar(pitch * drift[2].midiratio, SinOsc.kr(0.2, Rand(0, 2pi)).range(0.3, 0.7)) * 0.5);
		MoogFF.ar(osc * 0.5, // the filter closes a little with the release
			(sdgcut * (2 ** (SinOsc.kr(sdgrate, Rand(0, 2pi)) * sdgmove)) * (env * 0.5 + 0.5)).clip(30, 18000),
			sdgq.clip(0, 1) * 3.6)
	};
	var sig = [side.(#[-1, 0.5]), side.(#[1, -0.5])];
	sig = (sig * 1.2).tanh * 1.5;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
