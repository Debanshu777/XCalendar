SELECT count(*) AS frames,
  sum(jank_tag = 'Self Jank') AS self_jank,
  sum(jank_type GLOB '*App Deadline Missed*') AS app_deadline_misses,
  round(PERCENTILE(f.dur, 50)/1e6, 3) AS p50_ms,
  round(PERCENTILE(f.dur, 95)/1e6, 3) AS p95_ms,
  round(PERCENTILE(f.dur, 99)/1e6, 3) AS p99_ms,
  round(max(f.dur)/1e6, 3) AS max_ms
FROM actual_frame_timeline_slice f JOIN process p USING(upid)
WHERE p.name = 'com.debanshu.xcalendar.benchmark' AND f.dur > 0;
