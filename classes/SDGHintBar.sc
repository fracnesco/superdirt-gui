// =====================================================================
// SDGHintBar - a strip along the bottom that says what the mouse is over
//
// The same idea as the one in the OneShot / LongSample windows
// (ModuleHintBar) and the sample slicer, written out here so SuperDirtGUI
// keeps depending on nothing of theirs: a window full of small controls
// can be read without a manual.
//
//   hints = SDGHintBar("hover over a control and this bar says what it does");
//   hints.watch(window);                     // once, after the window exists
//   window.layout = VLayout(..., hints.view);
//
//   hints.attach(box, "what this box does");
//   hints.attach([label, slider, box], "the three parts of one control");
//   hints.attach(readout, { "now at " ++ thing.value });   // worked out on hover
//
// `attach` answers what it was given, so a control can be wrapped where it
// is built:
//
//   startBox = hints.attach(NumberBox(), "where the trim starts");
//
// A hint is a String, or a Function answering one. Moving onto nothing in
// particular puts the bar back to its default, and `note` changes that
// default - which is how the last thing that happened stays readable when
// the pointer is somewhere uninteresting.
// =====================================================================

SDGHintBar {
	classvar <>defaultHeight = 22;

	var <view, <text, <>defaultString;
	var toggleBox, font;

	*new { |defaultString, height| ^super.new.init(defaultString, height) }

	init { |str, height|
		defaultString = str ?? { "hover over a control and this bar says what it does" };
		font = Font(Font.defaultSansFace, 10);
		text = StaticText().font_(font).stringColor_(SDGWindow.colDim).string_(defaultString);
		view = View().background_(SDGWindow.colWaveBg).fixedHeight_(height ? defaultHeight);
		view.layout = HLayout(text).margins_([8, 1, 8, 1]);
		^this
	}

	// A child's mouse-over only arrives if the window is willing to hear
	// about it. Moving onto the window itself - the gaps between controls -
	// puts the bar back to its default, so a stale hint does not sit there
	// describing something the pointer left a while ago.
	watch { |window|
		var v = window.tryPerform(\view) ? window;
		v !? {
			v.acceptsMouseOver = true;
			v.mouseOverAction = { this.clear };
		};
		^this
	}

	// `hint` is a String, or a Function answering one. Answers `views`.
	attach { |views, hint|
		views.asArray.do { |v|
			v !? {
				v.acceptsMouseOver = true;
				v.mouseOverAction = { this.string_(hint.value) };
			};
		};
		^views
	}

	// what the bar falls back to when the pointer is over nothing: the last
	// thing the window said it did
	note { |str|
		str !? { defaultString = str.asString };
		this.string_(str);
	}

	string_ { |str| text !? { text.string = " " ++ (str ? defaultString) } }

	string { ^text !? (_.string) }

	clear { this.string_(nil) }

	visible { ^view.visible }

	visible_ { |bool|
		view.visible = bool;
		toggleBox !? { |b| b.value = bool };
	}

	// a checkbox that folds the bar away; made on the first ask, the same
	// one after that
	toggle {
		toggleBox ?? {
			toggleBox = CheckBox().string_("hints").value_(view.visible)
				.action_({ |v| view.visible = v.value });
			this.attach(toggleBox, "this bar - untick to fold it away");
		};
		^toggleBox
	}
}
