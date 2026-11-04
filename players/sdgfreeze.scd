// players/sdgfreeze.scd - a sample player: stop the spectrum, let it ring.
//
// the sample plays, and when the reading gets sdgfreeze of the way through (0 to
// 1) its spectrum locks for the rest of the event. sdghold, in cycles, makes the
// event last that long however short the sample is: that is the drone.
// comes out about 46 ms late, the length of its FFT window.
//
// parameters:
//   sdgfreeze  0 to 1            how far through the reading the spectrum locks
//   sdghold    0.1 to 16 cycles  makes the event last that long: the drone
//
//   d1 $ s "numbers:3" # pF "sdgfreeze" 0.3 # pF "sdghold" 4
//   d1 $ s "breaks152/4" # pF "sdgfreeze" "<0.1 0.5>" # pF "sdghold" 4
(
~sdg.addSamplePlayer(\sdgfreeze, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgfreeze = 0.25|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var span = max(absdif(begin, end) * max(BufFrames.ir(bufnum), 1), 1);
		// 0.1 s at least: frozen before the first window is filled, it would hold silence
		var hold = (Sweep.kr(0, rate.abs * BufSampleRate.ir(bufnum) / span) > sdgfreeze) * (Sweep.kr(0, 1) > 0.1);
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase, 0, 4).asArray.collect { |channel|
			IFFT(PV_MagFreeze(FFT(LocalBuf(2048), channel), hold))
		};
		Out.ar(out, DirtPan.ar(sound.unbubble, numChannels, pan))
	}
}, { ~sdgfreeze.notNil }, prepare: { ~sdghold !? { ~sustain = ~sdghold / ~cps } });
)
