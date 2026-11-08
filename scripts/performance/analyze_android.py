#!/usr/bin/env python3
"""Run the checked-in Perfetto queries, one result set per file.

Inspect health.txt first. Nonzero loss, overwritten buffer, invalid-state or
parser-error counters invalidate full-workload comparisons.
"""
import argparse
import pathlib
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--processor', type=pathlib.Path, required=True)
    parser.add_argument('--trace', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    processor = args.processor.resolve()
    with processor.open('rb') as source:
        header = source.read(200)
    command = [sys.executable, str(processor)] if b'python' in header.split(b'\n')[0] else [str(processor)]
    queries = pathlib.Path(__file__).with_name('android_queries')
    for name in ('health', 'frames', 'composition', 'cpu'):
        with (args.output / (name + '.txt')).open('w') as output:
            subprocess.run(command + ['-q', str(queries / (name + '.sql')), str(args.trace)], stdout=output, check=True)


if __name__ == '__main__':
    main()
