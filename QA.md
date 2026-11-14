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
