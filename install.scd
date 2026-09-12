// Symlink this repository into the SuperCollider extensions folder.
// Evaluate the block below, then recompile the class library
// (Language -> Recompile Class Library, or ctrl/cmd + shift + L).
//
// This is the route for a clone you want to keep editing. If you would
// rather have it installed and updated for you, do not use this file -
// install it as a quark instead, from a fresh sclang:
//
//   Quarks.install("https://github.com/fracnesco/superdirt-gui.git");
//   thisProcess.recompile;
//
// One or the other, not both: two copies on the class path is a
// duplicate class error at compile time.
//
// ln -s is macOS and Linux. On Windows, use the quark, or copy this
// folder into Platform.userExtensionDir by hand - .openOS opens it.
//
// Needs SuperDirt installed and compiled first. Quarks.install("SuperDirt")
// brings Vowel and Dirt-Samples along with it; the quark route above does
// the same when SuperDirt is missing.

(
var src = PathName(thisProcess.nowExecutingPath).pathOnly;
var dst = Platform.userExtensionDir +/+ "SuperDirtGUI";

// drop the trailing slash, ln -s does not like it
if (src.endsWith("/")) { src = src.drop(-1) };

case
{ File.exists(dst) } {
	("SuperDirtGUI: % already exists - remove it first if you want to relink."
		.format(dst)).warn;
}
{ true } {
	File.mkdir(Platform.userExtensionDir);
	("ln -s" + src.shellQuote + dst.shellQuote).unixCmd({ |exit|
		if (exit == 0) {
			"SuperDirtGUI: linked".postln;
			("   " ++ src).postln;
			("-> " ++ dst).postln;
			"".postln;
			"Now recompile the class library: ctrl/cmd + shift + L".postln;
		} {
			("SuperDirtGUI: linking failed with exit code" + exit).warn;
		};
	});
};
)


// ---------------------------------------------------------------- verify
(
var missing = [\SuperDirtGUI]
	.select { |sym| sym.asClass.isNil };

if (missing.notEmpty) {
	("SuperDirtGUI is not compiled - % is missing."
		"\n  Recompile the class library, ctrl/cmd + shift + L, and check the"
		"\n  post window for compile errors.".format(missing.join(", "))).warn;
} {
	"SuperDirtGUI is installed.".postln;
	if (\SuperDirt.asClass.isNil) {
		"  but SuperDirt is not: Quarks.install(\"SuperDirt\"), then recompile.".warn;
	} {
		"  start: ~sdg = SuperDirtGUI(~dirt) after ~dirt.start - see examples/superdirt-gui-demo.scd".postln;
	};
	"  help: SuperDirtGUI, then cmd+d".postln;
};
)


// ------------------------------------------------------------- uninstall
(
var link = Platform.userExtensionDir +/+ "SuperDirtGUI";
case
{ File.type(link) == \symlink } {
	File.delete(link);
	("Removed %."
		"\n  Recompile the class library to finish.".format(link)).postln;
}
{ File.type(link) == \not_found } {
	"Nothing to remove at %.".format(link).postln;
}
{ true } {
	"% is not a link, leaving it alone.".format(link).warn;
};
)
