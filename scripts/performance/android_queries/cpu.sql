SELECT t.name, t.is_main_thread, round(sum(s.dur)/1e6, 3) AS running_cpu_ms
FROM sched s JOIN thread t USING(utid) JOIN process p USING(upid)
WHERE p.name = 'com.debanshu.xcalendar.benchmark' AND s.dur > 0
GROUP BY t.utid ORDER BY running_cpu_ms DESC LIMIT 12;
