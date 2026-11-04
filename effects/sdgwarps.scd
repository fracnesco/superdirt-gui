// effects/sdgwarps.scd - MiWarps, Mutable Instruments' meta-modulator: what comes in modulates its internal oscillator.
// Ring modulation, wavefolding, a vocoder; it sounds only while something comes in.
//
// requires: MiWarps (mi-UGens)
//
// not in any orbit until you add it: the add menu of the window's effects tab,
// or ~sdg.addTrack(0, \global_sdgwarps).
//
// parameters:
//   sdgwarps       0 to 1         how much of it
//   sdgwarpsfreq   20 to 2000 Hz  the oscillator's frequency
//   sdgwarpsalgo   0 to 7.5       0 fade, 1 fold, 2 ring, 3 digital ring, 4 xor, 5 compare, 6 up vocoder
//   sdgwarpstimb   0 to 1         how many high harmonics
//   sdgwarpsosc    1 to 3         1 sine, 2 triangle, 3 saw; from algo 6: saw, pulses, noise
//   sdgwarpsdrive  0 to 1         the level going in, and its saturation
//
//   d1 $ s "cp*2" # pF "sdgwarps" 0.6
(
var n = ~dirt.numChannels;

SynthDef("global_sdgwarps" ++ n, { |dryBus, effectBus, gate = 1, sdgwarps = 0.4, sdgwarpsfreq = 110, sdgwarpsalgo = 2, sdgwarpstimb = 0.5, sdgwarpsosc = 1, sdgwarpsdrive = 0.5|
	var in = In.ar(dryBus, n);
	var mono = in.asArray.sum;
	var open = (Amplitude.kr(mono, 0.01, 0.3) > 0.001).lag(0.05);
	var wet = LeakDC.ar(MiWarps.ar(0, mono, 0.5, sdgwarpsdrive.clip(0, 1), sdgwarpsalgo.clip(0, 7.5), sdgwarpstimb.clip(0, 1),
		sdgwarpsosc.clip(1, 3), sdgwarpsfreq.clip(20, 2000), 5)[0]) * open;	// silent from algo 7.9, measured; 5 is the vocoder's gain
	var env = EnvGen.kr(Env.asr, gate, doneAction: 2);
	wet = NumChannels.ar(wet, n) * sdgwarps.clip(0, 1).lag(0.05);
	DirtPause.ar(in.asArray.sum.abs + wet.asArray.sum.abs, graceTime: 4);	// the tail keeps it awake too
	Out.ar(effectBus, wet * env);
}, [\ir, \ir]).add;

~sdg.offerGlobalEffect(\global_sdgwarps, [\sdgwarps, \sdgwarpsfreq, \sdgwarpsalgo, \sdgwarpstimb, \sdgwarpsosc, \sdgwarpsdrive], (
	sdgwarps: [0, 1, \lin],
	sdgwarpsfreq: [20, 2000, \exp],
	sdgwarpsalgo: [0, 7.5, \lin],
	sdgwarpstimb: [0, 1, \lin],
	sdgwarpsosc: [1, 3, \lin, 1],
	sdgwarpsdrive: [0, 1, \lin]
));
)
