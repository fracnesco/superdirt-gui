// =====================================================================
// SDGSlicer - a sample cut into slices, written as a bank of its own
//
//   e = SDGSampleEdit(~sdg, \breaks152, 0); e.analyseFeatures;
//   x = SDGSlicer(e);
//   x.mode = \grid; x.division = 1/16;      // on the grid of e.bpm and e.beatOffset
//   x.mode = \transients; x.sensitivity = 0.7;   // on the attacks the analysis heard
//   x.starts;                               // the frames each slice starts on
//   x.addSliceAt(22050); x.removeSliceNear(44100);   // by hand, on top of either
//   x.export;                               // breaks152-000_slices/ beside the file, read as a bank
//   x.export(SDGSlicer.samplesFolder +/+ "brk");   // brk/brk_00.wav, brk_01.wav ... into Dirt-Samples
//
// The slices cover the editor's trim: the first starts where the trim does,
// the last ends where it ends. A slice is written with the editor's gain and
// DC as set, and short fades of its own so no cut clicks; the file the
// slices come from is never written to. The folder the slices go to is new
// every time (_2, _3 ... when the name is taken), and is loaded into the
// library as a bank named after it. Every file is named after the folder too,
// with the slice's index from 0: the n Tidal plays it as.
//
// Notifies \slices when the starts change.
// =====================================================================

SDGSlicer {
	classvar <>fadeInMs = 1, <>fadeOutMs = 4;
	classvar <>exportParent;		// where the editor's export last went; nil: Dirt-Samples

	var <edit, <mode = \grid, <division = 0.0625, <sensitivity = 0.5, <minLength = 0.03;
	var <added, <removed, <starts;

	*new { |edit| ^super.newCopyArgs(edit).init }

	init {
		added = [];
		removed = [];
		starts = [];
		this.compute;
	}

	// the divisions a grid offers, as [fraction of a whole note, name]
	*divisions {
		^[[1, "1 bar"], [1/2, "1/2"], [1/4, "1/4"], [1/8, "1/8"], [1/16, "1/16"], [1/32, "1/32"],
			[1/6, "1/4 T"], [1/12, "1/8 T"], [1/24, "1/16 T"]]
	}

	*modes { ^#[\grid, \transients] }

	mode_ { |m| mode = m.asSymbol; this.compute }
	division_ { |d| division = d.asFloat.max(1/128); this.compute }
	sensitivity_ { |v| sensitivity = v.asFloat.clip(0, 1); this.compute }
	// seconds: a cut closer than this to the one before it is not made
	minLength_ { |seconds| minLength = seconds.asFloat.max(0.001); this.compute }

	sampleRate { ^edit.sampleRate }

	// ------------------------------------------------------------ the cuts

	compute {
		var f0 = edit.startFrame, f1 = edit.endFrame, sr = edit.sampleRate, gap = (minLength * sr).round.asInteger;
		var tol = (0.003 * sr).round.asInteger, pts, out;
		pts = if (mode == \grid) { this.gridStarts(f0, f1) } { this.transientStarts(f0, f1) };
		pts = pts.reject { |p| removed.any { |r| (p - r).abs <= tol } };
		pts = (pts ++ added).select { |p| p > f0 and: { p < f1 } }.asSet.asArray.sort;
		out = [f0];
		pts.do { |p| if ((p - out.last) >= gap and: { (f1 - p) >= gap }) { out = out.add(p) } };
		starts = out;
		this.changed(\slices);
		^starts
	}

	// every grid line inside the trim
	gridStarts { |f0, f1|
		var sr = edit.sampleRate;
		if (edit.bpm.isNil) { ^[] };
		^edit.gridLines(division, f0 / sr, f1 / sr).collect { |line| (line[0] * sr).round.asInteger }
	}

	// every attack the analysis heard at the sensitivity, made exact
	transientStarts { |f0, f1|
		var sr = edit.sampleRate;
		^edit.transients(sensitivity).select { |t| (t * sr) > (f0 - (0.02 * sr)) and: { (t * sr) < f1 } }
			.collect { |t| edit.refineOnset(t) }
	}

	// [[start, end] ...], frames
	slices {
		var f1 = edit.endFrame;
		^starts.collect { |a, i| [a, starts[i + 1] ? f1] }
	}

	numSlices { ^starts.size }

	// a cut by hand, kept through every change of mode or sensitivity until clearEdits
	addSliceAt { |frame|
		frame = frame.round.asInteger;
		removed = removed.reject { |r| (r - frame).abs <= (0.003 * edit.sampleRate) };
		added = added.add(frame);
		this.compute;
	}

	// the cut nearest a frame, if one is within `tolerance` frames; not the first
	removeSliceNear { |frame, tolerance|
		var tol = tolerance ? (0.01 * edit.sampleRate), i;
		i = starts.drop(1).minIndex { |s| (s - frame).abs };
		if (i.isNil) { ^false };
		i = i + 1;
		if ((starts[i] - frame).abs > tol) { ^false };
		added = added.reject { |a| (a - starts[i]).abs <= (0.003 * edit.sampleRate) };
		removed = removed.add(starts[i]);
		this.compute;
		^true
	}

	clearEdits { added = []; removed = []; this.compute }

	// ------------------------------------------------------------ writing

	// "<file>_slices" beside the file, or with _2, _3 ... after it if that is taken
	*freeFolder { |parent, name|
		var path = parent +/+ name, n = 2;
		while { File.exists(path) } { path = parent +/+ (name ++ "_" ++ n); n = n + 1 };
		^path
	}

	// "<file>_slices", a name SuperDirt takes as a bank's
	defaultName { ^SuperDirtGUI.bankName(edit.path.basename.splitext[0] ++ "_slices").asString }

	defaultFolder { ^this.class.freeFolder(edit.path.dirname, this.defaultName) }

	// the folder SuperDirt's own loadSoundFiles reads, Dirt-Samples beside the
	// SuperDirt quark, or nil when it is not there
	*samplesFolder {
		var cls = \DirtSoundLibrary.asClass, path;
		if (cls.isNil) { ^nil };
		path = cls.filenameSymbol.asString.dirname.dirname.dirname +/+ "Dirt-Samples";
		^if (File.exists(path)) { path }
	}

	// where the editor's export dialog opens: where it went last, else
	// Dirt-Samples, else beside the file
	*exportStart { |edit|
		^[exportParent, this.samplesFolder].detect { |p| p.notNil and: { File.exists(p) } } ?? { edit !? { edit.path.dirname } }
	}

	// the files an export to folder would write: <folder>_00.wav, <folder>_01.wav ...
	fileNames { |folder|
		var base = folder.withoutTrailingSlash.basename, ext = edit.path.splitext[1] ? "wav";
		var digits = (this.numSlices - 1).asString.size.max(2);
		^this.numSlices.collect { |i| "%_%.%".format(base, i.asString.padLeft(digits, "0"), ext) }
	}

	// Every slice to a file of its own in a new folder, each named after the
	// folder with its index from 0 (the n Tidal plays it as); then the folder
	// into the library as a bank, every slice tagged `slice` (and given the
	// grid's bpm). Answers (folder:, paths:, bank:), or nil.
	export { |folder, load = true|
		var paths, names, store, files, sdg = edit.sdg;
		folder = (folder ?? { this.defaultFolder }).standardizePath.withoutTrailingSlash;
		if (File.exists(folder)) { folder = this.class.freeFolder(folder.dirname, folder.basename) };
		if (this.numSlices < 1) { "SDGSlicer: nothing to slice".warn; ^nil };
		File.mkdir(folder);
		if (File.exists(folder).not) { "SDGSlicer: could not make %".format(folder).warn; ^nil };
		files = this.fileNames(folder);
		paths = this.slices.collect { |pair, i|
			var p = folder +/+ files[i];
			edit.writeRange(p, pair[0], pair[1], fadeInMs / 1000, fadeOutMs / 1000)
		};
		if (paths.includes(nil)) { "SDGSlicer: some slices could not be written".warn };
		paths = paths.reject(_.isNil);
		if (load and: { sdg.notNil }) {
			names = sdg.loadSamples(folder);
			store = sdg.tags;
			paths.do { |p|
				store.addTag(p, \slice);
				if (mode == \grid and: { edit.bpm.notNil }) { store.setBpm(p, edit.bpm, 0) };
			};
			store.enqueue(paths, true);
		};
		this.changed(\exported, folder);
		^(folder: folder, paths: paths, bank: names !? (_.first))
	}
}
