// synths/sdgsnare.scd - a snare drum: two tuned shells and a burst of filtered noise, the
// snares. sdgcolor goes from the 808, clean shells nearly an octave apart, to the 909,
// shaped shells a fifth apart whose noise holds a moment before it falls.
//
// parameters:
//   sdgfreq    80 to 500 Hz       the low shell at note 0
//   sdgdecay   0.03 to 1 s        how long the shells ring (to -60 dB)
//   sdgsnap    0 to 1             shells against noise, the 808's snappy
//   sdgnoise   0.02 to 1 s        how long the noise lasts
//   sdgtone    0 to 1             dark to bright: the upper shell and the noise band
//   sdgpunch   0 to 24 semitones  how far above its pitch the hit starts
//   sdgcolor   0 to 1             0 the 808, 1 the 909
//   sdgdrive   0 to 1             saturation of the shells
//
// presets, by n: 0 808, 1 909, 2 tight, 3 piccolo, 4 fat, 5 rimshot crack, 6 brush,
// 7 trap, 8 lofi dusty, 9 gated 80s. note tunes the shells and, half as far, the noise;
// legato or sustain shorten it, like a choke.
//
//   d1 $ s "~ sdgsnare ~ sdgsnare"
//   d1 $ s "~ sdgsnare:1 ~ [~ sdgsnare:2]" # pF "sdgsnap" "0.7 0.4"
//
// examples/tidal/lessons/16-drums-classic.tidal plays it.
(
var name = \sdgsnare;
SynthDef(name, { |out, sustain = 1.2, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 172, sdgdecay = 0.22, sdgsnap = 0.55, sdgnoise = 0.2, sdgtone = 0.4,
	sdgpunch = 3, sdgcolor = 0, sdgdrive = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.03, 1);
	var ndecay = sdgnoise.clip(0.02, 1);
	var snap = sdgsnap.clip(0, 1);
	var tone = sdgtone.clip(0, 1);
	var color = sdgcolor.clip(0, 1);
	var shape = (sdgdrive.clip(0, 1) + (color * 0.3)).min(1);
	var f0 = sdgfreq.clip(80, 500) * tune * Line.kr(1, 1 + accelerate, decay);
	var pitch = f0 * (1 + ((sdgpunch.clip(0, 24).midiratio - 1) * EnvGen.ar(Env([1, 0], [0.05], -7))));
	// the 808's shells sit at 172 and 339 Hz (x1.97), the 909's a fifth apart (x1.47)
	var lo = SinOsc.ar(pitch) * EnvGen.ar(Env([0, 1, 0.001, 0], [0.0005, decay, 0.005], [0, \exp, 0]));
	var hi = SinOsc.ar(pitch * color.linlin(0, 1, 1.97, 1.47))
		* EnvGen.ar(Env([0, 1, 0.001, 0], [0.0005, decay * 0.45, 0.005], [0, \exp, 0]));
	var shells = lo + (hi * tone.linlin(0, 1, 0.08, 0.6));
	// the 909 holds its noise 2 to 40 ms, then both fall; the 808's noise is half-wave
	// rectified white noise, a quirk of its transistor
	var hold = color * 0.035 + 0.002;
	var nenv = EnvGen.ar(Env([0, 1, 1, 0.001, 0], [0.0005, hold, ndecay, 0.005], [0, 0, \exp, 0]));
	var nz = WhiteNoise.ar;
	var ntune = tune.sqrt;
	var hp = (1400 - (color * 400)) * (2 ** (tone * 1.2)) * ntune;
	var lp = (6000 + (color * 3000)) * (2 ** ((tone - 0.4) * 1.5)) * ntune;
	var sig;
	nz = LinXFade2.ar(nz.max(0) * 2, nz, color * 2 - 1);
	nz = LPF.ar(HPF.ar(nz, hp.clip(200, 12000)), lp.clip(1000, 18000));
	shells = LinXFade2.ar(shells, (shells * (1 + (shape * 5))).tanh, shape * 2 - 1);
	shells = LPF.ar(shells, (pitch * 6).clip(100, 18000));
	nz = nz * nenv * snap.sqrt * color.linlin(0, 1, 2.6, 1.3);
	nz = (nz / 1.5).tanh * 1.5;	// shaves the rare noise spike, keeps the peak in line
	sig = (shells * (1 - snap).sqrt) + nz;
	Line.kr(0, 0, max(decay, ndecay + hold) + 0.015, doneAction: 2);
	Out.ar(out, DirtPan.ar(sig * 0.65, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "808", sdgfreq: 172, sdgdecay: 0.22, sdgsnap: 0.55, sdgnoise: 0.2, sdgtone: 0.4, sdgpunch: 3, sdgcolor: 0, sdgdrive: 0),	// 0 808
	(presetName: "909", sdgfreq: 190, sdgdecay: 0.15, sdgsnap: 0.6, sdgnoise: 0.2, sdgtone: 0.6, sdgpunch: 7, sdgcolor: 1, sdgdrive: 0.4),	// 1 909
	(presetName: "tight", sdgfreq: 215, sdgdecay: 0.09, sdgsnap: 0.65, sdgnoise: 0.08, sdgtone: 0.6, sdgpunch: 5, sdgcolor: 0.5, sdgdrive: 0.1),	// 2 tight
	(presetName: "piccolo", sdgfreq: 330, sdgdecay: 0.16, sdgsnap: 0.4, sdgnoise: 0.12, sdgtone: 0.9, sdgpunch: 5, sdgcolor: 0.3, sdgdrive: 0.3),	// 3 piccolo
	(presetName: "fat", sdgfreq: 140, sdgdecay: 0.32, sdgsnap: 0.45, sdgnoise: 0.3, sdgtone: 0.25, sdgpunch: 4, sdgcolor: 0.6, sdgdrive: 0.2),	// 4 fat
	(presetName: "rimshot crack", sdgfreq: 230, sdgdecay: 0.12, sdgsnap: 0.35, sdgnoise: 0.1, sdgtone: 0.85, sdgpunch: 9, sdgcolor: 0.8, sdgdrive: 0.7),	// 5 rimshot crack
	(presetName: "brush", sdgfreq: 160, sdgdecay: 0.06, sdgsnap: 0.95, sdgnoise: 0.35, sdgtone: 0.25, sdgpunch: 0, sdgcolor: 0.2, sdgdrive: 0),	// 6 brush
	(presetName: "trap", sdgfreq: 200, sdgdecay: 0.18, sdgsnap: 0.7, sdgnoise: 0.28, sdgtone: 0.75, sdgpunch: 6, sdgcolor: 0.9, sdgdrive: 0.3),	// 7 trap
	(presetName: "lofi dusty", sdgfreq: 180, sdgdecay: 0.2, sdgsnap: 0.5, sdgnoise: 0.18, sdgtone: 0.15, sdgpunch: 2, sdgcolor: 0.4, sdgdrive: 0.5),	// 8 lofi dusty
	(presetName: "gated 80s", sdgfreq: 160, sdgdecay: 0.35, sdgsnap: 0.6, sdgnoise: 0.3, sdgtone: 0.55, sdgpunch: 5, sdgcolor: 0.7, sdgdrive: 0.15)	// 9 gated 80s
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
