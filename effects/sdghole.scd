// effects/sdghole.scd - Greyhole as a reverb: a big diffuse cloud, slowly moving.
// Julian Parker's delay network with the diffusion up: the echoes smear into each other.
//
// requires: Greyhole, GreyholeRaw (sc3-plugins)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdghole).
//
// parameters:
//   sdghole      0 to 1        how much of it
//   sdgholetime  0.05 to 2 s   the delay lines' time
//   sdgholesize  0.5 to 5      how large the network is
//   sdgholedamp  0 to 1        the top lost on each pass
//   sdgholediff  0 to 0.95     0 echoes, up from there a wash
//   sdgholefb    0 to 0.95     how long it lasts
//   sdgholemod   0 to 1        how far the lines waver
//   sdgholerate  0.1 to 10 Hz  how fast they waver
//
//   d1 $ s "cp*2" # pF "sdghole" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdghole" ++ n, { |dryBus, effectBus, gate = 1, sdghole = 0.4, sdgholetime = 0.8, sdgholesize = 1.5, sdgholedamp = 0.2, sdgholediff = 0.8, sdgholefb = 0.8, sdgholemod = 0.2, sdgholerate = 1|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var wet = Greyhole.ar(stereo, sdgholetime.clip(0.05, 2), sdgholedamp.clip(0, 1), sdgholesize.clip(0.5, 5),
		sdgholediff.clip(0, 0.95), sdgholefb.clip(0, 0.95), sdgholemod.clip(0, 1), sdgholerate.clip(0.1, 10));
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdghole.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdghole, [\sdghole, \sdgholetime, \sdgholesize, \sdgholedamp, \sdgholediff, \sdgholefb, \sdgholemod, \sdgholerate], (
	sdghole: [0, 1, \lin],
	sdgholetime: [0.05, 2, \exp],
	sdgholesize: [0.5, 5, \lin],
	sdgholedamp: [0, 1, \lin],
	sdgholediff: [0, 0.95, \lin],
	sdgholefb: [0, 0.95, \lin],
	sdgholemod: [0, 1, \lin],
	sdgholerate: [0.1, 10, \exp]
));
)
