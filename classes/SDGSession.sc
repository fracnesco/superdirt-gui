// =====================================================================
// SDGSession - the whole mixer of a SuperDirtGUI, saved under a name and
// brought back: every fader, mute, solo, limiter and output bus, the tracks
// with their levels and parameters, the return tracks with every send, every
// insert in its place with its settings - a VST insert with the plugin's own state.
//
//   ~sdg.saveSession("gig");                  // saved a moment later: plugins are asked
//   ~sdg.sessionNames;                        // -> [ gig ]
//   ~sdg.loadSession("gig", { |ok| ok.postln });
//   SDGSession.folder.openOS;                 // where they are
//
// A session is a folder of its own, in the user's support directory so that it
// outlives an update of the quark: session.scd, readable, an orbit at a time,
// midi.scd, the controller mappings as the code that makes them (SDGMidi),
// and one .state file per VST insert, the plugin's state as it hands it over.
// The state is a file of its own because a plugin's can run to megabytes, which
// would bury the rest of the text. session.scd is SuperCollider
// code, read by running it: load only sessions you trust.
//
// Loading replaces what the orbits hold. Inserts are all taken out and made
// again; a track whose effect the session also has stays and keeps playing, so
// its tail rings on; the others go, and the session's missing ones are added.
// Something that cannot come back - an effect whose file is gone, a kind whose
// extension is not installed - is warned about and left out, and the rest loads.
// Orbits beyond the session's are left as they are. The mappings are always
// replaced: a session saved with none, or from before there were mappings,
// leaves none. ~sdg.midi.loadSession(name) takes another session's alone.
//
// What a session does not hold: content files (they are on disk), sample banks
// (load them as you did), Tidal's own patterns, and the insert windows' places.
// =====================================================================

SDGSession {

	classvar folder;

	var <sdg, <name, <busy = false;
	var <problems;			// what the last load had to leave out, as Strings
	var <>windowState;		// the window's part of the last session, for a window opened later

	*version { ^1 }

	*folder { ^folder ?? { Platform.userAppSupportDir +/+ "superdirt-gui-sessions" } }
	*folder_ { |path| folder = path }

	*pathOf { |name| ^this.folder +/+ name.asString }

	// the sessions in the folder, by name, whatever the case
	*names {
		^SuperDirtGUI.prFolderEntries(this.folder)
			.select { |entry| File.exists(entry +/+ "session.scd") }
			.collect { |entry| entry.withoutTrailingSlash.basename }
			.sort { |a, b| a.toLower < b.toLower }
	}

	*exists { |name| ^File.exists(this.pathOf(name) +/+ "session.scd") }

	// the session's controller mappings, SDGMidi's code
	*midiPathOf { |name| ^this.pathOf(name) +/+ "midi.scd" }

	// Why a name will not do, as a String, or nil when it will. It names a
	// folder, on any of the systems a student has.
	*nameProblem { |name|
		name = (name ? "").asString;
		if(name.isEmpty) { ^"no name yet" };
		if(name.any { |char| "/\\:*?\"<>|".includes(char) }) { ^"a name cannot have any of / \\ : * ? \" < > |" };
		if(name.first == $.) { ^"a name cannot start with a dot" };
		if(name.last == $. or: { name.last == $  }) { ^"a name cannot end with a dot or a space" };
		^nil
	}

	// made by SuperDirtGUI:session, one per SuperDirtGUI
	*new { |sdg| ^super.newCopyArgs(sdg) }

	// ----- saving

	// Every VST insert is asked for its state first, then the session is written
	// over any of that name. action gets true, or false with a warning.
	save { |argName, action|
		var problem = this.class.nameProblem(argName) ?? { this.prBlocked } ?? { this.prNotReady };
		if(problem.notNil) {
			"SuperDirtGUI: session not saved - %".format(problem).warn;
			^action.value(false)
		};
		busy = true;
		argName = argName.asString;
		Routine {
			var ok = false;
			this.prReadPluginStates;
			try {
				ok = this.prWrite(argName);
			} { |err|
				"SuperDirtGUI: session '%' not saved - %".format(argName, err.errorString).warn;
			};
			busy = false;
			if(ok) {
				name = argName;
				if(sdg.verbose) { "SuperDirtGUI: session '%' saved in %".format(name, this.class.pathOf(name)).postln };
				sdg.changed(\sessions, name);
			};
			action.value(ok);
		}.play(AppClock);
	}

	// the session as it would be written, as an Event; a VST insert's state as
	// the bytes read back last
	capture {
		var orbits = sdg.dirt.orbits;
		^(
			version: this.class.version,
			numChannels: sdg.dirt.numChannels,
			window: sdg.controlWindow !? (_.sessionState) ?? { windowState },
			returnFeedback: sdg.returnFeedback,
			returns: sdg.returns.collect { |ret| ret.sessionData },
			master: sdg.master !? { |m| m.sessionData },
			orbits: orbits.collect { |orbit, i| this.prOrbitData(orbit, i) }
		)
	}

	prOrbitData { |orbit, i|
		^(
			fader: sdg.faderValues[i],
			mute: sdg.muteStates[i],
			solo: sdg.soloStates[i],
			limitertype: (orbit.get(\limitertype) ? 1).asInteger,
			outBus: (orbit.outBus.tryPerform(\index) ? orbit.outBus ? 0).asInteger,
			dest: sdg.orbitDest(i),
			directBus: sdg.directBusOf(i),
			tracks: sdg.tracksOf(i).select { |track| track.fx.notNil }.collect { |track|
				(
					name: track.name,
					level: track.level,
					active: track.fx.active,
					params: this.prParamsOf(orbit, track.fx),
					pre: track.pre.collect(_.sessionData),
					post: track.post.collect(_.sessionData)
				)
			},
			inserts: sdg.insertsOf(i).collect(_.sessionData),
			// pairs: a letter is a capital, which an Event's text could not have as a key
			sends: sdg.returns.collect { |ret| [ret.letter, ret.orbitLevel(i)] }.flatten(1)
		)
	}

	// what the orbit's default event holds for an effect: what the knobs set.
	// fx.state is not asked: it also holds the last value a pattern sent, which
	// would come back as a value the pattern never has to send again
	prParamsOf { |orbit, fx|
		var out = ();
		fx.paramNames.asArray.do { |param|
			var value = orbit.defaultParentEvent[param];
			if(value.isNumber) { out[param] = value }
		};
		^out
	}

	// each open plugin read back now, and at most five seconds for them all: one
	// that does not answer is saved with the state it last gave. parameterAutomated
	// is no help here, a preset picked in a plugin's editor need not send it
	prReadPluginStates {
		var waiting = 0, waited = 0;
		this.prEveryInsert.select(_.isVST).do { |insert|
			waiting = waiting + 1;
			insert.rememberState({ waiting = waiting - 1 });
		};
		while { waiting > 0 and: { waited < 5 } } {
			0.02.wait;
			waited = waited + 0.02;
		};
		if(waiting > 0) { "SuperDirtGUI: % plugins did not hand over their state in time".format(waiting).warn };
	}

	prEveryInsert {
		^sdg.dirt.orbits.collect { |orbit| sdg.prEveryInsertOf(orbit.orbitIndex) }.flatten(1)
			++ sdg.busTracks.collect(_.inserts).flatten(1)
	}

	// The state files first, then the text, which is compiled and run before it
	// replaces anything - a session that cannot be read back is not saved at all.
	// Answers true.
	prWrite { |argName|
		var dir = this.class.pathOf(argName), data = this.capture;
		var count = 0, states = [], text, check, keep;
		this.prInsertDataOf(data).do { |d|
			d[\state] !? { |bytes|
				count = count + 1;
				d[\state] = "vst%.state".format(count);
				states = states.add([d[\state], bytes]);
			}
		};
		text = this.prText(argName, data);
		check = this.prEvaluate(text);
		if(check.isKindOf(Event).not) { Error("what would be written does not read back").throw };
		File.mkdir(dir);
		states.do { |pair|
			File.use(dir +/+ pair[0], "wb", { |file| file.write(pair[1]) })
		};
		File.use(dir +/+ "session.scd", "w", { |file| file.write(text) });
		// always, so loading it always says what the mappings are, none included
		File.use(dir +/+ "midi.scd", "w", { |file| file.write(sdg.midi.code) });
		keep = states.collect(_[0]);
		(dir +/+ "*.state").pathMatch.do { |old|
			if(keep.includesEqual(old.basename).not) { File.delete(old) }
		};
		^true
	}

	// every insert's Event in a session's data: the orbits', their tracks', the returns'
	prInsertDataOf { |data|
		var out = [];
		data[\orbits].do { |orbit|
			out = out ++ (orbit[\inserts] ? []);
			(orbit[\tracks] ? []).do { |t| out = out ++ (t[\pre] ? []) ++ (t[\post] ? []) };
		};
		((data[\returns] ? []) ++ (data[\master] !? { |m| [m] } ? [])).do { |r|
			out = out ++ (r[\pre] ? []) ++ (r[\inserts] ? []) ++ (r[\post] ? [])
		};
		^out
	}

	// an orbit or a return at a time, for a session that can be read and compared
	prText { |argName, data|
		var lines = { |list| list.collect { |x| "\t" ++ x.asCompileString }.join(",\n") };
		^"// SuperDirtGUI session %, saved %\n(\nversion: %,\nnumChannels: %,\nwindow: %,\nreturnFeedback: %,\nreturns: [\n%\n],\nmaster: %,\norbits: [\n%\n]\n)\n".format(
			argName.asCompileString, Date.localtime.asString,
			data[\version], data[\numChannels], data[\window].asCompileString,
			data[\returnFeedback] ? false,
			lines.(data[\returns] ? []),
			data[\master].asCompileString,
			lines.(data[\orbits]))
	}

	// the text's value, nil when it does not compile or throws
	prEvaluate { |text|
		var func = thisProcess.interpreter.compile(text);
		^func !? { try { func.value } }
	}

	// ----- loading

	// action gets true once everything is playing and every plugin has its state,
	// false with a warning when nothing was touched because the session could not
	// be read. An orbit that is not ready yet - loaded in the same breath as
	// SuperDirtGUI(~dirt) - is waited for, up to ten seconds.
	load { |argName, action|
		var problem = this.prBlocked, data;
		if(problem.notNil) {
			"SuperDirtGUI: session not loaded - %".format(problem).warn;
			^action.value(false)
		};
		argName = argName.asString;
		data = this.prRead(argName);
		if(data.isNil) { ^action.value(false) };
		busy = true;
		problems = [];
		Routine {
			var ok = false, waited = 0;
			while { this.prNotReady.notNil and: { waited < 10 } } {
				0.1.wait;
				waited = waited + 0.1;
			};
			if(this.prNotReady.notNil) {
				"SuperDirtGUI: session '%' not loaded - %".format(argName, this.prNotReady).warn;
			} {
				this.prFindPlugins(data);
				try {
					this.prApply(data, this.class.pathOf(argName));
					// a mapping whose effect is not back yet waits for it
					sdg.midi.load(this.class.midiPathOf(argName));
					ok = true;
				} { |err|
					"SuperDirtGUI: session '%' stopped half way - %".format(argName, err.errorString).warn;
				};
			};
			if(ok) {
				name = argName;
				windowState = data[\window];
				sdg.changed(\session, name);
				this.prWaitForInserts;
				if(problems.notEmpty) {
					"SuperDirtGUI: session '%' loaded without:\n  %".format(argName, problems.join("\n  ")).warn
				} {
					if(sdg.verbose) { "SuperDirtGUI: session '%' loaded".format(name).postln }
				};
			};
			busy = false;
			action.value(ok);
		}.play(AppClock);
	}

	// what waiting will not help
	prBlocked {
		if(busy) { ^"another session is being saved or loaded" };
		if(sdg.dirt.server.serverRunning.not) { ^"the server is not running" };
		^nil
	}

	// what will pass by itself: a String, nil when the orbits are ready
	prNotReady {
		var orbits = sdg.dirt.orbits;
		if(orbits.isNil) { ^"no orbits yet - call ~dirt.start first" };
		if(sdg.faderValues.isNil or: { orbits.any { |orbit|
			orbit.globalEffects.every { |fx| fx.name != SuperDirtGUI.faderEffectName }
		} }) { ^"the orbits' faders are not there yet" };
		^nil
	}

	// the session's Event, or nil with a warning
	prRead { |argName|
		var problem = this.class.nameProblem(argName), file, data;
		if(problem.notNil) {
			"SuperDirtGUI: no session called % - %".format(argName.asCompileString, problem).warn;
			^nil
		};
		file = this.class.pathOf(argName) +/+ "session.scd";
		if(File.exists(file).not) {
			"SuperDirtGUI: no session called % in %".format(argName.asCompileString, this.class.folder).warn;
			^nil
		};
		data = this.prEvaluate(File.readAllString(file));
		if(data.isKindOf(Event).not or: { data[\orbits].isKindOf(Array).not }) {
			"SuperDirtGUI: % does not read as a session".format(file).warn;
			^nil
		};
		if((data[\version] ? 1) > this.class.version) {
			"SuperDirtGUI: session '%' was saved by a newer SuperDirtGUI - what it does not know is left out".format(argName).warn
		};
		^data
	}

	// A plugin saved by name needs VSTPlugin's search to have found it: when one
	// is unknown and its file is not there either, the search runs now - quick
	// from its cache, up to two minutes when it has never run.
	prFindPlugins { |data|
		var missing = false, done = false, waited = 0;
		this.prInsertDataOf(data).do { |d|
			if(d[\kind] == \vst and: { SDGInsert.available(\vst) } and: { this.prPluginFor(d).isNil }) { missing = true }
		};
		if(missing) {
			sdg.vstPlugins({ done = true }, true);
			while { done.not and: { waited < 120 } } { 0.1.wait; waited = waited + 0.1 };
		}
	}

	// what to open for a saved plugin: its name once the search knows it, or a file
	// path that is there; nil when neither is
	prPluginFor { |d|
		var cls = \VSTPlugin.asClass, plugin = d[\plugin].asString, path = d[\path];
		var known = cls !? { (cls.plugins(sdg.dirt.server) ? ()).at(plugin.asSymbol) };
		if(known.notNil) { ^plugin };
		if(File.exists(plugin.standardizePath)) { ^plugin };
		if(path.notNil and: { File.exists(path) }) { ^path };
		^nil
	}

	prApply { |data, dir|
		var orbits = sdg.dirt.orbits, saved = data[\orbits];
		if(saved.size != orbits.size) {
			problems = problems.add("% of its % orbits: this SuperDirt has %".format(
				saved.size.min(orbits.size), saved.size, orbits.size));
		};
		if((data[\numChannels] ? sdg.dirt.numChannels) != sdg.dirt.numChannels) {
			problems = problems.add("its plugins' channels: it was saved with % channels, this SuperDirt has %".format(
				data[\numChannels], sdg.dirt.numChannels));
		};
		this.prApplyReturns(data[\returns] ? [], data[\returnFeedback] ? false, dir);
		(data[\groups] ? []).do { |d|
			problems = problems.add("group %: there are no group tracks now - its orbits go to the master".format(d[\name]))
		};
		this.prApplyMaster(data[\master] ? (), dir);
		saved.size.min(orbits.size).do { |i| this.prApplyOrbit(orbits[i], i, saved[i], dir) };
	}

	// The returns first, so the orbits' sends have something to go to. One
	// the session also has stays and its inserts are made again; the others go. A
	// session from before there were returns has none, so none are left.
	prApplyReturns { |saved, feedback, dir|
		var wanted = saved.collect { |d| d[\letter] };
		sdg.returns.copy.do { |ret|
			if(wanted.includes(ret.letter)) {
				ret.inserts.do { |x| sdg.removeInsert(x) }
			} {
				sdg.removeReturn(ret)
			}
		};
		saved.do { |d|
			var ret = sdg.returnAt(d[\letter]) ?? { sdg.addReturn(d[\letter]) };
			ret !? {
				// a return held one global effect once
				d[\effect] !? { |name|
					problems = problems.add("%'s effect %: a return holds inserts only now".format(ret.where, name))
				};
				this.prApplyBus(ret, d, dir);
				d[\preFader] !? { |v| ret.preFader = v };
			}
		};
		// between returns: all of them are there by now
		saved.do { |d|
			(d[\sends] ? []).pairsDo { |letter, value|
				sdg.returnAt(letter) !? { |to| sdg.setReturnSend(d[\letter], to, value) }
			}
		};
		sdg.returnFeedback = feedback;
	}

	// A session from before there was a master leaves it clean, at 0 dB.
	prApplyMaster { |d, dir|
		var m = sdg.master;
		if(m.isNil) {
			if((d[\inserts] ? []).notEmpty) { problems = problems.add("the master's inserts: there is no master (useMaster is false)") };
			^this
		};
		m.inserts.do { |x| sdg.removeInsert(x) };
		m.level = d[\level] ? 1;
		m.limitertype = d[\limitertype] ? 0;
		m.route(\direct, d[\directBus] ? sdg.hardwareOut);
		(d[\inserts] ? []).do { |x| this.prAddInsert(nil, x, m, \chain, dir) };
	}

	// what the bus tracks have in common
	prApplyBus { |track, d, dir|
		d[\level] !? { |v| track.level = v };
		track.mute = d[\mute] ? false;
		track.solo = d[\solo] ? false;
		d[\limitertype] !? { |v| track.limitertype = v };
		if(d[\dest].notNil or: { d[\directBus].notNil }) {
			track.route(d[\dest] ? \master, d[\directBus])
		} {
			// a session from before there was a master: its output bus, if any
			d[\outBus] !? { |v| if(v == sdg.hardwareOut) { track.route(\master) } { track.route(\direct, v) } }
		};
		((d[\pre] ? []) ++ (d[\inserts] ? []) ++ (d[\post] ? [])).do { |x| this.prAddInsert(nil, x, track, \chain, dir) };
	}

	// The output first: it rebuilds the orbit, which an insert still starting
	// would then start a second time. The fader last, so a level comes up on the
	// chain it was saved with.
	prApplyOrbit { |orbit, i, d, dir|
		sdg.removeInserts(i);
		sdg.tracksOf(i).do { |track| track.inserts.do { |x| sdg.removeInsert(x) } };
		sdg.adoptEffects(orbit);
		sdg.returns.do { |ret| sdg.setSend(i, ret, 0) };
		(d[\sends] ? []).pairsDo { |letter, value| sdg.setSend(i, letter, value) };
		d[\limitertype] !? { |v| sdg.setOrbitParam(i, \limitertype, v) };
		// a session from before there was a master: its output bus says where
		if(d[\dest].notNil) {
			// a group's name, from a session with group tracks: the master
			sdg.setOrbitDest(i, if(d[\dest] == \direct) { \direct } { \master },
				d[\directBus])
		} {
			d[\outBus] !? { |v|
				if(v == sdg.hardwareOut) { sdg.setOrbitDest(i, \master) } { sdg.setOrbitDest(i, \direct, v) }
			}
		};
		this.prApplyTracks(orbit, i, d[\tracks] ? [], dir);
		(d[\inserts] ? []).do { |x| this.prAddInsert(i, x, nil, nil, dir) };
		d[\fader] !? { |v| sdg.setFader(i, v) };
		d[\mute] !? { |v| sdg.setMute(i, v) };
		d[\solo] !? { |v| sdg.setSolo(i, v) };
		sdg.wakeOrbit(orbit);
	}

	prApplyTracks { |orbit, i, saved, dir|
		var wanted = saved.collect { |d| d[\name] }, placed = 0;
		sdg.tracksOf(i).copy.do { |track|
			if(wanted.includes(track.name).not) { sdg.removeTrack(track) }
		};
		saved.do { |d|
			var track = sdg.tracksOf(i).detect { |x| x.name == d[\name] } ?? { sdg.addTrack(i, d[\name]) };
			if(track.isNil) {
				problems = problems.add("orbit %'s effect %: no such SynthDef".format(i, d[\name]));
			} {
				sdg.moveTrack(track, placed);
				placed = placed + 1;
				d[\level] !? { |v| track.level = v };
				track.fx !? { |fx| this.prApplyParams(orbit, fx, d[\params] ? (), d[\active] ? true) };
				[\pre, \post].do { |place|
					(d[place] ? []).do { |x| this.prAddInsert(i, x, track, place, dir) }
				};
			}
		}
	}

	// A parameter the session has no value for goes back to the SynthDef's default
	// and leaves the default event, as the window's x does. An effect that is off
	// takes nothing (GlobalDirtEffect:set), so it is on while it is told.
	prApplyParams { |orbit, fx, saved, active|
		var desc = SynthDescLib.at((fx.name.asString ++ fx.numChannels).asSymbol);
		if(fx.active.not) { fx.active_(true) };
		fx.paramNames.asArray.do { |param|
			var value = saved[param], default;
			if(value.notNil) {
				orbit.set(param, value)
			} {
				if(orbit.defaultParentEvent[param].notNil) {
					default = desc !? { desc.controlDict[param] } !? { |ctl| ctl.defaultValue };
					if(default.isNumber) { fx.synth !? { |syn| syn.set(param, default) } };
					orbit.defaultParentEvent.removeAt(param);
					fx.state.removeAt(param);
				}
			}
		};
		fx.set(orbit.defaultParentEvent);
		if(active.not) { fx.active_(false) };
	}

	// one insert back, settings before its synth starts: they go into the effect's
	// state, which play sends, or for a plugin wait for it to open. Added last
	// in its list, so the session's order is the order it is added in
	prAddInsert { |i, d, track, place, dir|
		var kind = d[\kind], plugin, insert, where;
		where = if(track.isKindOf(SDGBusTrack)) { track.where } {
			track !? { "orbit %'s % %".format(i, place, track.shortName) } ?? { "orbit %".format(i) }
		};
		if(SDGInsert.available(kind).not) {
			problems = problems.add("%'s % insert: its extension is not installed".format(where, kind));
			^nil
		};
		if(kind == \vst) {
			plugin = this.prPluginFor(d);
			if(plugin.isNil) {
				problems = problems.add("%'s plugin %: not found on this machine".format(where, d[\plugin]));
				^nil
			};
		};
		insert = if(track.notNil) {
			sdg.addTrackInsert(track, place, kind, nil, plugin)
		} {
			sdg.addInsert(i, kind, nil, plugin)
		};
		if(insert.isNil) {
			problems = problems.add("%'s % insert".format(where, kind));
			^nil
		};
		insert.prRestore(d, this.prReadState(d, dir, where));
		^insert
	}

	prReadState { |d, dir, where|
		var file = d[\state] !? { dir +/+ d[\state].asString.basename }, bytes;
		if(file.isNil) { ^nil };
		if(File.exists(file).not) {
			problems = problems.add("%'s plugin state: % is gone".format(where, file.basename));
			^nil
		};
		File.use(file, "rb", { |f|
			bytes = Int8Array.newClear(f.length);
			f.read(bytes);
		});
		^bytes
	}

	// until every insert has its synth and every plugin its state, at most thirty seconds
	prWaitForInserts {
		var waited = 0, late;
		while {
			late = this.prEveryInsert.reject(_.isReady);
			late.notEmpty and: { waited < 30 }
		} {
			0.05.wait;
			waited = waited + 0.05;
		};
		late.do { |insert| problems = problems.add("% on %: did not start in time".format(insert.shortLabel, insert.where)) };
	}
}
