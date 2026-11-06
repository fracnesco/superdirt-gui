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
	~dirt.start(57120, 0 ! 4);    // twelve orbits, d1 .. d12, all on channels 1-2
	s.sync;
	~sdg = SuperDirtGUI(~dirt);    // synths/, modules/, players/, effects/ into ~dirt
	"SuperDirtGUI: ready - %".format(~sdg).postln;
};
)

// ---- 2. what got loaded
~sdg.synthNames;          // -> [ sdgsine, sdgacid, sdgchoir, ... ]
~sdg.moduleNames;         // -> [ sdgcomb ]
~sdg.samplePlayerNames;   // -> [ sdgwarp ]
~sdg.globalEffectNames;   // -> [ global_sdgspace ]

// ---- 3. the window: a strip per orbit - orbit 0 is where d1 plays - with its
//         inserts, meters and fader; the effects of one orbit, the sound
//         explorer, and what was loaded
~sdg.gui;

// ---- 4. from tidal - examples/tidal/lessons/ has a file for each of these
//   d1 $ s "sdgsine" # n "0 7 12" # sustain 0.4
//   d1 $ s "bd*4" # pF "sdgcomb" 0.4 # pF "sdgres" 2
//   d1 $ s "breaks152" # pF "sdgwarp" 1 # speed 0.5 # pF "sdgpitch" 1.5
//   d1 $ s "bd*4" # pF "sdgspace" 0.5 # pF "sdgtime" 0.375

// ---- 5. the same events from sclang, no tidal needed
~dirt.orbits[0].valuePairs([\s, \sdgsine, \n, 7]);
~dirt.orbits[0].valuePairs([\s, \bd, \sdgcomb, 0.4, \sdgres, 2]);
~dirt.orbits[0].valuePairs([\s, \breaks152, \n, 0, \sdgwarp, 1, \speed, 0.5, \sdgpitch, 1.5]);
~dirt.orbits[0].valuePairs([\s, \bd, \sdgspace, 0.5, \sdgtime, 0.375]);

// ---- 6. the mixer from code - the window follows
~sdg.setFader(0, 0.5);    // orbit 0 (d1) at half
~sdg.setMute(0, true);    // orbit 0 silent
~sdg.setMute(0, false);
~sdg.setSolo(1, true);    // only orbit 1 (d2)
~sdg.setSolo(1, false);

// inserts, before the orbit's fader - + insert on a strip does the same.
// they need the ParaEQ, Compressor and Insight extensions
~eq = ~sdg.addInsert(0, \eq);           // an eq on orbit 0
~eq.gui;                                // its own window
~comp = ~sdg.addInsert(0, \compressor); // after the eq
~comp.unit.threshold = -24; ~comp.unit.ratio = 4;
~sdg.moveInsert(~comp, 0);              // before it now
~comp.enabled = false;                  // bypassed
~sdg.addInsert(0, \insight).gui;        // what the orbit sounds like after both
// a VST plugin (the VSTPlugin extension): + insert -> VST plugin... in the window, or
~sdg.vstPlugins({ |list| list.collect(_.name).postln });   // the VST effects there are
~vst = ~sdg.addInsert(0, \vst, plugin: "ValhallaSupermassive");
~vst.gui;                               // its own editor, once it has loaded
~sdg.removeInserts(0);

// ---- 7. the sample editor on one sample: trim, gain, dc, fades, save
~sdg.edit(\bd, 3);        // bank, index; plays through orbit 0
// SDGSampleEditor.forPath("~/somewhere/kick.wav", ~sdg);   // a file outside the library

// ---- 8. your own: new... in the window's modules tab copies an example under a
//         name you give and opens it in the IDE; save it and the window reads it
//         again. edit... opens the file a name came from. the same from code:
~sdg.newFile(\players, \mywarp);          // players/mywarp.scd, from the example, loaded
~sdg.nameProblem(\modules, \room);        // why a name will not do -> 'room' is taken ...
~sdg.sourceFile(\players, \mywarp);       // the file a name came from
~sdg.loadFile("players/mywarp.scd");      // edited it with the window closed? read it again
~sdg.reload;                              // or every file in the four folders

// ---- 9. more sample banks: one subfolder per bank, appended to what is loaded.
//         the drum banks rendered from the drum voices (sdgbd, sdgsd, sdghh, sdgperc,
//         sdgfx, sdgresamp, docs/drums.md) came in with ~sdg itself
~sdg.loadSamples("~/more-samples");
// ~dirt.postSampleInfo;

// ---- 10. take the add-on out again (superdirt keeps running)
~sdg.free;
