// =====================================================================
// SDGSyncWindow - the window of an SDGSync: the samples, what happens to
// each to put it on one tempo and in one key, and the Tidal code that does
// it, to copy into a Tidal editor or to hear here first
//
//   ~sdg.sync.gui;          // or sync... in the explorer, or y on a sample
//
// The explorer adds samples (add to sync, or y); the list shows, for each,
// its bpm, how many beats it holds, the loopAt number, how far the tempo
// moves its pitch, its key and where it goes, the speed that takes it
// there and the chop. Everything is worked out again whenever a setting,
// a sample or the analysis changes.
// =====================================================================

SDGSyncWindow {
	classvar <>windowBounds;

	var <sync, <window, <hints, <list;
	var codeView, bpmBox, bpcBox, tempoCheck, keyCheck, keyMenu, chopMenu, offsetCheck, orbitMenu, playBtn, summary;
	var refreshPending = false, font, smallFont, monoFont, <code;

	*new { |sync| ^super.newCopyArgs(sync).init }

	// what the key menu offers after auto: the twelve major keys, then the minor ones
	*keyChoices { ^SDGSampleTags.noteNames ++ SDGSampleTags.noteNames.collect { |n| n ++ "m" } }

	*chopLabels {
		^SDGSync.chopChoices.collect { |n|
			switch(n, 0, { "no chop" }, 1, { "chop a beat" }, { "chop 1/%".format(n * 4) })
		}
	}

	init {
		font = Font(Font.defaultSansFace, 11);
		smallFont = Font(Font.defaultSansFace, 9);
		monoFont = Font(Font.defaultMonoFace, 11);
		this.prBuild;
		sync.addDependant(this);
		sync.tags.addDependant(this);
		CmdPeriod.add(this);
		this.refresh;
		window.front;
	}

	isOpen { ^window.notNil and: { window.isClosed.not } }
	front { if (this.isOpen) { window.front } }
	close { if (this.isOpen) { window.close } }

	// ---------------------------------------------------------------- build

	prBuild {
		var bounds = windowBounds ?? { Rect(160, 160, 940, 620) };
		var label = { |str| StaticText().string_(str).stringColor_(SDGWindow.colDim).font_(font) };
		var button = { |str, width, func, hint|
			var b = Button().fixedWidth_(width).states_([[str, SDGWindow.colText, Color.grey(0.26)]]).action_({ func.value });
			hints.attach(b, hint);
			b
		};
		var check = { |str, func, hint|
			var c = CheckBox().string_(str).font_(font).action_({ |v| func.(v.value) });
			hints.attach(c, hint);
			c
		};
		var addBtn, bpmBtn, removeBtn, upBtn, downBtn, clearBtn, autoBtn, halfBtn, doubleBtn, copyBtn, postBtn;
		window = Window("SuperDirtGUI sync", bounds);
		if (GUI.id == \qt) { window.view.palette = QPalette.dark };
		window.view.background = SDGWindow.colBg;
		hints = SDGHintBar("add long samples from the explorer (y, or add to sync), then copy the code into Tidal");

		bpmBox = hints.attach(NumberBox().decimals_(2).clipLo_(20).clipHi_(400).fixedWidth_(64).font_(font),
			"the tempo they all play at, bpm - auto takes the first sample's");
		bpmBox.action = { |b| sync.bpm = b.value };
		autoBtn = button.("auto", 40, { sync.bpm = nil }, "the tempo of the first sample with one");
		halfBtn = button.("÷2", 28, { sync.bpm = sync.targetBpm / 2 }, "half the tempo");
		doubleBtn = button.("×2", 28, { sync.bpm = sync.targetBpm * 2 }, "twice the tempo");
		bpcBox = hints.attach(NumberBox().decimals_(0).clipLo_(1).clipHi_(16).step_(1).scroll_step_(1).fixedWidth_(34).font_(font),
			"how many beats a Tidal cycle holds: 4 is a bar of 4/4, what setcps (bpm/60/4) means");
		bpcBox.action = { |b| sync.beatsPerCycle = b.value };
		tempoCheck = check.("same tempo", { |v| sync.matchTempo = v },
			"stretch every loop with a bpm to whole beats of the tempo; off, each is stretched to the nearest whole cycles");
		keyCheck = check.("same key", { |v| sync.matchKey = v },
			"move every sample with a key or a note into one key, with a speed on top of the stretch");
		keyMenu = hints.attach(PopUpMenu().items_(["auto"] ++ this.class.keyChoices).fixedWidth_(64).font_(font),
			"the key: auto is the first sample with a key, as the tempo leaves it - so it needs the least speed");
		keyMenu.action = { |m| sync.key = if (m.value == 0) { nil } { this.class.keyChoices[m.value - 1] } };
		chopMenu = hints.attach(PopUpMenu().items_(this.class.chopLabels).fixedWidth_(96).font_(font),
			"how many pieces chop cuts a beat into: each piece starts on the grid, so a sample moved by speed stays in time");
		chopMenu.action = { |m| sync.chopsPerBeat = SDGSync.chopChoices[m.value] };
		offsetCheck = check.("first beat", { |v| sync.useOffsets = v },
			"start each loop on its first beat, as the analysis or the editor's grid has it, with <~");

		list = SDGExplorerView(nil, Rect(0, 0, 900, 200));
		list.columns = [
			(key: \name, label: "sample"),
			(key: \bpm, label: "bpm", width: 54, align: \right),
			(key: \beats, label: "beats", width: 42, align: \right),
			(key: \cycles, label: "loopAt", width: 50, align: \right),
			(key: \tempo, label: "tempo st", width: 60, align: \right),
			(key: \key, label: "key", width: 44, align: \right),
			(key: \to, label: "to", width: 40, align: \right),
			(key: \speed, label: "speed", width: 56, align: \right),
			(key: \chop, label: "chop", width: 40, align: \right),
			(key: \notes, label: "look out for")
		];
		list.keyAction = { |char, mod, key|
			case
			{ key == 16r01000003 or: { key == 16r01000007 } } { this.removeSelected; true }
			{ char == $  } { this.togglePlay; true }
			{ char == $b } { this.editBpm; true }
			{ nil }
		};
		list.hintAction = { |key, inHeader|
			hints.string = switch(key,
				\bpm, { "the sample's own tempo - a ? when the analysis is not sure; b sets it by hand" },
				\beats, { "how many beats the file holds at its bpm, counted in half or double time when that is nearer the tempo" },
				\cycles, { "loopAt's number: the cycles the whole file is stretched over" },
				\tempo, { "how far the stretch alone moves its pitch, semitones" },
				\key, { "its key, or the note of a one-shot - a ? when the analysis is not sure" },
				\to, { "the key it ends in" },
				\speed, { "the speed inside the loopAt: the rest of the way into the key, and a loop cut to whole beats read at the tempo" },
				\chop, { "how many pieces chop cuts the whole file into" },
				{ "the samples, in the order of the code: d1 first - delete takes one out, b sets a bpm by hand" }
			)
		};
		addBtn = button.("add selected", 90, { this.addFromExplorer }, "add the sample selected in the explorer, or every one of a selected bank");
		bpmBtn = button.("bpm...", 52, { this.editBpm }, "set the selected sample's bpm by hand, or from how many beats it holds (b)");
		removeBtn = button.("remove", 60, { this.removeSelected }, "take the selected sample out (delete)");
		upBtn = button.("up", 36, { list.selectedIndex !? { |i| sync.move(i, i - 1); list.select(i - 1) } }, "move it up: an earlier d");
		downBtn = button.("down", 44, { list.selectedIndex !? { |i| sync.move(i, i + 1); list.select(i + 1) } }, "move it down");
		clearBtn = button.("clear", 44, { sync.clear }, "take every sample out");
		orbitMenu = hints.attach(PopUpMenu().items_((sync.dirt.orbits ? []).collect { |o, i| "orbit %".format(i) }).fixedWidth_(76).font_(font),
			"the orbit the preview plays into");
		orbitMenu.action = { |m| sync.orbit = m.value };
		playBtn = Button().fixedWidth_(60).states_([["play", SDGWindow.colText, Color.grey(0.26)], ["stop", Color.black, SDGWindow.colAccent]])
			.action_({ |b| if (b.value == 1) { sync.play } { sync.stop } });
		hints.attach(playBtn, "hear them together here, as the code would play them from Tidal (space)");

		summary = StaticText().stringColor_(SDGWindow.colDim).font_(smallFont);
		codeView = TextView().font_(monoFont).editable_(false).hasVerticalScroller_(true);
		codeView.background = Color.grey(0.12);
		codeView.stringColor = SDGWindow.colText;
		hints.attach(codeView, "the Tidal code: setcps, then one d a sample - copy it, or post it to the post window");
		copyBtn = button.("copy code", 76, { if (sync.copy) { hints.note("the code is on the clipboard") } { this.code.postln; hints.note("no clipboard here: the code is in the post window") } },
			"put the code on the clipboard");
		postBtn = button.("post", 44, { this.code.postln; hints.note("the code is in the post window") }, "write the code in the post window");

		window.layout = VLayout(
			HLayout(label.("tempo"), bpmBox, autoBtn, halfBtn, doubleBtn, 12, label.("beats a cycle"), bpcBox, 12,
				tempoCheck, keyCheck, keyMenu, chopMenu, offsetCheck, nil).spacing_(4),
			[list.view, stretch: 2],
			HLayout(addBtn, bpmBtn, removeBtn, upBtn, downBtn, clearBtn, nil, orbitMenu, playBtn).spacing_(4),
			summary,
			[codeView, stretch: 3],
			HLayout(nil, postBtn, copyBtn).spacing_(4),
			hints.view
		).margins_(8).spacing_(6);
		window.view.keyDownAction = { |v, char| if (char == $ ) { this.togglePlay; true } };
		window.onClose = { this.prClosed };
	}

	// --------------------------------------------------------------- acting

	addFromExplorer {
		var win = sync.sdg.controlWindow;
		if (win.isNil) { ^hints.note("open the SuperDirtGUI window and select a sample there") };
		if (win.addSelectedToSync.not) { hints.note("select a sample or a bank in the explorer first") };
	}

	removeSelected {
		list.selectedIndex !? { |i| sync.removeAt(i); list.select(i.min(sync.items.size - 1).max(0)) };
	}

	togglePlay { if (sync.isPlaying) { sync.stop } { sync.play } }

	// the selected sample's bpm by hand, in SDGWindow's bpm dialog
	editBpm {
		var it = list.selectedIndex !? { |i| sync.items[i] }, buf;
		if (it.isNil) { ^hints.note("select a sample to set its bpm") };
		buf = sync.sdg.bufferAt(it[\bank], it[\index]);
		if (buf.isNil or: { buf.path.isNil }) { ^hints.note("that sample is not loaded") };
		^SDGWindow.bpmDialog(sync.sdg, [[buf.path, buf.numFrames / buf.sampleRate.max(1)]], nil,
			Point(window.bounds.left + 200, window.bounds.top + 200))
	}

	// ------------------------------------------------------------- refresh

	update { |who, what|
		if (#[\sync, \playing, \tags, \analysis].includes(what)) { this.prScheduleRefresh };
	}

	// a cmd-period has cleared AppClock, and with it a refresh on its way: the
	// flag is dropped, or nothing would ever be drawn again
	cmdPeriod {
		refreshPending = false;
		{ if (this.isOpen) { this.refresh } }.defer;
	}

	prScheduleRefresh {
		if (refreshPending) { ^this };
		refreshPending = true;
		{ refreshPending = false; if (this.isOpen) { this.refresh } }.defer(0.15);
	}

	refresh {
		var rows = sync.plan, n = SDGSync, t = sync.targetBpm, target = rows.first !? { |r| r[\target] };
		var sel = list.selectedIndex;
		if (this.isOpen.not) { ^this };
		bpmBox.value = t;
		bpmBox.stringColor = if (sync.bpm.isNil) { SDGWindow.colDim } { SDGWindow.colText };
		bpcBox.value = sync.beatsPerCycle;
		tempoCheck.value = sync.matchTempo;
		keyCheck.value = sync.matchKey;
		keyMenu.value = sync.key !? { |k|
			var p = SDGSampleTags.parseKey(k);
			p !? { 1 + p[\tonic] + if (p[\mode] == \minor) { 12 } { 0 } }
		} ? 0;
		keyMenu.enabled = sync.matchKey;
		chopMenu.value = SDGSync.chopChoices.indexOf(sync.chopsPerBeat) ? 2;
		offsetCheck.value = sync.useOffsets;
		orbitMenu.value = sync.orbit.min(orbitMenu.items.size - 1).max(0);
		playBtn.value = if (sync.isPlaying) { 1 } { 0 };
		list.rows = rows.collect { |r|
			(level: 0, color: if (r[\path].isNil) { SDGWindow.colWarn },
				cells: [
					r[\label],
					r[\bpm] !? { |b| n.num(b, 1) ++ if (r[\bpmSure]) { "" } { "?" } } ? if (r[\analysed]) { "-" } { "…" },
					n.num(r[\beats], 2),
					n.num(r[\cycles], 4),
					n.signed(r[\tempoSt] ? 0, 1),
					r[\keyFrom] !? { |k| k ++ if (r[\info][\keySure] == true) { "" } { "?" } } ? "-",
					r[\keyTo] ? "",
					(r[\speed] * (r[\trim] ? 1)) !? { |x| if ((x - 1).abs > 0.0003) { "x" ++ n.num(x, 4) } { "" } },
					r[\chop] !? (_.asString) ? "",
					r[\notes].join(", ")
				])
		};
		sel !? { if (sel < rows.size) { list.select(sel, false) } };
		summary.string = if (rows.isEmpty) { "no samples yet" } {
			"% sample%, % bpm (cps %), % beats a cycle%".format(rows.size, if (rows.size == 1) { "" } { "s" },
				n.num(t, 2), n.num(sync.cps, 4), sync.beatsPerCycle,
				target !? { ", in " ++ SDGSync.keyName(target[\tonic], target[\mode]) } ? if (sync.matchKey) { ", no sample with a key" } { "" })
		};
		code = sync.code;
		codeView.string = code;
	}

	prClosed {
		CmdPeriod.remove(this);
		sync.removeDependant(this);
		sync.tags.removeDependant(this);
		sync.stop;
		try { windowBounds = window.bounds };	// gone already when the window closed itself
	}
}
