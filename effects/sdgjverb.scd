// effects/sdgjverb.scd - JPverb as a hall: long, smooth, with separate decay for lows, mids and highs.
//
// category: reverb
//
// requires: JPverb, JPverbRaw (sc3-plugins)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgjverb).
//
// parameters:
//   sdgjverb       0 to 1       how much of it
//   sdgjverbtime   0.1 to 60 s  the time it takes to die away
//   sdgjverbsize   0.5 to 5     how large the room is
//   sdgjverbdamp   0 to 1       the top lost on each pass
//   sdgjverbearly  0 to 1       how smeared the first reflections are
//   sdgjverbmod    0 to 1       how far the tail wavers
//   sdgjverblow    0 to 2       decay of what is under 500 Hz, 1 as the mids
//   sdgjverbhigh   0 to 2       decay of what is over 2 kHz, 1 as the mids
//
//   d1 $ s "cp*2" # pF "sdgjverb" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgjverb" ++ n, { |dryBus, effectBus, gate = 1, sdgjverb = 0.35, sdgjverbtime = 3, sdgjverbsize = 1.5, sdgjverbdamp = 0.2, sdgjverbearly = 0.707, sdgjverbmod = 0.1, sdgjverblow = 1, sdgjverbhigh = 1|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var wet = JPverb.ar(stereo, sdgjverbtime.clip(0.1, 60), sdgjverbdamp.clip(0, 1), sdgjverbsize.clip(0.5, 5),
		sdgjverbearly.clip(0, 1), sdgjverbmod.clip(0, 1), 2, sdgjverblow.clip(0, 2), 1, sdgjverbhigh.clip(0, 2));
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgjverb.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgjverb, [\sdgjverb, \sdgjverbtime, \sdgjverbsize, \sdgjverbdamp, \sdgjverbearly, \sdgjverbmod, \sdgjverblow, \sdgjverbhigh], (
	sdgjverb: [0, 1, \lin],
	sdgjverbtime: [0.1, 60, \exp],
	sdgjverbsize: [0.5, 5, \lin],
	sdgjverbdamp: [0, 1, \lin],
	sdgjverbearly: [0, 1, \lin],
	sdgjverbmod: [0, 1, \lin],
	sdgjverblow: [0, 2, \lin],
	sdgjverbhigh: [0, 2, \lin]
));
)
