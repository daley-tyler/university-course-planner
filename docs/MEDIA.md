# Campus background footage

The homepage uses **Aerial View of Iconic University Campus** by **David Pickup**, supplied through [Pexels](https://www.pexels.com/video/aerial-view-of-iconic-university-campus-31033176/). I chose real campus scenery to give the demo an academic setting while keeping the application branding generic. The footage is illustrative; the project is independent of the institution shown.

The clip is covered by the [Pexels License](https://www.pexels.com/license/), checked on 29 October 2026. Pexels permits website use and modification, with no required attribution. Its restrictions include implying endorsement and redistribution on stock-media platforms. The homepage still includes a visible creator credit. This asset remains subject to the Pexels License, independently of any future license selected for the application source.

## Preparation

The downloaded source is a 2560 × 1440 MP4, approximately 10.5 seconds long. Its SHA-256 is `07290a6828f8db4f25a2e15e3bbaa25505ff8c028f1c17dee4559f8f1c8dae22`. The source download is retained outside this repository in the local review archive.

`public/videos/campus-background.mp4` is a silent 1280 × 720, 24 fps derivative. I trimmed it to a nine-second loop and dissolved the end into the beginning. The JPEG poster is a frame from that derivative. The video starts automatically, muted and looping, and retries playback when the page becomes visible. The page shows the still image when reduced motion is requested or video loading fails.

To reproduce the assets, download the source from the linked Pexels page and run this from the repository root with Python 3 and FFmpeg installed:

```bash
python tools/media/prepare_campus_video.py /path/to/download.mp4
```

Set `FFMPEG_BINARY` if the encoder is outside PATH. FFmpeg needs the libx264 encoder. The original abstract animation was replaced after the visual review; its source and assets remain in the separate restricted local archive and are excluded from this clean history.
