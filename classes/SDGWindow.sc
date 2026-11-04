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
//              mode of the monitor, and the orbit's output bus - as a number
//              box, and as a dropdown of BusManager names when that quark is
//              installed. A send knob per return track, over the meters.
//   returns    after the orbits, a strip per return track (SDGReturn): its effect,
//              its inserts, sends to the returns, fader, mute, solo, pre/post of
//              what is sent into it, limiter and output. + return adds one, feedback
//              lets returns send to each other. Its name shows its parameters on
//              the effects tab.
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
//              what the selected one does: its description and parameters
//
// Along the top, sessions (SDGSession): a menu of the saved ones, load session
// for the one it shows, save session... to keep the whole mixer under a name.
// Choosing in the menu changes nothing until load session is pressed.
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
	var <selectedReturn;			// a return on show on the effects tab, in the orbit's place
	var returnHolder, returnContent, <returnViews;	// the return strips: one Event of views per return
	var busManager;					// the BusManager class if installed, else nil
	var <strips;					// one Event of views per orbit
	var parentSnapshots;			// pristine defaultParentEvent values, for clearing parameters
	var meterData, <meterStamps, rmsFunc, meterRoutine;
	var <peakHolds;					// per orbit, the highest peak since a click on its readout
	var <tab = 0, tabButtons, pages;
	var effectHeader, trackScroll, trackCanvas, addMenu, addCandidates;
	var <trackViews;				// one Event of views per track of the selected orbit
	var trackMeterData, trackFunc;	// track id -> [stamp, numbers], and what fills it
	var insertListHolder, insertRowsView, insertAddMenu, vstWindow;
	var paramHolder, paramContentView;
	var rightColumn, panelDrag, <panelHandle;	// the right panel, where a drag of its handle started, the handle
	var <tree, searchField, soundInfo, countText, <explorerModel, <selected;
	var trigOrbitMenu, <trigNBox, <trigParams, trigSpecs, editBtn, revealBtn, menu;
	var loadedLists, <loadedEntries, loadedKind, fileText, pathText, newDialog;
	var notesView, <notesString;	// the description at the bottom of the modules tab, and what it says
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
	// (the default follows the pointer round the dial), half a percent a pixel
	*makeKnob { |size = 36, centered = false|
		^Knob().fixedWidth_(size).fixedHeight_(size)
			.mode_(\vert).step_(0.005).centered_(centered)
			.color_([Color.grey(0.32), colAccent, Color.grey(0.16), colText])
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
		returnViews = [];
		font = Font(Font.defaultSansFace, 11);
		smallFont = Font(Font.defaultSansFace, 9);
		boldFont = Font(Font.defaultSansFace, 12, true);

		if(busManager.notNil) {
			SuperDirtGUI.registerBusses(dirt);
			busManager.addDependant(this);
		};
		sdg.addDependant(this);

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
				\returns, { { this.prReturnsChanged }.defer },
				\trackLevel, { { if(this.isOpen) { this.refreshTrackLevels } }.defer },
				\session, { { this.prSessionLoaded(args[0]) }.defer },
				\sessions, { { if(this.isOpen) { this.prRefreshSessions(args[0]) } }.defer }
			);
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
		var width = (visibleStrips * 96) + panelWidth.max(minPanelWidth) + 20;
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
		var recBtn, saveBtn;
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
		sessionMenu = PopUpMenu().fixedWidth_(150);
		sessionLoadBtn = Button().fixedWidth_(86);
		sessionLoadBtn.action = { this.loadSelectedSession };
		saveBtn = Button().fixedWidth_(86).states_([["save session", colText, colPanel]]);
		saveBtn.action = { this.saveSessionDialog };
		hints.attach(sessionMenu, "the saved sessions - choosing one changes nothing until you press load session");
		hints.attach(sessionLoadBtn, "replace the mixer with the session in the menu: faders, tracks, parameters, inserts, VST states");
		hints.attach(saveBtn, "keep the whole mixer under a name, every fader, parameter, insert and VST state");
		this.prRefreshSessions;
		^HLayout(title, info, nil, sessionMenu, sessionLoadBtn, saveBtn, busBtn, recBtn)
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
			sortKey: sortKey, sortAscending: sortAscending, columns: explorerColumns.copy, panelWidth: this.panelWidth)
	}

	applySessionState { |state|
		state[\tab] !? { |v| this.selectTab(v) };
		state[\orbit] !? { |v| this.selectOrbit(v) };
		state[\playOrbit] !? { |v| trigOrbitMenu.value = v.clip(0, dirt.orbits.size - 1) };
		state[\columns] !? { |v| this.explorerColumns = v };
		state[\panelWidth] !? { |v| this.setPanelWidth(v) };
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

	makeStripArea {
		// vertical as well: a strip with many inserts grows taller than the window
		var scroll = ScrollView().autohidesScrollers_(true).hasBorder_(false).background_(colBg);
		var canvas = View().background_(colBg);
		strips = dirt.orbits.collect { |orbit, i| this.makeStrip(orbit, i) };
		returnHolder = View().background_(colBg);
		returnHolder.layout = HLayout().margins_(0).spacing_(0);
		canvas.layout = HLayout(*(strips.collect(_[\view]) ++ [returnHolder, nil]))
			.margins_(0).spacing_(5);
		scroll.canvas = canvas;
		// one strip at least: the right panel may take the rest, and the strips scroll
		scroll.minWidth_(96).minHeight_(400);
		strips.size.do { |i| this.rebuildStripInserts(i) };
		this.prReturnsChanged;
		^scroll
	}

	makeStrip { |orbit, i|
		var ampSpec = paramSpecs[\orbitAmp];
		var view = View().fixedWidth_(90).background_(colPanel);
		var label = Button().fixedHeight_(22).states_([
			["orbit " ++ i, colText, Color.grey(0.26)],
			["orbit " ++ i, Color.black, colAccent]
		]);
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
		var outBox = NumberBox().fixedHeight_(20).clipLo_(0).step_(1).decimals_(0);
		var outLabel = StaticText().string_("out").stringColor_(colDim).fixedWidth_(24);
		var outMenu, head;

		label.action = { this.selectOrbit(i) };
		addInsert.action = { this.prShowAddInsertMenu(i) };
		peak.action = { peakHolds[i] = 0.0; this.prShowPeak(i) };

		// the fader, mute and solo show the SuperDirtGUI's state (refreshMixer)
		// and hand every change back to it
		fader.action = { |sl| sdg.setFader(i, ampSpec.map(sl.value)) };
		mute.action = { |b| sdg.setMute(i, b.value == 1) };
		solo.action = { |b| sdg.setSolo(i, b.value == 1) };

		lim.value = (orbit.get(\limitertype) ? 1).asInteger.clip(0, 2);
		lim.action = { |m| sdg.setOrbitParam(i, \limitertype, m.value) };

		outBox.value = this.prOutIndex(orbit);
		outBox.action = { |b|
			this.prSetOutBus(orbit, b.value.asInteger.max(0));
			this.refreshBusMenus;
		};

		if(busManager.notNil) {
			outMenu = PopUpMenu().fixedHeight_(20).items_(["bus…"]);
			outMenu.action = { |m|
				var names = strips[i][\outMenuNames] ? [];
				var index;
				if(m.value > 0 and: { names.size >= m.value }) {
					index = busManager.index(names[m.value - 1]);
					index !? {
						this.prSetOutBus(orbit, index);
						outBox.value = index;
					};
				};
			};
		};

		hints.attach(label, "orbit % - where d% plays; selects it for the effects tab, and the explorer plays into it".format(i, i + 1));
		hints.attach(addInsert, "add an insert to orbit % - an eq, a compressor or an insight, after the ones above, before the fader".format(i));
		hints.attach(meters, "peak and rms of each channel leaving the orbit, after its inserts and fader - a tick every 5 dB");
		hints.attach(peak, "the highest peak since you last clicked here, in dB - red once it reached 0");
		hints.attach([fader, faderText], "the orbit's output level, in dB under it - the orbitAmp of its fader, before the monitor");
		hints.attach(mute, "silence this orbit");
		hints.attach(solo, "silence every other orbit");
		hints.attach(lim, "how the monitor guards the output: nothing, a limiter, or a hard clip");
		hints.attach([outLabel, outBox], "the hardware bus this orbit's monitor writes to");
		outMenu !? { hints.attach(outMenu, "the same, by BusManager name") };

		head = View().layout_(VLayout(label, insertHolder, addInsert, nil).margins_(0).spacing_(4));
		view.layout = VLayout(
			head,
			sendHolder,
			[HLayout(*(meters ++ [fader])).spacing_(2), stretch: 1],
			View().fixedHeight_(this.prFootHeight).layout_(VLayout(
				faderText,
				peak,
				HLayout(mute, solo).spacing_(2),
				lim,
				HLayout(outLabel, outBox).spacing_(2),
				outMenu,   // nil is fine here: layouts skip it
				nil
			).margins_(0).spacing_(4))
		).margins_(4).spacing_(4);

		^(view: view, head: head, label: label, insertHolder: insertHolder, addInsert: addInsert, sendHolder: sendHolder,
			meters: meters, peak: peak, peakShown: "-inf", fader: fader, faderText: faderText,
			mute: mute, solo: solo, lim: lim, outBox: outBox, outMenu: outMenu)
	}

	// ---------------------------------------------------------------- returns

	// a send knob: nothing to 0 dB, a dB readout in the hint bar as it moves. Answers
	// its Event; what it holds shows through refreshMixer
	prSendKnob { |ret, value, enabled, action, hint|
		var spec = this.class.sendSpec;
		var knob = this.class.makeKnob(24).value_(spec.unmap(value));
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
	// is fixed, 36 a row, so an orbit's and a return's meters start level
	prSendGrid { |cells|
		var rows = (cells.size / 3).ceil.asInteger;
		^View().fixedHeight_((rows * 38 - 2).max(0)).layout_(VLayout(*(cells.collect(_[\view]).clump(3).collect { |row|
			HLayout(*(row ++ [nil])).margins_(0).spacing_(1)
		})).margins_(0).spacing_(2))
	}

	rebuildStripSends { |i|
		var strip = strips !? { strips[i] }, cells;
		if(strip.isNil) { ^this };
		strip[\sendView] !? { |v| v.remove };
		cells = sdg.returns.collect { |ret|
			this.prSendKnob(ret, ret.orbitLevel(i), true, { |amp| sdg.setSend(i, ret, amp) },
				"how much of orbit % goes to return %, %".format(i, ret.letter,
					if(ret.preFader) { "before its fader" } { "after its fader" }))
		};
		strip[\sendKnobs] = cells;
		strip[\sendView] = this.prSendGrid(cells);
		strip[\sendHolder].layout.add(strip[\sendView]);
	}

	// A return's strip: as an orbit's, with the effect a track has
	makeReturnStrip { |ret, preRows, postRows|
		var ampSpec = paramSpecs[\orbitAmp];
		var view = View().fixedWidth_(92).background_(Color(0.20, 0.24, 0.27));
		var label = Button().fixedHeight_(22).states_([
			[ret.shortName, colText, Color(0.27, 0.33, 0.38)],
			[ret.shortName, Color.black, colSel]
		]).value_((ret === selectedReturn).binaryValue);
		var rm = Button().fixedWidth_(16).fixedHeight_(22).font_(smallFont).states_([["✕", colWarn, Color.grey(0.26)]]);
		var names = this.class.effectCandidates(sdg, ret);
		var loadMenu = PopUpMenu().font_(smallFont).fixedHeight_(18)
			.items_(["load effect…"] ++ names.collect(_.asString) ++ if(ret.fx.notNil) { ["no effect"] } { [] });
		var onBtn = Button().fixedHeight_(18).font_(smallFont).states_([
			["off", colDim, Color.grey(0.26)],
			["on", Color.black, colOn]
		]).value_(ret.effectOn.binaryValue);
		var addPre = Button().fixedHeight_(16).font_(smallFont).states_([["+ pre", colDim, Color.grey(0.21)]]);
		var addPost = Button().fixedHeight_(16).font_(smallFont).states_([["+ post", colDim, Color.grey(0.21)]]);
		var mark = StaticText().fixedHeight_(14).align_(\center).font_(smallFont)
			.string_("▼ " ++ ret.effectLabel ++ " ▼").stringColor_(if(ret.fx.notNil) { colAccent } { colDim });
		var sends = ret.targets.collect { |to|
			this.prSendKnob(to, ret.returnLevel(to), sdg.returnFeedback, { |amp| sdg.setReturnSend(ret, to, amp) },
				"how much of return % goes to return %, a block later - needs feedback on".format(ret.letter, to.letter))
		};
		var meters = { LevelIndicator().fixedWidth_(11).minHeight_(150)
			.drawsPeak_(true).warning_(0.85).critical_(0.95)
			.numTicks_(13).numMajorTicks_(3) } ! dirt.numChannels;
		var fader = Slider().fixedWidth_(20).minHeight_(150);
		var faderText = StaticText().fixedHeight_(14).align_(\right).stringColor_(colText).font_(smallFont);
		var mute = Button().fixedHeight_(20).states_([["M", colDim, Color.grey(0.26)], ["M", Color.black, colWarn]]);
		var solo = Button().fixedHeight_(20).states_([["S", colDim, Color.grey(0.26)], ["S", Color.black, colAccent]]);
		var prePost = Button().fixedHeight_(16).font_(smallFont).states_([
			["post fader", colDim, Color.grey(0.26)],
			["pre fader", Color.black, colSel]
		]);
		var lim = PopUpMenu().fixedHeight_(20).items_(["raw", "limit", "clip"]).value_(ret.limitertype);
		var outBox = NumberBox().fixedHeight_(20).clipLo_(0).step_(1).decimals_(0).value_(ret.outBus);
		var outLabel = StaticText().string_("out").stringColor_(colDim).fixedWidth_(24);
		var head;

		label.action = { this.selectReturn(ret) };
		// these go with the strip they are in, so not from inside their own action
		rm.action = { { sdg.removeReturn(ret) }.defer };
		loadMenu.action = { |m|
			case
			{ m.value == 0 } { }
			{ m.value <= names.size } { { this.loadReturnEffect(ret, names[m.value - 1]) }.defer }
			{ { this.loadReturnEffect(ret, nil) }.defer };
		};
		onBtn.action = { |b| { ret.effectOn = b.value == 1 }.defer };
		addPre.action = { this.prShowAddInsertMenu(nil, ret, \pre) };
		addPost.action = { this.prShowAddInsertMenu(nil, ret, \post) };
		fader.action = { |sl| ret.level = ampSpec.map(sl.value) };
		mute.action = { |b| ret.mute = b.value == 1 };
		solo.action = { |b| ret.solo = b.value == 1 };
		prePost.action = { |b| ret.preFader = b.value == 1 };
		lim.action = { |m| ret.limitertype = m.value };
		outBox.action = { |b| ret.outBus = b.value.asInteger.max(0) };

		hints.attach(label, "% - its effect's parameters on the effects tab".format(ret.shortName));
		hints.attach(rm, "take % out, with its effect, inserts and every send to it".format(ret.shortName));
		hints.attach(loadMenu, "the global effect on this return; it comes out fully wet, and with none the line just passes");
		hints.attach(onBtn, "off bypasses the effect: the line passes as if it were not there");
		hints.attach(addPre, "an insert before the effect - an eq, a compressor, an insight or a VST plugin");
		hints.attach(addPost, "an insert after the effect, before the sends and the fader");
		hints.attach(meters, "what the return plays, after its fader");
		hints.attach([fader, faderText], "the level of what comes back");
		hints.attach(mute, "silence this return, and what it sends to other returns");
		hints.attach(solo, "hear only this - the orbits' direct sound stops, what they send here goes on");
		hints.attach(prePost, "whether what is sent into this return is taken before or after the source's fader");
		hints.attach(lim, "how the output is guarded: nothing, a limiter, or a hard clip - a feedback loop can get loud");
		hints.attach([outLabel, outBox], "the bus this return writes to");

		head = View().layout_(VLayout(
			HLayout(label, rm).spacing_(1),
			loadMenu,
			onBtn,
			this.prInsertColumn(ret.pre, preRows),
			addPre,
			mark,
			this.prInsertColumn(ret.post, postRows),
			addPost,
			nil
		).margins_(0).spacing_(4));
		view.layout = VLayout(
			head,
			this.prSendGrid(sends),
			[HLayout(*(meters ++ [fader])).spacing_(2), stretch: 1],
			// row for row as an orbit's, pre/post where it has its peak
			View().fixedHeight_(this.prFootHeight).layout_(VLayout(
				faderText,
				prePost,
				HLayout(mute, solo).spacing_(2),
				lim,
				HLayout(outLabel, outBox).spacing_(2),
				nil
			).margins_(0).spacing_(4))
		).margins_(4).spacing_(4);
		^(view: view, head: head, track: ret, label: label, sendKnobs: sends, meters: meters, fader: fader,
			faderText: faderText, mute: mute, solo: solo, prePost: prePost, loadMenu: loadMenu, candidates: names)
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
		^View().layout_(VLayout(add, feedback, nil).margins_(0).spacing_(4))
	}

	// the return strips made again, and the send knobs of every orbit
	prReturnsChanged {
		var list;
		if(this.isOpen.not and: { window.notNil }) { ^this };
		list = sdg.returns;
		if(selectedReturn.notNil and: { list.includes(selectedReturn).not }) { selectedReturn = nil };
		returnViews.do { |views| if(list.includes(views[\track]).not) { views[\track].metering = false } };
		returnViews = list.collect { |ret|
			this.makeReturnStrip(ret, this.prMaxRows(list.collect(_.pre)), this.prMaxRows(list.collect(_.post)))
		};
		returnViews.do { |views| views[\track].metering = true };
		returnContent !? { returnContent.remove };
		returnContent = View().background_(colBg);
		returnContent.layout = HLayout(*(returnViews.collect(_[\view]) ++ [this.prMakeReturnButtons]))
			.margins_(0).spacing_(5);
		returnHolder.layout.add(returnContent);
		strips.size.do { |i| this.rebuildStripSends(i) };
		this.prAlignStrips;
		this.refreshMixer;
		if(paramHolder.notNil) { this.rebuildEffectSection };
	}

	// a return's effect, parameters and inserts on the effects tab, in the place of the
	// selected orbit's. A click on an orbit's name brings that back
	selectReturn { |ret|
		selectedReturn = ret;
		strips.do { |strip| strip[\label].value = 0 };
		returnViews.do { |views| views[\label].value = (views[\track] === ret).binaryValue };
		this.selectTab(0);
		this.rebuildEffectSection;
	}

	// what the effects tab has on show: the return, or the orbit
	prHost { ^selectedReturn ?? { this.selectedOrbit } }

	// an effect on a return, and its window state follows. Answers the new effect, or nil
	loadReturnEffect { |ret, name|
		var fx = sdg.setReturnEffect(ret, name);
		if(fx.isNil and: { name.notNil }) { hints.note("could not load % - the post window says why".format(name)) };
		^fx
	}

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
	// strip of a row leaves room for the longest list. Orbits and returns share one row,
	// so the part above the sends is one height for both. Track strips: rebuildTrackStrips
	prAlignStrips {
		var rows = this.prColumnHeight(this.prMaxRows(strips.size.collect { |i| sdg.insertsOf(i) }));
		var height = 22 + rows + 18 + (2 * 4);	// name, inserts, + insert
		if(returnViews.notEmpty) {
			// name, load, on, pre, + pre, the effect's name, post, + post
			height = max(height, 22 + 18 + 18 + 16 + 14 + 16 + (7 * 4)
				+ this.prColumnHeight(this.prMaxRows(sdg.returns.collect(_.pre)))
				+ this.prColumnHeight(this.prMaxRows(sdg.returns.collect(_.post))));
		};
		strips.do { |strip|
			strip[\insertHolder].fixedHeight = rows;
			strip[\head].fixedHeight = height;
		};
		returnViews.do { |views| views[\head].fixedHeight = height };
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
			if(insert.isOnReturn) {
				"% %'s effect".format(if(insert.place == \pre) { "before" } { "after" }, insert.where)
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
			MenuAction("remove", { sdg.removeInsert(insert) })
		]));
		menu.front;
	}

	// track and place: on a track, before or after its effect
	prShowAddInsertMenu { |i, track, place|
		menu !? (_.destroy);
		menu = Menu(*([
			MenuAction(case
				{ track.isKindOf(SDGReturn) } { "add an insert % the effect, %".format(if(place == \pre) { "before" } { "after" }, track.where) }
				{ track.notNil } { "add an insert %, orbit %".format(if(place == \pre) { "before " } { "after " } ++ track.shortName, i) }
				{ "add an insert to orbit %".format(i) }).enabled_(false),
			MenuAction.separator
		] ++ SDGInsert.kindNames.collect { |kind|
			MenuAction(this.class.insertKindName(kind), { this.addInsert(i, kind, track, place) })
				.enabled_(SDGInsert.available(kind))
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
		// nil: an insert of a return, whose strip shows it
		if(i.isNil) { this.prReturnsChanged } { this.rebuildStripInserts(i) };
		if(i.isNil or: { i == selectedOrbitIndex }) {
			this.rebuildInsertRows;
			this.rebuildTrackStrips;
		};
	}

	prTracksChanged { |i|
		if(this.isOpen.not) { ^this };
		if(i.isNil or: { i == selectedOrbitIndex }) { this.rebuildEffectSection };
	}

	prOutIndex { |orbit|
		^(orbit.outBus.tryPerform(\index) ? orbit.outBus ? 0).asInteger
	}

	// DirtOrbit:outBus_ starts the orbit's effects again: SuperDirtGUI:setOutBus
	// releases them the way that frees sleeping ones, and puts the inserts back
	prSetOutBus { |orbit, index|
		sdg.setOutBus(orbit.orbitIndex, index);
	}

	selectOrbit { |i|
		selectedOrbitIndex = i.clip(0, dirt.orbits.size - 1);
		selectedReturn = nil;
		returnViews.do { |views| views[\label].value = 0 };
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
			mutes !? { mutes[i] !? { strip[\mute].value = mutes[i].binaryValue } };
			solos !? { solos[i] !? { strip[\solo].value = solos[i].binaryValue } };
			(strip[\sendKnobs] ? []).do { |cell|
				cell[\knob].value = this.class.sendSpec.unmap(cell[\ret].orbitLevel(i))
			};
		};
		returnViews.do { |views|
			var ret = views[\track];
			views[\fader].value = ampSpec.unmap(ret.level);
			views[\faderText].string = this.class.faderString(ret.level);
			views[\mute].value = ret.muted.binaryValue;
			views[\solo].value = ret.soloed.binaryValue;
			views[\prePost].value = ret.preFader.binaryValue;
			views[\sendKnobs].do { |cell|
				cell[\knob].value = this.class.sendSpec.unmap(ret.returnLevel(cell[\ret]))
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

	startMeters {
		// 20 replies a second, peaks held 1.5 s - see SuperDirtGUI:startMeters
		sdg.startMeters(20, 1.5);
		rmsFunc = OSCFunc({ |msg|
			var i = (msg[2] ? -1).asInteger;
			if(i >= 0 and: { i < meterData.size }) {
				meterData[i] = msg.copyToEnd(3);
				meterStamps[i] = Main.elapsedTime;
			};
		}, '/rms', dirt.server.addr);
		trackFunc = OSCFunc({ |msg|
			trackMeterData[msg[2].asInteger] = [Main.elapsedTime, msg.copyToEnd(3)];
		}, '/sdgtrack', dirt.server.addr);
		meterRoutine = Routine {
			loop {
				this.updateMeterViews;
				0.05.wait;
			}
		}.play(AppClock);
	}

	updateMeterViews {
		var now = Main.elapsedTime;
		strips.do { |strip, i|
			var vals = meterData[i];
			// dirt_rms pauses itself when the orbit is silent, so a strip whose
			// messages stopped is showing old numbers - drop it to zero instead
			var stale = (now - meterStamps[i]) > 0.5;
			strip[\meters].do { |m, ch|
				var peak = if(stale) { 0.0 } { (vals[ch * 2] ? 0.0).asFloat };
				var rms = if(stale) { 0.0 } { (vals[(ch * 2) + 1] ? 0.0).asFloat };
				m.value = if(rms <= 0.0) { 0 } { rms.ampdb.linlin(-60, 0, 0, 1) };
				m.peakLevel = if(peak <= 0.0) { 0 } { peak.ampdb.linlin(-60, 0, 0, 1) };
				if(peak > peakHolds[i]) { peakHolds[i] = peak };
			};
			this.prShowPeak(i);
		};
		(trackViews ++ returnViews).do { |views|
			var data = trackMeterData[views[\track].id];
			var stale = data.isNil or: { (now - data[0]) > 0.5 };
			views[\meters].do { |m, ch|
				var peak = if(stale) { 0.0 } { (data[1][ch * 2] ? 0.0).asFloat };
				var rms = if(stale) { 0.0 } { (data[1][(ch * 2) + 1] ? 0.0).asFloat };
				m.value = if(rms <= 0.0) { 0 } { rms.ampdb.linlin(-60, 0, 0, 1) };
				m.peakLevel = if(peak <= 0.0) { 0 } { peak.ampdb.linlin(-60, 0, 0, 1) };
			}
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
		var addBtn, refreshBtn, headerRow;
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

		addMenu = PopUpMenu();
		addBtn = Button().fixedWidth_(50).states_([["add", colText, Color.grey(0.26)]]);
		addBtn.action = {
			if(addCandidates.notNil and: { addCandidates.notEmpty }) {
				if(selectedReturn.notNil) {
					this.loadReturnEffect(selectedReturn, addCandidates[addMenu.value ? 0])
				} {
					this.addEffect(this.selectedOrbit, addCandidates[addMenu.value ? 0])
				}
			};
		};
		hints.attach(addMenu, "every SynthDef that follows the global effect convention and is not in this orbit yet");
		hints.attach(addBtn, "the chosen effect on a new track, after the others");

		paramHolder = View();
		paramHolder.layout = VLayout().margins_(0).spacing_(0);

		section.layout = VLayout(
			headerRow,
			trackScroll,
			HLayout(addMenu, addBtn),
			paramHolder,
			this.makeInsertSection,
			[View(), stretch: 1]	// what is left stays under them, not between them
		).margins_(6).spacing_(6);
		^section
	}

	// the selected orbit's inserts, under its global effects: a row each, in
	// the order the signal takes, and a menu to add one
	makeInsertSection {
		var addBtn, available = SDGInsert.kindNames;
		insertListHolder = View();
		insertListHolder.layout = VLayout().margins_(0).spacing_(0);
		insertAddMenu = PopUpMenu().items_(available.collect { |kind| this.class.insertKindName(kind) });
		addBtn = Button().fixedWidth_(50).states_([["add", colText, Color.grey(0.26)]]);
		addBtn.action = {
			var kind = available[insertAddMenu.value ? 0];
			if(SDGInsert.available(kind)) {
				if(selectedReturn.notNil) {
					this.addInsert(nil, kind, selectedReturn, \post)
				} {
					this.addInsert(selectedOrbitIndex, kind)
				}
			} {
				hints.note("% is not installed - see its repository's install.scd".format(this.class.insertKindName(kind)))
			}
		};
		hints.attach(insertAddMenu, "the kinds of insert - those whose extension is installed can be added");
		hints.attach(addBtn, "put the chosen insert on the orbit, after its other inserts, and open its window");
		^View().layout_(VLayout(
			insertListHolder,
			HLayout(insertAddMenu, addBtn)
		).margins_(0).spacing_(6))
	}

	rebuildInsertRows {
		var list = if(selectedReturn.notNil) { selectedReturn.inserts } { sdg.insertsOf(selectedOrbitIndex) };
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
		effectHeader.string = if(selectedReturn.notNil) {
			"% - %".format(selectedReturn.shortName, selectedReturn.effectLabel)
		} {
			"orbit % - effect tracks".format(selectedOrbitIndex)
		};
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
		var list = if(selectedReturn.notNil) { [] } { sdg.tracksOf(selectedOrbitIndex) };
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
		// the first entry names the menu, so choosing any effect is always a change
		var names = this.class.effectCandidates(sdg, track.orbit);
		var loadMenu = PopUpMenu().font_(smallFont).fixedHeight_(18)
			.items_(["load effect…"] ++ names.collect(_.asString));
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
		loadMenu.action = { |m|
			var name = if(m.value > 0) { names[m.value - 1] };
			// the menu goes with the strip it is in, so not from inside its own action
			name !? { { this.loadTrackEffect(track, name) }.defer };
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

		hints.attach(label, "% - its parameters under the strips".format(track.name));
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
		addCandidates = this.class.effectCandidates(sdg, this.prHost);
		addMenu.items = if(addCandidates.isEmpty) {
			["(no other effect defs)"]
		} {
			addCandidates.collect(_.asString)
		};
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
		var fx = if(selectedReturn.notNil) { selectedReturn.fx } {
			orbit.globalEffects.detect { |x| x.name == selectedEffectName }
		};
		var cells;
		paramContentView !? { paramContentView.remove };
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
		var knob = this.class.makeKnob;
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

		^this.prKnobCell(param.asString, knob, box, if(orbit.isKindOf(SDGReturn)) {
			"% of % - no event comes to a return, this is its value".format(param, fx.name)
		} {
			"% of % - set here and carried by every event that follows".format(param, fx.name)
		}, clear)
	}

	specFor { |fx, param|
		^paramSpecs[param] ?? { sdg.effectParamSpec(fx.name, param) } ?? {
			var default = this.descDefault(fx, param) ? 0;
			ControlSpec(0, max(default * 2, 1), \lin, 0, default)
		}
	}

	descDefault { |fx, param|
		var desc = SynthDescLib.at((fx.name.asString ++ dirt.numChannels).asSymbol);
		var ctl = desc !? { desc.controlDict[param] };
		var default = ctl !? { ctl.defaultValue };
		^if(default.isNumber) { default } { nil }
	}

	currentValue { |orbit, fx, param|
		var v = orbit.defaultParentEvent[param];
		if(v.isNumber.not) { v = fx.state[param] };
		if(v.isNumber.not) { v = this.descDefault(fx, param) };
		if(v.isNumber.not) { v = this.specFor(fx, param).default };
		^v
	}

	setEffectParam { |orbit, fx, param, value|
		orbit.set(param, value);
		fx.set(orbit.defaultParentEvent);
		// its track's send and return may be asleep: an effect started by a knob is heard too
		sdg.wakeOrbit(orbit);
	}

	// put the parameter back to what it was before the GUI touched it. Keys the
	// defaultParentEvent defines by itself (cps, lock, dry ...) are restored to
	// their startup value, never removed - other code relies on them existing.
	// Answers the value the widgets should show afterwards.
	clearEffectParam { |orbit, fx, param|
		var snapshot = if(orbit.isKindOf(SDGReturn).not) { parentSnapshots[selectedOrbitIndex][param] };
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
		var playBtn = this.prButton("play", 50, { this.playSelected },
			"play the selected sample into the orbit chosen below, through the \\dirt event type (enter)");
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
		var knob = this.class.makeKnob(36, key == \pan);
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

	// posted whatever happens - the clipboard is best effort. On Windows through
	// PowerShell's Set-Clipboard, the path in single quotes, where only a ' needs
	// doubling
	*copyPathToClipboard { |path|
		var cmd;
		if(path.isNil) { ^false };
		path.postln;
		cmd = this.prClipboardCommand(path);
		cmd !? { cmd.unixCmd };
		^cmd.notNil
	}

	// the shell command that puts path on the clipboard, for this platform or
	// the one named; nil where there is none
	*prClipboardCommand { |path, platform|
		^switch(platform ? thisProcess.platform.name,
			\osx, { "printf %s" + path.shellQuote + "| pbcopy" },
			\linux, { "printf %s" + path.shellQuote + "| xclip -selection clipboard" },
			\windows, {
				"powershell -NoProfile -NonInteractive -Command \"Set-Clipboard -Value '"
					++ this.prWindowsPath(path).replace("'", "''") ++ "'\""
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
			.background_(Color.grey(0.16)).minHeight_(170),
			"what the selected one does, and its parameters - the comments at the top of its file");

		section.layout = VLayout(
			header,
			[HLayout(*lists).spacing_(4), stretch: 1],
			fileText,
			HLayout(reloadBtn, freeBtn, bankBtn, nil).spacing_(4),
			pathText,
			[notesView, stretch: 2]
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
		strips.do { |strip, i|
			strip[\outBox].value = this.prOutIndex(dirt.orbits[i]);
		};
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
		returnViews.do { |views| views[\track].metering = false };
		returnViews = [];
		meterRoutine !? { meterRoutine.stop };
		meterRoutine = nil;
		libraryPoll !? { libraryPoll.stop };
		libraryPoll = nil;
		try { sdg.stopMeters };
		busManager !? { busManager.removeDependant(this) };
		sdg.removeDependant(this);
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
