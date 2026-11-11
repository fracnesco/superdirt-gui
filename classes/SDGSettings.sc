// =====================================================================
// SDGSettings - SuperDirtGUI's preferences on this computer, kept between
// runs in one file: <folder>/settings.scd, an Event as code
//
//   SDGSettings.at(\sleepAfter, 4);     // the value, or the default
//   SDGSettings.put(\sleepAfter, 10);   // written at once
//   SDGSettings.folder.openOS;
//
// Not in a session: a session is the mixer, these are how the machine
// runs it. Checks set SDGSettings.folder to their scratchpad, so the
// user's own file is never touched.
// =====================================================================

SDGSettings {
	classvar folder, cache;

	*folder { ^folder ?? { Platform.userAppSupportDir +/+ "superdirt-gui-settings" } }
	*folder_ { |path| folder = path; cache = nil }
	*path { ^this.folder +/+ "settings.scd" }

	*all { ^cache ?? { cache = this.prRead } }
	*at { |key, default| ^this.all[key] ? default }
	*put { |key, value| this.all.put(key, value); this.prWrite }

	*prRead {
		var path = this.path, value;
		if(File.exists(path).not) { ^() };
		value = try { File.readAllString(path).interpret };
		if(value.isKindOf(Dictionary).not) {
			"SuperDirtGUI: could not read the settings in % - using the defaults".format(path).warn;
			^()
		};
		^value
	}

	*prWrite {
		var file;
		File.mkdir(this.folder);
		file = File(this.path, "w");
		if(file.isOpen.not) {
			^"SuperDirtGUI: could not write the settings to %".format(this.path).warn
		};
		file.write("// SuperDirtGUI settings, written by its settings window\n");
		file.write(this.all.asCompileString ++ "\n");
		file.close;
	}
}
