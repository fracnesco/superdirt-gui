// modules/sdgdive.scd - the hit dives in pitch, like a record stopped by hand.
//
// the event's sound goes through a delay that grows longer as it plays, so it
// is read slower and slower: by sdgdivetime seconds it is sdgdive semitones
// down, and it stays there, stretched, until the delay is full (half a
// second). a hat becomes a zap, a kick a tape stop. a delay can only make
// things later, so a dive only goes down.
//
// parameters:
//   sdgdive      0 to 48 st     how far down it ends
//   sdgdivetime  0.01 to 1 s    how long it takes to get there
//
//   d1 $ s "hh*8" # pF "sdgdive" 24 # pF "sdgdivetime" 0.05
//   d1 $ s "bd*4" # pF "sdgdive" "0 12" # pF "sdgdivetime" 0.3
//   d1 $ s "breaks125" # legato 1 # pF "sdgdive" 36 # pF "sdgdivetime" 1
(
var numChannels = ~dirt.numChannels;
var maxDelay = 0.5;

~dirt.addModule('sdgdive', { |dirtEvent|
	var dry = ~sustain;
	~sustain = ~sustain + maxDelay;	// read later by dirt_gate: what was delayed still has to come out
	dirtEvent.sendSynth("sdgdive" ++ numChannels, [
		sdgdive: ~sdgdive,
		sdgdivetime: ~sdgdivetime ? 0.15,
		sdgdrylen: dry,
		out: ~out
	])
}, { ~sdgdive.notNil });

SynthDef("sdgdive" ++ numChannels, { |out, sdgdrylen = 1, sdgdive = 12, sdgdivetime = 0.15|
	var sig = In.ar(out, numChannels)
		// a sample would start again once its own length is over (the gate now lives longer): stop it there
		* EnvGen.ar(Env([1, 1, 0], [(sdgdrylen - 0.004).max(0), 0.003]));
	// how far down, in semitones, now: falls in sdgdivetime, then stays
	var semis = EnvGen.ar(Env([0, sdgdive.clip(0, 48)], [sdgdivetime.clip(0.01, 1)], -2));
	var ratio = (semis.neg).midiratio;
	// read at ratio: the delay grows by 1 - ratio every second
	var delay = Integrator.ar((1 - ratio) * SampleDur.ir, 1).clip(0, maxDelay);
	// when the delay is full the read would jump back to normal speed: fade out first
	var full = (delay < (maxDelay - 0.02)).lag(0.01);
	ReplaceOut.ar(out, DelayC.ar(sig, maxDelay, delay) * full);
}).add;
)
