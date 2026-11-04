// synths/sdgplastic.scd - hyper-real material percussion: bubbles, latex, squeaks,
// plastic knocks and drips. a sine whose pitch moves a long way very fast, bent by fm
// and folding, through a resonant filter that follows it, and squashed at the end.
//
// parameters:
//   sdgfreq      30 to 9000 Hz      the pitch, before note
//   sdgdecay     0.005 to 2 s       how long it rings
//   sdgatk       0.0005 to 0.5 s    how it starts: a rub swells, a snap does not
//   sdgpunch     0 to 48 semitones  how far above its pitch it starts: a snap
//   sdgbend      0.001 to 1 s       how long that fall takes
//   sdgrise      0 to 36 semitones  how far it rises as it dies: a bubble
//   sdgwobble    0 to 1             a wobble in pitch that dies away, rubber
//   sdgslip      0 to 1             catches and slips, the stick-slip of a squeak
//   sdgcolor     0 to 1             how fast the wobble and the slips go, 6 to 160 Hz
//   sdgratio     0.25 to 8 x        fm: the modulator against the pitch
//   sdgindex     0 to 10            fm: how much, falling over the hit
//   sdgwavefold  0 to 1             folding: shine
//   sdgq         0 to 1             resonance of the filter; high, it pings
//   sdgtone      0 to 1             the filter, from the pitch to 16 times it
//   sdgclick     0 to 1             a knock: the strike's own hollow modes
//   sdgdrive     0 to 1             how hard it is squashed at the end
//
// presets, by n: 0 bubble, 1 latex snap, 2 squeak, 3 plastic clank, 4 drip, 5 rubber.
// note tunes it in semitones; legato or sustain shorten it, like a choke.
//
//   d1 $ s "sdgplastic*8" # note (irand 12) # pan rand
//   d1 $ s "sdgplastic:1 ~ sdgplastic:3 sdgplastic:1" # pF "sdgwobble" "<0.2 0.8>"
//
// examples/tidal/18-drums-sophie.tidal plays it.
(
var name = \sdgplastic;
SynthDef(name, { |out, sustain = 2.6, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 600, sdgdecay = 0.07, sdgatk = 0.0005, sdgpunch = 0, sdgbend = 0.02,
	sdgrise = 14, sdgwobble = 0, sdgslip = 0, sdgcolor = 0.5, sdgratio = 1.5,
	sdgindex = 0, sdgwavefold = 0, sdgq = 0.2, sdgtone = 0.5, sdgclick = 0, sdgdrive = 0.2|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var decay = sdgdecay.clip(0.005, 2), atk = sdgatk.clip(0.0005, 0.5);
	var env = EnvGen.ar(Env.perc(atk, decay, 1, -5), doneAction: 2);
	var rate = sdgcolor.clip(0, 1).linexp(0, 1, 6, 160);
	var slip = sdgslip.clip(0, 1);
	var base = sdgfreq.clip(30, 9000) * tune;
	var wob = SinOsc.ar(rate * LFNoise1.kr(7).range(0.9, 1.1))
		* sdgwobble.clip(0, 1) * EnvGen.ar(Env.perc(0.002, atk + (decay * 0.8), 1, -3));
	// stick-slip: the surface catches and lets go at an uneven rate
	var slips = Lag.ar(LFPulse.ar(rate * LFNoise1.kr(30).range(0.8, 1.25), 0, 0.3), 0.0005);
	var pitch = base * Line.kr(1, 1 + accelerate, atk + decay)
		* EnvGen.ar(Env([sdgpunch.clip(0, 48).midiratio, 1], [sdgbend.clip(0.001, 1)], -6))
		* EnvGen.ar(Env([1, 1, sdgrise.clip(0, 36).midiratio], [atk, decay], 3))	// rises as it dies
		* (2 ** (wob * 0.5))	// up to half an octave either way
		* (1 + (slips * slip * 0.06));
	var ienv = EnvGen.ar(Env.perc(0.0005, (decay * 0.6).max(0.004), 1, -5));
	var drive = sdgdrive.clip(0, 1).linexp(0, 1, 1.2, 16);
	var q = sdgq.clip(0, 1), sig, knock;
	pitch = pitch.clip(20, 16000);
	sig = SinOsc.ar(pitch, SinOsc.ar((pitch * sdgratio.clip(0.25, 8)).min(20000)) * sdgindex.clip(0, 10) * ienv);
	sig = sig * (1 - (slip * (1 - slips) * 0.85));
	sig = (sig * (1 + (sdgwavefold.clip(0, 1) * 5))).fold2(1) * (1 + (sdgwavefold.clip(0, 1) * 0.8));	// folding moves energy up, where the filter takes some
	// the knock: a click on three hollow, quickly damped modes, then the filter pings on it too
	knock = Ringz.ar(Impulse.ar(0), (base * [1.0, 2.31, 3.93]).clip(20, 16000), [0.03, 0.018, 0.01]).sum * 0.35;
	sig = sig + (knock * sdgclick.clip(0, 1));
	sig = MoogFF.ar(sig, (pitch * (2 ** (sdgtone.clip(0, 1) * 4))).clip(30, 18000), q * 3.5) * (1 + (q * 1.5));
	sig = HPF.ar(sig, (base * 0.5).clip(25, 2000));	// the knock's thump and any dc go
	sig = (sig * drive).tanh / drive.tanh;
	// a short hit is played hotter, so that the knob changes length more than loudness
	sig = sig * 0.45 * (0.15 / (atk + decay)).clip(1, 6).sqrt;
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(sdgfreq: 600, sdgdecay: 0.07, sdgrise: 14),                                        // 0 bubble
	(sdgfreq: 220, sdgdecay: 0.22, sdgpunch: 24, sdgbend: 0.035, sdgrise: 0,
		sdgwobble: 0.5, sdgcolor: 0.45, sdgratio: 2, sdgindex: 1.5, sdgwavefold: 0.4,
		sdgq: 0.6, sdgtone: 0.4, sdgclick: 0.4, sdgdrive: 0.5),                         // 1 latex snap
	(sdgfreq: 2200, sdgdecay: 0.16, sdgatk: 0.03, sdgrise: 3, sdgslip: 0.8,
		sdgcolor: 0.6, sdgq: 0.5, sdgtone: 0.1, sdgdrive: 0.3),                          // 2 squeak
	(sdgfreq: 380, sdgdecay: 0.05, sdgpunch: 12, sdgbend: 0.008, sdgrise: 0,
		sdgratio: 1.73, sdgindex: 3, sdgwavefold: 0.2, sdgq: 0.85, sdgtone: 0.3,
		sdgclick: 1, sdgdrive: 0.6),                                                    // 3 plastic clank
	(sdgfreq: 1000, sdgdecay: 0.03, sdgrise: 24, sdgq: 0.3, sdgclick: 0.1),             // 4 drip
	(sdgfreq: 140, sdgdecay: 0.45, sdgatk: 0.01, sdgrise: 7, sdgwobble: 0.8,
		sdgslip: 0.5, sdgcolor: 0.3, sdgwavefold: 0.5, sdgq: 0.4, sdgtone: 0.5,
		sdgdrive: 0.5)                                                                  // 5 rubber
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
