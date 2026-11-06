// modules/sdgbody.scd - the hit strikes a body: a drum shell, a bar, a tube or a bell.
//
// the event's sound goes into a bank of resonators tuned to the modes of a
// real object, so a clap rings like a tom, a hat like a struck bar. sdgbodytype
// picks the object: 0 a membrane (a drum's skin), 1 a free bar (marimba,
// glock), 2 a tube (harmonic, like a string), 3 a bell (hum, prime, tierce,
// quint, nominal). sdgbodyfreq is its lowest mode, sdgbodydecay how long it
// rings; higher modes die sooner. the event lasts at least as long as the ring
// (Ableton's Corpus and the Machinedrum's PI machines do the same).
//
// parameters:
//   sdgbody       0 to 1          how much of the body against the dry hit
//   sdgbodyfreq   40 to 2000 Hz   the lowest mode
//   sdgbodytype   0 to 3          0 membrane, 1 bar, 2 tube, 3 bell
//   sdgbodydecay  0.05 to 4 s     how long it rings, to -60 dB
//
//   d1 $ s "cp*4" # pF "sdgbody" 0.6 # pF "sdgbodyfreq" 110
//   d1 $ s "hh*8" # pF "sdgbody" 0.5 # pF "sdgbodytype" 1 # pF "sdgbodyfreq" "440 660"
//   d1 $ s "sn*2" # pF "sdgbody" 0.7 # pF "sdgbodytype" 3 # pF "sdgbodyfreq" 220 # pF "sdgbodydecay" 2
(
var numChannels = ~dirt.numChannels;
// mode ratios: Bessel zeros of a circular membrane; a free bar; a tube; a church bell
var objects = [
	[1, 1.593, 2.136, 2.295, 2.653, 2.917, 3.155, 3.5],
	[1, 2.756, 5.404, 8.933],
	[1, 2, 3, 4, 5, 6, 7, 8],
	[0.5, 1, 1.183, 1.506, 2, 2.514, 2.662, 3.011]
];

~dirt.addModule('sdgbody', { |dirtEvent|
	var decay = (~sdgbodydecay ? 0.6).clip(0.05, 4);
	var type = (~sdgbodytype ? 0).asInteger.clip(0, 3);
	var dry = ~sustain;
	~sustain = max(~sustain, decay);	// read later by dirt_gate: let it ring out
	dirtEvent.sendSynth("sdgbody_" ++ type ++ "_" ++ numChannels, [
		sdgbody: ~sdgbody,
		sdgbodyfreq: ~sdgbodyfreq ? 180,
		sdgbodydecay: decay,
		sdgdrylen: dry,
		out: ~out
	])
}, { ~sdgbody.notNil });

objects.do { |ratios, type|
	SynthDef("sdgbody_" ++ type ++ "_" ++ numChannels, { |out, sdgdrylen = 1, sdgbody = 0.5, sdgbodyfreq = 180, sdgbodydecay = 0.6|
		var sig = In.ar(out, numChannels)
			// a sample would start again once its own length is over (the gate now lives longer): stop it there
			* EnvGen.ar(Env([1, 1, 0], [(sdgdrylen - 0.004).max(0), 0.003]));
		var freq = sdgbodyfreq.clip(40, 2000);
		var decay = sdgbodydecay.clip(0.05, 4);
		var amps = ratios.collect { |r| r.reciprocal.sqrt };
		// a resonator above 18 kHz would only alias: drop it
		var keep = ratios.collect { |r| (freq * r) < 18000 };
		var ring = DynKlank.ar(`[ratios, amps * keep, ratios.collect { |r| r.sqrt.reciprocal }],
			LPF.ar(sig, 8000), freq, 0, decay);
		// a Ringz bank rings louder the longer and lower it is: level it by both
		ring = LeakDC.ar(ring) * (freq.sqrt / decay) * 0.001 / amps.sum.sqrt;
		ring = (ring * 0.7).tanh / 0.7;
		// equal power, and exactly 0 at the ends (XFade2 leaves 1e-17 of the other side: denormals)
		ring = (sig * (1 - sdgbody.clip(0, 1)).sqrt) + (ring * sdgbody.clip(0, 1).sqrt);
		ReplaceOut.ar(out, (ring * 0.5).softclip * 2);
	}).add;
};
)
