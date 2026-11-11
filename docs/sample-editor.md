# The sample editor

The editor is its own quark, [sc-sample-editor](https://github.com/fracnesco/sc-sample-editor)
(class `SampleEditor`), which SuperDirtGUI depends on. Its own
[docs/sample-editor.md](https://github.com/fracnesco/sc-sample-editor/blob/master/docs/sample-editor.md)
has every gesture, key and number: the trim, the gain, the beat grid, the slicer and what it keeps, what a
save writes. This page is what SuperDirtGUI adds.

```supercollider
~sdg.edit(\bd, 3);           // bank, index, and optionally the orbit it plays through
~sdg.editFile("~/x.wav");    // a file from anywhere; as bd:3 when the library holds it
~sdg.editorHost;             // the host every editor gets while this loader runs
```

**edit...** in the sound explorer, or `e`, opens the selected sample. A sound file dropped on the
explorer opens in the editor; a folder dropped there is loaded as a bank.

## The host

What an editor knows of the world is its host. `SuperDirtGUI` makes one when it starts
(`editorHost`, a `SampleEditorDirtHost` with the loader as its owner) and sets it as
`SampleEditor.defaultHost`, so every editor opened while it runs, from the window or from code, uses it;
`free` puts the plain host back. Through it:

| the editor | goes through |
|------------|--------------|
| plays the preview | the orbit's dry bus, after `wakeOrbit`, so its effects, inserts and fader apply |
| plays a library sample | the library's own Buffer |
| saves, reverts | `reloadSample`: the same Buffer read again, Tidal plays the new file at once |
| saves as into the bank's folder | `addSample`: appended to the bank |
| writes a bank (to bank..., export slices...) | `loadSamples`: the explorer shows the new bank |
| reads and sets bpm, key, tags | `tags`, the `SDGSampleTags` store: the explorer and Tidal's picks see them |

## Without the quark

An install from before the editor moved, updated with `Quarks.update`, does not have it:
`Quarks.update` installs no new dependency. SuperDirtGUI then finds no `SampleEditor` class (it looks
the editor up by name, never naming it in its code, so it still compiles): **edit...**, `~sdg.edit` and a
dropped file post the line that installs it, and nothing is analysed - the explorer's bpm and key columns
stay empty, and tags, bpm and key set by hand work as before. The analysis (`SampleFeatures`) is in that
quark too; `SDGSampleTags` keeps its own `parseKey` so searches by key still work.
