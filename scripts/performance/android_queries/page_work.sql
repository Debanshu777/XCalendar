-- Main-thread page lifecycle hotspots. Durations are inclusive and overlap;
-- do not add these rows together or interpret them as CPU samples.
SELECT s.name, count(*) AS calls,
  round(sum(s.dur) / 1e6, 3) AS total_ms,
  round(max(s.dur) / 1e6, 3) AS worst_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid)
JOIN process p USING (upid)
WHERE p.name = 'com.debanshu.xcalendar.benchmark'
  AND t.is_main_thread = 1 AND s.dur > 0
  AND (s.name IN ('AndroidOwner:measureAndLayout', 'Compose:deactivate',
                 'Compose:onForgotten', 'Compose:applyChanges', 'Compose:recompose')
       OR s.name GLOB '*prefetch*')
GROUP BY s.name
ORDER BY worst_ms DESC;
