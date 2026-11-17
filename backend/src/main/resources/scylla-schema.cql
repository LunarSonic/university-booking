CREATE KEYSPACE IF NOT EXISTS university_events
    WITH replication = {'class': 'NetworkTopologyStrategy', 'datacenter1': 3}
    AND durable_writes = true;

CREATE TABLE IF NOT EXISTS university_events.events_by_result_day (
    result      text,
    day_utc     text,
    occurred_at timestamp,
    event_id    uuid,
    operation   text,
    booking_id  bigint,
    error_code  text,
    PRIMARY KEY ((result, day_utc), occurred_at, event_id)
) WITH CLUSTERING ORDER BY (occurred_at DESC, event_id DESC);

CREATE TABLE IF NOT EXISTS university_events.events_by_operation_day (
    operation   text,
    day_utc     text,
    occurred_at timestamp,
    event_id    uuid,
    result      text,
    booking_id  bigint,
    error_code  text,
    PRIMARY KEY ((operation, day_utc), occurred_at, event_id)
) WITH CLUSTERING ORDER BY (occurred_at DESC, event_id DESC);

CREATE TABLE IF NOT EXISTS university_events.events_by_booking_day (
    booking_id  bigint,
    day_utc     text,
    occurred_at timestamp,
    event_id    uuid,
    operation   text,
    result      text,
    error_code  text,
    PRIMARY KEY ((booking_id, day_utc), occurred_at, event_id)
) WITH CLUSTERING ORDER BY (occurred_at DESC, event_id DESC);
