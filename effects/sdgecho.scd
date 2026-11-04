// effects/sdgecho.scd - Greyhole as a delay: separate echoes that blur and darken as they repeat.
// The same network with the diffusion nearly off and a small size; each pass wavers a little, like tape.
//
// requires: Greyhole, GreyholeRaw (sc3-plugins)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgecho).
//
// parameters:
//   sdgecho      0 to 1       how much of it
//   sdgechotime  0.08 to 2 s  the time between echoes
//   sdgechofb    0 to 0.95    how many echoes
//   sdgechoblur  0 to 0.9     the echoes smear into each other
//   sdgechodamp  0 to 1       the top lost on each echo
//   sdgechomod   0 to 1       how far the time wavers
//
//   d1 $ s "cp*2" # pF "sdgecho" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgecho" ++ n, { |dryBus, effectBus, gate = 1, sdgecho = 0.4, sdgechotime = 0.375, sdgechofb = 0.55, sdgechoblur = 0.1, sdgechodamp = 0.35, sdgechomod = 0.1|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var time = sdgechotime.clip(0.08, 2) - 0.07;	// Greyhole takes 70 ms more than its time, measured
	var wet = Greyhole.ar(DelayL.ar(stereo, 2, time), time, sdgechodamp.clip(0, 1), 1,
		sdgechoblur.clip(0, 0.9), sdgechofb.clip(0, 0.95), sdgechomod.clip(0, 1), 1.3);
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgecho.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgecho, [\sdgecho, \sdgechotime, \sdgechofb, \sdgechoblur, \sdgechodamp, \sdgechomod], (
	sdgecho: [0, 1, \lin],
	sdgechotime: [0.08, 2, \exp],
	sdgechofb: [0, 0.95, \lin],
	sdgechoblur: [0, 0.9, \lin],
	sdgechodamp: [0, 1, \lin],
	sdgechomod: [0, 1, \lin]
));
)
