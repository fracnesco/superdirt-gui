// =====================================================================
// SDGSync - longer samples played together at one tempo, and in one key,
// written out as Tidal code: loopAt stretches each one over a whole number
// of cycles, chop cuts it into pieces that each start on the grid, and a
// speed moves a sample with a key the rest of the way into the key.
//
//   y = ~sdg.sync;                      // one per SuperDirtGUI
//   y.add(\breaks125, 0); y.add(\arpy, 3);
//   y.bpm = 124; y.key = "Am";          // nil: from the first sample
//   y.code.postln;                      // setcps, then d1, d2 ...
//   y.play; y.stop;                     // hear it here, without Tidal
//   y.gui;                              // the window, SDGSyncWindow
//
// A loop of b bpm that lasts d seconds holds round(d * b / 60) beats. At
// t bpm and beatsPerCycle beats a cycle, loopAt (beats / beatsPerCycle)
// reads it at t / b of its speed: it is on the grid, and 12 log2(t / b)
// semitones higher. A loop whose own bpm is nearer half or twice t is
// counted in half or double time, so the tempo alone never moves a sample
// by more than a tritone. With matchKey, a melodic sample - tagged melodic,
// or with a key set by hand - is then moved the rest of the way into the
// target key, by less than a
// tritone up or down, with a speed inside the loopAt. It now reads a
// little faster or slower than the grid, and chop keeps it in step: every
// piece starts on its own beat, so a piece only ends a little early or
// late. A sample with no tempo, or matchTempo off, is stretched to the
// whole number of cycles nearest its own length.
//
// A loop that is not a whole number of beats long (a pickup, a tail) plays
// its whole beats only: # end e cuts it there, and # speed e reads what is
// left at the tempo, so the kept beats fill the loop exactly.
//
// The numbers come from the analysis (SDGSampleTags), or what was set by
// hand in the editor: a wrong bpm there is a wrong loop here.
// =====================================================================

SDGSync {
	classvar <>chopChoices;		// pieces a beat the window offers; 0 is no chop
	classvar <>latency = 0.2;	// the preview's events, all with the same latency
	classvar <>minOffset = 0.015;	// a first beat later than this, seconds, is shifted onto the grid
	classvar <>wholeTolerance = 0.12;	// beats a loop may be off a whole number before it is trimmed

	var <sdg, <items;
	var <bpm, <key, <matchTempo = true, <matchKey = true;
	var <beatsPerCycle = 4, <chopsPerBeat = 2, <useOffsets = true, <orbit = 0;
	var players, <window;

	*initClass { chopChoices = [0, 1, 2, 4, 8] }

	*new { |sdg| ^super.newCopyArgs(sdg).init }

	init { items = List.new }

	tags { ^sdg.tags }
	dirt { ^sdg.dirt }

	// ------------------------------------------------------------ samples

	// a sample of the library, by bank and index; answers its place, or nil
	// when there is no such sample or it is in already
	add { |bank, index = 0|
		var bufs = this.dirt.soundLibrary.buffers[bank.asSymbol], path;
		if (bufs.isNil or: { bufs.isEmpty }) { ^nil };
		bank = bank.asSymbol;
		index = index.asInteger.wrap(0, bufs.size - 1);
		if (items.any { |it| it[\bank] == bank and: { it[\index] == index } }) { ^nil };
		items.add((bank: bank, index: index));
		path = bufs[index] !? (_.path);
		path !? { this.tags.enqueue([path], true) };
		this.changed(\sync);
		^items.size - 1
	}

	// every sample of a bank, up to max
	addBank { |bank, max = 16|
		var bufs = this.dirt.soundLibrary.buffers[bank.asSymbol] ? [];
		bufs.size.min(max).do { |i| this.add(bank, i) };
	}

	removeAt { |i|
		if (i.notNil and: { i >= 0 } and: { i < items.size }) { items.removeAt(i); this.changed(\sync) };
	}

	move { |i, to|
		var it;
		if (i.isNil or: { i < 0 } or: { i >= items.size }) { ^this };
		to = to.clip(0, items.size - 1);
		if (to == i) { ^this };
		it = items.removeAt(i);
		items.insert(to, it);
		this.changed(\sync);
	}

	clear { items.clear; this.changed(\sync) }

	// --------------------------------------------------------- settings

	// the tempo they all play at, bpm; nil takes the first sample's own
	bpm_ { |x| bpm = x !? { x.asFloat.clip(20, 400) }; this.changed(\sync) }

	// the key they all play in: "Am", "F#", "Eb", as SDGSampleFeatures.parseKey
	// reads it; nil takes the first sample with a key, as the tempo leaves it
	key_ { |str|
		if (str.notNil and: { SDGSampleFeatures.parseKey(str).isNil }) {
			"SDGSync: % is not a key - try Am, F#, Eb".format(str.asCompileString).warn;
			^this
		};
		key = str !? (_.asString);
		this.changed(\sync);
	}

	matchTempo_ { |bool| matchTempo = bool == true; this.changed(\sync) }
	matchKey_ { |bool| matchKey = bool == true; this.changed(\sync) }
	beatsPerCycle_ { |n| beatsPerCycle = n.asInteger.clip(1, 16); this.changed(\sync) }
	chopsPerBeat_ { |n| chopsPerBeat = n.asInteger.clip(0, 16); this.changed(\sync) }
	useOffsets_ { |bool| useOffsets = bool == true; this.changed(\sync) }
	orbit_ { |i| orbit = i.asInteger.max(0); this.changed(\sync) }

	// ------------------------------------------------------------ the plan

	targetBpm { ^bpm ?? { this.prFirstBpm } ? 120 }

	cps { ^this.targetBpm / 60 / beatsPerCycle }

	prFirstBpm {
		if (matchTempo.not) { ^nil };
		items.do { |it| this.prInfo(it)[\bpm] !? { |b| ^b.round(0.01) } };
		^nil
	}

	prInfo { |it|
		var path = sdg.bufferAt(it[\bank], it[\index]) !? (_.path);
		^(path !? { this.tags.infoOf(path, false) }) ? ()
	}

	// The key they end in, (tonic:, mode:), or nil when the key is not matched
	// or nothing has one.
	targetKey { ^this.prTarget(items.collect { |it| this.prTempo(it, this.targetBpm, this.cps) }) }

	// What happens to each sample, an Event a sample: label, duration, bpm,
	// beats, cycles (loopAt's number), rate (the tempo's read rate),
	// tempoSt, correction (semitones by speed), speed, pitch (semitones in
	// all), chop, shift (cycles earlier, for a first beat that is not at the
	// start), keyFrom, keyTo, notes (what to look out for).
	plan {
		var t = this.targetBpm, cps = this.cps, rows, target;
		rows = items.collect { |it| this.prTempo(it, t, cps) };
		target = this.prTarget(rows);
		rows.do { |r| this.prPitch(r, target) };
		^rows
	}

	prTempo { |it, t, cps|
		var buf = sdg.bufferAt(it[\bank], it[\index]), path = buf !? (_.path);
		var info = (path !? { this.tags.infoOf(path, false) }) ? ();
		var dur = (buf !? { |b| b.numFrames !? { b.numFrames / (b.sampleRate ? 44100).max(1) } }) ? 0;
		var r = (bank: it[\bank], index: it[\index], label: "%:%".format(it[\bank], it[\index]), path: path,
			duration: dur, info: info, bpm: info[\bpm], bpmSure: info[\bpmSure] == true, notes: List.new,
			analysed: info[\analysed] == true, grid: false);
		var raw, fold, natural, offset;
		if (dur <= 0) {
			r.putAll((beats: beatsPerCycle, cycles: 1, rate: 1, tempoSt: 0));
			r[\notes].add("not loaded");
		} {
			if (matchTempo and: { r[\bpm].notNil } and: { r[\bpm] > 0 }) {
				fold = 2 ** (t / r[\bpm]).log2.round;
				raw = dur * r[\bpm] * fold / 60;
				r[\beats] = raw.round.asInteger.max(1);
				// a part beat at the end is cut off, not stretched over the rest
				if ((raw - r[\beats]).abs > wholeTolerance and: { raw >= 1 }) {
					r[\beats] = raw.floor.asInteger;
					r[\trim] = r[\beats] / raw;
					r[\notes].add("% beats long: the first % play (end %)".format(raw.round(0.01), r[\beats], this.class.num(r[\trim], 4)));
				};
				r[\cycles] = r[\beats] / beatsPerCycle;
				r[\grid] = true;
				if (fold != 1) { r[\notes].add("counted at % bpm, %".format(this.class.num(r[\bpm] * fold, 1),
					if (fold > 1) { "double time" } { "half time" })) };
				if (r[\bpmSure].not) { r[\notes].add("the bpm is not sure") };
				offset = info[\beatOffset] ? 0;
			} {
				natural = dur * cps;
				r[\cycles] = if (natural >= 0.75) { natural.round } { 2 ** natural.max(1e-3).log2.round };
				r[\cycles] = r[\cycles].max(1/16);
				r[\beats] = r[\cycles] * beatsPerCycle;
				if (matchTempo) {
					r[\notes].add(if (r[\analysed]) { "no tempo: stretched to whole cycles" } { "not analysed yet" })
				};
			};
			r[\rate] = dur * (r[\trim] ? 1) * cps / r[\cycles];
			r[\tempoSt] = 12 * r[\rate].log2;
			if (useOffsets and: { r[\grid] } and: { (offset ? 0) > minOffset }) {
				r[\shift] = offset / r[\rate] * cps;
			};
			if (r[\tempoSt].abs > 7) { r[\notes].add("% semitones from its own tempo".format(this.class.num(r[\tempoSt], 1))) };
		};
		r[\chop] = if (chopsPerBeat > 0) { (r[\beats] * chopsPerBeat).round.asInteger.max(1) };
		^r
	}

	prTarget { |rows|
		var first;
		if (matchKey.not) { ^nil };
		if (key.notNil) { ^SDGSampleFeatures.parseKey(key) };
		first = rows.detect { |r| this.class.isTonal(r[\info]) };
		if (first.isNil) { ^nil };
		^(tonic: (first[\info][\tonic] + first[\tempoSt].round).asInteger % 12, mode: first[\info][\mode])
	}

	prPitch { |r, target|
		var tonic = r[\info][\tonic], mode = r[\info][\mode], want, corr = 0;
		r[\target] = target;
		if (tonic.notNil) { r[\keyFrom] = this.class.keyName(tonic, mode, r[\info][\key]) };
		if (target.notNil and: { this.class.isTonal(r[\info]) } and: { r[\tempoSt].notNil }) {
			want = this.class.wantedTonic(mode, target);
			corr = ((want - tonic - r[\tempoSt]) + 6).mod(12) - 6;
			r[\keyTo] = this.class.keyName(want % 12, mode);
			if (r[\info][\keySure] != true) { r[\notes].add("the key is not sure") };
		} {
			if (target.notNil and: { tonic.notNil }) { r[\notes].add("not tagged melodic: its pitch is left alone") };
		};
		r[\correction] = corr;
		r[\speed] = 2 ** (corr / 12);
		r[\pitch] = (r[\tempoSt] ? 0) + corr;
	}

	// whether a sample's pitch is matched: it has a key or a note, and is
	// tagged melodic or had its key set by hand - a drum loop's key is noise
	*isTonal { |info|
		^info[\tonic].notNil and: { info[\keyByHand] == true or: { (info[\tags] ? []).includes(\melodic) } }
	}

	// the tonic a sample of a mode goes to, for a target key: a note goes to
	// the tonic; a minor sample into a major key to its relative minor, a
	// major sample into a minor key to its relative major
	*wantedTonic { |mode, target|
		^case
		{ mode == \note or: { target[\mode] == \note } or: { mode == target[\mode] } } { target[\tonic] }
		{ mode == \minor } { target[\tonic] - 3 }
		{ target[\tonic] + 3 }
	}

	*keyName { |tonic, mode, given|
		var name = SDGSampleFeatures.noteNames[tonic.asInteger % 12];
		^case
		{ mode == \minor } { name ++ "m" }
		{ mode == \note } { given ? name }
		{ name }
	}

	// a number as Tidal code: no trailing zeros, no exponent
	*num { |x, decimals = 4|
		var s = x.round(10 ** decimals.neg).asFloat.asStringPrec(12);
		if (s.contains(".")) {
			while { s.endsWith("0") } { s = s.drop(-1) };
			if (s.endsWith(".")) { s = s.drop(-1) };
		};
		if (s == "-0") { s = "0" };
		^s
	}

	// ----------------------------------------------------------- the code

	// The Tidal code: setcps, then one d a sample, each with a comment.
	code {
		var rows = this.plan, t = this.targetBpm, target = rows.first !? { |r| r[\target] }, lines = List.new;
		lines.add("-- SuperDirtGUI sync: % bpm, % beats a cycle%".format(this.class.num(t, 2), beatsPerCycle,
			target !? { ", in " ++ this.class.keyName(target[\tonic], target[\mode]) } ? ""));
		lines.add("setcps (%/60/%)".format(this.class.num(t, 2), beatsPerCycle));
		rows.do { |r, i|
			lines.add("");
			lines.add("-- " ++ this.describe(r));
			r[\notes].do { |n| lines.add("-- " ++ n) };
			lines.add(this.line(r, i + 1));
		};
		^lines.join("\n") ++ "\n"
	}

	// one sample's pattern: d<n> $ [shift <~] (loopAt c $ chop k $ s "bank:i" [# end e] [# speed x] [# legato 1])
	line { |r, d = 1|
		var p = "s \"%:%\"".format(r[\bank], r[\index]), speed = r[\speed] * (r[\trim] ? 1);
		r[\trim] !? { |e| p = p ++ " # end %".format(this.class.num(e, 4)) };
		if ((speed - 1).abs > 0.0003) { p = p ++ " # speed %".format(this.class.num(speed, 4)) };
		if (r[\correction].abs > 0.005) { p = p ++ " # legato 1" };
		if (r[\chop].notNil and: { r[\chop] > 1 }) { p = "chop % $ %".format(r[\chop], p) };
		p = "loopAt % $ %".format(this.class.num(r[\cycles], 4), p);
		if (r[\shift].notNil) { p = "% <~ (%)".format(this.class.num(r[\shift], 4), p) };
		^"d% $ %".format(d, p)
	}

	// one sample in words, for the comment above its line
	describe { |r|
		var out = r[\label] ++ ": ";
		out = out ++ if (r[\grid]) {
			"% bpm, % beats in % s".format(this.class.num(r[\bpm], 1), r[\beats], this.class.num(r[\duration], 2))
		} { "% s".format(this.class.num(r[\duration], 2)) };
		out = out ++ ", over % cycle%, read at x%".format(this.class.num(r[\cycles], 4),
			if (r[\cycles] == 1) { "" } { "s" }, this.class.num(r[\rate], 3));
		out = out ++ " (% st)".format(this.class.signed(r[\tempoSt]));
		r[\keyTo] !? {
			out = out ++ ", % to %: % st by speed".format(r[\keyFrom], r[\keyTo], this.class.signed(r[\correction]))
		};
		^out
	}

	*signed { |x, decimals = 2| ^if (x >= 0) { "+" } { "" } ++ this.num(x, decimals) }

	// puts the code on the clipboard; answers whether it could
	copy {
		var cmd = SDGWindow.prClipboardCommand(this.code);
		cmd !? { cmd.unixCmd };
		^cmd.notNil
	}

	// --------------------------------------------------------- the preview

	// Plays them all here, as the code would from Tidal: every piece an
	// event with Tidal's begin, end, speed and unit "c". Loops until stop.
	play { |orbitIndex|
		var rows = this.plan, cps = this.cps, t0;
		this.stop;
		if (rows.isEmpty) { ^this };
		orbitIndex = (orbitIndex ? orbit).clip(0, (this.dirt.orbits.size - 1).max(0));
		sdg.wakeOrbit(this.dirt.orbits[orbitIndex]);
		t0 = SystemClock.seconds + 0.05;
		players = rows.reject { |r| r[\path].isNil }.collect { |r|
			var rout = this.prPlayer(r, cps, orbitIndex);
			SystemClock.schedAbs(t0, rout);
			rout
		};
		this.changed(\playing, true);
	}

	stop {
		var was = players.notNil;
		players.do(_.stop);
		players = nil;
		if (was) { this.changed(\playing, false) };
	}

	isPlaying { ^players.notNil }

	prPlayer { |r, cps, orbitIndex|
		var period = r[\cycles] / cps, k = (r[\chop] ? 1).max(1), slot = period / k;
		var off = (r[\shift] ? 0) / cps, e = r[\trim] ? 1, speed = r[\speed] * e / r[\cycles], dirt = this.dirt;
		var legato = if (r[\correction].abs > 0.005) { 1 };
		var times = k.collect { |j| [((j * slot) - off).mod(period), j] }.sort { |a, b| a[0] < b[0] };
		^Routine {
			var now;
			loop {
				now = 0;
				times.do { |tj|
					(tj[0] - now).wait;
					now = tj[0];
					(type: \dirt, dirt: dirt, s: r[\bank], n: r[\index], orbit: orbitIndex,
						begin: tj[1] / k * e, end: (tj[1] + 1) / k * e, speed: speed, unit: \c, cps: cps,
						delta: slot, legato: legato, latency: latency).play;
				};
				(period - now).wait;
			}
		}
	}

	// ----------------------------------------------------------- the window

	gui {
		if (window.notNil and: { window.isOpen }) { window.front; ^window };
		window = SDGSyncWindow(this);
		^window
	}

	free {
		this.stop;
		window !? { |w| { w.close }.defer };
		window = nil;
	}
}
