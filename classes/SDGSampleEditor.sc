// =====================================================================
// SDGSampleEditor - a window on one sample: trim it, gain it, take the DC
// out, fade the edges, read its numbers, save it back into the library
//
//   SDGSampleEditor(~sdg, \808, 3);            // bank, index - the explorer's edit button
//   SDGSampleEditor.forPath("~/x.wav", ~sdg);  // a file that is not in the library
//
// Playback is SuperDirt's own Buffer through a small synth at the head
// of an orbit's group, so it is heard through that orbit's effects and
// fader - pick the orbit in the window. The trim, the gain, the fades and
// the DC are all applied live in the synth; the samples are only rewritten
// on save. The numbers on the right are SDGSampleAnalysis on the file as
// it is and on what the save would write.
//
// Under the header, what the analysis says (SDGSampleFeatures, kept by
// SDGSampleTags): the bpm, sure or not, with the beat grid it draws over the
// wave - halve it, double it, slide it with alt + drag, or put its first beat
// on the first attack; the key; the tags. Whatever is changed there is kept
// for the file and is what the explorer and Tidal see.
//
// The slicer (SDGSlicer) cuts the trim on the grid at a division, or on the
// attacks at a sensitivity, and shows the cuts on the wave as they move;
// shift + click adds or takes away a cut. export slices... asks where (the
// Finder's folder dialog, at Dirt-Samples the first time) and under what
// name, writes every slice to a new folder of that name, each file named
// after it with its index, and loads it as a bank; the file stays as it is.
//
// Keys, when the window has focus: space plays and stops, 0 shows the
// whole file, - and = zoom, f zooms to the trim, left and right slide,
// g shows and hides the grid, cmd-s saves (ctrl-s on Windows and Linux).
// =====================================================================

SDGSampleEditor {
	classvar <>windowBounds;
	classvar <all;						// the open editors: opening a sample twice fronts the first
	classvar <>folderChooser;			// { |start, ok| } in place of the Finder's folder dialog: checks set it

	var <sdg, <edit, <window, <waveView, <hints, <orbitIndex;
	var titleText, infoText, orbitMenu, playBtn, stopBtn;
	var startBox, endBox, fadeInBox, fadeOutBox;
	var gainKnob, gainBox, normBtn, ceilBox, dcBox, dcInfo;
	var resetBtn, revertBtn, saveBtn, saveAsBtn;
	var statTexts;						// key -> [original, result] StaticTexts
	var previewSynth, posFunc, tempBuffer, defReady = false, statsPending = false;
	var font, smallFont, boldFont, monoFont, gainSpec;
	var <slicer, analysisRoutine, bpmBox, offsetBox, bpmFlag, keyField, keyFlag, tagsField, gridCheck, analysisText;
	var sliceCheck, sliceModeMenu, sliceDivMenu, sensKnob, sensBox, minBox, sliceCount, exportBtn, sliceViews, <exportDialog;
	var persistPending = false;

	*initClass { all = List.new }

	// the modifier a shortcut takes here: cmd on macOS, ctrl elsewhere, which
	// is how QtCollider reports the key (QKeyModifiers.toCocoa)
	*isShortcut { |mod|
		^mod.notNil and: { Platform.case(\osx, { mod.isCmd }, { mod.isCtrl }) }
	}

	// what the hints call the save shortcut
	*saveKeyName { ^Platform.case(\osx, { "cmd-s" }, { "ctrl-s" }) }

	*new { |sdg, name, index = 0, orbit = 0|
		var open = all.detect { |e|
			e.edit.name == name.asSymbol and: { e.edit.index == index } and: { e.isOpen }
		};
		if (open.notNil) { open.front; ^open };
		^super.new.init(SDGSampleEdit(sdg, name, index), sdg, orbit)
	}

	*forPath { |path, sdg, orbit = 0|
		sdg = sdg ? SuperDirtGUI.current;
		if (sdg.isNil) {
			Error("SDGSampleEditor: no SuperDirtGUI instance - make one first, e.g. SuperDirtGUI(~dirt)").throw
		};
		^super.new.init(SDGSampleEdit.fromPath(path, sdg), sdg, orbit)
	}

	init { |anEdit, aSdg, orbit|
		edit = anEdit;
		sdg = aSdg;
		orbitIndex = (orbit ? 0).clip(0, ((this.dirt.orbits !? (_.size)) ? 1) - 1);
		font = Font(Font.defaultSansFace, 11);
		smallFont = Font(Font.defaultSansFace, 9);
		boldFont = Font(Font.defaultSansFace, 12, true);
		monoFont = Font(Font.defaultMonoFace, 10);
		gainSpec = ControlSpec(-24, 24, \lin, 0.1, 0);
		this.prBuild;
		edit.addDependant(this);
		this.store !? (_.addDependant(this));
		all.add(this);
		this.prListenForPosition;
		this.prEnsureDef;
		this.prGridFromStore;
		this.refresh;
		window.front;
		this.analyse;
	}

	// what the explorer and Tidal know about the file
	store { ^sdg !? (_.tags) }

	info { ^this.store !? { |st| st.infoOf(edit.path) } }

	// the SuperDirt the preview plays into
	dirt { ^sdg.dirt }

	isOpen { ^window.notNil and: { window.isClosed.not } }

	front { if (this.isOpen) { window.front } }

	close { if (this.isOpen) { window.close } }

	isPlaying { ^previewSynth.notNil }

	// --------------------------------------------------------------- build

	prBuild {
		var bounds = windowBounds ?? { Rect(120, 120, 980, 700) };
		window = Window("SuperDirtGUI sample - " ++ edit.label, bounds);
		if (GUI.id == \qt) { window.view.palette = QPalette.dark };
		window.view.background = SDGWindow.colBg;

		hints = SDGHintBar("trim with the S and E markers, set a gain, take the DC out, then save"
			" - the library plays the new file at once");
		waveView = SDGWaveView(nil);
		waveView.hints = hints;
		waveView.edit = edit;
		waveView.action = { this.prRefreshBoxes; this.prScheduleStats };

		waveView.gridAction = { |wv, done| this.prGridMoved(done) };
		waveView.sliceAction = { this.prRefreshSlicer };

		window.layout = VLayout(
			this.prHeaderRow,
			this.prAnalysisRow,
			[waveView.view, stretch: 1],
			this.prTrimRow,
			this.prGainRow,
			this.prSlicerRow,
			this.prStatsPanel,
			hints.view
		).margins_(8).spacing_(6);

		hints.watch(window);
		this.prAttachHints;
		window.view.keyDownAction = { |v, char, mod, uni, keycode, key| this.prKeyDown(char, mod, key) };
		window.onClose = { this.prClosed };
	}

	prHeaderRow {
		titleText = StaticText().string_(edit.label).font_(boldFont).stringColor_(SDGWindow.colAccent);
		infoText = StaticText().stringColor_(SDGWindow.colDim).font_(smallFont);
		playBtn = this.prButton("play", 46, { this.play }, "hear the trim through the orbit, with the gain, the fades and the DC as set (space)");
		stopBtn = this.prButton("stop", 46, { this.stop }, "stop (space)");
		orbitMenu = PopUpMenu().fixedWidth_(84)
			.items_(this.dirt.orbits.collect { |o, i| "orbit " ++ i })
			.value_(orbitIndex)
			.action_({ |m| orbitIndex = m.value });
		resetBtn = this.prButton("reset", 50, { edit.reset },
			"the whole file again, no gain, no fades, the DC left in");
		revertBtn = this.prButton("revert", 54, { this.prRevert },
			"put the original file back - the .bak the first save kept beside it");
		saveBtn = this.prButton("save", 50, { this.prSave },
			"write the result over the file (%); the original is kept beside it as .bak the first time".format(this.class.saveKeyName));
		saveAsBtn = this.prButton("save as...", 74, { this.prSaveAs },
			"write the result to a new file - into the bank's folder and it is added to the bank");
		^HLayout(
			titleText, infoText, nil,
			playBtn, stopBtn, this.prLabel("orbit", 30), orbitMenu,
			nil,
			resetBtn, revertBtn, saveBtn, saveAsBtn
		).spacing_(4)
	}

	prTrimRow {
		startBox = this.prNumBox(1, { |v| edit.start_(v / 1000 / edit.duration.max(1e-9)) });
		endBox = this.prNumBox(1, { |v| edit.end_(v / 1000 / edit.duration.max(1e-9)) });
		fadeInBox = this.prNumBox(1, { |v| edit.fadeIn_(v / 1000) });
		fadeOutBox = this.prNumBox(1, { |v| edit.fadeOut_(v / 1000) });
		^HLayout(
			this.prLabel("start ms", 50), startBox,
			this.prLabel("end ms", 44), endBox,
			this.prLabel("fade in ms", 62), fadeInBox,
			this.prLabel("fade out ms", 66), fadeOutBox,
			this.prButton("-", 26, { waveView.zoom(0.5) }, "zoom out (-)"),
			this.prButton("+", 26, { waveView.zoom(2) }, "zoom in (=)"),
			this.prButton("fit", 34, { waveView.zoomAll }, "the whole file (0)"),
			this.prButton("trim", 40, { waveView.zoomToRegion }, "zoom to the trim (f)"),
			nil,
			StaticText().string_("drag  top: S/E   bottom: fades   middle: move the trim")
				.stringColor_(SDGWindow.colDim).font_(smallFont).align_(\right).minWidth_(280)
		).spacing_(4)
	}

	prGainRow {
		gainKnob = SDGWindow.makeKnob(28, true)
			.action_({ |v| edit.gainDb_(gainSpec.map(v.value)) });
		gainBox = NumberBox().fixedWidth_(56).decimals_(1).align_(\center)
			.action_({ |v| edit.gainDb_(v.value) });
		ceilBox = NumberBox().fixedWidth_(46).decimals_(1).clipLo_(-24).clipHi_(0)
			.value_(SDGSampleEdit.ceilingDb);
		normBtn = this.prButton("normalize to", 84, { edit.normalize(ceilBox.value) },
			"set the gain so the loudest sample of the trim sits at the dBFS in the box");
		dcBox = CheckBox().string_("remove DC")
			.action_({ |v| edit.removeDC_(v.value) });
		dcInfo = StaticText().stringColor_(SDGWindow.colDim).font_(smallFont);
		^HLayout(
			this.prLabel("gain dB", 46), gainKnob, gainBox,
			normBtn, ceilBox, this.prLabel("dBFS", 30),
			dcBox, dcInfo,
			nil
		).spacing_(4)
	}

	// the bpm and its grid, the key, the tags
	prAnalysisRow {
		var panel = View().background_(SDGWindow.colPanel);
		analysisText = StaticText().stringColor_(SDGWindow.colDim).font_(smallFont).string_("analysing...");
		bpmBox = NumberBox().fixedWidth_(58).decimals_(2).clipLo_(0).clipHi_(999).step_(0.01)
			.action_({ |v| this.setBpm(if (v.value > 0) { v.value } { nil }) });
		bpmFlag = StaticText().font_(smallFont).fixedWidth_(44);
		offsetBox = NumberBox().fixedWidth_(58).decimals_(1).clipLo_(0)
			.action_({ |v| edit.beatOffset_(v.value / 1000); this.prGridMoved(true) });
		gridCheck = CheckBox().string_("grid").value_(true)
			.action_({ |v| waveView.showGrid = v.value; waveView.refresh });
		keyField = TextField().fixedWidth_(46)
			.action_({ |v| this.setKey(v.string) });
		keyFlag = StaticText().font_(smallFont).fixedWidth_(44);
		tagsField = TextField().minWidth_(160)
			.action_({ |v| this.setTags(v.string.split($ ).reject(_.isEmpty)) });
		panel.layout = VLayout(
			HLayout(
				this.prLabel("bpm", 24), bpmBox, bpmFlag,
				this.prButton("÷2", 26, { edit.bpm !? { |b| this.setBpm(b / 2) } }, "half the bpm: a beat twice as long"),
				this.prButton("×2", 26, { edit.bpm !? { |b| this.setBpm(b * 2) } }, "double the bpm"),
				this.prLabel("beat 1 ms", 56), offsetBox,
				this.prButton("to 1st hit", 66, { this.snapGridToFirstHit }, "put the grid's first beat on the first attack of the file"),
				gridCheck,
				this.prLabel("key", 24), keyField, keyFlag,
				this.prLabel("tags", 28), [tagsField, stretch: 1],
				this.prButton("analyse", 56, { this.analyse(true) }, "analyse the file again: onsets, bpm, key, tags")
			).spacing_(4),
			analysisText
		).margins_(5).spacing_(3);
		^panel
	}

	prSlicerRow {
		var panel = View().background_(SDGWindow.colPanel);
		sliceCheck = CheckBox().string_("slicer").action_({ |v| this.slicing = v.value });
		sliceModeMenu = PopUpMenu().fixedWidth_(96).items_(["on the grid", "on attacks"])
			.action_({ |m| slicer !? { slicer.mode = SDGSlicer.modes[m.value] }; this.prRefreshSlicer });
		sliceDivMenu = PopUpMenu().fixedWidth_(72).items_(SDGSlicer.divisions.collect(_[1])).value_(4)
			.action_({ |m| this.prDivisionChanged });
		sensKnob = SDGWindow.makeKnob(26).value_(0.5)
			.action_({ |k| sensBox.value = k.value.round(0.01); slicer !? { slicer.sensitivity = k.value } });
		sensBox = NumberBox().fixedWidth_(44).decimals_(2).clipLo_(0).clipHi_(1).step_(0.01).value_(0.5)
			.action_({ |b| sensKnob.value = b.value; slicer !? { slicer.sensitivity = b.value } });
		minBox = NumberBox().fixedWidth_(44).decimals_(0).clipLo_(1).value_(30)
			.action_({ |b| slicer !? { slicer.minLength = b.value / 1000 } });
		sliceCount = StaticText().stringColor_(SDGWindow.colText).font_(smallFont).fixedWidth_(70);
		exportBtn = this.prButton("export slices...", 100, { this.exportSlicesDialog },
			"pick a folder and a name: every slice goes to a new folder of that name, files numbered as Tidal's n, and is loaded as a bank - the file itself is not touched");
		sliceViews = [sliceModeMenu, sliceDivMenu, sensKnob, sensBox, minBox, exportBtn];
		panel.layout = HLayout(
			sliceCheck, sliceModeMenu, sliceDivMenu,
			this.prLabel("sensitivity", 58), sensKnob, sensBox,
			this.prLabel("min ms", 40), minBox,
			sliceCount, nil, exportBtn,
			this.prButton("clear cuts", 64, { slicer !? (_.clearEdits) }, "take away every cut made by hand with shift + click")
		).margins_(5).spacing_(4);
		sliceViews.do(_.enabled_(false));
		^panel
	}

	// original | result, one row per reading
	prStatsPanel {
		var rows, keys, panel;
		statTexts = IdentityDictionary.new;
		keys = [
			[\duration, "duration"], [\peak, "peak"], [\rms, "rms"], [\crest, "crest"],
			[\lufs, "loudness"], [\momentary, "loudest 400 ms"], [\dc, "dc offset"], [\clipped, "clipped"]
		];
		rows = [[
			StaticText().string_("").fixedWidth_(90),
			StaticText().string_("original").stringColor_(SDGWindow.colDim).font_(smallFont).align_(\right).fixedWidth_(150),
			StaticText().string_("result").stringColor_(SDGWindow.colAccent).font_(smallFont).align_(\right).fixedWidth_(150)
		]] ++ keys.collect { |pair|
			var orig = StaticText().stringColor_(SDGWindow.colText).font_(monoFont).align_(\right).fixedWidth_(150);
			var res = StaticText().stringColor_(SDGWindow.colText).font_(monoFont).align_(\right).fixedWidth_(150);
			statTexts[pair[0]] = [orig, res];
			[this.prLabel(pair[1], 90), orig, res]
		};
		panel = View().background_(SDGWindow.colPanel);
		panel.layout = HLayout(
			GridLayout.rows(*rows).spacing_(2).margins_(0),
			nil
		).margins_(6).spacing_(4);
		^panel
	}

	// --------------------------------------------------------------- hints

	prAttachHints {
		var h = hints;
		h.attach(titleText, "the bank and the index this sample is played as, from Tidal or the explorer");
		h.attach(infoText, "the file behind it");
		h.attach(orbitMenu, "the orbit the preview plays through - its effects and its fader apply");
		h.attach(waveView.view, "one lane per channel - drag the top strip for the trim, the bottom for the"
			" fades, the middle to move the trim   -   cmd + two fingers up and down zooms, two fingers"
			" on their own slide, double click shows the whole file");
		h.attach(startBox, "where the file will start, in milliseconds");
		h.attach(endBox, "where it will end");
		h.attach(fadeInBox, "a sine fade in over the first milliseconds of the trim");
		h.attach(fadeOutBox, "a sine fade out over the last");
		h.attach([gainKnob, gainBox], "dB on the samples - drawn into the wave, heard in the preview, written on save");
		h.attach(ceilBox, "the dBFS normalize aims the peak at");
		h.attach(dcBox, "take the mean of each channel out - the dashed line in the wave");
		h.attach(dcInfo, "the offset of each channel as it is in the file");
		h.attach(bpmBox, "the bpm of the grid - what the analysis heard until you change it; 0 takes the grid away");
		h.attach(bpmFlag, "sure: the analysis trusts its bpm; unsure: check it against the grid; by hand: you set it");
		h.attach(offsetBox, "where a beat of the grid falls, ms from the start - alt + drag on the wave slides it");
		h.attach(gridCheck, "show the beat grid over the wave: bars, beats, and the slicer's division (g)");
		h.attach(keyField, "the key (Am, F#, Eb) or the note of a one-shot (C4) - type to set it, empty for the analysis's");
		h.attach(keyFlag, "sure, unsure or by hand, as for the bpm");
		h.attach(tagsField, "the tags of the file, separated by spaces - enter keeps them; Tidal can pick samples by them");
		h.attach(analysisText, "what the analysis measured");
		h.attach(sliceCheck, "cut the trim into slices - the cuts show on the wave as you change what makes them");
		h.attach(sliceModeMenu, "on the grid: every division of the bpm grid; on attacks: wherever the analysis hears one");
		h.attach(sliceDivMenu, "how long a slice on the grid is: a bar, a quarter note, a sixteenth... T for triplets");
		h.attach([sensKnob, sensBox], "on attacks: how weak an attack still makes a cut - more is more slices; the dashed line on the curve");
		h.attach(minBox, "no slice shorter than this, ms");
		h.attach(sliceCount, "how many slices the export would write");
		statTexts.keysValuesDo { |key, texts|
			h.attach(texts, switch (key,
				\duration, { "seconds, and frames, of the file and of the trim" },
				\peak, { "the loudest sample, dBFS - over 0 and an integer file clips" },
				\rms, { "the mean level over every channel, dBFS" },
				\crest, { "peak over rms: how spiky it is" },
				\lufs, { "integrated loudness, K-weighted and gated as BS.1770 says - a stretch shorter than 400 ms is read as one block" },
				\momentary, { "the loudest 400 ms window, LUFS" },
				\dc, { "the mean of each channel: the offset a remove DC takes out" },
				\clipped, { "samples already at full scale in the file, and whether the result would pass it" },
				{ "" }
			));
		};
	}

	// ------------------------------------------------------------- widgets

	prLabel { |str, width|
		var t = StaticText().string_(str).stringColor_(SDGWindow.colDim).font_(smallFont).align_(\right);
		width !? { t.fixedWidth_(width) };
		^t
	}

	prButton { |str, width, func, hint|
		var b = Button().fixedWidth_(width).states_([[str, SDGWindow.colText, SDGWindow.colPanel]])
			.action_({ func.value });
		hint !? { hints.attach(b, hint) };
		^b
	}

	prNumBox { |decimals, func|
		var nb = NumberBox().fixedWidth_(66).decimals_(decimals).clipLo_(0);
		nb.action = { |v| func.value(v.value) };
		^nb
	}

	// ------------------------------------------------------------- refresh

	refresh {
		this.prRefreshHeader;
		this.prRefreshBoxes;
		this.prRefreshStats;
		this.prRefreshAnalysis;
		this.prRefreshSlicer;
		waveView.invalidate;
	}

	// ------------------------------------------------------------ analysis

	// the bpm, the beat and the key the explorer knows for the file, onto the edit
	prGridFromStore {
		var info = this.info;
		if (info.notNil and: { info[\bpm].notNil }) {
			edit.bpm = info[\bpm];
			edit.beatOffset = info[\beatOffset] ? 0;
		};
	}

	// the features of the whole file, in the background; again, and the
	// library's analysis too, when forced
	analyse { |force = false|
		analysisRoutine !? (_.stop);
		analysisText.string = "analysing...";
		analysisRoutine = Routine {
			var info;
			SDGSampleFeatures.breathing = true;
			edit.analyseFeatures;
			SDGSampleFeatures.breathing = false;
			if (force) { this.store !? { |st| st.analyse(edit.path, true) } };
			info = this.info;
			// what the file is known as wins over this pass, unless there is nothing
			if (info.notNil and: { info[\bpm].notNil }) {
				edit.bpm = info[\bpm];
				edit.beatOffset = info[\beatOffset] ? 0;
			};
			analysisRoutine = nil;
			if (this.isOpen) { this.prRefreshAnalysis; this.prRefreshSlicer; waveView.refresh };
		}.play(AppClock);
	}

	setBpm { |bpm|
		edit.bpm = bpm;
		if (bpm.isNil) { edit.beatOffset = 0 };
		this.prPersistGrid;
	}

	setKey { |str|
		var key = str.asString.stripWhiteSpace;
		this.store !? { |st| st.setKey(edit.path, if (key.isEmpty) { nil } { key }) };
		this.prRefreshAnalysis;
	}

	setTags { |words|
		this.store !? { |st| st.setTags(edit.path, words) };
		this.prRefreshAnalysis;
	}

	// the grid's first beat on the first attack, made exact to the sample
	snapGridToFirstHit {
		var t = edit.firstTransient;
		if (edit.bpm.isNil) { ^hints.note("no grid - give it a bpm first") };
		if (t.isNil) { ^hints.note("no attack heard in the file") };
		edit.beatOffset = edit.refineOnset(t) / edit.sampleRate;
		this.prPersistGrid;
	}

	prGridMoved { |done|
		offsetBox.value = (edit.beatOffset * 1000).round(0.1);
		if (done) { this.prPersistGrid };
	}

	// a grid set here is the file's bpm from now on, by hand
	prPersistGrid {
		this.store !? { |st| st.setBpm(edit.path, edit.bpm, edit.beatOffset) };
		this.prRefreshAnalysis;
		slicer !? (_.compute);
	}

	prFlag { |view, sure, byHand, present|
		view.string = case { present.not } { "" } { byHand } { "by hand" } { sure } { "sure" } { "unsure" };
		view.stringColor = case { byHand } { SDGWindow.colAccent } { sure } { SDGWindow.colOn } { SDGWindow.colWarn };
	}

	prRefreshAnalysis {
		var info = this.info, f = edit.features, parts;
		if (bpmBox.isNil) { ^this };
		bpmBox.value = edit.bpm ? 0;
		offsetBox.value = (edit.beatOffset * 1000).round(0.1);
		if (info.notNil and: { info[\analysed] or: { info[\bpmByHand] } or: { info[\keyByHand] } }) {
			this.prFlag(bpmFlag, info[\bpmSure], info[\bpmByHand], info[\bpm].notNil);
			if (keyField.hasFocus.not) { keyField.string = info[\key] ? "" };
			this.prFlag(keyFlag, info[\keySure], info[\keyByHand], info[\key].notNil);
			if (tagsField.hasFocus.not) { tagsField.string = info[\tags].join(" ") };
		} {
			this.prFlag(bpmFlag, f !? { f[\bpmSure] } ? false, false, edit.bpm.notNil);
			if (keyField.hasFocus.not) { keyField.string = f !? { f[\key] } ? "" };
			this.prFlag(keyFlag, f !? { f[\keySure] } ? false, false, f.notNil and: { f[\key].notNil });
			if (tagsField.hasFocus.not) { tagsField.string = info !? { info[\tags].join(" ") } ? "" };
		};
		if (f.isNil) {
			analysisText.string = if (analysisRoutine.notNil) { "analysing..." } { "" };
		} {
			parts = [
				"% attack%".format(f[\onsets].size, if (f[\onsets].size == 1) { "" } { "s" }),
				"attack % ms".format((f[\attack] * 1000).round(0.1)),
				"sounds % s".format(f[\length].round(0.01)),
				"centroid % Hz".format(f[\centroid].round),
				"tonal %".format(f[\tonal].round(0.01)),
				f[\bpm] !? { |b| "analysis: % bpm (%)%".format(b, f[\bpmConfidence].round(0.01), if (f[\lengthFit] == true) { ", fits the length" } { "" }) } ? "analysis: no tempo",
				f[\key] !? { |k| "% (%)".format(k, f[\keyConfidence].round(0.01)) } ? "no key"
			];
			analysisText.string = parts.join("  ·  ");
		};
	}

	// ------------------------------------------------------------- slicer

	slicing { ^slicer.notNil }

	slicing_ { |bool|
		if (bool and: { slicer.isNil }) {
			slicer = SDGSlicer(edit);
			slicer.mode = SDGSlicer.modes[sliceModeMenu.value];
			slicer.division = SDGSlicer.divisions[sliceDivMenu.value][0];
			slicer.sensitivity = sensBox.value;
			slicer.minLength = minBox.value / 1000;
			slicer.addDependant(this);
		};
		if (bool.not and: { slicer.notNil }) {
			slicer.removeDependant(this);
			slicer = nil;
		};
		sliceCheck.value = bool;
		sliceViews.do(_.enabled_(bool));
		waveView.slicer = slicer;
		this.prRefreshSlicer;
		waveView.refresh;
	}

	prDivisionChanged {
		var d = SDGSlicer.divisions[sliceDivMenu.value][0];
		waveView.gridDivision = d;
		slicer !? { slicer.division = d };
		waveView.refresh;
	}

	prRefreshSlicer {
		if (sliceCount.isNil) { ^this };
		sliceCount.string = if (slicer.isNil) { "" } {
			if (slicer.mode == \grid and: { edit.bpm.isNil }) { "no grid" } { "% slice%".format(slicer.numSlices, if (slicer.numSlices == 1) { "" } { "s" }) }
		};
		slicer !? {
			waveView.gridDivision = slicer.division;
			sliceModeMenu.value = SDGSlicer.modes.indexOf(slicer.mode) ? 0;
			sliceDivMenu.value = SDGSlicer.divisions.detectIndex { |d| (d[0] - slicer.division).abs < 1e-6 } ? sliceDivMenu.value;
		};
		sliceDivMenu.enabled = slicer.notNil and: { slicer.mode == \grid };
		[sensKnob, sensBox].do(_.enabled_(slicer.notNil and: { slicer.mode == \transients }));
		waveView.refresh;
	}

	// every slice to folder (nil: "<file>_slices" beside the file), then a bank
	exportSlices { |folder|
		var result, home = SDGSlicer.samplesFolder;
		if (slicer.isNil) { ^hints.note("switch the slicer on first") };
		result = slicer.export(folder);
		if (result.isNil) { ^hints.note("nothing written - see the post window") };
		hints.note("% slices in % - play them as s \"%\" # n 0 .. %%".format(
			result[\paths].size, result[\folder].basename, result[\bank] ? result[\folder].basename, result[\paths].size - 1,
			if (home.notNil and: { SuperDirtGUI.samePath(result[\folder].dirname, home) }) { " - in Dirt-Samples, so ~dirt.loadSoundFiles reads it at every start" } { "" }));
		^result
	}

	// The Finder's folder dialog for where the slices go (where they went last,
	// Dirt-Samples the first time), then a name for the folder: the bank's
	// name, and every file's with the slice's index after it.
	exportSlicesDialog {
		if (slicer.isNil) { ^hints.note("switch the slicer on first") };
		if (slicer.numSlices < 1) { ^hints.note("nothing to slice") };
		this.class.chooseFolder(SDGSlicer.exportStart(edit), { |dir| if (this.isOpen) { this.prNameDialog(dir) } });
	}

	*chooseFolder { |start, ok|
		var answer = { |path| path !? { { ok.value(path.asString.withoutTrailingSlash) }.defer } };
		if (folderChooser.notNil) { ^folderChooser.value(start, answer) };
		FileDialog(answer, nil, 2, 0, true, start);
	}

	// the name, with what it will write; return exports, escape cancels
	prNameDialog { |dir|
		var win, field, whereText, preview, go, refresh, name, target;
		var font = Font(Font.defaultSansFace, 11);
		var btn = { |str, func, back| Button().states_([[str, if (back.notNil) { Color.black } { SDGWindow.colText }, back ? SDGWindow.colPanel]]).action_({ func.value }) };
		exportDialog !? { |d| if (d[\window].isClosed.not) { d[\window].close } };
		SDGSlicer.exportParent = dir;
		win = Window("export slices - " ++ edit.label,
			Rect(window.bounds.left + 160, window.bounds.top + 160, 520, 132)).background_(SDGWindow.colBg);
		if (GUI.id == \qt) { win.view.palette = QPalette.dark };
		field = TextField().string_(slicer.defaultName).font_(font);
		whereText = StaticText().stringColor_(SDGWindow.colDim).font_(smallFont);
		preview = StaticText().stringColor_(SDGWindow.colDim).font_(smallFont);
		name = { SuperDirtGUI.bankName(field.string.stripWhiteSpace).asString };
		target = { SDGSlicer.freeFolder(dir, name.value) };
		refresh = {
			var n = name.value, t, files;
			whereText.string = "in " ++ dir;
			if (n.isEmpty) { preview.string = "give the folder a name" } {
				t = target.value; files = slicer.fileNames(t);
				preview.string = "%%/%%  -  s \"%\" # n 0 .. %".format(
					if (t.basename != n) { "% is taken there, so: ".format(n) } { "" },
					t.basename, files.first, if (files.size > 1) { " ... " ++ files.last } { "" },
					t.basename, files.size - 1);
			};
		};
		go = { |folderName|
			var n;
			folderName !? { field.string = folderName };
			n = name.value;
			if (n.notEmpty) { win.close; this.exportSlices(dir +/+ n) }
		};
		field.action = { go.value };
		field.keyUpAction = { refresh.value; nil };
		field.keyDownAction = { |view, char, mod, uni, keycode, key| if (key == 16r1000000) { win.close; true } };
		win.layout = VLayout(
			HLayout(StaticText().string_("folder name").stringColor_(SDGWindow.colDim).font_(font), field).spacing_(6),
			HLayout([whereText, stretch: 1], btn.("change...", {
				this.class.chooseFolder(dir, { |d| if (win.isClosed.not) { dir = d; SDGSlicer.exportParent = d; refresh.value; win.front } })
			}).fixedWidth_(70)).spacing_(6),
			preview,
			HLayout(nil, btn.("cancel", { win.close }), btn.("export", { go.value }, SDGWindow.colAccent)).spacing_(4)
		).margins_(8).spacing_(5);
		refresh.value;
		exportDialog = (window: win, folder: { dir }, export: go, preview: { preview.string });
		win.front;
		field.focus(true);
		^win
	}

	prRefreshHeader {
		titleText.string = edit.label;
		infoText.string = "%  ·  % s  ·  % ch  ·  % Hz  ·  % %".format(
			edit.fileName, edit.duration.round(0.001), edit.numChannels,
			edit.sampleRate.asInteger, edit.headerFormat, edit.sampleFormat);
		revertBtn.enabled = edit.hasBackup;
		window.name = "SuperDirtGUI sample - " ++ edit.label;
	}

	prRefreshBoxes {
		var dur = edit.duration, dc = edit.dcOffsets;
		startBox.value = (edit.start * dur * 1000).round(0.1);
		endBox.value = (edit.end * dur * 1000).round(0.1);
		fadeInBox.value = (edit.fadeIn * 1000).round(0.1);
		fadeOutBox.value = (edit.fadeOut * 1000).round(0.1);
		gainKnob.value = gainSpec.unmap(edit.gainDb);
		gainBox.value = edit.gainDb.round(0.1);
		dcBox.value = edit.removeDC;
		dcInfo.string = "dc " ++ dc.collect { |v| this.prSigned(v, 4) }.join("  ");
	}

	// a drag fires a dozen changes a second; the numbers are worked out once
	// it has been still for a moment
	prScheduleStats {
		if (statsPending) { ^this };
		statsPending = true;
		AppClock.sched(0.15, {
			statsPending = false;
			if (this.isOpen) { this.prRefreshStats };
			nil
		});
	}

	prRefreshStats {
		var o = edit.metrics(false), r = edit.metrics(true);
		[[o, 0], [r, 1]].do { |pair|
			var m = pair[0], col = pair[1], db = { |x| SDGSampleAnalysis.dbString(x, "") };
			statTexts[\duration][col].string = "% s  (%)".format(m[\duration].round(0.001), m[\frames]);
			statTexts[\peak][col].string = db.(m[\peak]) ++ " dBFS";
			statTexts[\rms][col].string = db.(m[\rms]) ++ " dBFS";
			statTexts[\crest][col].string = db.(m[\crest]) ++ " dB";
			statTexts[\lufs][col].string = db.(m[\lufs]) ++ " LUFS" ++ if (m[\wholeBlock]) { " (short)" } { "" };
			statTexts[\momentary][col].string = db.(m[\momentary]) ++ " LUFS";
			statTexts[\dc][col].string = m[\dcAfter].collect { |v| this.prSigned(v, 4) }.join(" ");
			statTexts[\clipped][col].string = if (col == 0) {
				if (m[\clipped] > 0) { "% samples".format(m[\clipped]) } { "none" }
			} {
				if (m[\overs]) { "over 0 dBFS" } { "none" }
			};
			statTexts[\clipped][col].stringColor = if (col == 1 and: { m[\overs] }) { SDGWindow.colWarn } { SDGWindow.colText };
			statTexts[\peak][col].stringColor = if (col == 1 and: { m[\overs] }) { SDGWindow.colWarn } { SDGWindow.colText };
		};
	}

	prSigned { |v, decimals = 4|
		var r = v.round(10 ** decimals.neg);
		^if (r > 0) { "+" ++ r } { r.asString }
	}

	// the model speaks; everything on screen follows
	// (no caret inside the deferred function: by the time it runs this method
	// has returned, and a return from a dead frame is an error, not a guard)
	update { |obj, what ... args|
		if (slicer.notNil and: { obj === slicer }) {
			if (what == \slices) { { if (this.isOpen) { this.prRefreshSlicer } }.defer };
			^this
		};
		if (obj === this.store) {
			if (what == \tags and: { args[0].isNil or: { args[0] == edit.path } }) {
				{ if (this.isOpen) { this.prRefreshAnalysis } }.defer
			};
			^this
		};
		if (obj !== edit) { ^this };
		if (what == \grid or: { what == \features }) {
			{ if (this.isOpen) { slicer !? (_.compute); waveView.refresh; this.prRefreshAnalysis } }.defer;
			^this
		};
		{
			if (this.isOpen) { switch (what,
				\params, {
					slicer !? (_.compute);
					this.prRefreshBoxes;
					waveView.refresh;
					this.prScheduleStats;
					previewSynth !? { |sy|
						sy.set(\amp, edit.gainDb.dbamp, \dc, this.prDcArgs);
					};
				},
				\loaded, { this.refresh },
				\saved, {
					this.refresh;
					this.analyse;
					this.store !? { |st| st.enqueue([edit.path], true) };
					waveView.zoomAll;
					hints.note("saved % - the library plays the new file now; revert brings the original back".format(edit.fileName));
				},
				\reverted, {
					this.refresh;
					this.analyse;
					this.store !? { |st| st.enqueue([edit.path], true) };
					waveView.zoomAll;
					hints.note("% is the original again".format(edit.fileName));
				},
				\savedAs, {
					hints.note("wrote " ++ args[0].asString.basename
						++ if (SuperDirtGUI.samePath(args[0].asString.dirname, edit.path.dirname) and: { edit.inLibrary }) {
							" - added to the bank as its last index" } { "" });
				}
			) };
		}.defer;
	}

	// ---------------------------------------------------------------- files

	prSave {
		if (edit.isPristine) { ^hints.note("nothing to save - the file is as it was") };
		if (edit.save.isNil) { hints.note("could not save - see the post window") };
	}

	// the dialog opens in the sample's own folder, with a name beside it
	prSaveAs {
		var ext = edit.path.splitext[1] ? "wav";
		var suggested = edit.path.dirname +/+ (edit.path.basename.splitext[0] ++ "-edit." ++ ext);
		Dialog.savePanel({ |p|
			if (p.splitext[1].isNil) { p = p ++ "." ++ ext };
			if (edit.saveAs(p).isNil) { hints.note("could not write " ++ p) };
		}, nil, suggested);
	}

	prRevert {
		this.stop;
		if (edit.revert.isNil) { hints.note("nothing to revert to") };
	}

	// -------------------------------------------------------------- preview

	previewDefName { ^SDGSampleEditor.previewDefName(edit.numChannels, this.dirt.numChannels) }

	*previewDefName { |src, out| ^"sdg_editpreview_%_%".format(src, out).asSymbol }

	// The library's Buffer, read from startFrame to endFrame once, with the
	// gain, the DC and the fades applied on the way, into an orbit's dry bus.
	// The read position comes back 25 times a second for the playhead.
	*previewDef { |src = 1, out = 2|
		^SynthDef(this.previewDefName(src, out), {
			|bufnum, dryBus, startFrame = 0, endFrame = 1, amp = 1, fadeIn = 0, fadeOut = 0, gate = 1|
			var dc = NamedControl.kr(\dc, 0 ! src);
			var frames = (endFrame - startFrame).max(1);
			var dur = frames / BufSampleRate.ir(bufnum);
			var phase = Line.ar(startFrame, endFrame, dur, doneAction: 2);
			var sig = BufRd.ar(src, bufnum, phase, 0, 4).asArray - dc.asArray;
			var env = EnvGen.ar(Env([0, 1, 1, 0], [fadeIn, (dur - fadeIn - fadeOut).max(0.001), fadeOut], \sine));
			var stopEnv = EnvGen.kr(Env.cutoff(0.02), gate, doneAction: 2);
			SendReply.kr(Impulse.kr(25), '/sdg_editpos', A2K.kr(phase) / BufFrames.ir(bufnum));
			sig = sig * env * stopEnv * amp;
			sig = if (src > out) { sig.keep(out) } { sig.wrapExtend(out) };
			Out.ar(dryBus, sig);
		})
	}

	prEnsureDef {
		var s = this.dirt.server;
		if (s.serverRunning.not) { ^this };
		fork {
			SDGSampleEditor.previewDef(edit.numChannels, this.dirt.numChannels).add;
			s.sync;
			defReady = true;
		};
	}

	prListenForPosition {
		posFunc = OSCFunc({ |msg|
			if (previewSynth.notNil and: { msg[1] == previewSynth.nodeID }) {
				{ if (this.isOpen) { waveView.playhead = msg[3] } }.defer;
			};
		}, '/sdg_editpos', this.dirt.server.addr);
	}

	prDcArgs {
		^if (edit.removeDC) { edit.dcOffsets } { 0.0 ! edit.numChannels }
	}

	// the Buffer to play from: the library's, or one read for a file from
	// outside it and freed with the window. a library made with doNotReadYet
	// holds Buffers the server has not read: read this one first (we are
	// inside the fork play started, so the sync is fine).
	prBuffer { |action|
		var buf, lib, event;
		if (edit.inLibrary and: { sdg.notNil }) {
			lib = this.dirt.soundLibrary;
			event = lib.bufferEvents[edit.name] !? (_[edit.index]);
			if (event.notNil and: { event[\notYetRead] == true }) {
				lib.readFileIfNecessary(event);
				this.dirt.server.sync;
			};
		};
		buf = edit.buffer;
		if (buf.notNil) { ^action.value(buf) };
		if (tempBuffer.notNil and: { tempBuffer.path == edit.path }) { ^action.value(tempBuffer) };
		tempBuffer !? (_.free);
		tempBuffer = Buffer.read(this.dirt.server, edit.path, action: { |b| action.value(b) });
	}

	play {
		var s = this.dirt.server;
		if (s.serverRunning.not) { ^hints.note("the server is not running") };
		if (this.dirt.orbits.isNil or: { this.dirt.orbits.isEmpty }) { ^hints.note("no orbit to play through") };
		this.stop;
		fork {
			if (defReady.not) {
				SDGSampleEditor.previewDef(edit.numChannels, this.dirt.numChannels).add;
				s.sync;
				defReady = true;
			};
			this.prBuffer({ |buf| this.prStart(buf) });
		};
	}

	// SuperDirt's monitor (and the add-on's fader) pause themselves after 4 s
	// of silence and only a set resumes them, so the orbit is woken first or a
	// preview into a quiet orbit would go unheard. wakeOrbit sends its resume
	// bundle at the server's latency; the synth goes out at the same time so
	// the first fraction of a second is not lost.
	prStart { |buf|
		var server = this.dirt.server;
		var orbit = this.dirt.orbits[orbitIndex.clip(0, this.dirt.orbits.size - 1)];
		var group = Group.basicNew(server, orbit.group);
		var synth;
		sdg.wakeOrbit(orbit);
		server.makeBundle(server.latency, {
			synth = Synth(this.previewDefName, [
				\bufnum, buf.bufnum, \dryBus, orbit.dryBus.index,
				\startFrame, edit.startFrame, \endFrame, edit.endFrame,
				\amp, edit.gainDb.dbamp, \fadeIn, edit.fadeIn, \fadeOut, edit.fadeOut,
				\dc, this.prDcArgs
			], group, \addToHead);
		});
		previewSynth = synth;
		synth.onFree {
			if (previewSynth === synth) { previewSynth = nil };
			{ if (this.isOpen) { waveView.playhead = nil } }.defer;
		};
	}

	stop {
		previewSynth !? { |sy|
			previewSynth = nil;
			sy.set(\gate, 0);
		};
		waveView !? { |wv| { if (this.isOpen) { wv.playhead = nil } }.defer };
	}

	// ------------------------------------------------------------ keyboard

	// answers true for a key it took and nil for the rest - to QtCollider a hook
	// answering false keeps the key from the widget as surely as true does
	prKeyDown { |char, mod, key|
		var isChar = char.notNil and: { char.isKindOf(Char) };
		switch (key,
			16r1000012, { waveView.panBy(-0.1); ^true },		// left
			16r1000014, { waveView.panBy(0.1); ^true }			// right
		);
		if (isChar.not) { ^nil };
		// the key code too: with ctrl held, Qt may hand over a control character
		// (19 for s) instead of the letter
		if (this.class.isShortcut(mod)) {
			if (char.toLower == $s or: { key == 16r53 }) { this.prSave; ^true };
			^nil
		};
		switch (char,
			$ , { if (this.isPlaying) { this.stop } { this.play }; ^true },
			$0, { waveView.zoomAll; ^true },
			$-, { waveView.zoom(0.5); ^true },
			$=, { waveView.zoom(2); ^true },
			$+, { waveView.zoom(2); ^true },
			$f, { waveView.zoomToRegion; ^true },
			$g, { gridCheck.valueAction = gridCheck.value.not; ^true }
		);
		^nil
	}

	// ------------------------------------------------------------ lifecycle

	prClosed {
		this.stop;
		analysisRoutine !? (_.stop);
		analysisRoutine = nil;
		SDGSampleFeatures.breathing = false;
		slicer !? (_.removeDependant(this));
		this.store !? (_.removeDependant(this));
		posFunc !? (_.free);
		posFunc = nil;
		tempBuffer !? (_.free);
		tempBuffer = nil;
		edit.removeDependant(this);
		all.remove(this);
		try { windowBounds = window.bounds };
	}
}
