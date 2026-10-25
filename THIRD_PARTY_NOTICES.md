# Third-party components

## ImageJ1

- Build/runtime API: `net.imagej:ij:1.54p`.
- Dependency source: https://repo.maven.apache.org/maven2/net/imagej/ij/1.54p/ij-1.54p.jar
- Pinned SHA-256: `2e1a09961dfb41cee66ddc821b2577a41a072566ce45a49bae69267099741e20`.
- Upstream: https://imagej.net/ and https://github.com/imagej/ImageJ
- Upstream licensing information: https://imagej.net/licensing/

ImageJ1 is described by upstream as public-domain software. Fiji includes additional components with their own licenses; this statement does not license all Fiji components or this plugin. Neither ImageJ's JAR nor a JDK/Fiji distribution is included in this release candidate.

## Build automation

The CI workflow references the official `actions/checkout`, `actions/setup-java`, and `actions/setup-python` repositories using fixed commits. Their source is not bundled. Consult the respective upstream repositories for their license terms. The build uses Python's standard library and a separately installed JDK.

## Provenance limitation

The historical algorithm snapshot has no available Git commit in the working project. On 2026-10-25, the owner supplied the name `yujiechen`, confirmed personal ownership and authorized MIT licensing; the project's root `LICENSE` applies to this snapshot. This records the owner's declaration, not an independent provenance audit. Any incorporated third-party code must still retain its required notices and comply with its own terms. The project MIT notice does not relicense third-party material. See `LICENSE_STATUS.md`.
