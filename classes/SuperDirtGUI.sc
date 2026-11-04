// ==== SuperDirtGUI ============================================================
//
// extra synths, per-event effect modules, sample players, global (per-orbit)
// effects, a control window and a sample editor for SuperDirt, loaded into a
// running instance. it adds to SuperDirt and never changes it, so it lives
// next to a normal install and updates on its own.
//
//   s.boot;
//   ~dirt = SuperDirt(2, s);
//   ~dirt.loadSoundFiles;
//   ~dirt.start(57120, 0 ! 12);
//   ~sdg = SuperDirtGUI(~dirt);          // reads synths/, modules/, players/, effects/
//   ~sdg.gui;                            // orbit strips, effects, the sound explorer
//
//   ~sdg.synthNames;                     // -> [ sdgsine ]         d1 $ s "sdgsine"
//   ~sdg.moduleNames;                    // -> [ sdgcomb ]         # pF "sdgcomb" 0.4
//   ~sdg.samplePlayerNames;              // -> [ sdgwarp ]         # pF "sdgwarp" 1
//   ~sdg.globalEffectNames;              // -> [ global_sdgspace ] # pF "sdgspace" 0.5
//   ~sdg.reload;                         // re-read the files after editing them
//   ~sdg.loadSamples("~/more-samples");  // extra banks, appended to ~dirt's library
//   ~sdg.free;
//
//   ~sdg.newFile(\players, \mywarp);     // players/mywarp.scd: the example, renamed, loaded
//   ~sdg.sourceFile(\players, \mywarp);  // the file a name came from
//   ~sdg.loadFile("players/mywarp.scd"); // read one file again after editing it
//
//   i = ~sdg.addInsert(0, \eq);          // an eq insert on orbit 0, before its fader
//   ~sdg.addInsert(0, \compressor);      // after it; \insight for a meter
//   ~sdg.moveInsert(i, 1);               // the eq after the compressor
//   i.gui; ~sdg.removeInsert(i);         // see SDGInsert
//
//   t = ~sdg.tracksOf(0).first;          // orbit 0's first global effect, as a track
//   t.level = 0.5;                       // its own fader
//   ~sdg.setTrackEffect(t, \dirt_reverb); // another effect on that track
//   ~sdg.addTrackInsert(t, \pre, \eq);    // an insert before it; \post after it
//
//   a = ~sdg.addReturn;                  // return A, a send knob on every orbit (SDGReturn)
//   ~sdg.setReturnEffect(a, \dirt_reverb);
//   ~sdg.setSend(0, a, 0.5);             // orbit 0 into it
//   ~sdg.addTrackInsert(a, \post, \eq);  // inserts and VSTs, as on a track
//   ~sdg.returnFeedback = true;          // returns may send to returns, and so loop
//   ~sdg.setReturnSend(a, a, 0.5);       // here, into itself
//
//   ~sdg.saveSession("gig");             // the whole mixer, VST states and all (SDGSession)
//   ~sdg.loadSession("gig");             // and back
//
//   ~sdg.tags.analyseLibrary;            // tags, bpm and key for every sample (SDGSampleTags)
//   ~sdg.tags.query([\percussive, \short], [\melodic]);   // [[\bd, 3] ...]
//   // and from tidal, a sample picked by its tags on every event:
//   // d1 $ sound "sdgany*8" # pS "sdgtag" "percussive.short" # pS "sdgnot" "melodic"
//
// content is plain .scd files in four folders next to classes/, written the
// way SuperDirt's own synths/ folder is. every file sees ~dirt, and ~sdg:
//
//   synths/    one SynthDef per tidal sound, SuperDirt's argument names
//   modules/   ~dirt.addModule(name, func, test) plus its SynthDef,
//              named "<name>" ++ ~dirt.numChannels
//   players/   ~sdg.addSamplePlayer(name, synthFunc, test): another way of
//              reading a sample, chosen per event by the parameter it is
//              named after (see SDGSamplePlayer)
//   effects/   SynthDef("global_<name>" ++ ~dirt.numChannels), then
//              ~sdg.addGlobalEffect(\global_<name>, [\param, ...]) for every
//              orbit, or ~sdg.offerGlobalEffect for the window's add menu only.
//              a line "// requires: SomeUGen" skips the file without that class
//
// the example file in each folder is a template: newFile copies it (or any
// file of the folder) under a new name - so does new... in the window's
// modules tab, which opens the copy in the IDE.
//
// every file is remembered with what it defined, so one file can be read
// again on its own (loadFile): what it no longer defines is taken out, and a
// global effect it defines starts again on its new SynthDef while the rest of
// the orbit's chain plays on. a file that does not compile or throws leaves
// what it loaded before playing. while the window is open, a file saved in
// any editor is read again within a second (reloadChangedFiles).
//
// what the class adds on top of the files, all through SuperDirt's public
// surface (dirt.modules, orbit.globalEffects, the sound library):
//
//   - a sample-player module, put in front of SuperDirt's own 'sound' module.
//     when a sample event asks for one of the players it sends the player's
//     synth and marks the event diverted, so 'sound' leaves it alone. events
//     that ask for no player never touch it.
//   - one global_sdgfader per orbit, last before SuperDirt's rms and monitor:
//     the orbit's output level, mute and solo (setFader, setMute, setSolo).
//     It also writes the orbit as it leaves the fader to a bus of its own:
//     what the recorder reads (tapBusOf, recorder).
//   - inserts per orbit, before the fader: an eq, a compressor or an insight,
//     each a global effect of SuperDirt's kind driven by the ParaEQ,
//     Compressor or Insight extension (SDGInsert), as many as you like.
//   - every global effect of an orbit as a track (SDGTrack): inserts before
//     and after the effect, a fader on what it adds, another effect loaded in.
//   - return tracks with a send from every orbit, as in Ableton (SDGReturn):
//     their own group after SuperDirt's, inserts, an effect, sends to each other.
//   - reloadSample / addSample, what the sample editor calls after a save so
//     tidal plays the new file under the same name and number.
//   - sessions: the whole mixer saved under a name and brought back (SDGSession).
//   - a tag picker in every orbit's default event (its diversion, which
//     SuperDirt calls before it looks the sound up): an event with sdgtag,
//     sdgnot, sdgbpm or sdgkey has its s and n replaced by a sample of the
//     library that matches (SDGSampleTags), before anything else reads them.
//   - changed notifications for the window: \loaded, \samples, \reloaded, \mixer,
//     \returns (one came, went or changed), \session (one was loaded), \sessions
//     (one was saved).

SuperDirtGUI {

	classvar <current;
	classvar <>faderEffectName = \global_sdgfader;
	classvar <>playerModuleName = \sdgplayer;
	classvar <>anySoundName = \sdgany;	// the sound that means the whole library to the tag picker
	classvar tapBusses;		// DirtOrbit -> Bus: per orbit, not per loader, as the fader outlives a second loader

	var <dirt, <path;
	var <globalEffectSpecs, <samplePlayers;
	var <faderValues, <muteStates, <soloStates;
	var <>verbose = true;
	var controlWindow, appliedAmps, warnedInstruments;
	var fileRecords;		// path -> (kind:, names:, hash:, failed:), one per file read
	var loadingNames;		// while a file runs: the players and effects it registers
	var inserts;			// orbit index -> [SDGInsert], in chain order
	var treeFunc;			// the ServerTree function that brings VST inserts back after a cmd-period
	var insertDefs;			// the insert kinds whose SynthDef has been added
	var tracks;				// orbit index -> [SDGTrack], in chain order
	var silentBus;			// what a track's inserts read as their effect bus: nothing writes to it
	var session;			// the SDGSession, made when first asked for
	var <returns;			// the return tracks (SDGReturn), by letter
	var <returnFeedback = false;	// whether returns may send to returns
	var returnGroupID, returnHeadsID;	// their nodes, made when the first return is
	var keepAwake;			// the routine that keeps what is on a return from sleeping
	var tags;				// the SDGSampleTags, made when first asked for
	var sync;				// the SDGSync, made when first asked for
	var pickers;			// DirtOrbit -> [our diversion, the one it replaced]
	var warnedPicker = false;

	*new { |dirt|
		if(dirt.isKindOf(SuperDirt).not) {
			Error("SuperDirtGUI: pass a SuperDirt instance, e.g. SuperDirtGUI(~dirt)").throw
		};
		^super.newCopyArgs(dirt).init
	}

	init {
		// <repo>/classes/SuperDirtGUI.sc -> <repo>, through a symlink or a quark install alike
		path = this.class.filenameSymbol.asString.dirname.dirname;
		globalEffectSpecs = [];
		samplePlayers = [];
		fileRecords = Dictionary.new;
		inserts = IdentityDictionary.new;
		tracks = IdentityDictionary.new;
		returns = [];
		insertDefs = IdentitySet.new;
		warnedInstruments = IdentitySet.new;
		current = this;
		this.prCheckSuperDirtVersion;
		this.load;
		pickers = IdentityDictionary.new;
		this.prWhenOrbitsExist { this.prInstallTagPicker };
	}

	// SuperDirt's own orbit plumbing: never listed as effects, never moved
	*plumbingEffectNames { ^[\dirt_rms, \dirt_monitor] }

	// ----- folders

	// the four kinds of content, each read from the folder of the same name
	*kinds { ^#[\synths, \modules, \players, \effects] }

	// what one of a kind is called in messages and in the window
	*kindLabel { |kind|
		^(synths: "synth", modules: "effect", players: "player", effects: "global effect")[kind.asSymbol]
	}

	// a global effect is named global_<parameter>: its name without the prefix.
	// any other name as it is. Both answer Strings
	*baseName { |kind, name|
		name = name.asString;
		^if(kind.asSymbol == \effects and: { name.beginsWith("global_") }) { name.drop(7) } { name }
	}

	// the name a kind registers under, from the name or (for an effect) its parameter
	*fullName { |kind, name|
		var base = this.baseName(kind, name);
		^if(kind.asSymbol == \effects) { ("global_" ++ base).asSymbol } { base.asSymbol }
	}

	dirFor { |kind| ^path +/+ kind.asString }
	synthDir { ^this.dirFor(\synths) }
	moduleDir { ^this.dirFor(\modules) }
	playerDir { ^this.dirFor(\players) }
	effectDir { ^this.dirFor(\effects) }

	// ----- loading

	load {
		this.loadSynths;
		this.loadModules;
		this.loadSamplePlayers;
		this.loadGlobalEffects;
		this.changed(\loaded);
	}

	// take our modules, players and global effects out, then read every file again
	reload {
		this.prRemoveModules;
		this.prRemoveSamplePlayers;
		this.prRemoveGlobalEffects(true);
		fileRecords = Dictionary.new;
		this.load;
		fork { dirt.server.sync; this.prRestartReturnEffects };
	}

	loadSynths { this.prLoadFolder(\synths) }

	loadModules { this.prLoadFolder(\modules) }

	loadSamplePlayers { this.prLoadFolder(\players) } // the files call addSamplePlayer

	loadGlobalEffects {
		globalEffectSpecs = [];
		this.prDefineFaderDef;
		SDGTrack.makeDefs(dirt.numChannels);
		SDGReturn.makeDefs(dirt.numChannels);
		this.prLoadFolder(\effects); // the files call addGlobalEffect
		this.prWhenOrbitsExist { this.applyGlobalEffects };
	}

	// Read one file of the four folders again - or for the first time - after it
	// was edited or made. What it defined and no longer does is taken out; a
	// global effect it defines starts again on its new SynthDef, see
	// prPlaceGlobalEffects. A file that does not compile or throws leaves what it
	// loaded before in place, and a file that is gone is taken out. filePath is
	// absolute or relative to the repository. Answers true when the file ran.
	loadFile { |filePath|
		var file = this.prOwnPath(filePath), kind, before, ok = false;
		if(file.isNil) {
			"SuperDirtGUI: % is not a file of synths/, modules/, players/ or effects/".format(filePath).warn;
			^false
		};
		kind = this.prKindOf(file);
		before = this.prNamesIn(file);
		if(File.exists(file)) {
			ok = this.prReadFile(file, kind);
			if(ok and: { kind == \effects }) { this.prPlaceGlobalEffects(this.prNamesIn(file), before) };
		} {
			this.prForgetFile(file);
		};
		this.changed(\loaded, kind, file);
		^ok
	}

	// every file of the four folders whose text is not what was read last - edited,
	// new, or gone - through loadFile. The window calls this once a second while it
	// is open, which is how a file saved in the IDE is heard. Answers the paths.
	reloadChangedFiles {
		var due = [];
		this.class.kinds.do { |kind|
			var onDisk = this.prFilesIn(kind);
			onDisk.do { |file|
				var record = fileRecords[file];
				if(record.isNil or: { record[\hash] != this.prHash(file) }) { due = due.add(file) }
			};
			this.prPathsOf(kind).do { |file|
				if(onDisk.includesEqual(file).not) { due = due.add(file) }
			};
		};
		due.do { |file| this.loadFile(file) };
		^due
	}

	// ----- files: where each name came from, and new ones

	// what synths/ and modules/ defined, in load order
	synthNames { ^this.prNamesFromFiles(\synths) }
	moduleNames { ^this.prNamesFromFiles(\modules) }

	// the names a kind has now: synthNames, moduleNames, samplePlayerNames or
	// globalEffectNames
	namesOf { |kind|
		^switch(kind.asSymbol,
			\synths, { this.synthNames },
			\modules, { this.moduleNames },
			\players, { this.samplePlayerNames },
			\effects, { this.globalEffectNames }
		) ? []
	}

	// the file a name came from, or nil for one registered from code. a global
	// effect by its name or its parameter: \global_sdgspace or \sdgspace
	sourceFile { |kind, name|
		name = this.class.fullName(kind, name);
		^this.prPathsOf(kind.asSymbol).detect { |file| fileRecords[file][\names].includes(name) }
	}

	// true when the file's last read failed - it did not compile, or threw - so
	// what plays is what it loaded before, if anything
	fileFailed { |filePath|
		var record = fileRecords[this.prOwnPath(filePath) ? filePath];
		^record.notNil and: { record[\failed] }
	}

	// what the window lists for a kind: an Event (name:, path:, failed:) per name,
	// path nil for one registered from code; then one per file that failed before
	// it defined anything, name nil, and missing: the classes its "// requires:"
	// line names that are not installed, if that is why
	entries { |kind|
		var out;
		kind = kind.asSymbol;
		out = this.namesOf(kind).collect { |name|
			var file = this.sourceFile(kind, name);
			(name: name, path: file, failed: file.notNil and: { fileRecords[file][\failed] })
		};
		this.prPathsOf(kind).do { |file|
			var record = fileRecords[file];
			if(record[\failed] and: { record[\names].isEmpty }) {
				out = out.add((name: nil, path: file, failed: true, missing: record[\missing]))
			}
		};
		^out
	}

	// What a content file says about itself, from the comments above its code: the
	// first paragraph without its "folder/file.scd - ", and the lines under
	// "// parameters:", each "//   name  range  what it does" with two spaces or
	// more between the columns. Answers (description:, params: [(name:, range:,
	// text:)], listed: true when the file has that section). Comments, so a file
	// that does not compile still has them, and new... copies them along.
	*fileNotes { |filePath|
		var lines, head, end, description, params = [], start;
		if(filePath.isNil or: { File.exists(filePath).not }) {
			^(description: "", params: [], listed: false)
		};
		lines = File.readAllString(filePath).replace("\r", "").split($\n);
		end = lines.detectIndex { |line| line.beginsWith("//").not } ? lines.size;
		head = lines.keep(end);
		description = head.keep(head.indexOfEqual("//") ? head.size).collect { |line, i|
			var cut = if(i == 0) { line.find(".scd - ") };
			if(cut.notNil) { line.drop(cut + 7) } { line.drop(2) }.stripWhiteSpace
		}.join(" ");
		start = head.indexOfEqual("// parameters:");
		start !? {
			head.drop(start + 1).do { |line|
				var cells;
				if(line.beginsWith("//   ").not) { ^(description: description, params: params, listed: true) };
				cells = line.drop(2).stripWhiteSpace.findRegexp("^(\\S+)\\s{2,}(.*?)(?:\\s{2,}(.*))?$");
				if(cells.size >= 3) {
					params = params.add((name: cells[1][1].asSymbol, range: cells[2][1],
						text: cells[3] !? { |c| c[1] } ? ""))
				}
			};
		};
		^(description: description, params: params, listed: start.notNil)
	}

	// fileNotes of the file a name came from. A file without a parameters section,
	// or a name registered from code, lists the controls of its SynthDef with
	// their defaults, as far as the SynthDef is there: range nil, default a number.
	notes { |kind, name|
		var notes = this.class.fileNotes(this.sourceFile(kind, name));
		if(notes[\listed].not) { notes[\params] = this.prControlsOf(kind.asSymbol, name) };
		^notes
	}

	// the controls of what a kind's name plays, without those every synth of that
	// kind has: (name:, range: nil, default:)
	prControlsOf { |kind, name|
		var chans = dirt.numChannels;
		var def = switch(kind,
			\modules, { name.asString ++ chans },
			\players, { "%_1_%".format(name, chans) },
			\effects, { name.asString ++ chans },
			{ name.asString }
		);
		var desc = SynthDescLib.global.at(def.asSymbol);
		var common = #[\out, \bufnum, \sustain, \begin, \end, \speed, \endSpeed, \freq, \pan,
			\accelerate, \span, \dryBus, \effectBus, \outBus, \gate, \orbitIndex, \dirt_resumed, \resumed, '?'];
		if(desc.isNil) { ^[] };
		^desc.controls.reject { |c| common.includes(c.name.asSymbol) }.collect { |c|
			(name: c.name.asSymbol, range: nil, default: c.defaultValue, text: "")
		}
	}

	// the name the folder's example file defines - what newFile copies unless it is
	// told otherwise - or else the first name that came from a file of the folder
	templateName { |kind|
		var files = this.prPathsOf(kind.asSymbol);
		var examples = files.select { |file| file.basename.beginsWith("example") };
		^(examples ++ files).collect { |file| fileRecords[file][\names] }.flatten.first
	}

	// Why a name will not do for a new file of a kind, as a String, or nil when it
	// will. It becomes an argument name and an event key, so it is an identifier;
	// and it must mean nothing yet - no file of that name in the folder, and none
	// of the words SuperDirt, tidal or this loader already read (prWordsInUse):
	// a module named speed would switch on with every event that has a speed.
	nameProblem { |kind, name|
		var base, taken, defs;
		kind = kind.asSymbol;
		base = this.class.baseName(kind, name ? "");
		if(base.isEmpty) { ^"no name yet" };
		if(base.first.isLower.not or: { base.any { |char| (char.isAlphaNum or: { char == $_ }).not } }) {
			^"a name is a lowercase letter, then letters, digits or _"
		};
		if(File.exists(this.dirFor(kind) +/+ base ++ ".scd")) {
			^"%/%.scd exists already".format(kind, base)
		};
		taken = this.prWordsInUse[base.asSymbol];
		taken !? { ^"'%' is taken - %".format(base, taken) };
		// the SynthDefs the file will build
		defs = switch(kind,
			\modules, { [base ++ dirt.numChannels] },
			\players, { (1..SuperDirt.maxSampleNumChannels).collect { |n| "%_%_%".format(base, n, dirt.numChannels) } },
			\effects, { ["global_" ++ base ++ dirt.numChannels] }
		) ? [];
		defs.do { |def|
			if(SynthDescLib.global.at(def.asSymbol).notNil) {
				^"'%' is taken - there is a SynthDef called %".format(base, def)
			}
		};
		^nil
	}

	// A new file in a kind's folder, made from one that is there: from's file,
	// with from's name - and the file's own name - replaced wherever it stands as
	// a word (global_sdgspace and sdgwarp_1_2 have sdgspace and sdgwarp in them,
	// sdgspacer does not), written to <kind>/<name>.scd and loaded. Parameters
	// other than the name keep theirs. from is a name of the same kind, the
	// example's by default. Answers the path, or nil with a warning when the name
	// will not do (nameProblem) or there is nothing to copy.
	newFile { |kind, name, from|
		var label, base, problem, fromName, source, fromBase, text, file;
		kind = kind.asSymbol;
		label = this.class.kindLabel(kind);
		base = this.class.baseName(kind, name ? "");
		problem = this.nameProblem(kind, base);
		if(problem.notNil) {
			"SuperDirtGUI: no new % - %".format(label, problem).warn;
			^nil
		};
		fromName = if(from.notNil) { this.class.fullName(kind, from) } { this.templateName(kind) };
		source = fromName !? { this.sourceFile(kind, fromName) };
		if(source.isNil or: { File.exists(source).not }) {
			"SuperDirtGUI: no new % - no file in %/ to copy%".format(
				label, kind, fromName !? { " for '%'".format(fromName) } ? "").warn;
			^nil
		};
		fromBase = this.class.baseName(kind, fromName);
		text = File.readAllString(source);
		if(this.class.prHasWord(text, fromBase).not) {
			"SuperDirtGUI: no new % - % never says '%', so there is nothing to rename"
				.format(label, source.basename, fromBase).warn;
			^nil
		};
		text = text.replace(source.basename, base ++ ".scd");
		text = this.class.prReplaceWord(text, fromBase, base);
		file = this.dirFor(kind) +/+ base ++ ".scd";
		File.use(file, "w", { |f| f.write(text) });
		this.loadFile(file);
		^this.prOwnPath(file) ? file // the spelling its record is kept under
	}

	// event keys SuperDirt, tidal or the player module read without any SynthDef
	// or default event declaring them - the core and internal part of SuperDirt's
	// own used-parameters.scd, and the player module's player and diversion
	*eventWords {
		^#[\s, \n, \sound, \note, \legato, \accelerate, \channel, \cycle, \cps, \orbit,
			\instrument, \type, \buffer, \bufnum, \unitDuration, \fadeInTime, \endSpeed,
			\synthGroup, \dirtOut, \i_out, \player, \diversion]
	}

	// ----- sample players (see SDGSamplePlayer)

	// builds the player's SynthDefs and puts it at the front of the list, so the
	// most recently added player wins when several tests pass. re-adding a name
	// replaces it in place. the first player installs the module that hands
	// sample events to the players, in front of SuperDirt's 'sound' module.
	addSamplePlayer { |name, synthFunc, test, defPrefix, rates, prepare|
		var player = SDGSamplePlayer(name, synthFunc, test, defPrefix, rates, prepare);
		var index = samplePlayers.indexOfEqual(player);
		player.makeSynthDefs(dirt.numChannels);
		if(index.isNil) {
			samplePlayers = samplePlayers.addFirst(player)
		} {
			samplePlayers.put(index, player)
		};
		this.prInstallPlayerModule;
		this.prRegistered(player.name);
		^player
	}

	removeSamplePlayer { |name|
		name = name.asSymbol;
		samplePlayers = samplePlayers.reject { |x| x.name == name };
		if(samplePlayers.isEmpty) { this.prRemovePlayerModule };
	}

	getSamplePlayer { |name|
		name = name.asSymbol;
		^samplePlayers.detect { |x| x.name == name }
	}

	samplePlayerNames { ^samplePlayers.collect { |x| x.name } }

	// the player for a sample event: an explicit ~player wins, otherwise the
	// first whose test passes; nil when none wants it, and SuperDirt plays it
	samplePlayerFor { |event|
		var name = event[\player], player;
		if(name.notNil) {
			player = this.getSamplePlayer(name);
			if(player.notNil) { ^player };
			"SuperDirtGUI: no sample player named '%', choosing one by test instead.".format(name).warn;
		};
		^samplePlayers.detect { |x| x.matches(event) }
	}

	// The module runs before 'sound'. When a player wants the event it sends
	// the player's synth with the arguments SuperDirt would have sent - the
	// SynthDesc's msgFunc, the buffer under bufnum, freq tuned by the sample's
	// metadata - and sets ~diversion, which is the key SuperDirt's own 'sound'
	// module checks before playing. Nothing of SuperDirt's is replaced.
	prInstallPlayerModule {
		var modules, mine, soundIndex;
		if(dirt.getModule(playerModuleName).isNil) {
			dirt.addModule(playerModuleName, { |dirtEvent|
				var player = this.samplePlayerFor(currentEnvironment);
				var instrument, args, i;
				if(player.notNil) {
					player.prepare.value(dirtEvent);
					instrument = player.instrumentFor(currentEnvironment);
					if(SynthDescLib.global.at(instrument).isNil) {
						if(warnedInstruments.includes(instrument).not) {
							warnedInstruments.add(instrument);
							"SuperDirtGUI: player '%' has no SynthDef '%' - SuperDirt plays the sample instead"
								.format(player.name, instrument).warn;
						};
					} {
						args = dirtEvent.getMsgFunc(instrument).valueEnvir;
						args = args ++ [\bufnum, ~buffer];
						i = args.indexOf(\freq);
						i !? { args[i + 1] = args[i + 1] * (~metaDataTuneRatio.value ? 1.0) };
						dirtEvent.sendSynth(instrument, args);
						~diversion = true; // the sample is playing: 'sound' skips this event
					}
				}
			}, { ~buffer.notNil and: { ~diversion.value.isNil } });
		};
		// in front of 'sound', which is where SuperDirt's own sample playback happens
		modules = dirt.modules;
		mine = modules.detect { |m| m.name == playerModuleName };
		modules = modules.reject { |m| m.name == playerModuleName };
		soundIndex = modules.detectIndex { |m| m.name == \sound } ? modules.size;
		dirt.modules = modules.insert(soundIndex, mine);
	}

	prRemovePlayerModule {
		dirt.removeModule(playerModuleName);
	}

	// ----- global effects

	// called from effects/*.scd: register a SynthDef name and the event keys it listens to
	addGlobalEffect { |name, paramNames, alwaysRun = false|
		name = name.asSymbol;
		globalEffectSpecs = globalEffectSpecs.reject { |spec| spec[\name] == name };
		globalEffectSpecs = globalEffectSpecs.add(
			(name: name, paramNames: paramNames.asArray, alwaysRun: alwaysRun)
		);
		this.prRegistered(name);
	}

	// the same for an effect no orbit gets by itself: it waits in the add menu of the
	// window's effects tab (or addTrack, setReturnEffect) and costs nothing until
	// then. It always runs, because an event need not carry any of its parameters:
	// having been added is what switches it on. specs: param -> what asSpec takes,
	// the range of its knob.
	offerGlobalEffect { |name, paramNames, specs|
		name = name.asSymbol;
		globalEffectSpecs = globalEffectSpecs.reject { |spec| spec[\name] == name };
		globalEffectSpecs = globalEffectSpecs.add(
			(name: name, paramNames: paramNames.asArray, alwaysRun: true, offered: true, specs: specs)
		);
		this.prRegistered(name);
	}

	globalEffectNames { ^globalEffectSpecs.collect { |spec| spec[\name] } }

	// the names no orbit gets by itself, see offerGlobalEffect
	offeredEffectNames {
		^globalEffectSpecs.select { |spec| spec[\offered] == true }.collect { |spec| spec[\name] }
	}

	// the knob range the effect's file gave a parameter, or nil
	effectParamSpec { |name, param|
		^this.prSpecFor(name.asSymbol) !? { |spec|
			spec[\specs] !? { |specs| specs[param.asSymbol] !? (_.asSpec) }
		}
	}

	// every orbit gets its own instance of each registered effect, first in the
	// chain, and a fader last before SuperDirt's rms and monitor. safe to call
	// twice: an orbit that already has one is left alone.
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
		var plumbing = this.class.plumbingEffectNames;
		dirt.orbits.do { |orbit|
			var added = false, fxs, insertAt, fader;
			globalEffectSpecs.do { |spec|
				if(this.prBelongsIn(orbit, spec) and: { orbit.globalEffects.any { |fx| fx.name == spec[\name] }.not }) {
					orbit.globalEffects = orbit.globalEffects.addFirst(
						GlobalDirtEffect(spec[\name], spec[\paramNames]).alwaysRun_(spec[\alwaysRun])
					);
					added = true;
				}
			};
			if(orbit.globalEffects.any { |fx| fx.name == faderEffectName }.not) {
				fxs = orbit.globalEffects;
				insertAt = fxs.detectIndex { |fx| plumbing.includes(fx.name) } ? fxs.size;
				fader = GlobalDirtEffect(faderEffectName, [\orbitAmp]).alwaysRun_(true);
				// in the state, not the parameters: play passes it on, no event touches it
				fader.state[\tapBus] = this.prTapBus(orbit).index;
				orbit.globalEffects = fxs.insert(insertAt, fader);
				added = true;
			};
			if(this.prSyncTracks(orbit)) { added = true };
			this.prChainOrbit(orbit);
			if(added) { this.rebuildOrbit(orbit) };
			this.changed(\tracks, orbit.orbitIndex);
		};
		// the fresh fader synths start at 1: push the mixer's state onto them
		this.prInitMixer;
		this.applyAmps;
	}

	// an offered effect only goes into an orbit that has a track waiting for it, after a reload
	prBelongsIn { |orbit, spec|
		^spec[\offered] != true or: {
			this.tracksOf(orbit.orbitIndex).any { |track| track.name == spec[\name] and: { track.fx.isNil } }
		}
	}

	// The fader scales the orbit's dry and effect busses in place, so the
	// monitor after it - SuperDirt's own, untouched - sees the level the window
	// set. It sleeps with the orbit (DirtPause on its input, so a muted orbit
	// with events playing stays awake) and is woken like the monitor, on every
	// event, because it always runs. Asleep it writes nothing to the tap, which
	// then reads as silence: a recording of it goes on, in time.
	prDefineFaderDef {
		var numChannels = dirt.numChannels;
		SynthDef(faderEffectName.asString ++ numChannels, { |dryBus, effectBus, gate = 1, orbitAmp = 1, tapBus|
			var dry = In.ar(dryBus, numChannels);
			var wet = In.ar(effectBus, numChannels);
			var amp = orbitAmp.lag(0.05);
			EnvGen.kr(Env.asr, gate, doneAction: 2);
			DirtPause.ar(dry + wet, graceTime: 4);
			ReplaceOut.ar(dryBus, dry * amp);
			ReplaceOut.ar(effectBus, wet * amp);
			Out.ar(tapBus, (dry + wet) * amp);
		}, [\ir, \ir, \kr, \kr, \ir]).add;
	}

	// ----- the mixer: one fader, mute and solo per orbit

	prInitMixer {
		var n = dirt.orbits.size;
		if(faderValues.notNil and: { faderValues.size == n }) { ^this };
		faderValues = 1.0 ! n;
		muteStates = false ! n;
		soloStates = false ! n;
		appliedAmps = nil ! n;
	}

	setFader { |i, amp|
		if(faderValues.isNil) { ^this };
		faderValues[i] = amp.clip(0, 4).asFloat;
		this.applyAmps;
	}

	setMute { |i, bool|
		if(muteStates.isNil) { ^this };
		muteStates[i] = bool.asBoolean;
		this.applyAmps;
	}

	setSolo { |i, bool|
		if(soloStates.isNil) { ^this };
		soloStates[i] = bool.asBoolean;
		this.applyAmps;
	}

	// what the fader effect of orbit i is told: 0 when muted or another orbit is soloed
	effectiveAmp { |i|
		if(faderValues.isNil) { ^1.0 };
		if(muteStates[i]) { ^0.0 };
		if(this.prAnySolo and: { soloStates[i].not }) { ^0.0 };
		^faderValues[i]
	}

	// a soloed return silences the orbits' direct sound too, not what they send it
	prAnySolo { ^soloStates.includes(true) or: { returns.any(_.soloed) } }

	// written into the orbit's defaultParentEvent first, then pushed through the
	// effect's change tracking - so the value the synth holds and the value every
	// future event carries agree, and nothing flips back. only orbits whose level
	// changed are touched: a fader drag sends a dozen updates a second.
	applyAmps {
		if(dirt.orbits.isNil or: { faderValues.isNil }) { ^this };
		dirt.orbits.do { |orbit, i|
			var amp = this.effectiveAmp(i);
			if(appliedAmps[i] != amp) {
				appliedAmps[i] = amp;
				orbit.set(\orbitAmp, amp);
				this.wakeOrbit(orbit);
			}
		};
		this.prApplyReturnLevels;
		this.changed(\mixer);
	}

	// a parameter of the orbit's plumbing - limitertype - set the same way
	setOrbitParam { |i, key, value|
		var orbit = dirt.orbits[i];
		orbit.set(key, value);
		this.wakeOrbit(orbit);
	}

	// push the defaultParentEvent to every always-on effect of the orbit: the
	// fader takes its new level, and the monitor is resumed if it was asleep,
	// so a change is heard even after the orbit has been silent for a while.
	// The same thing every event does on its way in.
	wakeOrbit { |orbit|
		orbit.globalEffects.do { |fx|
			if(fx.alwaysRun) { fx.set(orbit.defaultParentEvent) }
		}
	}

	// GlobalDirtEffect:release, made to work on a sleeping synth too. DirtPause
	// is a DetectSilence that pauses its synth, and once it has paused it, it
	// pauses it again in the first block after any /n_run that comes without the
	// dirt_resumed trigger. release sends none: the gate's release never gets to
	// finish, and the synth stays in the node tree, paused, holding its delay
	// lines, until the next cmd-period. Measured: every effect that had run and
	// gone quiet was left behind by each reload. This sends the trigger with the
	// gate; a stock release after it (play sends one) changes nothing.
	releaseEffect { |fx, releaseTime = 0.2|
		var synth = fx.synth;
		synth !? {
			synth.server.sendBundle(synth.server.latency,
				['/error', -1], // as release does: the synth may be gone already
				[12, synth.nodeID, 1], // /n_run
				[15, synth.nodeID, \dirt_resumed, 1, \gate, -1.0 - releaseTime], // /n_set
				['/error', -2]
			)
		}
	}

	// DirtOrbit:initNodeTree - every effect of the orbit started again, in the
	// order of its globalEffects - without leaving sleeping synths behind
	rebuildOrbit { |orbit|
		orbit.globalEffects.do { |fx| this.releaseEffect(fx) };
		orbit.initNodeTree;
		this.orderInserts(orbit);
	}

	// DirtOrbit:outBus_ for orbit i: it rebuilds the orbit, so the effects are
	// released the way that frees sleeping ones and the inserts put back in place
	setOutBus { |i, bus|
		var orbit = dirt.orbits[i];
		orbit.globalEffects.do { |fx| this.releaseEffect(fx) };
		orbit.outBus = bus;
		this.orderInserts(orbit);
	}

	// ----- the meters

	// SuperDirt's rms replies - dirt_rms, what the window's meters read - at a
	// rate that lands. DirtOrbit:startSendRMS hands the rate to the effect
	// while it is still switched off, so it never arrives and the replies come
	// 8 a second with peaks held 3 seconds. Here the rate goes into the orbit's
	// default event and the effect's state first - every event carries it
	// from then on, so none sets it back - and the orbit is rebuilt, since a
	// reply rate is fixed when its synth starts.
	startMeters { |rate = 20, peakLag = 1.5|
		(dirt.orbits ? []).do { |orbit|
			var rms = orbit.globalEffects.detect { |fx| fx.name == \dirt_rms };
			rms !? {
				orbit.set(\rmsReplyRate, rate, \rmsPeakLag, peakLag);
				rms.state[\rmsReplyRate] = rate;
				rms.state[\rmsPeakLag] = peakLag;
				rms.active_(true);
				this.rebuildOrbit(orbit);
			}
		}
	}

	stopMeters { dirt.stopSendRMS }

	// ----- inserts (see SDGInsert)

	// the inserts of an orbit, in the order the signal goes through them
	insertsOf { |orbitIndex| ^inserts[orbitIndex] ? [] }

	// An insert of a kind - \eq, \compressor, \insight or \vst - on an orbit, at
	// a position among its inserts, the end by default: into the orbit's chain
	// before the fader, its synth started in its place and nothing else
	// restarted. A \vst insert needs its plugin: a name VSTPlugin.search found
	// (see vstPlugins) or a file path. Answers the SDGInsert, or nil when there
	// is no such orbit or the kind's extension is not installed.
	addInsert { |orbitIndex, kind, index, plugin|
		^this.prAddInsert(orbitIndex, kind, index, plugin)
	}

	// The same on a track: place is \pre, before its effect, or \post, after it;
	// index is among the inserts of that place.
	addTrackInsert { |track, place, kind, index, plugin|
		place = place.asSymbol;
		if(#[\pre, \post].includes(place).not) {
			"SuperDirtGUI: a track's insert goes \\pre or \\post its effect, not %".format(place).warn;
			^nil
		};
		^this.prAddInsert(track.orbitIndex, kind, index, plugin, track, place)
	}

	prAddInsert { |orbitIndex, kind, index, plugin, track, place|
		var orbit, list, insert, count, onReturn = track.isKindOf(SDGReturn);
		kind = kind.asSymbol;
		orbit = if(onReturn.not) { dirt.orbits !? { dirt.orbits[orbitIndex] } };
		if(orbit.isNil and: { onReturn.not }) {
			"SuperDirtGUI: no orbit % for an insert - call ~dirt.start first".format(orbitIndex).warn;
			^nil
		};
		if(SDGInsert.available(kind).not) {
			"SuperDirtGUI: no % insert - % is not installed".format(kind,
				SDGInsert.kinds[kind] !? { |k| k[\className] } ? kind).warn;
			^nil
		};
		if(kind == \vst and: { plugin.isNil }) {
			"SuperDirtGUI: a VST insert needs a plugin: addInsert(%, \\vst, nil, \"its name\")".format(orbitIndex).warn;
			^nil
		};
		list = if(track.notNil) { track.insertsAt(place) } { this.insertsOf(orbitIndex) };
		count = list.count { |x| x.kind == kind } + 1;
		insert = SDGInsert(this, orbitIndex, kind,
			if(onReturn) {
				"% · %%".format(track.where, place, if(count > 1) { " · " ++ count } { "" })
			} {
				"orbit %%%".format(orbitIndex,
					track !? { " · % %".format(track.shortName, place) } ? "",
					if(count > 1) { " · " ++ count } { "" })
			},
			plugin, track, place);
		index = (index ? list.size).asInteger.clip(0, list.size);
		this.prStoreInserts(insert, list.insert(index, insert));
		orbit !? { this.prChainOrbit(orbit) };
		this.prEnsureInsertDef(kind);
		if(insert.isVST) { this.prWatchTree };
		fork {
			dirt.server.sync; // its def first
			if(this.insertListOf(insert).includes(insert)) {
				case
				{ onReturn and: { insert.isVST } } { insert.prStart(this.returnHeads, \addAfter) }
				{ onReturn } { track.prPlay(insert.fx) }
				{ insert.isVST } { insert.prStart(Group.basicNew(dirt.server, orbit.group), \addAfter) }
				{ insert.fx.play(*this.prPlayArgs(orbit)) };
				if(onReturn) {
					this.orderReturns
				} {
					this.orderInserts(orbit);
					this.wakeOrbit(orbit);
				}
			}
		};
		this.changed(\inserts, orbitIndex);
		^insert
	}

	// take an insert out: its synth released, which fades the two busses back
	// to what they were, and its object, window, insight or plugin freed
	removeInsert { |insert|
		var i = insert.orbitIndex, orbit = insert.orbit, list = this.insertListOf(insert);
		if(list.includes(insert).not) { ^this };
		this.prStoreInserts(insert, list.reject { |x| x === insert });
		insert.fx !? { |fx|
			this.releaseEffect(fx);
			orbit !? { orbit.globalEffects = orbit.globalEffects.reject { |x| x === fx } };
		};
		insert.free;
		this.changed(\inserts, i);
	}

	// The VST plugins the server can open: VSTPlugin's own search, whose
	// results it caches on disk, so only plugins it has not met before take
	// time to probe - a first search over many can take a minute. action gets
	// their VSTPluginDescs, effects only, sorted by name. rescan searches again.
	vstPlugins { |action, rescan = false|
		var cls = \VSTPlugin.asClass, server = dirt.server, done;
		if(cls.isNil) { ^action.value([]) };
		done = {
			action.value((cls.plugins(server) ? ()).values.asArray
				.reject { |desc| desc.synth == true }
				.sort { |a, b| a.name.asString.toLower < b.name.asString.toLower })
		};
		if(rescan.not and: { cls.plugins(server).size > 0 }) { ^done.value };
		cls.search(server, verbose: false, action: { { done.value }.defer });
	}

	// a cmd-period frees every node, a VST insert's synth with its plugin.
	// SuperDirt rebuilds the orbits on the same ServerTree, registered before
	// this, so by now the chain is back and the VST inserts go into it again,
	// each plugin with the state it last read back
	prWatchTree {
		treeFunc ?? {
			treeFunc = { this.prRestartVSTInserts; this.prRestartReturns };
			ServerTree.add(treeFunc, dirt.server);
		}
	}

	prRestartVSTInserts {
		(dirt.orbits ? []).do { |orbit|
			var vsts = this.prEveryInsertOf(orbit.orbitIndex).select { |x| x.isVST };
			if(vsts.notEmpty) {
				vsts.do { |x| x.prStart(Group.basicNew(dirt.server, orbit.group), \addAfter) };
				this.orderInserts(orbit);
			}
		}
	}

	// every insert of an orbit out
	removeInserts { |orbitIndex|
		this.insertsOf(orbitIndex).copy.do { |x| this.removeInsert(x) }
	}

	// the list an insert is in: its track's before or after the effect, or its orbit's
	insertListOf { |insert|
		^if(insert.track.notNil) { insert.track.insertsAt(insert.place) } { this.insertsOf(insert.orbitIndex) }
	}

	prStoreInserts { |insert, list|
		if(insert.track.notNil) {
			insert.track.prStoreInserts(insert.place, list)
		} {
			inserts[insert.orbitIndex] = list
		}
	}

	prEveryInsertOf { |orbitIndex|
		^this.insertsOf(orbitIndex) ++ this.tracksOf(orbitIndex).collect(_.inserts).flatten(1)
	}

	// An insert to another position in its list. The synths are moved, not
	// started again, so nothing is interrupted.
	moveInsert { |insert, toIndex|
		var list = this.insertListOf(insert), from = list.indexOf(insert);
		if(from.isNil) { ^this };
		toIndex = toIndex.asInteger.clip(0, list.size - 1);
		if(toIndex == from) { ^this };
		list = list.copy;
		list.removeAt(from);
		this.prStoreInserts(insert, list.insert(toIndex, insert));
		if(insert.isOnReturn) {
			this.orderReturns
		} {
			this.prChainOrbit(insert.orbit);
			this.orderInserts(insert.orbit);
		};
		this.changed(\inserts, insert.orbitIndex);
	}

	// The orbit's chain from what is known of it: the tracks, the orbit's own
	// inserts, its sends to the returns, then the fader, rms and monitor as they stand. An effect that
	// reached the list by hand and has no track yet goes first, the way it
	// plays until prSyncTracks makes it one.
	prChainOrbit { |orbit|
		var i = orbit.orbitIndex, plumbing = this.class.plumbingEffectNames;
		var tail = orbit.globalEffects.select { |fx| fx.name == faderEffectName or: { plumbing.includes(fx.name) } };
		var mine = this.tracksOf(i).collect(_.chain).flatten(1)
			++ this.insertsOf(i).collect(_.fx).reject(_.isNil)
			++ this.prSendsOf(i);
		var loose = orbit.globalEffects.reject { |fx| tail.includes(fx) or: { mine.includes(fx) } };
		loose = loose.reject { |fx|
			SDGInsert.isInsertName(fx.name) or: { SDGTrack.isTrackName(fx.name) } or: { SDGReturn.isReturnName(fx.name) }
		};
		orbit.globalEffects = loose ++ mine ++ tail;
	}

	// Every node of the orbit's chain walked into the order of the chain, each
	// right after the one before: moved, never started again. GlobalDirtEffect:play
	// puts a synth at the head of the chain, and after a rebuild a VST insert's
	// synth is wherever the old chain left it - this is what puts them back.
	orderInserts { |orbit|
		var i = orbit.orbitIndex;
		var prev = Group.basicNew(dirt.server, orbit.group);
		var plumbing = this.class.plumbingEffectNames;
		var mine = this.tracksOf(i).collect(_.chain).flatten(1)
			++ this.insertsOf(i).collect(_.fx)
			++ this.prSendsOf(i);
		var nodes = orbit.globalEffects.reject { |fx|
			mine.includes(fx) or: { fx.name == faderEffectName } or: { plumbing.includes(fx.name) }
		}.collect(_.synth)
			++ this.tracksOf(i).collect(_.nodes).flatten(1)
			++ this.insertsOf(i).collect(_.node)
			++ this.prSendsOf(i).collect(_.synth)
			++ orbit.globalEffects.select { |fx| fx.name == faderEffectName or: { plumbing.includes(fx.name) } }.collect(_.synth);
		nodes.reject(_.isNil).do { |node|
			node.moveAfter(prev);
			prev = node;
		}
	}

	// ----- tracks (see SDGTrack)

	// the tracks of an orbit, in the order of its chain: one per global effect
	tracksOf { |orbitIndex| ^tracks[orbitIndex] ? [] }

	// the track a GlobalDirtEffect is the effect of, or nil
	trackOf { |fx|
		tracks.do { |list| list.do { |track| if(track.fx === fx) { ^track } } };
		^nil
	}

	// the keys a global effect listens to: what its file registered, or else
	// its SynthDef's controls without the plumbing. nil when there is no such def
	effectParams { |name|
		var desc = SynthDescLib.at((name.asString ++ dirt.numChannels).asSymbol);
		this.prSpecFor(name) !? { |spec| ^spec[\paramNames] };
		if(desc.isNil) { ^nil };
		^desc.controls.collect(_.name).reject { |n| SDGWindow.hiddenParams.includes(n) }
	}

	// A new track with the named global effect, at a position among the orbit's
	// tracks, the end by default. Nothing else in the chain restarts. Answers the
	// SDGTrack, or nil when there is no such SynthDef or the orbit has it already.
	addTrack { |orbitIndex, name, index|
		var orbit = dirt.orbits !? { dirt.orbits[orbitIndex] };
		var fx, track, list;
		name = name.asSymbol;
		fx = this.prNewEffect(orbit, name);
		if(fx.isNil) { ^nil };
		track = SDGTrack(this, orbitIndex, fx);
		list = this.tracksOf(orbitIndex);
		index = (index ? list.size).asInteger.clip(0, list.size);
		tracks[orbitIndex] = list.insert(index, track);
		this.prChainOrbit(orbit);
		track.prPlay(this.prPlayArgs(orbit));
		this.orderInserts(orbit);
		this.wakeOrbit(orbit);
		this.changed(\tracks, orbitIndex);
		^track
	}

	// Another global effect on a track: the old one released with its tail, the
	// new one started between the same inserts, reading the same send, at the
	// same fader. Answers the new GlobalDirtEffect, or nil when there is no such
	// SynthDef or the orbit has it already.
	setTrackEffect { |track, name|
		var orbit = track.orbit, fx, old = track.fx;
		name = name.asSymbol;
		if(old.notNil and: { old.name == name }) { ^old };
		fx = this.prNewEffect(orbit, name);
		if(fx.isNil) { ^nil };
		old !? {
			this.releaseEffect(old);
			orbit.globalEffects = orbit.globalEffects.reject { |x| x === old };
		};
		track.prAttach(fx);
		this.prChainOrbit(orbit);
		fx.play(*this.prPlayArgs(orbit));
		this.orderInserts(orbit);
		fx.set(orbit.defaultParentEvent);
		this.wakeOrbit(orbit);
		this.changed(\tracks, track.orbitIndex);
		^fx
	}

	// a track to another position among its orbit's tracks: its synths are moved,
	// not started again
	moveTrack { |track, toIndex|
		var i = track.orbitIndex, list = this.tracksOf(i), from = list.indexOf(track);
		if(from.isNil) { ^this };
		toIndex = toIndex.asInteger.clip(0, list.size - 1);
		if(toIndex == from) { ^this };
		list = list.copy;
		list.removeAt(from);
		tracks[i] = list.insert(toIndex, track);
		this.prChainOrbit(track.orbit);
		this.orderInserts(track.orbit);
		this.changed(\tracks, i);
	}

	// a track and its effect out of the orbit, its inserts with them
	removeTrack { |track|
		var i = track.orbitIndex, orbit = track.orbit;
		if(this.tracksOf(i).includes(track).not) { ^this };
		track.inserts.do { |x| this.removeInsert(x) };
		tracks[i] = this.tracksOf(i).reject { |x| x === track };
		track.fx !? { |fx| this.releaseEffect(fx) };
		track.prFree;
		orbit.globalEffects = orbit.globalEffects.reject { |fx|
			fx === track.fx or: { fx === track.sendFx } or: { fx === track.returnFx }
		};
		this.changed(\tracks, i);
	}

	// the bus a track's inserts read as their effect bus: nothing writes to it
	silentBus {
		^silentBus ?? { silentBus = Bus.audio(dirt.server, dirt.numChannels) }
	}

	// a GlobalDirtEffect of a name, with a warning and nil when there is nothing to
	// play or the orbit has one - a name is how an orbit's effects are told apart
	prNewEffect { |orbit, name|
		var params = this.effectParams(name), spec = this.prSpecFor(name);
		if(orbit.isNil) { "SuperDirtGUI: no such orbit for %".format(name).warn; ^nil };
		if(params.isNil) { "SuperDirtGUI: no SynthDef '%%' found".format(name, dirt.numChannels).warn; ^nil };
		if(orbit.globalEffects.any { |fx| fx.name == name }) {
			"SuperDirtGUI: orbit % has % already".format(orbit.orbitIndex, name).warn;
			^nil
		};
		^GlobalDirtEffect(name, params, dirt.numChannels).alwaysRun_(spec !? { spec[\alwaysRun] } ? false)
	}

	// Tracks for the effects of the orbit's list that have none. A track waiting
	// for its effect after a reload gets it back, under the same name, so its
	// inserts and its fader survive; one still waiting goes. Effects further
	// up the list than any track go first, the others last. A send or return
	// left by an earlier SuperDirtGUI comes out. Answers true when any effect
	// has to start again on its new busses.
	prSyncTracks { |orbit|
		var i = orbit.orbitIndex, list = this.tracksOf(i), changed = false;
		var owned = list.collect(_.chain).flatten(1);
		var unowned, firstOwned, head = [], tail = [];
		orbit.globalEffects = orbit.globalEffects.reject { |fx|
			var foreign = (SDGTrack.isTrackName(fx.name) or: { SDGReturn.isReturnName(fx.name) })
				and: { owned.includes(fx).not } and: { this.prSendsOf(i).includes(fx).not };
			if(foreign) { this.releaseEffect(fx) };
			foreign
		};
		unowned = orbit.globalEffects.reject { |fx|
			owned.includes(fx)
			or: { fx.name == faderEffectName }
			or: { this.class.plumbingEffectNames.includes(fx.name) }
			or: { SDGInsert.isInsertName(fx.name) }
			or: { SDGReturn.isReturnName(fx.name) }
		};
		list.do { |track|
			if(track.fx.isNil) {
				unowned.detect { |fx| fx.name == track.name } !? { |fx|
					track.prAttach(fx);
					unowned = unowned.reject { |x| x === fx };
					changed = true;
				}
			}
		};
		firstOwned = orbit.globalEffects.detectIndex { |fx| owned.includes(fx) } ? inf;
		unowned.do { |fx|
			var track = SDGTrack(this, i, fx);
			if(orbit.globalEffects.indexOf(fx) < firstOwned) { head = head.add(track) } { tail = tail.add(track) };
			changed = true;
		};
		tracks[i] = head ++ list ++ tail;
		this.tracksOf(i).select { |track| track.fx.isNil }.do { |track| this.removeTrack(track) };
		^changed
	}

	// What the window's refresh asks: an effect that reached the orbit's list from
	// code gets a track. Nothing happens to an orbit whose effects all have one.
	adoptEffects { |orbit|
		if(this.prSyncTracks(orbit)) {
			this.prChainOrbit(orbit);
			this.rebuildOrbit(orbit);
			this.changed(\tracks, orbit.orbitIndex);
		}
	}

	// ----- return tracks (see SDGReturn)

	// the group the returns run in, right after SuperDirt's, and the one in it where
	// the heads run, before anything of any return
	returnGroup { ^returnGroupID !? { Group.basicNew(dirt.server, returnGroupID) } }
	returnHeads { ^returnHeadsID !? { Group.basicNew(dirt.server, returnHeadsID) } }

	prMakeReturnGroups {
		returnGroupID = returnGroupID ?? { dirt.server.nextPermNodeID };
		returnHeadsID = returnHeadsID ?? { dirt.server.nextPermNodeID };
		dirt.server.sendBundle(nil,
			['/error', -1], // they may be there already
			['/g_new', returnGroupID, 3, dirt.group], // 3: after
			['/g_new', returnHeadsID, 0, returnGroupID],
			['/error', -2]
		);
	}

	returnAt { |letter|
		letter = letter.asSymbol;
		^returns.detect { |ret| ret.letter == letter }
	}

	// A return track, the next letter free unless one is asked for, with a send
	// from every orbit and to and from every return, all at nothing. Nothing that
	// plays restarts. Answers the SDGReturn, or nil.
	addReturn { |letter|
		var ret;
		if(dirt.orbits.isNil) {
			"SuperDirtGUI: no orbits yet - call ~dirt.start first, then addReturn".warn;
			^nil
		};
		letter = letter !? (_.asSymbol) ?? {
			"ABCDEFGHIJKLMNOPQRSTUVWXYZ".detect { |c| this.returnAt(c.asString).isNil } !? { |c| c.asString.asSymbol }
		};
		if(letter.isNil or: { this.returnAt(letter).notNil }) {
			"SuperDirtGUI: no return % - %".format(letter ? "", if(letter.isNil) { "all 26 letters are taken" } { "there is one" }).warn;
			^nil
		};
		this.prWatchTree;
		this.prMakeReturnGroups;
		ret = SDGReturn(this, letter);
		returns = returns.add(ret).sort { |a, b| a.letter.asString < b.letter.asString };
		returns.do { |other|
			ret.prAddTarget(other);
			other.prAddTarget(ret);
		};
		ret.prStart;
		dirt.orbits.do { |orbit|
			var send = ret.sendFxs[orbit.orbitIndex];
			this.prChainOrbit(orbit);
			send.play(*this.prPlayArgs(orbit));
			this.orderInserts(orbit);
			send.resume;
		};
		this.orderReturns;
		this.prStartKeepAwake;
		this.applyAmps;
		this.changed(\returns);
		^ret
	}

	// A return out, with its inserts, its effect, every send to it and from it.
	removeReturn { |ret|
		if(returns.includes(ret).not) { ^this };
		ret.inserts.do { |insert| this.removeInsert(insert) };
		ret.fx !? { |fx| this.releaseEffect(fx) };
		returns = returns.reject { |x| x === ret };
		returns.do { |other| other.prRemoveTarget(ret) };
		(dirt.orbits ? []).do { |orbit|
			var send = ret.sendFxs[orbit.orbitIndex];
			this.releaseEffect(send);
			orbit.globalEffects = orbit.globalEffects.reject { |fx| fx === send };
		};
		ret.prFree;
		if(returns.isEmpty) { this.prStopKeepAwake };
		this.applyAmps;
		this.changed(\returns);
	}

	// A global effect on a return, in the place of the one it has, or none with a
	// nil name; the tail of the old one rings on the bus as it fades. Starts at
	// values that are heard (SDGReturn.wetDefaults). Answers the GlobalDirtEffect,
	// or nil when there is none now.
	setReturnEffect { |ret, name|
		var fx, old = ret.fx;
		name = name !? (_.asSymbol);
		if(name.notNil and: { old.notNil } and: { old.name == name }) { ^old };
		if(name.notNil) {
			fx = this.prNewEffect(ret, name);
			if(fx.isNil) { ^nil };
		};
		old !? { this.releaseEffect(old) };
		ret.prAttach(fx);
		fx !? { ret.prPlay(fx) };
		this.orderReturns;
		this.changed(\returns);
		^fx
	}

	// orbit i's send to a return, 0 to 1. ret may be its letter.
	setSend { |i, ret, amp|
		ret = this.prReturnFrom(ret);
		ret !? {
			ret.prOrbitLevel_(i, amp);
			this.applyAmps;
		}
	}

	sendOf { |i, ret|
		^this.prReturnFrom(ret) !? { |r| r.orbitLevel(i) } ? 0
	}

	// one return's send to another or to itself, 0 to 1. It is only heard while
	// returnFeedback is on; the knob keeps its level, and a loop can be set up first.
	setReturnSend { |from, to, amp|
		from = this.prReturnFrom(from);
		to = this.prReturnFrom(to);
		if(from.notNil and: { to.notNil }) {
			from.prReturnLevel_(to, amp);
			this.applyAmps;
		}
	}

	returnFeedback_ { |bool|
		returnFeedback = bool.asBoolean;
		this.applyAmps;
		this.changed(\returns);
	}

	prReturnFrom { |ret|
		^if(ret.isKindOf(SDGReturn)) { ret } { ret !? { this.returnAt(ret) } }
	}

	// the sends in orbit i's chain, in the order of the returns
	prSendsOf { |i| ^returns.collect { |ret| ret.sendFxs[i] } }

	// Ableton's rules: a return's Pre/Post says whether the source's fader counts for
	// what goes into it; a mute silences a source's sends too; solo keeps what a
	// soloed orbit sends audible on the returns, and a soloed return hears what
	// every orbit sends it. Only orbit solos cut other orbits' sends.
	prSendAmp { |i, ret|
		var level = ret.orbitLevel(i), fader = (faderValues !? { faderValues[i] }) ? 1.0;
		if(level <= 0) { ^0.0 };
		if(muteStates.notNil and: { muteStates[i] }) { ^0.0 };
		if(soloStates.notNil and: { soloStates.includes(true) } and: { soloStates[i].not }) { ^0.0 };
		^level * if(ret.preFader) { 1.0 } { fader }
	}

	prReturnAmp { |ret|
		if(ret.muted) { ^0.0 };
		if(returns.any(_.soloed) and: { ret.soloed.not }) { ^0.0 };
		^ret.level
	}

	prLinkAmp { |from, to|
		if(returnFeedback.not or: { from.muted }) { ^0.0 };
		^from.returnLevel(to) * if(to.preFader) { 1.0 } { from.level }
	}

	prApplyReturnLevels {
		returns.do { |ret|
			ret.prAmp_(this.prReturnAmp(ret));
			dirt.orbits.size.do { |i| ret.prSendAmp_(i, this.prSendAmp(i, ret)) };
			ret.targets.do { |to| ret.prLinkAmp_(to, this.prLinkAmp(ret, to)) };
		}
	}

	// every node of the returns walked into the order of their chains, after the heads
	orderReturns {
		var prev = this.returnHeads;
		prev !? {
			returns.collect(_.nodes).flatten(1).do { |node|
				node.moveAfter(prev);
				prev = node;
			}
		}
	}

	// after a cmd-period: SuperDirt has made its orbits again, sends in them
	prRestartReturns {
		if(returns.notEmpty) {
			this.prMakeReturnGroups;
			returns.do(_.prStart);
			this.orderReturns;
			this.prStopKeepAwake;
			this.prStartKeepAwake;
			this.prApplyReturnLevels;
		}
	}

	// what no event wakes: see SDGReturn. A cmd-period clears the clock, and
	// prRestartReturns starts this again
	prStartKeepAwake {
		keepAwake ?? {
			keepAwake = Routine {
				loop {
					1.wait;
					if(dirt.server.serverRunning) { returns.do(_.prWake) };
				}
			}.play(SystemClock)
		}
	}

	prStopKeepAwake {
		keepAwake !? { keepAwake.stop };
		keepAwake = nil;
	}

	// after an effect file was read again: on a return it starts again on its new
	// SynthDef and parameters too
	prRestartReturnEffects { |names|
		returns.do { |ret|
			ret.fx !? { |fx|
				if(names.isNil or: { names.includes(fx.name) }) {
					this.prSpecFor(fx.name) !? { |spec|
						fx.paramNames = spec[\paramNames];
						fx.alwaysRun = spec[\alwaysRun];
					};
					this.releaseEffect(fx);
					ret.prPlay(fx);
				}
			}
		};
		this.orderReturns;
	}

	prEnsureInsertDef { |kind|
		if(insertDefs.includes(kind).not) {
			SDGInsert.makeDef(kind, dirt.numChannels) !? { |def| def.add; insertDefs.add(kind) }
		}
	}

	// what GlobalDirtEffect:play is given, as DirtOrbit:initNodeTree gives it
	prPlayArgs { |orbit|
		^[orbit.group, orbit.outBus, orbit.dryBus, orbit.globalEffectBus, orbit.orbitIndex]
	}

	// ----- samples

	// append a folder to ~dirt's library: one bank per subfolder, the way
	// Dirt-Samples is laid out, and the sound files lying in the folder itself as
	// a bank named after it. a file the bank already holds is not read twice, so
	// a folder can be loaded again for what is new in it. goes to the library's
	// loadSoundFilePaths because its loadSoundFiles only looks at subfolders.
	// Answers the names of the banks that got files, in order.
	loadSamples { |folderPath|
		var lib = dirt.soundLibrary, root = folderPath.standardizePath.withoutTrailingSlash;
		var entries = this.class.prFolderEntries(root);
		var isFolder = { |p| p.endsWith(Platform.pathSeparator.asString) };
		var added = 0, names = [];
		([root] ++ entries.select(isFolder)).do { |folder|
			var files = if(folder == root) { entries.reject(isFolder) } { this.class.prFolderEntries(folder) };
			var name = this.class.bankName(folder), before = (lib.buffers[name] ? []).size;
			files = files.select { |p| lib.fileExtensions.includesEqual((p.splitext[1] ? "").toLower) };
			files = files.reject { |p| (lib.buffers[name] ? []).any { |b| this.class.samePath(b.path, p) } };
			lib.loadSoundFilePaths(files, name, true);
			before = (lib.buffers[name] ? []).size - before;	// a synth of that name keeps its name
			if(before > 0) { added = added + before; names = names.add(name) };
		};
		if(names.isEmpty) {
			"SuperDirtGUI: no new sound files in % or its subfolders".format(root).warn;
			^names
		};
		"SuperDirtGUI: % new samples in %\n".postf(added, names.join(" "));
		this.changed(\samples);
		^names
	}

	// what is in a folder, sorted. pathMatch is glob: a folder called Kit [Vol 1]
	// would match nothing unless its brackets are escaped. Windows' has no such
	// reading of them, and there \ is the separator.
	*prFolderEntries { |folder|
		if(thisProcess.platform.name != \windows) {
			"\\[]*?".do { |c| folder = folder.escapeChar(c) };
		};
		^(folder +/+ "*").pathMatch.sort
	}

	// the bank a folder makes: its name, with what tidal's mini notation would read
	// as something else (spaces above all) as _. Letters beyond ASCII stay.
	*bankName { |folderPath|
		^folderPath.withoutTrailingSlash.basename.collect { |c|
			if(c.ascii >= 0 and: { c.isAlphaNum.not } and: { "_-.".includes(c).not }) { $_ } { c }
		}.asSymbol
	}

	// one file, appended to a bank as its last index (a new bank if the name is new)
	addSample { |filePath, bankName|
		bankName = bankName.asSymbol;
		dirt.soundLibrary.loadSoundFile(filePath.standardizePath, bankName, true);
		this.changed(\samples, bankName);
	}

	// Read a sample that was rewritten on disk back into the same Buffer, so
	// every event keeps its bufnum and tidal plays the new file under the same
	// name and number. The sample editor calls this after a save. Answers the
	// Buffer, or nil.
	reloadSample { |name, index = 0|
		var lib = dirt.soundLibrary, buf, event, metaData;
		name = name.asSymbol;
		buf = lib.buffers[name] !? (_[index]);
		if(buf.isNil or: { buf.path.isNil }) {
			"SuperDirtGUI: nothing to reload at %:%".format(name, index).warn;
			^nil
		};
		if(File.exists(buf.path).not) {
			"SuperDirtGUI: % is not on disk any more".format(buf.path).warn;
			^nil
		};
		// the header first, so the event's frame count is right before the
		// server has even read the file - the same order readWithInfo uses
		SoundFile.use(buf.path, { |file|
			buf.sampleRate = file.sampleRate;
			buf.numFrames = file.numFrames;
			buf.numChannels = file.numChannels;
		});
		buf.allocRead(buf.path);
		event = lib.bufferEvents[name] !? (_[index]);
		event !? {
			metaData = lib.tryPerform(\readMetaData, buf.path);
			event.putAll(lib.makeEventForBuffer(buf, metaData));
			event[\notYetRead] = false;
		};
		this.changed(\reloaded, name, index);
		^buf
	}

	// the sound library's buffer for a bank and index, or nil
	bufferAt { |name, index = 0|
		var bufs = dirt.soundLibrary.buffers[name.asSymbol];
		if(bufs.isNil or: { bufs.isEmpty }) { ^nil };
		^bufs.wrapAt(index.asInteger)
	}

	// ----- BusManager (optional quark, looked up by name at runtime)

	// name every orbit's dryBus and globalEffectBus in the BusManager registry as
	// orbit0_dry, orbit0_fx ... so they can be metered and listened to from its window.
	// a no-op when the quark is not installed; safe to call twice.
	*registerBusses { |dirt|
		var bm = \BusManager.asClass;
		if(bm.isNil) { ^this };
		dirt = dirt ?? { SuperDirt.default } ?? { current !? (_.dirt) };
		if(dirt.isNil or: { dirt.orbits.isNil }) {
			"SuperDirtGUI: no started SuperDirt - nothing to register with BusManager.".postln;
			^this
		};
		dirt.orbits.do { |orbit, i|
			bm.addBus("orbit%_dry".format(i).asSymbol, orbit.dryBus);
			bm.addBus("orbit%_fx".format(i).asSymbol, orbit.globalEffectBus);
		};
		"SuperDirtGUI: registered % orbit busses with BusManager.".format(dirt.orbits.size * 2).postln;
	}

	// ----- the recorder (BusRecorder, optional, looked up by name at runtime)

	// the orbit's sound after its effects, inserts, fader, mute and solo - what
	// its meters read: its dry and effect busses summed by the fader effect. What SuperDirt's
	// monitor adds, the limiter, is not in it. Nil while the orbit has no fader.
	tapBusOf { |orbitIndex|
		^dirt.orbits !? { |orbits| orbits[orbitIndex] !? { |orbit| tapBusses !? { tapBusses[orbit] } } }
	}

	prTapBus { |orbit|
		tapBusses = tapBusses ?? { IdentityDictionary.new };
		tapBusses[orbit] ?? { tapBusses[orbit] = Bus.audio(dirt.server, dirt.numChannels) };
		^tapBusses[orbit]
	}

	// the preset: one source per orbit, named after it, one file per source. It
	// replaces what the recorder holds. Float, since there is no limiter on the
	// tap and an orbit can go over 0 dB. Not during a take.
	recordOrbits {
		var rec = \BusRecorder.asClass;
		if(rec.isNil) { "SuperDirtGUI: BusRecorder is not installed.".warn; ^nil };
		if(dirt.orbits.isNil) { "SuperDirtGUI: no orbits yet - call ~dirt.start first.".warn; ^nil };
		if(rec.recording) { "SuperDirtGUI: the recorder is recording - stop it first.".warn; ^nil };
		rec.server = dirt.server;
		rec.clear;
		dirt.orbits.do { |orbit, i| rec.addBus(this.prTapBus(orbit), "orbit%".format(i).asSymbol) };
		returns.do { |ret| rec.addBus(ret.tapBus, "return%".format(ret.letter).asSymbol) };
		rec.mode = \sources;
		rec.label = \name;
		rec.sampleFormat = "float";
		^rec
	}

	// the recorder's window, on the preset when the recorder holds nothing else:
	// a list you made is yours and stays, one left from an earlier SuperDirt reads
	// busses nothing writes to any more and goes
	recorder {
		var rec = \BusRecorder.asClass, stale;
		if(rec.isNil) { "SuperDirtGUI: BusRecorder is not installed.".warn; ^nil };
		if(dirt.orbits.isNil) { "SuperDirtGUI: no orbits yet - call ~dirt.start first.".warn; ^nil };
		stale = rec.sources.every { |src|
			src.kind == \bus and: { src.name.asString.beginsWith("orbit") }
			and: { dirt.orbits.any { |orbit| this.prTapBus(orbit) === src.bus }.not }
		} or: {
			// the preset itself, from before the returns it should hold now
			rec.sources.every { |src|
				dirt.orbits.any { |orbit| this.prTapBus(orbit) === src.bus }
				or: { src.name.asString.beginsWith("return") }
			} and: {
				rec.sources.size != (dirt.orbits.size + returns.size)
			}
		};
		if(stale and: { rec.recording.not }) { this.recordOrbits };
		^rec.gui
	}

	// with the faders gone nothing writes to the taps; a take in progress keeps
	// its files, and goes on writing silence until it is stopped
	prFreeTapBusses {
		var rec = \BusRecorder.asClass;
		(dirt.orbits ? []).do { |orbit|
			var bus = tapBusses !? { tapBusses.removeAt(orbit) };
			bus !? {
				rec !? {
					rec.sources.select { |src| src.bus === bus }.do { |src| rec.remove(src.name) }
				};
				bus.free;
			}
		}
	}

	// ----- gui

	// the control window (SDGWindow): orbit strips with meters, fader, mute and
	// solo; the global effects of an orbit; the sound explorer; what was loaded.
	// needs the orbits, so after ~dirt.start.
	gui {
		if(controlWindow.notNil and: { controlWindow.isOpen }) { controlWindow.front; ^controlWindow };
		if(dirt.orbits.isNil or: { dirt.orbits.isEmpty }) {
			"SuperDirtGUI: no orbits yet - call ~dirt.start first, then gui".warn;
			^nil
		};
		this.prInitMixer;
		controlWindow = SDGWindow(this);
		^controlWindow
	}

	// the Window while the control window is open, nil otherwise
	window {
		^if(controlWindow.notNil and: { controlWindow.isOpen }) { controlWindow.window }
	}

	// the SDGWindow while it is open, nil otherwise
	controlWindow {
		^if(controlWindow.notNil and: { controlWindow.isOpen }) { controlWindow }
	}

	// ----- tags (see SDGSampleTags)

	tags { ^tags ?? { tags = SDGSampleTags(this) } }

	// ----- sync (see SDGSync): long samples on one tempo and in one key, as Tidal code

	sync { ^sync ?? { sync = SDGSync(this) } }

	// "percussive.short" or [\percussive, \short] -> [\percussive, \short]: tidal's
	// strings take letters, digits and . - _ : and a space would make two events
	*splitTags { |value|
		if (value.isNil) { ^nil };
		if (value.isArray and: { value.isString.not }) { ^value.collect(_.asSymbol) };
		^value.asString.split($.).reject(_.isEmpty).collect(_.asSymbol)
	}

	// The diversion SuperDirt calls before it looks the sound up, in every
	// orbit's default event. With sdgtag, sdgnot, sdgbpm or sdgkey in the
	// event it picks a sample of the library that matches and puts it in s
	// and n: at random, or the n-th match when the event has an n. A bank in
	// s keeps the pick in that bank; no s, or sdgany, means the whole library.
	// Nothing matching: the event plays nothing. It answers what the diversion
	// it replaced answers, so it never stops an event by itself.
	prTagDiversion { |previous|
		^{ |dirtEvent|
			var hits, pick, bank;
			if (~sdgtagDone.isNil and: { ~sdgtag.notNil or: { ~sdgnot.notNil } or: { ~sdgbpm.notNil } or: { ~sdgkey.notNil } }) {
				~sdgtagDone = true;
				bank = if (#['', 'nil', \none].includes(~s) or: { ~s == anySoundName }) { nil } { ~s };
				hits = this.tags.query(this.class.splitTags(~sdgtag), this.class.splitTags(~sdgnot),
					~sdgbpm !? (_.asFloat), ~sdgkey !? (_.asString), bank);
				if (hits.isEmpty) {
					if (this.tags.isAnalysing.not and: { SDGSampleTags.autoAnalyse }) { this.tags.analyseLibrary };
					if (warnedPicker.not) {
						warnedPicker = true;
						"SuperDirtGUI: no sample has the tags asked for (%)%".format(
							[~sdgtag, ~sdgnot !? { |v| "not " ++ v }, ~sdgbpm, ~sdgkey].reject(_.isNil).join(", "),
							if (this.tags.isAnalysing) { " - the library is still being analysed" } { "" }).postln;
					};
					~s = '';	// SuperDirt plays nothing for an empty sound
				} {
					pick = if (~n.isNumber) { hits.wrapAt(~n.asInteger) } { hits.choose };
					~s = pick[0];
					~n = pick[1];
				};
			};
			previous.value(dirtEvent)
		}
	}

	prInstallTagPicker {
		(dirt.orbits ? []).do { |orbit|
			var current = orbit.defaultParentEvent[\diversion], mine;
			if (pickers[orbit].isNil or: { pickers[orbit][0] !== current }) {
				mine = this.prTagDiversion(current);
				pickers[orbit] = [mine, current];
				orbit.defaultParentEvent[\diversion] = mine;
			};
		};
	}

	// what was there before goes back, unless something replaced ours since
	prRemoveTagPicker {
		(pickers ? ()).keysValuesDo { |orbit, pair|
			if (orbit.defaultParentEvent[\diversion] === pair[0]) {
				if (pair[1].isNil) { orbit.defaultParentEvent.removeAt(\diversion) } {
					orbit.defaultParentEvent[\diversion] = pair[1]
				};
			};
		};
		pickers = IdentityDictionary.new;
	}

	// ----- sessions (see SDGSession)

	session { ^session ?? { session = SDGSession(this) } }

	// the whole mixer under a name, over any of that name. It takes a moment:
	// every plugin is asked for its state. action gets true or false
	saveSession { |name, action| this.session.save(name, action) }

	// a saved mixer back, in place of what the orbits hold now. action gets true
	// or false, once everything is playing
	loadSession { |name, action| this.session.load(name, action) }

	sessionNames { ^SDGSession.names }

	// the session saved or loaded last, nil before the first
	sessionName { ^this.session.name }

	// the sample editor on one sample of the library, playing through an orbit
	edit { |name, index = 0, orbit = 0|
		^SDGSampleEditor(this, name, index, orbit)
	}

	// ----- teardown

	// take our modules, players and global effects out of ~dirt and close the
	// window. synthdefs stay on the server; they do no harm and tidal may still
	// be using them
	free {
		returns.copy.do { |ret| this.removeReturn(ret) };
		// after the last of the returns' own frees
		returnGroupID !? { |id| SystemClock.sched(0.5, { dirt.server.sendMsg('/n_free', id); nil }) };
		returnGroupID = nil;
		returnHeadsID = nil;
		inserts.keys.asArray.do { |i| this.removeInserts(i) };
		treeFunc !? { ServerTree.remove(treeFunc, dirt.server); treeFunc = nil };
		this.prRemoveModules;
		this.prRemoveSamplePlayers;
		this.prRemoveGlobalEffects;
		this.prRemoveTagPicker;
		tags !? (_.stop);
		sync !? (_.free);
		sync = nil;
		silentBus !? { silentBus.free; silentBus = nil };
		this.prFreeTapBusses;
		(dirt.orbits ? []).do { |orbit|
			orbit.defaultParentEvent.removeAt(\rmsReplyRate);
			orbit.defaultParentEvent.removeAt(\rmsPeakLag);
		};
		fileRecords = Dictionary.new;
		globalEffectSpecs = [];
		controlWindow !? { |w| { w.close }.defer };
		controlWindow = nil;
		if(current === this) { current = nil };
		this.changed(\loaded);
	}

	printOn { |stream|
		stream << this.class.name << "(" << this.synthNames.size << " synths, "
			<< this.moduleNames.size << " modules, " << samplePlayers.size << " players, "
			<< globalEffectSpecs.size << " global effects)"
	}

	// ----- private

	prLoadFolder { |kind|
		var files = this.prFilesIn(kind);
		if(files.isEmpty) {
			if(verbose) { "SuperDirtGUI: nothing to load in %".format(this.dirFor(kind)).postln };
			^this
		};
		files.do { |file| this.prReadFile(file, kind) }
	}

	// Read one file with ~dirt and ~sdg bound, the way SuperDirt reads its own
	// synths/, and note what it defined. One that does not compile or throws keeps
	// what it defined before; one that runs has what it no longer defines taken
	// out - unless another file defines it too. Answers true when it ran. A file
	// whose "// requires:" line names a class that is not installed is not even
	// compiled: an unknown class is a compile error, and a plugin nobody has is no
	// error. It is told once and listed, and reads again once its text changes.
	prReadFile { |file, kind|
		var text = File.readAllString(file) ? "";
		var before = this.prNamesIn(file);
		var missing = this.class.missingClasses(text);
		var func, keep, snapshot, names, error, outer, gone;
		if(missing.notEmpty) {
			"SuperDirtGUI: % needs %, not installed - skipped".format(file.basename, missing.join(", ")).postln;
			fileRecords[file] = (kind: kind, names: before, hash: text.hash, failed: true, missing: missing);
			^false
		};
		func = this.prCompile(text);
		keep = if(before.isEmpty) { "" } { "; what it loaded before stays" };
		if(func.isNil) {
			"SuperDirtGUI: % does not compile - the parse error is posted above%".format(file.basename, keep).warn;
			fileRecords[file] = (kind: kind, names: before, hash: text.hash, failed: true);
			^false
		};
		snapshot = this.prSnapshot(kind);
		outer = loadingNames;
		loadingNames = [];
		try { this.prRun(func, file) } { |err| error = err };
		names = this.prDefinedSince(kind, snapshot, loadingNames);
		loadingNames = outer;
		if(error.notNil) {
			"SuperDirtGUI: % failed to load - %%".format(file.basename, error.errorString, keep).warn;
			fileRecords[file] = (kind: kind, names: this.prUnion(before, names), hash: text.hash, failed: true);
			^false
		};
		fileRecords[file] = (kind: kind, names: names, hash: text.hash, failed: false);
		gone = before.reject { |name| names.includes(name) or: { this.prClaimedElsewhere(kind, name, file) } };
		this.prTakeOut(kind, gone);
		if(verbose) { "SuperDirtGUI: loaded %".format(file.basename).postln };
		^true
	}

	// the classes a file's "// requires: A, B" line names and this sclang does not have
	*missingClasses { |text|
		var line = text.replace("\r", "").split($\n)
			.select { |x| x.beginsWith("//") }
			.detect { |x| x.drop(2).stripWhiteSpace.beginsWith("requires:") };
		if(line.isNil) { ^[] };
		^line.drop(line.find("requires:") + 9).split($,)
			.collect { |word| word.stripWhiteSpace.split($ ).first.asSymbol }	// "MiClouds (mi-UGens)"
			.select { |word| word.asString.notEmpty and: { word.asClass.isNil } }
	}

	// what String:load does: the interpreter's preprocessor, then the compiler.
	// nil, with the parse error posted, when the text does not compile
	prCompile { |text|
		var interpreter = thisProcess.interpreter;
		interpreter.preProcessor !? { |pre| text = pre.value(text, interpreter) };
		^interpreter.compile(text)
	}

	// and what executeFile does around it: the file's path while it runs
	prRun { |func, file|
		var saved = thisProcess.nowExecutingPath;
		protect {
			thisProcess.nowExecutingPath = file;
			(dirt: dirt, sdg: this).use { func.value };
		} {
			thisProcess.nowExecutingPath = saved;
		}
	}

	// What is there before a file runs, to see afterwards what it defined. Synths
	// and modules go straight into SuperDirt and SC, where redefining one replaces
	// the object under its name, so the objects are compared; players and global
	// effects say their names as they register (prRegistered).
	prSnapshot { |kind|
		^switch(kind,
			\synths, { SynthDescLib.global.synthDescs.copy },
			\modules, { dirt.modules.copy }
		)
	}

	prDefinedSince { |kind, snapshot, registered|
		var lib, ours, existing;
		^switch(kind,
			\synths, {
				lib = SynthDescLib.global.synthDescs;
				lib.keys.asArray.select { |key| snapshot[key] !== lib[key] }.sort
			},
			\modules, {
				// added, or one of ours replaced - never one of SuperDirt's that a
				// file happens to redefine, which free would then take away
				ours = this.moduleNames;
				existing = snapshot.collect(_.name);
				dirt.modules.reject { |module| snapshot.includes(module) }
					.collect(_.name)
					.select { |name| existing.includes(name).not or: { ours.includes(name) } }
					.reject { |name| name == playerModuleName }
			},
			{ this.prDistinct(registered ? []) }
		)
	}

	prRegistered { |name|
		loadingNames !? { loadingNames = loadingNames.add(name.asSymbol) }
	}

	// what a file defined and no longer does: modules and players come out,
	// global effects leave every orbit; synths stay on the server, as after free
	prTakeOut { |kind, names|
		if(names.isEmpty) { ^this };
		switch(kind,
			\modules, { names.do { |name| dirt.removeModule(name) } },
			\players, { names.do { |name| this.removeSamplePlayer(name) } },
			\effects, { this.prTakeOutGlobalEffects(names) }
		)
	}

	// a file that is gone: what it defined is taken out
	prForgetFile { |file|
		var record = fileRecords.removeAt(file);
		record !? {
			this.prTakeOut(record[\kind],
				record[\names].reject { |name| this.prClaimedElsewhere(record[\kind], name, file) });
			if(verbose) { "SuperDirtGUI: % is gone - took out %".format(file.basename, record[\names]).postln };
		}
	}

	prClaimedElsewhere { |kind, name, file|
		^fileRecords.keys.any { |other|
			other != file and: { fileRecords[other][\kind] == kind } and: { fileRecords[other][\names].includes(name) }
		}
	}

	prTakeOutGlobalEffects { |names|
		globalEffectSpecs = globalEffectSpecs.reject { |spec| names.includes(spec[\name]) };
		returns.do { |ret| if(names.includes(ret.name)) { this.setReturnEffect(ret, nil) } };
		(dirt.orbits ? []).do { |orbit|
			this.tracksOf(orbit.orbitIndex).select { |track| names.includes(track.name) }.do { |track|
				this.removeTrack(track)
			};
			orbit.globalEffects.do { |fx| if(names.includes(fx.name)) { this.releaseEffect(fx) } };
			orbit.globalEffects = orbit.globalEffects.reject { |fx| names.includes(fx.name) };
		}
	}

	// After an effect file was read again. An effect it defines for the first time
	// goes first into every orbit, where applyGlobalEffects puts it; one it defined
	// before is started again where it is, on its new SynthDef and parameters -
	// but not put back into an orbit it was taken out of. Nothing else in the
	// chain is touched, so the other effects keep their tails. GlobalDirtEffect.play
	// always starts a synth right after the orbit's group, at the head of the
	// chain, so one further down is moved back behind the effect before it.
	prPlaceGlobalEffects { |names, before|
		if(dirt.orbits.isNil or: { names.isEmpty }) { ^this };
		fork {
			dirt.server.sync; // the new SynthDefs first, see applyGlobalEffects
			dirt.orbits.do { |orbit|
				var args = this.prPlayArgs(orbit);
				orbit.globalEffects.do { |fx|
					var spec;
					if(before.includes(fx.name) and: { names.includes(fx.name) }) {
						spec = this.prSpecFor(fx.name);
						spec !? { fx.paramNames = spec[\paramNames]; fx.alwaysRun = spec[\alwaysRun] };
						this.releaseEffect(fx); // play's own release leaves a sleeping synth behind
						fx.play(*args);
					}
				};
				names.reject { |name| before.includes(name) }.do { |name|
					var spec = this.prSpecFor(name), fx;
					if(spec.notNil and: { spec[\offered] != true } and: { orbit.globalEffects.every { |x| x.name != name } }) {
						fx = GlobalDirtEffect(name, spec[\paramNames], dirt.numChannels).alwaysRun_(spec[\alwaysRun]);
						orbit.globalEffects = orbit.globalEffects.addFirst(fx);
						this.prSyncTracks(orbit);
						this.prChainOrbit(orbit);
						this.trackOf(fx) !? { |track| track.prPlay(args) };
					}
				};
				this.orderInserts(orbit);
				this.changed(\tracks, orbit.orbitIndex);
			};
			this.prRestartReturnEffects(names.select { |name| before.includes(name) });
		}
	}

	prSpecFor { |name| ^globalEffectSpecs.detect { |spec| spec[\name] == name } }

	// Every word that means something already, and what: our names, SuperDirt's
	// modules, the banks and sounds of the library, the keys of the orbits'
	// default events and the parameters of their effects, every SynthDef and every
	// parameter of one, and the keys read without being declared (eventWords).
	// The first meaning found is the one given.
	prWordsInUse {
		var words = IdentityDictionary.new;
		var note = { |list, meaning|
			list.do { |word| word = word.asSymbol; if(words[word].isNil) { words[word] = meaning } }
		};
		this.class.kinds.do { |kind|
			note.(this.namesOf(kind).collect { |name| this.class.baseName(kind, name) },
				"one of your %s".format(this.class.kindLabel(kind)))
		};
		note.(dirt.modules.collect(_.name), "a SuperDirt module");
		note.(dirt.soundLibrary.buffers.keys, "a sample bank");
		note.(dirt.soundLibrary.synthEvents.keys, "a sound of the library");
		note.(this.class.eventWords, "a key SuperDirt reads from every event");
		(dirt.orbits ? []).do { |orbit|
			note.(orbit.defaultParentEvent.keys, "a key of every orbit's default event");
			orbit.globalEffects.do { |fx| note.(fx.paramNames, "a parameter of %".format(fx.name)) };
		};
		SynthDescLib.global.synthDescs.keysDo { |key| note.([key], "a SynthDef") };
		SynthDescLib.global.synthDescs.keysValuesDo { |key, desc|
			note.(desc.controlNames ? [], "a parameter of the SynthDef %".format(key))
		};
		^words
	}

	// whether word stands in text on its own: no letter or digit right before or after
	*prHasWord { |text, word|
		var i = 0;
		while { (i = text.find(word, false, i)).notNil } {
			if(this.prIsWordAt(text, word, i)) { ^true };
			i = i + 1;
		};
		^false
	}

	// word replaced by with wherever it stands on its own; _ is a gap, not part
	// of a word, so global_sdgspace and sdgwarp_1_2 change with it
	*prReplaceWord { |text, word, with|
		var out = String.new, start = 0, i;
		while { (i = text.find(word, false, start)).notNil } {
			out = out ++ text.copyRange(start, i - 1) ++ (if(this.prIsWordAt(text, word, i)) { with } { word });
			start = i + word.size;
		};
		^out ++ text.copyToEnd(start)
	}

	*prIsWordAt { |text, word, i|
		var before = if(i > 0) { text[i - 1] };
		var after = text[i + word.size];
		^(before.isNil or: { before.isAlphaNum.not }) and: { after.isNil or: { after.isAlphaNum.not } }
	}

	// the .scd files of a kind's folder, as the folder lists them, in load order
	prFilesIn { |kind| ^(this.dirFor(kind) +/+ "*.scd").standardizePath.pathMatch.sort }

	// the files read for a kind, in load order
	prPathsOf { |kind|
		^fileRecords.keys.asArray.select { |file| fileRecords[file][\kind] == kind }.sort
	}

	prNamesIn { |file| ^(fileRecords[file] !? { |record| record[\names] }) ? [] }

	prNamesFromFiles { |kind|
		^this.prDistinct(this.prPathsOf(kind).collect { |file| fileRecords[file][\names] }.flatten)
	}

	// Whether two paths name the same place, however they are spelled: ~
	// expanded, \ read as /, no trailing separator, and - on Windows, whose
	// file system ignores it - no case. Comparing paths as plain Strings
	// fails there as soon as one side came from +/+ (which writes \) and the
	// other from somewhere that writes /.
	*samePath { |a, b|
		^a.notNil and: { b.notNil } and: { this.prNormalPath(a) == this.prNormalPath(b) }
	}

	*prNormalPath { |p|
		p = p.asString.standardizePath.tr($\\, $/);
		while { p.size > 1 and: { p.last == $/ } } { p = p.drop(-1) };
		^if(thisProcess.platform.name == \windows) { p.toLower } { p }
	}

	// the folder a file is in, by name
	prKindOf { |file|
		^this.class.kinds.detect { |kind| this.class.samePath(file.dirname, this.dirFor(kind)) }
	}

	// A file as the folder listing spells it - the spelling every record is kept
	// under - given relative to the repository or absolute, also when reached
	// another way, as through the real folder behind the extensions link. A
	// file that is gone is found among the records. nil when it is not in one
	// of the four folders.
	prOwnPath { |filePath|
		var file = filePath.asString.standardizePath, kind, real;
		if(PathName(file).isRelativePath) { file = path +/+ file };
		kind = this.prKindOf(file);
		if(kind.notNil) {
			^(this.prFilesIn(kind) ++ fileRecords.keys.asArray)
				.detect { |other| this.class.samePath(other, file) } ? file
		};
		if(File.exists(file).not) { ^nil };
		real = File.realpath(file);
		^this.class.kinds.collect { |k| this.prFilesIn(k) }.flatten
			.detect { |other| this.class.samePath(File.realpath(other), real) }
	}

	prHash { |file|
		^try { File.readAllString(file).hash }
	}

	// keeps the order things were loaded in
	prUnion { |old, new| ^old ++ new.reject { |name| old.includes(name) } }

	prDistinct { |names|
		^names.asArray.inject([]) { |out, name| if(out.includes(name)) { out } { out.add(name) } }
	}

	prRemoveModules {
		this.moduleNames.do { |name| dirt.removeModule(name) };
	}

	prRemoveSamplePlayers {
		samplePlayers = [];
		this.prRemovePlayerModule;
	}

	// Ours and the fader; the orbits are handed back the way they were, every
	// effect that stays on the orbit's own busses again. For a reload, keepTracks:
	// a track keeps its place, inserts and fader, and waits for its effect to
	// come back (prSyncTracks).
	prRemoveGlobalEffects { |keepTracks = false|
		var names = this.globalEffectNames ++ [faderEffectName];
		if(dirt.orbits.isNil) { ^this };
		dirt.orbits.do { |orbit|
			var i = orbit.orbitIndex;
			var mine = orbit.globalEffects.select { |fx| names.includes(fx.name) };
			if(keepTracks) {
				this.tracksOf(i).do { |track| if(names.includes(track.name)) { track.prDetach } }
			} {
				this.tracksOf(i).do { |track|
					track.inserts.do { |x| this.removeInsert(x) };
					track.prDetach;
					track.prFree;
					orbit.globalEffects = orbit.globalEffects.reject { |fx|
						fx === track.sendFx or: { fx === track.returnFx }
					};
				};
				tracks[i] = [];
			};
			if(mine.notEmpty or: { keepTracks.not }) {
				mine.do { |fx| this.releaseEffect(fx) };
				orbit.globalEffects = orbit.globalEffects.reject { |fx| names.includes(fx.name) };
				orbit.defaultParentEvent.removeAt(\orbitAmp);
				this.rebuildOrbit(orbit);
			}
		};
		appliedAmps = nil ! (appliedAmps.size ? 0);
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
