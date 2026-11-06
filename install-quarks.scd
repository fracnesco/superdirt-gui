// Install every optional quark SuperDirtGUI can use, in one go.
// Evaluate the first block, wait for the post window to say "done", then
// recompile the class library (Language -> Recompile Class Library,
// ctrl/cmd + shift + L). The second block checks what you have.
//
// Nothing here is required: without a quark its menu entry is greyed out or
// its insert kind is not listed, and the rest works the same
// (docs/requirements.md has the table). Each line is one quark; delete the
// ones you do not want. Each install clones a repository with git, so git
// has to be on the machine, and a few of them take a moment.
//
// What they are:
//   BusManager    names the orbit busses, an output menu on each strip
//   BusRecorder   the recorder button: a file per orbit and return
//   DigitDrag     number boxes drag by the digit under the pointer
//   ServerPanel   the server window, frequency scope, level meters and node tree
//   ParaEQ, Compressor, Insight   the eq, compressor and meter inserts
//   FxKit and Growl, Tunnel, Smear, Cloud, Stammer, Sympathy, Spiral
//                 the creative inserts (each pulls FxKit and ParaEQ in)
//   Reverbs       four reverb inserts: Hall, Plate, Coil, Shimmer (on FxKit too)
//   Modality-toolkit   MIDI controllers on the mixer (~sdg.midi)
//
// Not quarks, so not here: VSTPlugin, sc3-plugins and mi-UGens are server
// plugins you download and copy into the Extensions folder by hand -
// docs/requirements.md, "Plugins".

(
var quarks = [
	"https://github.com/fracnesco/sc-bus-manager.git",                // BusManager
	"https://github.com/fracnesco/sc-recorder.git",                   // BusRecorder
	"https://github.com/fracnesco/sc-digit-drag.git",                 // DigitDrag
	"https://github.com/fracnesco/sc-server-panel.git",               // ServerPanel
	"https://github.com/fracnesco/sc-eq.git",                         // ParaEQ
	"https://github.com/fracnesco/sc-compressor.git",                 // Compressor
	"https://github.com/fracnesco/supercollider-insight-meter.git",   // Insight
	"https://github.com/fracnesco/sc-fx-kit.git",                     // FxKit
	"https://github.com/fracnesco/sc-growl.git",
	"https://github.com/fracnesco/sc-tunnel.git",
	"https://github.com/fracnesco/sc-smear.git",
	"https://github.com/fracnesco/sc-cloud.git",
	"https://github.com/fracnesco/sc-stammer.git",
	"https://github.com/fracnesco/sc-sympathy.git",
	"https://github.com/fracnesco/sc-spiral.git",
	"https://github.com/fracnesco/sc-reverbs.git",                     // Hall, Plate, Coil, Shimmer
	"Modality-toolkit"                                                // MIDI controllers
];
var failed = [];

if (\SuperDirtGUI.asClass.isNil) {
	"SuperDirtGUI itself is not installed yet. First:"
	"\n  Quarks.install(\"https://github.com/fracnesco/superdirt-gui.git\"); thisProcess.recompile;".warn;
};

quarks.do { |q|
	("installing " ++ q).postln;
	try { Quarks.install(q) } { |err|
		failed = failed.add(q);
		("  failed: " ++ err.errorString).warn;
	};
};

"".postln;
if (failed.isEmpty) {
	"done: every quark installed.".postln;
} {
	("done, but % could not be installed:\n  %"
		.format(failed.size, failed.join("\n  "))).warn;
	"  Check that git is installed and the machine is online, then evaluate again.".postln;
};
"Now recompile the class library: ctrl/cmd + shift + L, then run the block below.".postln;
)


// ------------------------------------------------------------------ check
// After the recompile: one line per piece, yes or -.
(
var have = { |names| names.every { |n| n.asSymbol.asClass.notNil } };
[
	["SuperDirt (required)", [\SuperDirt]],
	["SuperDirtGUI (required)", [\SuperDirtGUI]],
	["BusManager", [\BusManager]],
	["BusRecorder", [\BusRecorder]],
	["DigitDrag", [\DigitDrag]],
	["ServerPanel", [\ServerPanel]],
	["ParaEQ", [\ParaEQ]],
	["Compressor", [\Compressor]],
	["Insight", [\Insight]],
	["FxKit inserts", [\FxParamUnit, \Growl, \Tunnel, \Smear, \Cloud, \Stammer, \Sympathy, \Spiral]],
	["Reverb inserts", [\Hall, \Plate, \Coil, \Shimmer]],
	["Modality", [\MKtl]],
	["VSTPlugin (plugin)", [\VSTPlugin]],
	["sc3-plugins (plugin)", [\Greyhole, \JPverb]],
	["mi-UGens (plugin)", [\MiVerb, \MiClouds, \MiRings, \MiWarps]]
].do { |row| "%  %".format(row[0].padRight(26), if (have.(row[1])) { "yes" } { "-" }).postln };
if (\SuperDirt.asClass.notNil and: { SuperDirt.findMethod(\loadSoundFilesToBank).isNil }) {
	"SuperDirt is older than October 2025: refresh it (docs/requirements.md)".warn
};
)


// -------------------------------------------------------------- updating
// SuperCollider 3.13's Quarks.update always pulls a branch called master, so
// it fails on the repositories whose branch is main (sc-bus-manager, sc-eq,
// sc-compressor, supercollider-insight-meter). For those, uninstall and
// install again:
//
//   Quarks.uninstall("sc-eq"); Quarks.install("https://github.com/fracnesco/sc-eq.git");
//
// For the others, Quarks.update("sc-recorder") etc., then recompile.
