// =====================================================================
// SDGMidi - controllers on SuperDirtGUI, through the Modality toolkit
//
//   m = ~sdg.midi;                                  // one per SuperDirtGUI
//   m.useKtl(\m1, "akai-midimix");                  // MKtl(\m1), made if it is not there
//   m.map(\m1, \sl_1, [\fader, 0]);                 // the first fader is orbit 0's
//   m.map(\m1, \kn_1_1, [\param, 0, \global_sdgecho, \sdgfb], 0, 0.9, \exp);
//   m.map(\m1, \bt_1_1, [\mute, 0]);                // a button flips mute
//   m.map(\m1, \kn_2_1, [\tidal, \cut]);            // Tidal's cF 0 "cut"
//   m.learn([\solo, 0]);                            // the next element anyone moves solos orbit 0
//   m.code.postln;                                  // all of it, as the lines above
//   m.gui;                                          // controllers, learn, mappings: SDGMidiWindow
//   m.findDevices;                                  // MKtl.find; answers the devices seen
//   m.connect(m.devices.first);                     // one of them, from its description
//   m.forgetKtl(\m1);                               // its mappings go too
//   m.save(path); m.load(path);                     // a file of those lines
//
// What a mapping does is SDGMidiMap's; what it can move, SDGMidiTarget's.
// An element moves one target; a target has one element, so mapping either
// again replaces the mapping it had. A mapping whose target is gone waits for it,
// and one whose MKtl is not there yet starts working when it is (polled
// once a second).
//
// Elements are listened to as dependants (MKtlElement announces every move
// with changed(\doAction)), never through their action, so your own
// element actions keep working beside it. Buttons on on/off targets get
// their LED back when the target changes. Modality is looked up by name:
// without it there are no controllers and nothing else changes, and Tidal
// controls still work.
//
// The code is the storage: a session keeps it as midi.scd, and running
// that file - or the window's code... - puts the mappings back. Notifies
// \maps (the list), \map (one changed), \moved, \learn, \ktls, \devices,
// \learnMode (the control window's midi learn, switched on or off).
// =====================================================================

SDGMidi {
	classvar <continuousTypes, <buttonTypes;
	classvar <>tidalPort = 6010;

	var <sdg, <maps, <ktls;
	var listeners, <armed, learnAction, poller;
	var <tidalValues, tidalAddr;
	var window;					// the SDGMidiWindow while it is open

	*initClass {
		continuousTypes = #[\knob, \slider, \encoder, \xfader, \bender, \ribbon, \joyAxis, \mouseAxis];
		buttonTypes = #[\button, \pad, \encoderButton, \switch, \key];
	}

	*modality { ^\MKtl.asClass }
	*isAvailable { ^this.modality.notNil }

	*new { |sdg| ^super.newCopyArgs(sdg).init }

	init {
		maps = List.new;
		ktls = IdentityDictionary.new;
		listeners = IdentityDictionary.new;
		tidalValues = IdentityDictionary.new;
		sdg.addDependant(this);
		this.prStartPoll;
	}

	// ------------------------------------------------------------ controllers

	// name is an MKtl's, desc what makes it: MKtl(name, desc). Made here when there
	// is none of that name; one already there is left as it is - with a warning when
	// its description is not this one
	useKtl { |name, desc|
		var cl = this.class.modality, mktl;
		name = name.asSymbol;
		ktls[name] = desc !? (_.asString);
		if (cl.isNil) { ^nil };
		mktl = cl.all[name];
		if (mktl.isNil) {
			if (desc.isNil) { ^nil };
			mktl = try { cl.new(name, desc.asString) } { |err|
				"SuperDirtGUI: could not make MKtl(%, %): %".format(name.asCompileString, desc.asCompileString, err.errorString).warn;
				nil
			};
		} {
			if (desc.notNil and: { this.class.descMatches(mktl, desc).not }) {
				"SuperDirtGUI: MKtl(%) is a %, not a % - the mappings on it may land on the wrong elements"
				.format(name.asCompileString, this.class.descName(mktl), desc).warn
			}
		};
		mktl !? { this.prUse(mktl) };
		this.changed(\ktls);
		^mktl
	}

	*descName { |mktl| ^mktl.desc !? { |d| d.name.asString } ? "controller with no description" }

	// "akai-midimix" against the MKtl's own description's name; a * in it is any text
	*descMatches { |mktl, desc|
		var name = this.descName(mktl), parts = desc.asString.split($*).reject(_.isEmpty);
		^parts.isEmpty or: { parts.every { |p| name.containsi(p) } }
	}

	// the MKtls this sclang has, by name
	availableKtls { ^this.class.modality !? { |cl| cl.all.keys.asArray.sort } ? [] }

	ktl { |name| ^this.class.modality !? { |cl| cl.all[name.asSymbol] } }

	// whether the MKtl is there, and has a device under it (else it is virtual)
	ktlState { |name|
		var k = this.ktl(name);
		^case { k.isNil } { \missing } { k.hasDevice } { \connected } { \virtual }
	}

	// what this SuperDirtGUI takes mappings from: forget's the opposite of useKtl.
	// Its mappings go too; the MKtl itself stays, for whoever else uses it
	forgetKtl { |name|
		name = name.asSymbol;
		ktls.removeAt(name);
		this.prRelease(name);
		this.prRemove { |m| m.ktl == name };
		this.changed(\ktls);
	}

	// ---------------------------------------------------------------- devices

	// Modality's MKtl.find for these protocols - every port opened, the lookup made
	// again, the usual list in the post window - then answers the devices
	findDevices { |protocols = #[\midi, \hid], post = true|
		var cl = \MKtlDevice.asClass;
		if (cl.isNil) { "SuperDirtGUI: finding controllers needs the Modality toolkit".warn; ^[] };
		try { cl.find(protocols, post) } { |err| "SuperDirtGUI: MKtl.find: %".format(err.errorString).warn };
		this.changed(\devices);
		^this.devices(protocols)
	}

	// The devices the last find saw, one Event each: lookupName, protocol, deviceName,
	// filenames (Modality's descriptions for it: none, one, sometimes several),
	// multiIndex, and ktl - the name of an MKtl already on it, if any
	devices { |protocols = #[\midi, \hid]|
		var lookup = \MKtlLookup.asClass, out = List.new;
		if (lookup.isNil) { ^[] };
		lookup.allFor(protocols).keysValuesDo { |key, info|
			var name = info[\deviceName] ?? {
				info[\deviceInfo] !? { |d| try { d.productName } } ? info[\idInfo] ? key
			}, port;
			// a MIDI device of several ports (the IAC Driver's busses): the port says which
			if (info[\protocol] == \midi) {
				port = try { info[\deviceInfo].bubble.flatten.first.name };
				if (port.notNil and: { port.asString != name.asString }) { name = "% %".format(name, port) };
			};
			out.add((
				lookupName: key, protocol: info[\protocol], deviceName: name.asString,
				filenames: info[\filenames].asArray.collect(_.asString),
				multiIndex: info[\multiIndex], ktl: this.ktlOnDevice(key)
			))
		};
		^out.asArray.sort { |a, b|
			[a[\protocol], a[\deviceName]].join(" ") <= [b[\protocol], b[\deviceName]].join(" ")
		}
	}

	// the name of the MKtl whose device is lookupName's, or nil
	ktlOnDevice { |lookupName|
		var cl = this.class.modality;
		cl ?? { ^nil };
		cl.all.keysValuesDo { |name, k|
			if (k.lookupName == lookupName
				or: { k.device !? { |d| d.name == lookupName } ? false }) { ^name }
		};
		^nil
	}

	// A name for an MKtl of this description, short and free: "akai-midimix" -> \midimix,
	// \midimix2 when that is taken by another device
	suggestName { |device, desc|
		var cl = this.class.modality, base, name, k = 1;
		if (device.notNil) { device[\ktl] !? { |n| ^n } };
		base = (desc ?? { device !? { |d| d[\deviceName] } } ? "ctl").asString;
		base = base.split($-).reject(_.isEmpty).last ? base;
		base = base.select { |c| c.isAlphaNum or: { c == $_ } }.toLower;
		if (base.isEmpty or: { base.first.isAlpha.not }) { base = "ctl" ++ base };
		base = base.keep(16);
		name = base.asSymbol;
		while { ktls.includesKey(name) or: { cl !? { |c| c.all[name].notNil } ? false } } {
			k = k + 1; name = (base ++ k).asSymbol
		};
		^name
	}

	// A found device on SuperDirtGUI: its MKtl when there is one, made from the
	// description otherwise. Answers the MKtl, or nil (no description, or Modality
	// would not make it). Saved with the session as a useKtl line
	connect { |device, name, desc|
		var mktl;
		if (this.class.isAvailable.not) { "SuperDirtGUI: controllers need the Modality toolkit".warn; ^nil };
		desc = desc ?? { device[\filenames].first };
		if (desc.isNil) {
			"SuperDirtGUI: % has no Modality description, so it has no controls to map".format(device[\deviceName]).warn;
			^nil
		};
		name = (name ?? { this.suggestName(device, desc) }).asSymbol;
		mktl = device[\ktl] !? { |n| this.ktl(n) };
		if (mktl.notNil and: { mktl.name != name }) {
			// the device already has an MKtl: use that one, under its own name
			name = mktl.name;
		};
		^this.useKtl(name, desc)
	}

	// ---------------------------------------------------------------- mapping

	// Answers the SDGMidiMap. address is an SDGMidiTarget's; lo and hi are in the
	// target's own units (nil: its whole range)
	map { |ktl, element, address, lo, hi, curve, invert, takeover, mode|
		var target, m;
		// a kind there is no longer (a file from a version with group tracks): skipped,
		// so the mappings after it still land
		if (SDGMidiTarget.kinds[address.asArray.first.asSymbol].isNil) {
			"SuperDirtGUI midi: no such target %, mapping skipped".format(address.asCompileString).warn;
			^nil
		};
		target = SDGMidiTarget(sdg, address);
		ktl = ktl.asSymbol;
		element = element.asSymbol;
		// one element for a target, one target for an element: what it moved before is let go
		maps.copy.do { |x|
			if (x.target == target or: { x.ktl == ktl and: { x.element == element } }) { maps.remove(x) }
		};
		m = SDGMidiMap(this, ktl, element, target, lo, hi, curve, invert, takeover, mode);
		maps.add(m);
		this.ktl(ktl) !? { |k|
			this.prUse(k);
			// so the code, and so a session, can make it again
			if (ktls[ktl].isNil) { k.desc !? { |d| ktls[ktl] = d.name.asString } };
		};
		this.prFeedback(m);
		this.changed(\maps);
		^m
	}

	unmap { |address|
		var target = SDGMidiTarget(sdg, address);
		this.prRemove { |m| m.target == target }
	}

	unmapElement { |ktl, element|
		this.prRemove { |m| m.ktl == ktl.asSymbol and: { m.element == element.asSymbol } }
	}

	remove { |map| this.prRemove { |m| m === map } }

	// every mapping and every controller name: what a file of code starts with
	clear {
		maps = List.new;
		ktls = IdentityDictionary.new;
		this.cancelLearn;
		this.changed(\maps);
		this.changed(\ktls);
	}

	prRemove { |test|
		var n = maps.size;
		maps = maps.reject(test);
		if (maps.size != n) { this.changed(\maps) };
	}

	mapFor { |address|
		var target = SDGMidiTarget(sdg, address);
		^maps.detect { |m| m.target == target }
	}

	mapsOf { |ktl, element| ^maps.select { |m| m.ktl == ktl.asSymbol and: { m.element == element.asSymbol } } }

	// whether an element is a button (a note pair counts as one): nil when the
	// controller is not there to ask
	isButton { |ktl, element|
		var k = this.ktl(ktl), d, el;
		if (k.isNil or: { k.elementsDict.isNil }) { ^nil };
		d = k.elementsDict;
		if (d[(element ++ "_on").asSymbol].notNil) { ^true };
		el = d[element.asSymbol];
		^el !? { buttonTypes.includes(el.type) }
	}

	// ---------------------------------------------------------------- the window

	// the mapping editor, SDGMidiWindow; answers it, so .select(address) can follow
	gui {
		if (window.notNil and: { window.isOpen }) { window.front; ^window };
		window = SDGMidiWindow(this);
		^window
	}

	window { ^if (window.notNil and: { window.isOpen }) { window } }

	// ---------------------------------------------------------------- learning

	// the next element moved on any MKtl is mapped to address: a knob as soon as
	// it moves, a button when pressed. action gets the SDGMidiMap
	learn { |address, action|
		if (this.class.isAvailable.not) { "SuperDirtGUI: MIDI learn needs the Modality toolkit".warn; ^this };
		armed = SDGMidiTarget(sdg, address);
		learnAction = action;
		this.prUseAll;
		this.changed(\learn);
	}

	cancelLearn {
		if (armed.notNil) { armed = nil; learnAction = nil; this.changed(\learn) };
	}

	isLearning { ^armed.notNil }

	// ------------------------------------------------------------------- Tidal

	// Tidal's controller input: OSC /ctrl name value to its port, read in a
	// pattern with cF 0 "name" (cI, cS ...). Tidal must listen: cCtrlListen,
	// on by default
	sendTidal { |name, value|
		name = name.asSymbol;
		tidalValues[name] = value;
		tidalAddr = tidalAddr ?? { NetAddr("127.0.0.1", tidalPort) };
		tidalAddr.sendMsg('/ctrl', name.asString, value.asFloat);
	}

	tidalPort_ { |port| tidalPort = port; tidalAddr = nil }

	// -------------------------------------------------------------------- code

	// the mappings as code that makes them again: what midi.scd holds
	code {
		var lines = List[
			"// SuperDirtGUI MIDI mappings - run it (the whole file) to put them back",
			"// ~sdg.midi.map(ktl, element, target, lo, hi, curve:, invert:, takeover:, mode:) - see SDGMidi's help",
			"~sdg.midi.clear;"
		];
		ktls.keys.asArray.sort.do { |k|
			lines.add("~sdg.midi.useKtl(%, %);".format(this.class.codeOf(k), ktls[k].asCompileString))
		};
		maps.do { |m| lines.add(m.asCode) };
		^lines.join("\n") ++ "\n"
	}

	// x as you would type it: \\kn_1_1, [\\fader, 0], 0.25
	*codeOf { |x|
		^case
		{ x.isKindOf(Symbol) and: { "^[a-z_][a-zA-Z0-9_]*$".matchRegexp(x.asString) } } { "\\" ++ x }
		{ x.isKindOf(Symbol) } { x.asCompileString }
		{ x.isFloat } { x.round(0.0001).asString }
		{ x.isArray and: { x.isString.not } } { "[" ++ x.collect { |y| this.codeOf(y) }.join(", ") ++ "]" }
		{ x.asCompileString }
	}

	save { |path|
		var f = File(path.standardizePath, "w");
		if (f.isOpen.not) { "SuperDirtGUI: could not write %".format(path).warn; ^false };
		f.write(this.code);
		f.close;
		^true
	}

	// a file of mappings, in place of the ones there are: an empty file, or one
	// that is not there, clears them. Answers false when the file does not run
	load { |path|
		path = path.standardizePath;
		if (File.exists(path).not) { this.clear; ^true };
		^this.loadCode(File.readAllString(path), path)
	}

	// the mappings saved with another session, in place of these; the rest of
	// the mixer stays as it is
	loadSession { |name|
		if (SDGSession.exists(name).not) { "SuperDirtGUI: no session called %".format(name.asCompileString).warn; ^false };
		^this.load(SDGSession.midiPathOf(name))
	}

	loadCode { |text, where = "the mappings"|
		var func, ok = true;
		if (text.isNil or: { text.every(_.isSpace) }) { this.clear; ^true };
		func = thisProcess.interpreter.compile(text);
		if (func.isNil) { "SuperDirtGUI: % does not compile".format(where).warn; ^false };
		this.clear;
		// the file speaks of ~sdg: this one, whatever the environment holds
		Environment.make { ~sdg = sdg; ~dirt = sdg.dirt }.use {
			try { func.value } { |err| "SuperDirtGUI: %: %".format(where, err.errorString).warn; ok = false }
		};
		^ok
	}

	// ---------------------------------------------------------------- listening

	prUse { |mktl|
		var func, name = mktl.name;
		if ((listeners[name] !? { |l| l[0] === mktl }) == true) { ^this };
		this.prRelease(name);
		if (mktl.elementsDict.isNil) { ^this };
		func = { |el, what| if (what == \doAction) { this.prMoved(name, el) } };
		mktl.elementsDict.do { |el| el.addDependant(func) };
		listeners[name] = [mktl, func];
		// the LEDs as the targets are now
		maps.do { |m| if (m.ktl == name) { this.prFeedback(m) } };
	}

	prRelease { |name|
		listeners.removeAt(name) !? { |l|
			l[0].elementsDict !? { |d| d.do { |el| el.removeDependant(l[1]) } }
		}
	}

	prUseAll { this.class.modality !? { |cl| cl.all.do { |k| this.prUse(k) } } }

	// an MKtl a mapping names, made since or made again; while learning, any
	prStartPoll {
		if (this.class.isAvailable.not) { ^this };
		poller = SkipJack({ this.prPoll }, 1, name: "SuperDirtGUI midi");
	}

	prPoll {
		var cl = this.class.modality;
		if (armed.notNil) { ^this.prUseAll };
		(maps.collect(_.ktl).asSet ++ ktls.keys).do { |n|
			cl.all[n] !? { |k| this.prUse(k) }
		};
		// an MKtl freed since: its elements are gone
		listeners.keys.copy.do { |n|
			if (cl.all[n] !== listeners[n][0]) { this.prRelease(n) }
		};
	}

	// in Modality's thread - MIDI's, or the one that called valueAction: nothing may throw back into it
	prMoved { |ktl, el|
		try { this.prHandle(ktl, el) } { |err| ("SuperDirtGUI midi: " ++ err.errorString).warn }
	}

	prHandle { |ktl, el|
		var c = this.class.control(el), m;
		if (c[\value].isNumber.not) { ^this };
		if (armed.notNil) {
			if (c[\button].not or: { c[\press] }) {
				m = this.map(ktl, c[\key], armed.address);
				armed = nil;
				learnAction.value(m);
				learnAction = nil;
				this.changed(\learn, m);
			};
			^this
		};
		maps.do { |m| if (m.ktl == ktl and: { m.element == c[\key] }) { m.handle(c) } };
	}

	// An element as a control. A note pair - a pad sending note on when pressed
	// and note off when let go, Modality's notePair - is one button, named as
	// its group: bt_1_1 for bt_1_1_on and bt_1_1_off.
	*control { |el|
		var name = el.name.asString;
		var role = el.elemDesc !? { |d| d[\key] };
		var value = el.value;
		^case
		{ role == \on and: { name.endsWith("_on") } } {
			(key: name.drop(-3).asSymbol, button: true, press: value.isNumber and: { value > 0 }, value: value)
		}
		{ role == \off and: { name.endsWith("_off") } } {
			(key: name.drop(-4).asSymbol, button: true, press: false, value: 0)
		}
		{ buttonTypes.includes(el.type) } {
			(key: el.name, button: true, press: value.isNumber and: { value > 0.5 }, value: value)
		}
		{ (key: el.name, button: false, value: value) }
	}

	// ----------------------------------------------------------------- feedback

	// a button on an on/off target shows it: its LED, or the _on half of a note pair.
	// value_ sends to the device and is no \doAction, so nothing comes back in
	prFeedback { |m|
		var k = this.ktl(m.ktl), el, v;
		if (k.isNil or: { m.target.isToggle.not } or: { k.elementsDict.isNil }) { ^this };
		el = k.elementsDict[(m.element ++ "_on").asSymbol] ?? { k.elementsDict[m.element] };
		v = m.target.value;
		if (el.notNil and: { v.isNumber } and: { buttonTypes.includes(el.type) }) {
			try { el.value = v.clip(0, 1) }
		};
	}

	// the mixer moved some other way: the mouse, a session, code
	update { |who, what|
		if (who === sdg and: { #[\mixer, \returns].includes(what) }) {
			maps.do { |m| if (m.target.isToggle) { this.prFeedback(m) } }
		}
	}

	// ---------------------------------------------------------------------------

	free {
		window !? { |w| w.close }; window = nil;
		poller !? { poller.stop; poller = nil };
		listeners.keys.copy.do { |n| this.prRelease(n) };
		sdg.removeDependant(this);
		maps = List.new;
		armed = nil;
	}
}
