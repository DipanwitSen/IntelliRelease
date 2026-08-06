import { Pipe, PipeTransform } from '@angular/core';

/**
 * "3 minutes ago", "yesterday", "12 Mar".
 *
 * Uses `Intl.RelativeTimeFormat` so the wording is locale-correct without a
 * date library, and falls back to an absolute date past a week — "47 days ago"
 * is a worse answer than "12 Mar" for anything a person needs to correlate
 * with a calendar.
 *
 * Pure by default, which means it is not re-evaluated on a timer. That is the
 * right trade for this product: nothing here is a live clock, and a pipe that
 * marks itself impure would run on every change-detection pass across
 * thousands of table cells.
 */
@Pipe({ name: 'relativeTime', standalone: true })
export class RelativeTimePipe implements PipeTransform {
  private static readonly formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

  private static readonly UNITS: readonly { limitSeconds: number; perUnit: number; unit: Intl.RelativeTimeFormatUnit }[] = [
    { limitSeconds: 60, perUnit: 1, unit: 'second' },
    { limitSeconds: 3600, perUnit: 60, unit: 'minute' },
    { limitSeconds: 86_400, perUnit: 3600, unit: 'hour' },
    { limitSeconds: 604_800, perUnit: 86_400, unit: 'day' },
  ];

  transform(value: string | Date | null | undefined): string {
    if (!value) {
      return '—';
    }

    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '—';
    }

    const deltaSeconds = (date.getTime() - Date.now()) / 1000;
    const magnitude = Math.abs(deltaSeconds);

    for (const { limitSeconds, perUnit, unit } of RelativeTimePipe.UNITS) {
      if (magnitude < limitSeconds) {
        return RelativeTimePipe.formatter.format(Math.round(deltaSeconds / perUnit), unit);
      }
    }

    const sameYear = date.getFullYear() === new Date().getFullYear();
    return date.toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      year: sameYear ? undefined : 'numeric',
    });
  }
}

/** Absolute, unambiguous timestamp — for audit rows and tooltips. */
@Pipe({ name: 'absoluteTime', standalone: true })
export class AbsoluteTimePipe implements PipeTransform {
  transform(value: string | Date | null | undefined, withSeconds = false): string {
    if (!value) {
      return '—';
    }
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '—';
    }
    return date.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: withSeconds ? '2-digit' : undefined,
    });
  }
}

/** 1536 -> "1.5 KB". Used by the payload explorer and context package stats. */
@Pipe({ name: 'fileSize', standalone: true })
export class FileSizePipe implements PipeTransform {
  transform(bytes: number | null | undefined): string {
    if (bytes === null || bytes === undefined || Number.isNaN(bytes)) {
      return '—';
    }
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const units = ['KB', 'MB', 'GB'];
    let value = bytes / 1024;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
      value /= 1024;
      unitIndex++;
    }
    return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unitIndex]}`;
  }
}

/** Milliseconds -> "820 ms" / "1.4 s" / "2m 05s". */
@Pipe({ name: 'duration', standalone: true })
export class DurationPipe implements PipeTransform {
  transform(ms: number | null | undefined): string {
    if (ms === null || ms === undefined || Number.isNaN(ms)) {
      return '—';
    }
    if (ms < 1000) {
      return `${Math.round(ms)} ms`;
    }
    if (ms < 60_000) {
      return `${(ms / 1000).toFixed(1)} s`;
    }
    const minutes = Math.floor(ms / 60_000);
    const seconds = Math.round((ms % 60_000) / 1000);
    return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
  }
}
