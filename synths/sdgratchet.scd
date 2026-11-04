// synths/sdgratchet.scd - a roll in one event: sdgcount clicks spread over
// sdglen seconds, evenly or like a bouncing ball, each one pinging a resonant
// filter whose pitch walks from click to click.
//
// parameters:
//   sdgcount   1 to 32               clicks in the roll
//   sdglen     0.01 to 2 s           from the first click to the last
//   sdgcurve   -1 to 1               0 even, above 0 faster and quieter (a bouncing ball), below 0 slowing
//   sdgfreq    60 to 12000 Hz        the filter's pitch at the first click, at note 0
//   sdgwalk    -48 to 48 semitones   how far the pitch walks over the roll
//   sdgcolor   0 to 1                each click's pitch scattered at random, up to an octave
//   sdgdecay   0.002 to 0.5 s        how long the filter rings after each click
//   sdgtone    0 to 1                0 a clean ping, 1 a noisy tick on every click
//   sdgbits    0 to 1                bit and sample-rate crush, 0 clean
//   sdgspread  0 to 1                clicks thrown left and right in turn
//
// the gaps shrink or grow by one ratio, so at sdgcurve 1 the last gap is 1/32 of
// the first, at -1 32 times it. presets, by n: 0 bounce, 1 ratchet, 2 slowdown,
// 3 zipper, 4 crushed. note tunes it; legato or sustain cut the roll short.
//
//   d1 $ s "sdgratchet ~ ~ ~" # pF "sdgcount" "<4 8 16>"
//   d1 $ s "[sdgratchet:1, sdgkick]" # pF "sdgcurve" (range (-1) 1 sine)
//
// examples/tidal/19-drums-autechre.tidal plays it.
(
var name = \sdgratchet;
SynthDef(name, { |out, sustain = 3, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgcount = 12, sdglen = 0.5, sdgcurve = 0.8, sdgfreq = 1400, sdgwalk = 7, sdgcolor = 0,
	sdgdecay = 0.035, sdgtone = 0.3, sdgbits = 0, sdgspread = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var n = sdgcount.clip(1, 32).round;
	var len = sdglen.clip(0.01, 2);
	var curve = sdgcurve.clip(-1, 1);
	var dec = sdgdecay.clip(0.002, 0.5);
	var tone = sdgtone.clip(0, 1);
	var bits = sdgbits.clip(0, 1);
	var nyq = SampleRate.ir * 0.45;
	// n clicks, n - 1 gaps, each gap r times the one before; last / first = 2 ** (-5 * curve)
	var gaps = (n - 1).max(1);
	var r = 2 ** (curve.neg * 5 / (gaps - 1).max(1));
	var total = Select.kr((r - 1).abs < 1e-6, [(1 - (r ** gaps)) / (1 - r), gaps]);
	var first = len / total;
	// a bouncing ball's bounce is as loud as its gap is long: the level follows the gaps
	var fade = r ** (curve * 0.6);	// < 1 either way: a roll fades whichever way it bends
	var trig = TDuty.ar(Dgeom(first, r, n), 0, Dgeom(1, fade, n));
	var step = sdgwalk.clip(-48, 48) / gaps;
	var semis = Demand.ar(trig, 0, Dseries(0, step, n) + (Dwhite(-12, 12, n) * sdgcolor.clip(0, 1)));
	var side = Demand.ar(trig, 0, Dseq([-1, 1], inf)) * sdgspread.clip(0, 1);
	var pitch = (sdgfreq.clip(60, 12000) * tune * semis.midiratio).clip(20, nyq)
		* Line.kr(1, 1 + accelerate, len + dec);
	// Ringz reads its pitch once a block: the click waits two blocks so it rings at its own
	// pitch, and the ring before it glides there, as one stepped analog filter would
	var hit = DelayN.ar(trig, 0.02, ControlDur.ir * 2);
	var tick = WhiteNoise.ar * EnvGen.ar(Env.perc(0.0002, 0.0015), hit) * Latch.ar(hit, hit);
	var env = EnvGen.ar(Env([1, 1, 0], [(len * (n > 1)) + (dec * 1.3) + (ControlDur.ir * 2) + 0.01, 0.005]), doneAction: 2);
	var sig = Ringz.ar(hit + (tick * tone * 0.12), pitch, dec);
	sig = sig + (HPF.ar(tick, 2000) * tone * 0.5);
	// crush: down to 1.4 kHz and 3 bits at 1 (at 44.1 kHz)
	// Latch only moves on a rising trigger, so its clock stays at or under half the sample
	// rate; the first tenth of the knob fades the crush in rather than jumping to it
	sig = SelectX.ar((bits * 10).min(1), [sig,
		(Latch.ar(sig, Impulse.ar(SampleRate.ir * 0.5 * (2 ** (bits * -4))))
			* (2 ** (15 - (bits * 13)))).round / (2 ** (15 - (bits * 13)))]);
	sig = LeakDC.ar(sig).tanh;
	sig = Pan2.ar(sig, Latch.ar(side, hit));
	Out.ar(out, DirtPan.ar(sig, ~dirt.numChannels, pan, env));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(sdgcount: 12, sdglen: 0.5, sdgcurve: 0.8, sdgfreq: 1400, sdgwalk: 7, sdgdecay: 0.035, sdgtone: 0.3),	// 0 bounce
	(sdgcount: 6, sdglen: 0.12, sdgcurve: 0, sdgfreq: 900, sdgwalk: 0, sdgdecay: 0.03, sdgtone: 0.5),	// 1 ratchet
	(sdgcount: 10, sdglen: 0.9, sdgcurve: -0.7, sdgfreq: 2600, sdgwalk: -12, sdgdecay: 0.05, sdgtone: 0.2, sdgspread: 0.6),	// 2 slowdown
	(sdgcount: 32, sdglen: 0.45, sdgcurve: 0.5, sdgfreq: 1800, sdgwalk: 24, sdgdecay: 0.012, sdgtone: 0.6),	// 3 zipper
	(sdgcount: 5, sdglen: 0.2, sdgcurve: 0.3, sdgfreq: 420, sdgwalk: -5, sdgdecay: 0.06, sdgtone: 0.4, sdgbits: 0.75)	// 4 crushed
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
