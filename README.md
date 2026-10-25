# PaC DSA 2ROI

A Java plugin for ImageJ1/Fiji that extracts time-to-peak (TTP) from two area ROIs in an image stack and reports the signed difference, **ROI 2 − ROI 1**.

[中文说明](README.zh-CN.md) · [Installation](docs/INSTALL.md) · [Method and limitations](docs/METHODS.md) · [Validation](docs/VALIDATION.md)

## Release status

This is release candidate **2026.10.25-rc.2**, prepared for the public repository linked below. A formal release tag and GitHub CI results must be checked on the repository; local tests alone are not a final release certification.

| Item | Value |
| --- | --- |
| Package release | `2026.10.25-rc.2` |
| Measurement algorithm / displayed plugin version | `2026.10.07-quality.1` |
| Language | Java, ImageJ1 `PlugIn` API |
| Reference dependency | `net.imagej:ij:1.54p` |
| Local tested runtime | macOS arm64, Zulu OpenJDK 21.0.7; see validation report |
| GitHub repository | [Chenyujiemed123a/PaC-DSA-2ROI](https://github.com/Chenyujiemed123a/PaC-DSA-2ROI) |
| Historical source commit | Unavailable; this snapshot is identified by SHA-256 |
| License | [MIT](LICENSE), Copyright (c) 2026 yujiechen |

The measurement source is unchanged from the existing local algorithm. Packaging adds an explicit Plugins menu entry, build tooling, documentation, and synthetic tests. The packaging date is not a new algorithm version.

Source SHA-256: `8509c67f5c59933f3162ca38c3cd01726d080b3bca80bf9e988eb23530a1eabe`.

## Quick start

1. Back up any existing `PaC_DSA_2ROI.jar` **outside** Fiji's plugins folder. Remove duplicate plugin classes/JARs from that folder.
2. Copy `dist/PaC_DSA_2ROI.jar` into Fiji's `plugins` folder and restart Fiji.
3. Open `examples/synthetic/synthetic_6fps.tif`.
4. Open ROI Manager via **Analyze → Tools → ROI Manager**. Load `examples/synthetic/synthetic_ROIs.zip` using **More → Open**. Use an empty manager for this example.
5. Select **Plugins → PaC DSA 2ROI**. Use automatic mode, smoothing radius 1, sustained-rise span 3, noise multiplier 3, threshold 90%, maximum first-pass duration 3 s, frame rate 6 frames/s.
6. Expected integer results: proximal TTP **17**, distal TTP **19**, ΔTTP **+2 frames**, or **1/3 s**. A locally accepted parabola is not evidence of repeatability.

Only mathematically generated pixels and traces are included. No clinical DICOM, patient screenshot, name, or measurement spreadsheet is included.

## Build and test

Developer prerequisites: JDK 21 and Python 3.9+ (standard library only). Python is **not required to run the plugin** in Fiji.

```sh
python3 scripts/build.py
```

On Windows, use `python scripts/build.py`. Set `JAVA_HOME` to the JDK, or pass `--java-home /path/to/jdk`. The build downloads one fixed ImageJ dependency from Maven Central and verifies its SHA-256. For offline builds:

```sh
python3 scripts/build.py --imagej-jar /path/to/ij-1.54p.jar --java-home /path/to/jdk
```

The script compiles Java 8-targeted bytecode, runs 10 synthetic test groups, generates the synthetic TIFF/ROI/curve example, and writes `dist/PaC_DSA_2ROI.jar`, `validation/build-and-test.json`, and `SHA256SUMS.txt`. Java 8 bytecode targeting is not a claim that every Java 8/Fiji installation has been tested.

After review, `python3 scripts/package_release.py` creates a ZIP beside this directory using an explicit file allowlist, verifies its contents and writes a SHA-256 sidecar. It does not upload anything or include the surrounding workspace.

GitHub Actions is configured for macOS, Windows, and Linux with read-only repository permissions. Those jobs have **not** run on GitHub yet; local test results are not cross-platform certification.

## Scientific scope

This software estimates image-intensity peak timing. It does not directly measure volumetric blood flow, automatically establish anatomical suitability, or validate ROI placement. Integer-frame and local parabolic estimates must remain distinct. The reported `Peak_selection_stability` is deliberately `Not tested`; repeated human ROI placement is an external workflow.

Research software; no diagnostic or clinical validation is claimed. This statement describes validation scope, not a restriction added to the MIT license. Review [METHODS](docs/METHODS.md) and [VALIDATION](docs/VALIDATION.md) before interpreting results.

## License and citation

Licensed under [MIT](LICENSE), as authorized by the owner, `yujiechen`. The license is also included in the plugin JAR. See [third-party notices](THIRD_PARTY_NOTICES.md) for separately supplied components.

If using the software in research, please cite the version actually used. A suggested [BibTeX citation](CITATION.bib) preserves the supplied author name verbatim and includes the repository URL. No paper or software DOI is assigned. Record the actual Git commit or release tag used; citing a paper is not an added MIT condition.

## Release review

Follow [PUBLISH_CHECKLIST](docs/PUBLISH_CHECKLIST.md). MIT and personal ownership have been confirmed by the user. The public repository was created on 2026-10-25. Outstanding review items and the actual tagged release status remain distinct from repository creation. Do not invent a historical commit or use this snapshot hash as a Git commit. See [LICENSE_STATUS](LICENSE_STATUS.md).
