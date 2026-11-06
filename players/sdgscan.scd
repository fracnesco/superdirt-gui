// players/sdgscan.scd - a sample player: the sample becomes an oscillator.
//
// two overlapping readers repeat a tiny piece of the sample at the pitch sdgscan
// asks for, in Hz, while the piece crawls from begin to end over the event: a
// drum break turns into a pitched, shifting wavetable. speed sets how slowly it
// crawls, sdgstep how many times a second it moves. sdghold, in cycles, makes the event last that long however short
// the sample is.
//
// parameters:
//   sdgscan  20 to 2000 Hz     the pitch of the repeating piece
//   sdgstep  1 to 40 per s     how often the piece moves on: low is stepped, high is a blur
//   sdghold  0.1 to 16 cycles  makes the event last that long
//
//   d1 $ loopAt 2 (chop 4 $ s "breaks152") # pF "sdgscan" "110 165"
//   d1 $ s "numbers:3" # end 0.4 # pF "sdgscan" 110 # pF "sdghold" 1
//   d1 $ s "hh*2" # end 0.12 # pF "sdgscan" "220 330" # pF "sdghold" 0.5
(
~sdg.addSamplePlayer(\sdgscan, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0,
		sdgscan = 110, sdgstep = 12|
		var frames = max(BufFrames.ir(bufnum), 1);
		var repeat = max(sdgscan, 1);
		var piece = 2 * BufSampleRate.ir(bufnum) / repeat; // two cycles: two readers half apart repeat every one
		// the piece stands still between steps: it has to repeat exactly to have a pitch
		var position = Latch.ar(Line.ar(begin, end, sustain) * frames, Impulse.ar(sdgstep));
		var sound = [0, 1].sum { |i|
			var ramp = LFSaw.ar(repeat * 0.5, i).range(0, 1);
			var start = Latch.ar(position, (HPZ1.ar(ramp) < 0) + Impulse.ar(0));
			BufRd.ar(sampleNumChannels, bufnum, start + (ramp * piece), 1, 4) * (ramp * pi).sin.squared
		};
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgscan.notNil }, prepare: { ~sdghold !? { ~sustain = ~sdghold / ~cps } });
)
