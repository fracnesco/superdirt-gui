# Tidal

Everything SuperDirtGUI has for TidalCycles, in one folder.

| | |
|---|---|
| [boot.hs](boot.hs) | the boot file: starts Tidal, declares every SuperDirtGUI word (`sdgcomb 0.4` instead of `pF "sdgcomb" 0.4`) and loads the functions below |
| [functions/](functions/) | Haskell that boot.hs loads: [running.hs](functions/running.hs) changes what a `d` plays in place (`with`, `undo`, `briefly`, `clone`, `shadow` ...), [scenes.hs](functions/scenes.hs) is the crossfader (`scene`, `morph`, `blend`, `xfadeAt`, `sceneIn`, `xf`), [readers.hs](functions/readers.hs) picks a sample player per event (`readers`, `tuned`, `grainy`, `frozen` ...) |
| [lessons/](lessons/) | a file per thing, from `00-setup.tidal`: the synth, the module, the players, the global effects, the drum machine, tags, sync; then 26 to 34, five pieces of nine parts each |
| [multi target tidal/](multi%20target%20tidal/) | a boot that sends to TouchDesigner as well as SuperDirt, on time with the sound, and a file that plays both |

## Booting

Make `boot.hs` your Boot Tidal Path - in Pulsar, Settings > Packages >
tidalcycles; in VS Code, the tidalcycles extension's settings - and point it at
the file where the quark put it:

- macOS: `~/Library/Application Support/SuperCollider/downloaded-quarks/superdirt-gui/examples/tidal/boot.hs`
- Linux: `~/.local/share/SuperCollider/downloaded-quarks/superdirt-gui/examples/tidal/boot.hs`
- Windows: `%LOCALAPPDATA%\SuperCollider\downloaded-quarks\superdirt-gui\examples\tidal\boot.hs`

`Quarks.update` then keeps it up to date with the rest. When Tidal starts it
says `SuperDirtGUI: words and functions loaded`. If it says the functions were
not found, set the environment variable `SDG_TIDAL` to this folder, or write it
into `sdgFolderByHand` in a copy of boot.hs.

Files you make with **new...** in the window bring words of their own: add a
line for each at the end of the words, as for `playGrain`. Do that in a copy of
boot.hs kept outside the quark folder, and point your editor at the copy: a
file changed inside the quark stops `Quarks.update`, and the copy still finds
the functions here.
