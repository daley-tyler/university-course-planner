"""Prepare the licensed campus clip for the homepage.

Download Pexels video 31033176 (see docs/MEDIA.md), then run:
    python tools/media/prepare_campus_video.py /path/to/download.mp4
Requires FFmpeg with libx264 on PATH, or set FFMPEG_BINARY to its location.
"""

import argparse
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    encoder = os.environ.get("FFMPEG_BINARY", "ffmpeg")
    output = Path(__file__).resolve().parents[2] / "course_planner_frontend/public/videos"
    output.mkdir(parents=True, exist_ok=True)
    # Rotate the first second to the end and dissolve the seam. The last
    # frame then leads back into the beginning of the middle section.
    filters = (
        "[0:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,"
        "fps=24,setsar=1,split=3[body][end][start];"
        "[body]trim=start=1:end=9,setpts=PTS-STARTPTS,fps=24[middle];"
        "[end]trim=start=9:end=10,setpts=PTS-STARTPTS,fps=24[tail];"
        "[start]trim=start=0:end=1,setpts=PTS-STARTPTS,fps=24[head];"
        "[tail][head]xfade=transition=fade:duration=1:offset=0[seam];"
        "[middle][seam]concat=n=2:v=1:a=0[out]"
    )
    video = output / "campus-background.mp4"
    subprocess.run([
        encoder, "-y", "-loglevel", "error", "-i", str(args.source),
        "-filter_complex", filters, "-map", "[out]", "-an", "-c:v", "libx264",
        "-crf", "27", "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
        str(video),
    ], check=True)
    subprocess.run([
        encoder, "-y", "-loglevel", "error", "-ss", "3", "-i", str(video),
        "-frames:v", "1", "-q:v", "3", str(output / "campus-background.jpg"),
    ], check=True)


if __name__ == "__main__":
    main()
