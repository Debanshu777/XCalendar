-- Run this query with trace_processor or use ../analyze_android.py trace.
-- Reject traces with packet loss / invalid-state skips before comparing counts.
SELECT name, severity, value FROM stats WHERE value != 0 AND
  (severity != 'info' OR name GLOB '*loss*' OR name GLOB '*dropped*'
    OR name GLOB '*skipped*' OR name GLOB '*overwritten*');
