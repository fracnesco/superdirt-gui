// effects/sdgshimmer.scd - JPverb with a pitch shifter in its feedback: the tail climbs while it dies.
//
// category: reverb
//
// requires: JPverb, JPverbRaw (sc3-plugins)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgshimmer).
//
// parameters:
//   sdgshimmer       0 to 1       how much of it
//   sdgshimmertime   0.5 to 30 s  the time it takes to die away
//   sdgshimmerfb     0 to 0.8     how much goes round through the shifter
//   sdgshimmerpitch  -12 to 24    the interval of each round, in semitones
//   sdgshimmerdamp   0 to 1       the top lost on each pass
//
//   d1 $ s "cp*2" # pF "sdgshimmer" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgshimmer" ++ n, { |dryBus, effectBus, gate = 1, sdgshimmer = 0.35, sdgshimmertime = 5, sdgshimmerfb = 0.35, sdgshimmerpitch = 12, sdgshimmerdamp = 0.3|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var fed = LocalIn.ar(2);
	var verb = JPverb.ar(stereo + fed, sdgshimmertime.clip(0.5, 30), sdgshimmerdamp.clip(0, 1), 2, 0.707, 0.2, 1.1);
	var up = PitchShift.ar(verb, 0.2, sdgshimmerpitch.clip(-12, 24).midiratio, 0.01, 0.02);
	var wet = LeakDC.ar(verb + up);
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	LocalOut.ar(LPF.ar(LeakDC.ar(up), 9000).tanh * sdgshimmerfb.clip(0, 0.8));
	wet = NumChannels.ar(wet, n) * sdgshimmer.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgshimmer, [\sdgshimmer, \sdgshimmertime, \sdgshimmerfb, \sdgshimmerpitch, \sdgshimmerdamp], (
	sdgshimmer: [0, 1, \lin],
	sdgshimmertime: [0.5, 30, \exp],
	sdgshimmerfb: [0, 0.8, \lin],
	sdgshimmerpitch: [-12, 24, \lin],
	sdgshimmerdamp: [0, 1, \lin]
));
)
