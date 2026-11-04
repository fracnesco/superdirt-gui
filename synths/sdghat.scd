// synths/sdghat.scd - a hi-hat: the 808's six square oscillators, a band pass
// around 7 kHz and a high pass, through a swing VCA. closed, pedal and open by
// decay; noise in place of the squares for the sampled 909 kind.
//
// parameters:
//   sdgdecay   0.02 to 2 s  how long it rings: 0.1 closed, 0.5 and up open
//   sdgtone    0 to 1       dark to bright: the high pass from 3 to 10 kHz, the 3.4 kHz band under it
//   sdgmetal   0 to 1       0 white noise, 1 the six squares only
//   sdgcolor   0 to 1       the squares' spread: 0 the 808's 205 to 800 Hz, 1 the 606's tighter 245 to 619
//   sdgclick   0 to 1       a sharp tick on top of the first 10 ms; 0 a soft "chick"
//   sdgbits    0 to 1       the 909's sampled grit: fewer bits and a lower rate, 0 clean
//
// presets, by n: 0 808 closed, 1 pedal, 2 808 open, 3 909 closed, 4 909 open,
// 5 606 closed. note shifts the squares and half as much the filters.
// put closed and open in one cut group and a closed hat chokes the open one:
//
//   d1 $ s "sdghat*8" # n "0 0 2 0 0 1 2 0" # cut 1
//   d1 $ s "sdghat:3*16" # gain "1 0.9" # pF "sdgdecay" (range 0.05 0.2 sine)
//
// examples/tidal/16-drums-classic.tidal plays it.
(
var name = \sdghat;
SynthDef(name, { |out, sustain = 2.1, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgdecay = 0.11, sdgtone = 0.8, sdgmetal = 1, sdgcolor = 0, sdgclick = 0.5, sdgbits = 0|
	var tune = freq / 261.6256;	// note 0 is the 808's own tuning
	var decay = sdgdecay.clip(0.02, 2);
	var tone = sdgtone.clip(0, 1);
	var metal = sdgmetal.clip(0, 1);
	var click = sdgclick.clip(0, 1);
	var bits = sdgbits.clip(0, 1);
	var nyq = SampleRate.ir * 0.45;
	// the 808's six oscillators (Werner/Abel/Smith) bent toward the 606's set (Moritz Klein)
	var f808 = [205.3, 304.4, 369.6, 522.7, 540, 800];
	var f606 = [245, 306, 365, 415, 437, 619];
	var bend = (tune * Line.kr(1, 1 + accelerate, decay)).clip(0.1, 8);
	var track = tune.clip(0.25, 4).sqrt;	// the filters follow note by half
	var hp = (tone.linexp(0, 1, 3000, 10000) * track).min(nyq);
	// Pulse is band-limited: these squares' harmonics reach far past Nyquist, and an LFPulse
	// would fold them back as a hiss under the metal
	var squares = Pulse.ar(f808 * ((f606 / f808) ** sdgcolor.clip(0, 1)) * bend, 0.48).sum;
	var src = (squares * metal * 0.5) + (WhiteNoise.ar * (1 - metal) * 0.32);
	var band = BPF.ar(src, (7100 * track).min(nyq), 0.7)
		+ (BPF.ar(src, (3440 * track).min(nyq), 0.7) * (1 - tone) * 0.6);
	var atk = 0.0004 + ((1 - click) * 0.004);
	// a closed hat dies away exponentially, an open one fades more evenly to its end
	var env = EnvGen.ar(Env([0, 1, 0], [atk, decay], [0, decay.explin(0.1, 1, -6, -3)]), doneAction: 2);
	var tick = EnvGen.ar(Env([0, 1, 0], [atk, 0.008], [0, -4])) * click;
	// a longer hat is not a louder one, and a brighter one would read quieter
	var level = ((decay / 0.11).clip(1, 20) ** -0.2) * ((tone - 0.7) * 10).dbamp;
	var sig, crushed;
	// the 909's hats are 6-bit samples played at ~30 kHz: grit that scales with the level
	// Latch only moves on a rising edge: a clock above SR/2 gives SR - f edges, fewer, not more
	crushed = Latch.ar(band, Impulse.ar((SampleRate.ir * (2 ** (bits * -1.6))).min(SampleRate.ir * 0.5)));
	crushed = (crushed * (2 ** (11 - (bits * 7)))).round / (2 ** (11 - (bits * 7)));
	sig = Select.ar(bits > 0, [band, crushed]);
	sig = sig * env * (1 + tick);
	sig = (sig * 4).max(sig * 0.1);	// the 808's swing VCA: one half-wave much louder
	sig = sig / (1 + sig.abs);
	sig = HPF.ar(HPF.ar(LeakDC.ar(sig), hp), hp);
	Out.ar(out, DirtPan.ar(sig * level * 2.43, ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(sdgdecay: 0.11, sdgtone: 0.8, sdgmetal: 1, sdgclick: 0.5),	// 0 808 closed
	(sdgdecay: 0.09, sdgtone: 0.1, sdgmetal: 0.85, sdgclick: 0),	// 1 pedal
	(sdgdecay: 0.55, sdgtone: 0.45, sdgmetal: 1, sdgclick: 0.3),	// 2 808 open
	(sdgdecay: 0.12, sdgtone: 0.8, sdgmetal: 0.3, sdgclick: 0.6, sdgbits: 0.6),	// 3 909 closed
	(sdgdecay: 0.6, sdgtone: 0.65, sdgmetal: 0.3, sdgclick: 0.4, sdgbits: 0.6),	// 4 909 open
	(sdgdecay: 0.08, sdgtone: 0.8, sdgmetal: 1, sdgcolor: 1, sdgclick: 0.7)	// 5 606 closed
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
