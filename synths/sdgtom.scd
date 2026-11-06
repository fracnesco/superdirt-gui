// synths/sdgtom.scd - a tom, conga or bongo: a tuned skin that starts sharp and settles,
// as the 808's do, with a stick on top and a breath of noise under the low ones. sdgcolor
// rounds the sine toward the 909's shaped triangle.
//
// parameters:
//   sdgfreq    40 to 600 Hz       its pitch at note 0
//   sdgdecay   0.05 to 2 s        how long it rings (to -60 dB)
//   sdgpunch   0 to 24 semitones  how far above its pitch the hit starts
//   sdgbend    0.001 to 1 s       how long it takes to settle
//   sdgsnap    0 to 1             noise under the skin, the 808 low tom's
//   sdgclick   0 to 1             the stick
//   sdgtone    0 to 1             dark to bright: the stick, the noise and the 909 body
//   sdgcolor   0 to 1             0 the 808's sine, 1 the 909's rounded triangle
//
// presets, by n: 0 low tom, 1 mid tom, 2 high tom, 3 conga, 4 bongo, 5 808 deep,
// 6 909 low, 7 909 high, 8 syndrum, 9 floor tom. note tunes it in semitones from its
// own pitch, so one preset plays a whole set of toms.
//
//   d1 $ s "sdgtom:2 sdgtom:1 ~ sdgtom" # legato 1
//   d1 $ n "3 3 4 3" # s "sdgtom" # note "0 0 0 -5"
//
// examples/tidal/lessons/16-drums-classic.tidal plays it.
(
var name = \sdgtom;
SynthDef(name, { |out, sustain = 2.5, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 90, sdgdecay = 0.6, sdgpunch = 4, sdgbend = 0.08, sdgsnap = 0.12,
	sdgclick = 0.2, sdgtone = 0.3, sdgcolor = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.05, 2);
	var tone = sdgtone.clip(0, 1);
	var color = sdgcolor.clip(0, 1);
	var f0 = sdgfreq.clip(40, 600) * tune * Line.kr(1, 1 + accelerate, decay);
	// the 808's diodes: 20-30 % sharp in the first 10 ms on its toms, 7-17 % on its congas
	var pitch = f0 * (1 + ((sdgpunch.clip(0, 24).midiratio - 1) * EnvGen.ar(Env([1, 0], [sdgbend.clip(0.001, 1)], -7))));
	var env = EnvGen.ar(Env([0, 1, 0.001, 0], [0.0005, decay, 0.005], [0, \exp, 0]));
	var h5 = tone.linlin(0, 1, 0.012, 0.04) * (pitch * 5 < 20000);
	var sine = SinOsc.ar(pitch, 0.5pi);
	var tri = (sine + ((SinOsc.ar(pitch * 3, 0.5pi) / 9) * (pitch * 3 < 20000)) + (SinOsc.ar(pitch * 5, 0.5pi) * h5)) / (1.111 + h5);
	var body = LinXFade2.ar(sine, tri, color * 2 - 1) * env;
	// pink noise a little longer than the skin: the 808 low tom's "artificial reverb"
	var nz = LPF.ar(HPF.ar(PinkNoise.ar, (f0 * 0.7).clip(30, 400)), (f0 * 8 * (2 ** (tone * 2))).clip(200, 16000))
		* EnvGen.ar(Env([0, 1, 0.001, 0], [0.002, decay * 1.2, 0.005], [0, \exp, 0])) * sdgsnap.clip(0, 1) * 0.9;
	var stick = HPF.ar(Trig1.ar(Impulse.ar(0), 0.0004), 1500 * (2 ** (tone * 2))) * sdgclick.clip(0, 1) * 0.5;
	var sig = body + nz + stick;
	// as the kick: a short ring reads quieter than a long one at the same peak
	var level = 0.62 * (0.6 / decay).pow(0.3).clip(0.8, 1.8);
	EnvGen.kr(Env([0, 0], [decay * 1.2 + 0.01]), doneAction: 2);
	Out.ar(out, DirtPan.ar(sig * level, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "low tom", sdgfreq: 90, sdgdecay: 0.6, sdgpunch: 4, sdgbend: 0.08, sdgsnap: 0.12, sdgclick: 0.2, sdgtone: 0.3, sdgcolor: 0),	// 0 low tom
	(presetName: "mid tom", sdgfreq: 136, sdgdecay: 0.42, sdgpunch: 4.5, sdgbend: 0.08, sdgsnap: 0.05, sdgclick: 0.2, sdgtone: 0.3, sdgcolor: 0),	// 1 mid tom
	(presetName: "high tom", sdgfreq: 190, sdgdecay: 0.33, sdgpunch: 3.5, sdgbend: 0.07, sdgsnap: 0.03, sdgclick: 0.2, sdgtone: 0.35, sdgcolor: 0),	// 2 high tom
	(presetName: "conga", sdgfreq: 250, sdgdecay: 0.3, sdgpunch: 2, sdgbend: 0.04, sdgsnap: 0, sdgclick: 0.25, sdgtone: 0.5, sdgcolor: 0.1),	// 3 conga
	(presetName: "bongo", sdgfreq: 412, sdgdecay: 0.22, sdgpunch: 1.5, sdgbend: 0.03, sdgsnap: 0, sdgclick: 0.3, sdgtone: 0.6, sdgcolor: 0.1),	// 4 bongo
	(presetName: "808 deep", sdgfreq: 62, sdgdecay: 1.3, sdgpunch: 7, sdgbend: 0.15, sdgsnap: 0.15, sdgclick: 0.05, sdgtone: 0.2, sdgcolor: 0),	// 5 808 deep
	(presetName: "909 low", sdgfreq: 85, sdgdecay: 0.5, sdgpunch: 6, sdgbend: 0.1, sdgsnap: 0.1, sdgclick: 0.4, sdgtone: 0.5, sdgcolor: 1),	// 6 909 low
	(presetName: "909 high", sdgfreq: 160, sdgdecay: 0.35, sdgpunch: 5, sdgbend: 0.08, sdgsnap: 0.05, sdgclick: 0.4, sdgtone: 0.55, sdgcolor: 1),	// 7 909 high
	(presetName: "syndrum", sdgfreq: 220, sdgdecay: 0.45, sdgpunch: 24, sdgbend: 0.35, sdgsnap: 0, sdgclick: 0.1, sdgtone: 0.3, sdgcolor: 0),	// 8 syndrum
	(presetName: "floor tom", sdgfreq: 70, sdgdecay: 0.9, sdgpunch: 3, sdgbend: 0.12, sdgsnap: 0.2, sdgclick: 0.3, sdgtone: 0.35, sdgcolor: 0.3)	// 9 floor tom
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
