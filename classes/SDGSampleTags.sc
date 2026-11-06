// =====================================================================
// SDGSampleTags - what every sample of the library is: tags, bpm, key, dates
//
//   t = ~sdg.tags;
//   t.analyseLibrary;                         // in the background; the window starts it
//   t.infoOf(path);                           // (tags:, bpm:, bpmSure:, key:, keySure: ...)
//   t.tagsOf(path);                           // [\short, \percussive, \kick]
//   t.addTag(path, "dusty"); t.removeTag(path, \kick);
//   t.setBpm(path, 92); t.setKey(path, "Am");  // by hand: sure from then on
//   t.query([\percussive, \short], [\melodic]);   // [[\bd, 3], [\sn, 0] ...]
//   t.query(bpm: 120);                        // 120, and 60, 240, 30, 480 within 3%
//   t.allTags;                                // tag -> how many samples have it
//
// A sample is known by its path. Its analysis (SDGSampleFeatures) is kept in
// analysis.tsv with the file's size and modification time, and redone when
// either changes; what was set by hand - tags added, tags taken away, a bpm, a
// key - is kept apart in tags.tsv and never thrown away by an analysis. Both
// live in `folder`, in the user's support directory: a bank's own folder is
// never written to.
//
// The tags made by `autoTags` from the features:
//
//   short medium long       how long it sounds, to 20 dB under its peak
//   oneshot loop            one attack; a whole number of beats at its bpm
//   percussive sustained    a fast attack and a short decay; a level that holds
//   melodic noisy           a pitch or a key; a spectrum close to noise
//   kick snare hat bass     guesses from the shape and where the energy is
//   bright dark             the spectral centroid
//   bpm-unsure key-unsure   a bpm or a key the analysis does not trust
//
// Notifies \tags (path, or nil for many) when a sample's tags or numbers
// change, and \analysis (done, total) while the queue runs.
// =====================================================================

SDGSampleTags {
	classvar folder;
	classvar <>autoAnalyse = true;			// the window starts analyseLibrary when it opens
	classvar <>bpmTolerance = 0.03;			// a bpm query matches within this share
	classvar <>bpmMultiples = #[0.25, 0.5, 1, 2, 4];
	classvar <>chunk = 24;					// files whose dates are read in one call

	var <sdg, <records, <manual;
	var queue, queued, <routine, <done = 0, <total = 0;
	var <stamp = 0;							// bumped on every change: what query caches look at
	var saveRecordsPending = false, saveManualPending = false;
	var queryCache, queryStamp;

	*folder { ^folder ?? { Platform.userAppSupportDir +/+ "superdirt-gui-tags" } }
	*folder_ { |path| folder = path }

	// what a path is kept under. On Windows one file reaches here spelled
	// several ways (+/+ and pathMatch write \, Qt's dialogs /, any case), and
	// the tags set under one spelling must not be lost under another; elsewhere
	// the path itself, as before
	*keyOf { |path|
		^if (thisProcess.platform.name == \windows) { SuperDirtGUI.prNormalPath(path) } { path }
	}

	*recordsPath { ^this.folder +/+ "analysis.tsv" }
	*manualPath { ^this.folder +/+ "tags.tsv" }

	*new { |sdg| ^super.newCopyArgs(sdg).init }

	init {
		records = Dictionary.new;
		manual = Dictionary.new;
		queue = List.new;
		queued = Set.new;
		queryCache = Dictionary.new;
		this.load;
	}

	dirt { ^sdg !? (_.dirt) }

	// ------------------------------------------------------------ reading

	// the analysis of a file if it is still the file that was analysed; with
	// check false the file is not asked (a list of thousands redrawn often):
	// what was last analysed is taken as it is
	recordOf { |path, check = true|
		var r = records[this.class.keyOf(path)];
		if (r.isNil) { ^nil };
		if (r[\version] != SDGSampleFeatures.version) { ^nil };
		if (check and: { r[\size] != this.class.prSize(path) or: { r[\mtime] != this.class.prMTime(path) } }) { ^nil };
		^r
	}

	isAnalysed { |path| ^this.recordOf(path).notNil }

	// Everything known about a file, hand settings over the analysis:
	//   analysed, duration, numChannels, sampleRate, onsets, beatOffset
	//   bpm, bpmSure, bpmByHand        key, keySure, keyByHand, tonic, mode
	//   tags (what counts), auto (the analysis's), added, removed (by hand)
	//   created, dateAdded             seconds since 1970, nil if unknown
	infoOf { |path, check = true|
		var r = this.recordOf(path, check) ? (), m = manual[this.class.keyOf(path)] ? ();
		var auto = r[\auto] ? [], added = m[\add] ? [], removed = m[\remove] ? [];
		var info = (
			path: path, analysed: r[\version].notNil,
			duration: r[\duration], numChannels: r[\numChannels], sampleRate: r[\sampleRate],
			onsets: r[\onsets] ? [], peaks: r[\peaks],
			bpm: m[\bpm] ? r[\bpm], bpmSure: m[\bpm].notNil or: { r[\bpmSure] ? false }, bpmByHand: m[\bpm].notNil,
			beatOffset: m[\offset] ? r[\beatOffset] ? 0,
			key: m[\key] ? r[\key], keySure: m[\key].notNil or: { r[\keySure] ? false }, keyByHand: m[\key].notNil,
			auto: auto, added: added, removed: removed,
			created: r[\created], dateAdded: r[\dateAdded] ? m[\firstSeen]
		);
		var k = info[\key] !? { |str| SDGSampleFeatures.parseKey(str) };
		info[\tonic] = k !? (_[\tonic]);
		info[\mode] = k !? (_[\mode]);
		info[\tags] = this.class.prEffective(auto, added, removed, info);
		^info
	}

	// what counts: the analysis's tags less what was taken away, with what was
	// added; the unsure tags follow a bpm or key set by hand
	*prEffective { |auto, added, removed, info|
		var tags = auto.reject { |t| removed.includes(t) };
		if (info[\bpmByHand]) { tags = tags.reject(_ == 'bpm-unsure') };
		if (info[\keyByHand]) { tags = tags.reject(_ == 'key-unsure') };
		added.do { |t| if (tags.includes(t).not) { tags = tags.add(t) } };
		^tags
	}

	tagsOf { |path, check = true| ^this.infoOf(path, check)[\tags] }

	autoTagsOf { |path| ^(this.recordOf(path, false) !? (_[\auto])) ? [] }

	// ------------------------------------------------------------ by hand

	*normaliseTag { |str|
		var s = str.asString.toLower.collect { |c|
			if (c.isAlphaNum or: { c == $- } or: { c == $_ }) { c } { $- }
		};
		while { s.beginsWith("-") } { s = s.copyToEnd(1) };
		while { s.endsWith("-") } { s = s.copyRange(0, s.size - 2) };
		^if (s.isEmpty) { nil } { s.asSymbol }
	}

	prManual { |path| var k = this.class.keyOf(path); ^manual[k] ?? { manual[k] = (add: [], remove: []); manual[k] } }

	addTag { |path, tag|
		var m, t = this.class.normaliseTag(tag);
		if (t.isNil or: { path.isNil }) { ^this };
		m = this.prManual(path);
		m[\remove] = m[\remove].reject(_ == t);
		if (this.autoTagsOf(path).includes(t).not and: { m[\add].includes(t).not }) { m[\add] = m[\add].add(t) };
		this.prManualChanged(path);
	}

	removeTag { |path, tag|
		var m, t = this.class.normaliseTag(tag);
		if (t.isNil or: { path.isNil }) { ^this };
		m = this.prManual(path);
		m[\add] = m[\add].reject(_ == t);
		if (this.autoTagsOf(path).includes(t) and: { m[\remove].includes(t).not }) { m[\remove] = m[\remove].add(t) };
		this.prManualChanged(path);
	}

	// the tags as given, whatever the analysis said
	setTags { |path, tags|
		var want = tags.collect { |t| this.class.normaliseTag(t) }.reject(_.isNil).asSet, auto = this.autoTagsOf(path), m;
		m = this.prManual(path);
		m[\add] = want.reject { |t| auto.includes(t) }.asArray.sort;
		m[\remove] = auto.reject { |t| want.includes(t) };
		this.prManualChanged(path);
	}

	// back to what the analysis says: tags, bpm and key
	resetTags { |path|
		var m = manual[this.class.keyOf(path)];
		if (m.isNil) { ^this };
		manual[this.class.keyOf(path)] = (add: [], remove: [], firstSeen: m[\firstSeen]);
		this.prManualChanged(path);
	}

	// a bpm (and where the first beat is, seconds) by hand; nil goes back to the analysis's
	setBpm { |path, bpm, offset|
		var m = this.prManual(path);
		m[\bpm] = bpm !? { |b| b.asFloat.round(0.001) };
		if (bpm.isNil) { m[\offset] = nil } { offset !? { m[\offset] = offset.asFloat.max(0) } };
		this.prManualChanged(path);
	}

	setBeatOffset { |path, seconds|
		this.prManual(path)[\offset] = seconds !? (_.asFloat.max(0));
		this.prManualChanged(path);
	}

	// a key by hand, "Am", "F#", "C3"; nil goes back to the analysis's
	setKey { |path, key|
		var m = this.prManual(path);
		if (key.notNil and: { SDGSampleFeatures.parseKey(key).isNil }) {
			"SDGSampleTags: % is not a key - try Am, F#, Eb or C3".format(key.asCompileString).warn;
			^this
		};
		m[\key] = key !? (_.asString);
		this.prManualChanged(path);
	}

	prManualChanged { |path|
		stamp = stamp + 1;
		this.prScheduleSave(\manual);
		this.changed(\tags, path);
	}

	// ------------------------------------------------------------ analysis

	// One file, now, in the calling thread. Answers its record, or nil when
	// it cannot be read. A file analysed before and unchanged is not read again.
	analyse { |path, force = false, dates|
		var f, r, auto;
		if (path.isNil) { ^nil };
		if (force.not) { this.recordOf(path) !? { |rec| ^rec } };
		if (File.exists(path).not) { ^nil };
		f = try { SDGSampleFeatures.analyse(path) } { |err|
			"SDGSampleTags: could not analyse % - %".format(path.basename, err.errorString).warn;
			nil
		};
		if (f.isNil) { ^nil };
		dates = dates ?? { this.class.fileDates([path])[0] };
		r = this.class.recordFrom(f, path, dates);
		records[this.class.keyOf(path)] = r;
		if (manual[this.class.keyOf(path)].isNil or: { manual[this.class.keyOf(path)][\firstSeen].isNil }) {
			this.prManual(path)[\firstSeen] = Date.getDate.rawSeconds.round;
			this.prScheduleSave(\manual);
		};
		stamp = stamp + 1;
		this.prScheduleSave(\records);
		this.changed(\tags, path);
		^r
	}

	// what is kept of the features: the numbers the tags and the columns need
	*recordFrom { |f, path, dates|
		var r = (
			version: f[\version], size: this.prSize(path), mtime: this.prMTime(path),
			duration: f[\duration], numChannels: f[\numChannels], sampleRate: f[\sampleRate],
			bpm: f[\bpm], bpmConfidence: f[\bpmConfidence], bpmSure: f[\bpmSure], beatOffset: f[\beatOffset],
			lengthFit: f[\lengthFit],
			key: f[\key], keyConfidence: f[\keyConfidence], keySure: f[\keySure],
			onsets: f[\onsets].keep(512),
			centroid: f[\centroid], flatness: f[\flatness], low: f[\low], high: f[\high],
			tonal: f[\tonal], attack: f[\attack], length: f[\length], sustain: f[\sustain], sweep: f[\sweep],
			decay: f[\decay], audible: f[\audible],
			created: dates !? (_[0]), dateAdded: dates !? (_[1])
		);
		r[\auto] = this.autoTags(r);
		// a kick's falling pitch is not a key anyone plays it in
		if (r[\auto].includes(\kick)) { r[\key] = nil; r[\keySure] = false; r[\auto] = r[\auto].reject(_ == 'key-unsure') };
		^r
	}

	// The tags the numbers say. Thresholds come from a calibration on
	// Dirt-Samples (bd, sn, hh, cp, 808bd, arpy, breaks*, pad, bass3, juno,
	// moog, pluck, jvbass, amencutup, notes, 2026-11-03): good guesses, not
	// truths - edit what is wrong, by hand.
	*autoTags { |r|
		var tags = [], len = r[\length] ? 0, tonal = r[\tonal] ? 0, cent = r[\centroid] ? 0;
		var low = r[\low] ? 0, high = r[\high] ? 0, onsets = (r[\onsets] ? []).size;
		var audible = (r[\audible] ? len).max(1e-3), rate = onsets / audible;
		var percussive, kick = false, beats;
		tags = tags.add(case { len < 0.25 } { \short } { len < 1.5 } { \medium } { \long });
		percussive = ((r[\attack] ? 1) < 0.03 and: { len < 0.6 } and: { (r[\decay] ? 1) < 0.35 })
			or: { rate >= 3 and: { tonal < 0.3 } };
		if (percussive) { tags = tags.add(\percussive) };
		if ((r[\sustain] ? 0) >= 0.6 and: { len >= 0.5 } and: { percussive.not }) { tags = tags.add(\sustained) };
		if (onsets <= 1 and: { len < 2 }) { tags = tags.add(\oneshot) };
		if (r[\bpm].notNil and: { r[\bpmSure] == true } and: { (r[\duration] ? 0) >= 1 }) {
			beats = r[\duration] * r[\bpm] / 60;
			if (r[\lengthFit] == true or: { beats.round >= 2 and: { ((beats - beats.round).abs / beats.round) < 0.04 } }) {
				tags = tags.add(\loop)
			};
		};
		if (percussive) {
			case
			{ low >= 0.6 and: { cent < 250 } and: { len < 0.6 } and: { len < 0.2 or: { (r[\sweep] ? 1) >= 2 } } } {
				kick = true; tags = tags.add(\kick)
			}
			{ tonal < 0.3 and: { high >= 0.15 or: { cent >= 2500 } } and: { low < 0.35 } } { tags = tags.add(\hat) }
			{ cent >= 150 and: { cent < 2500 } and: { low < 0.5 } and: { len < 0.5 } } { tags = tags.add(\snare) };
		};
		if (r[\key].notNil and: { tonal >= 0.3 } and: { kick.not }) { tags = tags.add(\melodic) };
		if (tonal < 0.1 and: { (r[\flatness] ? 0) > 0.05 or: { high > 0.1 } }) { tags = tags.add(\noisy) };
		if (low >= 0.6 and: { kick.not } and: { tonal >= 0.3 }) { tags = tags.add(\bass) };
		if (cent >= 3000) { tags = tags.add(\bright) };
		if (cent > 0 and: { cent <= 250 }) { tags = tags.add(\dark) };
		if (r[\bpm].notNil and: { r[\bpmSure] != true }) { tags = tags.add('bpm-unsure') };
		if (r[\key].notNil and: { r[\keySure] != true }) { tags = tags.add('key-unsure') };
		^tags
	}

	// ------------------------------------------------------------ the queue

	// every sample in the library that has no fresh analysis, in the
	// background; a bank asked for with `prioritise` goes first
	analyseLibrary {
		this.enqueue(this.libraryPaths);
	}

	libraryPaths {
		var lib = this.dirt !? (_.soundLibrary), out = [];
		if (lib.isNil) { ^out };
		lib.buffers.keys.asArray.sort.do { |bank|
			(lib.buffers[bank] ? []).do { |buf| buf !? { buf.path !? { |p| out = out.add(p) } } }
		};
		^out
	}

	// a bank's paths
	pathsOf { |bank|
		var bufs = this.dirt !? { |d| d.soundLibrary.buffers[bank.asSymbol] };
		^(bufs ? []).collect { |b| b !? (_.path) }.reject(_.isNil)
	}

	enqueue { |paths, front = false|
		var fresh = paths.reject { |p| p.isNil or: { this.recordOf(p).notNil } };
		if (front) {
			fresh.reverseDo { |p| queue.remove(p); queue.addFirst(p); queued.add(p) };
		} {
			fresh.do { |p| if (queued.includes(p).not) { queue.add(p); queued.add(p) } };
		};
		total = done + queue.size;
		this.prRun;
	}

	// a bank to the front of the queue, as the explorer asks when it is opened
	prioritise { |bank| this.enqueue(this.pathsOf(bank), true) }

	isAnalysing { ^routine.notNil }

	// after a cmd-period, which has cleared the clock the routine ran on: the
	// batch it was in the middle of goes back to the front, and it runs again
	prResume {
		routine = nil;
		SDGSampleFeatures.breathing = false;
		queued.do { |p| if (queue.includes(p).not) { queue.addFirst(p) } };
		this.prRun;
	}

	stop {
		routine !? (_.stop);
		routine = nil;
		SDGSampleFeatures.breathing = false;
		queue.clear;
		queued.clear;
		done = 0;
		total = 0;
	}

	prRun {
		if (routine.notNil or: { queue.isEmpty }) { ^this };
		routine = Routine {
			var batch;
			while { queue.notEmpty } {
				batch = [];
				while { queue.notEmpty and: { batch.size < chunk } } { batch = batch.add(queue.removeAt(0)) };
				// the dates of a batch in one call to the system
				this.class.fileDates(batch).do { |dates, i|
					var p = batch[i];
					SDGSampleFeatures.breathing = true;
					this.analyse(p, false, dates);
					SDGSampleFeatures.breathing = false;
					queued.remove(p); // after: a cmd-period in the middle puts it back (prResume)
					done = done + 1;
					if ((done % 8) == 0 or: { queue.isEmpty }) { this.changed(\analysis, done, total) };
					0.002.wait;
				};
			};
			routine = nil;
			this.changed(\analysis, done, total);
			done = 0;
			total = 0;
			this.changed(\tags, nil);
		}.play(AppClock);
	}

	// --------------------------------------------------------------- queries

	// Does a file have every tag of `include` and none of `exclude`, a bpm
	// matching `bpm` (or a multiple of it), a key matching `key`?
	matches { |path, include, exclude, bpm, key, check = true|
		var info = this.infoOf(path, check), tags = info[\tags];
		if (include.notNil and: { include.any { |t| tags.includes(t).not } }) { ^false };
		if (exclude.notNil and: { exclude.any { |t| tags.includes(t) } }) { ^false };
		if (bpm.notNil and: { this.class.bpmMatches(info[\bpm], bpm).not }) { ^false };
		if (key.notNil and: { this.class.keyMatches(info, key).not }) { ^false };
		^true
	}

	// a bpm matches a target when it is within bpmTolerance of it or of one of
	// its multiples - a loop at 60 is a loop at 120, a half-time 240
	*bpmMatches { |bpm, target, tolerance, multiples|
		if (bpm.isNil or: { target.isNil } or: { target <= 0 }) { ^false };
		^(multiples ? bpmMultiples).any { |m| ((bpm / (target * m)) - 1).abs <= (tolerance ? bpmTolerance) }
	}

	// how far a bpm is from a target, counting the multiples, as a ratio: 0 is exact
	*bpmDistance { |bpm, target|
		if (bpm.isNil or: { target.isNil } or: { target <= 0 }) { ^inf };
		^bpmMultiples.collect { |m| ((bpm / (target * m)) - 1).abs }.minItem
	}

	// a key query: tonic and mode as asked; a note matches every key on its tonic
	*keyMatches { |info, key|
		var want = if (key.isKindOf(Event)) { key } { SDGSampleFeatures.parseKey(key) };
		if (want.isNil or: { info[\tonic].isNil }) { ^false };
		if (want[\tonic] != info[\tonic]) { ^false };
		^(want[\mode] == \note) or: { info[\mode] == \note } or: { want[\mode] == info[\mode] }
	}

	// [[bank, index] ...] of the samples in the library that match, in bank
	// order; bank restricts it to one. Kept until anything changes, so a
	// pattern asking every event costs a lookup.
	query { |include, exclude, bpm, key, bank|
		var lib = this.dirt !? (_.soundLibrary), sig, ck, out = [], banks, want;
		if (lib.isNil) { ^out };
		include = include !? { include.asArray.collect { |t| this.class.normaliseTag(t) }.reject(_.isNil) };
		exclude = exclude !? { exclude.asArray.collect { |t| this.class.normaliseTag(t) }.reject(_.isNil) };
		want = key !? { SDGSampleFeatures.parseKey(key) };
		if (key.notNil and: { want.isNil }) { ^out };
		sig = [lib.buffers.size, lib.buffers.sum { |a| a.size }];
		if (queryStamp != [stamp, sig]) { queryCache.clear; queryStamp = [stamp, sig] };
		ck = [include, exclude, bpm, key, bank].asCompileString;
		queryCache[ck] !? { |hit| ^hit };
		banks = if (bank.notNil) { [bank.asSymbol] } { lib.buffers.keys.asArray.sort };
		banks.do { |b|
			(lib.buffers[b] ? []).do { |buf, i|
				var p = buf !? (_.path);
				if (p.notNil and: { this.matches(p, include, exclude, bpm, want, false) }) { out = out.add([b, i]) };
			};
		};
		queryCache[ck] = out;
		^out
	}

	// a tag -> how many samples of the library have it
	allTags {
		var counts = IdentityDictionary.new;
		this.libraryPaths.do { |p| this.tagsOf(p, false).do { |t| counts[t] = (counts[t] ? 0) + 1 } };
		^counts
	}

	// ------------------------------------------------------------ the system

	*prSize { |path| ^try { File.fileSize(path) } { -1 } }
	*prMTime { |path| ^try { File.mtime(path) } { -1 } }

	// [created, dateAdded] per path, seconds since 1970, nil when the system
	// does not say: birth time and Spotlight's date added on macOS, birth time
	// on Linux, the modification time for both on Windows
	*fileDates { |paths|
		var created, added;
		if (paths.isEmpty) { ^[] };
		^Platform.case(
			\osx, {
				created = this.prLines("stat -f %B " ++ paths.collect(_.shellQuote).join(" "));
				added = this.prDatesAdded(paths);
				paths.collect { |p, i| [created[i] !? { |s| s.asInteger }, added[i]] }
			},
			\linux, {
				created = this.prLines("stat -c %W " ++ paths.collect(_.shellQuote).join(" "));
				paths.collect { |p, i|
					var c = created[i] !? { |s| s.asInteger };
					if (c.isNil or: { c <= 0 }) { c = this.prMTime(p) };
					[c, nil]
				}
			},
			{ paths.collect { |p| var m = this.prMTime(p); [m, m] } }
		)
	}

	*prLines { |cmd|
		var out = try { cmd.unixCmdGetStdOut } { "" };
		^(out ? "").split($\n).reject(_.isEmpty)
	}

	// mdls -raw with several files answers their values NUL-separated
	*prDatesAdded { |paths|
		var out = try { ("mdls -raw -name kMDItemDateAdded " ++ paths.collect(_.shellQuote).join(" ")).unixCmdGetStdOut } { "" };
		var parts = (out ? "").split(0.asAscii);
		^paths.collect { |p, i| parts[i] !? { |s| this.parseDate(s) } }
	}

	// "2025-08-25 03:27:00 +0000" -> seconds since 1970, nil for "(null)"
	*parseDate { |str|
		var s = str.asString.stripWhiteSpace, y, mo, d, h, mi, se, days, era, yoe, doy, doe, sign, off = 0;
		if (s.size < 19 or: { s[4] != $- }) { ^nil };
		y = s.copyRange(0, 3).asInteger; mo = s.copyRange(5, 6).asInteger; d = s.copyRange(8, 9).asInteger;
		h = s.copyRange(11, 12).asInteger; mi = s.copyRange(14, 15).asInteger; se = s.copyRange(17, 18).asInteger;
		if (s.size >= 25) {
			sign = if (s[20] == $-) { -1 } { 1 };
			off = sign * ((s.copyRange(21, 22).asInteger * 3600) + (s.copyRange(23, 24).asInteger * 60));
		};
		// days from the civil date (Howard Hinnant's algorithm)
		if (mo <= 2) { y = y - 1 };
		era = (if (y >= 0) { y } { y - 399 }) div: 400;
		yoe = y - (era * 400);
		doy = ((153 * (mo + (if (mo > 2) { -3 } { 9 }))) + 2) div: 5 + d - 1;
		doe = (yoe * 365) + (yoe div: 4) - (yoe div: 100) + doy;
		days = (era * 146097) + doe - 719468;
		^(days * 86400) + (h * 3600) + (mi * 60) + se - off
	}

	// ------------------------------------------------------------ files

	load {
		records = this.class.readRecords(this.class.recordsPath);
		manual = this.class.readManual(this.class.manualPath);
		stamp = stamp + 1;
	}

	// writes wait a moment, so a queue of a hundred files writes once
	prScheduleSave { |which|
		if (which == \manual) {
			if (saveManualPending) { ^this };
			saveManualPending = true;
			AppClock.sched(0.5, { saveManualPending = false; this.saveManual; nil });
		} {
			if (saveRecordsPending) { ^this };
			saveRecordsPending = true;
			AppClock.sched(3, { saveRecordsPending = false; this.saveRecords; nil });
		};
	}

	save { this.saveRecords; this.saveManual }

	saveRecords { this.class.writeRecords(this.class.recordsPath, records) }

	saveManual { this.class.writeManual(this.class.manualPath, manual) }

	*recordKeys {
		^#[version, size, mtime, duration, numChannels, sampleRate, bpm, bpmConfidence, bpmSure, beatOffset,
			lengthFit, key, keyConfidence, keySure, centroid, flatness, low, high, tonal, attack, length,
			sustain, sweep, decay, audible, created, dateAdded]
	}

	// one line per file: the path, the numbers in recordKeys' order, the
	// tags, the onsets in ms - tab-separated, "-" for nil
	*writeRecords { |path, recs|
		var file, keys = this.recordKeys;
		this.prEnsureFolder;
		file = File(path, "w");
		if (file.isOpen.not) { "SDGSampleTags: cannot write %".format(path).warn; ^nil };
		file.write("# superdirt-gui sample analysis - delete it to analyse everything again\n");
		recs.keysValuesDo { |p, r|
			var fields = keys.collect { |k| this.prField(r[k]) };
			file.write(p ++ "\t" ++ fields.join("\t") ++ "\t" ++ (r[\auto] ? []).join(",")
				++ "\t" ++ (r[\onsets] ? []).collect { |t| (t * 1000).round.asInteger }.join(",") ++ "\n");
		};
		file.close;
		^path
	}

	*prField { |v|
		^case
		{ v.isNil } { "-" }
		{ v == true } { "1" }
		{ v == false } { "0" }
		{ v.isFloat } { v.round(0.0001).asString }
		{ v.asString }
	}

	*readRecords { |path|
		var out = Dictionary.new, keys = this.recordKeys, text;
		if (File.exists(path).not) { ^out };
		text = File.readAllString(path);
		text.split($\n).do { |line|
			var f, r;
			if (line.notEmpty and: { line[0] != $# }) {
				f = line.split($\t);
				if (f.size >= (keys.size + 3)) {
					r = ();
					keys.do { |k, i| r[k] = this.prParse(k, f[i + 1]) };
					r[\auto] = f[keys.size + 1].split($,).reject(_.isEmpty).collect(_.asSymbol);
					r[\onsets] = f[keys.size + 2].split($,).reject(_.isEmpty).collect { |s| s.asInteger / 1000 };
					out[this.keyOf(f[0])] = r;
				};
			};
		};
		^out
	}

	*prParse { |key, s|
		if (s.isNil or: { s == "-" }) { ^nil };
		^switch (key,
			\key, { s },
			\bpmSure, { s == "1" }, \keySure, { s == "1" }, \lengthFit, { s == "1" },
			\version, { s.asInteger }, \size, { s.asInteger }, \mtime, { s.asInteger },
			\numChannels, { s.asInteger }, \created, { s.asInteger }, \dateAdded, { s.asInteger },
			{ s.asFloat }
		)
	}

	// path, added tags, removed tags, bpm, beat offset, key, first seen - tab-separated
	*writeManual { |path, man|
		var file;
		this.prEnsureFolder;
		if (File.exists(path)) { File.delete(path ++ ".bak"); File.copy(path, path ++ ".bak") };
		file = File(path, "w");
		if (file.isOpen.not) { "SDGSampleTags: cannot write %".format(path).warn; ^nil };
		file.write("# superdirt-gui tags set by hand: path, added, taken away, bpm, beat offset, key, first seen\n");
		man.keys.asArray.sort.do { |p|
			var m = man[p];
			file.write([p, (m[\add] ? []).join(","), (m[\remove] ? []).join(","),
				this.prField(m[\bpm]), this.prField(m[\offset]), this.prField(m[\key]), this.prField(m[\firstSeen])
			].join("\t") ++ "\n");
		};
		file.close;
		^path
	}

	*readManual { |path|
		var out = Dictionary.new;
		if (File.exists(path).not) { ^out };
		File.readAllString(path).split($\n).do { |line|
			var f;
			if (line.notEmpty and: { line[0] != $# }) {
				f = line.split($\t);
				if (f.size >= 7) {
					out[this.keyOf(f[0])] = (
						add: f[1].split($,).reject(_.isEmpty).collect(_.asSymbol),
						remove: f[2].split($,).reject(_.isEmpty).collect(_.asSymbol),
						bpm: this.prParse(\bpm, f[3]), offset: this.prParse(\offset, f[4]),
						key: this.prParse(\key, f[5]), firstSeen: this.prParse(\created, f[6])
					);
				};
			};
		};
		^out
	}

	*prEnsureFolder { if (File.exists(this.folder).not) { File.mkdir(this.folder) } }
}
