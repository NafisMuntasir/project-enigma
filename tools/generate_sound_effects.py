"""Reproducible original sci-fi SFX; Python standard library only.

Run from any directory. --preview also writes build/sfx-preview.wav in cue order.
No sampled recordings or third-party audio are used. GPL-3.0, like the project.
"""
from pathlib import Path
import argparse
import math
import random
import struct
import wave

ROOT = Path(__file__).resolve().parents[1]
RATE = 44100
TAU = math.tau


def tone(length, frequency, end=None, decay=5, attack=.005, harmonics=.15):
    result = []
    phase = 0
    for i in range(round(length * RATE)):
        t = i / RATE
        f = frequency + ((end or frequency) - frequency) * t / length
        phase += TAU * f / RATE
        envelope = min(1, t / attack) * math.exp(-decay * t / length)
        envelope *= min(1, (length - t) / .015)
        result.append(envelope * (math.sin(phase) + harmonics * math.sin(phase * 2)))
    return result


def noise(length, seed, decay=8, smooth=.65):
    rng = random.Random(seed)
    result = []
    value = 0
    for i in range(round(length * RATE)):
        t = i / RATE
        value = smooth * value + (1 - smooth) * rng.uniform(-1, 1)
        envelope = min(1, t / .003) * math.exp(-decay * t / length) * min(1, (length - t) / .015)
        result.append(value * envelope)
    return result


def mix(length, *layers):
    result = [0.] * round(length * RATE)
    for offset, gain, samples in layers:
        start = round(offset * RATE)
        for i, value in enumerate(samples[:len(result) - start]):
            result[start + i] += gain * value
    peak = max(map(abs, result)) or 1
    # Headroom, exact silent endpoints, and a final fade prevent clicks/clipping.
    for i in range(len(result)):
        fade = min(1, i / (RATE * .002), (len(result) - 1 - i) / (RATE * .01))
        result[i] *= .78 / peak * fade
    return result


def write(path, samples):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as output:
        output.setparams((1, 2, RATE, len(samples), 'NONE', 'not compressed'))
        output.writeframes(struct.pack('<' + 'h' * len(samples), *(round(v * 32767) for v in samples)))


def generate(preview=False):
    cues = {
        'hover': mix(.065, (0, 1, tone(.065, 1050, 1400, decay=6, harmonics=.04))),
        'click': mix(.12, (0, .7, tone(.075, 650, 380)), (.028, .45, tone(.09, 1350, 1200)), (0, .15, noise(.04, 1))),
        'damage': mix(.28, (0, 1, tone(.22, 175, 48, decay=7)), (0, .9, noise(.18, 2, smooth=.35)),
                      (.008, .22, tone(.25, 860, 440, decay=8))),
        'heal': mix(.64, (0, .7, tone(.4, 523.25, decay=3, attack=.02, harmonics=.05)),
                    (.10, .65, tone(.4, 659.25, decay=3, attack=.02)), (.20, .55, tone(.44, 783.99, decay=4, attack=.015))),
        'chest': mix(.48, (0, .8, noise(.10, 3, smooth=.15)), (.03, .55, tone(.18, 240, 85)),
                     (.09, .7, noise(.30, 4, decay=4, smooth=.85)), (.19, .35, tone(.23, 1174.66, decay=4))),
        'skill': mix(.43, (0, .75, tone(.30, 180, 1800, decay=1.5, attack=.025)),
                     (.10, .22, tone(.30, 370, 2200, decay=2)), (.21, .7, noise(.22, 5, decay=4, smooth=.35))),
        'encounter': mix(.70, (0, .8, tone(.23, 440, 330, decay=2, harmonics=.28)),
                         (.20, .8, tone(.30, 330, 220, decay=3, harmonics=.25)), (.20, .6, tone(.45, 110, 75))),
        'power_up': mix(.88, (0, .65, tone(.28, 523.25, decay=4)), (.095, .6, tone(.30, 659.25, decay=4)),
                        (.19, .6, tone(.32, 783.99, decay=4)), (.29, .6, tone(.59, 1046.5, decay=4, attack=.015)),
                        (.31, .22, tone(.56, 1567.98, decay=4, attack=.025))),
    }
    for name, samples in cues.items():
        write(ROOT / 'assets' / 'audio' / (name + '.wav'), samples)
        print(f'{name}: {len(samples) / RATE:.3f}s, {len(samples) * 2 + 44} bytes')
    if preview:
        sequence = [0.] * round(.2 * RATE)
        gains = [.32, .42, .70, .55, .60, .55, .55, .60]
        for samples, gain in zip(cues.values(), gains):
            sequence += [v * gain * .65 for v in samples] + [0.] * round(.45 * RATE)
        write(ROOT / 'build' / 'sfx-preview.wav', sequence)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--preview', action='store_true')
    generate(parser.parse_args().preview)
