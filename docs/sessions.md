# Sessions

The whole mixer under a name, brought back in place of what the orbits hold.

```supercollider
~sdg.saveSession("gig", { |ok| ok.postln });   // a moment later: plugins are asked for their state
~sdg.sessionNames;                             // -> [ gig ]
~sdg.sessionName;                              // the one saved or loaded last
~sdg.loadSession("gig", { |ok| ok.postln });   // once everything is playing
SDGSession.folder.openOS;                      // where they are
```

In the window: the menu along the top, **load session** for the one it shows, **save session**
for a name. Choosing in the menu changes nothing until you press load.

## What is in one

| held | not held |
|------|----------|
| every orbit's fader, mute, solo, limiter and output bus | content files: they are on disk |
| the tracks in order, each with its effect, level, on or off, and the parameters the knobs set | sample banks: load them as you did |
| every insert in its place with its settings | Tidal's patterns |
| a VST plugin's name, file path and state | the insert windows' places |
| the return tracks with every send, effect, inserts and levels, and the feedback switch | |
| the window's tab, selected orbit, play orbit, and speed, amp and pan knobs | |

**A session's parameters are the default event's only**, never `fx.state`, which also holds the last
value a pattern sent. Otherwise that value would come back as one the pattern never has to send again.

## Where

A folder per session, in `SDGSession.folder`, which is `superdirt-gui-sessions` in your SuperCollider
support directory, so that it survives an update of the quark:

```
 superdirt-gui-sessions/
   gig/
     session.scd      readable, an orbit or a return per line
     vst1.state       a plugin's state, as it hands it over
     vst2.state
```

Copy the folder to give a session to someone. **`session.scd` is SuperCollider code, read by running
it: only load sessions you trust.**

The state files are separate to keep the text readable, since a plugin's state can run to megabytes. A
name can be anything a folder can be on any system a student has: none of `/ \ : * ? " < > |`, no leading
dot, no trailing dot or space.

## What saving does

1. Every VST insert is asked for its state, at most five seconds in all. A plugin that does not answer is
   saved with the state it last gave, with a warning.
2. The session is put together as an Event and written out as text.
3. **That text is compiled and run first.** A session that cannot be read back is not saved at all, and
   whatever had that name is untouched.
4. The state files are written, then `session.scd`, then any state file no longer used is deleted.

From code it saves over a session of the same name without asking; the window's button says **replace** first. It answers false, with a warning, if the
name will not do, the server is not running, the orbits are not ready, or another save or load is running.

## What loading does

Nothing is touched if the session cannot be read. An orbit not ready yet, loaded in the same breath as
`SuperDirtGUI(~dirt)`, is waited for up to ten seconds.

1. **Plugins are looked for.** A plugin saved by name needs VSTPlugin's search to know it, and in a fresh
   sclang it has not searched, so the session keeps `info.path` beside the name and opens by that. Only when
   neither is usable does the search run: quick from its cache, up to two minutes the first time.
2. **Returns first,** so the orbits' sends have something to go to. A return the session also has stays and
   its inserts are made again; the others go. Sends between returns are set when all of them are there.
3. **Each orbit:** its inserts are taken out, sends set, limiter, then the output. The output comes first of
   the things that could rebuild the orbit, since a rebuild would start a starting insert a second time.
4. **Its tracks:** a track whose effect the session also has stays and keeps playing, so its tail rings on.
   The others go, and the session's missing ones are added, in order. A parameter the session has no value for
   goes back to the SynthDef's default and leaves the default event, as the window's **x** does.
5. **Its inserts,** in the session's order, settings before their synths start: they land in the effect's
   state, which `play` sends, or for a plugin wait for it to open.
6. **Fader, mute and solo last,** so a level comes up on the chain it was saved with.
7. **Waiting.** The action gets its answer once every insert has its synth and every plugin its state, at most
   thirty seconds.

Orbits beyond the session's are left as they are.

## What cannot come back

An effect whose file is gone, a kind whose extension is not installed, a plugin not on this machine, a state
file that is gone: each is left out with a warning, and the rest loads. All of them are posted together at the end
as `session 'x' loaded without:`, and `~sdg.session.problems` holds them as Strings. A session with more orbits
than this SuperDirt fills what it can. A session saved by a newer version says so and leaves out what it does
not know.

## Adding to it

Anything new the mixer can hold goes in a session: `SDGSession:prOrbitData` and `prApplyOrbit`, or
`SDGInsert:sessionData` and `prRestore` for an insert.

## Text limits, if you edit one by hand

A String literal over 8188 characters is cut off silently when compiled, with no error. Plugin states are not in
the text, so this only concerns anything long you add by hand.
