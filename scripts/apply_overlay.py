#!/usr/bin/env python3
"""
اعمال تغییرات PingLess روی سورس v2rayNG.
استفاده:  python3 scripts/apply_overlay.py <path-to-V2rayNG/app>

کارها:
  1) کپی صفحه فعال‌سازی و آیکون‌ها
  2) نام برنامه = PingLess
  3) شناسه برنامه = com.pingless.vpn (تا کنار v2rayNG اصلی نصب شود)
  4) شناسه‌های داخلی (action / permission / authority) هم‌نام با شناسه جدید
  5) صفحه فعال‌سازی = صفحه شروع برنامه
"""
import os
import re
import shutil
import sys

APP_NAME = "PingLess"
NEW_APP_ID = "com.pingless.vpn"
OLD_PREFIX = "com.v2ray.ang"
BUILDCONFIG = "com.v2ray.ang.BuildConfig.APPLICATION_ID"
ACTIVITY = "com.v2ray.ang.pingless.ActivationActivity"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OVERLAY = os.path.join(ROOT, "overlay")


def log(msg):
    print(f"[pingless] {msg}", flush=True)


def fail(msg):
    print(f"::error::{msg}", flush=True)
    sys.exit(1)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


def main():
    if len(sys.argv) != 2:
        fail("usage: apply_overlay.py <V2rayNG/app>")
    app = os.path.abspath(sys.argv[1])
    src_main = os.path.join(app, "src", "main")
    manifest_path = os.path.join(src_main, "AndroidManifest.xml")
    if not os.path.isfile(manifest_path):
        fail(f"AndroidManifest.xml not found under {src_main}")

    # ---- 1) Java/Kotlin sources
    java_root = os.path.join(src_main, "java")
    if not os.path.isdir(java_root):
        java_root = os.path.join(src_main, "kotlin")
    shutil.copytree(os.path.join(OVERLAY, "java"), java_root, dirs_exist_ok=True)
    log(f"copied sources into {java_root}")

    # ---- 1b) resources (icons)
    res = os.path.join(src_main, "res")
    for d in os.listdir(res):
        if re.fullmatch(r"mipmap-[a-z]*dpi(-v\d+)?", d):
            for f in os.listdir(os.path.join(res, d)):
                if re.fullmatch(r"ic_launcher(_round)?\.(png|webp)", f):
                    os.remove(os.path.join(res, d, f))
    shutil.copytree(os.path.join(OVERLAY, "res"), res, dirs_exist_ok=True)
    # any other adaptive-icon folders (e.g. mipmap-anydpi) also point to our icon
    for d in os.listdir(res):
        if d.startswith("mipmap-anydpi") and d != "mipmap-anydpi-v26":
            for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
                if os.path.exists(os.path.join(res, d, n)):
                    shutil.copy(os.path.join(OVERLAY, "res", "mipmap-anydpi-v26", n), os.path.join(res, d, n))
    log("icons replaced")

    # ---- 2) app name
    count = 0
    for d in os.listdir(res):
        p = os.path.join(res, d, "strings.xml")
        if d.startswith("values") and os.path.isfile(p):
            s = read(p)
            s2 = re.sub(r'(<string name="app_name"[^>]*>)[^<]*(</string>)', rf"\g<1>{APP_NAME}\g<2>", s)
            if s2 != s:
                write(p, s2)
                count += 1
    log(f"app_name set in {count} strings files")

    # ---- 3) applicationId
    gradle = None
    for n in ("build.gradle.kts", "build.gradle"):
        if os.path.isfile(os.path.join(app, n)):
            gradle = os.path.join(app, n)
            break
    if not gradle:
        fail("app build.gradle(.kts) not found")
    g = read(gradle)
    g2, n = re.subn(r'applicationId\s*=?\s*"[^"]+"', f'applicationId = "{NEW_APP_ID}"' if gradle.endswith(".kts") else f'applicationId "{NEW_APP_ID}"', g)
    if n == 0:
        fail("applicationId not found in gradle file")
    write(gradle, g2)
    log(f"applicationId -> {NEW_APP_ID} ({n} place)")

    # ---- 4) manifest: internal identifiers + launcher
    m = read(manifest_path)
    renamed = set()

    def ident_repl(match):
        val = match.group(2)
        renamed.add(val)
        return f'{match.group(1)}"${{applicationId}}{val[len(OLD_PREFIX):]}"'

    # <action|category|permission|uses-permission|permission-group android:name="com.v2ray.ang.xxx">
    m = re.sub(r'(<(?:action|category|permission|uses-permission|permission-group)\b[^>]*?android:name=)"(' + re.escape(OLD_PREFIX) + r'\.[^"]+)"', ident_repl, m)
    # android:authorities / android:permission / android:taskAffinity attributes
    m = re.sub(r'(android:(?:authorities|permission|taskAffinity)=)"(' + re.escape(OLD_PREFIX) + r'(?:\.[^"]*)?)"', ident_repl, m)
    log(f"manifest identifiers renamed: {sorted(renamed)}")

    # remove LAUNCHER from existing activities, add ours
    m, nl = re.subn(r'\s*<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>', "", m)
    log(f"removed {nl} LAUNCHER categories")
    activity = f'''
        <activity
            android:name="{ACTIVITY}"
            android:exported="true"
            android:label="{APP_NAME}"
            android:screenOrientation="unspecified"
            android:theme="@android:style/Theme.Material.Light.NoActionBar"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
'''
    if "</application>" not in m:
        fail("</application> not found in manifest")
    m = m.replace("</application>", activity + "    </application>", 1)
    write(manifest_path, m)
    log("activation screen registered as launcher")

    # ---- 4b) code: literals that matched renamed manifest ids + ANG_PACKAGE
    changed_files = 0
    for dirpath, _, files in os.walk(java_root):
        for f in files:
            if not f.endswith((".kt", ".java")):
                continue
            p = os.path.join(dirpath, f)
            s = read(p)
            s2 = s
            for val in sorted(renamed, key=len, reverse=True):
                suffix = val[len(OLD_PREFIX):]
                expr = f'({BUILDCONFIG} + "{suffix}")' if suffix else f"({BUILDCONFIG})"
                s2 = s2.replace(f'"{val}"', expr)
            s2 = re.sub(r'(ANG_PACKAGE\s*(?::\s*String\s*)?=\s*)"' + re.escape(OLD_PREFIX) + '"', rf"\g<1>{BUILDCONFIG}", s2)
            if s2 != s:
                write(p, s2)
                changed_files += 1
                log(f"updated identifiers in {os.path.relpath(p, app)}")
    log(f"code files updated: {changed_files}")

    # diagnostics: remaining hard-coded package literals (for review in the build log)
    leftovers = []
    for dirpath, _, files in os.walk(java_root):
        for f in files:
            if f.endswith((".kt", ".java")):
                p = os.path.join(dirpath, f)
                for i, line in enumerate(read(p).splitlines(), 1):
                    if f'"{OLD_PREFIX}' in line and "pingless" not in p:
                        leftovers.append(f"{os.path.relpath(p, app)}:{i}: {line.strip()[:140]}")
    if leftovers:
        log("NOTE remaining literals (usually class names, harmless):")
        for x in leftovers[:40]:
            print("    " + x)
    log("done")


if __name__ == "__main__":
    main()
