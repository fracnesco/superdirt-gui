// =====================================================================
// SDGWindow - the control window of a SuperDirtGUI, on a running SuperDirt
//
//   SDGWindow(~sdg);    // or: ~sdg.gui
//
// after ~dirt.start, i.e. once the orbits exist. Four areas:
//
//   strips     one per orbit, named after it - orbit 0 is where Tidal's d1
//              plays: its inserts, top first (an eq, a compressor or an
//              insight each - click one for its window, right click to
//              bypass, move or remove it, + insert to add one), rms/peak
//              meters per channel with the highest peak in dB under them
//              (via SuperDirt's dirt_rms global effect, 20 replies a
//              second), an output fader with mute and solo (via the orbitAmp
//              control of the add-on's global_sdgfader effect), the limiter
//              mode of the monitor, and where the orbit goes: the master, or
//              direct to the hardware bus in the box beside it (and a dropdown
//              of BusManager names when that quark is installed). A send knob
//              per return track, over the meters. A right click on its name:
//              fold it, copy and paste inserts.
//   returns    on the right, as in Ableton, a strip per return track (SDGReturn):
//              its effects (inserts), sends to the returns, fader, mute, solo,
//              pre/post of what is sent into it, limiter and output. + return adds
//              one, feedback lets returns send to each other. Then the master
//              (SDGMaster): everything that is not sent direct, its effects - a
//              mastering chain - its fader and the hardware bus it writes to.
//              Every strip folds thin with the arrow beside its name; an insert's
//              right click copies it, or its whole chain, and + insert, + effect
//              and the right click paste it onto any strip.
//   effects    the global effects of the selected orbit as tracks, a strip
//              each (SDGTrack): load another effect from its menu, switch it
//              on and off, move it, take it out, inserts before and after the
//              effect, a meter and a fader on what it adds. A click on a
//              strip's name shows its parameters as knobs under the strips;
//              add puts in any other SynthDef that follows the global-effect
//              convention (dryBus, effectBus, gate). Under them the same
//              orbit's inserts, with on, the arrows and the cross
//   sound      the sound explorer: every bank in the sound library as a
//   explorer   folder that opens onto its samples, the synth events under
//              them, a search over both; a sample plays through the \dirt event
//              type, and opens in the sample editor (SDGSampleEditor) to be
//              trimmed, gained, DC-corrected and saved back into the bank
//   modules    what the SuperDirtGUI read from its folders - synths, effects
//              (modules/), players, global effects - a list each, with new... (a
//              copy of the example, or of the one selected, under a name you
//              give) and edit... (the file a name came from, as a document in
//              the IDE); then reload, free and a bank loader, and at the bottom
//              what the selected one does: its description and parameters;
//              for a synth, a preview under it - knobs, note, preset, play,
//              and copy, which puts the settings on the clipboard as Tidal
//
// Along the top, sessions (SDGSession): a menu of the saved ones, load session
// for the one it shows, save session... to keep the whole mixer under a name.
// Choosing in the menu changes nothing until load session is pressed.
//
// midi learn (with the Modality toolkit): while it is on, a click on a fader,
// mute, solo, send, track fader, effect knob or the explorer's play waits for
// the next control moved on a controller, and maps it (SDGMidi). A blue ground
// marks what can be mapped, an orange one what is waiting; a mapped control
// shows blue (a knob's arc, a fader's handle, a button's letter). A right click
// on any of them learns, edits, removes or posts its mapping. Knobs follow a
// controller. The midi button opens SDGMidiWindow: controllers found and
// connected, learn mode on and off, every mapping in depth; it is lit while
// learn mode is on.
//
// The three panels on the right are tabs. A hint bar along the bottom says
// what the pointer is over.
//
// While the window is open it reads a content file again within a second of
// its being saved - from the IDE or any editor - through the SuperDirtGUI's
// reloadChangedFiles, so editing a player, module, effect or synth is: edit
// it, save it, hear it. A file with an error says so in the post window and
// the list marks it; what plays is the last version that loaded.
//
// Parameter edits are written into the orbit's defaultParentEvent *and*
// pushed to the running effect synth. That keeps the GlobalDirtEffect
// change-tracking consistent: the next event (from Tidal or a Pbind)
// carries the same value, so nothing flips back and no nil is ever sent
// to the server.
//
// The fader, mute and solo state lives in the SuperDirtGUI (setFader,
// setMute, setSolo); the window only shows it, and follows its \mixer
// notification. The same for the library: stock SuperDirt's sound library
// says nothing when it changes, so the window listens to the SuperDirtGUI's
// \samples and \reloaded notifications and, for banks loaded straight through
// ~dirt.loadSoundFiles, polls the library's size once a second.
//
// If the BusManager quark is installed (looked up by name at runtime - no
// compile-time dependency), SuperDirtGUI.registerBusses names every orbit's
// dryBus and globalEffectBus in its registry, so they can be metered, listened
// to and routed to by name from its window. The internal audio routing busses
// are not registered.
//
// Number boxes drag by the digit under the pointer when the DigitDrag
// extension is on the class path - nothing here asks for it.
// =====================================================================

SDGWindow {

	classvar <>paramSpecs;			// Symbol -> ControlSpec, for the effect parameter knobs
	classvar <>hiddenParams;		// SynthDef controls that are plumbing, never offered as parameters
	classvar <colBg, <colPanel, <colText, <colDim, <colAccent, <colOn, <colWarn;
	classvar <colWave, <colWaveDim, <colWaveBg, <colHandle, <colPlay, <colSel;
	classvar <>notesColumns = 68;	// how many characters fit across the notes of the modules tab
	classvar <>explorerTab = 1;		// the tab a new window opens on: 0 effects, 1 explorer, 2 modules
	classvar <>reloadOnSave = true;	// read a content file again when it is saved
	classvar <>codeEditor;			// a Function of a path that opens a content file; nil: see openCode
	classvar <>explorerColumnKeys;	// the explorer's columns in a new window
	classvar <>panelWidth = 450;	// the right panel's width in a new window: the last one dragged to
	classvar <>minPanelWidth = 360;
	classvar <bpmDialogWindow;		// the open bpm dialog: one at a time

	var <sdg, <dirt, <window, <hints;		// dirt is sdg.dirt, the SuperDirt
	var <selectedOrbitIndex = 0, selectedEffectName;
	var <selectedBus;				// a return or the master on show on the effects tab, in the orbit's place
	var busScroll, busHolder, busContent;	// where the returns and master go
	var <busViews;					// the return and master strips: one Event of views each
	var <collapsed;					// the names of the strips folded thin: "orbit 2", "return A", "master"
	var busManager;					// the BusManager class if installed, else nil
	var <strips;					// one Event of views per orbit
	var parentSnapshots;			// pristine defaultParentEvent values, for clearing parameters
	var meterData, <meterStamps, rmsFunc, meterRoutine;
	var <peakHolds;					// per orbit, the highest peak since a click on its readout
	var <tab = 0, tabButtons, pages;
	var effectHeader, trackScroll, trackCanvas, addMenu, addCandidates, addRow;
	var <trackViews;				// one Event of views per track of the selected orbit
	var trackMeterData, trackFunc;	// track id -> [stamp, numbers], and what fills it
	var insertListHolder, insertRowsView, vstWindow;
	var paramHolder, paramContentView;
	var rightColumn, panelDrag, <panelHandle;	// the right panel, where a drag of its handle started, the handle
	var <tree, searchField, soundInfo, countText, <explorerModel, <selected;
	var trigOrbitMenu, <trigNBox, <trigParams, trigSpecs, editBtn, revealBtn, menu;
	var loadedLists, <loadedEntries, loadedKind, fileText, pathText, newDialog;
	var notesView, <notesString;	// the description at the bottom of the modules tab, and what it says
	var previewHolder, previewContent, <preview;	// the selected synth's preview under the notes, and its model
	var watching = true;			// false once watching the folders failed
	var refreshPending = false;
	var librarySignature, libraryPoll;	// what the library looked like at the last refresh, and the routine that compares
	var <rebuilds = 0;				// how many times the tree was built from scratch (checks read it)
	var sessionMenu, sessionLoadBtn, <sessionNames, sessionDialog;
	var trigViews;					// key -> [knob, box], the explorer's speed, amp and pan
	var expandedBanks, searchCollapsed;	// the banks open in the list, and those closed while a search shows all
	var explorerColumns, sortKey = \name, sortAscending = true;
	var tagBar, tagDialog, tagsRefreshPending = false;
	var font, smallFont, boldFont;
	var <midiViews, <midiLearning = false, learnBtn, midiPaintPending = false;	// what a controller can move, and learn mode
	var <paramCells, paramRefreshPending = false;	// the knobs on show, to follow a controller

	*initClass {
		colBg = Color.grey(0.13);
		colPanel = Color.grey(0.19);
		colText = Color.grey(0.85);
		colDim = Color.grey(0.55);
		colAccent = Color(0.95, 0.65, 0.25);
		colOn = Color(0.45, 0.8, 0.45);
		colWarn = Color(0.9, 0.4, 0.35);
		colWave = Color(0.42, 0.76, 0.95);
		colWaveDim = Color(0.30, 0.33, 0.40);
		colWaveBg = Color(0.09, 0.10, 0.12);
		colHandle = Color(0.95, 0.95, 0.97);
		colPlay = Color(0.42, 0.94, 0.55);
		colSel = Color(0.42, 0.76, 0.95);
		explorerColumnKeys = [\name, \duration, \bpm, \key, \tags];

		// dirt_resumed is the control GlobalDirtEffect.resume sets in every def that uses DirtPause
		hiddenParams = [\dryBus, \effectBus, \outBus, \gate, \orbitIndex, \dirt_resumed, \resumed];
		paramSpecs = IdentityDictionary[
			\delaytime -> ControlSpec(0, 2, \lin, 0, 0.25),
			\delayfeedback -> ControlSpec(0, 0.95, \lin, 0, 0.5),
			\delaySend -> ControlSpec(0, 1, \lin, 0, 1),
			\delayAmp -> ControlSpec(0, 2, \lin, 0, 1),
			\lock -> ControlSpec(0, 1, \lin, 1, 0),
			\cps -> ControlSpec(0.05, 4, \exp, 0, 1),
			\size -> ControlSpec(0, 1, \lin, 0, 0.1),
			\room -> ControlSpec(0, 1, \lin, 0, 0),
			\dry -> ControlSpec(0, 1, \lin, 0, 0),
			\leslie -> ControlSpec(0, 1, \lin, 0, 0.5),
			\lrate -> ControlSpec(0.1, 20, \exp, 0, 6.7),
			\lsize -> ControlSpec(0, 2, \lin, 0, 0.3),
			\limitertype -> ControlSpec(0, 2, \lin, 1, 1),
			\orbitAmp -> ControlSpec(0, 1.5, \amp, 0, 1),
			\sdgspace -> ControlSpec(0, 1, \lin, 0, 0),
			\sdgtime -> ControlSpec(0.01, 1, \lin, 0, 0.3)
		];
	}

	// the knob of every parameter, here and in the sample editor: dragged up and down
	// (the default follows the pointer round the dial), half a percent a pixel.
	// default is where a double click puts it, through its action: a value of the
	// knob (0..1) or a function answering one, asked at the click; nil: no reset
	*makeKnob { |size = 36, centered = false, default|
		var knob = Knob().fixedWidth_(size).fixedHeight_(size)
			.mode_(\vert).step_(0.005).centered_(centered)
			.color_([Color.grey(0.32), colAccent, Color.grey(0.16), colText]);
		// true keeps the double click from the knob; nil passes any other click on
		knob.mouseDownAction = { |v, x, y, mods, button, clickCount|
			var unit;
			if(clickCount == 2 and: { (button ? 0) == 0 }) {
				unit = default.value;
				if(unit.isNumber) { v.valueAction = unit.clip(0, 1); true }
			}
		};
		^knob
	}

	// orbit effects that are plumbing, not for the effect list: SuperDirt's rms
	// and monitor, and the add-on's fader. asked of SuperDirtGUI each time, so
	// class initialisation order never matters
	*hiddenEffects {
		^SuperDirtGUI.plumbingEffectNames ++ [SuperDirtGUI.faderEffectName]
	}

	// ...and the inserts, which have a list of their own, and a track's send and return
	*isHiddenEffect { |name|
		^this.hiddenEffects.includes(name.asSymbol)
			or: { SDGInsert.isInsertName(name) }
			or: { SDGTrack.isTrackName(name) }
			or: { SDGReturn.isReturnName(name) }
	}

	*new { |sdg|
		sdg = sdg ? SuperDirtGUI.current;
		if(sdg.isNil) {
			Error("SDGWindow: no SuperDirtGUI instance. Pass one, e.g. SDGWindow(~sdg)").throw
		};
		if(sdg.dirt.orbits.isNil or: { sdg.dirt.orbits.isEmpty }) {
			Error("SDGWindow: this SuperDirt has no orbits yet - call ~dirt.start first").throw
		};
		^super.newCopyArgs(sdg).init
	}

	// every SynthDef for this channel count that follows the global effect convention
	// (dryBus, effectBus, gate), isn't plumbing and isn't already in the orbit
	*effectCandidates { |sdg, orbit|
		var suffix = sdg.dirt.numChannels.asString;
		var existing = orbit.globalEffects.collect(_.name);
		var out = [];
		SynthDescLib.global.synthDescs.keysValuesDo { |key, desc|
			var name = key.asString;
			var base, controls;
			if(name.endsWith(suffix) and: { name.size > suffix.size }) {
				base = name.copyRange(0, name.size - suffix.size - 1).asSymbol;
				controls = desc.controls.collect(_.name);
				if(controls.includes(\dryBus)
					and: { controls.includes(\effectBus) }
					and: { controls.includes(\gate) }
					and: { existing.includes(base).not }
					and: { this.isHiddenEffect(base).not }
				) { out = out.add(base) };
			};
		};
		^out.sort
	}

	init {
		var numOrbits;
		dirt = sdg.dirt;
		numOrbits = dirt.orbits.size;
		busManager = \BusManager.asClass;
		parentSnapshots = dirt.orbits.collect { |o| o.defaultParentEvent.copy };
		meterData = { 0.0 ! (dirt.numChannels * 2) } ! numOrbits;
		meterStamps = 0.0 ! numOrbits;
		peakHolds = 0.0 ! numOrbits;
		trackMeterData = IdentityDictionary.new;
		trackViews = [];
		busViews = [];
		collapsed = Set.new;
		midiViews = [];
		paramCells = List.new;
		font = Font(Font.defaultSansFace, 11);
		smallFont = Font(Font.defaultSansFace, 9);
		boldFont = Font(Font.defaultSansFace, 12, true);

		if(busManager.notNil) {
			SuperDirtGUI.registerBusses(dirt);
			busManager.addDependant(this);
		};
		sdg.addDependant(this);
		sdg.midi.addDependant(this);

		this.makeWindow;
		this.selectOrbit(0);
		this.refreshMixer;
		this.refreshBusMenus;
		this.refreshExplorer;
		this.refreshLoaded;
		this.selectTab(explorerTab);
		sdg.session.windowState !? { |state| this.applySessionState(state) };
		this.startMeters;
		this.startLibraryPoll;
		if(SDGSampleTags.autoAnalyse) { sdg.tags.analyseLibrary };
		window.front;
	}

	selectedOrbit { ^dirt.orbits[selectedOrbitIndex] }

	isOpen { ^window.notNil and: { window.isClosed.not } }

	front { if(this.isOpen) { window.front } }

	close { if(this.isOpen) { window.close } }

	// BusManager tells its dependants when the registry changes; the
	// SuperDirtGUI when the mixer moved, a bank was loaded, a sample rewritten,
	// or its files were read again
	update { |object, what ... args|
		if(busManager.notNil and: { object === busManager } and: { what == \buses }) {
			{ this.refreshBusMenus }.defer
		};
		if(object === sdg.tags and: { #[\tags, \analysis].includes(what) }) {
			{ this.prTagsChanged(what) }.defer
		};
		if(object === sdg) {
			switch(what,
				\mixer, { { this.refreshMixer }.defer },
				\samples, { { this.prLibraryChanged(\buffers, args) }.defer },
				\reloaded, { { this.prLibraryChanged(\reloaded, args) }.defer },
				\loaded, { { this.prLoadedChanged(args) }.defer },
				\inserts, { { this.prInsertsChanged(args[0]) }.defer },
				\tracks, { { this.prTracksChanged(args[0]) }.defer },
				\returns, { { this.prBussesChanged }.defer },
				\trackLevel, { { if(this.isOpen) { this.refreshTrackLevels } }.defer },
				\param, { this.prParamChanged },
				\session, { { this.prSessionLoaded(args[0]) }.defer },
				\sessions, { { if(this.isOpen) { this.prRefreshSessions(args[0]) } }.defer },
				\presets, { { this.prPresetsChanged(args[0]) }.defer },
				\cmdPeriod, {
					// the flags now, the rest on the (cleared) AppClock
					refreshPending = false; tagsRefreshPending = false;
					paramRefreshPending = false; midiPaintPending = false;
					{ this.prCmdPeriod }.defer
				}
			);
		};
		if(object === sdg.midi) {
			switch(what,
				\maps, { this.prMidiChanged },
				\map, { this.prMidiChanged },
				\learn, {
					this.prMidiChanged;
					args[0] !? { |m| { if(this.isOpen) { hints.note("% %  ->  %".format(m.ktl, m.element, m.target.label)) } }.defer }
				}
			)
		};
	}

	// after reload or free: the lists, the explorer, and the orbit's effects -
	// which come back a moment later, once applyGlobalEffects has synced the
	// server, so the effect section is read a second time after that. After one
	// file was read (args: its kind and path), only what that file can change.
	prLoadedChanged { |args|
		var kind = args !? { args[0] }, file = args !? { args[1] };
		if(this.isOpen.not) { ^this };
		this.refreshLoaded;
		file !? { this.prNoteFile(file) };
		if(kind.isNil) { this.prScheduleExplorerRefresh };
		if(kind.isNil or: { kind == \effects }) {
			this.rebuildEffectSection;
			AppClock.sched(1.0, {
				if(this.isOpen) { this.rebuildEffectSection };
				nil
			});
		}
	}

	// The list is rebuilt a moment after the last change, and keeps which banks
	// are open and what was selected. A rewritten sample is analysed again, new
	// ones join the analysis queue.
	prLibraryChanged { |what, args|
		var name = args !? { args[0] };
		if(window.isNil or: { window.isClosed }) { ^this };
		if(what == \reloaded) {
			sdg.bufferAt(name, args[1] ? 0) !? { |buf| buf.path !? { |p| sdg.tags.enqueue([p], true) } };
		} {
			if(name.notNil) { sdg.tags.enqueue(sdg.tags.pathsOf(name)) };
		};
		this.prScheduleExplorerRefresh;
	}

	// the tags: a change to one sample, or many, or the analysis moving on - the
	// list follows at most every second and a half while the queue runs
	prTagsChanged { |what|
		if(this.isOpen.not) { ^this };
		if(what == \analysis) { this.prRefreshCount };
		if(tagsRefreshPending) { ^this };
		tagsRefreshPending = true;
		AppClock.sched(if(sdg.tags.isAnalysing) { 1.5 } { 0.2 }, {
			tagsRefreshPending = false;
			if(this.isOpen) { this.refreshExplorer };
			nil
		});
	}

	// a bank loads one file at a time and says so each time; one refresh a
	// moment after the last is enough
	prScheduleExplorerRefresh {
		if(refreshPending) { ^this };
		refreshPending = true;
		AppClock.sched(0.3, {
			refreshPending = false;
			if(window.notNil and: { window.isClosed.not }) { this.refreshExplorer };
			nil
		});
	}

	// stock SuperDirt's sound library says nothing when ~dirt.loadSoundFiles
	// adds to it, so once a second the window compares its size with what the
	// explorer last showed. The same second it asks the SuperDirtGUI to read
	// again the content files that changed on disk, which is how a file saved in
	// the IDE is heard.
	prLibrarySignature {
		var lib = dirt.soundLibrary;
		^[lib.buffers.size, lib.buffers.sum { |a| a.size }, lib.synthEvents.size]
	}

	startLibraryPoll {
		librarySignature = this.prLibrarySignature;
		libraryPoll = Routine {
			loop {
				1.0.wait;
				try {
					var sig = this.prLibrarySignature;
					if(sig != librarySignature) {
						librarySignature = sig;
						this.prScheduleExplorerRefresh;
					};
				};
				try { if(SDGSession.names != sessionNames) { this.prRefreshSessions } };
				if(reloadOnSave and: { watching }) {
					try { sdg.reloadChangedFiles } { |err|
						watching = false; // once, not every second
						"SDGWindow: stopped reading saved files again - %".format(err.errorString).warn;
					}
				};
			}
		}.play(AppClock);
	}

	// ------------------------------------------------------------------ window

	makeWindow {
		var visibleStrips = dirt.orbits.size.min(8);
		var width = (visibleStrips * 96) + 190 + panelWidth.max(minPanelWidth) + 20;
		window = Window("SuperDirtGUI", Rect(80, 60, width, 760)).background_(colBg);
		if(GUI.id == \qt) { window.view.palette = QPalette.dark };
		hints = SDGHintBar("hover over a control and this bar says what it does"
			"   -   in the explorer: enter plays, e edits, right click for the file");
		window.layout = VLayout(
			this.makeTopBar,
			HLayout(
				this.makeStripArea,
				this.makePanelHandle,
				this.makeRightColumn
			).spacing_(2),
			hints.view
		).margins_(8).spacing_(6);
		hints.watch(window);
		window.onClose = { this.cleanup };
	}

	makeTopBar {
		var title = StaticText().string_("SuperDirtGUI")
			.font_(Font.sansSerif(14, true)).stringColor_(colAccent);
		var info = StaticText().stringColor_(colDim).string_(
			"% orbits · % channels · port %".format(
				dirt.orbits.size, dirt.numChannels, dirt.port ? "-"));
		var recBtn, saveBtn, tidalBtn;
		var busBtn = Button().fixedWidth_(110)
			.states_([["bus manager", colText, colPanel]]);
		busBtn.action = { busManager !? { busManager.gui } };
		busBtn.enabled = busManager.notNil;
		hints.attach(busBtn, "the BusManager window - the orbit busses are named in it");
		recBtn = Button().fixedWidth_(110)
			.states_([["recorder", colText, colPanel]]);
		recBtn.action = { sdg.recorder };
		recBtn.enabled = \BusRecorder.asClass.notNil;
		hints.attach(recBtn, "the BusRecorder window - ready to write each orbit to a stereo file of its name, after its effects and fader");
		tidalBtn = Button().fixedWidth_(90)
			.states_([["from tidal", colText, colPanel]]);
		tidalBtn.action = { sdg.tidalLog.gui };
		hints.attach(tidalBtn, "a window with every event Tidal sends, as text: pattern, orbit, cycle, sound and parameters");
		// one button for all of MIDI: it opens SDGMidiWindow (controllers, learn,
		// mappings) and lights while learn mode is on
		learnBtn = Button().fixedWidth_(64).states_([
			["midi", colText, colPanel],
			["midi", Color.black, colSel]
		]);
		learnBtn.action = { |b| b.value = midiLearning.binaryValue; sdg.midi.gui };
		hints.attach(learnBtn, if(SDGMidi.isAvailable) {
			"MIDI: find and connect a controller, midi learn, every mapping - lit while midi learn is on"
		} { "MIDI: Tidal controls only - controllers need the Modality-toolkit quark" });
		sessionMenu = PopUpMenu().fixedWidth_(150);
		sessionLoadBtn = Button().fixedWidth_(86);
		sessionLoadBtn.action = { this.loadSelectedSession };
		saveBtn = Button().fixedWidth_(86).states_([["save session", colText, colPanel]]);
		saveBtn.action = { this.saveSessionDialog };
		hints.attach(sessionMenu, "the saved sessions - choosing one changes nothing until you press load session");
		hints.attach(sessionLoadBtn, "replace the mixer with the session in the menu: faders, tracks, parameters, inserts, VST states");
		hints.attach(saveBtn, "keep the whole mixer under a name, every fader, parameter, insert and VST state");
		this.prRefreshSessions;
		^HLayout(title, info, nil, sessionMenu, sessionLoadBtn, saveBtn, learnBtn, tidalBtn, busBtn, recBtn)
	}

	// ------------------------------------------------------------------ sessions

	// the session the menu shows, nil when there are none
	selectedSession { ^sessionNames !? { sessionNames[sessionMenu.value ? 0] } }

	// the menu to the folder, on the session named, or else on the one it showed
	prRefreshSessions { |select|
		var wanted = select ?? { this.selectedSession } ?? { sdg.sessionName };
		var any;
		sessionNames = SDGSession.names;
		any = sessionNames.notEmpty;
		sessionMenu.items = if(any) { sessionNames } { ["no sessions yet"] };
		sessionMenu.value = sessionNames.indexOfEqual(wanted) ? 0;
		// a disabled button keeps the colours it was given: dim them to show it
		sessionLoadBtn.states = [["load session"] ++ if(any) { [colText, colPanel] } { [colDim, colBg] }];
		sessionLoadBtn.enabled = any;
	}

	// what load session does. Answers nothing: a load takes a moment, and the hint
	// bar says how it went
	loadSelectedSession {
		var name = this.selectedSession;
		if(name.isNil) { ^hints.note("no session to load yet - save session keeps one") };
		hints.note("loading session %...".format(name));
		sdg.loadSession(name, { |ok|
			var late = sdg.session.problems ? [];
			{
				if(this.isOpen) {
					hints.note(case
						{ ok.not } { "session % did not load - the post window says why".format(name) }
						{ late.notEmpty } { "session % loaded, without % things - the post window lists them".format(name, late.size) }
						{ "session % loaded".format(name) })
				}
			}.defer
		});
	}

	// A small window asking for a name, starting from the session last saved or
	// loaded. It says as you type whether the name will do, and whether it saves
	// over one. Answers the dialog's Window.
	saveSessionDialog {
		var field, info, saveBtn, cancelBtn, check, save;
		sessionDialog !? { |w| if(w.isClosed.not) { w.close } };
		sessionDialog = Window("save session", Rect(0, 0, 420, 110).center_(Window.availableBounds.center), false)
			.background_(colBg);
		if(GUI.id == \qt) { sessionDialog.view.palette = QPalette.dark };
		field = TextField().string_(sdg.sessionName ? "");
		info = StaticText().font_(smallFont);
		cancelBtn = Button().fixedWidth_(80).states_([["cancel", colText, Color.grey(0.26)]])
			.action_({ sessionDialog.close });
		saveBtn = Button().fixedWidth_(80);
		check = {
			var name = field.string, problem = if(name.notEmpty) { SDGSession.nameProblem(name) };
			var over = name.notEmpty and: { problem.isNil } and: { SDGSession.exists(name) };
			var ok = name.notEmpty and: { problem.isNil };
			info.stringColor = if(problem.isNil and: { over.not }) { colDim } { colWarn };
			info.string = problem ?? {
				case
				{ over } { "there is a session called this - saving replaces it" }
				{ name.isEmpty } { "a name for this session" }
				{ SDGSession.pathOf(name) }
			};
			saveBtn.states = [case
				{ ok.not } { ["save", colDim, Color.grey(0.26)] }
				{ over } { ["replace", Color.black, colWarn] }
				{ ["save", Color.black, colAccent] }
			];
			saveBtn.enabled = ok;
			ok
		};
		save = {
			if(check.value) {
				this.saveSession(field.string);
				sessionDialog.close;
			}
		};
		field.keyUpAction = { check.value };
		field.action = { save.value };		// enter
		field.keyDownAction = { |v, char, mod, uni, keycode, key|
			if(key == 16r1000000) { sessionDialog.close; true } { nil }		// escape
		};
		saveBtn.action = { save.value };
		sessionDialog.layout = VLayout(
			HLayout(StaticText().string_("name").stringColor_(colDim).fixedWidth_(36), field),
			info,
			HLayout(nil, cancelBtn, saveBtn)
		).margins_(10).spacing_(6);
		check.value;
		sessionDialog.front;
		field.focus(true);
		^sessionDialog
	}

	// SuperDirtGUI:saveSession, and the hint bar says how it went
	saveSession { |name|
		hints.note("saving session %...".format(name));
		sdg.saveSession(name, { |ok|
			{
				if(this.isOpen) {
					hints.note(if(ok) { "session % saved".format(name) } { "session % not saved - the post window says why".format(name) })
				}
			}.defer
		});
	}

	// after a load: everything the window shows of the mixer
	prSessionLoaded { |name|
		if(this.isOpen.not) { ^this };
		strips.do { |strip, i|
			strip[\lim].value = (dirt.orbits[i].get(\limitertype) ? 1).asInteger.clip(0, 2);
			this.rebuildStripInserts(i);
		};
		this.prReturnsChanged;
		this.refreshMixer;
		this.refreshBusMenus;
		sdg.session.windowState !? { |state| this.applySessionState(state) };
		this.rebuildEffectSection;
		this.prRefreshSessions(name);
	}

	// the window's part of a session: the tab and orbit on show, where the explorer
	// plays, and its speed, amp and pan
	sessionState {
		^(tab: tab, orbit: selectedOrbitIndex, playOrbit: trigOrbitMenu.value ? 0, trig: trigParams.copy,
			sortKey: sortKey, sortAscending: sortAscending, columns: explorerColumns.copy, panelWidth: this.panelWidth,
			collapsed: collapsed.asArray.sort)
	}

	applySessionState { |state|
		state[\tab] !? { |v| this.selectTab(v) };
		state[\orbit] !? { |v| this.selectOrbit(v) };
		state[\playOrbit] !? { |v| trigOrbitMenu.value = v.clip(0, dirt.orbits.size - 1) };
		state[\columns] !? { |v| this.explorerColumns = v };
		state[\panelWidth] !? { |v| this.setPanelWidth(v) };
		// a session from before folding had none folded
		collapsed.copy.do { |key| this.setCollapsed(key, false) };
		(state[\collapsed] ? []).do { |key| this.setCollapsed(key, true) };
		state[\sortKey] !? { |v| this.sortBy(v, state[\sortAscending] ? true) };
		state[\trig] !? { |values|
			values.keysValuesDo { |key, value|
				trigViews[key] !? { |views|
					trigParams[key] = value;
					views[0].value = trigSpecs[key].unmap(value);
					views[1].value = value;
				}
			}
		};
	}

	// -------------------------------------------------------------- orbit strips

	// The orbits scroll, on the left; the returns and the master stay on the
	// right, as in Ableton
	makeStripArea {
		// vertical as well: a strip with many inserts grows taller than the window
		var scroll = ScrollView().autohidesScrollers_(true).hasBorder_(false).background_(colBg);
		var canvas = View().background_(colBg);
		strips = dirt.orbits.collect { |orbit, i| this.makeStrip(orbit, i) };
		canvas.layout = HLayout(*(strips.collect(_[\view]) ++ [nil]))
			.margins_(0).spacing_(5);
		scroll.canvas = canvas;
		// one strip at least: the right panel may take the rest, and the strips scroll
		scroll.minWidth_(96).minHeight_(400);
		busScroll = ScrollView().autohidesScrollers_(true).hasBorder_(false).hasHorizontalScroller_(false)
			.background_(colBg);
		busHolder = View().background_(colBg);
		busHolder.layout = HLayout().margins_(0).spacing_(0);
		busScroll.canvas = busHolder;
		strips.size.do { |i| this.rebuildStripInserts(i) };
		strips.do { |strip| this.prApplyCollapse(strip) };
		this.prBussesChanged;
		^View().layout_(HLayout([scroll, stretch: 1], busScroll).margins_(0).spacing_(5))
	}

	makeStrip { |orbit, i|
		var ampSpec = paramSpecs[\orbitAmp];
		var key = "orbit " ++ i;
		var view = View().fixedWidth_(90).background_(colPanel);
		var label = Button().fixedHeight_(22).states_([
			["orbit " ++ i, colText, Color.grey(0.26)],
			["orbit " ++ i, Color.black, colAccent]
		]);
		var fold = this.prFoldButton(key);
		var insertHolder = View().layout_(VLayout().margins_(0).spacing_(0));
		var sendHolder = View().layout_(VLayout().margins_(0).spacing_(0));
		var addInsert = Button().fixedHeight_(18).font_(smallFont)
			.states_([["+ insert", colDim, Color.grey(0.21)]]);
		// 0 at -60 dB, 1 at 0 dB, a tick every 5 dB: warning from -9 dB, critical from -3 dB
		var meters = { LevelIndicator().fixedWidth_(13).minHeight_(150)
			.drawsPeak_(true).warning_(0.85).critical_(0.95)
			.numTicks_(13).numMajorTicks_(3) } ! dirt.numChannels;
		var peak = Button().fixedHeight_(16).font_(smallFont).states_([
			["-inf", colDim, Color.grey(0.16)],
			["-inf", Color.black, colWarn]
		]);
		var fader = Slider().fixedWidth_(22).minHeight_(150);
		var faderText = StaticText().fixedHeight_(14).align_(\right).stringColor_(colText).font_(smallFont);
		var mute = Button().fixedHeight_(20).states_([
			["M", colDim, Color.grey(0.26)],
			["M", Color.black, colWarn]
		]);
		var solo = Button().fixedHeight_(20).states_([
			["S", colDim, Color.grey(0.26)],
			["S", Color.black, colAccent]
		]);
		var lim = PopUpMenu().fixedHeight_(20).items_(["raw", "limit", "clip"]);
		var destMenu = PopUpMenu().fixedHeight_(20).font_(smallFont);
		var outBox = NumberBox().fixedHeight_(20).fixedWidth_(28).clipLo_(0).step_(1).decimals_(0);
		var outMenu, head, thin, strip;

		label.action = { this.selectOrbit(i) };
		label.mouseDownAction = { |v, x, y, mods, button|
			if((button ? 0) == 1) { { this.prShowOrbitMenu(i) }.defer(0.05) };
			nil
		};
		addInsert.action = { this.prShowAddInsertMenu(i) };
		peak.action = { peakHolds[i] = 0.0; this.prShowPeak(i) };

		// the fader, mute and solo show the SuperDirtGUI's state (refreshMixer)
		// and hand every change back to it
		fader.action = { |sl| sdg.setFader(i, ampSpec.map(sl.value)) };
		mute.action = { |b| sdg.setMute(i, b.value == 1) };
		solo.action = { |b| sdg.setSolo(i, b.value == 1) };
		this.prMidiAttach(fader, [\fader, i]);
		this.prMidiAttach(mute, [\mute, i]);
		this.prMidiAttach(solo, [\solo, i]);

		lim.value = (orbit.get(\limitertype) ? 1).asInteger.clip(0, 2);
		lim.action = { |m| sdg.setOrbitParam(i, \limitertype, m.value) };

		destMenu.action = { |m|
			var dest = (strips[i][\destItems] ? [])[m.value];
			// it rebuilds the strips' menus, this one too: not from inside its own action
			dest !? { { sdg.setOrbitDest(i, dest) }.defer };
		};
		outBox.action = { |b|
			{ sdg.setOrbitDest(i, \direct, b.value.asInteger.max(0)) }.defer;
		};

		if(busManager.notNil) {
			outMenu = PopUpMenu().fixedHeight_(20).items_(["bus…"]);
			outMenu.action = { |m|
				var names = strips[i][\outMenuNames] ? [];
				var index;
				if(m.value > 0 and: { names.size >= m.value }) {
					index = busManager.index(names[m.value - 1]);
					index !? { { sdg.setOrbitDest(i, \direct, index) }.defer };
				};
			};
		};

		hints.attach(label, "orbit % - where d% plays; selects it for the effects tab, and the explorer plays into it - right click: copy, paste, fold".format(i, i + 1));
		hints.attach(addInsert, "add an insert to orbit % - an eq, a compressor or an insight, after the ones above, before the fader".format(i));
		hints.attach(meters, "peak and rms of each channel leaving the orbit, after its inserts and fader - a tick every 5 dB");
		hints.attach(peak, "the highest peak since you last clicked here, in dB - red once it reached 0");
		hints.attach([fader, faderText], "the orbit's output level, in dB under it - the orbitAmp of its fader, before the monitor");
		hints.attach(mute, "silence this orbit");
		hints.attach(solo, "silence every other orbit");
		hints.attach(lim, "how the monitor guards the output: nothing, a limiter, or a hard clip");
		hints.attach(destMenu, "where this orbit goes: the master, or direct to the hardware bus beside it");
		hints.attach(outBox, "the hardware bus this orbit writes to when it goes direct - typing one sends it there");
		outMenu !? { hints.attach(outMenu, "direct to a bus, by BusManager name") };

		head = View().layout_(VLayout(HLayout(label, fold).spacing_(1), insertHolder, addInsert, nil).margins_(0).spacing_(4));
		view.layout = VLayout(
			head,
			sendHolder,
			[HLayout(*(meters ++ [fader])).spacing_(2), stretch: 1],
			View().fixedHeight_(this.prFootHeight).layout_(VLayout(
				faderText,
				peak,
				HLayout(mute, solo).spacing_(2),
				lim,
				HLayout(destMenu, outBox).spacing_(2),
				outMenu,   // nil is fine here: layouts skip it
				nil
			).margins_(0).spacing_(4))
		).margins_(4).spacing_(4);

		thin = this.prThinStrip(key, "orbit " ++ i, colPanel,
			{ this.selectOrbit(i) }, { |b| sdg.setMute(i, b.value == 1) }, { |b| sdg.setSolo(i, b.value == 1) });
		strip = (head: head, label: label, insertHolder: insertHolder, addInsert: addInsert, sendHolder: sendHolder,
			meters: meters, peak: peak, peakShown: "-inf", fader: fader, faderText: faderText,
			mute: mute, solo: solo, lim: lim, destMenu: destMenu, outBox: outBox, outMenu: outMenu);
		^this.prWrapStrip(strip, view, thin, key, 90)
	}

	// --------------------------------------------------------------- folding

	// A strip as two views side by side, one of them shown: the whole strip, or a
	// thin one with its name upright, a meter, mute and solo. strip is its Event
	// of views, which gets the outer view as its view
	prWrapStrip { |strip, full, thin, key, width|
		var outer = View().layout_(HLayout(full, thin[\view]).margins_(0).spacing_(0));
		^strip.putAll((view: outer, full: full, thin: thin[\view], thinMeter: thin[\meter],
			thinMute: thin[\mute], thinSolo: thin[\solo], thinLabel: thin[\label], key: key, width: width))
	}

	// the button beside a strip's name that folds it
	prFoldButton { |key|
		var b = Button().fixedWidth_(14).fixedHeight_(22).font_(smallFont)
			.states_([["‹", colDim, Color.grey(0.26)]]);
		b.action = { { this.setCollapsed(key, true) }.defer };
		hints.attach(b, "fold this strip thin - its name, a meter, mute and solo; click the arrow on it to unfold");
		^b
	}

	// The thin strip: unfold, the name upright (a click selects), one meter of all
	// channels, mute and solo when muteAction and soloAction are given
	prThinStrip { |key, name, colour, selectAction, muteAction, soloAction|
		var view = View().fixedWidth_(this.class.thinWidth).background_(colour);
		var unfold = Button().fixedHeight_(22).font_(smallFont).states_([["›", colText, Color.grey(0.3)]]);
		var label = UserView().minHeight_(60);
		var meter = LevelIndicator().fixedWidth_(10).minHeight_(150).drawsPeak_(true)
			.warning_(0.85).critical_(0.95);
		var mute = muteAction !? { Button().fixedHeight_(18).font_(smallFont)
			.states_([["M", colDim, Color.grey(0.26)], ["M", Color.black, colWarn]]).action_(muteAction) };
		var solo = soloAction !? { Button().fixedHeight_(18).font_(smallFont)
			.states_([["S", colDim, Color.grey(0.26)], ["S", Color.black, colAccent]]).action_(soloAction) };
		label.drawFunc = { |v|
			var b = v.bounds;
			Pen.translate((b.width / 2) - 6, b.height - 4);
			Pen.rotate(-0.5pi);
			Pen.stringAtPoint(name, 0@0, boldFont, colText);
		};
		label.mouseUpAction = { selectAction.value };
		unfold.action = { { this.setCollapsed(key, false) }.defer };
		hints.attach(unfold, "unfold %".format(name));
		hints.attach(label, "% - click to select it".format(name));
		hints.attach(meter, "the loudest channel of %".format(name));
		view.layout = VLayout(*([unfold, [label, stretch: 1], [meter, align: \center], mute, solo].reject(_.isNil)))
			.margins_(2).spacing_(3);
		^(view: view, meter: meter, mute: mute, solo: solo, label: label)
	}

	*thinWidth { ^24 }

	isCollapsed { |key| ^collapsed.includes(key.asString) }

	// fold a strip thin, or unfold it, by its name: "orbit 2", "return A", "master"
	setCollapsed { |key, bool = true|
		key = key.asString;
		if(bool) { collapsed.add(key) } { collapsed.remove(key) };
		if(this.isOpen.not) { ^this };
		(strips ++ busViews).do { |views| if(views[\key] == key) { this.prApplyCollapse(views) } };
		this.prFitBusArea;
	}

	prApplyCollapse { |views|
		var thin = this.isCollapsed(views[\key]);
		views[\full].visible = thin.not;
		views[\thin].visible = thin;
		views[\view].fixedWidth = if(thin) { this.class.thinWidth } { views[\width] };
	}

	// ---------------------------------------------------------------- routing

	// what an orbit's output menu offers, and the routes they stand for
	prDestItems { ^[\master, \direct] }

	prDestLabel { |dest| ^dest.asString }

	// the orbits' and bus tracks' output menus and bus numbers to where they go
	refreshRouting {
		var items = this.prDestItems;
		strips.do { |strip, i|
			var dest = sdg.orbitDest(i);
			strip[\destItems] = items;
			strip[\destMenu].items = items.collect { |x| this.prDestLabel(x) };
			strip[\destMenu].value = items.indexOf(dest) ? 0;
			strip[\outBox].value = sdg.directBusOf(i);
			strip[\outBox].stringColor = if(dest == \direct) { colText } { colDim };
		};
		busViews.do { |views|
			var track = views[\track];
			views[\destMenu] !? { |m| m.value = if(track.dest == \direct) { 1 } { 0 } };
			views[\outBox] !? { |b|
				b.value = track.directBus;
				if(track.isKindOf(SDGMaster).not) { b.stringColor = if(track.dest == \direct) { colText } { colDim } };
			};
		};
	}

	// right click on an orbit's name
	prShowOrbitMenu { |i|
		var clip = sdg.insertClipboard ? [], track = sdg.trackClipboard;
		menu !? (_.destroy);
		menu = Menu(*([
			MenuAction("orbit %".format(i)).enabled_(false),
			MenuAction.separator,
			MenuAction(if(this.isCollapsed("orbit " ++ i)) { "unfold" } { "fold thin" }, {
				this.setCollapsed("orbit " ++ i, this.isCollapsed("orbit " ++ i).not)
			}),
			MenuAction.separator,
			MenuAction("copy its inserts (%)".format(sdg.insertsOf(i).size), { sdg.copyInserts(sdg.insertsOf(i)) })
				.enabled_(sdg.insertsOf(i).notEmpty),
			MenuAction(this.prPasteLabel, { sdg.pasteInserts(i) }).enabled_(clip.notEmpty),
			MenuAction(if(track.notNil) { "paste effect track %".format(this.class.effectShortName(track[\name])) } { "paste effect track" }, {
				if(sdg.pasteTrack(i).isNil) { hints.note("orbit % has % already, or it is gone".format(i, track[\name])) }
			}).enabled_(track.notNil)
		]));
		menu.front;
	}

	*effectShortName { |name|
		var str = name.asString;
		^case
		{ str.beginsWith("global_") } { str.drop(7) }
		{ str.beginsWith("dirt_") } { str.drop(5) }
		{ str }
	}

	// what the clipboard holds, as a menu says it
	prPasteLabel {
		var clip = sdg.insertClipboard ? [];
		^case
		{ clip.isEmpty } { "paste" }
		{ clip.size == 1 } { "paste %".format(this.prDataLabel(clip[0])) }
		{ "paste % effects (%)".format(clip.size, clip.collect { |d| this.prDataLabel(d) }.join(", ")) }
	}

	prDataLabel { |data|
		^if(data[\kind] == \vst) { PathName(data[\plugin].asString).fileNameWithoutExtension } {
			SDGInsert.kindLabel(data[\kind]) ? data[\kind].asString
		}
	}

	// ---------------------------------------------------------------- bus tracks

	// a send knob: nothing to 0 dB, a dB readout in the hint bar as it moves. Answers
	// its Event; what it holds shows through refreshMixer
	prSendKnob { |ret, value, enabled, action, hint|
		var spec = this.class.sendSpec;
		var knob = this.class.makeKnob(24, false, spec.unmap(spec.default)).value_(spec.unmap(value));
		var text = StaticText().fixedHeight_(12).string_(ret.letter.asString).align_(\center).font_(smallFont)
			.stringColor_(if(enabled) { colAccent } { colDim });
		knob.enabled = enabled;
		if(enabled.not) { knob.color = knob.color.put(1, colDim) };
		knob.action = { |k|
			var amp = spec.map(k.value);
			action.value(amp);
			hints.note("% - %".format(hint, this.class.faderString(amp)));
		};
		hints.attach([knob, text], hint);
		^(view: View().fixedWidth_(26).layout_(VLayout(knob, text).margins_(0).spacing_(0)), knob: knob, ret: ret)
	}

	// send knobs three to a row: a strip is narrow and returns have no limit. Its height
	// is fixed, 36 a row, so every strip's meters start level; the master has none,
	// and room for as many
	prSendGrid { |cells, count|
		var rows = ((count ? cells.size) / 3).ceil.asInteger;
		^View().fixedHeight_((rows * 38 - 2).max(0)).layout_(VLayout(*(cells.collect(_[\view]).clump(3).collect { |row|
			HLayout(*(row ++ [nil])).margins_(0).spacing_(1)
		})).margins_(0).spacing_(2))
	}

	rebuildStripSends { |i|
		var strip = strips !? { strips[i] }, cells;
		if(strip.isNil) { ^this };
		strip[\sendView] !? { |v| v.remove };
		cells = sdg.returns.collect { |ret|
			var cell = this.prSendKnob(ret, ret.orbitLevel(i), true, { |amp| sdg.setSend(i, ret, amp) },
				"how much of orbit % goes to return %, %".format(i, ret.letter,
					if(ret.preFader) { "before its fader" } { "after its fader" }));
			this.prMidiAttach(cell[\knob], [\send, i, ret.letter]);
			cell
		};
		strip[\sendKnobs] = cells;
		strip[\sendView] = this.prSendGrid(cells);
		strip[\sendHolder].layout.add(strip[\sendView]);
	}

	*busColour { |track|
		^case
		{ track.isKindOf(SDGMaster) } { Color(0.28, 0.23, 0.17) }
		{ Color(0.20, 0.24, 0.27) }
	}

	// A return's or the master's strip: as an orbit's, with sends to the returns
	// (none on the master), and its output to the master or the hardware
	makeBusStrip { |track|
		var ampSpec = paramSpecs[\orbitAmp];
		var isMaster = track.isKindOf(SDGMaster), isReturn = track.isKindOf(SDGReturn);
		var key = track.shortName, colour = this.class.busColour(track);
		var view = View().fixedWidth_(92).background_(colour);
		var label = Button().fixedHeight_(22).states_([
			[track.shortName, colText, colour.blend(Color.grey(0.5), 0.25)],
			[track.shortName, Color.black, colSel]
		]).value_((track === selectedBus).binaryValue);
		var rm = if(isMaster.not) { Button().fixedWidth_(14).fixedHeight_(22).font_(smallFont).states_([["✕", colWarn, Color.grey(0.26)]]) };
		var fold = this.prFoldButton(key);
		var insertHolder = View().layout_(VLayout(*(track.inserts.collect { |x| this.prInsertButton(x) } ++ [nil])).margins_(0).spacing_(2));
		var addFx = Button().fixedHeight_(18).font_(smallFont).states_([["+ effect", colDim, Color.grey(0.21)]]);
		var sends = if(isReturn) {
			track.targets.collect { |to|
				this.prSendKnob(to, track.returnLevel(to), sdg.returnFeedback, { |amp| sdg.setReturnSend(track, to, amp) },
					"how much of return % goes to return %, a block later - needs feedback on".format(track.letter, to.letter))
			}
		} { [] };
		var meters = { LevelIndicator().fixedWidth_(11).minHeight_(150)
			.drawsPeak_(true).warning_(0.85).critical_(0.95)
			.numTicks_(13).numMajorTicks_(3) } ! dirt.numChannels;
		var fader = Slider().fixedWidth_(20).minHeight_(150);
		var faderText = StaticText().fixedHeight_(14).align_(\right).stringColor_(colText).font_(smallFont);
		var mute = if(isMaster.not) { Button().fixedHeight_(20).states_([["M", colDim, Color.grey(0.26)], ["M", Color.black, colWarn]]) };
		var solo = if(isMaster.not) { Button().fixedHeight_(20).states_([["S", colDim, Color.grey(0.26)], ["S", Color.black, colAccent]]) };
		var second = if(isReturn) {
			Button().fixedHeight_(16).font_(smallFont).states_([
				["post fader", colDim, Color.grey(0.26)],
				["pre fader", Color.black, colSel]
			])
		} {
			StaticText().fixedHeight_(16).font_(smallFont).stringColor_(colDim).align_(\center)
				.string_("everything")
		};
		var lim = PopUpMenu().fixedHeight_(20).items_(["raw", "limit", "clip"]).value_(track.limitertype);
		var destMenu = if(isMaster.not) { PopUpMenu().fixedHeight_(20).font_(smallFont).items_(["master", "direct"]) };
		var outLabel = if(isMaster) { StaticText().string_("out").stringColor_(colDim).fixedWidth_(24) };
		var outBox = NumberBox().fixedHeight_(20).fixedWidth_(28).clipLo_(0).step_(1).decimals_(0).value_(track.directBus);
		var head, thin, views;

		label.action = { this.selectBus(track) };
		rm !? {
			// these go with the strip they are in, so not from inside their own action
			rm.action = { { sdg.removeReturn(track) }.defer };
		};
		addFx.action = { this.prShowAddInsertMenu(nil, track, \chain) };
		fader.action = { |sl| track.level = ampSpec.map(sl.value) };
		mute !? { mute.action = { |b| track.mute = b.value == 1 } };
		solo !? { solo.action = { |b| track.solo = b.value == 1 } };
		if(isReturn) {
			this.prMidiAttach(fader, [\returnLevel, track.letter]);
			this.prMidiAttach(mute, [\returnMute, track.letter]);
			this.prMidiAttach(solo, [\returnSolo, track.letter]);
			second.action = { |b| track.preFader = b.value == 1 };
		} {
			this.prMidiAttach(fader, [\masterLevel])
		};
		lim.action = { |m| track.limitertype = m.value };
		destMenu !? { destMenu.action = { |m| { track.route(if(m.value == 1) { \direct } { \master }) }.defer } };
		outBox.action = { |b| { track.route(\direct, b.value.asInteger.max(0)) }.defer };

		hints.attach(label, "% - its effects on the effects tab".format(track.shortName));
		rm !? { hints.attach(rm, "take % out, with its effects and every send to it".format(track.shortName)) };
		hints.attach(addFx, "an effect at the end of the chain - an eq, a compressor, an insight, an fx kit effect or a VST plugin");
		hints.attach(meters, "what % plays, after its fader".format(track.shortName));
		hints.attach([fader, faderText], if(isMaster) { "the level of everything" } { "the level of what % plays".format(track.shortName) });
		mute !? { hints.attach(mute, "silence %".format(track.shortName)) };
		solo !? { hints.attach(solo, "hear only this - the orbits' direct sound stops, what they send here goes on") };
		if(isReturn) { hints.attach(second, "whether what is sent into this return is taken before or after the source's fader") };
		hints.attach(lim, "how the output is guarded: nothing, a limiter, or a hard clip%".format(if(isReturn) { " - a feedback loop can get loud" } { "" }));
		destMenu !? { hints.attach(destMenu, "where % goes: the master, or direct to the hardware bus beside it".format(track.shortName)) };
		hints.attach([outLabel, outBox].reject(_.isNil), if(isMaster) { "the hardware bus everything goes to" } {
			"the hardware bus % writes to when it goes direct".format(track.shortName)
		});

		head = View().layout_(VLayout(
			HLayout(*[label, rm, fold].reject(_.isNil)).spacing_(1),
			insertHolder,
			addFx,
			nil
		).margins_(0).spacing_(4));
		view.layout = VLayout(
			head,
			this.prSendGrid(sends, sdg.returns.size),
			[HLayout(*(meters ++ [fader])).spacing_(2), stretch: 1],
			// row for row as an orbit's
			View().fixedHeight_(this.prFootHeight).layout_(VLayout(*[
				faderText,
				second,
				if(isMaster) { View().fixedHeight_(20) } { HLayout(mute, solo).spacing_(2) },
				lim,
				HLayout(*[outLabel, destMenu, outBox].reject(_.isNil)).spacing_(2),
				nil
			]).margins_(0).spacing_(4))
		).margins_(4).spacing_(4);

		thin = this.prThinStrip(key, track.shortName, colour, { this.selectBus(track) },
			mute !? { { |b| track.mute = b.value == 1 } }, solo !? { { |b| track.solo = b.value == 1 } });
		views = (head: head, track: track, label: label, insertHolder: insertHolder, sendKnobs: sends, meters: meters,
			fader: fader, faderText: faderText, mute: mute, solo: solo, prePost: if(isReturn) { second },
			lim: lim, destMenu: destMenu, outBox: outBox);
		^this.prWrapStrip(views, view, thin, key, 92)
	}

	// + return, and the switch that lets returns send to each other
	prMakeReturnButtons {
		var add = Button().fixedWidth_(76).fixedHeight_(22).states_([["+ return", colText, Color.grey(0.26)]]);
		var feedback = Button().fixedWidth_(76).fixedHeight_(22).font_(smallFont).states_([
			["feedback off", colDim, Color.grey(0.26)],
			["feedback on", Color.black, colWarn]
		]).value_(sdg.returnFeedback.binaryValue);
		add.action = { { sdg.addReturn }.defer };
		feedback.action = { |b| { sdg.returnFeedback = b.value == 1 }.defer };
		hints.attach(add, "a new return track, with a send knob on every orbit");
		hints.attach(feedback, "lets returns send to each other and to themselves, by their knobs - a loop can get loud, keep the limiter on");
		^View().fixedWidth_(76).layout_(VLayout(add, feedback, nil).margins_(0).spacing_(4))
	}

	// the return and master strips made again, and the send knobs of every orbit
	prBussesChanged {
		var list;
		if(this.isOpen.not and: { window.notNil }) { ^this };
		list = sdg.busTracks;
		if(selectedBus.notNil and: { list.includes(selectedBus).not }) { selectedBus = nil };
		busViews.do { |views| if(list.includes(views[\track]).not) { views[\track].metering = false } };
		busViews = list.collect { |track| this.makeBusStrip(track) };
		busViews.do { |views| views[\track].metering = true; this.prApplyCollapse(views) };
		busContent !? { busContent.remove };
		busContent = View().background_(colBg);
		// the returns, their buttons, the master: on the right as in Ableton
		busContent.layout = HLayout(*(
			busViews.reject { |views| views[\track].isKindOf(SDGMaster) }.collect(_[\view])
			++ [this.prMakeReturnButtons]
			++ busViews.select { |views| views[\track].isKindOf(SDGMaster) }.collect(_[\view])
		)).margins_(0).spacing_(5);
		busHolder.layout.add(busContent);
		strips.size.do { |i| this.rebuildStripSends(i) };
		this.prAlignStrips;
		this.prFitBusArea;
		this.refreshRouting;
		this.refreshMixer;
		if(paramHolder.notNil) { this.rebuildEffectSection };
	}

	prReturnsChanged { this.prBussesChanged }

	// the right-hand area as wide as the returns, their buttons and the master
	prFitBusArea {
		var widths;
		if(busScroll.isNil) { ^this };
		widths = busViews.collect { |views|
			if(this.isCollapsed(views[\key])) { this.class.thinWidth } { views[\width] }
		} ++ [76];
		busScroll.fixedWidth = widths.sum + (5 * (widths.size - 1)) + 16;
	}

	// a return's or the master's effects on the effects tab, in the place of
	// the selected orbit's. A click on an orbit's name brings that back
	selectBus { |track|
		selectedBus = track;
		strips.do { |strip| strip[\label].value = 0 };
		busViews.do { |views| views[\label].value = (views[\track] === track).binaryValue };
		this.selectTab(0);
		this.rebuildEffectSection;
	}

	selectReturn { |ret| this.selectBus(ret) }

	// what the effects tab has on show: the bus track, or the orbit
	prHost { ^selectedBus ?? { this.selectedOrbit } }

	// --------------------------------------------------------------- inserts

	// the colour of an insert's button, by kind
	*insertColour { |kind|
		^switch(kind,
			\eq, { Color(0.22, 0.34, 0.50) },
			\compressor, { Color(0.48, 0.32, 0.16) },
			\insight, { Color(0.20, 0.40, 0.28) },
			\vst, { Color(0.40, 0.26, 0.50) },
			{ Color.grey(0.3) }
		)
	}

	// what a menu calls a kind, and whether its extension is there
	*insertKindName { |kind|
		var name = switch(kind,
			\eq, { "eq - ParaEQ" },
			\compressor, { "compressor - Compressor" },
			\insight, { "insight - Insight" },
			\vst, { "VST plugin... - VSTPlugin" },
			{ kind.asString }
		);
		^if(SDGInsert.available(kind)) { name } { name ++ " (not installed)" }
	}

	// a strip's inserts, a button each, top first: the order the signal takes
	rebuildStripInserts { |i|
		var strip = strips !? { strips[i] }, rows;
		if(strip.isNil) { ^this };
		strip[\insertView] !? { |v| v.remove };
		rows = sdg.insertsOf(i).collect { |insert| this.prInsertButton(insert) };
		strip[\insertView] = View().layout_(VLayout(*(rows ++ [nil])).margins_(0).spacing_(2));
		strip[\insertHolder].layout.add(strip[\insertView]);
		this.prAlignStrips;
	}

	// Strips side by side keep their faders level, however many inserts each has: every
	// strip leaves room for the longest list, orbits, returns and the master
	// alike, so the part above the sends is one height for all. Track strips:
	// rebuildTrackStrips
	prAlignStrips {
		var rows = this.prColumnHeight(this.prMaxRows(
			strips.size.collect { |i| sdg.insertsOf(i) } ++ sdg.busTracks.collect(_.inserts)));
		var height = 22 + rows + 18 + (2 * 4);	// name, inserts, + insert
		(strips ++ busViews).do { |strip|
			strip[\insertHolder].fixedHeight = rows;
			strip[\head].fixedHeight = height;
		};
	}

	prMaxRows { |lists| ^lists.collect(_.size).maxItem ? 0 }

	// insert buttons are 18 high, 2 apart
	prColumnHeight { |rows| ^(rows * 20 - 2).max(0) }

	// what is under the fader: the readout, a small button, mute and solo, the limiter,
	// the output, and the bus menu when there is one
	prFootHeight { ^14 + 16 + 20 + 20 + 20 + (4 * 4) + if(busManager.notNil) { 24 } { 0 } }

	// a strip's inserts before or after its effect, in as much room as the longest in its row
	prInsertColumn { |list, rows|
		^View().fixedHeight_(this.prColumnHeight(rows)).layout_(
			VLayout(*(list.collect { |x| this.prInsertButton(x) } ++ [nil])).margins_(0).spacing_(2))
	}

	prInsertButton { |insert|
		var on = insert.enabled;
		var b = Button().fixedHeight_(18).font_(smallFont).states_([[
			if(on) { insert.shortLabel } { "(" ++ insert.shortLabel ++ ")" },
			if(on) { colText } { colDim },
			if(on) { this.class.insertColour(insert.kind) } { Color.grey(0.2) }
		]]);
		b.action = { this.prOpenInsert(insert) };
		// a right press shows the menu; answers nil, so the button still sees it
		b.mouseDownAction = { |v, x, y, mods, button|
			if((button ? 0) == 1) { { this.prShowInsertMenu(insert) }.defer(0.05) };
			nil
		};
		hints.attach(b, "% on %% - click for its window, right click to bypass, move or remove it".format(
			insert.shortLabel,
			if(insert.isOnBus) {
				insert.where
			} {
				insert.track !? { |t| "% of orbit %'s %".format(if(insert.place == \pre) { "before" } { "after" }, insert.orbitIndex, t.shortName) }
					?? { "orbit %".format(insert.orbitIndex) }
			},
			if(on) { "" } { ", bypassed" }));
		^b
	}

	prOpenInsert { |insert|
		try { insert.gui } { |err|
			hints.note("could not open % - %".format(insert.shortLabel, err.errorString));
			err.reportError;
		}
	}

	prShowInsertMenu { |insert|
		var list = sdg.insertListOf(insert), k = list.indexOf(insert) ? 0;
		var vst = if(insert.isVST) { [
			MenuAction("generic controls", { insert.genericGui }),
			MenuAction("remember its state now", {
				insert.rememberState({ |data|
					{ hints.note(if(data.notNil) { "% will come back like this after a cmd-period".format(insert.shortLabel) }
						{ "% has no state to read back".format(insert.shortLabel) }) }.defer
				})
			})
		] } { [] };
		menu !? (_.destroy);
		menu = Menu(*([
			MenuAction("% · %".format(insert.shortLabel, insert.label)).enabled_(false),
			MenuAction.separator,
			MenuAction("open", { this.prOpenInsert(insert) })
		] ++ vst ++ [
			MenuAction(if(insert.enabled) { "bypass" } { "switch on" }, { insert.enabled = insert.enabled.not })
				.enabled_(insert.canBypass),
			MenuAction("move up", { sdg.moveInsert(insert, k - 1) }).enabled_(k > 0),
			MenuAction("move down", { sdg.moveInsert(insert, k + 1) }).enabled_(k < (list.size - 1)),
			MenuAction.separator,
			MenuAction("copy", { sdg.copyInserts([insert]); hints.note("% copied - paste it on any strip".format(insert.shortLabel)) }),
			MenuAction("copy the whole chain (%)".format(list.size), {
				sdg.copyInserts(list);
				hints.note("% effects copied - paste them on any strip".format(list.size))
			}).enabled_(list.size > 1),
			MenuAction(this.prPasteLabel ++ " after this", {
				sdg.pasteInserts(insert.orbitIndex, insert.track, insert.place, k + 1)
			}).enabled_((sdg.insertClipboard ? []).notEmpty),
			MenuAction.separator,
			MenuAction("remove", { sdg.removeInsert(insert) })
		]));
		menu.front;
	}

	// track and place: on a track, before or after its effect. The kinds under
	// their categories' submenus (SDGInsert.kindCategory)
	prShowAddInsertMenu { |i, track, place|
		var item = { |kind|
			MenuAction(this.class.insertKindName(kind), { this.addInsert(i, kind, track, place) })
				.enabled_(SDGInsert.available(kind))
		};
		var clip = sdg.insertClipboard ? [];
		menu !? (_.destroy);
		menu = Menu(*([
			MenuAction(case
				{ track.isKindOf(SDGBusTrack) } { "add an effect to %".format(track.where) }
				{ track.notNil } { "add an insert %, orbit %".format(if(place == \pre) { "before " } { "after " } ++ track.shortName, i) }
				{ "add an insert to orbit %".format(i) }).enabled_(false)
		] ++ if(clip.notEmpty) { [
			MenuAction.separator,
			MenuAction(this.prPasteLabel, { sdg.pasteInserts(i, track, place) })
		] } { [] } ++ this.class.categorisedActions(SDGInsert.kindNames, { |kind| SDGInsert.kindCategory(kind) }, item)));
		menu.front;
	}

	// names grouped by category, in SuperDirtGUI.effectCategories order: an Array of
	// category -> [names], empty categories left out, each group in the names' order
	*categorised { |names, categoryFunc|
		var cats = names.collect { |name| SuperDirtGUI.effectCategory(categoryFunc.value(name)) };
		^SuperDirtGUI.effectCategories.collect { |a|
			a.key -> names.select { |name, k| cats[k] == a.key }
		}.reject { |a| a.value.isEmpty }
	}

	// a menu's actions for those names: a separator, then a submenu per category
	// (hovering it opens its names beside the menu), an action per name, made by
	// itemFunc. A category whose names are all disabled is disabled too
	*categorisedActions { |names, categoryFunc, itemFunc|
		^[MenuAction.separator] ++ this.categorised(names, categoryFunc).collect { |a|
			var items = a.value.collect(itemFunc);
			Menu(*items).title_(SuperDirtGUI.categoryLabel(a.key)).asMenuAction
				.enabled_(items.any(_.enabled))
		}
	}

	// the global effects that can go on the orbit, under their categories; a
	// choice calls action with the name
	prShowEffectMenu { |title, names, action|
		menu !? (_.destroy);
		menu = Menu(*([MenuAction(title).enabled_(false)]
			++ if(names.isEmpty) { [MenuAction("(no other effect defs)").enabled_(false)] } {
				this.class.categorisedActions(names, { |name| sdg.globalEffectCategory(name) }, { |name|
					MenuAction(this.class.effectShortName(name), { action.value(name) })
				})
			}));
		menu.front;
	}

	// what + insert and the effects tab's add do: SuperDirtGUI:addInsert, and
	// its window opened - or addTrackInsert, given a track and a place. A VST
	// plugin is chosen first, in vstDialog. Answers the SDGInsert, or nil
	addInsert { |i, kind, track, place|
		var insert;
		if(kind == \vst) { this.vstDialog(i, track, place); ^nil };
		insert = if(track.notNil) { sdg.addTrackInsert(track, place, kind) } { sdg.addInsert(i, kind) };
		if(insert.isNil) { hints.note("no % insert - the post window says why".format(kind)); ^nil };
		this.prOpenInsert(insert);
		^insert
	}

	// A window listing the VST effects the server can open, with a search.
	// insert, enter or a double click puts the chosen one on orbit i and opens
	// its editor once it has loaded. The list is SuperDirtGUI:vstPlugins:
	// VSTPlugin's search, quick once a plugin is in its cache, slow the first
	// time it meets many. Answers the dialog's Window.
	vstDialog { |i, track, place|
		var field, list, info, insertBtn, cancelBtn, rescanBtn, descs = [], shown = [], fill, make, load;
		vstWindow !? { |w| if(w.isClosed.not) { w.close } };
		vstWindow = Window("VST plugin on %".format(track !? (_.where) ?? { "orbit %".format(i) }),
			Rect(0, 0, 440, 440).center_(Window.availableBounds.center), false).background_(colBg);
		if(GUI.id == \qt) { vstWindow.view.palette = QPalette.dark };
		field = TextField();
		list = ListView().font_(font).background_(Color.grey(0.16));
		info = StaticText().font_(smallFont).stringColor_(colDim);
		insertBtn = Button().fixedWidth_(70).states_([["insert", Color.black, colAccent]]);
		cancelBtn = Button().fixedWidth_(70).states_([["cancel", colText, Color.grey(0.26)]])
			.action_({ vstWindow.close });
		rescanBtn = Button().fixedWidth_(70).states_([["rescan", colText, Color.grey(0.26)]]);
		fill = {
			var q = field.string.toLower;
			shown = descs.select { |d| q.isEmpty or: { (d.name.asString ++ " " ++ (d.vendor ? "")).toLower.contains(q) } };
			list.items = shown.collect { |d| "%  ·  %".format(d.name, d.vendor ? "?") };
			info.string = if(descs.isEmpty) { "no VST effects found - rescan looks again" } {
				"% of % effects - enter or a double click puts one on %".format(shown.size, descs.size, track !? (_.where) ?? { "orbit %".format(i) })
			};
		};
		load = { |rescan = false|
			info.string = "looking for plugins - the first time can take a minute...";
			sdg.vstPlugins({ |found|
				{ if(vstWindow.notNil and: { vstWindow.isClosed.not }) { descs = found; fill.value } }.defer
			}, rescan);
		};
		make = {
			var desc = list.value !? { |k| shown[k] };
			if(desc.isNil) { info.string = "pick a plugin first" } {
				vstWindow.close;
				this.prAddVST(i, desc.key, track, place);
			}
		};
		field.keyUpAction = { fill.value };
		field.action = { make.value };				// enter
		list.enterKeyAction = { make.value };
		// the first press of the two has made the row current
		list.mouseDownAction = { |v, x, y, mods, button, clickCount|
			if(clickCount == 2) { { make.value }.defer(0.05) };
			nil
		};
		insertBtn.action = { make.value };
		rescanBtn.action = { load.value(true) };
		hints.attach(rescanBtn, "look for plugins again - for ones installed since");
		vstWindow.layout = VLayout(
			HLayout(StaticText().string_("search").stringColor_(colDim).fixedWidth_(44), field),
			list,
			info,
			HLayout(rescanBtn, nil, cancelBtn, insertBtn)
		).margins_(10).spacing_(6);
		vstWindow.front;
		field.focus(true);
		load.value(false);
		^vstWindow
	}

	// the plugin on the orbit, and its editor once the server has loaded it
	prAddVST { |i, plugin, track, place|
		var insert = if(track.notNil) {
			sdg.addTrackInsert(track, place, \vst, nil, plugin)
		} { sdg.addInsert(i, \vst, nil, plugin) };
		if(insert.isNil) { ^hints.note("no VST insert - the post window says why") };
		hints.note("% on % - its editor opens once the plugin has loaded".format(insert.shortLabel, insert.where));
		fork {
			var tries = 0;
			while { (insert.controller.isNil or: { insert.controller.isOpen.not }) and: { tries < 150 } } {
				0.1.wait;
				tries = tries + 1
			};
			{ if(insert.controller.notNil and: { insert.controller.isOpen }) { this.prOpenInsert(insert) } }.defer;
		};
		^insert
	}

	// the insert lists of one orbit, or of every orbit when i is nil
	prInsertsChanged { |i|
		if(this.isOpen.not) { ^this };
		// nil: an insert of a return or the master, whose strip shows it
		if(i.isNil) { this.prBussesChanged } { this.rebuildStripInserts(i) };
		if(i.isNil or: { i == selectedOrbitIndex }) {
			this.rebuildInsertRows;
			this.rebuildTrackStrips;
		};
	}

	prTracksChanged { |i|
		if(this.isOpen.not) { ^this };
		if(i.isNil or: { i == selectedOrbitIndex }) { this.rebuildEffectSection };
	}

	// the hardware bus an orbit writes to when it goes direct
	prOutIndex { |orbit| ^sdg.directBusOf(orbit.orbitIndex) }

	selectOrbit { |i|
		selectedOrbitIndex = i.clip(0, dirt.orbits.size - 1);
		selectedBus = nil;
		busViews.do { |views| views[\label].value = 0 };
		strips.do { |strip, j|
			strip[\label].value = (j == selectedOrbitIndex).binaryValue
		};
		trigOrbitMenu !? { trigOrbitMenu.value = selectedOrbitIndex };
		selectedEffectName = nil;
		this.rebuildEffectSection;
	}

	// ------------------------------------------------------- fader / mute / solo

	// the widgets to the SuperDirtGUI's state - after the strips are built and
	// on every \mixer notification. setting a view's value never fires its action
	refreshMixer {
		var ampSpec = paramSpecs[\orbitAmp];
		var faders = sdg.faderValues, mutes = sdg.muteStates, solos = sdg.soloStates;
		if(strips.isNil or: { faders.isNil }) { ^this };
		strips.do { |strip, i|
			faders[i] !? {
				strip[\fader].value = ampSpec.unmap(faders[i]);
				strip[\faderShown] = this.class.faderString(faders[i]);
				strip[\faderText].string = strip[\faderShown];
			};
			mutes !? { mutes[i] !? { [strip[\mute], strip[\thinMute]].do { |b| b !? { b.value = mutes[i].binaryValue } } } };
			solos !? { solos[i] !? { [strip[\solo], strip[\thinSolo]].do { |b| b !? { b.value = solos[i].binaryValue } } } };
			(strip[\sendKnobs] ? []).do { |cell|
				cell[\knob].value = this.class.sendSpec.unmap(cell[\ret].orbitLevel(i))
			};
		};
		busViews.do { |views|
			var track = views[\track];
			views[\fader].value = ampSpec.unmap(track.level);
			views[\faderText].string = this.class.faderString(track.level);
			[views[\mute], views[\thinMute]].do { |b| b !? { b.value = track.muted.binaryValue } };
			[views[\solo], views[\thinSolo]].do { |b| b !? { b.value = track.soloed.binaryValue } };
			views[\prePost] !? { |b| b.value = track.preFader.binaryValue };
			views[\members] !? { |t| t.string = this.prMembersString(track) };
			views[\sendKnobs].do { |cell|
				cell[\knob].value = this.class.sendSpec.unmap(track.returnLevel(cell[\ret]))
			};
		};
	}

	// a send, from nothing to 0 dB
	*sendSpec { ^ControlSpec(0, 1, \amp, 0, 0) }

	// a fader's level as the strip writes it: -1.0 dB, +2.5 dB, 0.0 dB, -inf dB
	*faderString { |amp|
		var db;
		if(amp < -90.dbamp) { ^"-inf dB" };
		db = amp.ampdb.round(0.1);
		^case
		{ db == 0 } { "0.0 dB" }	// a level just under 1 rounds to -0.0
		{ db > 0 } { "+% dB".format(db) }
		{ "% dB".format(db) }
	}

	// ------------------------------------------------------------------- meters

	// a cmd-period has cleared AppClock, and with it the library poll and the
	// meter routine: start them again. The meter OSCFuncs are fixed and stay.
	// SuperDirt makes the orbits again on ServerTree with the reply rate in
	// dirt_rms's state, so the meters go on with the next event
	// A refresh that was on its way is gone with it: its flag is dropped, or
	// the explorer, the knobs and the midi marks would never be drawn again
	prCmdPeriod {
		if(this.isOpen.not) { ^this };
		libraryPoll !? (_.stop);
		meterRoutine !? (_.stop);
		this.startLibraryPoll;
		this.prStartMeterRoutine;
		this.refreshExplorer;
		this.refreshParams;
		this.prMidiPaintAll;
	}

	startMeters {
		// 20 replies a second, peaks held 1.5 s - see SuperDirtGUI:startMeters
		sdg.startMeters(20, 1.5);
		// fixed: a cmd-period frees every other OSCFunc, and cleanup frees these
		rmsFunc = OSCFunc({ |msg|
			var i = (msg[2] ? -1).asInteger;
			if(i >= 0 and: { i < meterData.size }) {
				meterData[i] = msg.copyToEnd(3);
				meterStamps[i] = Main.elapsedTime;
			};
		}, '/rms', dirt.server.addr).fix;
		trackFunc = OSCFunc({ |msg|
			trackMeterData[msg[2].asInteger] = [Main.elapsedTime, msg.copyToEnd(3)];
		}, '/sdgtrack', dirt.server.addr).fix;
		this.prStartMeterRoutine;
	}

	prStartMeterRoutine {
		meterRoutine = Routine {
			loop {
				this.updateMeterViews;
				0.05.wait;
			}
		}.play(AppClock);
	}

	updateMeterViews {
		var now = Main.elapsedTime;
		// a strip's meters, and its thin strip's one meter the loudest channel
		var show = { |views, numbers|
			var top = 0, topPeak = 0;
			views[\meters].do { |m, ch|
				var peak = (numbers[ch * 2] ? 0.0).asFloat;
				var rms = (numbers[(ch * 2) + 1] ? 0.0).asFloat;
				var v = if(rms <= 0.0) { 0 } { rms.ampdb.linlin(-60, 0, 0, 1) };
				var p = if(peak <= 0.0) { 0 } { peak.ampdb.linlin(-60, 0, 0, 1) };
				m.value = v;
				m.peakLevel = p;
				top = max(top, v);
				topPeak = max(topPeak, p);
			};
			views[\thinMeter] !? { |t| t.value = top; t.peakLevel = topPeak };
		};
		strips.do { |strip, i|
			// dirt_rms pauses itself when the orbit is silent, so a strip whose
			// messages stopped is showing old numbers - drop it to zero instead
			var stale = (now - meterStamps[i]) > 0.5;
			var vals = if(stale) { [] } { meterData[i] };
			show.(strip, vals);
			dirt.numChannels.do { |ch|
				var peak = (vals[ch * 2] ? 0.0).asFloat;
				if(peak > peakHolds[i]) { peakHolds[i] = peak };
			};
			this.prShowPeak(i);
		};
		(trackViews ++ busViews).do { |views|
			var data = trackMeterData[views[\track].id];
			var stale = data.isNil or: { (now - data[0]) > 0.5 };
			show.(views, if(stale) { [] } { data[1] });
		};
	}

	// the strip's peak readout: the highest peak since it was last clicked, in
	// dB, red from 0 dB. Its states are only rewritten when the text changes
	prShowPeak { |i|
		var strip = strips[i], level = peakHolds[i];
		var str = if(level < -90.dbamp) { "-inf" } { level.ampdb.round(0.1).asString };
		if(str != strip[\peakShown]) {
			strip[\peakShown] = str;
			strip[\peak].states = [[str, colDim, Color.grey(0.16)], [str, Color.black, colWarn]];
		};
		strip[\peak].value = (level >= 1.0).binaryValue;
	}

	// --------------------------------------------------------------------- tabs

	makeRightColumn {
		var names = ["effects", "sound explorer", "modules"];
		var widths = [62, 104, 66];
		tabButtons = names.collect { |str, i|
			Button().fixedWidth_(widths[i]).fixedHeight_(20)
				.states_([[str, colDim, colPanel]])
				.action_({ this.selectTab(i) })
		};
		hints.attach(tabButtons[0], "the global effects of the selected orbit as tracks, and its inserts");
		hints.attach(tabButtons[1], "every bank and synth in the sound library - play them, open a sample in the editor");
		hints.attach(tabButtons[2], "what SuperDirtGUI read from its folders - reload it, free it, add sample banks");
		pages = [this.makeEffectSection, this.makeExplorerSection, this.makeLoadedSection];
		rightColumn = View().fixedWidth_(panelWidth.max(minPanelWidth));
		^rightColumn.layout_(
			VLayout(
				HLayout(*(tabButtons ++ [nil, hints.toggle])).spacing_(3),
				*pages
			).margins_(0).spacing_(6)
		)
	}

	// The strip between the strips and the right panel: drag it to make the
	// panel wider or narrower, double click it for the width it started at.
	// Qt has no splitter here, so it sets the panel's fixed width.
	makePanelHandle {
		var handle = panelHandle = UserView().fixedWidth_(8).background_(colBg);
		handle.drawFunc = { |v|
			var h = v.bounds.height, cx = v.bounds.width / 2;
			Pen.fillColor = if(panelDrag.notNil) { colAccent } { Color.grey(0.34) };
			[-12, -6, 0, 6, 12].do { |dy| Pen.addOval(Rect(cx - 1.5, (h / 2) + dy - 1.5, 3, 3)) };
			Pen.fill;
		};
		handle.mouseDownAction = { |v, x, y, mod, button, clickCount|
			if(clickCount == 2) { this.setPanelWidth(450); panelDrag = nil } {
				// in screen terms: the handle itself moves while it is dragged
				panelDrag = [v.absoluteBounds.left + x, rightColumn.bounds.width];
			};
			v.refresh;
		};
		handle.mouseMoveAction = { |v, x, y|
			panelDrag !? { |d| this.setPanelWidth(d[1] - ((v.absoluteBounds.left + x) - d[0])) };
		};
		handle.mouseUpAction = { |v| panelDrag = nil; v.refresh };
		hints.attach(handle, "drag to make the panel wider or narrower - double click: back to 450");
		^handle
	}

	// the right panel's width now
	panelWidth { ^rightColumn !? { rightColumn.bounds.width } }

	// The right panel this wide, within minPanelWidth and what leaves one strip
	// beside it; new windows open with it.
	setPanelWidth { |w|
		var most;
		if(rightColumn.isNil or: { window.isNil } or: { window.isClosed }) { ^this };
		most = (window.bounds.width - 96 - 40).max(minPanelWidth);
		w = w.asInteger.clip(minPanelWidth, most);
		rightColumn.fixedWidth = w;
		this.class.panelWidth = w;
	}

	selectTab { |i|
		tab = i.clip(0, pages.size - 1);
		pages.do { |v, j| v.visible = (j == tab) };
		tabButtons.do { |b, j|
			var sel = (j == tab);
			b.states_([[
				b.states[0][0],
				if(sel) { colBg } { colDim },
				if(sel) { colSel } { colPanel }
			]]);
		};
	}

	walkTabs { this.selectTab((tab + 1) % pages.size) }

	// ------------------------------------------------------------- effect editor

	makeEffectSection {
		var addBtn, pasteBtn, refreshBtn, headerRow;
		var section = View().background_(colPanel);
		effectHeader = StaticText().stringColor_(colText)
			.font_(Font.sansSerif(12, true)).string_("global effects");
		refreshBtn = Button().fixedWidth_(26)
			.states_([["↻", colText, Color.grey(0.26)]]);
		refreshBtn.action = {
			sdg.adoptEffects(this.selectedOrbit);
			this.rebuildEffectSection;
		};
		hints.attach(refreshBtn, "read the orbit's effects again - one that came from code gets a track");
		headerRow = HLayout(effectHeader, nil, refreshBtn);

		trackScroll = ScrollView().autohidesScrollers_(true).hasBorder_(false)
			.background_(colPanel).fixedHeight_(350);

		addMenu = Button().states_([["add effect track...", colText, Color.grey(0.26)]]);
		addMenu.action = {
			var orbit = this.selectedOrbit;
			if(selectedBus.isNil) {
				this.refreshAddMenu;
				this.prShowEffectMenu("add an effect track to orbit %".format(selectedOrbitIndex), addCandidates,
					{ |name| { this.addEffect(orbit, name) }.defer })
			};
		};
		pasteBtn = Button().fixedWidth_(50).states_([["paste", colText, Color.grey(0.26)]]);
		pasteBtn.action = {
			var copied = sdg.trackClipboard;
			case
			{ copied.isNil } { hints.note("nothing copied - right click an effect track's name to copy it") }
			{ sdg.pasteTrack(selectedOrbitIndex).isNil } {
				hints.note("orbit % has % already, or it is gone".format(selectedOrbitIndex, copied[\name]))
			}
			{ selectedEffectName = copied[\name]; this.rebuildEffectSection };
		};
		hints.attach(addMenu, "a global effect on a new track, after the others: every SynthDef that follows the convention and is not in this orbit yet, by category");
		hints.attach(pasteBtn, "the effect track you copied (right click its name), with its parameters, level and inserts, on this orbit");
		addRow = View().layout_(HLayout(addMenu, pasteBtn).margins_(0).spacing_(4));

		paramHolder = View();
		paramHolder.layout = VLayout().margins_(0).spacing_(0);

		section.layout = VLayout(
			headerRow,
			trackScroll,
			addRow,
			paramHolder,
			this.makeInsertSection,
			[View(), stretch: 1]	// what is left stays under them, not between them
		).margins_(6).spacing_(6);
		^section
	}

	// the selected orbit's inserts, under its global effects: a row each, in
	// the order the signal takes, and a menu to add one
	makeInsertSection {
		var addBtn = Button().states_([["add insert...", colText, Color.grey(0.26)]]);
		insertListHolder = View();
		insertListHolder.layout = VLayout().margins_(0).spacing_(0);
		addBtn.action = {
			if(selectedBus.notNil) {
				this.prShowAddInsertMenu(nil, selectedBus, \chain)
			} {
				this.prShowAddInsertMenu(selectedOrbitIndex)
			}
		};
		hints.attach(addBtn, "an insert after the others, by category, and its window opened - those whose extension is installed can be added");
		^View().layout_(VLayout(
			insertListHolder,
			HLayout(addBtn)
		).margins_(0).spacing_(6))
	}

	rebuildInsertRows {
		var list = if(selectedBus.notNil) { selectedBus.inserts } { sdg.insertsOf(selectedOrbitIndex) };
		var rows = list.collect { |insert, k| this.makeInsertRow(insert, k, list.size) };
		if(insertListHolder.isNil) { ^this };
		insertRowsView !? { insertRowsView.remove };
		insertRowsView = View().layout_(VLayout(*(rows ++ [nil])).margins_(0).spacing_(2));
		insertListHolder.layout.insert(insertRowsView, 0);
	}

	makeInsertRow { |insert, k, n|
		var onBtn = Button().fixedWidth_(36).fixedHeight_(20).states_([
			["off", colDim, Color.grey(0.26)],
			["on", Color.black, colOn]
		]).value_(insert.enabled.binaryValue);
		var nameBtn = Button().fixedHeight_(20).states_([
			["% · %".format(insert.shortLabel, insert.label), colText, this.class.insertColour(insert.kind)]
		]);
		var up = Button().fixedWidth_(22).fixedHeight_(20).states_([["▲", colDim, Color.grey(0.26)]]);
		var down = Button().fixedWidth_(22).fixedHeight_(20).states_([["▼", colDim, Color.grey(0.26)]]);
		var rm = Button().fixedWidth_(22).fixedHeight_(20).states_([["✕", colWarn, Color.grey(0.26)]]);
		onBtn.enabled = insert.canBypass;
		up.enabled = k > 0;
		down.enabled = k < (n - 1);
		onBtn.action = { |b| insert.enabled = b.value == 1 };
		nameBtn.action = { this.prOpenInsert(insert) };
		up.action = { sdg.moveInsert(insert, k - 1) };
		down.action = { sdg.moveInsert(insert, k + 1) };
		rm.action = { sdg.removeInsert(insert) };
		hints.attach(onBtn, if(insert.canBypass) { "% - off bypasses it".format(insert.shortLabel) } { "an insight only reads: nothing to bypass" });
		hints.attach(nameBtn, "% - its window".format(insert.shortLabel));
		hints.attach([up, down], "move it among the inserts - the order here is the order the signal takes");
		hints.attach(rm, "take it off");
		^HLayout(onBtn, nameBtn, up, down, rm).spacing_(2)
	}

	rebuildEffectSection {
		var orbit = this.selectedOrbit;
		var visible = orbit.globalEffects.reject { |x|
			this.class.isHiddenEffect(x.name)
		};
		effectHeader.string = if(selectedBus.notNil) {
			"% - its effects".format(selectedBus.shortName)
		} {
			"orbit % - effect tracks".format(selectedOrbitIndex)
		};
		// a bus track has no global effects: its effects are the inserts under them
		trackScroll.visible = selectedBus.isNil;
		addRow.visible = selectedBus.isNil;
		if(selectedEffectName.notNil
			and: { visible.detect { |x| x.name == selectedEffectName }.isNil }) {
			selectedEffectName = nil;
		};
		if(selectedEffectName.isNil and: { visible.notEmpty }) {
			selectedEffectName = visible.first.name;
		};
		this.refreshAddMenu;
		this.rebuildTrackStrips;
		this.rebuildParamPanel;
		this.rebuildInsertRows;
	}

	// a strip per track of the selected orbit, left to right the order of its
	// chain. Only the tracks on show send meter numbers.
	rebuildTrackStrips {
		var list = if(selectedBus.notNil) { [] } { sdg.tracksOf(selectedOrbitIndex) };
		trackViews.do { |views| if(list.includes(views[\track]).not) { views[\track].metering = false } };
		// room for the longest list of each strip's inserts, so that the faders stay level
		trackViews = list.collect { |track, k|
			this.makeTrackStrip(track, k, list.size,
				this.prMaxRows(list.collect(_.pre)), this.prMaxRows(list.collect(_.post)))
		};
		trackViews.do { |views| views[\track].metering = true };
		trackCanvas !? { trackCanvas.remove };
		trackCanvas = View().background_(colPanel);
		trackCanvas.layout = HLayout(*(trackViews.collect(_[\view]) ++ [nil])).margins_(0).spacing_(5);
		trackScroll.canvas = trackCanvas;
		this.refreshTrackLevels;
	}

	makeTrackStrip { |track, k, n, preRows, postRows|
		var ampSpec = paramSpecs[\orbitAmp];
		var selectedNow = track.name == selectedEffectName;
		var view = View().fixedWidth_(92).background_(Color.grey(0.24));
		var label = Button().fixedHeight_(22).states_([
			[track.shortName, colText, Color.grey(0.3)],
			[track.shortName, Color.black, colAccent]
		]).value_(selectedNow.binaryValue);
		var names = this.class.effectCandidates(sdg, track.orbit);
		var loadMenu = Button().font_(smallFont).fixedHeight_(18)
			.states_([["load effect…", colText, Color.grey(0.26)]]);
		var onBtn = Button().fixedHeight_(18).font_(smallFont).states_([
			["off", colDim, Color.grey(0.26)],
			["on", Color.black, colOn]
		]).value_((track.fx !? (_.active) ? false).binaryValue);
		var left = Button().fixedWidth_(16).fixedHeight_(18).font_(smallFont).states_([["◀", colDim, Color.grey(0.26)]]);
		var right = Button().fixedWidth_(16).fixedHeight_(18).font_(smallFont).states_([["▶", colDim, Color.grey(0.26)]]);
		var rm = Button().fixedWidth_(16).fixedHeight_(18).font_(smallFont).states_([["✕", colWarn, Color.grey(0.26)]]);
		var addPre = Button().fixedHeight_(16).font_(smallFont).states_([["+ pre", colDim, Color.grey(0.21)]]);
		var addPost = Button().fixedHeight_(16).font_(smallFont).states_([["+ post", colDim, Color.grey(0.21)]]);
		var mark = StaticText().fixedHeight_(14).align_(\center).font_(smallFont)
			.string_("▼ " ++ track.shortName ++ " ▼").stringColor_(colAccent);
		var meters = { LevelIndicator().fixedWidth_(11).minHeight_(90)
			.drawsPeak_(true).warning_(0.85).critical_(0.95)
			.numTicks_(13).numMajorTicks_(3) } ! dirt.numChannels;
		var fader = Slider().fixedWidth_(20).minHeight_(90);
		var faderText = StaticText().fixedHeight_(14).align_(\right).stringColor_(colText).font_(smallFont);

		label.action = { this.prSelectTrack(track) };
		label.mouseDownAction = { |v, x, y, mods, button|
			if((button ? 0) == 1) { { this.prShowTrackMenu(track) }.defer(0.05) };
			nil
		};
		loadMenu.action = {
			// the button goes with the strip it is in, so not from inside the menu's action
			this.prShowEffectMenu("in place of % on orbit %".format(track.shortName, track.orbitIndex), names,
				{ |name| { this.loadTrackEffect(track, name) }.defer })
		};
		onBtn.action = { |b|
			track.fx !? { |fx|
				fx.active_(b.value == 1);
				sdg.wakeOrbit(track.orbit);
			}
		};
		left.enabled = k > 0;
		right.enabled = k < (n - 1);
		left.action = { sdg.moveTrack(track, k - 1) };
		right.action = { sdg.moveTrack(track, k + 1) };
		rm.action = { track.fx !? { |fx| this.removeEffect(fx) } };
		addPre.action = { this.prShowAddInsertMenu(track.orbitIndex, track, \pre) };
		addPost.action = { this.prShowAddInsertMenu(track.orbitIndex, track, \post) };
		fader.action = { |sl| track.level = ampSpec.map(sl.value) };
		track.name !? { |name| this.prMidiAttach(fader, [\trackLevel, track.orbitIndex, name]) };

		hints.attach(label, "% - its parameters under the strips; right click to copy it to another orbit".format(track.name));
		hints.attach(loadMenu, "another global effect on this track, in place of % - its inserts and fader stay".format(track.shortName));
		hints.attach(onBtn, "% - off pauses the synth, on resumes it".format(track.shortName));
		hints.attach([left, right], "move the track in the chain");
		hints.attach(rm, "take % and its inserts out of the orbit".format(track.shortName));
		hints.attach(addPre, "an insert before the effect: on what goes into it, top first");
		hints.attach(addPost, "an insert after the effect: on what comes out of it, top first");
		hints.attach(meters, "what the track adds to the orbit, after its post inserts and its fader");
		hints.attach([fader, faderText], "the level of what % adds to the orbit, in dB under it; the dry signal is not touched".format(track.shortName));

		view.layout = VLayout(
			label,
			loadMenu,
			HLayout(onBtn, left, right, rm).spacing_(1),
			this.prInsertColumn(track.pre, preRows),
			addPre,
			mark,
			this.prInsertColumn(track.post, postRows),
			addPost,
			[HLayout(*(meters ++ [fader])).spacing_(2), stretch: 1],
			faderText
		).margins_(3).spacing_(3);

		^(view: view, track: track, label: label, loadMenu: loadMenu, candidates: names,
			meters: meters, fader: fader, faderText: faderText)
	}

	// right click on a track's name: copy it, with its parameters, level and inserts
	prShowTrackMenu { |track|
		menu !? (_.destroy);
		menu = Menu(
			MenuAction("% · orbit %".format(track.shortName, track.orbitIndex)).enabled_(false),
			MenuAction.separator,
			MenuAction("copy this effect track", {
				sdg.copyTrack(track);
				hints.note("% copied - paste it with paste under the tracks, or a right click on an orbit's name".format(track.shortName))
			}),
			MenuAction("copy its inserts (%)".format(track.inserts.size), { sdg.copyInserts(track.inserts) })
				.enabled_(track.inserts.notEmpty)
		);
		menu.front;
	}

	// the track's name selects it, as an orbit's does: its parameters under the strips
	prSelectTrack { |track|
		selectedEffectName = track.name;
		trackViews.do { |views| views[\label].value = (views[\track].name == selectedEffectName).binaryValue };
		this.rebuildParamPanel;
	}

	// another effect on a track, and it becomes the one whose parameters show.
	// Answers the new GlobalDirtEffect, or nil
	loadTrackEffect { |track, name|
		var fx = sdg.setTrackEffect(track, name);
		fx !? { selectedEffectName = fx.name };
		if(fx.isNil) { hints.note("could not load % - the post window says why".format(name)) };
		this.rebuildEffectSection;
		^fx
	}

	// the track faders to their tracks' levels; setting a view's value never fires its action
	refreshTrackLevels {
		var ampSpec = paramSpecs[\orbitAmp];
		trackViews.do { |views|
			var level = views[\track].level;
			views[\fader].value = ampSpec.unmap(level);
			views[\faderText].string = this.class.faderString(level);
		}
	}

	refreshAddMenu {
		addCandidates = this.class.effectCandidates(sdg, this.selectedOrbit);
	}

	// the named effect on a new track of the orbit
	addEffect { |orbit, name|
		var track = sdg.addTrack(orbit.orbitIndex, name);
		track !? { selectedEffectName = track.name };
		this.rebuildEffectSection;
	}

	removeEffect { |fx|
		var track = sdg.trackOf(fx);
		var orbit = this.selectedOrbit;
		if(selectedEffectName == fx.name) { selectedEffectName = nil };
		if(track.notNil) {
			sdg.removeTrack(track)
		} {
			sdg.releaseEffect(fx);
			orbit.globalEffects = orbit.globalEffects.reject { |e| e === fx };
		};
		this.rebuildEffectSection;
	}

	// dir -1 earlier in the chain, 1 later
	moveEffect { |fx, dir|
		var track = sdg.trackOf(fx), k;
		if(track.isNil) { ^this };
		k = sdg.tracksOf(track.orbitIndex).indexOf(track);
		sdg.moveTrack(track, k + dir);
	}

	// --------------------------------------------------------- parameter editing

	rebuildParamPanel {
		var orbit = this.prHost;
		var fx = if(selectedBus.isNil) { orbit.globalEffects.detect { |x| x.name == selectedEffectName } };
		var cells;
		paramContentView !? { paramContentView.remove };
		paramCells = List.new;
		cells = if(fx.isNil) { [] } {
			fx.paramNames.asArray.collect { |param| this.makeParamCell(orbit, fx, param) }
		};
		paramContentView = View().layout_(
			VLayout(*(cells.clump(6).collect { |row|
				HLayout(*(row ++ [nil])).margins_(0).spacing_(4)
			} ++ [nil])).margins_(0).spacing_(6)
		);
		paramHolder.layout.insert(paramContentView, 0);
	}

	// a control as one cell of a row of them: its name over its knob, its value under it,
	// and beside the value whatever else it has
	prKnobCell { |name, knob, box, hint, more|
		var text = StaticText().string_(name).align_(\center).fixedHeight_(12)
			.font_(smallFont).stringColor_(colText);
		box.fixedHeight_(18).font_(smallFont).normalColor_(colText);
		hints.attach([text, knob, box], hint);
		^View().fixedWidth_(68).layout_(VLayout(
			text,
			[knob, align: \center],
			HLayout(*[box, more].reject(_.isNil)).margins_(0).spacing_(2)
		).margins_(0).spacing_(2))
	}

	makeParamCell { |orbit, fx, param|
		var spec = this.specFor(fx, param);
		var value = this.currentValue(orbit, fx, param);
		// a double click: the SynthDef's default (x: what it was before the window)
		var knob = this.class.makeKnob(36, false, { spec.unmap(this.descDefault(fx, param) ? spec.default) });
		var box = NumberBox().decimals_(3);
		var clear = Button().fixedWidth_(16).fixedHeight_(18).font_(smallFont)
			.states_([["x", colDim, Color.grey(0.26)]]);

		knob.value = spec.unmap(value);
		box.value = value;

		knob.action = { |k|
			var v = spec.map(k.value);
			box.value = v;
			this.setEffectParam(orbit, fx, param, v);
		};
		box.action = { |b|
			var v = spec.constrain(b.value);
			b.value = v;
			knob.value = spec.unmap(v);
			this.setEffectParam(orbit, fx, param, v);
		};
		clear.action = {
			var v = this.clearEffectParam(orbit, fx, param);
			box.value = v;
			knob.value = spec.unmap(v);
		};

		hints.attach(clear, "% back to what it was before the window touched it".format(param));
		paramCells.add((orbit: orbit, fx: fx, param: param, spec: spec, knob: knob, box: box));
		this.prMidiAttach(knob, [\param, orbit.orbitIndex, fx.name, param]);

		^this.prKnobCell(param.asString, knob, box,
			"% of % - set here and carried by every event that follows".format(param, fx.name), clear)
	}

	// the knob's range, default and value of an effect parameter, and setting it:
	// SuperDirtGUI's, so a controller does the same with the window closed
	specFor { |fx, param| ^sdg.effectParamSpecOf(fx, param) }

	descDefault { |fx, param| ^sdg.effectParamDefault(fx, param) }

	currentValue { |orbit, fx, param| ^sdg.effectParamValue(orbit, fx, param) }

	setEffectParam { |orbit, fx, param, value| sdg.setEffectParam(orbit, fx, param, value) }

	// put the parameter back to what it was before the GUI touched it. Keys the
	// defaultParentEvent defines by itself (cps, lock, dry ...) are restored to
	// their startup value, never removed - other code relies on them existing.
	// Answers the value the widgets should show afterwards.
	clearEffectParam { |orbit, fx, param|
		var snapshot = parentSnapshots[selectedOrbitIndex][param];
		var default;
		if(snapshot.isNumber) {
			this.setEffectParam(orbit, fx, param, snapshot);
			^snapshot
		};
		default = this.descDefault(fx, param) ? this.specFor(fx, param).default;
		// send the SynthDef default to the running synth first, then forget the
		// key on both sides, so future events (value nil) don't diff against it
		this.setEffectParam(orbit, fx, param, default);
		orbit.defaultParentEvent.removeAt(param);
		fx.state.removeAt(param);
		^default
	}

	// a controller moved a parameter (sdg.setEffectParam says so, and so does this
	// window's own knob): the knobs on show follow, at most 20 times a second
	prParamChanged {
		if(paramRefreshPending) { ^this };
		paramRefreshPending = true;
		AppClock.sched(0.05, {
			paramRefreshPending = false;
			if(this.isOpen) { this.refreshParams };
			nil
		});
	}

	// setting a view's value never fires its action
	refreshParams {
		paramCells.do { |cell|
			var v = this.currentValue(cell[\orbit], cell[\fx], cell[\param]);
			if(v.isNumber and: { cell[\knob].isClosed.not } and: { cell[\box].value != v }) {
				cell[\box].value = v;
				cell[\knob].value = cell[\spec].unmap(v);
			}
		}
	}

	// --------------------------------------------------------------------- midi

	// A view a controller can move, by an SDGMidiTarget's address. In learn mode a
	// click on it waits for the controller instead of moving it; a right click gives
	// its MIDI menu. Without Modality nothing changes. Answers the view.
	prMidiAttach { |view, address|
		var entry, own;
		if(SDGMidi.isAvailable.not) { ^view };
		entry = (view: view, address: address, style: this.prMidiStyle(view));
		// an Array: add answers the grown one
		midiViews = midiViews.reject { |e| e[\view].isClosed }.add(entry);
		// true keeps the click from the view; anything else goes to what the view
		// had (a knob's double click back to its default, makeKnob)
		own = view.mouseDownAction;
		view.mouseDownAction = { |v, x, y, mods, button, clickCount|
			case
			{ midiLearning } { this.prMidiArm(address); true }
			{ (button ? 0) == 1 } { { this.prShowMidiMenu(address) }.defer(0.05); true }
			{ own.value(v, x, y, mods, button, clickCount) }
		};
		this.prMidiPaint(entry);
		^view
	}

	// the colours a view had, to paint the marks over and take them off again
	prMidiStyle { |view|
		^case
		{ view.isKindOf(Knob) } { (knob: view.color.copy) }
		{ view.isKindOf(Button) } { (states: view.states.deepCopy) }
		// a Slider's background_ sets its groove, and background answers something else
		{ view.isKindOf(Slider) } { (groove: view.getProperty(\grooveColor), knobColor: view.knobColor) }
		{ () }
	}

	// mapped: blue (a knob's arc, a slider's handle, a button's letter). In learn mode
	// every mappable view gets a blue ground, and the one waiting for the controller orange
	prMidiPaint { |entry|
		var view = entry[\view], style = entry[\style];
		var mapped = sdg.midi.mapFor(entry[\address]).notNil;
		var armed = sdg.midi.armed !? { |t| t.address == entry[\address] } ? false;
		var ground = case { armed } { colAccent } { midiLearning } { Color(0.2, 0.3, 0.42) } { nil };
		var value;
		if(view.isClosed) { ^this };
		case
		{ style[\knob].notNil } {
			view.color = style[\knob].copy
				.put(0, ground ? style[\knob][0])
				.put(1, if(mapped and: { style[\knob][1] != colDim }) { colSel } { style[\knob][1] })
		}
		{ style[\states].notNil } {
			value = view.value;
			view.states = style[\states].collect { |st, k|
				[st[0], if(mapped and: { k == 0 }) { colSel } { st[1] }, if(ground.notNil) { ground } { st[2] }]
			};
			view.value = value;
		}
		{ style[\knobColor].notNil } {
			view.background = ground ? style[\groove];
			view.knobColor = if(mapped) { colSel } { style[\knobColor] };
		}
	}

	// the marks again, a moment after the last change
	prMidiChanged {
		if(midiPaintPending) { ^this };
		midiPaintPending = true;
		AppClock.sched(0.05, {
			midiPaintPending = false;
			if(this.isOpen) { this.prMidiPaintAll };
			nil
		});
	}

	prMidiPaintAll {
		midiViews = midiViews.reject { |e| e[\view].isClosed };
		midiViews.do { |e| this.prMidiPaint(e) };
	}

	// learn mode: clicks pick what the next controller move is mapped to, one after
	// another, until it is switched off
	midiLearning_ { |bool|
		midiLearning = bool == true and: { SDGMidi.isAvailable };
		if(midiLearning.not) { sdg.midi.cancelLearn };
		learnBtn !? { if(learnBtn.isClosed.not) { learnBtn.value = midiLearning.binaryValue } };
		hints.note(if(midiLearning) {
			"midi learn: click what to map, then move a control on the controller"
		} { "midi learn off" });
		this.prMidiPaintAll;
		sdg.midi.changed(\learnMode, midiLearning);
	}

	// the next element moved on a controller is mapped to address
	prMidiArm { |address|
		sdg.midi.learn(address);
		hints.note("move a control on the controller for % - or click something else".format(SDGMidiTarget(sdg, address).label));
		this.prMidiPaintAll;
	}

	prShowMidiMenu { |address|
		var target = SDGMidiTarget(sdg, address), m = sdg.midi.mapFor(address);
		var waiting = sdg.midi.armed == target;
		menu !? (_.destroy);
		menu = Menu(*[
			MenuAction(target.label).enabled_(false),
			MenuAction(m !? { "midi: % %".format(m.ktl, m.element) } ? "no midi mapping").enabled_(false),
			MenuAction.separator,
			if(waiting) {
				MenuAction("stop waiting for the controller", { sdg.midi.cancelLearn })
			} {
				MenuAction("learn midi", { this.prMidiArm(address) })
			},
			MenuAction("edit mapping...", { sdg.midi.gui.select(address) }).enabled_(m.notNil),
			MenuAction("remove midi mapping", { sdg.midi.unmap(address) }).enabled_(m.notNil),
			MenuAction("post its code", { m.asCode.postln }).enabled_(m.notNil)
		]);
		menu.front;
	}

	// ------------------------------------------------------------ sound explorer

	// what the explorer's columns are; name and tags share the width the others leave
	*explorerColumnSpecs {
		^[
			(key: \name, label: "name"),
			(key: \duration, label: "dur", width: 72, align: \right),
			(key: \number, label: "n", width: 32, align: \right),
			(key: \bpm, label: "bpm", width: 50, align: \right),
			(key: \key, label: "key", width: 40, align: \right),
			(key: \tags, label: "tags"),
			(key: \added, label: "added", width: 70, align: \right),
			(key: \created, label: "created", width: 70, align: \right)
		]
	}

	// the order a column sorts in on its first click: newest first for dates, as Finder does
	*firstSortAscending { |key| ^#[\added, \created].includes(key).not }

	makeExplorerSection {
		var section = View().background_(colPanel);
		var header = StaticText().string_("sound explorer").stringColor_(colText)
			.font_(Font.sansSerif(12, true));
		var refresh = this.prButton("↻", 26, { this.refreshExplorer }, "read the sound library again");
		var expand = this.prButton("open all", 60, { this.openAll }, "open every bank");
		var collapse = this.prButton("close all", 62, { this.closeAll }, "close every bank");
		var playBtn = this.prMidiAttach(this.prButton("play", 50, { this.playSelected },
			"play the selected sample into the orbit chosen below, through the \\dirt event type (enter)"), [\play]);
		var loadBtn = this.prButton("load folder...", 92, { this.loadFolderDialog },
			"read more samples from a folder: its files become a bank named after it, each subfolder a bank of its own");
		var tagsBtn, syncBtn;
		var trigCells;

		expandedBanks = IdentitySet.new;
		searchCollapsed = IdentitySet.new;
		explorerColumns = explorerColumns ?? { this.class.explorerColumnKeys.copy };
		countText = StaticText().stringColor_(colDim).font_(smallFont);
		searchField = hints.attach(TextField(), "type to narrow the banks and samples by name (808 bd: bd files in the 808 banks) - and tag:kick, -tag:melodic,"
			" bpm:120 (its halves and doubles too), key:am");
		searchField.keyUpAction = { this.prSearchChanged };
		tagsBtn = this.prButton("tags", 40, { this.prShowTagMenu }, "every tag of the library: pick one to narrow to it, or to leave it out");
		tagBar = View().fixedHeight_(20).background_(colPanel);
		tagBar.layout = HLayout().margins_(0).spacing_(3);

		tree = SDGExplorerView();
		tree.view.minHeight_(220);
		tree.columns = this.prColumnSpecs;
		tree.sortKey = sortKey;
		tree.ascending = sortAscending;
		tree.selectAction = { |row| this.prSelectRow(row) };
		tree.toggleAction = { |row| this.prToggleRow(row) };
		tree.activateAction = { |row| this.prSelectRow(row); this.playSelected };
		tree.menuAction = { |row| this.prSelectRow(row); this.prShowMenu };
		tree.headerAction = { |key| this.sortBy(key) };
		tree.headerMenuAction = { |key| this.prShowColumnMenu };
		tree.keyAction = { |char, mod, key|
			case
			{ key == 16r1000004 or: { key == 16r1000005 } } {
				if(selected.notNil and: { selected[\kind] == \bank }) { tree.current !? { |r| this.prToggleRow(r) } } { this.playSelected };
				true
			}
			{ char == $e } { this.editSelected; true }
			{ char == $t } { this.editTagsDialog; true }
			{ char == $y } { this.addSelectedToSync; true }
			{ char == $b } { this.editBpmDialog; true }
			{ char == $  } { this.playSelected; true }
			{ nil }
		};
		tree.hintAction = { |key, inHeader|
			hints.string = if(inHeader) {
				"click a column to sort by it, again for the other way round - right click to choose the columns"
			} {
				switch(key,
					\bpm, { "the tempo the analysis heard - a ? when it is not sure; b sets it by hand" },
					\key, { "the key, or the note of a one-shot (C4) - a ? when the analysis is not sure" },
					\tags, { "the sample's tags: by hand in the accent, by the analysis dimmed - t edits them" },
					\added, { "when the file came into its folder" },
					\created, { "when the file was made" },
					\number, { "n: the number tidal plays it as; for a bank, how many samples it holds" },
					\duration, { "seconds; for a bank, all of them together" },
					{ "the banks of the sound library - open one for its samples; enter plays, e edits, t tags, double click plays, right click for more" }
				)
			}
		};

		soundInfo = hints.attach(StaticText().stringColor_(colDim).font_(smallFont), "the selected sound");

		trigOrbitMenu = PopUpMenu().fixedWidth_(84)
			.items_(dirt.orbits.collect { |o, i| "orbit " ++ i });
		trigNBox = NumberBox().fixedWidth_(48).clipLo_(0).step_(1).decimals_(0).value_(0);
		// the box is what plays: picking a sample sets it, and editing it picks a sibling
		trigNBox.action = { this.prRefreshSelection };
		hints.attach(trigOrbitMenu, "the orbit the play button and the editor play into");
		hints.attach(trigNBox, "n - the sample in the bank that plays and edits, or the note for a synth; picking a sample sets it");

		trigSpecs = (
			speed: ControlSpec(0.125, 8, \exp, 0, 1),
			amp: ControlSpec(0, 1.5, \amp, 0, 0.4),
			pan: ControlSpec(0, 1, \lin, 0, 0.5)
		);
		trigParams = (speed: 1.0, amp: 0.4, pan: 0.5);
		trigViews = ();
		trigCells = [\speed, \amp, \pan].collect { |key| this.makeTrigCell(key) };

		editBtn = this.prButton("edit...", 56, { this.editSelected },
			"open the selected sample in the editor: trim, gain, DC, fades, the bpm grid, the slicer, save (e)");
		revealBtn = this.prButton(this.class.revealString, 100, { this.revealSelected },
			"show the selected sample's file");
		syncBtn = this.prButton("sync...", 56, { sdg.sync.gui },
			"the sync window: long samples put on one tempo and in one key, written as Tidal code with loopAt and chop (y adds the selected one)");

		section.layout = VLayout(
			HLayout(header, countText, nil, loadBtn, expand, collapse, refresh).spacing_(3),
			HLayout(searchField, tagsBtn).spacing_(3),
			tagBar,
			[tree.view, stretch: 1],
			soundInfo,
			HLayout(
				trigOrbitMenu,
				StaticText().string_("n").stringColor_(colDim).fixedWidth_(12),
				trigNBox,
				nil,
				playBtn, editBtn, syncBtn, revealBtn
			).spacing_(4),
			HLayout(*(trigCells ++ [nil])).spacing_(4)
		).margins_(6).spacing_(5);
		sdg.tags.addDependant(this);
		^section
	}

	makeTrigCell { |key|
		var spec = trigSpecs[key];
		var knob = this.class.makeKnob(36, key == \pan, spec.unmap(spec.default));
		var box = NumberBox().decimals_(3);
		knob.value = spec.unmap(trigParams[key]);
		box.value = trigParams[key];
		knob.action = { |k|
			trigParams[key] = spec.map(k.value);
			box.value = trigParams[key];
		};
		box.action = { |b|
			trigParams[key] = spec.constrain(b.value);
			b.value = trigParams[key];
			knob.value = spec.unmap(trigParams[key]);
		};
		trigViews[key] = [knob, box];
		^this.prKnobCell(key.asString, knob, box, "% for the play button and the editor's preview".format(key))
	}

	prButton { |str, width, func, hint|
		var b = Button().fixedWidth_(width).states_([[str, colText, Color.grey(0.26)]])
			.action_({ func.value });
		hint !? { hints.attach(b, hint) };
		^b
	}

	// What the load folder... buttons ask.
	loadFolderDialog {
		// stripResult: the folder comes as one string, not an array of them
		FileDialog({ |path| path !? { this.loadFolder(path.asString) } }, nil, 2, 0, true)
	}

	// SuperDirtGUI:loadSamples, then the first new bank selected in a tree with
	// no search left in it to hide it: the rebuild that \samples schedules keeps
	// the selection. A folder with nothing new says so where the selection shows.
	loadFolder { |path|
		var names = sdg.loadSamples(path);
		if(names.isEmpty) {
			soundInfo.string = "no new sound files in " ++ path.basename;
		} {
			searchField.string = "";
			selected = (kind: \bank, name: names[0], index: nil);
			sdg.tags.enqueue(names.collect { |n| sdg.tags.pathsOf(n) }.flatten, true);
		};
		^names
	}

	// the search field, from code
	search { ^searchField !? (_.string) }

	search_ { |str|
		searchField !? { searchField.string = str.asString };
		this.prSearchChanged;
	}

	prSearchChanged {
		searchCollapsed = IdentitySet.new;
		this.refreshExplorer;
	}

	// ----- columns and sorting

	explorerColumns { ^explorerColumns }

	// which columns show, name always first
	explorerColumns_ { |keys|
		var all = this.class.explorerColumnSpecs.collect(_[\key]);
		explorerColumns = [\name] ++ all.select { |k| k != \name and: { keys.includes(k) } };
		tree !? { tree.columns = this.prColumnSpecs; this.prRebuildRows };
	}

	prColumnSpecs {
		^this.class.explorerColumnSpecs.select { |c| explorerColumns.includes(c[\key]) }
	}

	sortKey { ^sortKey }
	sortAscending { ^sortAscending }

	// Finder's list mode: a click on a column sorts by it, a second click the
	// other way; banks among banks and samples within each bank, by the same key
	sortBy { |key, ascending|
		if(ascending.isNil) {
			ascending = if(key == sortKey) { sortAscending.not } { this.class.firstSortAscending(key) };
		};
		sortKey = key;
		sortAscending = ascending;
		tree !? { tree.sortKey = key; tree.ascending = ascending };
		this.refreshExplorer;
	}

	prShowColumnMenu {
		menu !? (_.destroy);
		menu = Menu(*this.class.explorerColumnSpecs.reject { |c| c[\key] == \name }.collect { |c|
			MenuAction(c[\label], {
				this.explorerColumns = if(explorerColumns.includes(c[\key])) {
					explorerColumns.reject(_ == c[\key])
				} { explorerColumns ++ [c[\key]] };
			}).checkable_(true).checked_(explorerColumns.includes(c[\key]))
		});
		menu.front;
	}

	// "kick tag:short -tag:melodic bpm:120 key:am" -> its parts
	*parseSearch { |str|
		var out = (words: [], include: [], exclude: [], bpm: nil, key: nil);
		str.asString.toLower.split($ ).reject(_.isEmpty).do { |w|
			case
			{ w.beginsWith("tag:") } { SDGSampleTags.normaliseTag(w.copyToEnd(4)) !? { |t| out[\include] = out[\include].add(t) } }
			{ w.beginsWith("-tag:") } { SDGSampleTags.normaliseTag(w.copyToEnd(5)) !? { |t| out[\exclude] = out[\exclude].add(t) } }
			{ w.beginsWith("bpm:") } { out[\bpm] = w.copyToEnd(4).asFloat; if(out[\bpm] <= 0) { out[\bpm] = nil } }
			{ w.beginsWith("key:") } { out[\key] = SDGSampleFeatures.parseKey(w.copyToEnd(4)) }
			{ out[\words] = out[\words].add(w) };
		};
		^out
	}

	// a tag in the search, or out of it, or the other way round (exclude)
	toggleSearchTag { |tag, exclude = false|
		var words = (this.search ? "").split($ ).reject(_.isEmpty);
		var mine = (if(exclude) { "-tag:" } { "tag:" }) ++ tag, other = (if(exclude) { "tag:" } { "-tag:" }) ++ tag;
		words = words.reject { |w| w == other };
		words = if(words.includesEqual(mine)) { words.reject { |w| w == mine } } { words.add(mine) };
		this.search = words.join(" ");
	}

	// "file10" after "file9": digits compared as numbers, letters without case
	*naturalKey { |str|
		var out = "", digits = "";
		str.asString.toLower.do { |c|
			if(c.isDecDigit) { digits = digits ++ c } {
				if(digits.notEmpty) { out = out ++ digits.padLeft(10, "0"); digits = "" };
				out = out ++ c;
			};
		};
		if(digits.notEmpty) { out = out ++ digits.padLeft(10, "0") };
		^out
	}

	// a bpm brought into 80 .. 160, so a loop at 70 sorts next to one at 140
	*foldBpm { |bpm|
		if(bpm.isNil or: { bpm <= 0 }) { ^nil };
		while { bpm < 80 } { bpm = bpm * 2 };
		while { bpm >= 160 } { bpm = bpm / 2 };
		^bpm
	}

	*keyOrder { |info|
		^info[\tonic] !? { |t| (t * 3) + (switch(info[\mode], \major, { 0 }, \minor, { 1 }, { 2 })) }
	}

	// what a sample sorts by under a key; nil sorts last whichever way
	prFileSortValue { |f, key, bpmTarget|
		var info = f[\info];
		^switch(key,
			\name, { f[\sortName] },
			\duration, { this.prDuration(f[\buffer]) },
			\number, { f[\index] },
			\bpm, {
				if(bpmTarget.notNil) { SDGSampleTags.bpmDistance(info[\bpm], bpmTarget) !? { |d| if(d == inf) { nil } { d } } } {
					this.class.foldBpm(info[\bpm]) !? { |b| (b * 1000).round + (info[\bpm] / 1000) }
				}
			},
			\key, { this.class.keyOrder(info) },
			\tags, { if(info[\tags].isEmpty) { nil } { info[\tags].collect(_.asString).sort.join(" ") } },
			\added, { info[\dateAdded] },
			\created, { info[\created] },
			{ nil }
		)
	}

	// what a bank sorts by: the sum, the count, the middle bpm, the commonest key, the newest date
	prBankSortValue { |name, files, key, bpmTarget|
		var vals;
		^switch(key,
			\name, { this.class.naturalKey(name) },
			\duration, { files.collect { |f| this.prDuration(f[\buffer]) }.maxItem },	// by the longest
			\number, { files.size },
			\added, { files.collect { |f| f[\info][\dateAdded] }.reject(_.isNil).maxItem },
			\created, { files.collect { |f| f[\info][\created] }.reject(_.isNil).maxItem },
			\tags, { this.class.naturalKey(name) },
			{
				vals = files.collect { |f| this.prFileSortValue(f, key, bpmTarget) }.reject(_.isNil).sort;
				if(vals.isEmpty) { nil } { vals[vals.size div: 2] }
			}
		)
	}

	*prSortPairs { |pairs, ascending|
		^pairs.sort { |a, b|
			var va = a[0], vb = b[0];
			case
			{ va.isNil and: { vb.isNil } } { a[1] <= b[1] }
			{ va.isNil } { false }
			{ vb.isNil } { true }
			{ va == vb } { a[1] <= b[1] }
			{ if(ascending) { va < vb } { va > vb } }
		}.collect(_[2])
	}

	// ----- the model

	// One entry per bank, its samples inside, and the synth events in a folder
	// of their own. The search keeps the banks and samples whose name has
	// every word of it in them, and the samples with the tags, bpm and key it
	// asks for; then the banks and each bank's samples sorted by the column.
	refreshExplorer {
		var lib = dirt.soundLibrary, store = sdg.tags;
		var query = this.class.parseSearch(searchField !? (_.string) ? "");
		var filtering = query[\include].notEmpty or: { query[\exclude].notEmpty } or: { query[\bpm].notNil } or: { query[\key].notNil };
		var banks = lib.buffers.keys.asArray;
		var synths = lib.synthEvents.keys.asArray.collect(_.asString).sort;
		var wasSelected = selected, pairs;
		var nameHas = { |name| query[\words].every { |w| name.toLower.contains(w) } };
		rebuilds = rebuilds + 1;
		explorerModel = [];
		pairs = banks.collect { |bank|
			var bufs = lib.buffers[bank] ? [];
			var bankMatch = nameHas.(bank.asString);
			var files = bufs.collect { |b, i|
				var p = b !? (_.path);
				(buffer: b, index: i, name: (p ? "").basename, path: p,
					info: if(p.notNil) { store.infoOf(p, false) } { () }, sortName: this.class.naturalKey((p ? "").basename))
			};
			// a file matches when the words are in its bank's name and its own between them:
			// "808 bd" finds bd files in the 808 banks
			if(bankMatch.not) { files = files.select { |f| nameHas.(bank.asString ++ "/" ++ f[\name]) } };
			if(filtering) {
				files = files.select { |f|
					f[\path].notNil and: { store.matches(f[\path], query[\include], query[\exclude], query[\bpm], query[\key], false) }
				};
			};
			if((bankMatch and: { filtering.not }) or: { files.notEmpty }) {
				files = this.class.prSortPairs(files.collect { |f|
					[this.prFileSortValue(f, sortKey, query[\bpm]), f[\sortName], f]
				}, sortAscending);
				[this.prBankSortValue(bank.asString, files, sortKey, query[\bpm]), this.class.naturalKey(bank.asString),
					(kind: \bank, name: bank.asSymbol, files: files.collect(_[\index]), fileData: files, numBuffers: bufs.size)]
			}
		}.reject(_.isNil);
		explorerModel = this.class.prSortPairs(pairs, sortAscending);
		if(query[\words].notEmpty) { synths = synths.select { |n| nameHas.(n) } };
		if(synths.notEmpty and: { filtering.not }) {
			explorerModel = explorerModel.add((kind: \synths, names: synths.collect(_.asSymbol)));
		};
		this.prRebuildRows;
		this.prRefreshCount;
		this.prRefreshTagBar;
		librarySignature = this.prLibrarySignature;
		selected = nil;
		wasSelected !? { this.prReselect(wasSelected) };
		this.prRefreshSelection;
	}

	isExpanded { |name|
		^if((this.search ? "").isEmpty) { expandedBanks.includes(name) } { searchCollapsed.includes(name).not }
	}

	// the rows the list shows, from the model and which banks are open
	prRebuildRows {
		var rows = [], cols, keep;
		if(tree.isNil) { ^this };
		cols = explorerColumns;
		explorerModel.do { |e, ei|
			var open;
			if(e[\kind] == \bank) {
				open = this.isExpanded(e[\name]);
				rows = rows.add((level: 0, expandable: e[\files].notEmpty, expanded: open,
					cells: this.prBankCells(e, cols), ref: [ei, nil], name: e[\name]));
				if(open) {
					e[\fileData].do { |f, fi|
						rows = rows.add((level: 1, cells: this.prFileCells(f, cols), ref: [ei, fi]));
					};
				};
			} {
				open = this.isExpanded(\synths);
				rows = rows.add((level: 0, expandable: true, expanded: open, color: colAccent,
					cells: cols.collect { |k| if(k == \name) { "synths (%)".format(e[\names].size) } { if(k == \number) { e[\names].size.asString } { "" } } },
					ref: [ei, nil], name: \synths));
				if(open) {
					e[\names].do { |n, ni|
						rows = rows.add((level: 1, cells: cols.collect { |k| if(k == \name) { n.asString } { if(k == \duration) { "synth" } { "" } } }, ref: [ei, ni]));
					};
				};
			};
		};
		keep = selected;
		tree.rows = rows;
		keep !? { this.prReselect(keep) };
	}

	*dateString { |seconds|
		var days, z, era, doe, yoe, y, doy, mp, d, m;
		if(seconds.isNil) { ^"" };
		days = seconds.asInteger div: 86400;
		z = days + 719468;
		era = (if(z >= 0) { z } { z - 146096 }) div: 146097;
		doe = z - (era * 146097);
		yoe = (doe - (doe div: 1460) + (doe div: 36524) - (doe div: 146096)) div: 365;
		y = yoe + (era * 400);
		doy = doe - ((365 * yoe) + (yoe div: 4) - (yoe div: 100));
		mp = ((5 * doy) + 2) div: 153;
		d = doy - (((153 * mp) + 2) div: 5) + 1;
		m = mp + if(mp < 10) { 3 } { -9 };
		if(m <= 2) { y = y + 1 };
		^"%-%-%".format(y, m.asString.padLeft(2, "0"), d.asString.padLeft(2, "0"))
	}

	prBankCells { |e, cols|
		var files = e[\fileData];
		var newest = { |key| files.collect { |f| f[\info][key] }.reject(_.isNil).maxItem };
		^cols.collect { |k|
			switch(k,
				\name, { "% (%)".format(e[\name], e[\numBuffers]) },
				\duration, { this.class.durationRange(files.collect { |f| this.prDuration(f[\buffer]) }) },
				\number, { files.size.asString },
				\added, { this.class.dateString(newest.(\dateAdded)) },
				\created, { this.class.dateString(newest.(\created)) },
				{ "" }
			)
		}
	}

	prFileCells { |f, cols|
		var info = f[\info], none = info[\analysed] != true;
		^cols.collect { |k|
			switch(k,
				\name, { f[\name] },
				\duration, { this.prDuration(f[\buffer]).round(0.01).asString },
				\number, { f[\index].asString },
				\bpm, { if(none) { "…" } { info[\bpm] !? { |b| b.round(0.1).asString ++ if(info[\bpmSure]) { "" } { "?" } } ? "" } },
				\key, { if(none) { "…" } { info[\key] !? { |key| key ++ if(info[\keySure]) { "" } { "?" } } ? "" } },
				\tags, { if(none) { [["…", colDim]] } { this.prTagPairs(info) } },
				\added, { this.class.dateString(info[\dateAdded]) },
				\created, { this.class.dateString(info[\created]) },
				{ "" }
			)
		}
	}

	// the tags as [name, colour]: by hand in the accent, unsure in the warning colour
	prTagPairs { |info|
		^info[\tags].collect { |t|
			[t.asString, case
				{ info[\added].includes(t) } { colAccent }
				{ #['bpm-unsure', 'key-unsure'].includes(t) } { colWarn.copy.alpha_(0.7) }
				{ Color.grey(0.62) }]
		}
	}

	prRefreshCount {
		var banks = explorerModel.select { |e| e[\kind] == \bank };
		var synths = explorerModel.detect { |e| e[\kind] == \synths };
		var store = sdg.tags;
		countText.string = "% banks · % samples · % synths%".format(
			banks.size, banks.sum { |e| e[\files].size }, (synths !? { |e| e[\names].size }) ? 0,
			if(store.isAnalysing) { " · analysing %/%".format(store.done, store.total) } { "" });
	}

	// the commonest tags of the library, a button each: click narrows to it, alt-click leaves it out
	prRefreshTagBar {
		var counts, top, active, query;
		if(tagBar.isNil) { ^this };
		counts = sdg.tags.allTags;
		query = this.class.parseSearch(this.search ? "");
		top = counts.keys.asArray.sort { |a, b| counts[a] > counts[b] or: { counts[a] == counts[b] and: { a <= b } } };
		top = top.reject { |t| #['bpm-unsure', 'key-unsure'].includes(t) }.keep(9);
		tagBar.children.copy.do(_.remove);
		top.do { |t|
			var inc = query[\include].includes(t), exc = query[\exclude].includes(t);
			var b = Button().fixedHeight_(18).font_(smallFont)
				.states_([[t.asString, if(inc or: { exc }) { Color.black } { colDim },
					case { inc } { colAccent } { exc } { colWarn } { Color.grey(0.22) }]]);
			b.action = { |btn, mod| this.toggleSearchTag(t, mod.notNil and: { mod.isAlt }) };
			hints.attach(b, "% samples are % - click to show only them, alt-click to leave them out".format(counts[t], t));
			tagBar.layout.add(b);
		};
		tagBar.layout.add(nil);
	}

	prShowTagMenu {
		var counts = sdg.tags.allTags, names = counts.keys.asArray.sort;
		menu !? (_.destroy);
		menu = Menu(*([MenuAction(if(names.isEmpty) {
			if(sdg.tags.isAnalysing) { "still analysing the library..." } { "no tags yet" }
		} { "show only - alt: leave out" }).enabled_(false), MenuAction.separator]
			++ names.collect { |t|
				MenuAction("%  (%)".format(t, counts[t]), { this.toggleSearchTag(t, false) })
			} ++ [MenuAction.separator, MenuAction("leave out...").enabled_(false)]
			++ names.collect { |t| MenuAction("- %".format(t), { this.toggleSearchTag(t, true) }) }
			++ [MenuAction.separator, MenuAction("analyse the library again", { this.reanalyseAll })]
		));
		menu.front;
	}

	reanalyseAll {
		sdg.tags.enqueue(sdg.tags.libraryPaths.select { |p| sdg.tags.isAnalysed(p).not });
		this.prRefreshCount;
	}

	openAll {
		explorerModel.do { |e| if(e[\kind] == \bank) { expandedBanks.add(e[\name]) } { expandedBanks.add(\synths) } };
		searchCollapsed = IdentitySet.new;
		this.prRebuildRows;
	}

	closeAll {
		expandedBanks = IdentitySet.new;
		explorerModel.do { |e| searchCollapsed.add(if(e[\kind] == \bank) { e[\name] } { \synths }) };
		this.prRebuildRows;
	}

	// a bank opened or closed; one opened goes first in the analysis queue
	prToggleRow { |row|
		var name = row[\name];
		if(name.isNil) { ^this };
		if(this.isExpanded(name)) {
			expandedBanks.remove(name);
			searchCollapsed.add(name);
		} {
			expandedBanks.add(name);
			searchCollapsed.remove(name);
			if(name != \synths) { sdg.tags.prioritise(name) };
		};
		this.prRebuildRows;
	}

	toggleBank { |name|
		this.prToggleRow((name: name.asSymbol))
	}

	// a bank opened from code
	expandBank { |name| if(this.isExpanded(name.asSymbol).not) { this.toggleBank(name) } }

	// a duration in seconds, from the buffer - read without its header yet, there are no numbers
	// a bank's durations as the shortest to the longest: "0.12-3.4", one number when they are all one
	*durationRange { |durs|
		var lo, hi, fmt = { |x|
			var str = if(x < 10) { x.round(0.01) } { x.round(0.1) }.asString;
			if(str.endsWith(".0")) { str.drop(-2) } { str }
		};
		if(durs.isEmpty) { ^"" };
		lo = fmt.(durs.minItem); hi = fmt.(durs.maxItem);
		^if(lo == hi) { lo } { lo ++ "-" ++ hi }
	}

	prDuration { |buf|
		^if(buf.notNil and: { buf.numFrames.notNil } and: { buf.sampleRate.notNil } and: { buf.sampleRate > 0 }) {
			buf.numFrames / buf.sampleRate
		} { 0 }
	}

	// ----- selection

	// a row of the list, read back into what it stands for
	prSelectRow { |row|
		var entry, ref;
		if(row.isNil) { selected = nil; ^this.prRefreshSelection };
		ref = row[\ref];
		entry = explorerModel[ref[0]];
		selected = entry !? {
			if(ref[1].isNil) {
				if(entry[\kind] == \bank) { (kind: \bank, name: entry[\name], index: nil) } { (kind: \synthsFolder) }
			} {
				if(entry[\kind] == \bank) {
					(kind: \sample, name: entry[\name], index: entry[\files][ref[1]])
				} {
					(kind: \synth, name: entry[\names][ref[1]], index: nil)
				}
			}
		};
		// picking a sample puts its index in the n box, which is what plays from then on
		if(selected.notNil and: { selected[\kind] == \sample } and: { selected[\index].notNil }) {
			trigNBox.value = selected[\index];
		};
		this.prRefreshSelection;
	}

	// a sound chosen from code: a bank, or a bank and an index (opens the bank)
	selectSound { |name, index|
		name = name.asSymbol;
		if(index.notNil) { this.expandBank(name) };
		this.prReselect((kind: if(index.isNil) { \bank } { \sample }, name: name, index: index));
		this.prSelectRow(tree.current);
	}

	// after a rebuild, the same sound again if it is still in the list
	prReselect { |sel|
		var i, entry, j, rowIndex;
		if(sel.isNil or: { sel[\name].isNil } or: { tree.isNil }) { ^this };
		i = explorerModel.detectIndex { |e|
			(sel[\kind] == \synth and: { e[\kind] == \synths }) or: { e[\name] == sel[\name] }
		};
		if(i.isNil) { ^this };
		entry = explorerModel[i];
		j = case
		{ sel[\kind] == \sample } { entry[\files].indexOf(sel[\index]) }
		{ sel[\kind] == \synth } { entry[\names].indexOf(sel[\name]) }
		{ nil };
		rowIndex = tree.rows.detectIndex { |r| r[\ref] == [i, j] };
		if(rowIndex.isNil and: { j.notNil }) { rowIndex = tree.rows.detectIndex { |r| r[\ref] == [i, nil] } };
		rowIndex !? {
			tree.select(rowIndex, false);
			selected = sel;
			if(tree.rows[rowIndex][\ref][1].isNil and: { j.notNil }) { selected = (kind: \bank, name: entry[\name], index: nil) };
		};
	}

	prRefreshSelection {
		var buf, info, extra = "";
		if(selected.isNil or: { selected[\kind] == \synthsFolder }) {
			soundInfo.string = "";
			editBtn.enabled = false;
			revealBtn.enabled = false;
			^this
		};
		switch(selected[\kind],
			\sample, {
				buf = this.prSelectedBuffer;
				info = buf !? { buf.path !? { |p| sdg.tags.infoOf(p, false) } };
				info !? {
					if(info[\analysed] == true) {
						extra = [
							info[\bpm] !? { |b| "% bpm%".format(b.round(0.1), if(info[\bpmSure]) { "" } { "?" }) },
							info[\key] !? { |k| k ++ if(info[\keySure]) { "" } { "?" } },
							if(info[\tags].notEmpty) { info[\tags].join(" ") }
						].reject(_.isNil).collect { |s| "  ·  " ++ s }.join;
					} { extra = "  ·  not analysed yet" };
				};
				soundInfo.string = buf !? { "%:%  %  ·  % s  ·  % ch  ·  % Hz%".format(
					selected[\name], trigNBox.value.asInteger, (buf.path ? "").basename,
					this.prDuration(buf).round(0.001), buf.numChannels ? "?",
					(buf.sampleRate ? 0).asInteger, extra) } ? "";
			},
			\bank, {
				soundInfo.string = "% - a bank of % samples; n picks one".format(
					selected[\name], (dirt.soundLibrary.buffers[selected[\name]] ? []).size);
			},
			\synth, {
				soundInfo.string = "% - a synth event; n is the note".format(selected[\name]);
			}
		);
		editBtn.enabled = this.prSelectedBuffer.notNil;
		revealBtn.enabled = this.prSelectedBuffer.notNil;
	}

	// the Buffer the selection stands for: the n-th of the bank, n being the box
	selectedBuffer { ^this.prSelectedBuffer }

	prSelectedBuffer {
		if(selected.isNil or: { selected[\name].isNil } or: { selected[\kind] == \synth }) { ^nil };
		^sdg.bufferAt(selected[\name], trigNBox.value)
	}

	// the files the selection stands for: one sample's, or every one of a bank's
	selectedPaths {
		if(selected.isNil or: { selected[\name].isNil }) { ^[] };
		if(selected[\kind] == \bank) { ^sdg.tags.pathsOf(selected[\name]) };
		^this.prSelectedBuffer !? { |b| b.path !? { |p| [p] } } ? []
	}

	// The selection into sdg.sync, and its window open: one sample, or every
	// sample of a bank. Answers false when nothing is selected.
	addSelectedToSync {
		var buf;
		if(selected.isNil or: { selected[\name].isNil } or: { selected[\kind] == \synth } or: { selected[\kind] == \synthsFolder }) { ^false };
		if(selected[\kind] == \bank) { sdg.sync.addBank(selected[\name]) } {
			buf = this.prSelectedBuffer;
			if(buf.isNil) { ^false };
			sdg.sync.add(selected[\name], trigNBox.value.asInteger);
		};
		sdg.sync.gui;
		hints.note("% in the sync window".format(selected[\name]));
		^true
	}

	playSelected {
		if(selected.isNil or: { selected[\name].isNil }) { ^this };
		(
			type: \dirt,
			dirt: dirt,
			s: selected[\name],
			n: trigNBox.value.asInteger,
			orbit: trigOrbitMenu.value ? 0,
			speed: trigParams[\speed],
			amp: trigParams[\amp],
			pan: trigParams[\pan],
			latency: 0.05    // snappier than the server latency the event type defaults to
		).play;
	}

	editSelected {
		var bufs, index;
		if(selected.isNil or: { selected[\name].isNil } or: { selected[\kind] == \synth }) {
			^hints.note("pick a sample to edit");
		};
		bufs = dirt.soundLibrary.buffers[selected[\name]];
		if(bufs.isNil or: { bufs.isEmpty }) { ^hints.note("nothing to edit in " ++ selected[\name]) };
		index = trigNBox.value.asInteger.wrap(0, bufs.size - 1);
		try {
			SDGSampleEditor(sdg, selected[\name], index, trigOrbitMenu.value ? 0);
		} { |err|
			hints.note("could not open %:% - %".format(selected[\name], index, err.errorString));
			err.reportError;
		};
	}

	revealSelected {
		var buf = this.prSelectedBuffer;
		if(buf.isNil or: { buf.path.isNil }) { ^this };
		this.class.revealPath(buf.path);
	}

	// ----- tags by hand

	// One sample: its tags in a line, to change and press enter. A bank: tags
	// to add to every sample of it ("-x" takes one away).
	editTagsDialog {
		var paths = this.selectedPaths, one, field, win, apply, label;
		if(paths.isEmpty) { ^hints.note("pick a sample, or a bank, to tag") };
		one = (selected[\kind] != \bank) and: { paths.size == 1 };
		tagDialog !? { |w| if(w.isClosed.not) { w.close } };
		win = Window(if(one) { "tags - " ++ paths[0].basename } { "tags for every sample of " ++ selected[\name] },
			Rect(window.bounds.left + 200, window.bounds.top + 200, 420, 84)).background_(colBg);
		label = StaticText().stringColor_(colDim).font_(smallFont).string_(if(one) {
			"the tags, separated by spaces - enter keeps them, escape leaves them"
		} { "tags to add to all % samples, separated by spaces; -tag takes one away - enter applies".format(paths.size) });
		field = TextField().string_(if(one) { sdg.tags.tagsOf(paths[0]).join(" ") } { "" });
		apply = {
			var words = field.string.split($ ).reject(_.isEmpty);
			if(one) { sdg.tags.setTags(paths[0], words) } {
				paths.do { |p|
					words.do { |w| if(w.beginsWith("-")) { sdg.tags.removeTag(p, w.copyToEnd(1)) } { sdg.tags.addTag(p, w) } }
				};
			};
			win.close;
		};
		field.action = { apply.value };
		field.keyDownAction = { |v, char, mod, uni, keycode, key| if(key == 16r1000000) { win.close; true } { nil } };
		win.layout = VLayout(label, field, HLayout(nil,
			Button().states_([["cancel", colText, colPanel]]).action_({ win.close }),
			Button().states_([["keep", Color.black, colAccent]]).action_({ apply.value })
		)).margins_(8).spacing_(5);
		tagDialog = win;
		win.front;
		field.focus(true);
		^win
	}

	// The bpm of the selected sample, or of every sample of a selected bank, by
	// hand: see *bpmDialog.
	editBpmDialog {
		var bufs;
		if(selected.isNil or: { selected[\name].isNil } or: { [\synth, \synthsFolder].includes(selected[\kind]) }) {
			^hints.note("pick a sample, or a bank, to set its bpm")
		};
		bufs = if(selected[\kind] == \bank) { dirt.soundLibrary.buffers[selected[\name]] ? [] } { [this.prSelectedBuffer] };
		bufs = bufs.reject { |b| b.isNil or: { b.path.isNil } };
		if(bufs.isEmpty) { ^hints.note("nothing to set a bpm on") };
		^this.class.bpmDialog(sdg, bufs.collect { |b| [b.path, this.prDuration(b)] },
			if(selected[\kind] == \bank) { selected[\name].asString } { nil },
			Point(window.bounds.left + 200, window.bounds.top + 200))
	}

	// A small window to set a bpm by hand, for one file or several: pairs is
	// [[path, seconds] ...]. The bpm typed goes to every file; beats typed
	// instead give each file the bpm its own length makes of that many beats -
	// what a bank of 4-beat loops at different tempos wants. ÷2 and ×2 act on
	// the box, back to the analysis forgets what was set by hand. Kept by
	// sdg.tags, so the explorer, the editor, the sync window and Tidal's
	// sdgbpm see it at once.
	*bpmDialog { |sdg, pairs, bankName, where|
		var store = sdg.tags, one = pairs.size == 1, info, bpmBox, beatsBox, from = \bpm, win, apply, label, flag;
		var font = Font(Font.defaultSansFace, 11), dur = pairs[0][1];
		var btn = { |str, func, back| Button().states_([[str, if(back.notNil) { Color.black } { colText }, back ? colPanel]]).action_({ func.value }) };
		bpmDialogWindow !? { |w| if(w.isClosed.not) { w.close } };
		info = store.infoOf(pairs[0][0], false);
		where = where ? Point(200, 200);
		win = Window(if(one) { "bpm - " ++ pairs[0][0].basename } { "bpm for every sample of " ++ (bankName ? "the bank") },
			Rect(where.x, where.y, 440, 112)).background_(colBg);
		if(GUI.id == \qt) { win.view.palette = QPalette.dark };
		label = StaticText().stringColor_(colDim).font_(Font(Font.defaultSansFace, 9)).string_(if(one) {
			case
			{ info[\bpmByHand] == true } { "set by hand - the analysis is kept underneath" }
			{ info[\bpm].notNil } { "the analysis heard % bpm%".format(info[\bpm].round(0.1), if(info[\bpmSure] == true) { "" } { ", not sure" }) }
			{ info[\analysed] == true } { "the analysis heard no tempo" }
			{ "not analysed yet" }
		} { "one bpm for all % samples, or beats: each gets the bpm its own length makes".format(pairs.size) });
		bpmBox = NumberBox().decimals_(2).clipLo_(0).clipHi_(999).fixedWidth_(70).font_(font);
		beatsBox = NumberBox().decimals_(2).clipLo_(0).clipHi_(4096).fixedWidth_(56).font_(font);
		flag = StaticText().stringColor_(colDim).font_(font).fixedWidth_(110);
		if(one) {
			info[\bpm] !? { |b| bpmBox.value = b; if(dur > 0) { beatsBox.value = (dur * b / 60).round(0.01) } };
		};
		// the box last typed in is the one that applies
		bpmBox.action = { |b| from = \bpm; if(one and: { dur > 0 }) { beatsBox.value = (dur * b.value / 60).round(0.01) } };
		beatsBox.action = { |b| from = \beats; if(one and: { dur > 0 }) { bpmBox.value = b.value * 60 / dur } };
		apply = {
			pairs.do { |pd|
				var bpm = if(from == \beats) { if(pd[1] > 0 and: { beatsBox.value > 0 }) { beatsBox.value * 60 / pd[1] } } { bpmBox.value };
				if(bpm.notNil and: { bpm > 0 }) { store.setBpm(pd[0], bpm) };
			};
			win.close;
		};
		[bpmBox, beatsBox].do { |v|
			v.keyDownAction = { |view, char, mod, uni, keycode, key|
				case
				{ key == 16r1000000 } { win.close; true }
				// return: the box takes the typed number first (its action), then it applies
				{ key == 16r1000004 or: { key == 16r1000005 } } { { if(win.isClosed.not) { apply.value } }.defer(0.05); nil }
				{ nil }
			}
		};
		win.layout = VLayout(
			label,
			HLayout(StaticText().string_("bpm").stringColor_(colDim).font_(font), bpmBox,
				btn.("÷2", { bpmBox.valueAction = bpmBox.value / 2 }).fixedWidth_(30),
				btn.("×2", { bpmBox.valueAction = bpmBox.value * 2 }).fixedWidth_(30),
				10, StaticText().string_("or beats").stringColor_(colDim).font_(font), beatsBox, nil).spacing_(4),
			HLayout(
				btn.("back to the analysis", { pairs.do { |pd| store.setBpm(pd[0], nil) }; win.close })
					.enabled_(pairs.any { |pd| store.infoOf(pd[0], false)[\bpmByHand] == true }),
				nil,
				btn.("cancel", { win.close }),
				btn.("keep", { apply.value }, colAccent)
			).spacing_(4)
		).margins_(8).spacing_(5);
		bpmDialogWindow = win;
		win.front;
		bpmBox.focus(true);
		^win
	}

	*prScaleBpm { |sdg, path, factor|
		var info = sdg.tags.infoOf(path, false);
		info[\bpm] !? { |b| sdg.tags.setBpm(path, b * factor) };
	}

	// the right click on the list: what can be done with the sound under it
	prShowMenu {
		var buf, path, here, info, paths, isBank;
		buf = this.prSelectedBuffer;
		path = buf !? (_.path);
		here = path.notNil and: { File.exists(path) };
		paths = this.selectedPaths;
		isBank = selected.notNil and: { selected[\kind] == \bank };
		info = if(isBank.not and: { path.notNil }) { sdg.tags.infoOf(path, false) };
		menu !? (_.destroy);
		menu = Menu(*([
			MenuAction(if(selected.notNil and: { selected[\name].notNil }) { selected[\name].asString } { "nothing selected" })
				.enabled_(false),
			MenuAction.separator,
			MenuAction("play", { this.playSelected }).enabled_(selected.notNil and: { selected[\name].notNil }),
			MenuAction("edit...", { this.editSelected }).enabled_(buf.notNil),
			MenuAction(if(isBank) { "add all to sync" } { "add to sync" }, { this.addSelectedToSync }).enabled_(paths.notEmpty),
			MenuAction.separator,
			MenuAction(if(isBank) { "add tags to all..." } { "edit tags..." }, { this.editTagsDialog }).enabled_(paths.notEmpty)
		] ++ (info !? { |i| i[\tags].collect { |t| MenuAction("remove " ++ t, { sdg.tags.removeTag(path, t) }) } } ? [])
		++ [
			MenuAction(if(isBank) { "set the bpm of all..." } { "set bpm..." }, { this.editBpmDialog }).enabled_(paths.notEmpty),
			MenuAction("bpm ÷2", { paths.do { |p| this.class.prScaleBpm(sdg, p, 0.5) } })
				.enabled_(paths.any { |p| sdg.tags.infoOf(p, false)[\bpm].notNil }),
			MenuAction("bpm ×2", { paths.do { |p| this.class.prScaleBpm(sdg, p, 2) } })
				.enabled_(paths.any { |p| sdg.tags.infoOf(p, false)[\bpm].notNil }),
			MenuAction(if(isBank) { "bpm of all back to the analysis" } { "bpm back to the analysis" }, {
				paths.do { |p| if(sdg.tags.infoOf(p, false)[\bpmByHand]) { sdg.tags.setBpm(p, nil) } }
			}).enabled_(paths.any { |p| sdg.tags.infoOf(p, false)[\bpmByHand] == true }),
			MenuAction(if(isBank) { "tags of all back to the analysis" } { "tags back to the analysis" }, {
				paths.do { |p| sdg.tags.resetTags(p) }
			}).enabled_(paths.notEmpty),
			MenuAction(if(isBank) { "analyse the bank again" } { "analyse again" }, {
				fork({ paths.do { |p| sdg.tags.analyse(p, true); 0.001.wait } }, AppClock)
			}).enabled_(paths.notEmpty),
			MenuAction.separator,
			MenuAction(this.class.revealString, { this.class.revealPath(path) }).enabled_(here),
			MenuAction("open in default app", { this.class.openPath(path) }).enabled_(here),
			MenuAction("copy path", { this.class.copyPathToClipboard(path) }).enabled_(path.notNil),
			MenuAction.separator,
			MenuAction("open all", { this.openAll }),
			MenuAction("close all", { this.closeAll })
		]));
		menu.front;
	}

	// what this machine calls showing a file where it lives
	*revealString {
		^Platform.case(
			\osx, { "reveal in Finder" },
			\windows, { "show in Explorer" },
			{ "show in file manager" }
		)
	}

	*revealPath { |path|
		if(path.isNil) { ^false };
		^Platform.case(
			\osx, { ("open -R" + path.shellQuote).unixCmd; true },
			\linux, { ("xdg-open" + path.dirname.shellQuote).unixCmd; true },
			\windows, { ("explorer /select," ++ this.prWindowsPath(path).quote).unixCmd; true },
			{ false }
		)
	}

	*openPath { |path|
		if(path.isNil) { ^false };
		^Platform.case(
			\osx, { ("open" + path.shellQuote).unixCmd; true },
			\linux, { ("xdg-open" + path.shellQuote).unixCmd; true },
			\windows, { ("start \"\"" + this.prWindowsPath(path).quote).unixCmd; true },
			{ false }
		)
	}

	// posted whatever happens - the clipboard is best effort
	*copyPathToClipboard { |path|
		var cmd;
		if(path.isNil) { ^false };
		path.postln;
		if(thisProcess.platform.name == \windows) { path = this.prWindowsPath(path) };
		cmd = this.prClipboardCommand(path);
		cmd !? { cmd.unixCmd };
		^cmd.notNil
	}

	// the shell command that puts text on the clipboard, for this platform or
	// the one named; nil where there is none. On Windows unixCmd runs `cmd /c`,
	// whose command line ends at the first newline and whose quoting a " in
	// the text breaks (Tidal code has both), so the text goes through a temp
	// file that PowerShell reads into Set-Clipboard; only a ' in the file's
	// path needs doubling there
	*prClipboardCommand { |text, platform|
		^switch(platform ? thisProcess.platform.name,
			\osx, { "printf %s" + text.shellQuote + "| pbcopy" },
			\linux, { "printf %s" + text.shellQuote + "| xclip -selection clipboard" },
			\windows, {
				var file = Platform.defaultTempDir +/+ "superdirt-gui-clipboard.txt";
				var ok = try { File.use(file, "w", { |f| f.write(text.asString) }); true } { false };
				if(ok) {
					"powershell -NoProfile -NonInteractive -Command \"Get-Content -Raw -Encoding UTF8 -LiteralPath '"
						++ this.prWindowsPath(file).replace("'", "''") ++ "' | Set-Clipboard\""
				}
			}
		)
	}

	// Explorer and start want \; SuperCollider may hand a path over with /
	*prWindowsPath { |path| ^path.asString.tr($/, $\\) }

	// ------------------------------------------------------------ modules tab

	// what the SuperDirtGUI read from its folders, a list per kind with new... and
	// edit... under it, and the ways to change everything at once
	makeLoadedSection {
		var section = View().background_(colPanel);
		var header = StaticText().string_("modules").stringColor_(colText)
			.font_(Font.sansSerif(12, true));
		var columns = [
			[\synths, "synths"], [\modules, "effects"],
			[\players, "players"], [\effects, "global effects"]
		];
		var lists, reloadBtn, freeBtn, bankBtn;
		loadedLists = IdentityDictionary.new;
		loadedEntries = IdentityDictionary.new;
		lists = columns.collect { |pair|
			var kind = pair[0], label = SuperDirtGUI.kindLabel(kind);
			var list = ListView().selectionMode_(\single).font_(font)
				.background_(Color.grey(0.16)).minHeight_(130);
			var newBtn = this.prButton("new...", 50, { this.newFileDialog(kind) },
				"a new % file: a copy of the example, or of the one selected, under a name you give; it opens in the IDE".format(label));
			var editBtn = this.prButton("edit...", 50, { this.editFile(kind) },
				"the selected %'s file, open in the IDE - save it and it is read again".format(label));
			loadedLists[kind] = list;
			list.action = { this.prLoadedSelect(kind) };
			list.mouseDownAction = { |v, x, y, mods, button, clickCount|
				loadedKind = kind;
				case
				{ (button ? 0) == 1 } { { this.prShowLoadedMenu(kind) }.defer(0.1) }
				// the first press of the two has made the row current
				{ clickCount == 2 } { this.editFile(kind) };
				nil		// the press is the list's own: it selects the row
			};
			list.keyDownAction = { |v, char, mod, uni, keycode, key|
				if(key == 16r1000004 or: { key == 16r1000005 }) { this.editFile(kind); true } { nil }
			};
			hints.attach(list, "the % read from %/ - double click or enter opens one's file, right click for more".format(pair[1], kind));
			VLayout(
				StaticText().string_(pair[1]).stringColor_(colDim).font_(smallFont),
				list,
				HLayout(newBtn, editBtn).spacing_(2)
			).spacing_(2)
		};
		fileText = hints.attach(StaticText().stringColor_(colDim).font_(smallFont),
			"the file the selected name came from");
		reloadBtn = this.prButton("reload", 60, { sdg.reload },
			"read every file in synths/, modules/, players/ and effects/ again - no reboot");
		freeBtn = this.prButton("free", 50, { sdg.free },
			"take the effects, players and global effects out of SuperDirt; the window closes");
		bankBtn = this.prButton("load folder...", 92, { this.loadFolderDialog },
			"read more samples from a folder: its files become a bank named after it, each subfolder a bank of its own");
		pathText = hints.attach(StaticText().string_(sdg.path.asString).stringColor_(colDim).font_(smallFont),
			"the folder the files are read from");
		notesView = hints.attach(TextView().editable_(false).font_(Font.monospace(10))
			.background_(Color.grey(0.16)).minHeight_(110),
			"what the selected one does, and its parameters - the comments at the top of its file");

		section.layout = VLayout(
			header,
			[HLayout(*lists).spacing_(4), stretch: 1],
			fileText,
			HLayout(reloadBtn, freeBtn, bankBtn, nil).spacing_(4),
			pathText,
			[notesView, stretch: 2],
			[this.makePreviewSection, stretch: 3]
		).margins_(6).spacing_(6);
		^section
	}

	// the four lists to what the SuperDirtGUI holds now, each keeping its
	// selection; a name whose file failed on its last read is marked, and a file
	// that failed before it defined anything is listed by its file name
	refreshLoaded {
		var plain = Color.grey(0.16), marked = colWarn.blend(plain, 0.55);
		if(loadedLists.isNil) { ^this };
		loadedLists.keysValuesDo { |kind, list|
			var was = this.prLoadedEntry(kind);
			var entries = sdg.entries(kind);
			loadedEntries[kind] = entries;
			list.items = entries.collect { |e| (e[\name] ?? { e[\path].basename }).asString };
			list.colors = entries.collect { |e| if(e[\failed]) { marked } { plain } };
			list.value = was !? {
				entries.detectIndex { |e| e[\name] == was[\name] and: { e[\path] == was[\path] } }
			};
		};
		pathText !? { pathText.string = sdg.path.asString };
		this.prShowLoadedSelection;
	}

	// the entry selected in a kind's list, as SuperDirtGUI:entries gives it, or nil
	prLoadedEntry { |kind|
		var i = loadedLists !? { loadedLists[kind] !? { |list| list.value } };
		^i !? { loadedEntries[kind] !? { |entries| entries[i] } }
	}

	// one list's selection is the tab's: the others let go of theirs. A list
	// letting go says nothing, so this answers only to a selection.
	prLoadedSelect { |kind|
		if(this.prLoadedEntry(kind).isNil) { ^this };
		loadedKind = kind;
		loadedLists.keysValuesDo { |other, list| if(other != kind) { list.value = nil } };
		this.prShowLoadedSelection;
	}

	prShowLoadedSelection {
		var entry = loadedKind !? { this.prLoadedEntry(loadedKind) };
		if(fileText.isNil) { ^this };
		fileText.string = case
		{ entry.isNil } { "" }
		{ entry[\path].isNil } { "% - registered from code, there is no file".format(entry[\name]) }
		{ entry[\missing].notNil } { "% needs %, which is not installed".format(this.prShortPath(entry[\path]), entry[\missing].join(", ")) }
		{ entry[\name].isNil } { "% did not load - open it, fix it, save it".format(this.prShortPath(entry[\path])) }
		{ entry[\failed] } { "% - % failed; the last version that loaded plays".format(entry[\name], this.prShortPath(entry[\path])) }
		{ "% - %".format(entry[\name], this.prShortPath(entry[\path])) };
		fileText.stringColor = if(entry.notNil and: { entry[\failed] }) { colWarn } { colDim };
		this.prShowNotes(entry);
		this.prShowPreview(entry);
	}

	// what the selected name does and takes, at the bottom of the tab: a line per
	// parameter, name and range in columns and the text wrapped under its own column
	prShowNotes { |entry|
		var notes, params, nameWidth, rangeWidth, spans = [], text = "";
		if(notesView.isNil) { ^this };
		if(entry.isNil) {
			notesString = "";
			notesView.string = "";
			^this
		};
		notes = if(entry[\name].notNil) {
			sdg.notes(loadedKind, entry[\name])
		} {
			SuperDirtGUI.fileNotes(entry[\path])
		};
		params = notes[\params].collect { |p|
			p[\range] = p[\range] ?? { "default %".format(p[\default].asCompileString) };
			p
		};
		nameWidth = params.collect { |p| p[\name].asString.size }.maxItem ? 0;
		rangeWidth = params.collect { |p| p[\range].size }.maxItem ? 0;
		text = if(notes[\description].isEmpty) { "no description in the file" } { notes[\description] };
		// an effect's heading in the add menus, from its "// category:" line
		if(#[\modules, \effects].includes(loadedKind)) {
			text = text ++ "\n\ncategory: " ++ SuperDirtGUI.categoryLabel(notes[\category])
		};
		text = text ++ "\n\n";
		text = text ++ if(params.isEmpty) { "no parameters of its own" } { "parameters and ranges\n" };
		params.do { |p|
			var name = p[\name].asString;
			spans = spans.add([text.size, name.size]);
			text = text ++ name.padRight(nameWidth + 2) ++ p[\range].padRight(rangeWidth + 2);
			text = text ++ this.prHanging(p[\text], nameWidth + rangeWidth + 4) ++ "\n";
		};
		notesString = text;
		notesView.string = text;
		notesView.setStringColor(colDim, 0, text.size);
		notesView.setStringColor(colText, 0, notes[\description].size);
		spans.do { |span| notesView.setStringColor(colAccent, span[0], span[1]) };
	}

	// ------------------------------------------------------------ synth preview

	// Under the notes, for a synth: a knob per parameter of its header, a note, the
	// preset (n, a menu of the synth's presets by name), a sustain and an orbit.
	// play sends one \dirt event, as Tidal would; copy puts it on the clipboard as
	// Tidal code, with only the parameters dialed away from the preset; random
	// throws every knob; save as... and delete change the synth's presets
	// (SuperDirtGUI:savePreset, removePreset, kept by SDGSynthPresets), file
	// presets puts back its file's. The model is preview, an Event; checks read it.
	makePreviewSection {
		previewHolder = View().background_(Color.grey(0.16)).visible_(false);
		previewHolder.layout = VLayout().margins_(4).spacing_(4);
		^previewHolder
	}

	// the selected synth's preview, made again when another one is selected or its
	// SynthDef changed (its file saved); what was dialed stays across a reload
	prShowPreview { |entry|
		var name = entry !? { entry[\name] }, desc, old;
		if(previewHolder.isNil) { ^this };
		desc = name !? { SynthDescLib.global.at(name.asSymbol) };
		if(loadedKind != \synths or: { desc.isNil }) {
			preview = nil;
			previewContent !? { previewContent.remove; previewContent = nil };
			previewHolder.visible = false;
			^this
		};
		name = name.asSymbol;
		if(preview.notNil and: { preview[\name] == name } and: { preview[\desc] === desc }) { ^this };
		old = if(preview.notNil and: { preview[\name] == name }) { preview };
		preview = this.prPreviewModel(name, desc, old);
		this.prBuildPreview;
		previewHolder.visible = true;
	}

	// the synth's presets changed: the menu again, on preset n if given, else on the
	// one it showed (the dialed values kept unless n is given)
	prPresetsChanged { |synth, n|
		var old = preview;
		if(old.isNil or: { old[\name] != synth.asSymbol } or: { this.isOpen.not }) { ^this };
		preview = this.prPreviewModel(old[\name], old[\desc], if(n.isNil) { old });
		n !? {
			preview[\n] = n.clip(0, (preview[\presets] - 1).max(0));
			preview[\note] = old[\note];
			preview[\orbit] = old[\orbit];
			this.prPreviewPreset(preview);
		};
		this.prBuildPreview;
	}

	// (name:, desc:, presets:, presetNames:, n:, note:, sustain:, orbit:, params: [key],
	// specs:, values:, start:) - start is what the preset (or the def) gives, values what plays
	prPreviewModel { |name, desc, old|
		var notes = sdg.notes(\synths, name);
		var controls = desc.controls.collect { |c| c.name.asSymbol };
		var params = notes[\params].select { |p| controls.includes(p[\name].asSymbol) };
		var model = (
			name: name, desc: desc,
			presets: sdg.presetsOf(name).size,
			presetNames: sdg.presetNames(name),
			n: 0, note: 0, orbit: selectedOrbitIndex,
			params: params.collect { |p| p[\name].asSymbol },
			ranges: IdentityDictionary.new,
			specs: IdentityDictionary.new,
			values: IdentityDictionary.new,
			start: IdentityDictionary.new
		);
		params.do { |p|
			var key = p[\name].asSymbol;
			model[\ranges][key] = p[\range];
			model[\specs][key] = this.class.previewSpec(p[\range], this.class.prControlDefault(desc, key));
		};
		old !? {
			model[\n] = old[\n].clip(0, (model[\presets] - 1).max(0));
			model[\note] = old[\note];
			model[\orbit] = old[\orbit];
		};
		this.prPreviewPreset(model);
		// what was dialed before, where the parameter still is
		old !? {
			old[\values].keysValuesDo { |key, v|
				if(model[\specs][key].notNil and: { this.prPreviewChanged(old, key) }) {
					model[\values][key] = model[\specs][key].constrain(v)
				}
			};
			if(old[\sustain] != old[\startSustain]) { model[\sustain] = old[\sustain] };
		};
		^model
	}

	// the values of the model's preset n (a def default where the preset says nothing)
	// as both what plays and what copy compares with
	prPreviewPreset { |model|
		var event = sdg.presetsOf(model[\name])[model[\n]] ? ();
		var defSustain = this.class.prControlDefault(model[\desc], \sustain);
		model[\params].do { |key|
			var v = event[key];
			v = if(v.isNumber) { v } { this.class.prControlDefault(model[\desc], key) ? model[\specs][key].default };
			model[\start][key] = v;
			model[\values][key] = v;
		};
		// a drum voice lasts its def's sustain (useSynthDefSustain); anything else a second
		model[\startSustain] = if(event[\unitDuration].notNil and: { defSustain.isNumber }) { defSustain.clip(0.05, 8) } { 1 };
		model[\sustain] = model[\startSustain];
	}

	*prControlDefault { |desc, key|
		var control = desc.controls.detect { |c| c.name.asSymbol == key };
		^control !? { |c| if(c.defaultValue.isNumber) { c.defaultValue } }
	}

	// a knob's spec from a header's range, "30 to 120 Hz": exponential where the top
	// is twenty times the bottom or more; a parameter with no range goes 0 to twice
	// its default
	*previewSpec { |range, default|
		var m = range !? { range.asString.findRegexp("^\\s*(-?[0-9]*\\.?[0-9]+)\\s+to\\s+(-?[0-9]*\\.?[0-9]+)") };
		var lo, hi;
		default = default ? 0;
		if(m.notNil and: { m.size >= 3 }) {
			lo = m[1][1].asFloat;
			hi = m[2][1].asFloat;
		} {
			lo = min(0, default);
			hi = max(1, default.abs * 2);
		};
		if(hi <= lo) { hi = lo + 1 };
		^ControlSpec(lo, hi, if(lo > 0 and: { hi / lo >= 20 }) { \exp } { \lin }, 0, default.clip(lo, hi))
	}

	prPreviewChanged { |model, key|
		var spec = model[\specs][key];
		^(model[\values][key] - model[\start][key]).abs > (abs(spec.maxval - spec.minval) * 1e-4)
	}

	// the event play sends: everything the knobs say, into the chosen orbit
	previewEvent {
		var event;
		if(preview.isNil) { ^nil };
		event = (type: \dirt, dirt: dirt, s: preview[\name], orbit: preview[\orbit] ? 0,
			note: preview[\note], sustain: preview[\sustain],
			latency: 0.05);    // snappier than the server latency the event type defaults to
		if(preview[\presets] > 0) { event[\n] = preview[\n] };
		preview[\values].keysValuesDo { |key, v| event[key] = v };
		^event
	}

	previewPlay {
		this.previewEvent !? { |event| event.play }
	}

	// the preview as a line of Tidal: the preset, the note, the sustain if changed,
	// and every parameter dialed away from the preset, as pF so it needs no boot.hs
	previewCode {
		var m = preview, code;
		if(m.isNil) { ^nil };
		code = "d1 $ s \"%\"".format(if(m[\presets] > 0 and: { m[\n] > 0 }) { "%:%".format(m[\name], m[\n]) } { m[\name] });
		if(m[\note] != 0) { code = code ++ " # note " ++ this.class.tidalNumber(m[\note]) };
		if((m[\sustain] - m[\startSustain]).abs > 1e-4) { code = code ++ " # sustain " ++ this.class.tidalNumber(m[\sustain]) };
		m[\params].do { |key|
			if(this.prPreviewChanged(m, key)) {
				code = code ++ " # pF \"%\" %".format(key, this.class.tidalNumber(m[\values][key]))
			}
		};
		^code
	}

	// a number as Tidal reads it: a negative one in brackets
	*tidalNumber { |v|
		var r = if(v.abs >= 100) { v.round(1) } { v.round(0.0001) };
		var str = if(r == r.round) { r.round.asInteger.asString } { r.asFloat.asStringPrec(6) };
		^if(r < 0) { "(" ++ str ++ ")" } { str }
	}

	// previewCode on the clipboard, and in the post window whatever happens
	previewCopy {
		var code = this.previewCode, cmd;
		if(code.isNil) { ^false };
		code.postln;
		cmd = this.class.prClipboardCommand(code);
		cmd !? { cmd.unixCmd };
		hints.note(if(cmd.notNil) { "on the clipboard: " ++ code } { "no clipboard here - the code is in the post window" });
		^cmd.notNil
	}

	// every knob back to the preset
	previewReset {
		if(preview.isNil) { ^this };
		this.prPreviewPreset(preview);
		this.prPreviewShowValues;
	}

	// every knob somewhere in its range, evenly over the knob's travel (an
	// exponential knob as often low as high); the note, preset and sustain stay
	previewRandomize {
		if(preview.isNil) { ^this };
		preview[\params].do { |key|
			preview[\values][key] = preview[\specs][key].map(1.0.rand)
		};
		this.prPreviewShowValues;
	}

	// the knobs as they are, as preset n of the synth under a name: answers n
	previewSave { |name|
		var n;
		if(preview.isNil) { ^nil };
		name = (name ? "").asString.stripWhiteSpace;
		if(name.isEmpty) { name = "preset %".format(preview[\presets]) };
		n = sdg.savePreset(preview[\name], name, preview[\values]);
		this.prPresetsChanged(preview[\name], n);
		hints.note("saved as %:% \"%\" - on this machine; file presets puts back the file's".format(preview[\name], n, name));
		^n
	}

	previewDelete {
		var m = preview, name;
		if(m.isNil or: { m[\presets] == 0 }) { ^false };
		name = m[\presetNames][m[\n]];
		sdg.removePreset(m[\name], m[\n]);
		this.prPresetsChanged(m[\name], m[\n].min(m[\presets] - 2).max(0));
		hints.note("% deleted from % - the ones after it moved down by one".format(name, m[\name]));
		^true
	}

	previewRestore {
		if(preview.isNil) { ^this };
		sdg.restoreFilePresets(preview[\name]);
		this.prPresetsChanged(preview[\name], 0);
		hints.note("% plays its file's presets again".format(preview[\name]));
	}

	// a small window asking the new preset's name; enter or save saves it
	previewSaveDialog {
		var win, field, save, m = preview;
		if(m.isNil) { ^nil };
		win = Window("save a preset of %".format(m[\name]),
			Rect(window.bounds.left + 300, window.bounds.top + 300, 360, 76)).background_(colBg);
		if(GUI.id == \qt) { win.view.palette = QPalette.dark };
		field = TextField().font_(font).string_("my % %".format(m[\name], m[\presets]));
		save = { this.previewSave(field.string); win.close };
		field.action = { save.value };
		field.keyDownAction = { |view, char, mod, uni, keycode, key|
			if(key == 16r1000000) { win.close; true } { nil }
		};
		win.layout = VLayout(
			StaticText().string_("as %:% - a name to know it by in the menu".format(m[\name], m[\presets]))
				.stringColor_(colDim).font_(smallFont),
			HLayout(field,
				Button().states_([["save", colText, Color.grey(0.26)]]).action_({ save.value }),
				Button().states_([["cancel", colText, Color.grey(0.26)]]).action_({ win.close })
			).spacing_(4)
		).margins_(8).spacing_(6);
		win.front;
		field.focus(true);
		^win
	}

	prPreviewShowValues {
		preview[\views].keysValuesDo { |key, views|
			var spec = if(key == \sustain) { this.class.previewLengthSpec } { preview[\specs][key] };
			var v = if(key == \sustain) { preview[\sustain] } { preview[\values][key] };
			views[0].value = spec.unmap(v);
			views[1].value = v;
		}
	}

	*previewLengthSpec { ^ControlSpec(0.05, 8, \exp, 0, 1) }

	prBuildPreview {
		var m = preview, cells, title, noteBox, noteText, presetMenu, orbitMenu, scroll, canvas;
		var playBtn, copyBtn, randomBtn, resetBtn, saveBtn, deleteBtn, restoreBtn, deleteArmed = false;
		var own = sdg.hasOwnPresets(m[\name]);
		var label = { |str| StaticText().string_(str).stringColor_(colDim).font_(smallFont) };
		var noteName = { |note|
			var midi = (note + 60).round.asInteger;
			SDGSampleFeatures.noteNames[midi % 12].toLower ++ (midi div: 12)
		};
		previewContent !? { previewContent.remove };
		m[\views] = IdentityDictionary.new;
		title = StaticText().string_("preview " ++ m[\name]).stringColor_(colText).font_(boldFont);

		noteBox = NumberBox().fixedWidth_(40).decimals_(0).step_(1).clipLo_(-48).clipHi_(48)
			.value_(m[\note]).font_(smallFont).normalColor_(colText);
		noteText = StaticText().string_(noteName.(m[\note])).stringColor_(colDim).font_(smallFont).fixedWidth_(26);
		noteBox.action = { |b|
			m[\note] = b.value.asInteger;
			noteText.string = noteName.(m[\note]);
			this.previewPlay;
		};
		hints.attach([noteBox, noteText], "note - semitones from the synth's own pitch, as Tidal's note: 0 is c5; changing it plays");

		presetMenu = PopUpMenu().font_(smallFont).minWidth_(140)
			.items_(if(m[\presets] > 0) {
				m[\presetNames].collect { |str, i| "% %".format(i, str) }
			} { ["no presets: n is the pitch"] })
			.value_(m[\n]).enabled_(m[\presets] > 0);
		presetMenu.action = { |v|
			m[\n] = v.value;
			this.previewReset;
			this.previewPlay;
		};
		hints.attach(presetMenu, if(m[\presets] > 0) {
			"n - the synth's presets%: picking one sets every knob to it and plays".format(if(own) { " (this machine's list)" } { "" })
		} {
			"this synth has no presets, so Tidal's n is its pitch - save as... makes the first"
		});

		orbitMenu = PopUpMenu().fixedWidth_(78).font_(smallFont)
			.items_(dirt.orbits.collect { |o, i| "orbit " ++ i }).value_(m[\orbit]);
		orbitMenu.action = { |v| m[\orbit] = v.value };
		hints.attach(orbitMenu, "the orbit the preview plays into, with its effects and fader");

		playBtn = this.prButton("play", 44, { this.previewPlay },
			"play one note of the synth with these settings - releasing a knob plays too");
		copyBtn = this.prButton("copy", 44, { this.previewCopy },
			"put it on the clipboard as Tidal: s, n, note, and the parameters moved away from the preset");
		randomBtn = this.prButton("random", 54, { this.previewRandomize; this.previewPlay },
			"every knob somewhere in its range, and play it - the note, preset and sustain stay");
		resetBtn = this.prButton("reset", 44, { this.previewReset; this.previewPlay },
			"every knob back to the preset");
		saveBtn = this.prButton("save as...", 66, { this.previewSaveDialog },
			"the knobs as a new preset at the end of the menu, under a name - kept on this machine");
		deleteBtn = Button().fixedWidth_(52).states_([
			["delete", colText, Color.grey(0.26)], ["sure?", Color.black, colWarn]
		]).enabled_(m[\presets] > 0);
		deleteBtn.action = { |b|
			if(deleteArmed) {
				deleteArmed = false;
				this.previewDelete;
			} {
				deleteArmed = true;
				b.value = 1;
				{ deleteArmed = false; if(b.isClosed.not) { b.value = 0 } }.defer(3);
			}
		};
		hints.attach(deleteBtn, "take the preset shown out of the synth - click twice; the ones after it move down by one n");
		restoreBtn = this.prButton("file presets", 74, { this.previewRestore },
			"forget this machine's list and play the presets the synth's file registers").enabled_(own);
		// a disabled Button keeps its states' colours in Qt: dim them
		if(own.not) { restoreBtn.states = [["file presets", colDim, Color.grey(0.2)]] };
		if(m[\presets] == 0) { deleteBtn.states = [["delete", colDim, Color.grey(0.2)], ["sure?", Color.black, colWarn]] };

		cells = m[\params].collect { |key| this.prPreviewCell(key) };
		cells = cells.add(this.prPreviewLengthCell);
		canvas = View().background_(Color.grey(0.16));
		canvas.layout = VLayout(*(cells.clump(6).collect { |row|
			HLayout(*(row ++ [nil])).margins_(0).spacing_(4)
		} ++ [nil])).margins_(0).spacing_(6);
		scroll = ScrollView().autohidesScrollers_(true).hasBorder_(false)
			.background_(Color.grey(0.16)).minHeight_(84).canvas_(canvas);

		previewContent = View().layout_(VLayout(
			HLayout(title, nil, label.("note"), noteBox, noteText, orbitMenu).spacing_(4),
			HLayout(label.("n"), presetMenu, saveBtn, deleteBtn, restoreBtn, nil).spacing_(4),
			HLayout(playBtn, copyBtn, randomBtn, resetBtn, nil).spacing_(4),
			[scroll, stretch: 1]
		).margins_(0).spacing_(4));
		previewHolder.layout.add(previewContent);
	}

	prPreviewCell { |key|
		var m = preview, spec = m[\specs][key];
		// a double click: the preset's value, as reset does for all of them
		var knob = this.class.makeKnob(36, spec.minval < 0 and: { spec.maxval > 0 },
			{ m[\start][key] !? { |v| spec.unmap(v) } });
		var box = NumberBox().decimals_(3);
		knob.value = spec.unmap(m[\values][key]);
		box.value = m[\values][key];
		knob.action = { |k|
			m[\values][key] = spec.map(k.value);
			box.value = m[\values][key];
		};
		knob.mouseUpAction = { this.previewPlay; nil };
		box.action = { |b|
			m[\values][key] = spec.constrain(b.value);
			b.value = m[\values][key];
			knob.value = spec.unmap(m[\values][key]);
			this.previewPlay;
		};
		m[\views][key] = [knob, box];
		^this.prKnobCell(key.asString, knob, box,
			"% - % - dial it and let go to hear it; copy writes the ones you moved".format(key, m[\ranges][key] ? "no range in the header"))
	}

	prPreviewLengthCell {
		var m = preview, spec = this.class.previewLengthSpec;
		var knob = this.class.makeKnob(36, false, { m[\startSustain] !? { |v| spec.unmap(v) } });
		var box = NumberBox().decimals_(3);
		knob.value = spec.unmap(m[\sustain]);
		box.value = m[\sustain];
		knob.action = { |k| m[\sustain] = spec.map(k.value); box.value = m[\sustain] };
		knob.mouseUpAction = { this.previewPlay; nil };
		box.action = { |b|
			m[\sustain] = spec.constrain(b.value);
			b.value = m[\sustain];
			knob.value = spec.unmap(m[\sustain]);
			this.previewPlay;
		};
		m[\views][\sustain] = [knob, box];
		^this.prKnobCell("sustain", knob, box,
			"sustain - how long the note is held, in seconds; a drum voice only shortens (a choke)")
	}

	// words wrapped to the width of the notes, every line after the first indented
	// by what stands to the left of them
	prHanging { |string, indent|
		var width = (notesColumns - indent).max(16), lines = [""];
		string.split($ ).do { |word|
			if(lines.last.notEmpty and: { lines.last.size + word.size + 1 > width }) { lines = lines.add("") };
			lines[lines.size - 1] = if(lines.last.isEmpty) { word } { lines.last ++ " " ++ word };
		};
		^lines.join("\n" ++ String.fill(indent, $ ))
	}

	// folder/file.scd
	prShortPath { |path| ^path.dirname.basename +/+ path.basename }

	// the hint bar after a file was read: what happened to it
	prNoteFile { |file|
		var short = this.prShortPath(file);
		hints.note(case
			{ File.exists(file).not } { "% is gone - what it defined is taken out".format(short) }
			{ sdg.fileFailed(file) } { "% has an error, see the post window - the last version that loaded plays".format(short) }
			{ "loaded % - edit it, save it, and it is read again".format(short) }
		)
	}

	// Open the file a name of the modules tab came from - the name given, or the
	// one selected in the kind's list - as a document (openCode).
	editFile { |kind, name|
		var entry = this.prLoadedEntry(kind);
		var file = if(name.notNil) { sdg.sourceFile(kind, name) } { entry !? { entry[\path] } };
		if(file.isNil) {
			^hints.note(if(name.isNil and: { entry.isNil }) {
				"pick one in the % list first".format(kind)
			} {
				"% was registered from code - there is no file to open".format(name ? entry[\name])
			})
		};
		this.class.openCode(file, sdg);
		hints.note(if(file.basename.beginsWith("example")) {
			"% is the template - changing it can stop Quarks.update; new... makes a copy of your own".format(file.basename)
		} {
			"% is open - save it and it is read again".format(this.prShortPath(file))
		});
	}

	// What make in the new... dialog does: SuperDirtGUI:newFile, then the new name
	// selected in its list and the file open as a document. Answers the path, or nil.
	makeFile { |kind, name, from|
		var file = sdg.newFile(kind, name, from), list;
		if(file.isNil) {
			hints.note("no new % - the post window says why".format(SuperDirtGUI.kindLabel(kind)));
			^nil
		};
		this.refreshLoaded;
		list = loadedLists !? { loadedLists[kind] };
		list !? { list.value = (loadedEntries[kind] ? []).detectIndex { |e| e[\path] == file } };
		this.prLoadedSelect(kind);
		this.class.openCode(file, sdg);
		^file
	}

	// A small window that asks for the name of a new file of a kind and which
	// file to copy: the one selected in the list, else the example. It says as
	// you type whether the name will do (SuperDirtGUI:nameProblem); make, or
	// enter, is makeFile. Answers the dialog's Window.
	newFileDialog { |kind, from|
		var label = SuperDirtGUI.kindLabel(kind);
		var names = sdg.entries(kind).select { |e| e[\name].notNil and: { e[\path].notNil } }.collect(_[\name]);
		var field, fromMenu, info, makeBtn, cancelBtn, check, make, start;
		newDialog !? { |w| if(w.isClosed.not) { w.close } };
		from = from ?? { this.prLoadedEntry(kind) !? { |e| e[\name] } } ?? { sdg.templateName(kind) };
		start = names.indexOf(from !? { SuperDirtGUI.fullName(kind, from) }) ? names.indexOf(sdg.templateName(kind)) ? 0;

		newDialog = Window("new " ++ label, Rect(0, 0, 460, 150).center_(Window.availableBounds.center), false)
			.background_(colBg);
		if(GUI.id == \qt) { newDialog.view.palette = QPalette.dark };
		field = TextField();
		fromMenu = PopUpMenu().items_(names.collect(_.asString)).value_(start);
		info = StaticText().font_(smallFont);
		cancelBtn = Button().fixedWidth_(70).states_([["cancel", colText, Color.grey(0.26)]])
			.action_({ newDialog.close });
		makeBtn = Button().fixedWidth_(70).states_([["make", Color.black, colAccent]]);

		check = {
			var name = field.string, problem, ok;
			problem = case
			{ names.isEmpty } { "nothing in %/ to copy - the example files are the templates".format(kind) }
			{ name.isEmpty } { nil }
			{ sdg.nameProblem(kind, name) };
			info.stringColor = if(problem.isNil) { colDim } { colWarn };
			info.string = problem ?? {
				if(name.isEmpty) {
					"the word tidal switches it on with - lowercase, best with a prefix of your own"
				} {
					this.prNewFileSummary(kind, name, names[fromMenu.value ? 0])
				}
			};
			ok = problem.isNil and: { name.notEmpty };
			// a disabled button keeps the colours it was given: dim them to show it
			makeBtn.states = [["make"] ++ if(ok) { [Color.black, colAccent] } { [colDim, Color.grey(0.26)] }];
			makeBtn.enabled = ok;
			problem
		};
		make = {
			if(check.value.isNil and: { field.string.notEmpty }) {
				if(this.makeFile(kind, field.string, names[fromMenu.value ? 0]).notNil) { newDialog.close }
			}
		};
		field.keyUpAction = { check.value };
		field.action = { make.value };		// enter
		field.keyDownAction = { |v, char, mod, uni, keycode, key|
			if(key == 16r1000000) { newDialog.close; true } { nil }		// escape
		};
		fromMenu.action = { check.value };
		makeBtn.action = { make.value };

		newDialog.layout = VLayout(
			StaticText().stringColor_(colText).string_("a copy of the one picked below, under a new name"),
			HLayout(StaticText().string_("name").stringColor_(colDim).fixedWidth_(36), field),
			HLayout(StaticText().string_("from").stringColor_(colDim).fixedWidth_(36), fromMenu),
			info,
			HLayout(nil, cancelBtn, makeBtn)
		).margins_(10).spacing_(6);
		check.value;
		newDialog.front;
		field.focus(true);
		^newDialog
	}

	// what the dialog will make, and how tidal will call it
	prNewFileSummary { |kind, name, from|
		var base = SuperDirtGUI.baseName(kind, name);
		var call = switch(kind,
			\synths, { "s \"%\"".format(base) },
			\players, { "# pF \"%\" 1".format(base) },
			{ "# pF \"%\" 0.5".format(base) }
		);
		^"%/%.scd, a copy of % - in tidal: %".format(kind, base, from, call)
	}

	// right click on a list: what can be done with the name under it
	prShowLoadedMenu { |kind|
		var entry = this.prLoadedEntry(kind);
		var path = entry !? { entry[\path] };
		var here = path.notNil and: { File.exists(path) };
		var label = SuperDirtGUI.kindLabel(kind);
		this.prLoadedSelect(kind);
		menu !? (_.destroy);
		menu = Menu(
			MenuAction(entry !? { (entry[\name] ?? { path.basename }).asString } ? "nothing selected").enabled_(false),
			MenuAction.separator,
			MenuAction("edit...", { this.editFile(kind) }).enabled_(here),
			MenuAction("new % from it...".format(label), { this.newFileDialog(kind, entry[\name]) })
				.enabled_(here and: { entry[\name].notNil }),
			MenuAction("new %...".format(label), { this.newFileDialog(kind) }),
			MenuAction.separator,
			MenuAction(this.class.revealString, { this.class.revealPath(path) }).enabled_(here),
			MenuAction("copy path", { this.class.copyPathToClipboard(path) }).enabled_(path.notNil)
		);
		menu.front;
	}

	// A content file as a document in the SuperCollider IDE, its ~dirt and ~sdg
	// those of the loader while it is the front document - so a block evaluated
	// in it sees what a load sees - with the IDE's own environment behind them.
	// Outside the IDE, the system's app for the file. codeEditor, when set, is
	// called with the path and the loader instead: an editor of your own.
	*openCode { |path, sdg|
		var document = \Document.asClass, ide = \ScIDE.asClass;
		if(path.isNil) { ^false };
		if(codeEditor.notNil) { codeEditor.value(path, sdg); ^true };
		if(document.notNil and: { ide.notNil } and: { ide.connected }) {
			document.open(path, 0, 0, sdg !? { (dirt: sdg.dirt, sdg: sdg).parent_(topEnvironment) });
			^true
		};
		^this.openPath(path)
	}

	// ------------------------------------------------------ BusManager integration

	refreshBusMenus {
		var names, entries;
		this.refreshRouting;
		if(busManager.isNil) { ^this };
		entries = busManager.entries.select { |e| e.isAudio };
		strips.do { |strip, i|
			var orbit = dirt.orbits[i];
			var own = [orbit.dryBus, orbit.globalEffectBus, orbit.synthBus]
				.collect { |b| b.tryPerform(\index) };
			var items = entries.reject { |e| own.includes(e.index) }.collect(_.name);
			var current = items.detectIndex { |n|
				busManager.index(n) == this.prOutIndex(orbit)
			};
			strip[\outMenuNames] = items;
			strip[\outMenu] !? {
				strip[\outMenu].items = ["bus…"] ++ items.collect(_.asString);
				strip[\outMenu].value = (current !? { current + 1 }) ? 0;
			};
		};
	}

	// ----------------------------------------------------------------- lifecycle

	cleanup {
		rmsFunc !? { rmsFunc.free };
		rmsFunc = nil;
		trackFunc !? { trackFunc.free };
		trackFunc = nil;
		trackViews.do { |views| views[\track].metering = false };
		trackViews = [];
		busViews.do { |views| views[\track].metering = false };
		busViews = [];
		meterRoutine !? { meterRoutine.stop };
		meterRoutine = nil;
		libraryPoll !? { libraryPoll.stop };
		libraryPoll = nil;
		try { sdg.stopMeters };
		busManager !? { busManager.removeDependant(this) };
		sdg.removeDependant(this);
		sdg.midi.removeDependant(this);
		if(midiLearning) { sdg.midi.cancelLearn };
		{ sdg.midi.changed(\learnMode, false) }.defer(0.05);	// once the window is gone
		sdg.tags.removeDependant(this);
		tagDialog !? { |w| if(w.isClosed.not) { w.close } };
		tagDialog = nil;
		menu !? (_.destroy);
		menu = nil;
		newDialog !? { |w| if(w.isClosed.not) { w.close } };
		newDialog = nil;
		sessionDialog !? { |w| if(w.isClosed.not) { w.close } };
		sessionDialog = nil;
		vstWindow !? { |w| if(w.isClosed.not) { w.close } };
		vstWindow = nil;
	}
}
