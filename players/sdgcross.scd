// players/sdgcross.scd - a sample player: one sample through another.
//
// sdgcross names a second sample, as a string. the event's own sample keeps its
// frequencies only where the second has them too, so a drum loop speaks with the
// rhythm of a voice. the two may have any number of channels; the second is
// read from its start, and round again if it is the shorter. a name that is not
// a loaded sample crosses the sample with itself. comes out about 46 ms late, the
// length of its FFT window.
//
// parameters:
//   sdgcross  a sample name  the second sample, as a string: "numbers:3"
//
//   d1 $ s "breaks125" # pS "sdgcross" "numbers:3"
//   d1 $ s "numbers:1 numbers:2" # pS "sdgcross" "breaks152"
(
var dirt = ~dirt;
var warned = Set.new;

~sdg.addSamplePlayer(\sdgcross, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgbuf2 = 0|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var channels = BufChannels.kr(sdgbuf2);
		var place = Phasor.ar(0, rate.abs * BufRateScale.kr(sdgbuf2), 0, max(BufFrames.kr(sdgbuf2), 1)).floor * channels;
		// Index, not BufRd: its table takes a file of any channel count, frame by frame
		var other = (Index.ar(sdgbuf2, place) + Index.ar(sdgbuf2, place + channels - 1)) * 0.5;
		var late = 2048 / SampleRate.ir;
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase, 0, 4).asArray.collect { |channel|
			var wet = IFFT(PV_MagMul(FFT(LocalBuf(2048), channel), FFT(LocalBuf(2048), other)));
			// multiplied spectra have no level of their own: back to the event's sample's
			(wet * (RunningSum.rms(DelayN.ar(channel, late, late), 2048) / max(RunningSum.rms(wet, 2048), 1e-4)).clip(0, 8).lag(0.05)).tanh
		};
		Out.ar(out, DirtPan.ar(sound.unbubble, numChannels, pan))
	}
}, { ~sdgcross.notNil }, prepare: {
	var name, index, event;
	~sdgcross !? {
		#name, index = ~sdgcross.asString.split($:);
		event = dirt.soundLibrary.getEvent(name.asSymbol, (index ? 0).asInteger);
	};
	~sdgbuf2 = event !? { event[\buffer] } ?? {
		if(~sdgcross.notNil and: { warned.includes(~sdgcross).not }) {
			warned.add(~sdgcross);
			"sdgcross: no sample '%', crossing the sample with itself".format(~sdgcross).warn;
		};
		~buffer
	};
});
)
