// modules/example-module.scd - a module: a comb filter on each event that asks for it.
//
// category: filter
//
// a module is a function SuperDirt calls for every event whose test passes,
// after the sound itself has been started. this one adds a comb filter to the
// event whenever it carries sdgcomb. the SynthDef reads the event's bus (out)
// and writes back in place, at the orbit's channel count; SuperDirt puts it in
// the event's group behind the sound, so it hears what the sound made.
//
// parameters:
//   sdgcomb  0 to 1     the comb's delay, 0.5 ms at 0 to 50 ms at 1
//   sdgres   0 to 10 s  how long the comb rings
//
//   d1 $ s "bd*4" # pF "sdgcomb" 0.4 # pF "sdgres" 2
//
// new... in the window's modules tab copies a file like this one under another
// name. while the window is open, a file is read again when you save it.
(
~dirt.addModule('sdgcomb', { |dirtEvent|
	dirtEvent.sendSynth("sdgcomb" ++ ~dirt.numChannels, [
		sdgcomb: ~sdgcomb,
		sdgres: ~sdgres ? 1,
		out: ~out
	])
}, { ~sdgcomb.notNil });

SynthDef("sdgcomb" ++ ~dirt.numChannels, { |out, sdgcomb = 0.5, sdgres = 1|
	var sig = In.ar(out, ~dirt.numChannels);
	var wet = CombC.ar(sig, 0.05, sdgcomb.linexp(0, 1, 0.0005, 0.05), sdgres);
	ReplaceOut.ar(out, (sig + wet) * 0.5);
}).add;
)
