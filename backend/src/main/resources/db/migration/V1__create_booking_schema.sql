CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(30) NOT NULL,
    city VARCHAR(80) NOT NULL,
    venue VARCHAR(150) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_events_city_starts_at ON events (LOWER(city), starts_at);

CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    seat_label VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    hold_id UUID,
    held_by VARCHAR(100),
    hold_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_event_seat UNIQUE (event_id, seat_label),
    CONSTRAINT chk_hold_fields CHECK (
        (status = 'HELD' AND hold_id IS NOT NULL AND held_by IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR
        (status <> 'HELD' AND hold_id IS NULL AND held_by IS NULL AND hold_expires_at IS NULL)
    )
);

CREATE INDEX idx_show_seats_expiry ON show_seats (hold_expires_at)
    WHERE status = 'HELD';

INSERT INTO events (name, category, city, venue, starts_at) VALUES
    ('Pune City FC vs Mumbai United', 'SPORTS', 'Pune', 'Balewadi Stadium', CURRENT_TIMESTAMP + INTERVAL '2 days'),
    ('AI Film Festival', 'MOVIE', 'Pune', 'Demo Multiplex', CURRENT_TIMESTAMP + INTERVAL '3 days'),
    ('Indie Music Evening', 'LIVE', 'Pune', 'Riverside Arena', CURRENT_TIMESTAMP + INTERVAL '4 days'),
    ('Mumbai City FC Final', 'SPORTS', 'Mumbai', 'City Stadium', CURRENT_TIMESTAMP + INTERVAL '5 days');

INSERT INTO show_seats (event_id, seat_label, status)
SELECT event_id, row_name || seat_number, 'AVAILABLE'
FROM generate_series(1, 4) AS event_id
CROSS JOIN (VALUES ('A'), ('B')) AS rows(row_name)
CROSS JOIN generate_series(1, 6) AS seat_number;
