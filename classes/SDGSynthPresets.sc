// =====================================================================
// SDGSynthPresets - a synth's presets as changed in the window (save as...,
// delete), kept on this machine, outside the repo: a file per synth in
// SDGSynthPresets.folder (the user support dir) holding the whole list as it
// should be. While a synth has one, it replaces the presets the synth's own
// file registers, so updating the quark never clashes with them;
// SuperDirtGUI:restoreFilePresets takes it away.
//
//   ~sdg.savePreset(\sdgkick, "my kick", (sdgdecay: 0.3));   // -> its n
//   SDGSynthPresets.read(\sdgkick);       // [(presetName: "808 long boom", sdgfreq: 49.5, ...), ...] or nil
//   SDGSynthPresets.folder.openOS;
//
// A preset is an Event of the synth's controls and its presetName, nothing else:
// SuperDirtGUI puts it on top of what the file's first preset holds besides
// those (instrument, note: 0, a drum voice's unitDuration).
// =====================================================================

SDGSynthPresets {
	classvar folder;

	*folder { ^folder ?? { Platform.userAppSupportDir +/+ "superdirt-gui-presets" } }
	*folder_ { |path| folder = path }

	*fileFor { |synth| ^this.folder +/+ synth.asString ++ ".scd" }

	*exists { |synth| ^File.exists(this.fileFor(synth)) }

	// the list, or nil when the synth has none of its own (or its file is broken)
	*read { |synth|
		var file = this.fileFor(synth), list;
		if(File.exists(file).not) { ^nil };
		list = try { File.readAllString(file).interpret } { nil };
		if(list.isKindOf(SequenceableCollection).not) {
			"SDGSynthPresets: % could not be read - the file's own presets play".format(file).warn;
			^nil
		};
		^list.select { |p| p.isKindOf(Dictionary) }.collect { |p| p.as(Event) }
	}

	// one preset a line, the numbers as they are, readable and editable by hand
	*write { |synth, list|
		var file = this.fileFor(synth), text;
		File.mkdir(this.folder);
		text = "// presets of % saved from SuperDirtGUI's window, one per n\n[\n".format(synth)
			++ list.collect { |p|
				var keys = p.keys.asArray.reject { |k| k == \presetName }.sort;
				"\t(presetName: " ++ (p[\presetName] ? "").asString.asCompileString
				++ keys.collect { |k| ", " ++ k ++ ": " ++ p[k].asCompileString }.join ++ ")"
			}.join(",\n") ++ "\n]\n";
		File.use(file, "w", { |f| f.write(text) });
		^file
	}

	*forget { |synth|
		var file = this.fileFor(synth);
		if(File.exists(file)) { File.delete(file) };
	}
}
