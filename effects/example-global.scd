// effects/example-global.scd - a global effect: an echo, one per orbit, always there.
//
// a global effect runs once per orbit for as long as the orbit lives, reading
// the orbit's dry signal and adding to its effect bus, like SuperDirt's own
// dirt_delay. the SynthDef is named "global_<name>" ++ the channel count. the
// gate lets SuperDirt release it, DirtPause puts it to sleep while the orbit is
// silent and no parameter arrives. addGlobalEffect registers it, and every
// orbit gets its own instance, first in the chain.
//
// parameters:
//   sdgspace  0 to 1       how much echo
//   sdgtime   0.01 to 1 s  the time between echoes
//
//   d1 $ s "bd*4" # pF "sdgspace" 0.5 # pF "sdgtime" 0.375
//
// new... in the window's modules tab copies a file like this one under another
// name. while the window is open, a file is read again when you save it: the
// effect starts again in every orbit, and the rest of the chain plays on.
(
var numChannels = ~dirt.numChannels;

SynthDef("global_sdgspace" ++ numChannels, { |dryBus, effectBus, gate = 1, sdgspace = 0, sdgtime = 0.3|
	var sig = In.ar(dryBus, numChannels);
	var wet = CombL.ar(sig, 1, sdgtime.clip(0.01, 1), 2);
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	DirtPause.ar(sig, graceTime: 4);
	Out.ar(effectBus, wet * sdgspace * env);
}, [\ir, \ir]).add;

~sdg.addGlobalEffect(\global_sdgspace, [\sdgspace, \sdgtime]);
)
