// synths/sdgmodal.scd - a struck object, modelled: ten resonant modes hit by a
// mallet (the Machinedrum's PI machines, the Syntakt's). one knob morphs the
// material from a tuned drum through a skin, a marimba bar and a free bar to a
// plate; others choose where it is hit and with what.
//
// parameters:
//   sdgfreq    30 to 4000 Hz      its pitch at note 0
//   sdgdecay   0.05 to 6 s        how long the lowest mode rings; the higher ones die sooner
//   sdgmetal   0 to 1             the material: 0 tuned (tabla), 0.25 skin, 0.5 marimba bar, 0.75 free bar (glass), 1 plate
//   sdgcolor   0 to 1             where it is hit: 0 the centre, round and dull, 1 near the edge, ringing
//   sdgtone    0 to 1             the mallet: 0 soft felt, 1 a hard stick
//   sdgpunch   0 to 24 semitones  a hard hit stretches the skin: the pitch starts this far up
//   sdgbend    0.001 to 1 s       how long it takes to settle
//   sdgsnap    0 to 1             snare wires that rattle as long as the body rings
//
// presets, by n: 0 drum (a tom-like skin), 1 marimba, 2 glass, 3 metal plate, 4 tabla,
// 5 wood, 6 vibes, 7 kalimba, 8 steel pan, 9 snare drum. note tunes it in semitones
// from its own pitch; legato or sustain shorten it, like a hand on the head.
//
//   d1 $ s "sdgmodal*4" # note "0 5 7 12"
//   d1 $ s "sdgmodal*8" # pF "sdgmetal" (slow 4 saw) # pF "sdgcolor" "0 0.5 1 0.3"
//
// examples/tidal/lessons/17-drums-fm.tidal plays it.
(
var name = \sdgmodal;
// mode ratios per material, ten each; sdgmetal morphs between neighbours mode by mode
var tables = [
	[1, 2, 3, 4, 5, 6, 7, 8, 9, 10],	// tuned: tabla, harmonic (Raman)
	[1, 1.593, 2.136, 2.295, 2.653, 2.917, 3.155, 3.5, 3.598, 3.647],	// skin: Bessel zeros j_mn
	[1, 3.93, 9.36, 16.8, 2.53, 6.1, 12.4, 24.9, 4.9, 34],	// marimba bar: 1, 4, 10, then weak torsional modes
	[1, 2.756, 5.404, 8.933, 13.344, 18.637, 24.814, 31.873, 39.81, 48.63],	// free-free bar
	[1, 2.081, 3.414, 3.893, 4.995, 5.954, 6.819, 8.28, 8.722, 8.882]	// clamped plate (SuperDirt's supergong)
];
var levels = [
	[1, 0.8, 0.6, 0.45, 0.35, 0.25, 0.18, 0.12, 0.08, 0.05],
	[1, 0.9, 0.8, 0.75, 0.7, 0.65, 0.6, 0.55, 0.5, 0.5],
	[1, 0.45, 0.2, 0.08, 0.12, 0.08, 0.05, 0.03, 0.06, 0.02],
	[1, 0.7, 0.5, 0.35, 0.25, 0.18, 0.12, 0.08, 0.06, 0.04],
	[1, 0.9, 0.8, 0.7, 0.6, 0.55, 0.5, 0.45, 0.4, 0.35]
];
// how much faster each material loses its upper modes: ring time = decay * ratio ** -damp
var damps = [0.8, 1.0, 1.3, 0.55, 0.4];
// a skin struck at r = 0, 0.25, 0.5, 0.75, 0.9 of the radius: |J_m(j_mn r)| per mode
var bessel = [
	[1.00, 0.91, 0.67, 0.34, 0.13], [0.00, 0.43, 0.58, 0.38, 0.16], [0.00, 0.18, 0.46, 0.40, 0.18],
	[1.00, 0.58, 0.17, 0.38, 0.19], [0.00, 0.07, 0.34, 0.40, 0.19], [0.00, 0.58, 0.13, 0.35, 0.20],
	[0.00, 0.03, 0.25, 0.39, 0.20], [0.00, 0.38, 0.31, 0.29, 0.21], [1.00, 0.13, 0.36, 0.26, 0.22],
	[0.00, 0.01, 0.18, 0.37, 0.21]
];
SynthDef(name, { |out, sustain = 8, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 110, sdgdecay = 0.6, sdgmetal = 0.25, sdgcolor = 0.4, sdgtone = 0.5,
	sdgpunch = 2, sdgbend = 0.06, sdgsnap = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.05, 6);
	var m = sdgmetal.clip(0, 1) * 4;
	var pos = sdgcolor.clip(0, 1);
	var skin = (1 - (m - 1).abs).max(0);	// 1 where the material is the skin
	var f = sdgfreq.clip(30, 4000) * tune * Line.kr(1, 1 + accelerate, decay);
	var damp = LinSelectX.kr(m, damps);
	var ratios = 10.collect { |k| LinSelectX.kr(m, tables.collect(_[k])) };
	var amps = 10.collect { |k|
		// anything else: a string-like |sin| of where along it is struck, with a floor
		var plain = 0.15 + (0.85 * sin(pi * (k + 1) * (0.5 - (pos * 0.45))).abs);
		var w = plain + ((LinSelectX.kr(pos * 4, bessel[k]).max(0.03) - plain) * skin);
		LinSelectX.kr(m, levels.collect(_[k])) * w * ((f * ratios[k]) < 18000)	// none past the top
	};
	var rings = ratios.collect { |r| decay * (r ** damp.neg) };
	// the mallet: its contact time puts the mallet's roll-off from 1 (felt) to 25 (stick) times the
	// pitch, so a high bar still sounds with a soft mallet; its area is divided out so every mallet
	// puts the same energy into the low modes, and a harder one only adds the high ones
	var contact = (6.9 / (2pi * f * sdgtone.clip(0, 1).linexp(0, 1, 1, 25))).clip(0.0001, 0.02);
	// Decay2's area in samples, exact: a stick's few samples are far from the continuous 0.1 * T * sr
	var area = (1 - exp(-6.9078 / (contact * SampleRate.ir))).reciprocal
		- (1 - exp(-6.9078 / (0.3 * contact * SampleRate.ir))).reciprocal;
	var exc = Decay2.ar(Impulse.ar(0), contact * 0.3, contact) / area
		+ (LPF.ar(WhiteNoise.ar, (2 / contact).min(18000)) * EnvGen.ar(Env.perc(0.0002, contact * 2))
			* 0.6 / (contact * SampleRate.ir).sqrt);
	var bend = EnvGen.kr(Env([sdgpunch.clip(0, 24).midiratio, 1], [sdgbend.clip(0.001, 1)], -5));
	var body = DynKlank.ar(`[ratios, amps, rings], exc, f * bend);
	var wires = HPF.ar(WhiteNoise.ar, 2500) * Amplitude.ar(body, 0.001, 0.12) * sdgsnap.clip(0, 1) * 0.6;
	// the modes' strengths are normalised, so a plate (all modes loud) and a centre-struck skin
	// (one mode) come out alike; higher pitches sound louder, -2.4 dB per octave above 80 Hz
	var norm = amps.collect(_.squared).sum.sqrt.max(0.3).reciprocal;
	var sig = (body + wires) * 0.42 * norm * (((f / 80).max(1)) ** -0.4);
	sig = (sig * (1 / 1.4)).tanh * 1.4;	// a soft ceiling: no setting goes over -8 dBFS at gain 1
	DetectSilence.ar(sig + Impulse.ar(0), 0.0001, 0.1, doneAction: 2);
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "drum"),	// 0 drum: the def's defaults
	(presetName: "marimba", sdgfreq: 523.25, sdgdecay: 0.55, sdgmetal: 0.5, sdgcolor: 0.3, sdgtone: 0.55, sdgpunch: 0),	// 1 marimba
	(presetName: "glass", sdgfreq: 1400, sdgdecay: 1.4, sdgmetal: 0.75, sdgcolor: 0.6, sdgtone: 0.9, sdgpunch: 0),	// 2 glass
	(presetName: "metal plate", sdgfreq: 330, sdgdecay: 1, sdgmetal: 1, sdgcolor: 0.7, sdgtone: 0.5, sdgpunch: 0.3),	// 3 metal plate
	(presetName: "tabla", sdgfreq: 300, sdgdecay: 0.9, sdgmetal: 0, sdgcolor: 0.2, sdgtone: 0.75, sdgpunch: 3, sdgbend: 0.08),	// 4 tabla
	(presetName: "wood", sdgfreq: 700, sdgdecay: 0.3, sdgmetal: 0.6, sdgcolor: 0.5, sdgtone: 0.9, sdgpunch: 0),	// 5 wood
	(presetName: "vibes", sdgfreq: 330, sdgdecay: 1.1, sdgmetal: 0.5, sdgcolor: 0.35, sdgtone: 0.15, sdgpunch: 0),	// 6 vibes
	(presetName: "kalimba", sdgfreq: 587.33, sdgdecay: 0.9, sdgmetal: 0.62, sdgcolor: 0.6, sdgtone: 0.55, sdgpunch: 0),	// 7 kalimba
	(presetName: "steel pan", sdgfreq: 392, sdgdecay: 1.1, sdgmetal: 0.08, sdgcolor: 0.7, sdgtone: 0.65, sdgpunch: 0.5),	// 8 steel pan
	(presetName: "snare drum", sdgfreq: 190, sdgdecay: 0.3, sdgmetal: 0.25, sdgcolor: 0.6, sdgtone: 0.85, sdgpunch: 2, sdgbend: 0.03, sdgsnap: 0.85)	// 9 snare drum
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
