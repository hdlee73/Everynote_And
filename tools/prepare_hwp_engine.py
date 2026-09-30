"""Download the pinned, offline HWP/HWPX WASM engine before Android builds."""
import hashlib
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    'rhwptopdf.umd.js': '7913400eae742e9f15f320fc377d5961a63216840f290a4c280fbe731ec3c0cd',
    'rhwptopdf.umd_bg.wasm': '7a3e1e3e3a8d2a5bea64feac7bd8a2b556687a70874003fcf2aa6677e92f83a7',
}
for name, digest in FILES.items():
    target = ROOT / 'app/src/main/assets/hwp' / name
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == digest:
        continue
    url = 'https://github.com/sanguneo/rhwptopdf/releases/download/v0.2.2/' + name
    with urllib.request.urlopen(url, timeout=90) as response:
        data = response.read(16 * 1024 * 1024)
    if hashlib.sha256(data).hexdigest() != digest:
        raise RuntimeError('HWP engine checksum mismatch: ' + name)
    target.write_bytes(data)
    print('Verified:', name, len(data))
