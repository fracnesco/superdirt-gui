// examples/tidal/make-fx-words.scd - writes the insert effects' Tidal words
//
// s "growl" # drive1 12 # tone 2000: each word a pattern can say to an insert
// effect is one line of Haskell in the boot files. This makes those lines from
// the effects installed here (every subclass of FxParamUnit, its *tidalParams):
// a knob or a toggle is a pF, a menu a pS (an item's name, or its index as
// "3"). It writes them between the BEGIN and END lines of insert effect words
// in BootTidal.hs and multi target tidal/boot-touchdesigner.hs, replacing what
// was there, and says what it could not make a word of:
//
//   - Tidal's own words of the same meaning (hold, delay ...) are left to Tidal;
//   - a word Tidal or Haskell already has as a function, or one of the boot's
//     own functions (functions/*.hs), would break it: rename the parameter in
//     its effect, with *renamed for old settings;
//   - SuperDirt's own event keys (pan, speed ...) are never read as an effect's.
//
// Run it after changing an effect's table or *tidalParams (sclang is inside
// SuperCollider.app on macOS; from a terminal it quits when done):
//   sclang "examples/tidal/make-fx-words.scd"
// (or evaluate the block in the IDE). Language only, no server.
(
var here = thisProcess.nowExecutingPath.dirname;
var boots = [here +/+ "BootTidal.hs", here +/+ "multi target tidal" +/+ "boot-touchdesigner.hs"];
var begin = "-- BEGIN insert effect words", end = "-- END insert effect words";
// Tidal 1.9.5's own parameters of these names, pF, meaning the same thing
var tidalParams = #[\hold, \smear, \decay, \detune, \ring, \delay];
// functions of Tidal, the Prelude or the boot's own (measured with :t in ghci, 2026-11-08)
var taken = #[\scramble, \stretch, \density, \reverse, \spread, \mod, \shuffle, \frozen, \range, \fast, \slow,
	\rev, \every, \sometimes, \chop, \striate, \fix, \scale, \arp, \rot, \run, \irand, \rand, \sine, \saw, \square,
	\tri, \id, \const, \map, \filter, \min, \max, \sum, \round, \floor, \ceiling, \pi, \log, \exp, \sqrt, \abs, \signum];
var reserved = SDGFxControl.reservedWords;
var kinds = IdentityDictionary.new, owners = IdentityDictionary.new, problems = [];
var lines, block, write;

(\FxParamUnit.asClass.allSubclasses ? []).sort { |a, b| a.name < b.name }.do { |cls|
	cls.tidalParams.do { |key|
		var e = cls.param(key), kind = if(e[\kind] == \menu) { \pS } { \pF };
		case
		{ reserved.includes(key) } { problems = problems.add("%.% is a SuperDirt event key: never read".format(cls.name, key)) }
		{ taken.includes(key) } { problems = problems.add("%.% is a Tidal/Haskell function: rename it in the effect".format(cls.name, key)) }
		{ tidalParams.includes(key) } { owners[key] = owners[key].add(cls.name) }	// Tidal has it
		{
			if(kinds[key].notNil and: { kinds[key] != kind }) {
				problems = problems.add("% is a % in % and a % in %: one Haskell word has one type"
					.format(key, kinds[key], owners[key], kind, cls.name));
			} {
				kinds[key] = kind;
			};
			owners[key] = owners[key].add(cls.name);
		}
	}
};

lines = kinds.keys.asArray.sort.collect { |key|
	"%% = % \"%\"   -- %".format(if(key == kinds.keys.asArray.sort.first) { "let " } { "    " },
		key, kinds[key], key, owners[key].join(", "))
};
block = ([begin, "-- (made by examples/tidal/make-fx-words.scd from the effects' tables: do not edit by hand)", ":{"]
	++ lines ++ [":}", end]).join("\n");

write = { |path|
	var text = File.readAllString(path), a = text.find(begin), b = text.find(end);
	if(a.isNil or: { b.isNil }) {
		problems = problems.add("% has no BEGIN/END lines for the words".format(path.basename));
	} {
		text = text.keep(a) ++ block ++ text.drop(b + end.size);
		File.use(path, "w", { |f| f.write(text) });
		"wrote % words into %".format(lines.size, path.basename).postln;
	}
};
boots.do(write);
"Tidal's own, left to it: %".format(tidalParams.select { |k| owners[k].notNil }).postln;
problems.do { |p| ("PROBLEM: " ++ p).postln };
if(problems.isEmpty) { "no problems".postln };
if(Platform.ideName == "none") { 0.exit };	// run from a terminal: done
)
