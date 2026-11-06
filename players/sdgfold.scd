// players/sdgfold.scd - a sample player: back and forth inside a window.
//
// the reading bounces between the walls of a window at its start, sdgfold of the
// trim wide (0 to 1): forwards, backwards, forwards, at speed. a wide
// window is a palindrome, a narrow one a buzz. sdghold, in cycles, makes the event last
// that long however short the sample is.
//
// parameters:
//   sdgfold  0 to 1            how wide the window is, of the trim
//   sdghold  0.1 to 16 cycles  makes the event last that long
//
//   d1 $ s "numbers:3" # begin 0.1 # pF "sdgfold" 0.2
//   d1 $ s "breaks125" # begin 0.25 # pF "sdgfold" "0.02 0.1"
//   d1 $ s "cp" # pF "sdgfold" 0.01 # pF "sdghold" 1
(
~sdg.addSamplePlayer(\sdgfold, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgfold = 0.1|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var frames = max(BufFrames.ir(bufnum), 1);
		var wide = max(absdif(begin, end) * frames * sdgfold.clip(0, 1), 2);
		// the window sits where the reading starts: the bottom forwards, the top backwards
		var low = if(speed < 0, max(begin, end) * frames - wide, min(begin, end) * frames);
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase.fold(low, low + wide), 0, 4);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgfold.notNil }, prepare: { ~sdghold !? { ~sustain = ~sdghold / ~cps } });
)
