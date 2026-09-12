// superdirt-gui-demo.scd - a workshop startup, one block at a time.
// this is an editor document: evaluate the blocks in order, never .load it.

// ---- 1. server, superdirt, samples, orbits, then the add-on
(
s.options.numBuffers = 1024 * 256;   // room for the sample banks
s.options.memSize = 8192 * 32;
s.options.maxNodes = 1024 * 32;
s.options.numOutputBusChannels = 2;

s.waitForBoot {
	~dirt = SuperDirt(2, s);       // two output channels
	~dirt.loadSoundFiles;          // Dirt-Samples
	s.sync;
	~dirt.start(57120, 0 ! 12);    // twelve orbits, d1 .. d12, all on channels 1-2
	s.sync;
	~sdg = SuperDirtGUI(~dirt);    // synths/, modules/, effects/ into ~dirt
	"SuperDirtGUI: ready - %".format(~sdg).postln;
};
)

// ---- 2. what got loaded
~sdg.synthNames;          // -> [ sdgsine ]
~sdg.moduleNames;         // -> [ sdgcomb ]
~sdg.globalEffectNames;   // -> [ global_sdgspace ]
~sdg.gui;

// ---- 3. from tidal
//   d1 $ s "sdgsine" # n "0 7 12" # sustain 0.4
//   d1 $ s "bd*4" # pF "sdgcomb" 0.4 # pF "sdgres" 2
//   d1 $ s "bd*4" # pF "sdgspace" 0.5 # pF "sdgtime" 0.375

// ---- 4. the same events from sclang, no tidal needed
~dirt.orbits[0].valuePairs([\s, \sdgsine, \n, 7]);
~dirt.orbits[0].valuePairs([\s, \bd, \sdgcomb, 0.4, \sdgres, 2]);
~dirt.orbits[0].valuePairs([\s, \bd, \sdgspace, 0.5, \sdgtime, 0.375]);

// ---- 5. edited a file in synths/, modules/ or effects/? read them again
~sdg.reload;

// ---- 6. more sample banks: one subfolder per bank, appended to what is loaded
// ~sdg.loadSamples("~/more-samples");
// ~dirt.postSampleInfo;

// ---- 7. take the add-on out again (superdirt keeps running)
~sdg.free;
