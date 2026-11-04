// synths/sdgcowbell.scd - a cowbell: the 808's two square waves a fifth-ish
// apart through a filter, loud for the first few milliseconds and then a ringing
// tail. agogo and bell by preset; ring the two together for clank.
//
// parameters:
//   sdgdecay   0.05 to 3 s  how long the tail rings
//   sdgtone    0 to 1       dark to bright: 0 nearly two sines, 1 the squares' edges
//   sdgcolor   0.05 to 1    the upper tone, 1 + color times the lower: 0.48 the 808, 1 an octave
//   sdgmetal   0 to 1       the two rung against each other: 0 a clean pair, 1 clank
//   sdgclick   0 to 1       how far the first 12 ms stand out over the tail
//
// presets, by n: 0 808, 1 808 short, 2 agogo, 3 bell, 4 clank. note tunes it in
// semitones from the 808's 558 Hz; agogo pairs are a note apart, "0 5" or "0 7".
//
//   d1 $ s "sdgcowbell(3,8)"
//   d1 $ s "sdgcowbell:2*8" # note "7 0 0 7 0 7 0 0"
//
// examples/tidal/16-drums-classic.tidal plays it.
(
var name = \sdgcowbell;
SynthDef(name, { |out, sustain = 3.2, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgdecay = 0.6, sdgtone = 0.45, sdgcolor = 0.476, sdgmetal = 0, sdgclick = 0.85|
	var tune = freq / 261.6256;	// note 0 is the 808's own pitch
	var decay = sdgdecay.clip(0.05, 3);
	var tone = sdgtone.clip(0, 1);
	var metal = sdgmetal.clip(0, 1);
	var click = sdgclick.clip(0, 1);
	var nyq = SampleRate.ir * 0.45;
	// measured on Dirt-Samples' 808 CB: 558 and 824 Hz, the upper one 12 dB louder
	var f1 = (558 * tune * Line.kr(1, 1 + accelerate, decay)).clip(20, nyq * 0.5);
	var f2 = f1 * (1 + sdgcolor.clip(0.05, 1));
	var a = Pulse.ar(f1, 0.48), b = Pulse.ar(f2, 0.48);
	var lp = (tone.linexp(0, 1, 1000, 9000) * tune.clip(0.25, 4).sqrt).min(nyq);
	// the 808's VCA: an abrupt first drop for the attack, then the tail (service notes, R82/C34)
	var env = EnvGen.ar(Env([0, 1, 1 - (click * 0.9), 0], [0.0005, 0.012, decay], [0, -3, -5]),
		doneAction: 2);
	var sig = ((a * 0.3) + b) * (1 - metal) + (a * b * metal * 2.5);
	sig = LPF.ar(LPF.ar(sig, lp), lp);
	// the ring's difference tone sits under the lower square: keep it out, it turns clank to gong
	sig = HPF.ar(HPF.ar(sig, (f1 * 0.7).max(150)), (f1 * 0.7).max(150));
	sig = LeakDC.ar(sig) * env * 1.6;
	sig = (sig * 0.5).tanh * 2;	// only the extremes reach this
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(sdgdecay: 0.6, sdgtone: 0.45, sdgcolor: 0.476, sdgmetal: 0, sdgclick: 0.85),	// 0 808
	(sdgdecay: 0.18, sdgtone: 0.55, sdgcolor: 0.476, sdgmetal: 0, sdgclick: 0.75),	// 1 808 short
	(sdgdecay: 0.35, sdgtone: 0.1, sdgcolor: 1, sdgmetal: 0, sdgclick: 0.5),	// 2 agogo
	(sdgdecay: 1.2, sdgtone: 0.15, sdgcolor: 0.76, sdgmetal: 0.35, sdgclick: 0.7),	// 3 bell
	(sdgdecay: 0.3, sdgtone: 0.8, sdgcolor: 0.42, sdgmetal: 0.75, sdgclick: 0.85)	// 4 clank
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
