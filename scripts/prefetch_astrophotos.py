#!/usr/bin/env python3
"""下载核心天体的高清实拍图并降采样后内置为离线首发图源。

用法：python scripts/prefetch_astrophotos.py
产出：app/src/main/assets/astrophotos/<KEY>.jpg（最长边 768px，JPEG q82）
"""

import io
import sys
import time
import urllib.request
from pathlib import Path

from PIL import Image

# 源为受信任的固定图源；巨幅原图经 draft 缩减解码，不做炸弹告警拦截
Image.MAX_IMAGE_PIXELS = None

ASSET_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/astrophotos"
UA = "AetherisSkyApp/2.0 (asset prefetch)"
MAX_EDGE = 768

WM = "https://upload.wikimedia.org/wikipedia/commons"


def hips2fits(ra_deg: float, dec_deg: float, fov_deg: float) -> str:
    return (
        "https://alasky.cds.unistra.fr/hips-image-services/hips2fits"
        "?hips=CDS%2FP%2FDSS2%2Fcolor&width=512&height=512"
        f"&fov={fov_deg}&projection=TAN&coordsys=icrs&ra={ra_deg}&dec={dec_deg}&format=jpg"
    )


SOURCES = {
    # 哈勃 / 卡西尼 / SDO 公共领域摄影（Wikimedia Commons 原图）
    "M31": f"{WM}/9/98/Andromeda_Galaxy_%28with_h-alpha%29.jpg",
    "M42": f"{WM}/f/f3/Orion_Nebula_-_Hubble_2006_mosaic_18000.jpg",
    "M45": f"{WM}/4/4e/Pleiades_large.jpg",
    "M1": f"{WM}/0/00/Crab_Nebula.jpg",
    "M13": f"{WM}/6/60/Heart_of_M13_Hercules_Globular_Cluster.jpg",
    "M51": f"{WM}/8/87/Whirlpool_Galaxy.jpg",
    "M57": f"{WM}/1/13/M57_The_Ring_Nebula.JPG",
    "MARS": f"{WM}/0/02/OSIRIS_Mars_true_color.jpg",
    "JUPITER": f"{WM}/2/2b/Jupiter_and_its_shrunken_Great_Red_Spot.jpg",
    "SATURN": f"{WM}/c/c7/Saturn_during_Equinox.jpg",
    "MOON": f"{WM}/e/e1/FullMoon2010.jpg",
    "SUN": f"{WM}/b/b4/The_Sun_by_the_Atmospheric_Imaging_Assembly_of_NASA%27s_Solar_Dynamics_Observatory_-_20100819.jpg",
    # DSS2 彩色巡天实拍切片（恒星特写 0.35° / 北斗天区 15°）
    "SIRIUS": hips2fits(101.287, -16.716, 0.35),
    "UMA": hips2fits(186.4, 55.5, 15.0),
}


def fetch(url: str) -> bytes:
    last_err: Exception | None = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=90) as resp:
                return resp.read()
        except Exception as e:  # noqa: BLE001
            last_err = e
            time.sleep(6 + attempt * 6)
    raise last_err  # type: ignore[misc]


def resample_jpeg(data: bytes) -> bytes:
    # draft 先以 DCT 缩减解码巨幅 JPEG，避免全像素展开
    img = Image.open(io.BytesIO(data))
    img.draft("RGB", (MAX_EDGE, MAX_EDGE))
    img = img.convert("RGB")
    w, h = img.size
    scale = min(1.0, MAX_EDGE / max(w, h))
    if scale < 1.0:
        img = img.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
    out = io.BytesIO()
    img.save(out, "JPEG", quality=82)
    return out.getvalue()


def main() -> int:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    failures = []
    for key, url in SOURCES.items():
        out = ASSET_DIR / f"{key}.jpg"
        if out.exists() and out.stat().st_size > 10_000:
            print(f"{key}: cached")
            continue
        try:
            raw = fetch(url)
            out.write_bytes(resample_jpeg(raw))
            print(f"{key}: {out.stat().st_size} bytes <- {url}")
        except Exception as e:  # noqa: BLE001
            failures.append(key)
            print(f"{key}: FAILED ({e})", file=sys.stderr)
        time.sleep(2)
    if failures:
        print(f"failed: {failures}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
