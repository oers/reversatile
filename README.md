Reversatile - Zebra Reversi for Android 
---------------------------------------------------------------

[![Build Status Development](https://travis-ci.org/oers/reversatile.svg?branch=development)](https://travis-ci.org/oers/revrsatile)

[![Build Status Master](https://travis-ci.org/oers/reversatile.svg?branch=master)](https://travis-ci.org/oers/revrsatile)

[![Build Status Foss](https://travis-ci.org/oers/reversatile.svg?branch=foss)](https://travis-ci.org/oers/revrsatile)

This app continues the work of the discontinued Droidzebra: https://github.com/alkom/droidzebra

Since Version 1.4 this app needs Android 5 to run. The latest release that supports older version can be found [here](https://github.com/oers/reversatile/releases/tag/1.3.11)


Reversatile is a graphical front-end for well-known Zebra Othello
Engine written by Gunnar Andersson. It is one of the strongest
othello-playing programs in the world. More info on Zebra engine
can be found here: http://radagast.se/othello/

The game requires 10MB of storage (external or internal) to play.

Info about Reversi: http://en.wikipedia.org/wiki/Reversi

You will need approximately 15mb of storage space to install
and play the game.

assets/ - compressed book and coeffs2.bin
jni/ - C code Zebra + mods
src/ - Java code
res/ - resource files

Differences from droidzebra:
- will start in practise mode with highest difficulty
- can read games via intents/copy and paste

Current features:
- Zebra engine
- play human vs human, hunam vs computer, computer vs computer
- multiple levels of play
- practice mode
- unlimited undo

The Development/Master Branch uses play store apis. The foss branch is free vom closed source software.


Version 1.0.0

- added functionality to enter games (via copy and paste or manually)
- added functionality to listen to intent from Reversi Wars

Version 1.3.x.
 - guess the best move mode

Version 1.4.X.
  - Better Status View
  - Minimum Android 5
