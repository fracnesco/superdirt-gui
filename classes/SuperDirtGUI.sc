// ==== SuperDirtGUI ============================================================
//
// extra synths, per-event effect modules, global (per-orbit) effects and
// sound-library helpers for SuperDirt, loaded into a running instance. it adds
// to SuperDirt and never changes it, so it lives next to a normal install and
// updates on its own.
//
//   s.boot;
//   ~dirt = SuperDirt(2, s);
//   ~dirt.loadSoundFiles;
//   ~dirt.start(57120, 0 ! 12);
//   ~sdg = SuperDirtGUI(~dirt);          // reads synths/, modules/, effects/
//   ~sdg.gui;                            // what got loaded, reload, free
//
//   ~sdg.synthNames;                     // -> [ sdgsine ]         d1 $ s "sdgsine"
//   ~sdg.moduleNames;                    // -> [ sdgcomb ]         # pF "sdgcomb" 0.4
//   ~sdg.globalEffectNames;              // -> [ global_sdgspace ] # pF "sdgspace" 0.5
//   ~sdg.reload;                         // re-read the files after editing them
//   ~sdg.loadSamples("~/more-samples");  // extra banks, appended to ~dirt's library
//   ~sdg.free;
//
// content is plain .scd files in three folders next to classes/, written the
// way SuperDirt's own synths/ folder is. every file sees ~dirt, and ~sdg:
//
//   synths/    one SynthDef per tidal sound, SuperDirt's argument names
//   modules/   ~dirt.addModule(name, func, test) plus its SynthDef,
//              named "<name>" ++ ~dirt.numChannels
//   effects/   SynthDef("global_<name>" ++ ~dirt.numChannels), then
//              ~sdg.addGlobalEffect(\global_<name>, [\param, ...])
//
// the example file in each folder is a template: copy it, rename it, edit it.

SuperDirtGUI {

	classvar <current;

	var <dirt, <path;
	var <synthNames, <moduleNames, <globalEffectSpecs;
	var <window, <>verbose = true;
	var controller;

	*new { |dirt|
		if(dirt.isKindOf(SuperDirt).not) {
			Error("SuperDirtGUI: pass a SuperDirt instance, e.g. SuperDirtGUI(~dirt)").throw
		};
		^super.newCopyArgs(dirt).init
	}

	init {
		// <repo>/classes/SuperDirtGUI.sc -> <repo>, through a symlink or a quark install alike
		path = this.class.filenameSymbol.asString.dirname.dirname;
		synthNames = [];
		moduleNames = [];
		globalEffectSpecs = [];
		current = this;
		this.prCheckSuperDirtVersion;
		this.load;
	}

	// ----- folders

	synthDir { ^path +/+ "synths" }
	moduleDir { ^path +/+ "modules" }
	effectDir { ^path +/+ "effects" }

	// ----- loading

	load {
		this.loadSynths;
		this.loadModules;
		this.loadGlobalEffects;
		this.changed(\loaded);
	}

	// take our modules and global effects out, then read every file again
	reload {
		this.prRemoveModules;
		this.prRemoveGlobalEffects;
		this.load;
	}

	loadSynths {
		var before = this.prSynthDescNames;
		this.prLoadFolder(this.synthDir);
		synthNames = this.prUnion(synthNames, this.prSynthDescNames.difference(before));
	}

	loadModules {
		var before = this.prModuleNames;
		this.prLoadFolder(this.moduleDir);
		moduleNames = this.prUnion(moduleNames, this.prModuleNames.difference(before));
	}

	loadGlobalEffects {
		globalEffectSpecs = [];
		this.prLoadFolder(this.effectDir); // the files call addGlobalEffect
		this.prWhenOrbitsExist { this.applyGlobalEffects };
	}

	// ----- global effects

	// called from effects/*.scd: register a SynthDef name and the event keys it listens to
	addGlobalEffect { |name, paramNames, alwaysRun = false|
		name = name.asSymbol;
		globalEffectSpecs = globalEffectSpecs.reject { |spec| spec[\name] == name };
		globalEffectSpecs = globalEffectSpecs.add(
			(name: name, paramNames: paramNames.asArray, alwaysRun: alwaysRun)
		);
	}

	globalEffectNames { ^globalEffectSpecs.collect { |spec| spec[\name] } }

	// every orbit gets its own instance of each registered effect, first in the
	// chain. safe to call twice: an orbit that already has one is left alone.
	// SynthDef.add is asynchronous, so the server is synced before initNodeTree
	// sends its /s_new - otherwise the effect silently never starts.
	applyGlobalEffects {
		if(dirt.orbits.isNil) {
			"SuperDirtGUI: no orbits yet - call ~dirt.start first, then applyGlobalEffects".warn;
			^this
		};
		fork { dirt.server.sync; this.prApplyGlobalEffects };
	}

	prApplyGlobalEffects {
		dirt.orbits.do { |orbit|
			var added = false;
			globalEffectSpecs.do { |spec|
				if(orbit.globalEffects.any { |fx| fx.name == spec[\name] }.not) {
					orbit.globalEffects = orbit.globalEffects.addFirst(
						GlobalDirtEffect(spec[\name], spec[\paramNames]).alwaysRun_(spec[\alwaysRun])
					);
					added = true;
				}
			};
			if(added) { orbit.initNodeTree };
		};
	}

	// ----- samples

	// append a folder of sample banks to ~dirt's library, one bank per subfolder,
	// the way Dirt-Samples is laid out. goes straight to the sound library because
	// SuperDirt:loadSoundFiles drops the appendToExisting flag on the way through.
	loadSamples { |folderPath|
		dirt.soundLibrary.loadSoundFiles(folderPath.standardizePath +/+ "*", true)
	}

	// ----- gui

	gui {
		var lists, refresh;
		if(window.notNil and: { window.isClosed.not }) { window.front; ^window };
		window = Window("SuperDirtGUI", Rect(120, 120, 560, 380));
		lists = 3.collect { ListView().selectionMode_(\none) };
		refresh = {
			lists[0].items = synthNames.collect(_.asString);
			lists[1].items = moduleNames.collect(_.asString);
			lists[2].items = this.globalEffectNames.collect(_.asString);
		};
		window.layout = VLayout(
			HLayout(
				VLayout(StaticText().string_("synths"), lists[0]),
				VLayout(StaticText().string_("modules"), lists[1]),
				VLayout(StaticText().string_("global effects"), lists[2])
			),
			HLayout(
				Button().states_([["reload"]]).action_({ this.reload }),
				Button().states_([["free"]]).action_({ this.free }),
				nil
			)
		);
		controller = SimpleController(this).put(\loaded, { { refresh.value }.defer });
		window.onClose = { controller.remove; controller = nil; window = nil };
		refresh.value;
		window.front;
		^window
	}

	// ----- teardown

	// take our modules and global effects out of ~dirt and close the window.
	// synthdefs stay on the server; they do no harm and tidal may still be using them
	free {
		this.prRemoveModules;
		this.prRemoveGlobalEffects;
		synthNames = [];
		moduleNames = [];
		globalEffectSpecs = [];
		if(window.notNil) { { window.close }.defer };
		if(current === this) { current = nil };
		this.changed(\loaded);
	}

	printOn { |stream|
		stream << this.class.name << "(" << synthNames.size << " synths, "
			<< moduleNames.size << " modules, " << globalEffectSpecs.size << " global effects)"
	}

	// ----- private

	prLoadFolder { |dir|
		var files = (dir +/+ "*.scd").standardizePath.pathMatch.sort;
		if(files.isEmpty) {
			if(verbose) { "SuperDirtGUI: nothing to load in %".format(dir).postln };
			^this
		};
		files.do { |file|
			try {
				(dirt: dirt, sdg: this).use { file.load };
				if(verbose) { "SuperDirtGUI: loaded %".format(file.basename).postln };
			} { |err|
				"SuperDirtGUI: % failed to load - %".format(file.basename, err.errorString).warn;
			}
		}
	}

	prSynthDescNames { ^SynthDescLib.global.synthDescs.keys.asArray }

	prModuleNames { ^dirt.modules.collect { |module| module.name } }

	// keeps the order things were loaded in
	prUnion { |old, new| ^old ++ new.reject { |name| old.includes(name) } }

	prRemoveModules {
		moduleNames.do { |name| dirt.removeModule(name) };
	}

	prRemoveGlobalEffects {
		var names = this.globalEffectNames;
		if(names.isEmpty or: { dirt.orbits.isNil }) { ^this };
		dirt.orbits.do { |orbit|
			var mine = orbit.globalEffects.select { |fx| names.includes(fx.name) };
			if(mine.notEmpty) {
				mine.do(_.release);
				orbit.globalEffects = orbit.globalEffects.reject { |fx| names.includes(fx.name) };
				orbit.initNodeTree;
			}
		}
	}

	// orbits only exist after ~dirt.start; if we were made before that, wait for them
	prWhenOrbitsExist { |func|
		if(dirt.orbits.notNil) { ^func.value };
		if(verbose) { "SuperDirtGUI: global effects will be added once ~dirt.start has made the orbits".postln };
		fork {
			var waited = 0;
			while { dirt.orbits.isNil and: { waited < 120 } } { 0.25.wait; waited = waited + 0.25 };
			if(dirt.orbits.notNil) { func.value } {
				"SuperDirtGUI: gave up waiting for orbits - run applyGlobalEffects after ~dirt.start".warn
			}
		}
	}

	prCheckSuperDirtVersion {
		if(SuperDirt.findMethod(\loadSoundFilesToBank).isNil) {
			("SuperDirtGUI: this SuperDirt predates October 2025. To refresh it:"
				"\n  Quarks.uninstall(\"SuperDirt\"); Quarks.folder.openOS;  // move the SuperDirt folder to the trash"
				"\n  Quarks.install(\"SuperDirt\"); thisProcess.recompile;"
				"\n  never Quarks.update(\"SuperDirt\"): it checks out a 2021 branch.").warn
		}
	}
}
