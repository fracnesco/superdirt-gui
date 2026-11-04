// players/sdgsmear.scd - a sample player: wash it into a cloud.
//
// Warp1 keeps the pitch while speed stretches the time, then sdgsmear (0 to 1)
// spreads each moment across its neighbouring frequencies and scrambles the
// phases. a low speed is a very long stretch; sdgpitch transposes, as on
// sdgwarp. sdghold, in cycles, makes the event last that long however short the sample is.
// comes out about 93 ms late, the length of its FFT window.
//
// parameters:
//   sdgsmear  0 to 1            how far each moment spreads and scrambles
//   sdgpitch  0.25 to 4 x       transposes
//   sdghold   0.1 to 16 cycles  makes the event last that long
//
//   d1 $ s "numbers:3" # pF "sdgsmear" 0.3
//   d1 $ s "breaks152/8" # pF "sdgsmear" 0.8 # speed 0.05
//   d1 $ s "numbers:3" # pF "sdgsmear" 0.5 # pF "sdghold" 2
(
~sdg.addSamplePlayer(\sdgsmear, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0,
		sdgsmear = 0.5, sdgpitch = 1|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var amount = sdgsmear.clip(0, 1);
		var warped = Warp1.ar(
			numChannels: sampleNumChannels,
			bufnum: bufnum,
			pointer: phase / max(BufFrames.ir(bufnum), 1),
			freqScale: sdgpitch,
			windowSize: 0.1,
			overlaps: 8,
			windowRandRatio: 0.2,
			interp: 2
		);
		var late = 4096 / SampleRate.ir;
		var plain = DelayN.ar(BufRd.ar(sampleNumChannels, bufnum, phase, 0, 4).asArray, late, late);
		var sound = warped.asArray.collect { |channel, i|
			var chain = PV_MagSmear(FFT(LocalBuf(4096), channel), amount * 64);
			var wet = IFFT(PV_Diffuser(chain, Impulse.kr(amount * 20)));
			// smearing changes the level by a factor of 5 or more, by material: back to the plain read's
			(wet * (RunningSum.rms(plain[i], 2048) / max(RunningSum.rms(wet, 2048), 1e-4)).clip(0, 8).lag(0.05)).tanh
		};
		Out.ar(out, DirtPan.ar(sound.unbubble, numChannels, pan))
	}
}, { ~sdgsmear.notNil }, prepare: { ~sdghold !? { ~sustain = ~sdghold / ~cps } });
)
