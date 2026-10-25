# Release review checklist

## Required decisions

- [x] Record planned repository name `PaC-DSA-2ROI` and public visibility.
- [x] Record the user's confirmation of personal ownership and copyright name `yujiechen`.
- [x] Apply the approved MIT license, update metadata and add a suggested citation.
- [ ] Complete applicable third-party source compliance review; the owner's declaration is not an independent audit.
- [x] Verify the intended GitHub account and create the public repository: https://github.com/Chenyujiemed123a/PaC-DSA-2ROI (2026-10-25).
- [ ] Add contact details only if separately approved by the owner; none have been invented.
- [x] Distinguish research validation limits from MIT usage permissions.

## Technical review

- [x] Isolate this directory from the private clinical working directory.
- [x] Preserve the algorithm source and record its SHA-256.
- [x] Include only synthetic public fixtures.
- [x] Build and run the 10 synthetic test groups locally.
- [x] Include settings, method limitations and explicit quality-status meanings.
- [ ] Run the GitHub Actions matrix and review actual macOS/Windows/Linux results.
- [ ] Smoke-test menu registration, example loading, settings, curve/Log/Results and file export in each intended GUI environment.
- [ ] Review every tracked filename, file content, Git metadata, screenshot and archive for personal/clinical data or secrets.

## Create the real source record

Start a repository **only inside this isolated release directory**, never in the parent clinical workspace. After the required rights/repository decisions:

1. Initialize the repository or import into the approved empty repository.
2. Review exactly which files will be tracked, including source, docs, synthetic fixtures and validation report. Do not add external folders.
3. Create a reviewed first commit with the real maintainer identity. Record `git rev-parse HEAD` in the GitHub release notes. There is no verified earlier historical commit to cite.
4. Use a tag such as `v2026.10.25-rc.2` for this package; state separately that its algorithm version is `2026.10.07-quality.1`.
5. Upload only to the approved repository, then review CI. Repository creation does not itself confirm file upload or test success.
6. Create a prerelease, attach the tested `dist/PaC_DSA_2ROI.jar`, source package and checksums; publish only after review. Keep a prerelease label until all claimed environments and licensing are ready.

Do not claim the local ZIP is a published GitHub release or has a DOI. The included MIT license is owner-authorized, but this does not establish an actual publication or verify third-party provenance. Do not rewrite old patient measurements merely to match tests.

## Draft release summary

> PaC DSA 2ROI package 2026.10.25-rc.2, algorithm 2026.10.07-quality.1. This packaging release preserves the measurement source and includes a menu entry, reproducible build steps, fully synthetic example data, 10 local automated test groups, and documentation. Local fit acceptance is not peak-selection stability validation. Repository commit: **fill with actual release commit**. License: MIT, Copyright (c) 2026 yujiechen. Clinical accuracy is not established.
