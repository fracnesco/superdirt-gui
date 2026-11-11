// =====================================================================
// SDGInsert - an insert effect on one orbit, the way a channel of a desk
// has inserts: an eq (ParaEQ), a compressor (Compressor) or a meter
// (Insight), in a chain of its own before the orbit's fader, in an order
// you choose, as many as you like.
//
//   i = ~sdg.addInsert(0, \eq);        // orbit 0, after its other inserts
//   i.gui;                             // the eq's own window
//   i.unit.preset = \warm;             // the ParaEQ itself
//   i.enabled = false;                 // bypassed
//   c = ~sdg.addInsert(0, \compressor, 0);   // in front of the eq
//   ~sdg.moveInsert(c, 1);             // behind it again
//   ~sdg.removeInsert(i);
//
// Each insert is a GlobalDirtEffect of SuperDirt's own kind, in the orbit's
// chain after SuperDirt's delay, reverb and leslie and before the add-on's
// fader - pre-fader, as on a desk - so SuperDirt keeps it in its place
// through every rebuild of the orbit, cmd-period included. Its synth hears
// the orbit's two busses as one: dry and effect summed, processed, written
// to the dry bus with the effect bus cleared - the sum SuperDirt's monitor
// would have made anyway. Taking one out fades the two busses back.
//
// The DSP is the extension's own pseudo-UGen, ParaEQ.ar or Compressor.ar,
// and the object that drives it is the extension's own object in driver
// mode, so the window, the presets and the clipboard are theirs. What the
// object pushes lands in the effect's state as well as on its synth, so a
// synth started again begins with the settings the last one had. An
// insight only reads: its insert copies the orbit's signal, at that point
// of the chain, to a bus of its own that an Insight reads.
//
// A VST plugin is the fourth kind (the VSTPlugin extension), and the one
// exception: a plugin lives inside the synth that hosts it, and SuperDirt
// replaces an orbit's effect synths whenever it rebuilds the orbit - which
// would close the plugin and lose what was set in it. So a VST insert is a
// synth of its own, outside SuperDirt's list, that SuperDirtGUI walks back
// into place after every rebuild (orderInserts). It never sleeps: a plugin
// may make sound from silence, and nothing would wake it. Only a cmd-period
// frees it; then it is made again and the plugin reopened with the state it
// last read back, which it does a moment after anything is changed in the
// plugin's own editor (rememberState).
//
//   v = ~sdg.addInsert(0, \vst, plugin: "ValhallaSupermassive");
//   v.gui;                             // the plugin's own editor
//   v.controller;                      // its VSTPluginController
//
// An insert can also sit on a track (SDGTrack, SuperDirtGUI:addTrackInsert):
// before a global effect or after it, the same object on the track's own bus.
// Or on a return, a group or the master (SDGBusTrack), in its one chain: it has
// no orbit then, and no event wakes it, so the bus track does.
//
// The four extensions are looked up by name, never compiled against: a
// kind whose extension is not installed is not offered.
//
// So are the effects built on FxParamUnit (sc fx kit: Growl, Tunnel, Smear,
// Cloud, Stammer, Sympathy, Spiral), which need not be named at all: each is a
// kind by being a subclass of it. Their synths stay awake while their output
// sounds, so a delay's tail outlives DirtPause's grace.
// =====================================================================

SDGInsert {

	classvar <kinds;			// kind -> (label:, className:, controlName:)
	classvar fxKinds, reverbKinds;
	classvar counter = 0;

	var <sdg, <orbitIndex, <kind;
	var <fx, <unit, <insight, <tapBus, <number, <label;
	var <plugin, <synth, <controller, <stateData, bypassed = false, changes = 0;
	var <track, <place;		// a track's insert: its SDGTrack, and \pre or \post its effect
	var opening = false;	// a VST insert: from its synth starting until the plugin has its state

	*initClass {
		kinds = IdentityDictionary[
			\eq -> (label: "EQ", className: \ParaEQ, controlName: \eq, category: \filter),
			\compressor -> (label: "Comp", className: \Compressor, controlName: \comp, category: \dynamics),
			\insight -> (label: "Insight", className: \Insight, category: \utility),
			\vst -> (label: "VST", className: \VSTPluginController, category: \plugin)
		];
		// the effects built on FxParamUnit (sc fx kit), each an extension of its own:
		// whichever are installed are kinds too, found here rather than named. They
		// have tails, so the insert stays awake for as long as its output sounds
		// the reverbs (sc reverbs: Hall, Plate, Coil, Shimmer) are told apart by their
		// display, ReverbView, so the menu can list them under "reverb"
		fxKinds = [];
		reverbKinds = [];
		\FxParamUnit.asClass !? { |base|
			base.allSubclasses.sort { |a, b| a.name < b.name }.do { |cls|
				var display = cls.displayClass;
				var isReverb = display.notNil and: { display.name == \ReverbView };
				kinds[cls.insertKind] = (label: cls.insertLabel, className: cls.name,
					controlName: cls.controlName, tail: true,
					category: this.prUnitCategory(cls, isReverb));
				if(isReverb) {
					reverbKinds = reverbKinds.add(cls.insertKind)
				} {
					fxKinds = fxKinds.add(cls.insertKind)
				};
			};
		};
	}

	// the kinds in the order a menu lists them
	*kindNames { ^#[\eq, \compressor, \insight, \vst] ++ fxKinds ++ reverbKinds }

	// the kinds that come from sc fx kit (FxParamUnit) but are not reverbs, in menu order
	*fxKindNames { ^fxKinds.copy }

	// the reverb kinds (sc reverbs), in menu order
	*reverbKindNames { ^reverbKinds.copy }

	*kindLabel { |kind| ^kinds[kind.asSymbol] !? { |k| k[\label] } }

	// the kind's heading in the add menus, one of SuperDirtGUI.effectCategories
	*kindCategory { |kind| ^SuperDirtGUI.effectCategory(kinds[kind.asSymbol] !? { |k| k[\category] }) }

	// an FxParamUnit's category: its own *insertCategory if it has one, else the
	// ones known here, a ReverbView display, or \other
	*prUnitCategory { |cls, isReverb|
		var known = IdentityDictionary[
			\growl -> \distortion, \tunnel -> \delay, \smear -> \spectral, \cloud -> \granular,
			\stammer -> \repeat, \sympathy -> \resonator, \spiral -> \pitch
		];
		^(if(cls.class.findRespondingMethodFor(\insertCategory).notNil) { cls.insertCategory })
			?? { known[cls.insertKind] }
			?? { if(isReverb) { \reverb } }
			? \other
	}

	// whether the extension a kind needs is installed
	*available { |kind|
		^(kinds[kind.asSymbol] !? { |k| k[\className].asClass.notNil }) ? false
	}

	*availableKinds { ^this.kindNames.select { |kind| this.available(kind) } }

	// the GlobalDirtEffect's name; its SynthDef adds the channel count
	*effectName { |kind| ^("sdgins_" ++ kind).asSymbol }

	*isInsertName { |name| ^name.asString.beginsWith("sdgins_") }

	// The insert's synth for a channel count. An eq or a compressor: the two
	// busses summed, through the DSP, onto the dry bus, the effect bus
	// cleared - all of it faded by the gate, so a release crossfades the
	// busses back to what they were. An insight: the sum copied to tapBus,
	// nothing touched. nil when the extension is not installed.
	*makeDef { |kind, numChannels|
		var n = numChannels.asInteger, spec = kinds[kind.asSymbol], cls;
		cls = spec !? { spec[\className].asClass };
		if(cls.isNil) { ^nil };
		// a VST plugin's host: no DirtPause, it runs for as long as the insert
		// does; bypass 2 is VSTPlugin's soft bypass, a crossfade that lets a
		// tail ring out
		if(kind == \vst) {
			^SynthDef(this.effectName(kind) ++ n, { |dryBus, effectBus, gate = 1, bypass = 0|
				var dry = In.ar(dryBus, n), wet = In.ar(effectBus, n);
				var sig = dry + wet;
				var env = EnvGen.kr(Env.asr(0.05, 1, 0.1), gate, doneAction: 2);
				var out = \VSTPlugin.asClass.ar(sig, n, bypass, id: \vst);
				ReplaceOut.ar(dryBus, (out * env) + (dry * (1 - env)));
				ReplaceOut.ar(effectBus, wet * (1 - env));
			}, [\ir, \ir])
		};
		if(kind == \insight) {
			^SynthDef(this.effectName(kind) ++ n, { |dryBus, effectBus, gate = 1, tapBus = 0|
				var sig = In.ar(dryBus, n) + In.ar(effectBus, n);
				EnvGen.kr(Env.asr(0, 1, 0), gate, doneAction: 2);
				DirtPause.ar(sig, graceTime: 4);
				Out.ar(tapBus, sig);
			}, [\ir, \ir])
		};
		^SynthDef(this.effectName(kind) ++ n, { |dryBus, effectBus, gate = 1, scBus = 0|
			var dry = In.ar(dryBus, n), wet = In.ar(effectBus, n);
			var sig = dry + wet;
			var env = EnvGen.kr(Env.asr(0.02, 1, 0.02), gate, doneAction: 2);
			var out = case
			{ kind == \compressor } { cls.ar(sig, spec[\controlName], InFeedback.ar(scBus, n)) }
			// the eq tells its window its levels in and out while one is open
			// (ParaEQ's meter: silent until asked, the sound untouched)
			{ kind == \eq } { cls.ar(sig, spec[\controlName], meter: true) }
			{ true } { cls.ar(sig, spec[\controlName]) };
			DirtPause.ar(if(spec[\tail] == true) { out } { sig }, graceTime: 4);
			ReplaceOut.ar(dryBus, (out * env) + (dry * (1 - env)));
			ReplaceOut.ar(effectBus, wet * (1 - env));
		}, [\ir, \ir])
	}

	// made by SuperDirtGUI:addInsert, which puts it into the orbit. plugin is
	// a VST insert's plugin: a name VSTPlugin.search found, or a file path
	*new { |sdg, orbitIndex, kind, label, plugin, track, place|
		^super.newCopyArgs(sdg, orbitIndex, kind.asSymbol).init(label, plugin, track, place)
	}

	init { |argLabel, argPlugin, argTrack, argPlace|
		var spec = kinds[kind], cls = spec !? { spec[\className].asClass };
		if(cls.isNil) { Error("SDGInsert: % is not installed".format(spec !? { spec[\className] } ? kind)).throw };
		counter = counter + 1;
		number = counter;
		track = argTrack;
		place = argPlace;
		label = argLabel ?? { this.where };
		if(kind == \vst) {
			if(argPlugin.isNil) { Error("SDGInsert: a VST insert needs a plugin").throw };
			plugin = argPlugin.asString;
			^this
		};
		fx = GlobalDirtEffect(this.class.effectName(kind), []).alwaysRun_(true);
		fx.numChannels = this.numChannels;
		// on a track, in the state: GlobalDirtEffect:play sends the orbit's busses first
		if(track.notNil) {
			fx.state[\dryBus] = this.dryBus.index;
			fx.state[\effectBus] = this.wetBus.index;
		};
		if(kind == \insight) {
			tapBus = Bus.audio(this.server, this.numChannels);
			fx.state[\tapBus] = tapBus.index;
			insight = cls.new(("%_insight%".format(this.where.replace(" ", ""), number)).asSymbol,
				tapBus.index, this.numChannels, this.server);
		} {
			unit = cls.new(SDGInsertTarget(this), spec[\controlName]);
			unit.label = label;
			unit.addDependant(this);
		};
	}

	server { ^sdg.dirt.server }
	numChannels { ^sdg.dirt.numChannels }
	orbit { ^orbitIndex !? { sdg.dirt.orbits[orbitIndex] } }

	// an insert of a return, a group or the master has no orbit
	isOnBus { ^track.isKindOf(SDGBusTrack) }

	// what a message calls where it is
	where { ^if(this.isOnBus) { track.where } { "orbit %".format(orbitIndex) } }

	// the busses its synth hears as dry and effect: the orbit's two - or on a
	// track, the track's bus at its place, and one nothing writes to
	dryBus { ^if(track.notNil) { track.busAt(place) } { this.orbit.dryBus } }
	wetBus { ^if(track.notNil) { sdg.silentBus } { this.orbit.globalEffectBus } }

	isVST { ^kind == \vst }

	// the node on the server, where the chain is ordered: the effect's synth,
	// or a VST insert's own
	node { ^if(fx.notNil) { fx.synth } { synth } }

	// "EQ", "Comp", "Insight" - or the plugin's name
	shortLabel {
		^if(this.isVST) { PathName(plugin).fileNameWithoutExtension } { kinds[kind][\label] }
	}

	// whether it can be bypassed: an insight only reads, there is nothing to bypass
	canBypass { ^unit.notNil or: { this.isVST } }

	// bypassed is not enabled: the DSP crossfades to the signal it was given
	enabled { ^if(this.isVST) { bypassed.not } { unit.isNil or: { unit.enabled } } }

	enabled_ { |bool|
		unit !? { unit.enabled = bool };
		if(this.isVST) {
			bypassed = bool.not;
			synth !? { synth.set(\bypass, if(bypassed) { 2 } { 0 }) };
			sdg.changed(\inserts, orbitIndex);
		}
	}

	// its window: the eq's, the compressor's, the insight's - a plugin's own
	// editor when it has one, VSTPlugin's generic one when it has not
	gui { |x, y|
		if(this.isVST) {
			if(controller.isNil or: { controller.isOpen.not }) {
				"SuperDirtGUI: % is not open (yet)".format(plugin).warn;
				^nil
			};
			^if(controller.info !? { |info| info.editor } ? false) {
				controller.editor(true)
			} {
				controller.gui
			}
		};
		^(unit ? insight).gui(x, y)
	}

	// a VST insert's parameters as sliders, VSTPlugin's generic window
	genericGui {
		^controller !? { |c| if(c.isOpen) { c.gui } }
	}

	// ----- a VST insert's synth and plugin

	// Its synth, where target and addAction say (SuperDirtGUI orders it into
	// the chain afterwards), and the plugin opened in it - with the state read
	// back last, when there is one: after a cmd-period, the plugin as it was.
	prStart { |target, addAction = \addAfter|
		var orbit = this.orbit;
		synth = Synth(this.class.effectName(kind) ++ this.numChannels, [
			\dryBus, this.dryBus, \effectBus, this.wetBus,
			\bypass, if(bypassed) { 2 } { 0 }
		], target, addAction);
		controller = \VSTPluginController.asClass.new(synth, \vst);
		controller.parameterAutomated = { this.prChanged };
		opening = true;
		controller.open(plugin, editor: true, action: { |ctl, ok|
			if(ok) {
				if(stateData.notNil) {
					ctl.setProgramData(stateData, { opening = false })
				} {
					opening = false
				}
			} {
				opening = false;
				"SuperDirtGUI: could not open the VST plugin % on %".format(plugin, this.where).warn
			};
			sdg.changed(\inserts, orbitIndex);
		});
	}

	// whether its synth has been started and a plugin has what it was given
	isReady { ^this.node.notNil and: { opening.not } }

	// something moved in the plugin's editor: its state is read back once the
	// moving has stopped for a second and a half
	prChanged {
		var mine;
		changes = changes + 1;
		mine = changes;
		SystemClock.sched(1.5, { if(mine == changes) { this.rememberState }; nil });
	}

	// Read the plugin's state back now and keep it, to open it with again after
	// a cmd-period. Happens on its own after every change in the editor; the
	// window's menu asks for it too. action gets the data, or nil.
	rememberState { |action|
		if(controller.isNil or: { controller.isOpen.not }) { ^action.value(nil) };
		controller.getProgramData({ |data|
			data !? { stateData = data };
			action.value(data);
		});
	}

	// ----- in a session (SDGSession)

	// what an Insight shows and aims at, each with a setter of the same name
	*insightKeys { ^#[\fftSize, \source, \imagerFrames, \tilt, \smoothing, \hold, \spectrumFloor, \target, \ceiling] }

	// All of its settings as an Event a session can write: the object's own, where
	// a compressor's sidechain comes from - by BusManager name when it has one -
	// which its settings leave out; a plugin's name, file and last state read back
	sessionData {
		var data = (kind: kind, enabled: this.enabled);
		unit !? {
			data[\settings] = unit.settings;
			(unit.tryPerform(\sidechainName) ?? { unit.tryPerform(\sidechain) }) !? { |source| data[\sidechain] = source };
		};
		insight !? {
			data[\insight] = ();
			this.class.insightKeys.do { |key| data[\insight][key] = insight.perform(key) };
		};
		if(this.isVST) {
			data[\plugin] = plugin;
			(controller !? { controller.info } !? { |info| info.path }) !? { |path| data[\path] = path };
			stateData !? { data[\state] = stateData };
		};
		^data
	}

	// sessionData back, with a plugin's state as its bytes. On an insert just added
	// nothing has started yet: the settings land in the effect's state, the state
	// waits for the plugin to open (prStart)
	prRestore { |data, bytes|
		bytes !? { stateData = bytes };
		unit !? {
			data[\settings] !? { |settings| unit.settings = settings };
			data[\sidechain] !? { |source| unit.tryPerform(\sidechain_, source) };
		};
		insight !? {
			data[\insight] !? { |values|
				this.class.insightKeys.do { |key|
					values[key] !? { |value| insight.perform(key.asSetter, value) }
				}
			}
		};
		if(this.canBypass) { data[\enabled] !? { |on| this.enabled = on } };
	}

	// what the object pushes: into the effect's state, so a synth started
	// again by SuperDirt gets it, and to the synth running now
	prSet { |pairs|
		pairs.pairsDo { |key, value| fx.state[key] = value };
		fx.synth !? { |syn| syn.set(*pairs) };
	}

	// the object changed: a bypass is news for the window, a sidechain is
	// a bus this synth has to read, which a driven graph wires itself
	update { |obj, what|
		if(obj === unit) {
			if(what == \sidechain) { this.prSet([\scBus, unit.tryPerform(\sidechain) ? 0]) };
			if(what == \enabled) { sdg.changed(\inserts, orbitIndex) };
		}
	}

	// the object, its window, the insight and its bus. SuperDirtGUI:removeInsert
	// takes the effect out of the orbit first
	free {
		unit !? { unit.removeDependant(this); unit.free };
		insight !? { insight.free };
		tapBus !? { tapBus.free };
		// a VST insert's synth fades out and frees itself, the plugin with it,
		// and its controller lets go when it does
		synth !? { synth.set(\gate, 0) };
		unit = nil;
		insight = nil;
		tapBus = nil;
		synth = nil;
		controller = nil;
	}

	printOn { |stream|
		stream << this.class.name << "(" << if(this.isVST) { plugin.asCompileString } { kind }
			<< ", " << this.where << ", " << if(this.enabled) { "on" } { "bypassed" } << ")"
	}
}


// What an insert's ParaEQ or Compressor drives: it answers `set` as a node
// does, and hands the settings to the insert (SDGInsert:prSet). For the
// eq's spectrum it says where to read: the insert's bus, at the tail of
// SuperDirt's group - the orbit after its inserts and fader, or a track's bus
// after its inserts - or at the tail of the returns' group, on a return.
SDGInsertTarget {
	var <insert;

	*new { |insert| ^super.newCopyArgs(insert) }

	set { |...pairs| insert.prSet(pairs) }

	server { ^insert.server }
	numChannels { ^insert.numChannels }
	bus { ^insert.dryBus }
	group {
		^if(insert.isOnBus) { insert.sdg.returnGroup } { Group.basicNew(insert.server, insert.sdg.dirt.group) }
	}

	// what the eq's and the compressor's windows say they are driving
	printOn { |stream|
		stream << if(insert.isOnBus) {
			insert.where
		} {
			"orbit " ++ insert.orbitIndex ++ if(insert.track.notNil) {
				", " ++ insert.place ++ " " ++ insert.track.shortName
			} { ", before its fader" }
		}
	}
}
