// =====================================================================
// SDGMidiWindow - everything MIDI of an SDGMidi: controllers, learn, mappings
//
//   ~sdg.midi.gui;                     // or midi in the control window
//   ~sdg.midi.gui.select([\fader, 0]); // with one mapping on show
//
// At the top, the controllers: find runs Modality's MKtl.find (midi, or midi
// and hid) and lists every device seen, with its description and whether it is
// connected; pick one, give it a name, connect - SuperDirtGUI listens to it from
// then on, and a session saves it. forget lets one go with its mappings.
// Controllers a session named that are not here now are listed too.
//
// midi learn switches the control window's learn mode: click a fader, knob,
// mute, solo or send there, move a control, and so on until it is off.
// With follow controller on, moving a mapped control selects its mapping: the
// list row, the range and the curve.
//
// The list has every mapping: controller, element, what it moves, its range,
// curve and takeover. Under it the selected one: lo and hi in the target's own
// units, invert, takeover, the button mode, and its curve - a preset, a bend,
// or drawn by hand. The curve is an EnvelopeView: drag its points, and a preset
// becomes a drawn curve; + point and - point add and take away, bend curves
// the segment after the selected point. The readout follows the controller.
//
// learn maps the next element moved to the selected target, remove takes the
// mapping away, + tidal learns a Tidal control (cF) by name. code... opens all
// the mappings as code, which is what a session saves; from session... puts
// another session's mappings in the place of these.
// =====================================================================

SDGMidiWindow {
	classvar <>windowBounds;
	classvar <menuCurves;		// the curve menu: its items, and the curve each one stands for

	var <midi, <window, <hints;
	var listView, <selectedAddress, <shown;	// the address on show, and the maps in the list's order
	var titleText, ktlText, readout;
	var loKnob, loBox, hiKnob, hiBox, loDb, hiDb, invertCheck, takeoverMenu, modeMenu;
	var curveMenu, bendBox, envView, addPointBtn, removePointBtn;
	var learnBtn, removeBtn, codeBtn, sessionBtn, tidalField, tidalBtn, menu;
	var <deviceRows, deviceList, deviceKey, findBtn, protoMenu, nameField, descMenu, connectBtn, forgetBtn;
	var learnModeBtn, followCheck, <>follow = true;	// follow: a mapping moved on the controller comes on show
	var refreshPending = false, readoutPending = false, editing = false, filling = false;
	var font, smallFont, monoFont;

	*initClass {
		menuCurves = [
			["linear", \lin],
			["exp - slow, then fast", \exp],
			["log - fast, then slow", \log],
			["s-curve - slow at both ends", \scurve],
			["bend", \bend],
			["drawn", \drawn]
		];
	}

	*new { |midi| ^super.newCopyArgs(midi).init }

	init {
		font = Font(Font.defaultSansFace, 11);
		smallFont = Font(Font.defaultSansFace, 9);
		monoFont = Font(Font.defaultMonoFace, 11);
		this.prBuild;
		midi.addDependant(this);
		midi.sdg.addDependant(this);
		CmdPeriod.add(this);
		this.refresh;
		window.front;
	}

	isOpen { ^window.notNil and: { window.isClosed.not } }
	front { if (this.isOpen) { window.front } }
	close { if (this.isOpen) { window.close } }

	// the mapping of this target on show, when there is one. From any thread: the
	// window follows on AppClock
	select { |address|
		selectedAddress = address !? { SDGMidiTarget(midi.sdg, address).address };
		this.prScheduleRefresh;
	}

	selectedMap { ^selectedAddress !? { |a| midi.mapFor(a) } }

	// ------------------------------------------------------------------ building

	prBuild {
		var bounds = windowBounds ?? { Rect(220, 80, 660, 800) };
		var colText = SDGWindow.colText, colDim = SDGWindow.colDim, grey = Color.grey(0.26);
		var label = { |str| StaticText().string_(str).stringColor_(colDim).font_(font) };
		var addPointRow, heading = { |str| StaticText().string_(str).stringColor_(colText).font_(Font(Font.defaultSansFace, 12, true)) };
		window = Window("SuperDirtGUI midi", bounds);
		if (GUI.id == \qt) { window.view.palette = QPalette.dark };
		window.view.background = SDGWindow.colBg;
		hints = SDGHintBar("find a controller, connect it, switch midi learn on and click what it should move");

		// the controllers
		protoMenu = PopUpMenu().font_(font).fixedWidth_(100).items_(["midi", "midi + hid"]);
		hints.attach(protoMenu, "what find looks for: MIDI ports, or game controllers and other HID devices too");
		findBtn = Button().font_(font).fixedWidth_(60).states_([["find", colText, grey]])
			.action_({ this.find });
		hints.attach(findBtn, "MKtl.find: every device plugged in now, with Modality's description of it (the post window has the same)");
		deviceList = ListView().font_(monoFont).fixedHeight_(110).background_(Color.grey(0.12))
			.stringColor_(colText).selectedStringColor_(Color.black).hiliteColor_(SDGWindow.colAccent);
		deviceList.action = { |v|
			if (filling.not) { deviceRows !? { |rows| v.value !? { |i| rows[i] } !? { |row| this.prChooseDevice(row) } } }
		};
		hints.attach(deviceList, "the devices find saw, and the controllers the mappings use - pick one to connect or forget it");
		nameField = TextField().font_(font).fixedWidth_(100);
		hints.attach(nameField, "the controller's name, as the mappings and the session say it: MKtl(\\name)");
		descMenu = PopUpMenu().font_(font).minWidth_(160);
		hints.attach(descMenu, "Modality's description of the device: which control is which");
		connectBtn = Button().font_(font).fixedWidth_(70).states_([["connect", colText, grey]])
			.action_({ this.prConnect });
		hints.attach(connectBtn, "listen to this controller: midi learn and the mappings can use it, and a session keeps it");
		forgetBtn = Button().font_(font).fixedWidth_(60).states_([["forget", SDGWindow.colWarn, grey]])
			.action_({ this.prForget });
		hints.attach(forgetBtn, "stop listening to this controller - its mappings go too");

		learnModeBtn = Button().font_(font).fixedWidth_(110).states_([
			["midi learn", colText, grey], ["midi learn: on", Color.black, SDGWindow.colAccent]
		]).action_({ |b| this.prLearnMode(b.value == 1) });
		followCheck = CheckBox().string_("follow controller").font_(font).value_(follow)
			.action_({ |c| follow = c.value });
		hints.attach(followCheck, "move a mapped control on the controller and its mapping is selected here - off to keep one on show while you play");
		hints.attach(learnModeBtn, "click a fader, knob, mute, solo or send in the control window, then move a control on the controller - again and again until it is off");

		ktlText = StaticText().stringColor_(colDim).font_(font);
		hints.attach(ktlText, "the controllers the mappings use: connected, virtual (made without a device) or missing");
		listView = ListView().font_(monoFont).background_(Color.grey(0.12))
			.stringColor_(colText).selectedStringColor_(Color.black).hiliteColor_(SDGWindow.colAccent);
		// filling the list may move its current row: only a person's choice selects
		listView.action = { |v|
			if (filling.not) { shown !? { |list| v.value !? { |i| list[i] } !? { |m|
				if (m.target.address != selectedAddress) { this.select(m.target.address) }
			} } }
		};
		hints.attach(listView, "controller, element, what it moves, range, curve and takeover - (waiting) when what it moves is not there now");

		titleText = StaticText().font_(Font(Font.defaultSansFace, 12, true)).stringColor_(SDGWindow.colAccent);

		// the range, in the target's own units
		loKnob = SDGWindow.makeKnob(32);
		hiKnob = SDGWindow.makeKnob(32);
		loBox = NumberBox().decimals_(3).font_(font).fixedWidth_(70).normalColor_(colText);
		hiBox = NumberBox().decimals_(3).font_(font).fixedWidth_(70).normalColor_(colText);
		loDb = StaticText().stringColor_(colDim).font_(smallFont).fixedWidth_(56);
		hiDb = StaticText().stringColor_(colDim).font_(smallFont).fixedWidth_(56);
		loKnob.action = { |k| this.prEdit { |m| m.lo = m.target.spec.map(k.value) } };
		hiKnob.action = { |k| this.prEdit { |m| m.hi = m.target.spec.map(k.value) } };
		loBox.action = { |b| this.prEdit { |m| m.lo = b.value } };
		hiBox.action = { |b| this.prEdit { |m| m.hi = b.value } };
		hints.attach([loKnob, loBox, loDb], "where the controller's bottom sends the target, in its own units");
		hints.attach([hiKnob, hiBox, hiDb], "where the controller's top sends it - under lo turns the control round");

		invertCheck = CheckBox().string_("invert").font_(font).action_({ |c| this.prEdit { |m| m.invert = c.value } });
		hints.attach(invertCheck, "turn the control round: its top is lo");
		takeoverMenu = PopUpMenu().font_(font).fixedWidth_(110).items_(["pickup", "jump"])
			.action_({ |p| this.prEdit { |m| m.takeover = #[\pickup, \jump][p.value] } });
		hints.attach(takeoverMenu, "pickup: nothing moves until the control reaches the target's value; jump: it goes there at once");
		modeMenu = PopUpMenu().font_(font).fixedWidth_(110).items_(["toggle", "momentary"])
			.action_({ |p| this.prEdit { |m| m.mode = #[\toggle, \momentary][p.value] } });
		hints.attach(modeMenu, "a button: toggle flips on each press, momentary holds while it is down");

		// the curve
		curveMenu = PopUpMenu().font_(font).fixedWidth_(190).items_(menuCurves.collect(_[0]));
		curveMenu.action = { |p| this.prCurveChosen(menuCurves[p.value][1]) };
		hints.attach(curveMenu, "how the control's travel is spread over the range");
		bendBox = NumberBox().decimals_(1).step_(0.5).clipLo_(-20).clipHi_(20).font_(font).fixedWidth_(56).normalColor_(colText);
		bendBox.action = { |b| this.prBend(b.value) };
		hints.attach(bendBox, "how far the curve bends: 0 straight, above 0 slow first, under 0 fast first - drawn: the segment after the selected point");
		envView = EnvelopeView().minHeight_(170).thumbSize_(9).keepHorizontalOrder_(true)
			.drawLines_(true).drawRects_(true).elasticSelection_(false)
			.background_(SDGWindow.colWaveBg).strokeColor_(SDGWindow.colWave)
			.fillColor_(SDGWindow.colAccent).selectionColor_(SDGWindow.colHandle)
			.gridOn_(true).grid_(0.25 @ 0.25).gridColor_(Color.grey(0.25));
		envView.action = { |v| this.prPointsMoved(v) };
		hints.attach(envView, "the control (left to right) against where it sends the target (bottom to top) - drag a point to draw your own");
		addPointBtn = Button().font_(font).fixedWidth_(64).states_([["+ point", colText, grey]])
			.action_({ this.prAddPoint });
		removePointBtn = Button().font_(font).fixedWidth_(64).states_([["- point", colText, grey]])
			.action_({ this.prRemovePoint });
		hints.attach(addPointBtn, "a point in the middle of the selected point's segment: the curve becomes drawn");
		hints.attach(removePointBtn, "take the selected point away - the two ends stay");
		readout = StaticText().font_(monoFont).stringColor_(colText);
		hints.attach(readout, "where the controller is, where the curve puts it, and what the target gets");

		learnBtn = Button().font_(font).fixedWidth_(60).states_([
			["learn", colText, grey], ["learning", Color.black, SDGWindow.colAccent]
		]).action_({ |b| this.prLearn(b.value == 1) });
		hints.attach(learnBtn, "map the next element moved on any controller to this target, in place of its mapping");
		removeBtn = Button().font_(font).fixedWidth_(64).states_([["remove", SDGWindow.colWarn, grey]])
			.action_({ selectedAddress !? { |a| midi.unmap(a) } });
		hints.attach(removeBtn, "take this mapping away");
		tidalField = TextField().font_(font).fixedWidth_(90);
		tidalBtn = Button().font_(font).fixedWidth_(64).states_([["+ tidal", colText, grey]])
			.action_({ this.prLearnTidal });
		hints.attach([tidalField, tidalBtn], "type a name, press + tidal, move a control: Tidal reads it as cF 0 \"name\"");
		codeBtn = Button().font_(font).fixedWidth_(60).states_([["code...", colText, grey]])
			.action_({ this.openCode });
		hints.attach(codeBtn, "every mapping as code: run the document to make them again - a session saves the same");
		sessionBtn = Button().font_(font).fixedWidth_(110).states_([["from session...", colText, grey]])
			.action_({ this.prShowSessionMenu });
		hints.attach(sessionBtn, "the mappings saved with another session, in the place of these - the mixer stays as it is");

		addPointRow = HLayout(label.("curve"), curveMenu, label.("bend"), bendBox, nil, addPointBtn, removePointBtn).spacing_(4);
		window.layout = VLayout(
			HLayout(heading.("controllers"), nil, protoMenu, findBtn).spacing_(4),
			deviceList,
			HLayout(label.("name"), nameField, label.("description"), [descMenu, stretch: 1], connectBtn, forgetBtn).spacing_(4),
			ktlText,
			8,
			HLayout(heading.("mappings"), 10, learnModeBtn, 10, followCheck, nil, codeBtn, sessionBtn).spacing_(4),
			[listView, stretch: 1],
			HLayout([titleText, stretch: 1], learnBtn, removeBtn, 12, tidalField, tidalBtn).spacing_(4),
			HLayout(
				label.("lo"), loKnob, VLayout(loBox, loDb).spacing_(0), 10,
				label.("hi"), hiKnob, VLayout(hiBox, hiDb).spacing_(0), 10,
				VLayout(invertCheck, nil),
				nil,
				VLayout(HLayout(label.("takeover"), takeoverMenu), HLayout(label.("button"), modeMenu)).spacing_(2)
			).spacing_(4),
			addPointRow,
			[envView, stretch: 2],
			readout,
			hints.view
		).margins_(8).spacing_(6);
		hints.watch(window);
		window.onClose = { this.prClosed };
	}

	// ------------------------------------------------------------------ showing

	update { |who, what ... args|
		if (who === midi) {
			switch(what,
				\moved, {
					args[0] !? { |m|
						case
						{ m.target.address == selectedAddress } { this.prScheduleReadout }
						// another mapping moved on the controller: it comes on show, list and curve
						{ follow and: { editing.not } and: { midi.armed.isNil } } { this.select(m.target.address) }
					}
				},
				\learn, {
					// a mapping learnt here or in the control window: it is the one on show
					args[0] !? { |m| selectedAddress = m.target.address };
					this.prScheduleRefresh
				},
				\map, { if (editing.not) { this.prScheduleRefresh } },
				\learnMode, { { if (this.isOpen) { this.prShowLearnMode } }.defer },
				{ this.prScheduleRefresh }
			)
		};
		// what a target is moved to some other way, for the readout
		if (who === midi.sdg and: { #[\mixer, \param, \returns, \tracks].includes(what) }) { this.prScheduleReadout };
	}

	// a cmd-period has cleared AppClock, and with it a refresh on its way: the
	// flag is dropped, or nothing would ever be drawn again
	cmdPeriod {
		refreshPending = false;
		readoutPending = false;
		{ if (this.isOpen) { this.refresh } }.defer;
	}

	prScheduleRefresh {
		if (refreshPending) { ^this };
		refreshPending = true;
		{ refreshPending = false; if (this.isOpen) { this.refresh } }.defer(0.05);
	}

	prScheduleReadout {
		if (readoutPending) { ^this };
		readoutPending = true;
		{ readoutPending = false; if (this.isOpen) { this.prShowReadout } }.defer(0.05);
	}

	refresh {
		var m, k;
		if (this.isOpen.not) { ^this };
		shown = midi.maps.copy;
		// a target stays on show with its mapping removed, to learn it again
		if (selectedAddress.isNil) { selectedAddress = shown.first !? { |x| x.target.address } };
		this.prFillList;
		ktlText.string = this.prKtlString;
		this.prFillDevices;
		this.prShowLearnMode;
		learnBtn.value = (midi.armed.notNil and: { midi.armed.address == selectedAddress }).binaryValue;
		m = this.selectedMap;
		titleText.string = case
		{ m.notNil } { "% %  ->  %%".format(m.ktl, m.element, m.target.label, if (m.isWaiting) { "  (waiting)" } { "" }) }
		{ selectedAddress.notNil } { "%  -  no mapping".format(SDGMidiTarget(midi.sdg, selectedAddress).label) }
		{ "no mapping yet: midi learn, then click something in the control window - or + tidal" };
		this.prShowMap(m);
	}

	// the rows, and the selected one current
	prFillList {
		var k = shown.detectIndex { |x| x.target.address == selectedAddress };
		filling = true;
		protect {
			listView.items = shown.collect { |x| this.class.line(x) };
			k !? { listView.value = k };
		} { filling = false };
	}

	// one row of the list
	*line { |m|
		var t = m.target, range = "% .. %".format(this.num(m.lo), this.num(m.hi));
		^"% % % % % %%".format(
			m.ktl.asString.padRight(6), m.element.asString.padRight(10),
			t.label.keep(32).padRight(32), range.padRight(14),
			this.curveName(m.curve).padRight(8), m.takeover,
			if (m.isWaiting) { "  (waiting)" } { "" })
	}

	*num { |x| ^if (x.abs >= 100) { x.round(1).asInteger.asString } { x.round(0.001).asString } }

	*curveName { |c|
		^case
		{ c.isKindOf(Env) } { "drawn" }
		{ c.isNumber } { "bend " ++ c.round(0.1) }
		{ c == \scurve } { "s-curve" }
		{ c.asString }
	}

	prKtlString {
		var names = (midi.ktls.keys.asArray ++ midi.maps.collect(_.ktl)).asSet.asArray.sort;
		if (SDGMidi.isAvailable.not) { ^"no Modality toolkit: Tidal mappings only" };
		if (names.isEmpty) { ^"no controller yet" };
		^"controllers: " ++ names.collect { |n|
			"% (%%)".format(n, midi.ktls[n] !? { |d| d ++ ", " } ? "", midi.ktlState(n))
		}.join(", ")
	}

	// the selected mapping into the widgets; setting a view's value never fires its action
	prShowMap { |m|
		var spec, isAmp, button, env;
		var views = [loKnob, loBox, hiKnob, hiBox, invertCheck, takeoverMenu, modeMenu, curveMenu, bendBox,
			envView, addPointBtn, removePointBtn, removeBtn];
		views.do { |v| v.enabled = m.notNil };
		learnBtn.enabled = selectedAddress.notNil and: { SDGMidi.isAvailable };
		if (m.isNil) { readout.string = ""; envView.setEnv(Env([0, 1], [1])); ^this };
		spec = m.target.spec;
		isAmp = this.class.isAmp(m.target);
		// a knob is only of use on a range it can cover: not Tidal's open one
		[loKnob, hiKnob].do { |k| k.visible = m.target.kind != \tidal };
		loKnob.value = spec.unmap(m.lo);
		hiKnob.value = spec.unmap(m.hi);
		loBox.value = m.lo;
		hiBox.value = m.hi;
		loDb.string = if (isAmp) { SDGWindow.faderString(m.lo) } { "" };
		hiDb.string = if (isAmp) { SDGWindow.faderString(m.hi) } { "" };
		invertCheck.value = m.invert;
		takeoverMenu.value = #[\pickup, \jump].indexOf(m.takeover) ? 0;
		modeMenu.value = #[\toggle, \momentary].indexOf(m.mode) ? 0;
		button = midi.isButton(m.ktl, m.element);
		modeMenu.enabled = button != false;
		takeoverMenu.enabled = button != true;
		curveMenu.value = menuCurves.detectIndex { |c| c[1] == this.class.menuKey(m.curve) } ? 0;
		bendBox.value = case
		{ m.curve.isNumber } { m.curve }
		{ m.curve.isKindOf(Env) } { this.prSegmentCurve(m.curve, this.prSelectedSegment(m.curve)) }
		{ #[\exp, \log].includes(m.curve) } { if (m.curve == \exp) { 4 } { -4 } }
		{ 0 };
		env = this.class.envOf(m.curve);
		envView.setEnv(env);
		removePointBtn.enabled = m.curve.isKindOf(Env) and: { env.levels.size > 2 };
		this.prShowReadout;
	}

	prShowReadout {
		var m = this.selectedMap, x, now;
		if (m.isNil) { ^this };
		x = m.lastInput;
		now = m.target.value;
		readout.string = "control %  ->  curve %  ->  %    now %".format(
			x !? { x.round(0.001).asString.padRight(5) } ? "-    ",
			x !? { m.shape(x).round(0.001).asString.padRight(5) } ? "-    ",
			x !? { this.prUnits(m, m.out(x)) } ? "-",
			now !? { this.prUnits(m, now) } ? "-")
	}

	prUnits { |m, v|
		^if (this.class.isAmp(m.target)) { SDGWindow.faderString(v) } { this.class.num(v) }
	}

	*isAmp { |target| ^#[\fader, \trackLevel, \returnLevel, \send, \groupLevel, \groupSend, \masterLevel].includes(target.kind) }

	// the curve menu's word for a curve
	*menuKey { |c|
		^case { c.isKindOf(Env) } { \drawn } { c.isNumber } { \bend } { c }
	}

	// any curve as an Env from 0 to 1 over 1: what the view draws
	*envOf { |c|
		^case
		{ c.isKindOf(Env) } { c }
		{ c.isNumber } { Env([0, 1], [1], c) }
		{ c == \exp } { Env([0, 1], [1], 4) }
		{ c == \log } { Env([0, 1], [1], -4) }
		{ c == \scurve } { Env([0, 1], [1], \sine) }
		{ Env([0, 1], [1], \lin) }
	}

	// an Env's curves, one per segment
	*curvesOf { |env| ^env.curves.asArray.wrapExtend(env.times.size) }

	// ------------------------------------------------------------------ editing

	// a change from a widget: the map tells its dependants, and this window does
	// not draw itself again in the middle of a drag
	prEdit { |func|
		var m = this.selectedMap;
		m ?? { ^this };
		editing = true;
		protect { func.value(m) } { editing = false };
		this.prShowMap(m);
		this.prFillList;
	}

	prCurveChosen { |key|
		this.prEdit { |m|
			m.curve = switch(key,
				\bend, { if (m.curve.isNumber) { m.curve } { bendBox.value.max(0.5) ? 2 } },
				\drawn, { this.class.envOf(m.curve) },
				{ key })
		}
	}

	prBend { |x|
		this.prEdit { |m|
			var env, curves, k;
			case
			{ m.curve.isKindOf(Env) } {
				env = m.curve;
				k = this.prSelectedSegment(env);
				curves = this.class.curvesOf(env).collect { |c| if (c.isNumber) { c } { this.class.curveNumber(c) } };
				curves[k] = x;
				m.curve = Env(env.levels, env.times, curves);
			}
			{ m.curve = x }
		}
	}

	// a named segment shape as the nearest bend, so a drawn curve's bend box always has a number
	*curveNumber { |c| ^switch(c, \exp, 4, \exponential, 4, \lin, 0, \linear, 0, \sine, 0, \sin, 0, 0) }

	prSegmentCurve { |env, k| ^this.class.curvesOf(env)[k] !? { |c| if (c.isNumber) { c } { this.class.curveNumber(c) } } ? 0 }

	// the segment after the selected point; the last point's is the one before it
	prSelectedSegment { |env|
		var i = (envView.index ? 0).max(0);
		^i.clip(0, env.times.size - 1)
	}

	// the points as dragged: the ends stay at 0 and 1 across, so the curve covers
	// the whole travel of the control. A preset becomes a drawn curve
	prPointsMoved { |v|
		var xy = v.value, xs = xy[0].copy, ys = xy[1].copy, m = this.selectedMap, curves, env;
		if (m.isNil or: { xs.size < 2 }) { ^this };
		xs[0] = 0; xs[xs.size - 1] = 1;
		xs = xs.collect(_.clip(0, 1));
		(1..(xs.size - 1)).do { |i| xs[i] = xs[i].max(xs[i - 1]) };
		curves = this.class.curvesOf(this.class.envOf(m.curve));
		curves = curves.wrapExtend(xs.size - 1);
		env = Env(ys.collect(_.clip(0, 1)), xs.differentiate.drop(1), curves);
		editing = true;
		protect { m.curve = env } { editing = false };
		if (xy[0].first != 0 or: { xy[0].last != 1 }) { envView.value = [xs, ys] };
		curveMenu.value = menuCurves.detectIndex { |c| c[1] == \drawn };
		removePointBtn.enabled = xs.size > 2;
		this.prFillList;
		this.prShowReadout;
	}

	prAddPoint {
		this.prEdit { |m|
			var env = this.class.envOf(m.curve), k = this.prSelectedSegment(env);
			var xs = [0] ++ env.times.integrate, ys = env.levels, curves = this.class.curvesOf(env);
			var x = (xs[k] + xs[k + 1]) / 2, y = env.at(x);
			xs = xs.insert(k + 1, x);
			ys = ys.insert(k + 1, y);
			curves = curves.insert(k + 1, curves[k]);
			m.curve = Env(ys, xs.differentiate.drop(1), curves);
		};
	}

	prRemovePoint {
		this.prEdit { |m|
			var env = m.curve, i = envView.index, xs, ys, curves;
			if (env.isKindOf(Env) and: { i.notNil } and: { i > 0 } and: { i < (env.levels.size - 1) }) {
				xs = [0] ++ env.times.integrate; xs.removeAt(i);
				ys = env.levels.copy; ys.removeAt(i);
				curves = this.class.curvesOf(env).copy; curves.removeAt(i.min(curves.size - 1));
				m.curve = Env(ys, xs.differentiate.drop(1), curves);
			}
		}
	}

	// ------------------------------------------------------------- controllers

	protocols { ^#[[\midi], [\midi, \hid]][protoMenu.value ? 0] }

	// MKtl.find, and the list again
	find {
		var n;
		if (SDGMidi.isAvailable.not) { ^hints.note("finding controllers needs the Modality-toolkit quark") };
		hints.note("looking for controllers...");
		n = midi.findDevices(this.protocols).size;
		this.refresh;
		hints.note(if (n == 0) { "no device found - is it plugged in?" } { "% found - pick one to connect it".format(n) });
	}

	// the devices of the last find, then the controllers in use that are on none of them
	prDeviceRows {
		var rows = midi.devices(this.protocols).asList, onDevice = rows.collect(_[\ktl]);
		midi.ktls.keys.asArray.sort.do { |name|
			if (onDevice.includes(name).not) {
				rows.add((ktl: name, deviceName: "-", protocol: "",
					filenames: [midi.ktls[name]].reject(_.isNil), notFound: true))
			}
		};
		^rows.asArray
	}

	*rowKey { |row| ^row[\lookupName] ? row[\ktl] }

	prFillDevices {
		var k;
		deviceRows = this.prDeviceRows;
		k = deviceRows.detectIndex { |r| this.class.rowKey(r) == deviceKey };
		filling = true;
		protect {
			deviceList.items = if (deviceRows.isEmpty) {
				[if (SDGMidi.isAvailable) { "press find to see what is plugged in" } { "no Modality toolkit: Tidal controls only" }]
			} { deviceRows.collect { |r| this.prDeviceLine(r) } };
			deviceList.value = k;
		} { filling = false };
		this.prShowDevice(k !? { deviceRows[k] });
	}

	prDeviceLine { |r|
		var files = r[\filenames], desc, status;
		desc = case { files.isEmpty } { "no description" } { files.size == 1 } { files[0] } { "% descriptions".format(files.size) };
		status = case
		{ r[\ktl].notNil and: { midi.ktls.includesKey(r[\ktl]) } } {
			"connected as %%".format(r[\ktl], if (r[\notFound] == true) { " (" ++ midi.ktlState(r[\ktl]) ++ ")" } { "" })
		}
		{ r[\ktl].notNil } { "MKtl % - not used".format(r[\ktl]) }
		{ "" };
		^"% % % %".format(r[\protocol].asString.padRight(4), r[\deviceName].keep(26).padRight(26), desc.keep(22).padRight(22), status)
	}

	// a person picked a row: its name and description into the fields
	prChooseDevice { |row|
		var files = row[\filenames];
		deviceKey = this.class.rowKey(row);
		descMenu.items = if (files.isEmpty) { ["no description"] } { files };
		descMenu.value = 0;
		nameField.string = (row[\ktl] ?? { midi.suggestName(row, files.first) }).asString;
		this.prShowDevice(row);
		if (files.isEmpty) {
			hints.note("Modality has no description of %: its controls are unknown, so nothing can be mapped".format(row[\deviceName]))
		}
	}

	// what can be done with the selected row
	prShowDevice { |row|
		var used = row !? { |r| r[\ktl] !? { |n| midi.ktls.includesKey(n) } } ? false;
		connectBtn.enabled = SDGMidi.isAvailable and: { row.notNil } and: { used.not } and: { row[\filenames].notEmpty };
		forgetBtn.enabled = used;
		nameField.enabled = row.notNil and: { row[\ktl].isNil };
		descMenu.enabled = row.notNil and: { used.not } and: { row[\filenames].size > 1 };
		if (row.isNil) { nameField.string = ""; descMenu.items = [] };
	}

	prSelectedDevice { ^deviceRows !? { |rows| rows.detect { |r| this.class.rowKey(r) == deviceKey } } }

	prConnect {
		var row = this.prSelectedDevice, name = nameField.string.stripWhiteSpace, desc, mktl;
		row ?? { ^hints.note("pick a device first") };
		if ("^[A-Za-z][A-Za-z0-9_]*$".matchRegexp(name).not) {
			^hints.note("a name: a letter, then letters, digits and _ (midimix, m1)")
		};
		if (row[\ktl].isNil and: { midi.ktls.includesKey(name.asSymbol) or: { midi.ktl(name).notNil } }) {
			^hints.note("% is taken - another name".format(name))
		};
		desc = row[\filenames][descMenu.value ? 0];
		mktl = midi.connect(row, name, desc);
		deviceKey = row[\lookupName] ? name.asSymbol;
		this.refresh;
		hints.note(if (mktl.notNil) {
			"% connected - midi learn, then click something in the control window".format(mktl.name)
		} { "% did not connect - the post window says why".format(name) });
	}

	prForget {
		var row = this.prSelectedDevice;
		row !? { |r| r[\ktl] } !? { |n|
			midi.forgetKtl(n);
			hints.note("% forgotten, with its mappings".format(n));
		}
	}

	// the control window's learn mode
	prLearnMode { |on|
		var cw = midi.sdg.controlWindow;
		if (cw.isNil) {
			learnModeBtn.value = 0;
			^hints.note("midi learn works in the control window - open it first (~sdg.gui)")
		};
		cw.midiLearning = on;
		if (on) { hints.note("click a fader, knob, mute, solo or send in the control window, then move a control") };
	}

	prShowLearnMode {
		var cw = midi.sdg.controlWindow;
		learnModeBtn.value = (cw !? { |w| w.midiLearning } ? false).binaryValue;
		learnModeBtn.enabled = SDGMidi.isAvailable;
	}

	// ----------------------------------------------------------------- actions

	prLearn { |on|
		if (on and: { selectedAddress.notNil }) {
			midi.learn(selectedAddress);
			hints.note("move a control on the controller for %".format(SDGMidiTarget(midi.sdg, selectedAddress).label));
		} {
			midi.cancelLearn
		};
	}

	prLearnTidal {
		var name = tidalField.string.stripWhiteSpace;
		if (name.isEmpty or: { "^[A-Za-z_][A-Za-z0-9_]*$".matchRegexp(name).not }) {
			^hints.note("a Tidal control name: letters, digits and _ (cut, lpf, myamount)")
		};
		selectedAddress = [\tidal, name.asSymbol];
		this.prLearn(true);
		this.refresh;
	}

	// every mapping as a document: run it to make them again. With the IDE, in a
	// document whose ~sdg is this one; a document already open gets the new text
	openCode {
		var path = Platform.defaultTempDir +/+ "SuperDirtGUI-midi.scd";
		var doc = \Document.asClass !? { |d| try { d.allDocuments.detect { |x| x.path.notNil and: { SuperDirtGUI.samePath(x.path, path) } } } };
		midi.save(path);
		if (doc.notNil and: { SDGWindow.codeEditor.isNil }) { doc.string = midi.code; doc.front; ^path };
		SDGWindow.openCode(path, midi.sdg);
		^path
	}

	prShowSessionMenu {
		var names = SDGSession.names.select { |n| File.exists(SDGSession.midiPathOf(n)) };
		menu !? (_.destroy);
		menu = Menu(*(
			[MenuAction("the mappings of a session, in the place of these").enabled_(false), MenuAction.separator]
			++ if (names.isEmpty) {
				[MenuAction("no session has mappings yet").enabled_(false)]
			} {
				names.collect { |n|
					MenuAction(n, {
						var ok = midi.loadSession(n);
						hints.note(if (ok) { "the mappings of % - % of them".format(n, midi.maps.size) } { "% did not load - the post window says why".format(n) })
					})
				}
			}
		));
		menu.front;
	}

	prClosed {
		CmdPeriod.remove(this);
		midi.removeDependant(this);
		midi.sdg.removeDependant(this);
		if (midi.armed.notNil and: { midi.armed.address == selectedAddress }) { midi.cancelLearn };
		menu !? (_.destroy);
		try { windowBounds = window.bounds };
	}
}
