// =====================================================================
// SDGGroup - a group track, as in Ableton: orbits go into it instead of the
// master, and what they sum to runs through a chain of effects, sends to the
// returns, a fader with mute and solo, and out to the master.
//
//   g = ~sdg.addGroup([0, 1, 2]);          // drums: orbits 0, 1 and 2
//   ~sdg.addBusInsert(g, \compressor);     // one compressor on all three
//   ~sdg.setGroupSend(g, \A, 0.3);         // the whole kit into return A
//   ~sdg.setOrbitDest(3, g.name);          // orbit 3 joins it
//   ~sdg.setOrbitDest(3, \master);         // and leaves
//   g.solo = true;                         // hear the group and nothing else
//   ~sdg.removeGroup(g);                   // its orbits go to the master again
//
// An orbit joins a group by writing to it: its monitor's output bus is the
// group's in bus (SuperDirtGUI:setOrbitDest), so everything of the orbit,
// effects and fader included, is in what the group gets. The orbit's own sends
// to the returns stay as they are; the group's are on what the group plays,
// after its effects, and reach a return in the same block (see SDGBusTrack).
// Soloing a group solos its orbits; soloing an orbit of a group keeps the group
// on, with the orbit alone in it.
// =====================================================================

SDGGroup : SDGBusTrack {

	var <name;

	// made by SuperDirtGUI:addGroup, which puts it in place
	*new { |sdg, name| ^super.new(sdg).initGroup(name) }

	initGroup { |argName| name = argName.asSymbol }

	// \g1 is "group 1"
	shortName { ^"group " ++ name.asString.drop(1) }

	startTarget { ^[sdg.groupsGroup, \addToTail] }

	// the orbits that write to it, by index
	members { ^sdg.membersOf(this) }

	sessionData { ^super.sessionData.put(\name, name) }
}
