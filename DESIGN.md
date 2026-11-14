# ChanFlow

A native Android reader for 4chan media, with a continuous vertical feed and an offline library.

Revised after the first preview was rejected as a generic dashboard.

References studied in browser: Cosmos Explore's natural image proportions and dense
image wall; Are.na's quiet metadata and straightforward collection navigation;
TikTok's desktop modular playback view. Primary references:
https://www.cosmos.so/ , https://www.are.na/root/interface ,
https://newsroom.tiktok.com/new-features-bring-tiktok-magic-to-desktop?lang=en

Palette: black #111111, charcoal #1C1C1C, warm white #EDEDE9, gray #898985,
sage #A3B7A3. Restrict sage to active state and download progress. No colored panels.
Arial/Helvetica for compact UI text and a tightly spaced, medium-weight wordmark.
Use monospace only for actual board codes and numeric thread IDs.

Desktop has a single slim top navigation, no dashboard sidebar and no hero copy.
Board navigation is a compact line of actual board codes. Threads form a dense,
variable-aspect image wall with unboxed captions and very small corner radii.
The feed fills the available screen. The top shows the current board/thread; a small
right-hand rail provides mute, fit, save and discussion actions. A restrained bottom
caption opens the post. Browse and Saved share the bottom navigation. Saved uses a
recycling thumbnail grid and opens the same swipe viewer, entirely offline.

Compared with a generic short-video clone: preserve images' original aspect ratios,
keep the thread context one tap away, and clearly distinguish streaming cache from
permanently saved files. No engagement counters, fake followers or recommendation claims.

Performance: one player per feed, settled-page playback, bounded caches, background
image decoding, paged/lazy UI composition, cancellation and stale-response protection.
