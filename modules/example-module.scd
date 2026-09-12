// modules/example-module.scd - a template. copy it, rename it, edit it.
//
// a module is a function SuperDirt calls for every event whose test passes,
// after the sound itself has been started. this one adds a comb filter to the
// event whenever it carries sdgcomb. the SynthDef reads the event's bus (out)
// and writes back in place, at the orbit's channel count; SuperDirt puts it in
// the event's group behind the sound, so it hears what the sound made.
//
//   d1 $ s "bd*4" # pF "sdgcomb" 0.4 # pF "sdgres" 2
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
