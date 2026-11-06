// synths/sdgslam.scd - a hyper-real kick, the slam: a driven sine that dives a long way
// in pitch, a struck metal clang over it and a gated burst of noise, squashed flat by a
// drive, an eq and a limiter inside the synth. loud at a modest peak, like a mastered hit.
//
// parameters:
//   sdgfreq      20 to 200 Hz       where the dive lands, before note
//   sdgdecay     0.03 to 3 s        how long the body holds before it is cut
//   sdgpunch     0 to 48 semitones  how far above sdgfreq the dive starts
//   sdgbend      0.005 to 1.5 s     how long the dive takes; long is the swoop
//   sdgclick     0 to 1             a bright tick at the front
//   sdgdrive     0 to 1             how hard the body is driven, 0 a sine to 1 a square
//   sdgwavefold  0 to 1             a folded, metallic copy of the body on top
//   sdgmetal     0 to 1             the clang: louder and longer
//   sdgcolor     0 to 1             the clang's pitch, a low gong to thin steel
//   sdgsnap      0 to 1             the gated noise
//   sdgnoise     0.005 to 1 s       how long the noise gate stays open
//   sdgtone      0 to 1             dark to bright, the eq's top end and the noise
//
// presets, by n: 0 slam, 1 dive, 2 clank, 3 gated, 4 crunch, 5 boom, 6 sub drop,
// 7 industrial, 8 hardstyle, 9 soft thud. note tunes it in semitones; legato or sustain
// shorten it, like a choke. it is louder than its peak suggests: the limiter leaves
// only a few dB between peak and body.
//
//   d1 $ s "sdgslam ~ ~ sdgslam ~ ~ sdgslam ~" # note "-2"
//   d1 $ s "sdgslam:1" # pF "sdgbend" "<0.3 0.8>" # pF "sdgmetal" 0.6
//
// examples/tidal/lessons/18-drums-hyperreal.tidal plays it.
(
var name = \sdgslam;
SynthDef(name, { |out, sustain = 3.2, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 45, sdgdecay = 0.35, sdgpunch = 30, sdgbend = 0.15, sdgclick = 0.5,
	sdgdrive = 0.6, sdgwavefold = 0.15, sdgmetal = 0.4, sdgcolor = 0.5,
	sdgsnap = 0.25, sdgnoise = 0.05, sdgtone = 0.55|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.03, 3);
	var rel = (decay * 0.15).clip(0.008, 0.06);
	var metal = sdgmetal.clip(0, 1), tone = sdgtone.clip(0, 1);
	var ring = 0.06 + (metal * 0.6);
	var nlen = sdgnoise.clip(0.005, 1);
	var life = max(decay + rel, max(ring * 1.5, nlen)) + 0.01;
	var base = sdgfreq.clip(20, 200) * tune * Line.kr(1, 1 + accelerate, decay);
	var top = sdgpunch.clip(0, 48).midiratio;
	// two segments: a few ms from far above (the tick of the dive), then the dive itself
	var pitch = base * EnvGen.ar(Env([top * 4, top, 1], [0.006, sdgbend.clip(0.005, 1.5)], [-4, \exp]));
	var sine = SinOsc.ar(pitch.clip(15, 12000), 0.5pi);
	var bodyEnv = EnvGen.ar(Env([0, 1, 0.85, 0], [0.0005, decay, rel], [0, -1, -4]));
	var drive = sdgdrive.clip(0, 1).linexp(0, 1, 1.2, 24);
	var fold = sdgwavefold.clip(0, 1);
	var body, clang, cf, noise, click, sig;

	body = sine + (HPF.ar((sine * (1 + (fold * 5))).fold2(1), 250) * fold * 0.6);
	body = (body * bodyEnv * drive).tanh / drive.tanh;	// the gate goes through the drive too

	// the clang: a struck sheet, six inharmonic modes (the higher, the shorter), and an fm
	// fizz whose sidebands darken as its index falls: steel rather than a bell
	cf = 160 * (2 ** (sdgcolor.clip(0, 1) * 3.5)) * tune;
	clang = Ringz.ar(Impulse.ar(0), (cf * [1, 1.47, 2.09, 2.56, 3.39, 4.17]).clip(20, 16000),
		ring * [1, 0.8, 0.65, 0.5, 0.4, 0.3]).sum * 0.1;
	clang = clang + (PMOsc.ar(cf * 1.13, cf * 1.13 * 1.414, 7 * EnvGen.ar(Env.perc(0.0005, ring * 0.5, 1, -4)))
		* EnvGen.ar(Env.perc(0.0005, ring * 0.7, 1, -5)) * 0.45);
	clang = (clang * 2.5).tanh * metal;

	noise = RHPF.ar(WhiteNoise.ar, 500 * (2 ** (tone * 4)), 0.6);
	noise = noise * EnvGen.ar(Env([0, 1, 1, 0], [0.0005, nlen, 0.002])) * sdgsnap.clip(0, 1) * 0.6;
	click = HPF.ar(WhiteNoise.ar, 3000) * EnvGen.ar(Env.perc(0, 0.0025, 1, -4));
	click = (click + BPF.ar(Impulse.ar(0) * 8, 4000, 0.6)) * sdgclick.clip(0, 1) * 0.6;

	// the fx chain: eq, then a fast limiter (2 ms look-ahead: the whole hit is 4 ms late)
	sig = body + clang + noise + click;
	sig = BHiPass.ar(sig, 24, 0.7);	// nothing under the sub; not LeakDC, which overshoots a driven sub
	sig = BPeakEQ.ar(sig, 380, 1.2, -4);	// the box
	sig = BHiShelf.ar(sig, 3500, 1, tone * 12 - 6);
	sig = LPF.ar(sig, 6000 * (2 ** (tone * 1.6)));
	sig = Limiter.ar(sig * 1.6, 0.95, 0.002);
	// a short hit is played hotter, so that the knob changes length more than loudness
	sig = sig * 0.32 * (0.35 / decay).clip(1, 4).sqrt * EnvGen.ar(Env([1, 1, 0], [life + 0.004, 0.005]), doneAction: 2);
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "slam", sdgfreq: 45, sdgdecay: 0.35, sdgpunch: 30, sdgbend: 0.15),                       // 0 slam
	(presetName: "dive", sdgfreq: 38, sdgdecay: 0.9, sdgpunch: 36, sdgbend: 0.7, sdgclick: 0.3,
		sdgdrive: 0.3, sdgwavefold: 0, sdgmetal: 0.1, sdgsnap: 0.1),                // 1 dive
	(presetName: "clank", sdgfreq: 52, sdgdecay: 0.2, sdgpunch: 24, sdgbend: 0.05, sdgmetal: 1,
		sdgcolor: 0.65, sdgdrive: 0.7, sdgsnap: 0.3, sdgnoise: 0.03),                // 2 clank
	(presetName: "gated", sdgfreq: 48, sdgdecay: 0.12, sdgpunch: 26, sdgbend: 0.04, sdgdrive: 0.85,
		sdgmetal: 0.15, sdgsnap: 0.8, sdgnoise: 0.12, sdgtone: 0.7),                 // 3 gated
	(presetName: "crunch", sdgfreq: 55, sdgdecay: 0.3, sdgpunch: 28, sdgbend: 0.1, sdgdrive: 1,
		sdgwavefold: 0.8, sdgmetal: 0.4, sdgcolor: 0.3, sdgsnap: 0.4, sdgtone: 0.8),  // 4 crunch
	(presetName: "boom", sdgfreq: 41, sdgdecay: 1.4, sdgpunch: 24, sdgbend: 0.12, sdgclick: 0.7,
		sdgdrive: 0.5, sdgwavefold: 0, sdgmetal: 0, sdgsnap: 0.15, sdgtone: 0.4),     // 5 boom
	(presetName: "sub drop", sdgfreq: 30, sdgdecay: 1.2, sdgpunch: 48, sdgbend: 1.2, sdgclick: 0.1, sdgdrive: 0.2, sdgwavefold: 0, sdgmetal: 0, sdgsnap: 0, sdgtone: 0.3),	// 6 sub drop
	(presetName: "industrial", sdgfreq: 50, sdgdecay: 0.25, sdgpunch: 30, sdgbend: 0.06, sdgclick: 0.8, sdgdrive: 0.9, sdgwavefold: 0.5, sdgmetal: 0.8, sdgcolor: 0.9, sdgsnap: 0.5, sdgnoise: 0.08, sdgtone: 0.75),	// 7 industrial
	(presetName: "hardstyle", sdgfreq: 50, sdgdecay: 0.5, sdgpunch: 34, sdgbend: 0.12, sdgclick: 0.6, sdgdrive: 1, sdgwavefold: 0.3, sdgmetal: 0, sdgsnap: 0.1, sdgtone: 0.6),	// 8 hardstyle
	(presetName: "soft thud", sdgfreq: 48, sdgdecay: 0.25, sdgpunch: 18, sdgbend: 0.06, sdgclick: 0.2, sdgdrive: 0.2, sdgwavefold: 0, sdgmetal: 0, sdgsnap: 0.05, sdgtone: 0.3)	// 9 soft thud
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
