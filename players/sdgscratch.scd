// players/sdgscratch.scd - a sample player: a hand on the record.
//
// the read pointer wobbles around where it would be, sdgscratch times a second
// and sdgdepth seconds to either side. a small depth is tape wow; when
// the wobble outruns the reading the pointer runs backwards, and that is a scratch.
//
// parameters:
//   sdgscratch  0.1 to 40 per s  how many times a second the pointer wobbles
//   sdgdepth    0 to 0.5 s       how far to either side: small is tape wow
//
//   d1 $ s "numbers:3" # pF "sdgscratch" 6
//   d1 $ s "breaks125" # pF "sdgscratch" "<2 8>" # pF "sdgdepth" 0.1
//   d1 $ s "numbers:3" # pF "sdgscratch" 3 # pF "sdgdepth" 0.003    -- wow
(
~sdg.addSamplePlayer(\sdgscratch, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0,
		sdgscratch = 6, sdgdepth = 0.05|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var hand = SinOsc.ar(sdgscratch) * sdgdepth * BufSampleRate.ir(bufnum);
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase + hand, 0, 4);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgscratch.notNil });
)
