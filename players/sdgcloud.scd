// players/sdgcloud.scd - a sample player: a cloud of separate grains.
//
// sdgcloud grains a second, sdggrain seconds long, each with a place of its own:
// sdgscatter seconds to either side of the reading, sdgspread (0 to 1) of the
// stereo image, a sdgrev chance (0 to 1) of playing backwards. sdgpitch transposes every
// grain. eight grains sound at most, so density times length past 8 thins out.
// made of BufRd, not GrainBuf, which reads mono files only.
//
// parameters:
//   sdgcloud    1 to 200 per s  how many grains a second
//   sdggrain    0.005 to 0.5 s  how long each grain is
//   sdgscatter  0 to 2 s        how far to either side of the reading a grain lands
//   sdgspread   0 to 1          how much of the stereo image the grains cover
//   sdgrev      0 to 1          the chance a grain plays backwards
//   sdgpitch    0.25 to 4 x     transposes every grain
//
//   d1 $ s "numbers:3" # pF "sdgcloud" 30
//   d1 $ loopAt 2 $ s "breaks152" # pF "sdgcloud" 80 # pF "sdggrain" 0.03 # pF "sdgscatter" 0.5
//   d1 $ s "numbers:3" # end 0.4 # pF "sdgcloud" 12 # pF "sdgrev" 0.5 # pF "sdgpitch" "<1 1.5>"
(
~sdg.addSamplePlayer(\sdgcloud, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0,
		sdgcloud = 30, sdggrain = 0.08, sdgscatter = 0.1, sdgspread = 1, sdgrev = 0, sdgpitch = 1|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var fileRate = BufSampleRate.ir(bufnum);
		var pulse = Impulse.ar(sdgcloud);
		var voices = 8;
		var sound = voices.collect { |i|
			var trig = PulseDivider.ar(pulse, voices, i);
			var direction = 1 - (2 * (TRand.ar(0, 1, trig) < sdgrev));
			var start = Latch.ar(phase, trig) + (TRand.ar(-1, 1, trig) * sdgscatter * fileRate);
			var read = start + Sweep.ar(trig, sdgpitch * direction * fileRate);
			var window = EnvGen.ar(Env.sine(1), trig, timeScale: sdggrain);
			var place = (pan + (TRand.ar(-1, 1, trig) * sdgspread)).clip2(1);
			DirtPan.ar(BufRd.ar(sampleNumChannels, bufnum, read, 1, 4) * window, numChannels, place)
		}.sum;
		Out.ar(out, sound / (sdgcloud * sdggrain).clip(1, voices).sqrt) // the same level, thick or thin
	}
}, { ~sdgcloud.notNil });
)
