// players/sdgself.scd - a sample player: the sound moves its own read pointer.
//
// what comes out is added to where it reads next, up to sdgself seconds at full
// level, so quiet parts play straight and loud ones throw the pointer around
// inside the trim. the output cannot get louder than the file: only the
// position feeds back, never the level. small values grit, large ones shatter.
//
// parameters:
//   sdgself  0 to 0.2 s  how far the sound throws the pointer at full level
//
//   d1 $ s "breaks125" # pF "sdgself" 0.002
//   d1 $ s "numbers:3" # pF "sdgself" "<0.01 0.1>"
(
~sdg.addSamplePlayer(\sdgself, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgself = 0.02|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var frames = max(BufFrames.ir(bufnum), 1);
		var low = min(begin, end) * frames;
		var high = max(max(begin, end) * frames, low + 1);
		var pushed = phase + (LocalIn.ar(1) * sdgself * BufSampleRate.ir(bufnum));
		var sound = BufRd.ar(sampleNumChannels, bufnum, pushed.wrap(low, high), 0, 4);
		LocalOut.ar(sound.asArray.sum / sampleNumChannels);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgself.notNil });
)
