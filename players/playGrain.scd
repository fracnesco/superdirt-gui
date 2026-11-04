// players/playGrain.scd - a sample player: time and pitch apart, through Warp1.
//
// a sample player is another way of reading a sample. SuperDirt plays every
// sample with its own dirt_sample synth; a player takes over the events that
// ask for it - here every event that carries playGrain - and reads the buffer
// its own way. this one hands it to Warp1: speed still sets how fast the
// sample is read, and sdgpitch sets the pitch on its own, so a sample can be
// slowed down without dropping, or transposed without changing length.
//
// the SynthDef function declares the nine standard controls first, in this
// order - out, bufnum, sustain, begin, end, speed, endSpeed, freq, pan - and
// anything after them is sent only when the event carries it. SDGBufRate and
// SDGBufPhase give the rate and the begin-to-end pointer dirt_sample uses,
// so the trim, speed, accelerate and n mean what they always mean. one call
// builds playGrain_1_<channels> and playGrain_2_<channels>, for mono and stereo
// files, and puts the player in front of the ones added before it.
//
// parameters:
//   playGrain  any number   switches the player on
//   sdgpitch   0.25 to 4 x  transposes without changing the length
//
//   d1 $ s "breaks152" # pF "playGrain" 1 # speed 0.5
//   d1 $ s "breaks152" # pF "playGrain" 1 # pF "sdgpitch" 1.5
//   d1 $ s "breaks152" # pS "player" "playGrain"       -- the same, by name
//
// new... in the window's modules tab copies a file like this one under another
// name. while the window is open, a file is read again when you save it.
(
~sdg.addSamplePlayer(\playGrain, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0, sdgpitch = 1|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var sound = Warp1.ar(
			numChannels: sampleNumChannels,
			bufnum: bufnum,
			pointer: phase / max(BufFrames.ir(bufnum), 1), // Warp1 reads its position as 0 .. 1 of the buffer
			freqScale: sdgpitch,
			windowSize: 0.1,
			overlaps: 8,
			windowRandRatio: 0.1,
			interp: 2
		);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~playGrain.notNil });
)
