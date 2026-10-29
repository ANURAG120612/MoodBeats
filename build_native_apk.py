import os
import sys
import glob
import subprocess
import shutil

JAVA_HOME = r"C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
os.environ["JAVA_HOME"] = JAVA_HOME
os.environ["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + os.environ.get("PATH", "")

SDK_ROOT = r"C:\Users\admin\AppData\Local\Android\Sdk"
BUILD_TOOLS = os.path.join(SDK_ROOT, "build-tools", "34.0.0")
PLATFORM = os.path.join(SDK_ROOT, "platforms", "android-34", "android.jar")
PROJECT_DIR = r"D:\MoodBeats_Android"

AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe")
JAR = os.path.join(JAVA_HOME, "bin", "jar.exe")

def run(cmd, cwd=PROJECT_DIR, check=True):
    print(f"[*] Running: {' '.join(cmd) if isinstance(cmd, list) else cmd}")
    res = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), capture_output=True, text=True)
    if res.stdout:
        print(res.stdout)
    if res.stderr:
        print(res.stderr, file=sys.stderr)
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed with return code {res.returncode}")
    return res

def main():
    print("========================================================")
    print("       Building Pure Native MoodBeats Android APK       ")
    print("========================================================")

    # 1. Clean dirs
    for d in ["build", "gen", "bin", "dex"]:
        p = os.path.join(PROJECT_DIR, d)
        if os.path.exists(p):
            shutil.rmtree(p)
        os.makedirs(p, exist_ok=True)

    # 2. AAPT2 Compile
    print("\n[Step 1/6] Compiling resources with AAPT2...")
    res_dir = os.path.join(PROJECT_DIR, "app", "src", "main", "res")
    compiled_res = os.path.join(PROJECT_DIR, "build", "compiled_res.zip")
    run([AAPT2, "compile", "--dir", res_dir, "-o", compiled_res])

    # 3. AAPT2 Link
    print("\n[Step 2/6] Linking resources and generating R.java...")
    manifest = os.path.join(PROJECT_DIR, "app", "src", "main", "AndroidManifest.xml")
    gen_dir = os.path.join(PROJECT_DIR, "gen")
    assets_dir = os.path.join(PROJECT_DIR, "app", "src", "main", "assets")
    base_apk = os.path.join(PROJECT_DIR, "build", "base.apk")
    run([
        AAPT2, "link", compiled_res,
        "-I", PLATFORM,
        "--manifest", manifest,
        "--java", gen_dir,
        "-A", assets_dir,
        "-o", base_apk,
        "--auto-add-overlay"
    ])

    # 4. Javac Compile
    print("\n[Step 3/6] Compiling Java classes with javac...")
    r_java = os.path.join(gen_dir, "com", "aurabeats", "moodbeats", "R.java")
    java_srcs = glob.glob(os.path.join(PROJECT_DIR, "app", "src", "main", "java", "com", "aurabeats", "moodbeats", "*.java"))
    all_sources = [r_java] + java_srcs
    bin_dir = os.path.join(PROJECT_DIR, "bin")
    run([
        JAVAC, "-cp", PLATFORM,
        "-d", bin_dir,
        "-encoding", "UTF-8",
        "-source", "1.8", "-target", "1.8"
    ] + all_sources)

    # 5. D8 Dexing
    print("\n[Step 4/6] Converting classes to DEX with D8...")
    class_files = []
    for root, _, files in os.walk(bin_dir):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))
    dex_dir = os.path.join(PROJECT_DIR, "dex")
    run([D8, "--lib", PLATFORM, "--output", dex_dir] + class_files)

    # 6. Packaging classes.dex into base.apk
    print("\n[Step 5/6] Packaging classes.dex and aligning APK...")
    dex_file = os.path.join(dex_dir, "classes.dex")
    run([JAR, "uf", base_apk, "-C", dex_dir, "classes.dex"])
    aligned_apk = os.path.join(PROJECT_DIR, "build", "aligned.apk")
    run([ZIPALIGN, "-f", "-p", "4", base_apk, aligned_apk])

    # 7. Signing APK
    print("\n[Step 6/6] Signing APK with apksigner...")
    keystore = os.path.join(PROJECT_DIR, "debug.keystore")
    final_apk = os.path.join(PROJECT_DIR, "MoodBeats.apk")
    run([
        APKSIGNER, "sign",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--ks", keystore,
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--out", final_apk,
        aligned_apk
    ])

    # 8. Copy to D:\MoodBeats\MoodBeats.apk
    copy_target = r"D:\MoodBeats\MoodBeats.apk"
    shutil.copy2(final_apk, copy_target)

    # 9. Verify Signature
    print("\n[Verification] Verifying APK signature...")
    run([APKSIGNER, "verify", "--verbose", final_apk])

    print("\n========================================================")
    print("       APK BUILD COMPLETED SUCCESSFULLY!                ")
    print(f" Primary APK: {final_apk}")
    print(f" Root Copy:   {copy_target}")
    print(f" Size:        {os.path.getsize(final_apk) / (1024*1024):.2f} MB")
    print("========================================================")

if __name__ == "__main__":
    main()
