// =====================================================================
// SDGSettingsWindow - SuperDirtGUI's settings: settings in the control
// window's top bar, or
//
//   ~sdg.settings;
//
// One at a time. Two sections: orbit sleep (SDGSleep): whether an orbit
// pauses when silent, and after how many seconds; the sample editor: the
// longest file it opens (sdg.editorMaxMinutes); SuperDirt's warning for
// events past the last orbit (sdg.warnOutOfOrbit). Every change is kept at once
// in SDGSettings, for the next run too.
// =====================================================================

SDGSettingsWindow {
	classvar <current, <>windowBounds;

	var <sdg, <window, <hints;
	var sleepCheck, secondsBox, secondsLabel, noteText, font;
	var editorBox, editorNote, orbitWarnCheck;

	*new { |sdg|
		if(current.notNil and: { current.isOpen }) { current.front; ^current };
		current = super.newCopyArgs(sdg).init;
		^current
	}

	init {
		font = Font(Font.defaultSansFace, 11);
		this.prBuild;
		sdg.sleep.addDependant(this);
		sdg.addDependant(this);
		this.refresh;
		window.front;
	}

	isOpen { ^window.notNil and: { window.isClosed.not } }
	front { if(this.isOpen) { window.front } }
	close { if(this.isOpen) { window.close } }

	prBuild {
		var bounds = windowBounds ?? { Rect(220, 220, 480, 330) };
		var heading, after, editorHeading, postHeading, editorRange = SuperDirtGUI.editorMaxMinutesRange;
		window = Window("SuperDirtGUI settings", bounds);
		if(GUI.id == \qt) { window.view.palette = QPalette.dark };
		window.view.background = SDGWindow.colBg;
		hints = SDGHintBar("settings of SuperDirtGUI on this computer - kept for the next run");

		heading = StaticText().string_("orbit sleep")
			.font_(Font.sansSerif(13, true)).stringColor_(SDGWindow.colAccent);
		sleepCheck = CheckBox().string_("sleep when silent").font_(font)
			.action_({ |c| sdg.sleep.enabled = c.value });
		hints.attach(sleepCheck, "on: a silent orbit pauses its effects, meter and output to save CPU (SuperDirt's way)"
			" - off: orbits never pause");
		after = StaticText().string_("after").stringColor_(SDGWindow.colText).font_(font);
		secondsBox = NumberBox().fixedWidth_(56).font_(font)
			.clipLo_(SDGSleep.graceTime).clipHi_(SDGSleep.maxSeconds)
			.decimals_(0).step_(1).scroll_step_(1)
			.action_({ |b|
				var v = b.value, cur = sdg.sleep.seconds;
				// 5 to 7 cannot be done: a step up from 4 goes to 8, down from 8 to 4
				if(v > 4 and: { v < 8 } and: { v != cur }) { v = if(v > cur) { 8 } { 4 } };
				sdg.sleep.seconds = v;
				this.refresh
			});
		hints.attach(secondsBox, "seconds of silence before an orbit sleeps - 4 is SuperDirt's own and the shortest,"
			" then 8 and up (5 to 7 cannot be done: they become 4 or 8)");
		secondsLabel = StaticText().string_("seconds of silence").stringColor_(SDGWindow.colText).font_(font);
		noteText = StaticText().stringColor_(SDGWindow.colDim).font_(font).minHeight_(48);

		editorHeading = StaticText().string_("sample editor")
			.font_(Font.sansSerif(13, true)).stringColor_(SDGWindow.colAccent);
		editorBox = NumberBox().fixedWidth_(56).font_(font)
			.clipLo_(editorRange[0]).clipHi_(editorRange[1])
			.decimals_(1).step_(0.5).scroll_step_(0.5)
			.action_({ |b| sdg.editorMaxMinutes = b.value });
		hints.attach(editorBox, "the longest file the sample editor opens, in minutes of stereo at 44.1 kHz"
			" (mono: twice as long) - opening reads it all, about 1 s a minute, and nothing plays meanwhile");
		editorNote = StaticText().stringColor_(SDGWindow.colDim).font_(font).minHeight_(32);

		postHeading = StaticText().string_("post window")
			.font_(Font.sansSerif(13, true)).stringColor_(SDGWindow.colAccent);
		orbitWarnCheck = CheckBox().string_("warn about events past the last orbit").font_(font)
			.action_({ |c| sdg.warnOutOfOrbit = c.value });
		hints.attach(orbitWarnCheck, "on: SuperDirt posts 'event falls out of existing orbits' for every event"
			" sent to an orbit it does not have (# orbit 8 with eight orbits) - off: those events are dropped quietly");

		window.layout = VLayout(
			heading,
			HLayout(sleepCheck, 16, after, secondsBox, secondsLabel, nil).spacing_(6),
			noteText,
			editorHeading,
			HLayout(StaticText().string_("open files up to").stringColor_(SDGWindow.colText).font_(font),
				editorBox,
				StaticText().string_("minutes (stereo, 44.1 kHz)").stringColor_(SDGWindow.colText).font_(font).minWidth_(190),
				nil).spacing_(6),
			editorNote,
			postHeading,
			orbitWarnCheck,
			nil,
			hints.view
		).margins_(10).spacing_(8);
		hints.watch(window);
		window.onClose = { this.prClosed };
	}

	update { |who, what|
		if([\sleep, \editorMaxMinutes, \warnOutOfOrbit].includes(what)) { { this.refresh }.defer }
	}

	refresh {
		var sleep = sdg.sleep, on;
		if(this.isOpen.not) { ^this };
		on = sleep.enabled;
		sleepCheck.value = on;
		secondsBox.value = sleep.seconds;
		secondsBox.enabled = on;
		[secondsLabel].do { |t| t.stringColor = if(on) { SDGWindow.colText } { SDGWindow.colDim } };
		noteText.string = sleep.describe;
		editorBox.value = sdg.editorMaxMinutes;
		orbitWarnCheck.value = sdg.warnOutOfOrbit;
		editorNote.string = if(\SampleEdit.asClass.isNil) {
			"The sample editor is not installed (the sc-sample-editor quark)."
		} {
			"A file this long takes about % s to open, and SuperDirt plays nothing meanwhile. "
			"At most % minutes: sclang cannot hold much more than that in one piece.".format(
				(sdg.editorMaxMinutes * 60 * 44100 * 2 * 0.18e-6).round(1).asInteger,
				SuperDirtGUI.editorMaxMinutesRange[1])
		};
	}

	prClosed {
		sdg.sleep.removeDependant(this);
		sdg.removeDependant(this);
		try { windowBounds = window.bounds };
		if(current === this) { current = nil };
	}
}
