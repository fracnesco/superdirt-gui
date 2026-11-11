// modules/sdgroll.scd - a roll from one hit: it plays its own start again, faster and faster.
//
// category: repeat
//
// Autechre fed a drum machine into a delay that retriggered itself. this keeps
// the first quarter second of the event's sound and plays it again sdgroll
// times: the first repeat sdgrolltime cycles after the hit, every next gap
// sdgrollcurve times the last (under 1 the roll speeds up, over 1 it slows
// down, 1 is a ratchet). each repeat is a little quieter, and sdgrollpitch
// moves each one by that many semitones. the event lasts as long as the roll.
//
// parameters:
//   sdgroll       1 to 16             how many repeats
//   sdgrolltime   0.01 to 1 cycles    the first gap: 0.0625 is a 16th, 0.125 an 8th
//   sdgrollcurve  0.5 to 1.5          each gap is the last times this
//   sdgrollpitch  -12 to 12 st        each repeat moves by this
//
//   d1 $ s "sn" # pF "sdgroll" 8 # pF "sdgrollcurve" 0.8
//   d1 $ s "bd ~ ~ cp" # pF "sdgroll" 3 # pF "sdgrolltime" (1/16) # pF "sdgrollcurve" 1
//   d1 $ s "drum:2" # pF "sdgroll" 12 # pF "sdgrollcurve" 0.85 # pF "sdgrollpitch" 1
(
var numChannels = ~dirt.numChannels;
var grab = 0.25;	// seconds of the hit that each repeat plays
var longest = 4;	// seconds: the last repeat starts by then, or is dropped

~dirt.addModule('sdgroll', { |dirtEvent|
	var count = (~sdgroll ? 4).asInteger.clip(1, 16);
	var gap = ((~sdgrolltime ? 0.0625).clip(0.01, 1) / (~cps ? 0.5)).clip(0.005, longest);
	var curve = (~sdgrollcurve ? 0.8).clip(0.5, 1.5);
	var span = min((1..count).sum { |i| gap * (curve ** (i - 1)) }, longest);
	var dry = ~sustain;
	~sustain = max(~sustain, span + grab);	// read later by dirt_gate: let the roll finish
	dirtEvent.sendSynth("sdgroll" ++ numChannels, [
		sdgroll: count,
		sdgrollgap: gap,
		sdgrollcurve: curve,
		sdgrollpitch: ~sdgrollpitch ? 0,
		sdgrollspan: span,
		sdgdrylen: dry,
		out: ~out
	])
}, { ~sdgroll.notNil });

// a delay line, not a buffer: repeat k reads the input from its start, delay = now - rate x (time since k)
SynthDef("sdgroll" ++ numChannels, { |out, sdgdrylen = 1, sdgroll = 4, sdgrollgap = 0.125, sdgrollcurve = 0.8, sdgrollpitch = 0,
	sdgrollspan = 1|
	var sig = In.ar(out, numChannels)
		// a sample would start again once its own length is over (the gate now lives longer): stop it there
		* EnvGen.ar(Env([1, 1, 0], [(sdgdrylen - 0.004).max(0), 0.003]));
	var count = sdgroll.clip(1, 16);
	var curve = sdgrollcurve.clip(0.5, 1.5);
	var step = sdgrollpitch.clip(-12, 12).midiratio;
	// a trigger now (level 0: the hit itself plays) and one per repeat, each gap curve x the last
	var trig = TDuty.ar(
		Dseq([sdgrollgap, Dgeom(sdgrollgap * curve, curve, count - 1), 1000]),
		0,
		Dseq([0, Dgeom(0.85, 0.85, count), 0])
	);
	var level = Latch.ar(trig, trig);
	var rate = Latch.ar(Demand.ar(trig, 0, Dgeom(step, step, inf)), trig);
	var now = Sweep.ar(0, 1);
	var since = Sweep.ar(trig, 1);
	var maxDelay = sdgrollspan.clip(0.01, longest) + grab;
	var play = DelayC.ar(sig, maxDelay, (now - (rate * since)).clip(0, maxDelay));
	// each repeat: a short fade in, the grab, a fade out; the next one restarts it
	var env = EnvGen.ar(Env([0, 1, 1, 0], [0.001, grab - 0.011, 0.01]), trig);
	ReplaceOut.ar(out, ((sig + (play * env * level)) * 0.5).softclip * 2);
}, [\kr, \kr, \kr, \kr, \kr, \kr, \ir]).add;
)
