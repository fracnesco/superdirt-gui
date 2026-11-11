// effects/sdgmiverb.scd - MiVerb, the reverb of Mutable Instruments' Clouds: bright, grainy, a bit metallic.
// Time at 1 or more does not die away and drives itself into soft distortion.
//
// category: reverb
//
// requires: MiVerb (mi-UGens)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgmiverb).
//
// parameters:
//   sdgmiverb        0 to 1     how much of it
//   sdgmiverbtime    0 to 1.25  how long it lasts
//   sdgmiverbdamp    0 to 1     the top lost on each pass
//   sdgmiverbdiff    0 to 1     how smeared it is
//   sdgmiverbhp      0 to 1     the lows taken out
//   sdgmiverbfreeze  0 or 1     holds the tail and closes the input
//
//   d1 $ s "cp*2" # pF "sdgmiverb" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgmiverb" ++ n, { |dryBus, effectBus, gate = 1, sdgmiverb = 0.4, sdgmiverbtime = 0.75, sdgmiverbdamp = 0.5, sdgmiverbdiff = 0.625, sdgmiverbhp = 0.05, sdgmiverbfreeze = 0|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var wet = LeakDC.ar(MiVerb.ar(stereo, sdgmiverbtime.clip(0, 1.25), 1, sdgmiverbdamp.clip(0, 1), sdgmiverbhp.clip(0, 1),
		sdgmiverbfreeze.clip(0, 1), sdgmiverbdiff.clip(0, 1)));
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgmiverb.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgmiverb, [\sdgmiverb, \sdgmiverbtime, \sdgmiverbdamp, \sdgmiverbdiff, \sdgmiverbhp, \sdgmiverbfreeze], (
	sdgmiverb: [0, 1, \lin],
	sdgmiverbtime: [0, 1.25, \lin],
	sdgmiverbdamp: [0, 1, \lin],
	sdgmiverbdiff: [0, 1, \lin],
	sdgmiverbhp: [0, 1, \lin],
	sdgmiverbfreeze: [0, 1, \lin, 1]
));
)
