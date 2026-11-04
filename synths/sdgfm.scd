// synths/sdgfm.scd - two-operator FM: electric piano, bells, metal, all from where
// you put the two numbers. the brightness dies faster than the note.
//
// parameters:
//   sdgratio   0.25 to 16 x  the modulator's pitch against the note: 1 e-piano, 3.5 a bell, 7.1 metal
//   sdgindex   0 to 10       how much it modulates: 0 a sine, 1 warm, 5 and up harsh
//   sdgdecay   0.01 to 4 s   how fast the brightness dies
//   sdgdetune  0 to 1        two carriers apart for a chorus, one each ear
//
//   d1 $ note (arp "up" "a'min7'6") # s "sdgfm" # legato 1
//   d1 $ note "c6 e6 g6" # s "sdgfm" # legato 2 # pF "sdgratio" 3.5 # pF "sdgindex" 3
//
// examples/tidal/14-synths.tidal plays it.
(
SynthDef(\sdgfm, { |out, sustain = 1, pan = 0.5, freq = 440, accelerate = 0,
	sdgratio = 1, sdgindex = 1.5, sdgdecay = 0.4, sdgdetune = 0.1|
	var env = EnvGen.ar(Env([0, 1, 0], [0.002, (sustain - 0.002).max(0.001)], -3), doneAction: 2);
	var bright = EnvGen.ar(Env([1, 0.1], [sdgdecay], -4));
	var pitch = freq * Line.kr(1, 1 + accelerate, sustain);
	var modulator = SinOsc.ar(pitch * sdgratio) * sdgindex * bright;
	var sig = SinOsc.ar(pitch * ([-0.2, 0.2] * sdgdetune).midiratio, modulator);
	sig = sig * 0.7;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;
)
