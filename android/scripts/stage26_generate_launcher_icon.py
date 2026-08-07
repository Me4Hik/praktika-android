"""Generate production launcher icons from existing hero orb asset."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC_ORB = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "question_hero_liquid_orb.png"
RES = ROOT / "app" / "src" / "main" / "res"
BG_COLOR = (8, 11, 18, 255)  # NavyBackground #080B12
CANVAS = 432
ORB_SCALE = 0.58  # inside adaptive safe zone


def compose_icon(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BG_COLOR)
    orb = Image.open(SRC_ORB).convert("RGBA")
    target = int(size * ORB_SCALE)
    ratio = min(target / orb.width, target / orb.height)
    new_size = (max(1, int(orb.width * ratio)), max(1, int(orb.height * ratio)))
    orb = orb.resize(new_size, Image.Resampling.LANCZOS)
    x = (size - orb.width) // 2
    y = int(size * 0.46 - orb.height // 2)
    canvas.alpha_composite(orb, (x, y))
    return canvas


def save_png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def main() -> None:
    if not SRC_ORB.exists():
        raise SystemExit(f"Missing source orb: {SRC_ORB}")

    foreground = compose_icon(CANVAS)
    save_png(RES / "drawable-nodpi" / "ic_launcher_foreground.png", foreground)

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        save_png(RES / folder / "ic_launcher.png", compose_icon(size))
        save_png(RES / folder / "ic_launcher_round.png", compose_icon(size))

    print("Launcher icons generated.")


if __name__ == "__main__":
    main()
