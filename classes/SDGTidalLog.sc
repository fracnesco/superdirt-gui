// =====================================================================
// SDGTidalLog - what Tidal sends: every /dirt/play message that reaches
// SuperDirt's port, as a line of text, newest first
//
//   l = ~sdg.tidalLog;                  // one per SuperDirtGUI
//   l.gui;                              // the window, SDGTidalLogWindow
//   l.start; l.stop;                    // listen without the window
//   l.lines;                            // the lines the window shows
//   l.orbit = 1; l.filter = "bd";       // only orbit 1, only lines with bd
//
// It listens with an OSCFunc of its own on SuperDirt's port, next to
// SuperDirt's: nothing of SuperDirt changes, and an event plays whether
// or not it is logged. The window listens while it is open.
//
// A line is the pattern (d1 for _id_ 1, the name for p "name"), the
// orbit, the cycle the event starts on, the sound (s:n) and every other
// parameter in the order Tidal sent it. cps and delta are timing, shown
// with showTiming.
// =====================================================================

SDGTidalLog {
	classvar <>maxEntries = 500;
	// the keys a line shows in its own columns, or only with showTiming
	classvar <columnKeys = #[\_id_, \orbit, \cycle, \s, \n];
	classvar <timingKeys = #[\cps, \delta, \latency];

	var <sdg, <entries, responder, window;
	var <paused = false, <orbit, <filter, <showTiming = false, <count = 0;

	*new { |sdg| ^super.newCopyArgs(sdg).init }

	init { entries = List.new }

	dirt { ^sdg.dirt }

	// ----------------------------------------------------------- listening

	isListening { ^responder.notNil }

	start {
		var port = sdg.dirt.port;
		if (responder.notNil) { ^this };
		if (port.isNil) { "SuperDirtGUI: SuperDirt is not listening yet (~dirt.start)".warn; ^this };
		// fixed, so a cmd-period does not take it away; stop frees it
		responder = OSCFunc({ |msg, time| this.add(msg, time) }, '/dirt/play', recvPort: port).fix;
		this.changed(\listening);
	}

	stop {
		responder !? { responder.free; responder = nil; this.changed(\listening) };
	}

	// one message, as OSCFunc hands it over: ['/dirt/play', key, value, ...]
	add { |msg, time|
		var e;
		if (paused) { ^this };
		e = this.class.parse(msg);
		e[\time] = time;
		entries.addFirst(e);
		while { entries.size > maxEntries } { entries.pop };
		count = count + 1;
		this.changed(\event);
	}

	clear { entries.clear; count = 0; this.changed(\event) }

	paused_ { |bool| paused = bool == true; this.changed(\settings) }
	orbit_ { |i| orbit = i !? { i.asInteger }; this.changed(\settings) }
	filter_ { |str| filter = if (str.isNil or: { str.isEmpty }) { nil } { str.asString }; this.changed(\settings) }
	showTiming_ { |bool| showTiming = bool == true; this.changed(\settings) }

	// --------------------------------------------------------------- lines

	// the parameters of a message, in the order they came, and by name
	*parse { |msg|
		var keys = List.new, e = ();
		msg.drop(1).pairsDo { |k, v| k = k.asSymbol; keys.add(k); e[k] = v };
		e[\keys] = keys.asArray;
		^e
	}

	// "d1", "o0", "12.25", "bd:3", "gain 1.2  speed 2"
	*patternName { |e|
		var id = e[\_id_] !? (_.asString);
		^case
		{ id.isNil } { "-" }
		{ id.notEmpty and: { id.every(_.isDecDigit) } } { "d" ++ id }
		{ id }
	}

	*num { |x|
		if (x.isNumber.not) { ^x.asString };
		if (x.frac == 0 and: { x.abs < 1e9 }) { ^x.asInteger.asString };
		^x.round(0.0001).asString
	}

	*soundName { |e|
		var s = e[\s] ? e[\sound];
		if (s.isNil) { ^"-" };
		^s.asString ++ (e[\n] !? { |n| ":" ++ this.num(n) } ? "")
	}

	*format { |e, timing = false|
		var skip = columnKeys ++ #[\sound] ++ if (timing) { [] } { timingKeys };
		var params = e[\keys].reject { |k| skip.includes(k) }.collect { |k| k.asString ++ " " ++ this.num(e[k]) };
		^"%  %  %  %  %".format(
			this.patternName(e).padRight(5),
			("o" ++ (e[\orbit] ? 0).asInteger).padRight(3),
			(e[\cycle] !? { |c| c.round(0.001).asStringPrec(10) } ? "-").padLeft(9),
			this.soundName(e).padRight(14),
			params.join("  ")
		)
	}

	// the entries the settings let through, newest first
	visibleEntries {
		^entries.select { |e|
			(orbit.isNil or: { (e[\orbit] ? 0).asInteger == orbit })
		}
	}

	lines {
		var out = this.visibleEntries.collect { |e| this.class.format(e, showTiming) };
		filter !? { |f| out = out.select { |line| line.containsi(f) } };
		^out.asArray
	}

	// ----------------------------------------------------------- the window

	gui {
		if (window.notNil and: { window.isOpen }) { window.front; ^window };
		window = SDGTidalLogWindow(this);
		^window
	}

	window { ^if (window.notNil and: { window.isOpen }) { window } }

	free {
		this.stop;
		window !? { |w| { w.close }.defer };
		window = nil;
	}
}
