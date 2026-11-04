// synths/sdgkick.scd - a kick drum: a sine that drops onto its pitch and rings out. sdgcolor
// goes from the 808, a ringing sine that sags a little as it fades, to the 909, a rounded
// triangle swept down from three times its pitch; a click on top, drive for the hard ones.
//
// parameters:
//   sdgfreq    30 to 120 Hz       its pitch at note 0
//   sdgdecay   0.05 to 3 s        how long the body rings (to -60 dB)
//   sdgpunch   0 to 48 semitones  how far above its pitch the hit starts
//   sdgbend    0.001 to 1 s       how long that drop takes
//   sdgclick   0 to 1             the beater: a pulse and a breath of noise
//   sdgtone    0 to 1             dark to bright: the click and the 909 body
//   sdgcolor   0 to 1             0 the 808's sine, 1 the 909's rounded triangle
//   sdgdrive   0 to 1             saturation, 1 nearly a square
//
// presets, by n: 0 808 long boom, 1 909, 2 606 tight, 3 hard techno, 4 deep sub.
// note tunes it in semitones from its own pitch; legato or sustain shorten it, like a choke.
//
//   d1 $ s "sdgkick*4"
//   d1 $ s "sdgkick:1*4" # pF "sdgdecay" 0.3 # note "-2"
//
// examples/tidal/16-drums-classic.tidal plays it.
(
var name = \sdgkick;
SynthDef(name, { |out, sustain = 3.1, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 49.5, sdgdecay = 1.6, sdgpunch = 12, sdgbend = 0.03, sdgclick = 0.25,
	sdgtone = 0.3, sdgcolor = 0, sdgdrive = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.05, 3);
	var tone = sdgtone.clip(0, 1);
	var color = sdgcolor.clip(0, 1);
	var drive = sdgdrive.clip(0, 1);
	var f0 = sdgfreq.clip(30, 120) * tune * Line.kr(1, 1 + accelerate, decay);
	// the drop, and the 808's sag: a real one reads 56 Hz at 40 ms, 51 at 100, 49.6 after
	var drop = EnvGen.ar(Env([1, 0], [sdgbend.clip(0.001, 1)], -7));
	var sag = EnvGen.ar(Env([1, 0], [0.42], -7)) * 0.25 * (1 - color);
	var pitch = f0 * (1 + ((sdgpunch.clip(0, 48).midiratio - 1) * drop) + sag);
	// a resonator's ring: straight down in dB, -60 dB at decay
	var env = EnvGen.ar(Env([0, 1, 0.001, 0], [0.0004, decay, 0.005], [0, \exp, 0]), doneAction: 2);
	// cosine phase: the first half-cycle is the knock. the 909's triangle is its first three
	// odd harmonics, band-limited; tone rounds it (the 909's 5th sits at -35 dB)
	var h5 = tone.linlin(0, 1, 0.012, 0.04) * (pitch * 5 < 20000);
	var sine = SinOsc.ar(pitch, 0.5pi);
	var tri = (sine + ((SinOsc.ar(pitch * 3, 0.5pi) / 9) * (pitch * 3 < 20000)) + (SinOsc.ar(pitch * 5, 0.5pi) * h5)) / (1.111 + h5);
	// a real 808 starts with its 2nd and 3rd harmonics at -28 and -36 dB, gone in ~150 ms;
	// the 909 keeps a 2nd at -29 dB, its waveshaper's lean
	var warm = EnvGen.ar(Env([1, 0], [0.35], -7));
	var h2 = SinOsc.ar(pitch * 2, 0.5pi) * ((warm * 0.17 * (1 - color)) + (color * 0.035)) * (pitch * 2 < 20000);
	var h3 = SinOsc.ar(pitch * 3, 0.5pi) * warm * 0.064 * (1 - color) * (pitch * 3 < 20000);
	var body = (LinXFade2.ar(sine, tri, color * 2 - 1) + h2 + h3) * env;
	var click = (HPF.ar(Trig1.ar(Impulse.ar(0), 0.0006), 600) * 0.4)
		+ (LPF.ar(WhiteNoise.ar, 800 * (2 ** (tone * 4.3))) * EnvGen.ar(Env.perc(0.0002, 0.015, 0.5, -6)));
	var sig = body + (click * sdgclick.clip(0, 1));
	// a short kick reads quieter than a long one at the same peak: lift it, within the ceiling
	var level = 0.67 * (1.6 / decay).pow(0.38).clip(0.75, 1.9);
	sig = LinXFade2.ar(sig, (sig * (1 + (drive * 7))).tanh * (1 - (drive * 0.6)), drive * 2 - 1);
	Out.ar(out, DirtPan.ar(sig * level, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(sdgfreq: 49.5, sdgdecay: 1.6, sdgpunch: 12, sdgbend: 0.03, sdgclick: 0.25, sdgtone: 0.3, sdgcolor: 0, sdgdrive: 0),	// 0 808 long boom
	(sdgfreq: 49, sdgdecay: 0.45, sdgpunch: 20, sdgbend: 0.07, sdgclick: 0.6, sdgtone: 0.7, sdgcolor: 1, sdgdrive: 0.1),	// 1 909
	(sdgfreq: 60, sdgdecay: 0.22, sdgpunch: 16, sdgbend: 0.03, sdgclick: 0.5, sdgtone: 0.6, sdgcolor: 0.3, sdgdrive: 0),	// 2 606 tight
	(sdgfreq: 50, sdgdecay: 0.5, sdgpunch: 28, sdgbend: 0.08, sdgclick: 0.8, sdgtone: 0.8, sdgcolor: 0.6, sdgdrive: 0.6),	// 3 hard techno
	(sdgfreq: 41, sdgdecay: 2.2, sdgpunch: 7, sdgbend: 0.08, sdgclick: 0.05, sdgtone: 0.1, sdgcolor: 0, sdgdrive: 0.15)	// 4 deep sub
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
