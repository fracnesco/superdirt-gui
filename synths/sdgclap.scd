// synths/sdgclap.scd - a hand clap: noise through a band pass, struck as a few bursts a
// hand's width apart, the last one longer, with a short room after it. as in the 808 and
// 909; one burst and a narrow band make a finger snap.
//
// parameters:
//   sdgfreq    300 to 4000 Hz  the band's centre at note 0
//   sdgq       0 to 1          how narrow the band is
//   sdgcount   1 to 8          how many hands, the bursts
//   sdgcolor   0 to 1          how loose they are: 4 to 24 ms apart, more uneven
//   sdgdecay   0.01 to 1 s     how long the last burst lasts
//   sdgtail    0 to 1          the room after it, louder and longer
//   sdgtone    0 to 1          dark to bright: air above the band
//   sdgspread  0 to 1          stereo width: each hand somewhere else
//
// presets, by n: 0 808, 1 909, 2 tight, 3 big room, 4 snap, 5 trap, 6 real hands, 7 crowd,
// 8 lofi, 9 micro. note moves the band in semitones; legato or sustain shorten it, like a
// choke. every clap is a little different.
//
//   d1 $ s "~ sdgclap ~ sdgclap"
//   d1 $ s "~ sdgclap:3 ~ [sdgclap:4 sdgclap:4]" # pF "sdgspread" 0.6
//
// examples/tidal/lessons/16-drums-classic.tidal plays it.
(
var name = \sdgclap;
SynthDef(name, { |out, sustain = 1.5, pan = 0.5, freq = 261.6256, accelerate = 0,
	sdgfreq = 1100, sdgq = 0.45, sdgcount = 4, sdgcolor = 0.35, sdgdecay = 0.12,
	sdgtail = 0.35, sdgtone = 0.3, sdgspread = 0|
	var tune = freq / 261.6256;	// note 0 is the voice's own pitch
	var color = sdgcolor.clip(0, 1);
	var count = sdgcount.clip(1, 8).round;
	var decay = sdgdecay.clip(0.01, 1);
	var tail = sdgtail.clip(0, 1);
	var tone = sdgtone.clip(0, 1);
	var spread = sdgspread.clip(0, 1);
	var taillen = tail.linlin(0, 1, 0.15, 1.3);
	var centre = (sdgfreq.clip(300, 4000) * tune * Line.kr(1, 1 + accelerate, decay + 0.05)).clip(100, 16000);
	var rq = sdgq.clip(0, 1).linexp(0, 1, 1.4, 0.12);
	// the 808 claps at 0, 11, 23 and 31 ms: each gap a little off, more when looser
	var gap = color.linlin(0, 1, 0.004, 0.024);
	var starts = [0] ++ Array.fill(7, { gap * (1 + (Rand(-1, 1) * color * 0.35)) }).integrate;
	var last = Select.kr(count - 1, starts);
	// equal-power pan per hand: [left, right] gains, centred when spread is 0
	var place = { var a = (Rand(-1, 1) * spread + 1) * 0.25pi; [a.cos, a.sin] };
	var hands = starts.collect { |t, i|
		// a short burst: -27 dB in 9 ms, as each of the 808's, and only the first count - 1
		EnvGen.ar(Env([0, 0, 0.7, 0.0007, 0], [t, 0.0003, 0.02, 0.001], [0, 0, \exp, 0]))
			* ((count - 1) > i) * place.value
	}.sum;
	var body = EnvGen.ar(Env([0, 0, 1, 0.001, 0], [last, 0.0003, decay, 0.002], [0, 0, \exp, 0]))
		* place.value;
	var room = EnvGen.ar(Env([0, 1, 0.001, 0], [0.012, taillen, 0.002], [2, \exp, 0])) * tail * 0.32 * 4.3;
	var a = WhiteNoise.ar, b = WhiteNoise.ar;
	var band = BPF.ar(a, centre, rq) * rq.reciprocal.sqrt * 4.3;	// the same loudness however narrow
	var air = HPF.ar(a, (centre * 3).clip(2000, 12000)) * tone * 1.5;
	var wet = [a, (a * (1 - spread)) + (b * spread)].collect { |n|
		BPF.ar(n, centre * 0.9, (rq * 2).min(2)) * (rq * 2).min(2).reciprocal.sqrt
	};
	var sig = ((band + air) * (hands + body)) + (wet * room);
	sig = HPF.ar(sig, (centre * 0.4).clip(80, 2000));
	sig = (sig / 1.9).tanh * 1.9;	// shaves the rare noise spike
	EnvGen.kr(Env([0, 0], [max(last + decay, taillen + 0.012) + 0.01]), doneAction: 2);
	// a short clap reads quieter than a long one at the same peak: lift it a little
	Out.ar(out, DirtPan.ar(sig * 0.9 * (0.12 / decay).pow(0.3).clip(0.8, 1.15), ~dirt.numChannels, pan));
}).add;

// presets: the sound library's events for this synth. useSynthDefSustain makes an
// event last the def's sustain, not one step; note: 0 keeps n from also transposing.
[
	(presetName: "808", sdgfreq: 1100, sdgq: 0.45, sdgcount: 4, sdgcolor: 0.35, sdgdecay: 0.12, sdgtail: 0.35, sdgtone: 0.3, sdgspread: 0),	// 0 808
	(presetName: "909", sdgfreq: 1250, sdgq: 0.35, sdgcount: 4, sdgcolor: 0.3, sdgdecay: 0.14, sdgtail: 0.5, sdgtone: 0.55, sdgspread: 0.15),	// 1 909
	(presetName: "tight", sdgfreq: 1300, sdgq: 0.4, sdgcount: 3, sdgcolor: 0.1, sdgdecay: 0.07, sdgtail: 0.15, sdgtone: 0.4, sdgspread: 0),	// 2 tight
	(presetName: "big room", sdgfreq: 1000, sdgq: 0.3, sdgcount: 5, sdgcolor: 0.7, sdgdecay: 0.18, sdgtail: 0.85, sdgtone: 0.35, sdgspread: 0.8),	// 3 big room
	(presetName: "snap", sdgfreq: 2200, sdgq: 0.65, sdgcount: 1, sdgcolor: 0, sdgdecay: 0.035, sdgtail: 0.05, sdgtone: 0.7, sdgspread: 0),	// 4 snap
	(presetName: "trap", sdgfreq: 1400, sdgq: 0.35, sdgcount: 5, sdgcolor: 0.45, sdgdecay: 0.16, sdgtail: 0.4, sdgtone: 0.65, sdgspread: 0.5),	// 5 trap
	(presetName: "real hands", sdgfreq: 900, sdgq: 0.25, sdgcount: 3, sdgcolor: 0.6, sdgdecay: 0.09, sdgtail: 0.15, sdgtone: 0.45, sdgspread: 0.2),	// 6 real hands
	(presetName: "crowd", sdgfreq: 1000, sdgq: 0.2, sdgcount: 8, sdgcolor: 1, sdgdecay: 0.2, sdgtail: 0.6, sdgtone: 0.5, sdgspread: 1),	// 7 crowd
	(presetName: "lofi", sdgfreq: 800, sdgq: 0.45, sdgcount: 4, sdgcolor: 0.35, sdgdecay: 0.13, sdgtail: 0.25, sdgtone: 0.15, sdgspread: 0),	// 8 lofi
	(presetName: "micro", sdgfreq: 2600, sdgq: 0.5, sdgcount: 2, sdgcolor: 0.15, sdgdecay: 0.03, sdgtail: 0.1, sdgtone: 0.8, sdgspread: 0.3)	// 9 micro
].do { |preset, i|
	~dirt.soundLibrary.addSynth(name, (instrument: name, note: 0) ++ preset,
		appendToExisting: i > 0, useSynthDefSustain: true)
};
)
