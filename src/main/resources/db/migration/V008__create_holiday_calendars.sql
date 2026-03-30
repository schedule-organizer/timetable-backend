-- V008__create_holiday_calendars.sql — HOL-01 (Epic 4)

CREATE TABLE holiday_calendars (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    academic_year_id BIGINT       NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    country          VARCHAR(100),
    region           VARCHAR(100),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, academic_year_id)
);

CREATE INDEX idx_holiday_calendars_tenant ON holiday_calendars(tenant_id);

CREATE TABLE holiday_dates (
    id                  BIGSERIAL    PRIMARY KEY,
    holiday_calendar_id BIGINT       NOT NULL REFERENCES holiday_calendars(id) ON DELETE CASCADE,
    tenant_id           BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    date                DATE         NOT NULL,
    type                VARCHAR(30)  NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_holiday_dates_calendar ON holiday_dates(holiday_calendar_id);
CREATE INDEX idx_holiday_dates_tenant ON holiday_dates(tenant_id);
