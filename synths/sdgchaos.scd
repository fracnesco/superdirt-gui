// synths/sdgchaos.scd - digital and chaotic noise percussion: a chaotic source
// (crackle, logistic map, stepped noise or Xenakis' gendy) over a dropping sine,
// a comb that rings it, drive, and a sample-rate and bit crush. glitch snares,
// digital hats, broken kicks; no two hits are the same.
//
// parameters:
//   sdgcolor   0 to 1                the source: 0 crackle, 0.33 logistic map, 0.67 stepped noise, 1 gendy
//   sdgsnap    0 to 1                0 all body (a kick), 1 all noise (a hat)
//   sdgfreq    30 to 2000 Hz         the body's pitch, the comb's and gendy's, at note 0
//   sdgtone    0 to 1                dark to bright: the noise's band and how fast the source runs
//   sdgdecay   0.01 to 1.5 s         how long the body lasts
//   sdgnoise   0.01 to 1.5 s         how long the noise lasts
//   sdgpunch   0 to 48 semitones     how far above its pitch the body starts
//   sdgbend    0.001 to 1 s          how long that drop takes
//   sdgindex   0 to 1                the source bends the body's pitch: a broken, unstable body
//   sdgtail    0 to 1                a comb at the pitch rings the noise, up to half a second
//   sdgdrive   0 to 1                saturation, then folding past 0.5
//   sdgbits    0 to 1                bit and sample-rate crush, 0 clean
//
// presets, by n: 0 glitch snare, 1 digital hat, 2 broken kick, 3 crackle, 4 gendy tom,
// 5 static. note tunes it; legato or sustain shorten it, like a choke.
//
//   d1 $ s "sdgchaos:2 ~ sdgchaos:0 ~" # pF "sdgbits" "<0 0.4 0.7>"
//   d1 $ s "sdgchaos:1*16" # pF "sdgcolor" (slow 4 saw) # pF "sdgnoise" (range 0.02 0.1 rand)
//
// examples/tidal/19-drums-autechre.tidal plays it.
(
var name = \sdgchaos;
SynthDef(name, { |out, sustain = 2.5, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgcolor = 0.33, sdgsnap = 0.6, sdgfreq = 190, sdgtone = 0.55, sdgdecay = 0.12, sdgnoise = 0.18,
	sdgpunch = 12, sdgbend = 0.03, sdgindex = 0, sdgtail = 0.25, sdgdrive = 0.2, sdgbits = 0.3|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var tone = sdgtone.clip(0, 1);
	var snap = sdgsnap.clip(0, 1);
	var dec = sdgdecay.clip(0.01, 1.5);
	var ndec = sdgnoise.clip(0.01, 1.5);
	var ring = sdgtail.clip(0, 1);
	var drive = sdgdrive.clip(0, 1);
	var bits = sdgbits.clip(0, 1);
	var nyq = SampleRate.ir * 0.45;
	var f = (sdgfreq.clip(30, 2000) * tune).clip(20, 4000);
	var rate = (1500 * (2 ** (tone * 4)) * tune).clip(100, SampleRate.ir);	// how fast the source steps
	var ringTime = ring * 0.5;
	var env = EnvGen.ar(Env([1, 1, 0], [max(dec, ndec) * 1.2 + ringTime + 0.01, 0.005]), doneAction: 2);
	// the four sources, morphed by sdgcolor (the band below evens out their levels)
	var src = SelectX.ar(sdgcolor.clip(0, 1) * 3, [
		LeakDC.ar(Crackle.ar(1.6 + (tone * 0.39))) * 1.5,
		LeakDC.ar(Logistic.ar(3.72 + (tone * 0.27), rate) * 2 - 1),
		LFNoise0.ar(rate),
		Gendy1.ar(1, 1, 1, 1, f, f * (1.5 + (tone * 6)), 0.6, 0.4, 12)
	]);
	var body = SinOsc.ar(f * EnvGen.ar(Env([sdgpunch.clip(0, 48).midiratio, 1], [sdgbend.clip(0.001, 1)], \exp))
		* Line.kr(1, 1 + accelerate, dec) * (1 + (src * sdgindex.clip(0, 1) * 0.6)).max(0.05))
		* EnvGen.ar(Env.perc(0.001, dec, 1, -4));
	// dark to bright: a band that slides from 30-400 Hz up to 7.7-18 kHz
	var band = LPF.ar(HPF.ar(src, 30 * (2 ** (tone * 8))), (400 * (2 ** (tone * 5.5))).min(nyq));
	// the sources put 40 dB more or less into a band: hold the band at one rms over the last
	// 23 ms (counting only what has run so far), so sdgcolor and sdgtone change character, not level
	var fill = (Sweep.ar(0, SampleRate.ir) + 1).min(1024) / 1024;
	var level = (RunningSum.ar(band.squared, 1024) / (1024 * fill)).max(0).sqrt;
	var noise = band * (0.25 / level.max(0.006)) * EnvGen.ar(Env.perc(0.0005, ndec, 1, -6));
	var sig = (body * (1 - snap)) + (noise * snap * 2.4);
	sig = sig + (CombC.ar(sig, 0.05, (1 / f).clip(0.0003, 0.05), ringTime.max(0.001)) * ring * 0.4);
	// drive: tanh up to 0.5, folding above
	sig = (sig * (1 + (drive * 5))).tanh;
	sig = SelectX.ar((drive - 0.5).max(0) * 2, [sig, (sig * 2.5).fold2(1)]);
	// crush: down to 1.4 kHz and 3 bits at 1 (at 44.1 kHz)
	// Latch only moves on a rising trigger, so its clock stays at or under half the sample
	// rate; the first tenth of the knob fades the crush in rather than jumping to it
	sig = SelectX.ar((bits * 10).min(1), [sig,
		(Latch.ar(sig, Impulse.ar(SampleRate.ir * 0.5 * (2 ** (bits * -4))))
			* (2 ** (15 - (bits * 13)))).round / (2 ** (15 - (bits * 13)))]);
	sig = LeakDC.ar(sig, 0.999) * 0.6;	// folding and crushing noise leave DC; a slow LeakDC spares the kick
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(sdgcolor: 0.33, sdgsnap: 0.6, sdgfreq: 190, sdgtone: 0.55, sdgdecay: 0.12, sdgnoise: 0.18, sdgpunch: 12, sdgtail: 0.25, sdgdrive: 0.2, sdgbits: 0.3),	// 0 glitch snare
	(sdgcolor: 0.67, sdgsnap: 1, sdgfreq: 1200, sdgtone: 0.95, sdgnoise: 0.045, sdgtail: 0, sdgdrive: 0, sdgbits: 0),	// 1 digital hat
	(sdgcolor: 1, sdgsnap: 0.12, sdgfreq: 48, sdgtone: 0.3, sdgdecay: 0.38, sdgnoise: 0.08, sdgpunch: 30, sdgbend: 0.05, sdgindex: 0.6, sdgtail: 0, sdgdrive: 0.45, sdgbits: 0.75),	// 2 broken kick
	(sdgcolor: 0, sdgsnap: 1, sdgfreq: 900, sdgtone: 0.65, sdgnoise: 0.07, sdgtail: 0.35, sdgdrive: 0.2, sdgbits: 0.1),	// 3 crackle
	(sdgcolor: 1, sdgsnap: 0.7, sdgfreq: 140, sdgtone: 0.35, sdgdecay: 0.25, sdgnoise: 0.25, sdgpunch: 7, sdgtail: 0.35, sdgdrive: 0.3, sdgbits: 0.15),	// 4 gendy tom
	(sdgcolor: 0.4, sdgsnap: 1, sdgfreq: 300, sdgtone: 0.6, sdgnoise: 0.25, sdgtail: 0.15, sdgdrive: 0.35, sdgbits: 0.65)	// 5 static
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
