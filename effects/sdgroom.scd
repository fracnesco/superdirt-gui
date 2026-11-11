// effects/sdgroom.scd - JPverb as a small room: short and close, a sense of place more than a tail.
//
// category: reverb
//
// requires: JPverb, JPverbRaw (sc3-plugins)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgroom).
//
// parameters:
//   sdgroom      0 to 1         how much of it
//   sdgroomtime  0.1 to 3 s     the time it takes to die away
//   sdgroomsize  0.5 to 1.5     how large the room is
//   sdgroomdamp  0 to 1         soft walls
//   sdgroomcut   20 to 1000 Hz  the lows kept out of it
//
//   d1 $ s "cp*2" # pF "sdgroom" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgroom" ++ n, { |dryBus, effectBus, gate = 1, sdgroom = 0.3, sdgroomtime = 0.6, sdgroomsize = 0.5, sdgroomdamp = 0.5, sdgroomcut = 200|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var wet = JPverb.ar(HPF.ar(stereo, sdgroomcut.clip(20, 1000)), sdgroomtime.clip(0.1, 3), sdgroomdamp.clip(0, 1),
		sdgroomsize.clip(0.5, 1.5), 0.9, 0.05, 1.3, 0.5, 1, 0.8);
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgroom.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgroom, [\sdgroom, \sdgroomtime, \sdgroomsize, \sdgroomdamp, \sdgroomcut], (
	sdgroom: [0, 1, \lin],
	sdgroomtime: [0.1, 3, \exp],
	sdgroomsize: [0.5, 1.5, \lin],
	sdgroomdamp: [0, 1, \lin],
	sdgroomcut: [20, 1000, \exp]
));
)
