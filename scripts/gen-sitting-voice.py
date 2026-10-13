"""Records every line a sitting says, in the sitting's voice, into kotlin/app/src/main/assets/voice/.

The member is face-down; the voice is what they hear instead of the screen. It is Ava
(en-US-AvaNeural, chosen by the owner on 13 Oct from five calm neural voices), slowed by 15 %
and pitched 4 Hz lower so it sounds calm and steady rather than bright.

Each clip is named by `VoiceClips.slug` of the words the app passes (Coach.words, RepHint.message,
the countdown, "N banked", "Done"). `VoiceClipsTest` fails when a line the app can say has no clip,
so a new Coach or RepHint needs a line here and a re-run.

Two backends, same voice and the same output format:
  - Azure Speech (the licensed one, for a release): set AZURE_SPEECH_KEY and AZURE_SPEECH_REGION.
  - Otherwise edge-tts (pip install edge-tts), free, for trying a voice out.

Usage, from the repo root:  python scripts/gen-sitting-voice.py [--out DIR]
"""

import argparse
import asyncio
import os
import re
import urllib.request
from pathlib import Path

VOICE = "en-US-AvaNeural"
RATE = "-15%"
PITCH = "-4Hz"
FORMAT = "audio-24khz-48kbitrate-mono-mp3"

# (the words the app passes, how they are said). A trailing full stop gives the falling,
# settled intonation; without it a single word often comes out as a question.
LINES = [
    # countdown and start (SittingCues)
    ("three", "Three."),
    ("two", "Two."),
    ("one", "One."),
    ("go", "Go."),
    ("Done", "Done."),
    # getting into position (Coach)
    ("More light", "More light."),
    ("Place your phone", "Place your phone."),
    ("Get in view", "Get in view."),
    ("Phone to 45°", "Phone to forty-five degrees."),
    ("Get down", "Get down."),
    ("Further", "Further."),
    ("Turn slightly left", "Turn slightly left."),
    ("Turn slightly right", "Turn slightly right."),
    ("Turn slightly away", "Turn slightly away."),
    ("Closer", "Closer."),
    ("Move left", "Move left."),
    ("Move right", "Move right."),
    ("Arms straight", "Arms straight."),
    ("Knees up", "Knees up."),
    ("Can't see knees", "Can't see knees."),
    ("Body straight", "Body straight."),
    ("Hold", "Hold."),
    # refused reps (RepHint)
    ("Go lower", "Go lower."),
    ("Keep your body straight", "Keep your body straight."),
    ("Slow down", "Slow down."),
    ("Knees off the floor", "Knees off the floor."),
    ("Keep your legs in the picture", "Keep your legs in the picture."),
    ("Hips off the floor", "Hips off the floor."),
]
# a block of ten banked (SittingCues says "$banked banked"); past 300 the device's own voice says it
LINES += [(f"{n} banked", f"{n} banked.") for n in range(10, 301, 10)]


def slug(words: str) -> str:
    """The same as VoiceClips.slug in the app."""
    s = words.lower().replace("°", "deg")
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def azure(text: str, path: Path, key: str, region: str) -> None:
    ssml = (
        '<speak version="1.0" xml:lang="en-US"><voice name="%s">'
        '<prosody rate="%s" pitch="%s">%s</prosody></voice></speak>' % (VOICE, RATE, PITCH, text)
    )
    req = urllib.request.Request(
        f"https://{region}.tts.speech.microsoft.com/cognitiveservices/v1",
        data=ssml.encode("utf-8"),
        headers={
            "Ocp-Apim-Subscription-Key": key,
            "Content-Type": "application/ssml+xml",
            "X-Microsoft-OutputFormat": FORMAT,
            "User-Agent": "holdout-gen-sitting-voice",
        },
    )
    with urllib.request.urlopen(req) as r:
        path.write_bytes(r.read())


async def edge(lines, out: Path) -> None:
    import edge_tts

    sem = asyncio.Semaphore(6)

    async def one(words, said):
        async with sem:
            await edge_tts.Communicate(said, VOICE, rate=RATE, pitch=PITCH).save(str(out / f"{slug(words)}.mp3"))

    await asyncio.gather(*(one(w, s) for w, s in lines))


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    p = argparse.ArgumentParser()
    p.add_argument("--out", type=Path, default=root / "kotlin/app/src/main/assets/voice")
    out = p.parse_args().out
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.mp3"):
        old.unlink()

    key, region = os.environ.get("AZURE_SPEECH_KEY"), os.environ.get("AZURE_SPEECH_REGION")
    if key and region:
        for words, said in LINES:
            azure(said, out / f"{slug(words)}.mp3", key, region)
        backend = "Azure Speech"
    else:
        asyncio.run(edge(LINES, out))
        backend = "edge-tts (try-out only; set AZURE_SPEECH_KEY and AZURE_SPEECH_REGION for a release)"
    print(f"{len(LINES)} clips in {out} via {backend}")


if __name__ == "__main__":
    main()
