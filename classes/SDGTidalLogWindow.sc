// =====================================================================
// SDGTidalLogWindow - the window of an SDGTidalLog: what Tidal sends to
// SuperDirt, a line an event, newest at the top
//
//   ~sdg.tidalLog.gui;      // or from tidal... in the control window
//
// It listens while it is open. pause holds the lines still (events still
// play), the orbit menu and the filter narrow them, timing adds cps and
// delta. The list is drawn again at most ten times a second, so a busy
// pattern costs the language little.
// =====================================================================

SDGTidalLogWindow {
	classvar <>windowBounds;
	classvar <>maxLines = 300;	// drawn; the log keeps more

	var <log, <window, <hints;
	var textView, pauseBtn, orbitMenu, filterField, timingCheck, countText;
	var refreshPending = false, font, monoFont;

	*new { |log| ^super.newCopyArgs(log).init }

	init {
		font = Font(Font.defaultSansFace, 11);
		monoFont = Font(Font.defaultMonoFace, 11);
		this.prBuild;
		log.addDependant(this);
		CmdPeriod.add(this);
		log.start;
		this.refresh;
		window.front;
	}

	isOpen { ^window.notNil and: { window.isClosed.not } }
	front { if (this.isOpen) { window.front } }
	close { if (this.isOpen) { window.close } }

	prBuild {
		var bounds = windowBounds ?? { Rect(180, 180, 820, 460) };
		var orbits = log.dirt.orbits ? [];
		var clearBtn, copyBtn, header;
		window = Window("SuperDirtGUI from Tidal", bounds);
		if (GUI.id == \qt) { window.view.palette = QPalette.dark };
		window.view.background = SDGWindow.colBg;
		hints = SDGHintBar("every event Tidal sends to SuperDirt, newest at the top");

		pauseBtn = Button().fixedWidth_(60)
			.states_([["pause", SDGWindow.colText, Color.grey(0.26)], ["paused", Color.black, SDGWindow.colAccent]])
			.action_({ |b| log.paused = b.value == 1 });
		hints.attach(pauseBtn, "hold the list still to read it - the events still play (space)");
		clearBtn = Button().fixedWidth_(50).states_([["clear", SDGWindow.colText, Color.grey(0.26)]])
			.action_({ log.clear });
		hints.attach(clearBtn, "forget every line so far");
		copyBtn = Button().fixedWidth_(50).states_([["post", SDGWindow.colText, Color.grey(0.26)]])
			.action_({ log.lines.reverseDo(_.postln); hints.note("the lines are in the post window, oldest first") });
		hints.attach(copyBtn, "write the lines shown into the post window, oldest first");
		orbitMenu = PopUpMenu().fixedWidth_(84).font_(font)
			.items_(["all orbits"] ++ orbits.collect { |o, i| "orbit %".format(i) })
			.action_({ |m| log.orbit = if (m.value == 0) { nil } { m.value - 1 } });
		hints.attach(orbitMenu, "show one orbit only - d1 is orbit 0 unless the pattern says # orbit");
		filterField = TextField().font_(font).fixedWidth_(160);
		filterField.action = { |f| log.filter = f.string };
		filterField.keyUpAction = { |f| log.filter = f.string; nil };
		hints.attach(filterField, "only the lines that hold this text: a sound (bd), a pattern (d2), a parameter (speed)");
		timingCheck = CheckBox().string_("timing").font_(font).action_({ |c| log.showTiming = c.value });
		hints.attach(timingCheck, "also show cps and delta, which Tidal sends with every event");
		countText = StaticText().stringColor_(SDGWindow.colDim).font_(font);

		header = StaticText().font_(monoFont).stringColor_(SDGWindow.colDim)
			.string_("%  %  %  %  parameters".format("pat".padRight(5), "orb", "cycle".padLeft(9), "sound".padRight(14)));
		textView = TextView().font_(monoFont).editable_(false).hasVerticalScroller_(true);
		textView.background = Color.grey(0.12);
		textView.stringColor = SDGWindow.colText;
		hints.attach(textView, "one line an event: pattern, orbit, cycle, s:n, then every other parameter as Tidal sent it");

		window.layout = VLayout(
			HLayout(pauseBtn, clearBtn, copyBtn, 12, orbitMenu, StaticText().string_("filter").stringColor_(SDGWindow.colDim).font_(font),
				filterField, timingCheck, nil, countText).spacing_(4),
			header,
			[textView, stretch: 1],
			hints.view
		).margins_(8).spacing_(6);
		hints.watch(window);
		window.view.keyDownAction = { |v, char|
			if (char == $ and: { filterField.hasFocus.not }) { log.paused = log.paused.not; true }
		};
		window.onClose = { this.prClosed };
	}

	update { |who, what|
		if (#[\event, \settings, \listening].includes(what)) { this.prScheduleRefresh };
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
		{ refreshPending = false; if (this.isOpen) { this.refresh } }.defer(0.1);
	}

	refresh {
		var lines;
		if (this.isOpen.not) { ^this };
		lines = log.lines;
		pauseBtn.value = if (log.paused) { 1 } { 0 };
		orbitMenu.value = log.orbit !? { |i| (i + 1).min(orbitMenu.items.size - 1) } ? 0;
		timingCheck.value = log.showTiming;
		countText.string = case
		{ log.isListening.not } { "not listening: SuperDirt has no port yet" }
		{ log.count == 0 } { "waiting for Tidal on port %".format(log.dirt.port) }
		{ "% event%".format(log.count, if (log.count == 1) { "" } { "s" }) };
		textView.string = lines.keep(maxLines).join("\n");
	}

	prClosed {
		CmdPeriod.remove(this);
		log.removeDependant(this);
		log.stop;
		try { windowBounds = window.bounds };
	}
}
