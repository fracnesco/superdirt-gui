// effects/sdgclouds.scd - MiClouds, Mutable Instruments' granular processor: turns what it hears into a texture.
//
// category: granular
//
// requires: MiClouds (mi-UGens)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgclouds).
//
// parameters:
//   sdgclouds        0 to 1     how much of it
//   sdgcloudspit     -24 to 24  the grains' pitch, in semitones
//   sdgcloudspos     0 to 1     where in the last seconds the grains read
//   sdgcloudssize    0 to 1     the grains' length
//   sdgcloudsdens    0 to 1     0.5 no grains, under it even, over it random
//   sdgcloudstex     0 to 1     the grains' shape
//   sdgcloudsrvb     0 to 1     its own reverb
//   sdgcloudsfb      0 to 1     its own feedback
//   sdgcloudsmode    0 to 3     0 granular, 1 stretch, 2 looping delay, 3 spectral
//   sdgcloudsfreeze  0 or 1     stops recording: the grains read what they have
//
//   d1 $ s "cp*2" # pF "sdgclouds" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgclouds" ++ n, { |dryBus, effectBus, gate = 1, sdgclouds = 0.5, sdgcloudspit = 0, sdgcloudspos = 0.5, sdgcloudssize = 0.35, sdgcloudsdens = 0.75, sdgcloudstex = 0.5, sdgcloudsrvb = 0.25, sdgcloudsfb = 0.15, sdgcloudsmode = 0, sdgcloudsfreeze = 0|
	var in = In.ar(dryBus, n);
	var stereo = NumChannels.ar(in, 2);
	var wet = LeakDC.ar(MiClouds.ar(stereo, sdgcloudspit.clip(-48, 48), sdgcloudspos.clip(0, 1), sdgcloudssize.clip(0, 1),
		sdgcloudsdens.clip(0, 1), sdgcloudstex.clip(0, 1), 1, 1, 0.7, sdgcloudsrvb.clip(0, 1), sdgcloudsfb.clip(0, 1),
		sdgcloudsfreeze.clip(0, 1), sdgcloudsmode.clip(0, 3)));
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgclouds.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgclouds, [\sdgclouds, \sdgcloudspit, \sdgcloudspos, \sdgcloudssize, \sdgcloudsdens, \sdgcloudstex, \sdgcloudsrvb, \sdgcloudsfb, \sdgcloudsmode, \sdgcloudsfreeze], (
	sdgclouds: [0, 1, \lin],
	sdgcloudspit: [-24, 24, \lin],
	sdgcloudspos: [0, 1, \lin],
	sdgcloudssize: [0, 1, \lin],
	sdgcloudsdens: [0, 1, \lin],
	sdgcloudstex: [0, 1, \lin],
	sdgcloudsrvb: [0, 1, \lin],
	sdgcloudsfb: [0, 1, \lin],
	sdgcloudsmode: [0, 3, \lin, 1],
	sdgcloudsfreeze: [0, 1, \lin, 1]
));
)
