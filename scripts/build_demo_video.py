#!/usr/bin/env python3
"""Build a 75-second, silent captioned walkthrough from the real UI captures.

Requires ffmpeg on PATH and DejaVu Sans fonts (or pass --font-dir).
Run from any directory: python3 scripts/build_demo_video.py
"""

import argparse
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
SCENES = [
    (5, "demo-frames/01-browse-events.jpg", "EVENT MANAGEMENT", "Meet EventOps", "Register attendees.\nManage capacity.\nTrack attendance."),
    (8, "demo-frames/02-signup-form.jpg", "01 / SIGN UP", "Register in\nyour browser", "Choose an event and enter\nyour name and email.\nNo attendee account needed."),
    (7, "demo-frames/03-confirmed.jpg", "02 / CONFIRM", "Fill the\navailable seat", "Alex receives confirmation\nand a private management\ncode on the signup receipt."),
    (8, "demo-frames/04-waitlisted.jpg", "03 / WAITLIST", "Keep the\nqueue moving", "Bri registers after the\nevent fills and automatically\njoins the waitlist."),
    (7, "demo-frames/05-manage-confirmed.jpg", "04 / MANAGE", "Check status\nprivately", "Attendees use their saved\ncode to view their status\nand manage a registration."),
    (9, "demo-frames/06-cancel-prompt.jpg", "05 / CANCEL", "Release a seat\nwith confidence", "Cancellation requires\nconfirmation and promotes\nthe next waiting attendee."),
    (8, "demo-frames/08-promoted.jpg", "06 / PROMOTE", "From waitlisted\nto confirmed", "After Alex cancels, Bri's\nregistration #11 changes\nfrom waitlisted to confirmed."),
    (9, "screenshots/organizer-dashboard.jpg", "07 / ORGANIZE", "See the\nwhole event", "SQL summaries show capacity,\nwaitlists, cancellations,\nand attendance in one place."),
    (7, "screenshots/organizer-dashboard.jpg", "08 / ATTENDANCE", "Keep check-in\npersistent", "Organizers search, filter,\nand check in confirmed\nattendees. Refresh keeps it."),
    (7, "screenshots/organizer-dashboard.jpg", "ENGINEERED AND VERIFIED", "Built for\nreal workflows", "Java and Spring Boot\nPostgreSQL and SQL\n29 automated tests\nDocker and Render"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font-dir", type=Path, default=Path("/usr/share/fonts/truetype/dejavu"))
    args = parser.parse_args()
    fonts = {"regular": args.font_dir / "DejaVuSans.ttf", "bold": args.font_dir / "DejaVuSans-Bold.ttf"}
    for font in fonts.values():
        if not font.is_file():
            parser.error(f"Missing font: {font}")
    output = ROOT / "docs/eventops-demo.mp4"
    with tempfile.TemporaryDirectory(prefix="eventops-video-") as tmp:
        work = Path(tmp)
        clips = []
        for index, (duration, image, step, title, body) in enumerate(SCENES, 1):
            texts = {
                "brand": "EVENTOPS  /  MICHAEL BEKELE",
                "step": step,
                "title": title,
                "body": body,
                "footer": "JAVA  /  SPRING BOOT  /  POSTGRESQL",
                "count": f"{index:02d} / {len(SCENES):02d}",
                "capture": "REAL APPLICATION CAPTURES  /  SYNTHETIC ATTENDEES",
            }
            files = {}
            for name, value in texts.items():
                files[name] = work / f"{index}-{name}.txt"
                files[name].write_text(value, encoding="utf-8")

            def text(name, x, y, size, color="white", bold=False):
                font = fonts["bold" if bold else "regular"]
                return (f"drawtext=fontfile='{font}':textfile='{files[name]}':"
                        f"x={x}:y={y}:fontsize={size}:fontcolor={color}:line_spacing=16")

            filters = [
                "[1:v]drawbox=x=680:y=112:w=1176:h=820:color=0xF4F7FB:t=fill[base]",
                "[0:v]scale=1132:776:force_original_aspect_ratio=decrease:flags=lanczos,setsar=1[shot]",
                "[base][shot]overlay=x=702+(1132-overlay_w)/2:y=134+(776-overlay_h)/2",
            ]
            drawing = [
                text("brand", 72, 58, 24, "0x9EB1CC", True),
                text("step", 72, 162, 23, "0x65AAFF", True),
                text("title", 72, 226, 55, bold=True),
                text("body", 72, 432, 29, "0xD4DEED"),
                text("capture", 702, 950, 19, "0x9EB1CC"),
                text("footer", 72, 1008, 20, "0x9EB1CC"),
                text("count", 1748, 1008, 23, "0x9EB1CC"),
                f"fade=t=in:st=0:d=0.18,fade=t=out:st={duration - 0.18}:d=0.18,format=yuv420p[out]",
            ]
            graph = ";".join(filters[:2]) + ";" + filters[2] + "," + ",".join(drawing)
            graphfile = work / f"{index}-filter.txt"
            graphfile.write_text(graph)
            clip = work / f"{index:02d}.mp4"
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-loop", "1", "-framerate", "24", "-i", str(ROOT / "docs" / image),
                "-f", "lavfi", "-i", "color=c=0x081426:s=1920x1080:r=24",
                "-filter_complex_script", str(graphfile), "-map", "[out]", "-t", str(duration),
                "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "19", "-threads", "2",
                str(clip),
            ], check=True)
            clips.append(clip)
            print(f"Rendered scene {index}/{len(SCENES)}", flush=True)
        listing = work / "clips.txt"
        listing.write_text("".join(f"file '{p}'\n" for p in clips))
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy",
            "-movflags", "+faststart", "-metadata", "title=EventOps — 75-second walkthrough",
            "-metadata", "comment=Captioned walkthrough of actual application screenshots; synthetic attendees; no narration.",
            str(output),
        ], check=True)
        print(output)


if __name__ == "__main__":
    main()
