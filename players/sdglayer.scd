// players/sdglayer.scd - a sample player: any drum sample, with a synthesized drum under it.
//
// the sample plays as it is, and a layer joins it at its first hit: a sine that drops onto
// its pitch, for the weight a thin sample lacks, and a burst of filtered noise, for a tail
// it does not have. sdglayer is how much of the layer, scaled to the sample's own peak, so
// it sits as well under a quiet 808 file as under a normalized one: at 1 the layer peaks
// where the sample does, at 0 the sample is heard alone, level for level as SuperDirt
// plays it. sdgclick shapes the sample's own attack: below 0 it softens it, above 0 it
// sharpens it, attack up and body down. the event lasts as long as the layer rings, even
// past a short sample, unless legato chokes it. note, speed and accelerate move the sample
// and the layer's pitch together, as for a sample played faster.
//
// parameters:
//   sdglayer  0 to 1              how much of the synthesized layer, against the sample's peak
//   sdgfreq   20 to 500 Hz        the sine's pitch at note 0: 50 a kick, 180 a snare's shell
//   sdgpunch  0 to 48 semitones   how far above its pitch the sine starts
//   sdgbend   0.001 to 1 s        how long that drop takes
//   sdgdecay  0.02 to 3 s         how long the sine rings (to -60 dB)
//   sdgsnap   0 to 1              sine against noise: 0 all sine, 1 all noise
//   sdgnoise  0.01 to 2 s         how long the noise rings (to -60 dB)
//   sdgtone   0 to 1              the noise, dark to bright: 400 Hz to 3 kHz up to 5 to 16 kHz
//   sdgclick  -1 to 1             the sample's attack: -1 soft (-12 dB), 0 as it is, 1 sharp
//
//   d1 $ s "bd*4" # pF "sdglayer" 0.6 # pF "sdgfreq" 48 # pF "sdgdecay" 0.6
//   d1 $ s "~ sn" # pF "sdglayer" 0.5 # pF "sdgsnap" 0.8 # pF "sdgfreq" 180
//   d1 $ s "808bd:3*4" # pF "sdglayer" "0 0.5" # pF "sdgclick" 0.7
//   d1 $ s "hh*8" # pF "sdglayer" 0 # pF "sdgclick" "-1 1"      -- the attack alone
(
~sdg.addSamplePlayer(\sdglayer, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0,
		sdglayer = 0.5, sdgfreq = 55, sdgpunch = 12, sdgbend = 0.04, sdgdecay = 0.3,
		sdgsnap = 0.2, sdgnoise = 0.15, sdgtone = 0.5, sdgclick = 0, sdglayerlen = 0|
		// sdglayerlen is the sample's own length when prepare has made the event longer:
		// accelerate still runs over the sample, not over the layer's ring
		var rate = SDGBufRate.kr(speed, endSpeed, Select.kr(sdglayerlen > 0, [sustain, sdglayerlen]), freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var frames = max(BufFrames.ir(bufnum), 1);
		var sr = BufSampleRate.ir(bufnum);
		// passes through the trim per second: the reader would go round again in a longer
		// event, so the sample stops after one, with a 2 ms fade
		var passes = (rate.abs * sr / (absdif(begin, end) * frames)).clip(1e-4, 1e5);
		var once = ((1 - Sweep.ar(0, passes)) / passes / 0.002).clip(0, 1);
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase, 0, 4).asArray;

		// the attack: two followers on a reading 1.5 ms ahead of the one heard, so the gain
		// moves with the attack, not after it. their difference is the attack in dB: the first
		// 5 to 10 ms of a drum. the release is slow enough that a kick's 50 Hz does not read
		// as an attack every half cycle (under 3 dB of ripple, the threshold)
		var ahead = (end - begin).sign * rate.abs * sr * 0.0015;
		var look = BufRd.ar(sampleNumChannels, bufnum, phase + ahead, 0, 1).asArray.collect(_.abs).sum;
		var fast = Amplitude.ar(look, 0.0003, 0.15).max(1e-4);
		var slow = Amplitude.ar(look, 0.015, 0.15).max(1e-4);
		var attack = ((fast.ampdb - slow.ampdb - 3) / 6).clip(0, 1);
		var click = sdgclick.clip(-1, 1);
		// sharper: the attack up to +4 dB, the body down to -8 dB; softer: the attack down to
		// -12 dB. 0 is exactly 0 dB, so the sample is untouched
		var shapeDb = (click.max(0) * ((attack * 12) - 8)) + (click.min(0) * attack * 12);

		// the layer starts where the sample first passes -40 dB, and follows the sample's
		// peak so far: its attack is the sample's attack, its level the sample's level
		var heard = sound.collect(_.abs).reduce(\max);
		var trig = SetResetFF.ar(heard > 0.01, 0);
		var level = RunningMax.ar(heard);
		var tune = rate.abs.clip(0.0625, 16);
		var drop = EnvGen.ar(Env([1, 0], [sdgbend.clip(0.001, 1)], -7), trig);
		var pitch = sdgfreq.clip(20, 500) * tune * (1 + ((sdgpunch.clip(0, 48).midiratio - 1) * drop));
		var body = sin(Sweep.ar(trig, pitch) * 2pi)
			* EnvGen.ar(Env([0, 1, 0.001, 0], [0.0005, sdgdecay.clip(0.02, 3), 0.005], [0, \exp, 0]), trig);
		var tone = sdgtone.clip(0, 1);
		var noise = LPF.ar(HPF.ar(WhiteNoise.ar, tone.linexp(0, 1, 400, 5000)), tone.linexp(0, 1, 3000, 16000))
			* EnvGen.ar(Env([0, 1, 0.001, 0], [0.001, sdgnoise.clip(0.01, 2), 0.005], [0, \exp, 0]), trig);
		var snap = sdgsnap.clip(0, 1);
		var layer = ((body * (1 - snap)) + (noise * snap * 1.4)) * level * sdglayer.clip(0, 1);

		sound = (sound * shapeDb.dbamp * once) + layer;
		Out.ar(out, DirtPan.ar(sound.unbubble, numChannels, pan))
	}
}, { ~sdglayer.notNil }, prepare: {
	// a short sample would end the event before the layer has rung out: the event lasts
	// what the layer needs, unless the pattern chokes it with legato. backwards, the hit
	// comes at the end of the sample, so the layer needs its time after that
	var need;
	if((~sdglayer ? 0) > 0 and: { ~legato.isNil }) {
		need = max((~sdgdecay ? 0.3).clip(0.02, 3), if((~sdgsnap ? 0.2) > 0) { (~sdgnoise ? 0.15).clip(0.01, 2) } { 0 });
		if((~speed ? 1) < 0) { need = need + ~sustain };
		if(need > ~sustain) { ~sdglayerlen = ~sustain; ~sustain = need };
	};
});
)
