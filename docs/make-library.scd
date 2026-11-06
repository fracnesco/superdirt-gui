// docs/make-library.scd - writes docs/library.md from the content files.
//
// Boots a server of its own on port 57131 (or $SDG_LIBRARY_PORT, when something
// else holds that one: Dante Virtual Soundcard does), makes a SuperDirt with no
// samples and no orbits, reads synths/, modules/, players/ and effects/ the way SuperDirtGUI
// always does, and writes down what each file says about itself (its header) and
// what its SynthDef turned out to be (its SynthDesc). Nothing plays. Run it again
// after changing a content file's header or controls:
//
//   /Applications/SuperCollider.app/Contents/MacOS/sclang "docs/make-library.scd" > library.log 2>&1
//
// The log ends with what does not match: a listed parameter with no control (right
// for a file's switch, or a word its prepare function reads) and a control no
// header lists (right for internal ones). A file whose "// requires:" names a class
// this sclang does not have is not built; its defaults are read from its text.
(
var out = [];
var problems = [];
var s = Server(\sdglibrary, NetAddr("127.0.0.1", ("SDG_LIBRARY_PORT".getenv ? "57131").asInteger));
var numChannels = 2;
var add = { |line| out = out.add(line) };

// the headers are lowercase prose: every sentence gets its capital, but for a word of the file's own
var capital = { |text|
	var upper = { |str| if(str.isEmpty or: { str.beginsWith("sdg") }) { str } { str.first.toUpper.asString ++ str.drop(1) } };
	text.split($.).collect { |part, i|
		case
		{ i == 0 } { upper.(part) }
		{ part.beginsWith(" ") } { " " ++ upper.(part.drop(1)) }
		{ part }
	}.join($.)
};

// the comment lines above the code, as paragraphs of lines without their //
var paragraphs = { |file|
	var lines = File.readAllString(file).replace("\r", "").split($\n);
	var head = lines.keep(lines.detectIndex { |line| line.beginsWith("//").not } ? lines.size);
	var result = [], current = [];
	head.do { |line|
		if(line.stripWhiteSpace == "//") {
			if(current.notEmpty) { result = result.add(current); current = [] }
		} {
			current = current.add(line.drop(2).stripWhiteSpace)
		}
	};
	if(current.notEmpty) { result = result.add(current) };
	result
};

var isExampleLine = { |line| line.beginsWith("d1 $") or: { line.beginsWith("d2 $") } };

// what the file says, sorted out: prose, tidal lines, the example file it names, what it requires
var readHeader = { |file|
	var prose = [], tidal = [], played, requires;
	paragraphs.(file).do { |para, i|
		var text = para.join(" ");
		case
		{ text.beginsWith("parameters:") } { }
		{ text.beginsWith("requires:") } { requires = text.drop(9).stripWhiteSpace }
		{ para.any(isExampleLine) } { tidal = tidal ++ para.select(isExampleLine) }
		{ text.beginsWith("not in any orbit") or: { text.beginsWith("new... in the window") } } { }
		{ text.find("examples/tidal/").notNil } {
			played = text.findRegexp("examples/tidal/(lessons/)?[-a-z0-9]+\\.tidal")[0][1]
		}
		{
			if(i == 0) { text = text.drop((text.find(".scd - ") ? -7) + 7) };
			prose = prose.add(capital.(text))
		}
	};
	(prose: prose, tidal: tidal, played: played, requires: requires)
};

var defaultString = { |value|
	case
	{ value.isNil } { "-" }
	{ value.isNumber } { value.round(0.0001).asString }
	{ value.asString }
};

var ioString = { |list|
	list.collect { |io|
		var from = io.startingChannel;
		"% % on `%`".format(io.rate, io.numberOfChannels, from)
	}.join(", ")
};

var section = { |kind, title, intro, nameOf, descOf|
	var files = (SuperDirtGUI.current.dirFor(kind) +/+ "*.scd").standardizePath.pathMatch.sort;
	var entries = SuperDirtGUI.current.entries(kind);
	var items = files.collect { |file|
		var entry = entries.detect { |e| e[\path].notNil and: { SuperDirtGUI.samePath(e[\path], file) } };
		(file: file, name: entry !? { |e| e[\name] }, label: entry !? { |e| e[\name] !? { nameOf.(e[\name]) } } ?? { file.basename.splitext[0] })
	};
	add.("## " ++ title);
	add.("");
	add.(intro);
	add.("");
	add.(items.collect { |item| "[%](#%)".format(item[\label], item[\label].toLower) }.join(" · "));
	add.("");
	items.do { |item|
		var file = item[\file], label = item[\label], rel = "%/%".format(kind, file.basename);
		var header = readHeader.(file), notes = SuperDirtGUI.fileNotes(file);
		var desc = item[\name] !? { |name| SynthDescLib.global.at(descOf.(name).asSymbol) };
		var text = File.readAllString(file);
		var controlNames = desc !? { |d| d.controls.collect { |c| c.name.asSymbol } } ? [];
		add.("### " ++ label);
		add.("");
		add.("[%](../%)%".format(rel, rel, header[\played] !? { |p| " · played in [%](../%)".format(p.basename, p) } ? ""));
		add.("");
		header[\prose].do { |para| add.(para); add.("") };
		header[\requires] !? { |r| add.("**Needs:** %.".format(r)); add.("") };
		if(desc.notNil) {
			if(desc.inputs.notEmpty) { add.("**In:** %.".format(ioString.(desc.inputs))); add.("") };
			add.("**Out:** %.".format(ioString.(desc.outputs)));
			add.("");
		} {
			add.("**Not built when this page was generated:** the class it needs was not installed, so the defaults below are read from the file's text.");
			add.("");
		};
		if(notes[\params].notEmpty) {
			add.("| parameter | range | default | what it does |");
			add.("|-----------|-------|---------|--------------|");
			notes[\params].do { |p|
				var ctl = desc !? { |d| d.controlDict[p[\name]] };
				var value = if(ctl.notNil) { ctl.defaultValue } {
					if(desc.isNil) {
						text.findRegexp("[ ,|]" ++ p[\name].asString ++ "\\s*=\\s*(-?[0-9.]+)") !? { |m|
							if(m.size > 1) { m[1][1].asFloat }
						}
					}
				};
				add.("| `%` | % | % | % |".format(p[\name], p[\range], defaultString.(value), p[\text]));
				if(desc.notNil and: { controlNames.includes(p[\name]).not }) {
					problems = problems.add("% lists %, which is no control".format(rel, p[\name]))
				};
			};
			add.("");
		};
		if(desc.notNil) {
			controlNames.reject { |name|
				notes[\params].any { |p| p[\name] == name }
				or: { #[\out, \bufnum, \sustain, \begin, \end, \speed, \endSpeed, \freq, \pan, \span, \accelerate,
					\dryBus, \effectBus, \gate, \dirt_resumed, \resumed, '?'].includes(name) }
			}.do { |name| problems = problems.add("% has control %, which it does not list".format(rel, name)) };
		};
		if(header[\tidal].notEmpty) {
			add.("```haskell");
			header[\tidal].do { |line| add.(line) };
			add.("```");
			add.("");
		};
	};
};

// the SynthDefs the mixer is made of
var plumbing = { |names|
	add.("## The mixer's own SynthDefs");
	add.("");
	add.("Not content: no file makes these and no Tidal word plays them. [the-mixer.md](the-mixer.md) says where each one sits. SuperDirt's own `dirt_rms` and `dirt_monitor` end every orbit's chain after them, and an insert's `sdgins_<kind>` is built when the first of that kind is added.");
	add.("");
	add.("| SynthDef | in | out | controls |");
	add.("|----------|----|-----|----------|");
	names.do { |name|
		var desc = SynthDescLib.global.at((name.asString ++ numChannels).asSymbol);
		desc !? {
			add.("| `%%` | % | % | % |".format(name, numChannels, ioString.(desc.inputs), ioString.(desc.outputs),
				desc.controls.collect { |c| "`%`".format(c.name) }.join(" ")))
		}
	};
	add.("");
};

{ 150.wait; "WATCHDOG".postln; 1.exit }.fork(SystemClock);

s.options.numInputBusChannels = 0;
s.options.numWireBufs = 256;
s.options.memSize = 8192 * 32;
s.options.numBuffers = 4096;

s.waitForBoot {
	var d, g, code = 0;
	SynthDescLib.global.removeServer(Server.default);
	SynthDescLib.global.addServer(s);
	{ Server.default = s }.defer;
	0.5.wait;
	d = SuperDirt(numChannels, s);
	s.sync;
	g = SuperDirtGUI(d);
	s.sync;

	add.("# The library");
	add.("");
	add.("Everything in the four content folders, control by control. **This page is generated**: [make-library.scd](make-library.scd) reads each file's header and looks at the SynthDef it builds, so change a file and run it again rather than editing this page. How to write your own is in [writing-content.md](writing-content.md).");
	add.("");
	add.("The channel counts are for `SuperDirt(%, s)`. A file builds its SynthDef with `~dirt.numChannels`, so an orbit of a different width has wider or narrower busses and everything here follows. A dash under *default* means the word has no control of that name: it is what switches the file on, or something the file reads from the event in the language.".format(numChannels));
	add.("");
	add.("Ranges are what is useful, written by hand in each file's header; the SynthDefs clip some of them and enforce none of the rest.");
	add.("");

	section.(\synths, "Synths",
		"Played by name, `s \"sdgacid\"`. Each takes `n` or `note` for its pitch and `sustain` or `legato` for its length, and SuperDirt's own effects apply. `sdgatk` and `sdgrel` are not `attack` and `release`, which would switch on SuperDirt's own envelope module: see [superdirt-notes.md](superdirt-notes.md).",
		{ |name| name.asString },
		{ |name| name.asString }
	);
	section.(\modules, "Modules",
		"An effect on one event: it runs behind the sound, in the event's own group, on every event whose parameter is set.",
		{ |name| name.asString },
		// a module with variants builds <name>_<variant>_<channels> (sdgbody: one per object): show the first
		{ |name| var plain = name.asString ++ numChannels;
			if(SynthDescLib.global.at(plain.asSymbol).notNil) { plain } { "%_0_%".format(name, numChannels) } }
	);
	section.(\players, "Sample players",
		"Each builds one SynthDef per sample width, `<name>_1_%` and `<name>_2_%`, so a mono or a stereo file both work; the channels shown are the stereo one's. An event's own `begin`, `end`, `speed`, `pan` and `n` keep their meaning unless a file says otherwise. How one gets chosen is in [sample-players.md](sample-players.md).".format(numChannels, numChannels),
		{ |name| name.asString },
		{ |name| "%_2_%".format(name, numChannels) }
	);
	section.(\effects, "Global effects",
		"One per orbit, on a track. `example-global` goes into every orbit by itself; the others are *offered*: each waits in the add menu of the window's effects tab, uses nothing until added, and is then on, because its amount's default is not 0. All of them read the track's `dryBus` and write its `effectBus`; [the-mixer.md](the-mixer.md) has the path around them.",
		{ |name| name.asString.replace("global_", "") },
		{ |name| name.asString ++ numChannels }
	);
	plumbing.([\global_sdgfader, \sdgtrack_send, \sdgtrack_return, \sdgreturn_send, \sdgreturn_head, \sdgreturn_mix, \sdgreturn_link, \sdgreturn_out]);

	File.use(g.path +/+ "docs" +/+ "library.md", "w", { |file| file.write(out.join("\n")) });
	"wrote % lines to %".format(out.size, g.path +/+ "docs" +/+ "library.md").postln;
	"---- does not match: %".format(problems.size).postln;
	problems.do(_.postln);
	"DONE".postln;
	try { s.quit };
	0.5.wait;
	code.exit;
};
)
