// modules/sdgchrome.scd - turns a hit to metal: four short combs, wobbling, squashed.
//
// category: resonator
//
// SOPHIE's metal came from feeding a short hit into short delays with a lot of
// feedback and then squashing everything. here four combs ring at sdgchromefreq
// and at 1.41, 1.73 and 2.24 times it - no two in a harmonic ratio, so it sounds
// struck, not plucked - their delay times wobble a little, like a chorus, and
// the sum is driven into a soft clip. sdgchromefb is how long the metal rings; the event lasts
// at least that long.
//
// parameters:
//   sdgchrome      0 to 1          how much metal against the dry hit
//   sdgchromefreq  50 to 2000 Hz   the lower comb's pitch
//   sdgchromefb    0 to 1          how long it rings: 0 is 50 ms, 1 is 3 s
//
//   d1 $ s "sn*4" # pF "sdgchrome" 0.6
//   d1 $ s "hh*8" # pF "sdgchrome" 0.8 # pF "sdgchromefreq" "<300 450>" # pF "sdgchromefb" 0.3
//   d1 $ s "bd*2" # pF "sdgchrome" 0.5 # pF "sdgchromefreq" 90 # pF "sdgchromefb" 0.8
(
var numChannels = ~dirt.numChannels;

~dirt.addModule('sdgchrome', { |dirtEvent|
	var ring = 0.05 * (60 ** (~sdgchromefb ? 0.5).clip(0, 1));
	var dry = ~sustain;
	~sustain = max(~sustain, ring);	// read later by dirt_gate: let it ring out
	dirtEvent.sendSynth("sdgchrome" ++ numChannels, [
		sdgchrome: ~sdgchrome,
		sdgchromefreq: ~sdgchromefreq ? 220,
		sdgchromefb: ~sdgchromefb ? 0.5,
		sdgdrylen: dry,
		out: ~out
	])
}, { ~sdgchrome.notNil });

SynthDef("sdgchrome" ++ numChannels, { |out, sdgdrylen = 1, sdgchrome = 0.5, sdgchromefreq = 220, sdgchromefb = 0.5|
	var sig = In.ar(out, numChannels)
		// a sample would start again once its own length is over (the gate now lives longer): stop it there
		* EnvGen.ar(Env([1, 1, 0], [(sdgdrylen - 0.004).max(0), 0.003]));
	var freq = sdgchromefreq.clip(50, 2000);
	var ring = 0.05 * (60 ** sdgchromefb.clip(0, 1));
	var input = HPF.ar(sig, freq * 0.5);
	// four combs on square roots of primes: no two in a harmonic ratio, so it sounds struck
	var wet = [1, 2.sqrt, 3.sqrt, 5.sqrt].collect { |ratio, i|
		var wobble = SinOsc.ar(0.5 + (i * 0.37), Rand(0, 2pi)) * 0.0002;
		CombC.ar(input, 0.03, ((freq * ratio).reciprocal + wobble).clip(0.0003, 0.03), ring / ratio.sqrt)
	}.sum;
	var amount = sdgchrome.clip(0, 1);
	// a comb's level grows with its ring time over its delay: level it by both
	wet = wet * (freq * ring).sqrt.reciprocal * 2;
	wet = (wet * 2).tanh / 1.5;
	wet = LeakDC.ar(wet);
	// equal power, and exactly 0 at the ends (XFade2 leaves 1e-17 of the other side: denormals)
	wet = (sig * (1 - amount).sqrt) + (wet * amount.sqrt);
	ReplaceOut.ar(out, (wet * 0.5).softclip * 2);
}).add;
)
