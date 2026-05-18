create schema if not exists booking;
create extension if not exists pg_trgm;

create table if not exists booking.branch (
  id            uuid         primary key,
  code          varchar(40)  not null,
  name          varchar(60)  not null,
  city          varchar(60)  not null,
  province      varchar(60),
  country       varchar(40)  not null,
  address       varchar(120),
  opening_time  time         not null,
  closing_time  time         not null,
  active        boolean      not null default true,
  admin_email   varchar(60)
);
create unique index if not exists branch_code_uidx on booking.branch (lower(code));
create index if not exists branch_city_idx on booking.branch (lower(city));
create index if not exists branch_admin_email_active_name_idx
  on booking.branch (lower(admin_email), active desc, name asc)
  where admin_email is not null;
create index if not exists branch_search_trgm_idx
  on booking.branch using gin (
    lower(code || ' ' || name || ' ' || city || ' ' || coalesce(province, '') || ' ' || country || ' ' || coalesce(address, '')) gin_trgm_ops
  );

create table if not exists booking.booking (
  id                  uuid         primary key,
  booking_reference   varchar(32)  not null constraint uq_booking_reference unique,
  idempotency_key     varchar(80)  not null constraint uq_booking_idempotency_key unique,
  branch_id           uuid         not null,
  start_datetime      timestamp    not null,
  end_datetime        timestamp    not null,
  customer_name       varchar(160) not null,
  customer_email      varchar(254) not null,
  preferred_language  varchar(16)  not null,
  status              varchar(24)  not null,
  created_at          timestamptz  not null default now(),
  updated_at          timestamptz  not null default now()
);
create index if not exists booking_customer_email_start_idx
  on booking.booking (lower(customer_email), start_datetime desc);
create index if not exists booking_upcoming_customer_confirmed_idx
  on booking.booking (lower(customer_email), start_datetime asc)
  where status = 'CONFIRMED';
create index if not exists booking_admin_confirmed_branch_start_customer_idx
  on booking.booking (branch_id, start_datetime asc, customer_name asc)
  where status = 'CONFIRMED';
create unique index if not exists uq_booking_confirmed_branch_start_idx
  on booking.booking (branch_id, start_datetime)
  where status = 'CONFIRMED';

insert into booking.branch (
  id, code, name, city, province, country, address, opening_time, closing_time, active, admin_email
) values
  ('1a2b3c4d-0001-4a66-9f4b-0d745e9f0001', 'CPT-BLV', 'Capitec Bellville',        'Cape Town',    'Western Cape',  'South Africa', 'Capitec Bellville, Cape Town',        time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0002-4a66-9f4b-0d745e9f0002', 'PE-BDW',  'Capitec Boardwalk',        'Gqeberha',     'Eastern Cape',  'South Africa', 'Capitec Boardwalk, Gqeberha',         time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0003-4a66-9f4b-0d745e9f0003', 'CPT-CBD', 'Capitec Cape Town CBD',    'Cape Town',    'Western Cape',  'South Africa', 'Capitec Cape Town CBD, Cape Town',    time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0004-4a66-9f4b-0d745e9f0004', 'DBN-GTW', 'Capitec Gateway Umhlanga', 'Durban',       'KwaZulu-Natal', 'South Africa', 'Capitec Gateway Umhlanga, Durban',    time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0005-4a66-9f4b-0d745e9f0005', 'PTA-MEN', 'Capitec Menlyn Maine',     'Pretoria',     'Gauteng',       'South Africa', 'Capitec Menlyn Maine, Pretoria',      time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0006-4a66-9f4b-0d745e9f0006', 'BFN-MMS', 'Capitec Mimosa Mall',      'Bloemfontein', 'Free State',    'South Africa', 'Capitec Mimosa Mall, Bloemfontein',   time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0007-4a66-9f4b-0d745e9f0007', 'MBL-RVS', 'Capitec Riverside Mall',   'Mbombela',     'Mpumalanga',    'South Africa', 'Capitec Riverside Mall, Mbombela',    time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0008-4a66-9f4b-0d745e9f0008', 'JHB-RBK', 'Capitec Rosebank Mall',    'Johannesburg', 'Gauteng',       'South Africa', 'Capitec Rosebank Mall, Johannesburg', time '09:00', time '16:00', true, null),
  ('0d0fb1e2-3d44-4a66-9f4b-0d745e9f1a03', 'JHB-SDT', 'Capitec Sandton City',     'Johannesburg', 'Gauteng',       'South Africa', 'Capitec Sandton City, Johannesburg',  time '09:00', time '16:00', true, null),
  ('1a2b3c4d-0009-4a66-9f4b-0d745e9f0009', 'DBN-PAV', 'Capitec The Pavilion',     'Durban',       'KwaZulu-Natal', 'South Africa', 'Capitec The Pavilion, Durban',        time '09:00', time '16:00', true, null)
on conflict do nothing;
