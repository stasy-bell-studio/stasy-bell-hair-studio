# API foundation

No public customer booking or production deployment in stage 1. Health endpoints are unprotected; future owner/business endpoints must enforce authorization. Database migrations must run before starting the API.

The database enforces overlaps with a GiST exclusion constraint on the occupied time range. Pending holds are excluded ONLY after they are marked expired; later booking operations must first expire elapsed holds transactionally, and the worker must periodically do the same.

Money is represented as integer kopecks, durations as integer minutes, timestamps as timezone-aware instants.
