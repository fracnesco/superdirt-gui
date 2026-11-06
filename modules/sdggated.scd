// modules/sdggated.scd - gated reverb: a big room cut off dead after every hit.
//
// the 80s snare: each hit goes into a dense, bright room, and a gate that the
// hit opens shuts it sdggatedtime seconds later, in 10 ms. the room is the
// event's own, so every hit gets its own gate: a long one on the snare and a
// short one on the toms in one pattern. the event lasts at least as long as
// the gate is open.
//
// parameters:
//   sdggated      0 to 1        how much room against the dry hit
//   sdggatedtime  0.05 to 1 s   how long the gate stays open after a hit
//
//   d1 $ s "~ sn" # pF "sdggated" 0.6
//   d1 $ s "bd sn:3 bd sn:3" # pF "sdggated" "0 0.7" # pF "sdggatedtime" 0.4
//   d1 $ s "sdgtom*4" # n "0 1 2 3" # pF "sdggated" 0.5 # pF "sdggatedtime" 0.15
(
var numChannels = ~dirt.numChannels;

~dirt.addModule('sdggated', { |dirtEvent|
	var hold = (~sdggatedtime ? 0.25).clip(0.05, 1);
	var dry = ~sustain;
	~sustain = max(~sustain, hold + 0.02);	// read later by dirt_gate: keep the room open
	dirtEvent.sendSynth("sdggated" ++ numChannels, [
		sdggated: ~sdggated,
		sdggatedtime: hold,
		sdgdrylen: dry,
		out: ~out
	])
}, { ~sdggated.notNil });

SynthDef("sdggated" ++ numChannels, { |out, sdgdrylen = 1, sdggated = 0.5, sdggatedtime = 0.25|
	var sig = In.ar(out, numChannels)
		// a sample would start again once its own length is over (the gate now lives longer): stop it there
		* EnvGen.ar(Env([1, 1, 0], [(sdgdrylen - 0.004).max(0), 0.003]));
	var mono = sig.asArray.sum / numChannels;
	var fast = Amplitude.ar(mono, 0.0005, 0.03);
	var slow = Amplitude.ar(mono, 0.01, 0.2);
	var hit = Trig1.ar((fast > (slow * 2)) * (fast > 0.003), 0.03);
	var gate = EnvGen.ar(Env([0, 1, 1, 0], [0.001, sdggatedtime.clip(0.05, 1), 0.01]), hit);
	// a dense room: big, bright, its tail far longer than the gate
	var room = FreeVerb.ar(HPF.ar(sig, 150), 1, 0.95, 0.15) * 6;
	var amount = sdggated.clip(0, 1);
	room = (room * gate).tanh;
	ReplaceOut.ar(out, ((sig + (room * amount)) * 0.5).softclip * 2);
}).add;
)
