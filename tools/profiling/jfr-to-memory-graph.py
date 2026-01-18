#!/usr/bin/env python3
"""
Parse JFR (Java Flight Recorder) heap summary events and generate memory usage graph.

Usage:
    jfr print --events jdk.GCHeapSummary recording.jfr | python3 jfr-to-memory-graph.py output.png

The script:
1. Parses JFR output to extract timestamp and heap usage
2. Calculates memory growth rate (MB/min)
3. Generates graph showing heap over time
4. Outputs assessment (linear growth = potential leak)
"""

import sys
import re
from datetime import datetime
from typing import List, Tuple
import subprocess

def parse_jfr_output(lines: List[str]) -> List[Tuple[datetime, float]]:
    """
    Parse jfr print output to extract (timestamp, heap_used_mb) pairs.

    Expected format:
    jdk.GCHeapSummary {
      startTime = 12:34:56.789
      heapUsed = 123456789
      ...
    }
    """
    datapoints = []
    current_event = {}

    for line in lines:
        line = line.strip()

        # New event starts
        if line.startswith('jdk.GCHeapSummary'):
            current_event = {}

        # Extract startTime
        if 'startTime' in line:
            # Format: startTime = 2026-01-18T12:34:56.789Z or 12:34:56.789
            match = re.search(r'startTime = (.+)', line)
            if match:
                time_str = match.group(1).strip()
                current_event['time_str'] = time_str

        # Extract heapUsed (in bytes)
        if 'heapUsed' in line:
            match = re.search(r'heapUsed = ([\d.]+)', line)
            if match:
                heap_bytes = float(match.group(1))
                heap_mb = heap_bytes / (1024 * 1024)  # Convert to MB
                current_event['heap_mb'] = heap_mb

        # Event complete (closing brace)
        if line == '}' and current_event:
            if 'time_str' in current_event and 'heap_mb' in current_event:
                # Store as (relative_time_seconds, heap_mb) for now
                # We'll process timestamps in a second pass
                datapoints.append((current_event['time_str'], current_event['heap_mb']))
            current_event = {}

    return datapoints

def normalize_timestamps(datapoints: List[Tuple[str, float]]) -> List[Tuple[float, float]]:
    """
    Convert timestamp strings to seconds since start of recording.
    Returns list of (seconds_since_start, heap_mb) tuples.
    """
    if not datapoints:
        return []

    # Parse first timestamp
    first_time_str = datapoints[0][0]

    # Try parsing different formats
    formats = [
        '%H:%M:%S.%f',  # 12:34:56.789
        '%Y-%m-%dT%H:%M:%S.%fZ',  # 2026-01-18T12:34:56.789Z
    ]

    first_time = None
    for fmt in formats:
        try:
            first_time = datetime.strptime(first_time_str, fmt)
            break
        except ValueError:
            continue

    if first_time is None:
        # Fallback: treat as seconds since epoch
        print(f"Warning: Could not parse timestamp format: {first_time_str}", file=sys.stderr)
        return [(float(i), heap) for i, (_, heap) in enumerate(datapoints)]

    # Convert all timestamps to seconds since start
    normalized = []
    for time_str, heap_mb in datapoints:
        try:
            t = datetime.strptime(time_str, formats[0])  # Use same format as first
            elapsed_seconds = (t - first_time).total_seconds()
            normalized.append((elapsed_seconds, heap_mb))
        except ValueError:
            continue

    return normalized

def calculate_growth_rate(datapoints: List[Tuple[float, float]]) -> Tuple[float, float, float]:
    """
    Calculate linear regression slope (MB/min) from datapoints.
    Returns (slope_mb_per_min, initial_mb, final_mb).
    """
    if len(datapoints) < 2:
        return (0.0, 0.0, 0.0)

    # Simple linear regression
    n = len(datapoints)
    sum_x = sum(t for t, _ in datapoints)
    sum_y = sum(h for _, h in datapoints)
    sum_xy = sum(t * h for t, h in datapoints)
    sum_x2 = sum(t * t for t, _ in datapoints)

    # slope = (n * sum_xy - sum_x * sum_y) / (n * sum_x2 - sum_x * sum_x)
    denominator = n * sum_x2 - sum_x * sum_x
    if denominator == 0:
        slope_mb_per_sec = 0.0
    else:
        slope_mb_per_sec = (n * sum_xy - sum_x * sum_y) / denominator

    slope_mb_per_min = slope_mb_per_sec * 60  # Convert to MB/min

    initial_mb = datapoints[0][1]
    final_mb = datapoints[-1][1]

    return (slope_mb_per_min, initial_mb, final_mb)

def generate_graph(datapoints: List[Tuple[float, float]], output_file: str, growth_rate: float):
    """
    Generate memory usage graph using matplotlib.
    """
    try:
        import matplotlib
        matplotlib.use('Agg')  # Non-interactive backend
        import matplotlib.pyplot as plt
    except ImportError:
        print("Error: matplotlib not installed. Install with: pip3 install matplotlib", file=sys.stderr)
        sys.exit(1)

    if not datapoints:
        print("Error: No datapoints to plot", file=sys.stderr)
        sys.exit(1)

    times = [t / 60 for t, _ in datapoints]  # Convert to minutes
    heaps = [h for _, h in datapoints]

    plt.figure(figsize=(12, 6))
    plt.plot(times, heaps, 'b-', linewidth=1, label='Heap Used')
    plt.xlabel('Time (minutes)')
    plt.ylabel('Heap Usage (MB)')
    plt.title(f'JVM Heap Usage Over Time\nGrowth Rate: {growth_rate:.2f} MB/min')
    plt.grid(True, alpha=0.3)
    plt.legend()

    # Add growth rate annotation
    if growth_rate > 5.0:
        assessment = "⚠️ LEAK DETECTED"
        color = 'red'
    elif growth_rate > 1.0:
        assessment = "⚠️ Potential leak"
        color = 'orange'
    else:
        assessment = "✅ Stable"
        color = 'green'

    plt.text(0.02, 0.98, assessment,
             transform=plt.gca().transAxes,
             fontsize=14, fontweight='bold',
             verticalalignment='top',
             bbox=dict(boxstyle='round', facecolor=color, alpha=0.3))

    plt.tight_layout()
    plt.savefig(output_file, dpi=100)
    print(f"Graph saved to: {output_file}")

def main():
    if len(sys.argv) < 2:
        print("Usage: jfr print --events jdk.GCHeapSummary recording.jfr | python3 jfr-to-memory-graph.py output.png", file=sys.stderr)
        print("   or: python3 jfr-to-memory-graph.py output.png < heap-metrics.txt", file=sys.stderr)
        sys.exit(1)

    output_file = sys.argv[1]

    # Read from stdin
    lines = sys.stdin.readlines()

    print(f"Parsing {len(lines)} lines of JFR output...", file=sys.stderr)

    # Parse JFR output
    datapoints_raw = parse_jfr_output(lines)
    print(f"Found {len(datapoints_raw)} heap summary events", file=sys.stderr)

    if not datapoints_raw:
        print("Error: No GCHeapSummary events found in input", file=sys.stderr)
        print("Make sure you're piping output from: jfr print --events jdk.GCHeapSummary", file=sys.stderr)
        sys.exit(1)

    # Normalize timestamps
    datapoints = normalize_timestamps(datapoints_raw)

    # Calculate growth rate
    growth_rate, initial_mb, final_mb = calculate_growth_rate(datapoints)

    # Print summary
    duration_min = datapoints[-1][0] / 60 if datapoints else 0
    print(f"\nMemory Analysis:", file=sys.stderr)
    print(f"  Duration: {duration_min:.1f} minutes", file=sys.stderr)
    print(f"  Initial heap: {initial_mb:.1f} MB", file=sys.stderr)
    print(f"  Final heap: {final_mb:.1f} MB", file=sys.stderr)
    print(f"  Growth: {final_mb - initial_mb:.1f} MB", file=sys.stderr)
    print(f"  Growth rate: {growth_rate:.2f} MB/min", file=sys.stderr)

    if growth_rate > 5.0:
        print(f"\n⚠️  LEAK DETECTED: Growth rate {growth_rate:.2f} MB/min indicates memory leak", file=sys.stderr)
    elif growth_rate > 1.0:
        print(f"\n⚠️  Potential leak: Growth rate {growth_rate:.2f} MB/min may indicate slow leak", file=sys.stderr)
    else:
        print(f"\n✅ Stable: Growth rate {growth_rate:.2f} MB/min is acceptable", file=sys.stderr)

    # Generate graph
    generate_graph(datapoints, output_file, growth_rate)

if __name__ == '__main__':
    main()