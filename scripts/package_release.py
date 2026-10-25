#!/usr/bin/env python3
"""Package a reviewed allowlist only; never recurse into the clinical workspace."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FILES = '''.gitignore
.github/workflows/build.yml
README.md
README.zh-CN.md
LICENSE
CITATION.bib
LICENSE_STATUS.md
THIRD_PARTY_NOTICES.md
CHANGELOG.md
release.json
SHA256SUMS.txt
docs/INSTALL.md
docs/METHODS.md
docs/VALIDATION.md
docs/PUBLISH_CHECKLIST.md
scripts/build.py
scripts/package_release.py
src/main/java/PaC_DSA_2ROI.java
src/main/resources/plugins.config
tests/ReleaseTests.java
dist/PaC_DSA_2ROI.jar
examples/synthetic/synthetic_6fps.tif
examples/synthetic/synthetic_ROIs.zip
examples/synthetic/synthetic_curves.csv
examples/synthetic/expected_results.json
validation/build-and-test.json'''.splitlines()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    metadata = json.loads((ROOT / 'release.json').read_text(encoding='utf-8'))
    report = json.loads((ROOT / 'validation/build-and-test.json').read_text(encoding='utf-8'))
    source = (ROOT / 'src/main/java/PaC_DSA_2ROI.java').read_bytes()
    binary = (ROOT / 'dist/PaC_DSA_2ROI.jar').read_bytes()
    if (report['result'] != 'PASS' or report['package_version'] != metadata['package_version']
            or report['source_sha256'] != digest(source)
            or report['jar_sha256'] != digest(binary)
            or metadata['source_sha256'] != digest(source)):
        raise SystemExit('Build report is missing, failing or stale. Rebuild before packaging.')
    for line in (ROOT / 'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
        expected, name = line.split('  ', 1)
        if name not in FILES or digest((ROOT / name).read_bytes()) != expected:
            raise SystemExit('Checksum mismatch or unexpected file: ' + name)
    contents = {}
    for name in FILES:
        path = ROOT / name
        if path.is_symlink() or not path.is_file():
            raise SystemExit('Missing file or symlink refused: ' + name)
        contents[name] = path.read_bytes()
    manifest = {
        'package_version': metadata['package_version'],
        'status': metadata['status'],
        'license': metadata['license'],
        'copyright_holder': metadata['copyright_holder'],
        'repository': metadata['repository'],
        'algorithm_version': metadata['algorithm_version'],
        'archive_scope': 'Explicit reviewed file allowlist; synthetic fixtures only',
        'files': {name: digest(data) for name, data in sorted(contents.items())}
    }
    contents['PACKAGE_MANIFEST.json'] = (json.dumps(manifest, indent=2, ensure_ascii=False)+'\n').encode('utf-8')
    prefix = 'PaC_DSA_2ROI-' + metadata['package_version']
    output = ROOT.parent / (prefix + '-github-ready.zip')
    with zipfile.ZipFile(output, 'w') as archive:
        for name, data in sorted(contents.items()):
            entry = zipfile.ZipInfo(prefix + '/' + name, (2026, 10, 25, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise SystemExit('ZIP integrity check failed')
        for name, data in contents.items():
            if archive.read(prefix + '/' + name) != data:
                raise SystemExit('ZIP round-trip mismatch: ' + name)
    checksum = digest(output.read_bytes())
    output.with_suffix('.zip.sha256').write_text(checksum + '  ' + output.name + '\n', encoding='utf-8')
    print('Verified ' + str(len(contents)) + ' archive entries.')
    print(output)
    print('SHA256 ' + checksum)


if __name__ == '__main__':
    main()
