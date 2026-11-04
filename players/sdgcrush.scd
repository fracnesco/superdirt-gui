// players/sdgcrush.scd - a sample player: lo-fi in the read pointer.
//
// the pointer may only stand on every sdgcrush-th frame, so the file is read as
// a staircase: sample-rate reduction with the aliasing that goes with it,
// and speed moves through the stairs. 4 is old sampler, 64 is broken, 1024 is a
// slow sequence of frozen levels.
//
// parameters:
//   sdgcrush  1 to 1024 frames  the step the pointer stands on: 4 old sampler, 64 broken
//
//   d1 $ s "breaks125" # pF "sdgcrush" 16
//   d1 $ s "numbers:3" # pF "sdgcrush" "4 16 64 256"
(
~sdg.addSamplePlayer(\sdgcrush, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgcrush = 16|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase.round(max(sdgcrush, 1)), 0, 1);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgcrush.notNil });
)
