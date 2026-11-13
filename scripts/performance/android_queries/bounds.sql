-- Convert trace-clock bounds with the nearest endpoint REALTIME snapshots.
-- Used to reject mocked delivery outside the actual recorded interval.
SELECT
  (start_ts + (SELECT clock_value - ts FROM clock_snapshot
    WHERE clock_name = 'REALTIME' ORDER BY ts LIMIT 1)) / 1e9 AS start_epoch_seconds,
  (end_ts + (SELECT clock_value - ts FROM clock_snapshot
    WHERE clock_name = 'REALTIME' ORDER BY ts DESC LIMIT 1)) / 1e9 AS end_epoch_seconds
FROM trace_bounds;
