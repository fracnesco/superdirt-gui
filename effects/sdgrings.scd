// effects/sdgrings.scd - MiRings, Mutable Instruments' resonator: whatever comes in rings at a pitch, as a bar or a string.
//
// requires: MiRings (mi-UGens)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgrings).
//
// parameters:
//   sdgrings        0 to 1    how much of it
//   sdgringspit     12 to 96  the pitch, a midi note; models 0 and 2 ring an octave above it
//   sdgringsstruct  0 to 1    the material: harmonic to inharmonic
//   sdgringsbright  0 to 1    how bright
//   sdgringsdamp    0 to 1    how long it rings, 0.1 to 10 s
//   sdgringspos     0 to 1    where it is struck
//   sdgringsmodel   0 to 5    0 modal, 1 strings, 2 inharmonic, 3 fm, 4 quantized, 5 with reverb
//   sdgringspoly    1 to 4    voices, fewer partials each
//
//   d1 $ s "cp*2" # pF "sdgrings" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgrings" ++ n, { |dryBus, effectBus, gate = 1, sdgrings = 0.5, sdgringspit = 48, sdgringsstruct = 0.25, sdgringsbright = 0.5, sdgringsdamp = 0.7, sdgringspos = 0.25, sdgringsmodel = 0, sdgringspoly = 2|
	var in = In.ar(dryBus, n);
	var wet = LeakDC.ar(MiRings.ar(in.asArray.sum * 0.5, 0, sdgringspit.clip(12, 96), sdgringsstruct.clip(0, 1),
		sdgringsbright.clip(0, 1), sdgringsdamp.clip(0, 1), sdgringspos.clip(0, 1), sdgringsmodel.clip(0, 5),
		sdgringspoly.clip(1, 4)));
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgrings.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgrings, [\sdgrings, \sdgringspit, \sdgringsstruct, \sdgringsbright, \sdgringsdamp, \sdgringspos, \sdgringsmodel, \sdgringspoly], (
	sdgrings: [0, 1, \lin],
	sdgringspit: [12, 96, \lin],
	sdgringsstruct: [0, 1, \lin],
	sdgringsbright: [0, 1, \lin],
	sdgringsdamp: [0, 1, \lin],
	sdgringspos: [0, 1, \lin],
	sdgringsmodel: [0, 5, \lin, 1],
	sdgringspoly: [1, 4, \lin, 1]
));
)
