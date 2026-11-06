// modules/sdgboom.scd - a tuned sub under every hit, like an 808's.
//
// it listens for the hits in the event's sound and answers each with a sine
// at sdgboomfreq, as loud as the hit and dying in sdgboomdecay seconds. it
// starts half an octave up and falls onto its note in 20 ms, which is the
// thump. a hat gets a kick under it; a break gets a sub on every hit it finds
// (Ableton's Drum Buss calls this Boom). the event lasts at least as long as
// the boom.
//
// parameters:
//   sdgboom       0 to 1          how loud the sub is
//   sdgboomfreq   25 to 200 Hz    its pitch: 41 is E, 49 G, 55 A
//   sdgboomdecay  0.05 to 3 s     how long it rings, to -60 dB
//
//   d1 $ s "hh*4" # pF "sdgboom" 0.7
//   d1 $ s "bd*4" # pF "sdgboom" 0.5 # pF "sdgboomfreq" 41 # pF "sdgboomdecay" 1.5
//   d1 $ s "breaks125" # legato 1 # pF "sdgboom" 0.4 # pF "sdgboomfreq" "<55 49>"
(
var numChannels = ~dirt.numChannels;

~dirt.addModule('sdgboom', { |dirtEvent|
	var decay = (~sdgboomdecay ? 0.5).clip(0.05, 3);
	var dry = ~sustain;
	~sustain = max(~sustain, decay);	// read later by dirt_gate: let it ring out
	dirtEvent.sendSynth("sdgboom" ++ numChannels, [
		sdgboom: ~sdgboom,
		sdgboomfreq: ~sdgboomfreq ? 50,
		sdgboomdecay: decay,
		sdgdrylen: dry,
		out: ~out
	])
}, { ~sdgboom.notNil });

SynthDef("sdgboom" ++ numChannels, { |out, sdgdrylen = 1, sdgboom = 0.5, sdgboomfreq = 50, sdgboomdecay = 0.5|
	var sig = In.ar(out, numChannels)
		// a sample would start again once its own length is over (the gate now lives longer): stop it there
		* EnvGen.ar(Env([1, 1, 0], [(sdgdrylen - 0.004).max(0), 0.003]));
	var mono = sig.asArray.sum / numChannels;
	var fast = Amplitude.ar(mono, 0.0005, 0.03);
	var slow = Amplitude.ar(mono, 0.01, 0.2);
	// a hit: the fast follower jumps past twice the slow one, at most every 60 ms
	var hit = Trig1.ar((fast > (slow * 2)) * (fast > 0.003), 0.06);
	var late = DelayN.ar(hit, 0.004, 0.003);
	var level = Latch.ar(fast, late).clip(0, 1);
	var freq = sdgboomfreq.clip(25, 200);
	var sweep = EnvGen.ar(Env([1.4, 1, 1], [0.02, 1], -4), hit);
	var env = EnvGen.ar(Env.perc(0.002, sdgboomdecay.clip(0.05, 3), 1, -4), hit);
	var sub = SinOsc.ar(freq * sweep) * env * level;
	sub = HPF.ar(sub, 20) * sdgboom.clip(0, 1) * 1.6;
	ReplaceOut.ar(out, ((sig + sub) * 0.5).softclip * 2);
}).add;
)
