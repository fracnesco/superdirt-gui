// synths/sdgping.scd - pinged resonant filters: one click rings three tuned
// resonators at once, and their pitch sweeps as they ring. blips, droplets,
// metallic ticks, wood and rubber.
//
// parameters:
//   sdgfreq    40 to 12000 Hz        the main resonator's pitch at note 0
//   sdgdecay   0.005 to 2 s          how long it rings
//   sdgpunch   -24 to 48 semitones   where the pitch starts: above it falls, below it rises (a droplet)
//   sdgbend    0.001 to 1 s          how long the pitch takes to arrive
//   sdgmetal   0 to 1                the other two resonators: 0 harmonic (2, 3), 1 a struck bar (2.76, 5.4)
//   sdgtone    0 to 1                0 a soft mallet and a dark ring, 1 a hard click and bright partials
//   sdgindex   0 to 8                FM on the main resonator: 0 a pure ping, up to a clangy blip
//   sdgratio   0.25 to 8             the FM's modulator, as a ratio of the pitch
//   sdgcolor   0 to 1                rattle: the resonators are hit again and again while they ring
//   sdgclick   0 to 1                a short noisy tick on top
//
// presets, by n: 0 blip, 1 droplet, 2 metal tick, 3 wood, 4 zap, 5 rubber, 6 sonar,
// 7 laser, 8 bar, 9 glitch rattle. note tunes it in semitones from its own pitch;
// legato or sustain shorten it, like a choke.
//
//   d1 $ s "sdgping*8" # n "<0 2>" # note "0 7 12 3"
//   d1 $ s "sdgping:1(5,8)" # note (range (-5) 12 rand) # pF "sdgdecay" 0.1
//
// examples/tidal/lessons/19-drums-small-machines.tidal plays it.
(
var name = \sdgping;
SynthDef(name, { |out, sustain = 3, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 1200, sdgdecay = 0.09, sdgpunch = 12, sdgbend = 0.025, sdgmetal = 0.15,
	sdgtone = 0.8, sdgindex = 0, sdgratio = 1.41, sdgcolor = 0, sdgclick = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var dec = sdgdecay.clip(0.005, 2);
	var tone = sdgtone.clip(0, 1);
	var metal = sdgmetal.clip(0, 1);
	var index = sdgindex.clip(0, 8);
	var rattle = sdgcolor.clip(0, 1);
	var nyq = SampleRate.ir * 0.45;
	var env = EnvGen.ar(Env([1, 1, 0], [dec * 1.3 + 0.01, 0.005]), doneAction: 2);
	var sweep = EnvGen.ar(Env([sdgpunch.clip(-24, 48).midiratio, 1], [sdgbend.clip(0.001, 1)], \exp));
	// clipped after the sweep: past Nyquist the FM sine would fold back down, to DC at worst
	var pitch = (sdgfreq.clip(40, 12000) * tune * sweep * Line.kr(1, 1 + accelerate, dec)).clip(20, nyq);
	// a struck free bar's modes against plain harmonics, met halfway geometrically
	var ratios = [1, 2 * (1.38 ** metal), 3 * (1.8 ** metal)];
	var amps = [1, 0.25 + (tone * 0.45), 0.08 + (tone * 0.35)];
	var decays = dec * [1, 0.55, 0.3];
	// a one-sample click rings every partial; a smooth pulse as long as 0.7 periods is a soft
	// mallet that rings the main one as hard and the upper ones much less. both have unit area
	var width = (0.7 / (sdgfreq.clip(40, 12000) * tune * sdgpunch.clip(0, 48).midiratio)).clip(SampleDur.ir * 4, 0.02);
	var mallet = EnvGen.ar(Env.sine(width)) * (2 / (width * SampleRate.ir));
	var exc = (Impulse.ar(0) * tone) + (mallet * (1 - tone))
		+ (Dust.ar(30 + (rattle * 250)) * EnvGen.ar(Env.perc(0.002, dec * 0.6)) * rattle * 0.35);
	var rings = ratios.collect { |r, i|
		Ringz.ar(exc, (pitch * r).clip(20, nyq), decays[i]) * amps[i]
	};
	// FM on the main resonator: a decaying FM sine standing in for it (Ringz only retunes once a block)
	var fmEnv = EnvGen.ar(Env([1, 0.001], [dec], \exp));
	var fm = SinOsc.ar(pitch, (SinOsc.ar((pitch * sdgratio.clip(0.25, 8)).min(nyq)) * index
		* EnvGen.ar(Env([1, 0.05], [dec * 0.5], \exp))) + 0.5pi) * fmEnv * 2;
	// a whole-number ratio puts a sideband on 0 Hz (DC 0.018 at sdgratio 1). it lasts as long as the
	// ring, too short for LeakDC, which only smears it: a high pass under the pitch takes it
	var main = XFade2.ar(rings[0], HPF.ar(fm, (pitch * 0.3).max(20)), (index.min(1) * 2 - 1));
	// a click starts every partial at its peak at once, a mallet does not
	var sig = (main + rings[1] + rings[2]) * 0.8 / (1 + (tone * (amps[1] + amps[2])));
	sig = sig + (HPF.ar(WhiteNoise.ar, 2500) * EnvGen.ar(Env.perc(0.0002, 0.003)) * sdgclick.clip(0, 1) * 0.6);
	sig = sig.tanh;	// a noise burst or a rattle can ring a Ringz past 2
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "blip", sdgfreq: 1200, sdgdecay: 0.09, sdgpunch: 12, sdgbend: 0.025, sdgmetal: 0.15, sdgtone: 0.8),	// 0 blip
	(presetName: "droplet", sdgfreq: 700, sdgdecay: 0.07, sdgpunch: -14, sdgbend: 0.06, sdgmetal: 0, sdgtone: 0.3),	// 1 droplet
	(presetName: "metal tick", sdgfreq: 3400, sdgdecay: 0.05, sdgpunch: 3, sdgbend: 0.004, sdgmetal: 1, sdgtone: 1, sdgclick: 0.4),	// 2 metal tick
	(presetName: "wood", sdgfreq: 820, sdgdecay: 0.08, sdgpunch: 2, sdgbend: 0.005, sdgmetal: 0.7, sdgtone: 0.5),	// 3 wood
	(presetName: "zap", sdgfreq: 1800, sdgdecay: 0.22, sdgpunch: 30, sdgbend: 0.09, sdgmetal: 0.4, sdgtone: 0.9, sdgindex: 2.5, sdgratio: 1.41),	// 4 zap
	(presetName: "rubber", sdgfreq: 260, sdgdecay: 0.18, sdgpunch: 5, sdgbend: 0.02, sdgmetal: 0.3, sdgtone: 0.5, sdgcolor: 0.6),	// 5 rubber
	(presetName: "sonar", sdgfreq: 1500, sdgdecay: 1.2, sdgpunch: 0, sdgmetal: 0, sdgtone: 0.3, sdgindex: 0),	// 6 sonar
	(presetName: "laser", sdgfreq: 900, sdgdecay: 0.3, sdgpunch: 48, sdgbend: 0.2, sdgmetal: 0.2, sdgtone: 1, sdgindex: 4, sdgratio: 2),	// 7 laser
	(presetName: "bar", sdgfreq: 523.25, sdgdecay: 0.3, sdgpunch: 0, sdgmetal: 1, sdgtone: 0.35),	// 8 bar
	(presetName: "glitch rattle", sdgfreq: 2500, sdgdecay: 0.12, sdgpunch: 7, sdgbend: 0.01, sdgmetal: 0.6, sdgtone: 0.65, sdgcolor: 0.4, sdgclick: 0.5)	// 9 glitch rattle
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
