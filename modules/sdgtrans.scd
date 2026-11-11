// modules/sdgtrans.scd - a transient designer: more or less attack, more or less body.
//
// category: dynamics
//
// two envelope followers on the event's sound, one fast and one slow to rise;
// where they differ is the attack, and sdgtrans turns it up or down, up to
// 15 dB either way. two more, slow and fast to fall, find the body after it,
// which sdgtransbody turns up or down by up to 18 dB. it listens to the shape,
// not the level, so a quiet hit gets the same treatment as a loud one (SPL's
// Transient Designer works this way). on a sample, the body is what was
// recorded: a dry kick has little to bring up.
//
// parameters:
//   sdgtrans      -1 to 1  the attack: 1 is +15 dB, -1 is -15 dB, 0 leaves it
//   sdgtransbody  -1 to 1  the body after the attack: 1 is +18 dB, -1 is -18 dB
//
//   d1 $ s "bd*4" # pF "sdgtrans" 1
//   d1 $ s "sn*4" # pF "sdgtrans" (-0.8) # pF "sdgtransbody" 0.7
//   d1 $ s "breaks125" # pF "sdgtrans" "<1 -1>" # legato 1
(
var numChannels = ~dirt.numChannels;

~dirt.addModule('sdgtrans', { |dirtEvent|
	dirtEvent.sendSynth("sdgtrans" ++ numChannels, [
		sdgtrans: ~sdgtrans,
		sdgtransbody: ~sdgtransbody ? 0,
		out: ~out
	])
}, { ~sdgtrans.notNil });

SynthDef("sdgtrans" ++ numChannels, { |out, sdgtrans = 0, sdgtransbody = 0|
	var sig = In.ar(out, numChannels);
	var mono = sig.asArray.sum.abs;
	// attack: same release, different rise
	var fastRise = Amplitude.ar(mono, 0.0002, 0.08);
	var slowRise = Amplitude.ar(mono, 0.02, 0.08);
	// body: same rise, different fall
	var fastFall = Amplitude.ar(mono, 0.0002, 0.03);
	var slowFall = Amplitude.ar(mono, 0.0002, 0.4);
	var attack = ((fastRise - slowRise) / (fastRise + 1e-5)).clip(0, 1);
	var body = ((slowFall - fastFall) / (slowFall + 1e-5)).clip(0, 1);
	var gainDb = (sdgtrans.clip(-1, 1) * 15 * attack) + (sdgtransbody.clip(-1, 1) * 18 * body);
	// below -80 dB there is nothing to shape: leave the floor alone
	gainDb = gainDb * (slowFall > -80.dbamp);
	ReplaceOut.ar(out, ((sig * gainDb.clip(-30, 18).dbamp) * 0.5).softclip * 2);
}).add;
)
