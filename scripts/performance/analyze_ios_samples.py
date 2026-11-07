#!/usr/bin/env python3
"""Summarize an Instruments time-profile XML export for XCalendar only.

Samples are statistical CPU evidence, not exact calls or recomposition counts.
Inclusive stack weights overlap and must not be summed across functions.
"""
import argparse
import collections
import json
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('xml')
    args = parser.parse_args()
    values = {}
    stacks = {}
    inclusive = collections.Counter()
    leaf = collections.Counter()
    threads = collections.Counter()
    total = 0
    samples = 0
    symbolized = 0
    for event, row in ET.iterparse(args.xml, events=['end']):
        if row.tag != 'row':
            continue
        for node in row.iter():
            identity = node.get('id')
            if not identity:
                continue
            if node.tag in ('thread', 'process'):
                values[identity] = node.get('fmt', '')
            elif node.tag == 'weight':
                values[identity] = int(node.text or 0)
            elif node.tag == 'frame':
                values[identity] = node.get('name', '')
            elif node.tag == 'tagged-backtrace':
                stacks[identity] = [frame.get('ref') or frame.get('id') for frame in node.findall('frame')]
        def value(tag):
            node = row.find(tag)
            return values.get(node.get('ref') or node.get('id')) if node is not None else None
        process = value('process') or ''
        if process.startswith('XCalendar ('):
            weight = value('weight') or 0
            total += weight
            samples += 1
            thread = value('thread') or ''
            threads['main' if thread.startswith('Main Thread') else 'background'] += weight
            stack = row.find('tagged-backtrace')
            names = [values.get(identity, '') for identity in stacks.get(stack.get('ref') or stack.get('id'), [])] if stack is not None else []
            if names:
                leaf[names[0]] += weight
                if any(not name.startswith('0x') for name in names):
                    symbolized += 1
                if thread.startswith('Main Thread'):
                    for name in set(names):
                        inclusive[name] += weight
        row.clear()
    def top(counter):
        return [{'name': name, 'sampled_ms': round(weight / 1e6, 3)} for name, weight in counter.most_common(35)]
    print(json.dumps({'samples': samples, 'samples_with_symbols': symbolized,
                      'sampled_cpu_ms': round(total / 1e6, 3),
                      'thread_sampled_ms': {key: round(value / 1e6, 3) for key, value in threads.items()},
                      'main_inclusive': top(inclusive),
                      'main_app_inclusive': top(collections.Counter({name: weight for name, weight in inclusive.items() if 'com.debanshu.xcalendar' in name})),
                      'main_compose_inclusive': top(collections.Counter({name: weight for name, weight in inclusive.items() if 'androidx.compose' in name})),
                      'leaf': top(leaf)}, indent=2))


if __name__ == '__main__':
    main()
