# Verification inventory

Desktop preview:
- Live boards/catalog loads; change board, search all boards, adult toggle and retry.
- Open thread, previous/next and arrow/wheel/touch navigation; first/last boundaries.
- Images/GIFs/video render without stretch; fit/fill full cycle; mute and pause.
- Post modal, thread save-all confirmation, library search/sort.
- Save actual bytes to IndexedDB, duplicate prevention, progress/cancel/retry.
- Reload and play a saved file with networking disabled; delete confirmation/cancel.
- Desktop, phone mode and 390px viewport; no clipped controls or horizontal overflow.
- Exploratory: navigate away during a thread fetch; delete last offline item; invalid
  proxy paths; interrupted download and unavailable media.

Android:
- Compile debug and optimized release, run unit tests and Android lint.
- Exercise browse/saved navigation and private-vault playback in emulator if available.
- Foreground download notification and update installer require Android-specific QA.

Evidence belongs in ignored preview/test-results and build output, not the source tree.

Verified for the first release:
- Four Android unit tests and Android debug/release lint checks passed.
- Three emulator tests passed: vault persistence, offline image playback and offline
  WebM playback. A vertical swipe advanced to the next video using the same player.
- Desktop tests passed for next-video cache reuse, playback, mute, fit, post details,
  whole-thread saving, batch deletion with cancellation, board search and narrow layout.
- A real attachment was saved, the browser reloaded without a network connection,
  and the saved attachment played from its private browser storage.
- Desktop and phone layouts were visually inspected, including the populated library.
- No horizontal overflow or JavaScript exceptions were observed in those checks.

Browser and Android libraries are separate. Physical-phone codec and battery behavior
are not covered by the emulator or browser preview.
