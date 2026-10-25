#!/usr/bin/env python3
"""Build and test with standard-library Python and a JDK; no patient data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def executable(name, java_home):
    suffix = '.exe' if os.name == 'nt' else ''
    path = Path(java_home) / 'bin' / (name + suffix) if java_home else shutil.which(name)
    if not path or not Path(path).is_file():
        raise SystemExit('Install JDK 21 and set JAVA_HOME, or pass --java-home.')
    return str(path)


def run(command):
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, encoding='utf-8', errors='replace')
    if result.returncode:
        raise SystemExit(result.stdout)
    return result.stdout.strip()


def jar_entry(archive, name, data):
    entry = zipfile.ZipInfo(name, (2026, 10, 25, 0, 0, 0))
    entry.compress_type = zipfile.ZIP_DEFLATED
    entry.create_system = 3
    entry.external_attr = 0o100644 << 16
    archive.writestr(entry, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--imagej-jar', type=Path, help='Offline pinned ImageJ dependency')
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args()
    metadata = json.loads((ROOT / 'release.json').read_text(encoding='utf-8'))
    source = ROOT / 'src/main/java/PaC_DSA_2ROI.java'
    if sha(source) != metadata['source_sha256']:
        raise SystemExit('Source differs from the declared algorithm snapshot. Review and update release.json explicitly.')
    java = executable('java', args.java_home)
    javac = executable('javac', args.java_home)
    dist = ROOT / 'dist'
    examples = ROOT / 'examples/synthetic'
    reports = ROOT / 'validation'
    for directory in (dist, examples, reports):
        directory.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='pac-dsa-build-') as tmp:
        tmp = Path(tmp)
        dependency = args.imagej_jar.resolve() if args.imagej_jar else tmp / 'ij.jar'
        if not args.imagej_jar:
            urllib.request.urlretrieve(metadata['imagej_dependency']['url'], dependency)
        if sha(dependency) != metadata['imagej_dependency']['sha256']:
            raise SystemExit('ImageJ SHA-256 mismatch; dependency not used.')
        classes = tmp / 'classes'
        classes.mkdir()
        run([javac, '--release', '8', '-encoding', 'UTF-8', '-cp', str(dependency),
             '-d', str(classes), str(source), str(ROOT / 'tests/ReleaseTests.java')])
        jar = dist / 'PaC_DSA_2ROI.jar'
        manifest = ('Manifest-Version: 1.0\r\nImplementation-Title: PaC DSA 2ROI\r\n'
                    'Implementation-Version: ' + metadata['algorithm_version'] + '\r\n'
                    'Package-Release: ' + metadata['package_version'] + '\r\n'
                    'License: MIT\r\n\r\n')
        with zipfile.ZipFile(jar, 'w') as archive:
            jar_entry(archive, 'META-INF/MANIFEST.MF', manifest.encode())
            jar_entry(archive, 'META-INF/LICENSE', (ROOT / 'LICENSE').read_bytes())
            jar_entry(archive, 'META-INF/THIRD_PARTY_NOTICES.md', (ROOT / 'THIRD_PARTY_NOTICES.md').read_bytes())
            for path in sorted(classes.glob('PaC_DSA_2ROI*.class')):
                jar_entry(archive, path.name, path.read_bytes())
            jar_entry(archive, 'plugins.config', (ROOT / 'src/main/resources/plugins.config').read_bytes())
        # Put the actual distributable JAR first: exercise its classes, not a
        # separately packaged or stale installation of the plugin.
        output = run([java, '-Djava.awt.headless=true', '-cp',
                      os.pathsep.join([str(jar), str(classes), str(dependency)]),
                      'ReleaseTests', str(examples)])
        print(output)
        report = {
            'package_version': metadata['package_version'],
            'algorithm_version': metadata['algorithm_version'],
            'source_sha256': sha(source), 'jar_sha256': sha(jar),
            'dependency_sha256': sha(dependency),
            'platform': platform.system() + ' ' + platform.release() + ' ' + platform.machine(),
            'javac': run([javac, '-version']),
            'java': run([java, '-version']),
            'bytecode_target': 'Java 8 (runtime tested on the recorded JDK only)',
            'suite': 'Public synthetic-only headless tests', 'result': 'PASS',
            'test_output': output.splitlines(),
            'limitations': ['No clinical accuracy claim', 'No spatial repeatability claim',
                            'No GUI automation or other operating-system runtime validation in this local report']
        }
        (reports / 'build-and-test.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        files = [source, jar, ROOT / 'LICENSE', ROOT / 'CITATION.bib',
                 *sorted(examples.iterdir()), reports / 'build-and-test.json']
        (ROOT / 'SHA256SUMS.txt').write_text(''.join(sha(p) + '  ' + p.relative_to(ROOT).as_posix() + '\n'
                                                 for p in files if p.is_file()), encoding='utf-8')
        print('Built dist/PaC_DSA_2ROI.jar; tests PASS; examples and validation updated.')


if __name__ == '__main__':
    main()
