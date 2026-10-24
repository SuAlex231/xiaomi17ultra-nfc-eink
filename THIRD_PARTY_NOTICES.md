# References and notices

The Xiaomi 17 Ultra ST54L backend (StController, StPolling, ST MIFARE compatibility script) is adapted from wiwiwei/ink-nfc-lab, commit `6ffde495a9eb53acc69c4acdeee74e7fab017f30`, https://github.com/wiwiwei/ink-nfc-lab . Its PolyForm Noncommercial 1.0.0 license and NOTICE are retained in assets/licenses/ink-nfc-lab-LICENSE.txt and ink-nfc-lab-NOTICE.txt. Required Notice: Copyright (c) 2026 kur0neko.

The native custom polling GET OID and OEM SET CRC conversion were verified against STMicroelectronics/ST54-android-hardware-st-nfc, st_dev_16 commit `429ab3ffe50f4d0496408f2b52c0a7a4736bc559` (Apache-2.0). No extracted phone firmware, framework JAR or vendor shared library is embedded in the APK.

Frida 17.24.0 Android arm64 frida-inject is included under the wxWindows Library Licence. The official compressed release SHA-256 is `261875c11ec37fa1d3c26c05f71c0390ef1176539b0545d0b253cdbf24ece0e5`, checked by build.py. Complete licence and attribution are packaged in assets/licenses/FRIDA-COPYING.txt and Frida-NOTICE.txt. Source: https://github.com/frida/frida/releases/tag/17.24.0 .

The factory image scan layout and D3/D4 protocol implementation were adapted with reference to:

- kur0neko, https://gitee.com/kur0neko/huawei-epd-pn532, commit `5d3acf730a988815b637262fd4862c1794ab079e`.
- Required Notice: Copyright (c) 2026 kur0neko
- PolyForm Noncommercial License 1.0.0; exact license and notice are retained in `third-party/kur0neko/` and packaged in APK assets. The app's license dialog displays the full license.

Protocol research also used solar2ain/ink-nfc-fmsc-by-pn532, commit `0f88cb315eab60e538c102e5883c5f957d9c1be0` (GPL-3.0-or-later), including the published D8/APDU definitions and LZO1X literal-stream format. No Python modules or native binaries from that project are embedded. The local reference checkout retains its license. The Android literal encoder implements the literal length prefix and LZO end marker directly; its output is independently tested with lzokay.

The v2.10-test1 panel selector and Java layout implementation also use the published Huawei A0 even/odd controller-row mapping and A1 linear layout as protocol references from that checkout (`images.py`, `catalog.py`, `_assets/drivers.json`). Full-frame scan-01 output was compared byte-for-byte against its `pack_indices` function. Its DB/DA driver recipes are not embedded or written by this app; the board's stored driver is retained.

The Android port adds Root/Binder Q-Poll discovery, ISO-DEP transport, target/profile gates, immutable image snapshots, Android image selection/preview, cancellation, and stricter refresh-state reporting. It does not ship PN532 serial transport, replacement display drivers, or vendor test images. The built-in test pattern is drawn locally.

lzokay is used only by the host-side test script and is not part of the APK.


Shizuku API 13.1.5 (api, provider, aidl, shared), copyright Rikka and contributors, MIT License. Source: https://github.com/RikkaApps/Shizuku-API . Full license: assets/licenses/Shizuku-LICENSE.txt.
