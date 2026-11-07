-- Inclusive durations overlap: do not add these rows together.
-- Executions include initial composition as well as recomposition.
INCLUDE PERFETTO MODULE slices.with_context;
SELECT name, count(*) AS executions, round(sum(dur)/1e6, 3) AS inclusive_ms,
  round(max(dur)/1e6, 3) AS max_ms
FROM thread_slice WHERE process_name = 'com.debanshu.xcalendar.benchmark'
  AND dur > 0 AND (name GLOB 'com.debanshu.xcalendar*' OR name IN
    ('Compose:recompose', 'Recomposer:recompose', 'measureAndLayout',
     'compose:lazy:prefetch:measure', 'Compose:deactivate', 'Compose:onForgotten'))
GROUP BY name ORDER BY inclusive_ms DESC;
