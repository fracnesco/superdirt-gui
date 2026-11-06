// synths/sdgrim.scd - rimshot, claves, woodblock: short pings, the way the 808
// makes them - a resonant filter struck once, a lower one under it, a tick of
// noise, and clipping for the rimshot's crack.
//
// parameters:
//   sdgcut     200 to 8000 Hz  the ping: 1710 the 808 rim, 2420 its claves
//   sdgcolor   0 to 1          where the low body sits under the ping, 0.1 to 0.8 of it
//   sdgtone    0 to 1          the balance, 0 all body to 1 all ping, and the tick's brightness
//   sdgdecay   0.005 to 0.5 s  how long the ping rings; the body rings a little longer
//   sdgsnap    0 to 1          the tick of noise against the pinged tones
//   sdgdrive   0 to 1          clipping: 0 a clean ping, 1 a hard crack
//
// presets, by n: 0 808 rim, 1 claves, 2 woodblock, 3 909 rim, 4 stick, 5 cross stick,
// 6 high block, 7 low block, 8 castanet, 9 hi click. note tunes both tones in
// semitones.
//
//   d1 $ s "~ sdgrim ~ sdgrim:3"
//   d1 $ s "sdgrim:1(3,8,<0 2>)" # note "<0 2 -3>"
//
// examples/tidal/lessons/16-drums-classic.tidal plays it.
(
var name = \sdgrim;
SynthDef(name, { |out, sustain = 1, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgcut = 1710, sdgcolor = 0.5, sdgtone = 0.65, sdgdecay = 0.015, sdgsnap = 0.2, sdgdrive = 0.8|
	var tune = freq / 261.6256;	// note 0 is the preset's own pitch
	var decay = sdgdecay.clip(0.005, 0.5);
	var tone = sdgtone.clip(0, 1);
	var snap = sdgsnap.clip(0, 1);
	var drive = sdgdrive.clip(0, 1);
	var nyq = SampleRate.ir * 0.45;
	var bend = Line.kr(1, 1 + accelerate, decay);
	var fPing = (sdgcut.clip(200, 8000) * tune * bend).clip(20, nyq);
	var fBody = fPing * sdgcolor.clip(0, 1).linexp(0, 1, 0.1, 0.8);
	// a struck resonator starts from zero at full swing and dies exponentially; the
	// lower one rings longer (measured on the 808 RS: 500 Hz outlasts 1.7 kHz)
	var ping = SinOsc.ar(fPing) * EnvGen.ar(Env([0, 1, 0], [0.0002, decay], [0, -6]));
	var body = SinOsc.ar(fBody) * EnvGen.ar(Env([0, 1, 0], [0.0002, decay * 1.5], [0, -6]), doneAction: 2);
	var tonal = ((ping * tone.sqrt) + (body * (1 - tone).sqrt)) / (tone.sqrt + (1 - tone).sqrt);
	var tick = HPF.ar(WhiteNoise.ar, tone.linexp(0, 1, 1000, 6000))
		* EnvGen.ar(Env([0, 1, 0], [0.0002, 0.004 + (decay * 0.4).min(0.012)], [0, -4]));
	var g = 1 + (drive * 8);
	var sig = (tonal * (1 - snap).sqrt) + (tick * snap.sqrt * 1.2);
	sig = (sig * g).tanh / g.tanh;	// clips harder with drive, the peak stays put
	// clipped noise is the rimshot's crack, but its corners overshoot between samples: round them
	sig = LPF.ar(sig, 12000);
	sig = LeakDC.ar(sig, 0.995);	// a sine struck from zero and dying in a few cycles leans to one side
	Out.ar(out, DirtPan.ar(sig * 0.63, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "808 rim", sdgcut: 1710, sdgcolor: 0.5, sdgtone: 0.65, sdgdecay: 0.015, sdgsnap: 0.2, sdgdrive: 0.8),	// 0 808 rim
	(presetName: "claves", sdgcut: 2420, sdgcolor: 0.5, sdgtone: 1, sdgdecay: 0.05, sdgsnap: 0, sdgdrive: 0),	// 1 claves
	(presetName: "woodblock", sdgcut: 2300, sdgcolor: 0.55, sdgtone: 0.25, sdgdecay: 0.06, sdgsnap: 0.15, sdgdrive: 0.15),	// 2 woodblock
	(presetName: "909 rim", sdgcut: 1850, sdgcolor: 0.42, sdgtone: 0.7, sdgdecay: 0.03, sdgsnap: 0.45, sdgdrive: 0.85),	// 3 909 rim
	(presetName: "stick", sdgcut: 3300, sdgcolor: 0.6, sdgtone: 0.75, sdgdecay: 0.025, sdgsnap: 0.5, sdgdrive: 0.3),	// 4 stick
	(presetName: "cross stick", sdgcut: 1500, sdgcolor: 0.35, sdgtone: 0.45, sdgdecay: 0.04, sdgsnap: 0.3, sdgdrive: 0.4),	// 5 cross stick
	(presetName: "high block", sdgcut: 3600, sdgcolor: 0.55, sdgtone: 0.3, sdgdecay: 0.05, sdgsnap: 0.1, sdgdrive: 0.1),	// 6 high block
	(presetName: "low block", sdgcut: 1300, sdgcolor: 0.6, sdgtone: 0.3, sdgdecay: 0.08, sdgsnap: 0.1, sdgdrive: 0.1),	// 7 low block
	(presetName: "castanet", sdgcut: 4200, sdgcolor: 0.4, sdgtone: 0.85, sdgdecay: 0.01, sdgsnap: 0.6, sdgdrive: 0.5),	// 8 castanet
	(presetName: "hi click", sdgcut: 6000, sdgcolor: 0.2, sdgtone: 0.95, sdgdecay: 0.008, sdgsnap: 0.4, sdgdrive: 0.9)	// 9 hi click
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
