// =====================================================================
// SDGBufRate / SDGBufPhase - the playback rate and read pointer shared by
// the sample players (see SDGSamplePlayer and players/): a sweep from begin
// to end of the buffer at the event's speed, in frames, ready for BufRd.
// The same arithmetic SuperDirt's dirt_sample uses, taken out so a player
// can spend the rate on pitch and the phase on position independently.
//
//   var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
//   var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
//   BufRd.ar(sampleNumChannels, bufnum, phase, 0, 4);
// =====================================================================

SDGBufRate : UGen {
	// speed glides to endSpeed over sustain; freq = 60.midicps is unity, so ~n and ~note transpose
	*kr { |speed = 1, endSpeed = 1, sustain = 1, freq = 440|
		^Line.kr(speed, endSpeed, sustain) * (freq / 60.midicps)
	}
}

SDGBufPhase : UGen {
	// frame position sweeping from begin to end at rate.
	// BufSampleRate adjusts the rate if the sound file doesn't have the same rate as the soundcard
	*ar { |bufnum, rate = 1, begin = 0, end = 1, speed = 1|
		var numFrames = max(BufFrames.ir(bufnum), 1); // > 0 even when no file is read yet
		var sawrate = rate * BufSampleRate.ir(bufnum) / (absdif(begin, end) * numFrames);
		^(speed.sign * LFSaw.ar(sawrate, 1)).range(begin, end) * numFrames
	}
}
