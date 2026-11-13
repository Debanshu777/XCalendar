-- Compare each app surface frame with its scheduled end time. This is not
-- presentation cadence; frame pipelines can overlap. Unmatched/duplicate
-- expected records are reported rather than silently inflating the sample.
WITH expected AS (
  SELECT upid, surface_frame_token, count(*) AS matches,
         max(ts + dur) AS deadline, max(dur) AS budget
  FROM expected_frame_timeline_slice
  WHERE dur > 0 AND surface_frame_token > 0
  GROUP BY upid, surface_frame_token
), frames AS (
  SELECT a.id, e.matches, e.budget,
         (a.ts + a.dur - e.deadline) / 1e6 AS overrun_ms
  FROM actual_frame_timeline_slice a JOIN process p USING (upid)
  LEFT JOIN expected e ON e.upid = a.upid AND e.surface_frame_token = a.surface_frame_token
  WHERE p.name = 'com.debanshu.xcalendar.benchmark'
    AND a.dur > 0 AND a.surface_frame_token > 0
)
SELECT count(*) AS frames,
       sum(matches IS NULL OR matches != 1) AS unmatched_or_ambiguous,
       sum(matches = 1) AS matched_frames,
       sum(matches = 1 AND overrun_ms > 0) AS late_frames,
       round(PERCENTILE(CASE WHEN matches = 1 THEN overrun_ms END, 95), 3) AS p95_overrun_ms,
       round(PERCENTILE(CASE WHEN matches = 1 THEN overrun_ms END, 99), 3) AS p99_overrun_ms,
       round(PERCENTILE(CASE WHEN matches = 1 THEN budget / 1e6 END, 50), 3) AS median_scheduled_budget_ms
FROM frames;
