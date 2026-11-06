// samples/render.scd - the drum banks in this folder, rendered from the drum voices.
// this is an editor document: evaluate the blocks in order, never .load it.
//
// every sound file in samples/ was made here, from the voices in synths/ (sdgkick,
// sdgsnare ... sdgchaos): each one played once, offline, into a file. as samples they
// cost next to nothing to play, chop and striate cut them like any break, the sample
// players read them (sdglayer, sdgwarp, sdgcrush ...), and one bank holds what only
// rendering gives: three voices stacked into one hit, cymbals backwards, hits through
// long feedback, crushed and re-pitched copies, and bars played by the voices.
//
//   sdgbd     kicks
//   sdgsd     snares and claps
//   sdghh     hats and cymbals
//   sdgperc   toms, rims, bells, struck things
//   sdgfx     the hyper-real and the broken: bubbles, slams, pings, rolls
//   sdgresamp stacks, reversed, fed back, crushed, and four one-bar loops at 120 bpm
//
// a file's name starts with its index, so n stays where it is: sdgbd:3 is 03_hard.wav.
// the lists in block 3 say what each one is.
//
// levels: a one-shot is written as loud as its role is meant to sound - kick, snare,
// clap, hat, cymbal ... - the same targets the voices are tuned to, so sdgbd against sdgsd
// sits like bd against sn, and a hat is a hat's level under them, not as loud as the kick.
// no file goes past full scale: a kick that would need more stays at full scale, where
// every normalized Dirt-Samples kick is. a loop is normalized to 1 dB under full scale,
// as the breaks are. 16-bit wav at 44.1 kHz, mono unless the sound is stereo (the 909
// and big claps, the crash, the slow roll).
//
// rendering does not touch your server or your orbits. each file is made by a server
// of its own, offline (Score.recordNRT), faster than real time: about a second a file.
// the voices and their presets are read from ~dirt, so SuperDirt and SuperDirtGUI must
// be running (examples/superdirt-gui-demo.scd, block 1).

// ---- 1. play them: one bank per folder, loaded with ~sdg itself (SuperDirtGUI.loadOwnSamples);
//         after a re-render, read the folder again for what changed
~sdg.loadSamples(~sdg.path +/+ "samples");
//   d1 $ s "sdgbd sdgsd:7 sdgbd sdgsd"
//   d1 $ s "sdghh*8" # n "0 0 2 0"
//   d1 $ chop 16 $ s "sdgresamp:12"
//   d1 $ s "sdgbd:4*4" # pF "sdglayer" 0.4 # pF "sdgfreq" 45

// ---- 2. the tools. evaluate once; block 3 and 5 use them
(
~renderFolder = ~sdg.path +/+ "samples";             // banks go into subfolders of this
~renderTemp = Platform.defaultTempDir +/+ "sdgrender"; // the raw renders, deleted after
~renderRate = 44100;

// how loud each role plays at gain 1 on the orbit, in LUFS (the loudest 400 ms):
// the targets the drum voices were tuned to
~roleLoudness = (kick: -24, snare: -26, clap: -28, closedhat: -34, openhat: -28,
	cymbal: -27, tom: -27, perc: -32, metal: -30, fx: -28);

// one voice, one preset (n), and settings of your own: a single hit at time 0
~one = { |name, n = 0, params| (voices: [[0, name, n, params ? ()]]) };

// a bar from step strings, x a hit: [voice, n, settings, "x...x...x...x..."]
~steps = { |rows, bpm = 120|
	var step = 60 / bpm / 4, hits = List.new;	// a 16th
	rows.do { |row|
		row[3].do { |c, i| if(c == $x) { hits.add([i * step, row[0], row[1], row[2] ? ()]) } }
	};
	hits.asArray
};

// what SuperDirt would send the voice: the preset's settings, yours over them, pitch from
// note (note 0 is 261.6 Hz, the voices' own pitch) and pan in the middle (0, after
// SuperDirt has made Tidal's 0..1 into -1..1)
~voiceArgs = { |name, n, params|
	var desc = SynthDescLib.global[name];
	var presets = ~dirt.soundLibrary.synthEvents[name];
	var all = (presets !? { presets.wrapAt(n ? 0) } ?? { () }).copy.putAll(params ? ());
	var args = [\freq, 261.6256 * (all[\note] ? 0).midiratio, \pan, (all[\pan] ? 0.5) * 2 - 1];
	if(desc.isNil) { Error("render: no voice called %".format(name)).throw };
	desc.controlNames.do { |key|
		if(#[\out, \freq, \pan].includes(key).not) {
			all[key] !? { |v| if(v.isNumber) { args = args ++ [key, v] } }
		}
	};
	args
};

// effects for the resampled bank: they read the voices' bus and write the file. they are
// only ever sent to the offline server
~renderFx = (
	// each echo a little lower than the last: a pitch shifter inside a feedback loop
	spiral: SynthDef(\sdgrender_spiral, { |in, out, time = 0.13, fb = 0.78, ratio = 0.94, cut = 5000|
		var dry = In.ar(in, 2).sum * 0.5;
		var back = LocalIn.ar(1);
		var wet = DelayC.ar(dry + back, 1, time - ControlDur.ir);
		LocalOut.ar(LeakDC.ar(LPF.ar(PitchShift.ar(wet, 0.1, ratio, 0, 0.004), cut)).tanh * fb);
		Out.ar(out, (dry + wet) ! 2)
	}),
	// six combs at a cymbal's out-of-tune ratios: whatever goes in rings like metal
	metal: SynthDef(\sdgrender_metal, { |in, out, freq = 180, decay = 2.5|
		var dry = In.ar(in, 2).sum * 0.5;
		var wet = CombC.ar(dry, 0.05, (freq * [1, 1.47, 2.09, 2.56, 3.37, 4.18]).reciprocal, decay).sum;
		Out.ar(out, LeakDC.ar(dry + (LPF.ar(wet, 7000) * 0.15)).tanh ! 2)
	}),
	// the hit convolved with another sound, the kernel (its first 0.37 s): the kick struck
	// through a metal plate. the convolution comes a frame late, so the dry waits for it
	conv: SynthDef(\sdgrender_conv, { |in, out, kernel, dry = 0.5, wet = 0.02|
		var sig = In.ar(in, 2).sum * 0.5, late = 16384 / SampleRate.ir;
		Out.ar(out, ((DelayN.ar(sig, late, late) * dry) + (Convolution2.ar(sig, kernel, 0, 16384) * wet)) ! 2)
	}),
	// a file played back at another rate through a sampler's few bits and low rate
	resample: SynthDef(\sdgrender_resample, { |out, buf, rate = 1, bits = 16, down = 0|
		var sig = PlayBuf.ar(2, buf, rate * BufRateScale.ir(buf));
		var steps = 2 ** (bits - 1);
		// down 0 keeps the rate: an Impulse at the sample rate itself never fires
		sig = Select.ar(down > 0, [sig, Latch.ar(sig, Impulse.ar(down))]);
		Out.ar(out, (sig * steps).round / steps)
	})
);

// render [[time, voice, n, settings], ...] offline into a 2-channel float file. an effect
// (a key of ~renderFx) gets the voices on a bus of their own; a kernel is a file the conv
// effect reads. waits until the file is written, so call it inside a Routine (fork)
~renderNRT = { |hits, seconds, path, fx, fxArgs, kernelPath|
	var score = List.new, done = Condition.new, out = 0, defs;
	var options = ServerOptions.new.numOutputBusChannels_(2).numInputBusChannels_(0)
		.sampleRate_(~renderRate).memSize_(8192 * 16).numWireBufs_(256);
	defs = hits.collect { |h| SynthDescLib.global[h[1]].def }.asSet.asArray;
	defs.do { |def| score.add([0.0, ['/d_recv', def.asBytes]]) };
	kernelPath !? { score.add([0.0, ['/b_allocReadChannel', 0, kernelPath, 0, 16384, 0]]) };
	fx !? {
		out = 2;	// the first bus after the two outputs
		score.add([0.0, ['/d_recv', ~renderFx[fx].asBytes]]);
		score.add([0.0, ['/s_new', ~renderFx[fx].name, 999, 1, 0, \in, 2, \out, 0, \kernel, 0] ++ (fxArgs ? [])]);
	};
	// in time order: the offline server reads the score from the top
	hits.copy.sort { |a, b| a[0] <= b[0] }.do { |h, i|
		score.add([h[0], ['/s_new', h[1], 1000 + i, 0, 0, \out, out] ++ ~voiceArgs.(h[1], h[2], h[3])])
	};
	score.add([seconds, ['/c_set', 0, 0]]);
	Score(score.asArray).recordNRT(path ++ ".osc", path, nil, ~renderRate, "WAV", "float", options, "",
		seconds, { done.unhang });
	done.hang;
	File.delete(path ++ ".osc");
	path
};

// a rendered file played again at another rate, crushed: then (rate:, bits:, down:)
~renderResample = { |inPath, outPath, then, seconds|
	var score = [
		[0.0, ['/d_recv', ~renderFx[\resample].asBytes]],
		[0.0, ['/b_allocRead', 0, inPath]],
		[0.0, ['/s_new', \sdgrender_resample, 1000, 0, 0, \buf, 0, \rate, then[\rate] ? 1,
			\bits, then[\bits] ? 16, \down, then[\down] ? 0]],
		[seconds, ['/c_set', 0, 0]]
	], done = Condition.new;
	var options = ServerOptions.new.numOutputBusChannels_(2).numInputBusChannels_(0).sampleRate_(~renderRate);
	Score(score).recordNRT(outPath ++ ".osc", outPath, nil, ~renderRate, "WAV", "float", options, "",
		seconds, { done.unhang });
	done.hang;
	File.delete(outPath ++ ".osc");
	outPath
};

// from the raw render to the file in the bank: mono if both sides are the same, the
// silence before the hit and after it (60 dB under the peak) cut, a fade at the end,
// backwards if asked, the level of its role, 16 bits
~finish = { |inPath, outPath, role, spec|
	var file = SoundFile.openRead(inPath), data, n, sides, chans, env, peak, floor, first, last;
	var fadeOut, loud, gainDb, frames, nc, out, hops, padded, mono, offset;
	// block: a silent render stops here, with nothing written
	block { |stop|
		data = FloatArray.newClear(file.numFrames * 2);
		file.readData(data); file.close;
		n = data.size div: 2;
		sides = 2.collect { |c| var s = Signal.newClear(n); n.do { |i| s[i] = data[(i * 2) + c] }; s };
		peak = max(sides[0].abs.maxItem, sides[1].abs.maxItem);
		if(peak <= 0) { "render: % came out silent, nothing written".format(outPath.basename).warn; stop.(nil) };
		mono = spec[\stereo] != true and: { (sides[0] - sides[1]).abs.maxItem <= (peak * 1e-4) };
		chans = if(mono) { [sides[0]] } { sides };
		env = if(mono) { chans[0].abs } { chans[0].abs.max(chans[1].abs) };
		if(spec[\loop].notNil) {
			// a loop keeps its length to the frame, from time 0
			first = 0; last = (spec[\loop] * ~renderRate).round.asInteger.min(n) - 1;
			fadeOut = 0.005;
		} {
			floor = peak * (spec[\floor] ? -60).dbamp;
			first = env.detectIndex { |x| x > floor } ? 0;
			last = n - 1 - (env.reverse.detectIndex { |x| x > floor } ? 0);
			fadeOut = 0.01;
			spec[\max] !? { |max|
				if((last - first) > (max * ~renderRate)) {
					last = first + (max * ~renderRate).asInteger;
					fadeOut = (max * 0.3).min(0.6);	// cut short: a long fade, not a click
				}
			};
		};
		frames = last - first + 1;
		nc = chans.size;
		chans = chans.collect { |c|
			var s = c.copyRange(first, last).as(Signal), fo = (fadeOut * ~renderRate).asInteger.min(frames);
			fo.do { |i| s[frames - fo + i] = s[frames - fo + i] * cos(i / fo * 0.5pi) };
			if(spec[\reverse] == true) {
				s = s.reverse.as(Signal);
				// the hit, now at the end, stops in 2 ms rather than at once
				88.min(frames).do { |i| s[frames - 1 - i] = s[frames - 1 - i] * (i / 88) };
			};
			s
		};
		peak = chans.collect { |c| c.abs.maxItem }.maxItem;
		// how loud it is (SDGSampleAnalysis is the sample editor's meter): a short hit is
		// measured with silence after it, as a 400 ms window hears it on the orbit
		padded = chans.collect { |c| c ++ Signal.newClear((0.5 * ~renderRate).asInteger) };
		hops = SDGSampleAnalysis.kHops(padded, ~renderRate);
		loud = SDGSampleAnalysis.loudness(hops, ~renderRate)[\momentaryMax];
		// on the orbit at gain 1 a file is SuperDirt's amp 0.4 times the panner's 0.71, on each
		// side, mono or stereo: a mono file (one channel heard on two) plays 7.96 dB under its
		// own loudness, a stereo one 10.97
		offset = if(nc == 1) { 7.96 } { 10.97 };
		gainDb = if(role == \loop) { -1 - peak.ampdb } {
			min(~roleLoudness[role] + offset - loud, peak.reciprocal.ampdb - 0.01)
		};
		out = FloatArray.newClear(frames * nc);
		chans.do { |c, k| frames.do { |i| out[(i * nc) + k] = (c[i] * gainDb.dbamp).clip(-0.9999, 0.9999) } };
		File.mkdir(outPath.dirname);
		file = SoundFile.openWrite(outPath, "WAV", "int16", nc, ~renderRate);
		file.writeData(out); file.close;
		"render: % % % s, peak % dBFS, % LUFS on the orbit (%)".format(
			outPath.dirname.basename +/+ outPath.basename, if(nc == 1) { "mono" } { "stereo" },
			(frames / ~renderRate).round(0.01), (peak.ampdb + gainDb).round(0.1),
			(loud + gainDb - offset).round(0.1), role).postln;
		outPath
	}
};

// one file of a bank: [name, role, spec] - spec as ~one makes it, plus any of
// max: seconds, stereo: true, reverse: true, floor: dB, loop: seconds,
// fx: \spiral / \metal / \conv, fxArgs: [...], kernel: [voice, n, settings],
// then: (rate:, bits:, down:)
~renderFile = { |bank, entry|
	var name = entry[0], role = entry[1], spec = entry[2];
	var raw = ~renderTemp +/+ "%_%.wav".format(bank, name), kernel, seconds;
	File.mkdir(~renderTemp);
	seconds = spec[\loop] ?? { (spec[\voices].collect(_[0]).maxItem ? 0) + (spec[\max] ? 3) + (spec[\tail] ? 0) + 0.2 };
	spec[\kernel] !? { |k| kernel = ~renderNRT.([[0] ++ k], 1, raw ++ "-kernel.wav") };
	~renderNRT.(spec[\voices], seconds, raw, spec[\fx], spec[\fxArgs], kernel);
	spec[\then] !? { |then|
		var again = ~renderResample.(raw, raw ++ "-then.wav", then, seconds / (then[\rate] ? 1) + 0.1);
		File.delete(raw); raw = again;
	};
	~finish.(raw, ~renderFolder +/+ bank +/+ name ++ ".wav", role, spec);
	[raw, kernel].do { |p| p !? { File.delete(p) } };
};
)

// ---- 3. the banks: [file name, role, what plays]. change one, add one, render again
(
~banks = [
	\sdgbd, [
		["00_808long",  \kick, ~one.(\sdgkick, 0)],
		["01_909",      \kick, ~one.(\sdgkick, 1)],
		["02_606",      \kick, ~one.(\sdgkick, 2)],
		["03_hard",     \kick, ~one.(\sdgkick, 3)],
		["04_sub",      \kick, ~one.(\sdgkick, 4)],
		["05_808short", \kick, ~one.(\sdgkick, 0, (sdgdecay: 0.45))],
		["06_909long",  \kick, ~one.(\sdgkick, 1, (sdgdecay: 1.2))],
		["07_fm",       \kick, ~one.(\sdgfmdrum, 0)],
		["08_slam",     \kick, ~one.(\sdgslam, 0)],
		["09_dive",     \kick, ~one.(\sdgslam, 1)],
		["10_boom",     \kick, ~one.(\sdgslam, 5)],
		["11_broken",   \kick, ~one.(\sdgchaos, 2)]
	],
	\sdgsd, [
		["00_808",      \snare, ~one.(\sdgsnare, 0)],
		["01_909",      \snare, ~one.(\sdgsnare, 1)],
		["02_tight",    \snare, ~one.(\sdgsnare, 2)],
		["03_piccolo",  \snare, ~one.(\sdgsnare, 3)],
		["04_fat",      \snare, ~one.(\sdgsnare, 4)],
		["05_fm",       \snare, ~one.(\sdgfmdrum, 1)],
		["06_glitch",   \snare, ~one.(\sdgchaos, 0)],
		["07_latex",    \snare, ~one.(\sdgplastic, 1)],
		["08_clap808",  \clap,  ~one.(\sdgclap, 0)],
		["09_clap909",  \clap,  ~one.(\sdgclap, 1) ++ (stereo: true)],
		["10_claptight", \clap, ~one.(\sdgclap, 2)],
		["11_clapbig",  \clap,  ~one.(\sdgclap, 3) ++ (stereo: true)],
		["12_snap",     \perc,  ~one.(\sdgclap, 4)],
		["13_fmclap",   \clap,  ~one.(\sdgfmdrum, 3)]
	],
	\sdghh, [
		["00_808closed", \closedhat, ~one.(\sdghat, 0)],
		["01_pedal",     \closedhat, ~one.(\sdghat, 1)],
		["02_808open",   \openhat,   ~one.(\sdghat, 2)],
		["03_909closed", \closedhat, ~one.(\sdghat, 3)],
		["04_909open",   \openhat,   ~one.(\sdghat, 4)],
		["05_606closed", \closedhat, ~one.(\sdghat, 5)],
		["06_digital",   \closedhat, ~one.(\sdgchaos, 1)],
		["07_digitalopen", \openhat, ~one.(\sdgchaos, 1, (sdgnoise: 0.45))],
		["08_ride",      \cymbal, ~one.(\sdgcymbal, 0, (sdgspread: 0)) ++ (max: 2.2)],
		["09_crash",     \cymbal, ~one.(\sdgcymbal, 1) ++ (max: 2.2, stereo: true)],
		["10_china",     \cymbal, ~one.(\sdgcymbal, 2, (sdgspread: 0)) ++ (max: 2)],
		["11_bell",      \metal,  ~one.(\sdgcymbal, 4, (sdgspread: 0)) ++ (max: 2.2)],
		["12_ridebell",  \metal,  ~one.(\sdgcymbal, 0, (sdgspread: 0, sdgcolor: 0.85, sdgdecay: 2)) ++ (max: 2)],
		["13_fmmetal",   \metal,  ~one.(\sdgfmdrum, 4)]
	],
	\sdgperc, [
		["00_lowtom",   \tom,   ~one.(\sdgtom, 0)],
		["01_midtom",   \tom,   ~one.(\sdgtom, 1)],
		["02_hitom",    \tom,   ~one.(\sdgtom, 2)],
		["03_conga",    \tom,   ~one.(\sdgtom, 3)],
		["04_bongo",    \tom,   ~one.(\sdgtom, 4)],
		["05_808deep",  \tom,   ~one.(\sdgtom, 5)],
		["06_rim",      \perc,  ~one.(\sdgrim, 0)],
		["07_claves",   \perc,  ~one.(\sdgrim, 1)],
		["08_woodblock", \perc, ~one.(\sdgrim, 2)],
		["09_blip",     \perc,  ~one.(\sdgping, 0)],
		["10_cowbell",  \metal, ~one.(\sdgcowbell, 0)],
		["11_agogo",    \metal, ~one.(\sdgcowbell, 2)],
		["12_marimba",  \perc,  ~one.(\sdgmodal, 1) ++ (max: 1.5)],
		["13_glass",    \metal, ~one.(\sdgmodal, 2) ++ (max: 2)],
		["14_tabla",    \tom,   ~one.(\sdgmodal, 4)],
		["15_wood",     \perc,  ~one.(\sdgping, 3)]
	],
	\sdgfx, [
		["00_bubble",   \fx, ~one.(\sdgplastic, 0)],
		["01_squeak",   \fx, ~one.(\sdgplastic, 2)],
		["02_clank",    \fx, ~one.(\sdgplastic, 3)],
		["03_drip",     \fx, ~one.(\sdgplastic, 4)],
		["04_rubber",   \fx, ~one.(\sdgplastic, 5)],
		["05_slamclank", \fx, ~one.(\sdgslam, 2)],
		["06_gated",    \fx, ~one.(\sdgslam, 3)],
		["07_crunch",   \fx, ~one.(\sdgslam, 4)],
		["08_droplet",  \fx, ~one.(\sdgping, 1)],
		["09_metaltick", \fx, ~one.(\sdgping, 2)],
		["10_zap",      \fx, ~one.(\sdgping, 4)],
		["11_bounce",   \fx, ~one.(\sdgratchet, 0)],
		["12_ratchet",  \fx, ~one.(\sdgratchet, 1)],
		["13_slowdown", \fx, ~one.(\sdgratchet, 2) ++ (stereo: true)],
		["14_zipper",   \fx, ~one.(\sdgratchet, 3)],
		["15_crackle",  \fx, ~one.(\sdgchaos, 3)]
	],
	\sdgresamp, [
		// three voices at once: one hit no single voice makes
		["00_stack",     \kick,  (voices: [[0, \sdgkick, 3], [0, \sdgclap, 1, (sdgspread: 0)], [0, \sdgcowbell, 4]])],
		["01_stackslam", \kick,  (voices: [[0, \sdgslam, 0], [0, \sdgchaos, 0], [0, \sdgplastic, 3]])],
		["02_stacksnare", \snare, (voices: [[0, \sdgsnare, 1], [0, \sdgclap, 0], [0.004, \sdgrim, 0]])],
		// backwards: a swell into the beat
		["03_revcrash",  \cymbal, ~one.(\sdgcymbal, 1, (sdgspread: 0)) ++ (max: 2, reverse: true)],
		["04_revgong",   \fx,    ~one.(\sdgcymbal, 3, (sdgspread: 0)) ++ (max: 2.5, reverse: true)],
		["05_revsnare",  \fx,    ~one.(\sdgsnare, 4) ++ (fx: \spiral, max: 1.6, reverse: true)],
		// through a long feedback: echoes falling in pitch, combs ringing like metal, a plate
		["06_spiralkick", \fx,   ~one.(\sdgkick, 1) ++ (fx: \spiral, max: 2.5)],
		["07_metalkick", \fx,    ~one.(\sdgkick, 2) ++ (fx: \metal, max: 2)],
		["08_convkick",  \fx,    ~one.(\sdgkick, 3) ++ (fx: \conv, kernel: [\sdgmodal, 3, (sdgdecay: 0.3)], max: 1.5)],
		// played again slower or faster, through a sampler's bits: SP-1200, 8-bit
		["09_sp1200kick", \kick, ~one.(\sdgkick, 1) ++ (then: (rate: 0.79, bits: 12, down: 26040), max: 1.5)],
		["10_crushsnare", \snare, ~one.(\sdgsnare, 0) ++ (then: (rate: 1.33, bits: 8, down: 11025), max: 1)],
		["11_downclap",  \clap,  ~one.(\sdgclap, 3, (sdgspread: 0)) ++ (then: (rate: 0.5, bits: 16), max: 2)],
		// one bar each at 120 bpm: 2 seconds, a cycle at cps 0.5. chop and striate them
		["12_loopclassic", \loop, (loop: 2, voices: ~steps.([
			[\sdgkick, 0, (sdgdecay: 0.6), "x.....x...x....."],
			[\sdgsnare, 0, (),              "....x.......x..."],
			[\sdgclap, 2, (),               "............x..x"],
			[\sdghat, 0, (),                "x.xxx.x.x.xxx.x."],
			[\sdghat, 2, (sdgdecay: 0.3),   "......x.......x."]
		]))],
		["13_loopfm", \loop, (loop: 2, voices: ~steps.([
			[\sdgfmdrum, 0, (),   "x...x...x..x.x.."],
			[\sdgfmdrum, 1, (),   "....x.......x..."],
			[\sdgfmdrum, 5, (),   "..x..x..x...x.x."],
			[\sdgfmdrum, 4, (sdgdecay: 0.15), "x.x.x.x.x.x.x.xx"]
		]))],
		["14_loopbroken", \loop, (loop: 2, voices: ~steps.([
			[\sdgchaos, 2, (),    "x......x..x....."],
			[\sdgchaos, 0, (),    "....x........x.."],
			[\sdgchaos, 1, (),    "x.xx..x.xx.x.xxx"],
			[\sdgping, 0, (),     "..x..x.x...x.x.."],
			[\sdgratchet, 1, (sdglen: 0.25), "...........x...."]
		]))],
		["15_loopsophie", \loop, (loop: 2, voices: ~steps.([
			[\sdgslam, 0, (sdgdecay: 0.4), "x.......x.x....."],
			[\sdgplastic, 1, (),  "....x.......x..."],
			[\sdgplastic, 0, (),  "..x.......x...x."],
			[\sdgplastic, 4, (),  "......x.......x."],
			[\sdgping, 2, (),     "x.x.x.x.x.x.x.x."]
		]))]
	]
];
)

// ---- 4. render every bank (a minute or two). the files are replaced; ~renderFolder
//         says where they go. already playing? ~sdg.reloadSample(\sdgbd, 3) hears one again
(
fork {
	var t0 = Main.elapsedTime;
	~renderDone = false;
	~banks.pairsDo { |bank, entries|
		entries.do { |entry| ~renderFile.(bank, entry) }
	};
	"render: % banks in % s, in %".format(~banks.size div: 2, (Main.elapsedTime - t0).round(0.1), ~renderFolder).postln;
	~renderDone = true;
}
)

// ---- 5. your own: a bank in a folder of yours, then loaded like any other. here two
//         kicks of your own, a slow stack and a bar. evaluate block 2 first
(
~renderFolder = "~/my-samples".standardizePath;
fork {
	[
		["00_deep", \kick, ~one.(\sdgkick, 4, (sdgfreq: 38, sdgdecay: 2.5, note: -2))],
		["01_click", \kick, ~one.(\sdgkick, 2, (sdgclick: 1, sdgtone: 1))],
		["02_wall", \fx, (voices: [[0, \sdgslam, 3], [0.03, \sdgcymbal, 2, (sdgspread: 0)]], max: 1.5)],
		["03_bar", \loop, (loop: 2, voices: ~steps.([
			[\sdgkick, 4, (), "x..x..x...x....."],
			[\sdgrim, 1, (),  "..x..x..x..x..x."]
		]))]
	].do { |entry| ~renderFile.(\mykit, entry) };
	~sdg.loadSamples(~renderFolder);	// d1 $ s "mykit:0 mykit:1"
}
)
