import {
  buildGroupedHalfHourTimeSlots,
  buildHalfHourTimeSlots,
  formatBookingDateTimeTime,
  fromIsoDate,
  getSystemTimeZone,
  isDateRangeFilterValid,
  roundDownToHalfHour,
  toApiTime,
  toHourMinuteTime,
  toIsoDate,
  toSystemUtcIsoDateTime
} from './date-time.utils';

describe('date-time utilities', () => {
  it('formats and parses ISO dates using the local calendar date', () => {
    const date = new Date(2026, 3, 30);

    expect(toIsoDate(date)).toBe('2026-04-30');
    expect(fromIsoDate('2026-04-30')).toEqual(date);
  });

  it('rounds time down to the nearest half hour', () => {
    expect(roundDownToHalfHour('09:01')).toBe('09:00');
    expect(roundDownToHalfHour('09:45')).toBe('09:30');
  });

  it('converts between hour-minute and API time formats', () => {
    expect(toApiTime('14:30')).toBe('14:30:00');
    expect(toApiTime('14:30:15')).toBe('14:30:15');
    expect(toHourMinuteTime('14:30:15')).toBe('14:30');
  });

  it('validates complete date range filters', () => {
    expect(isDateRangeFilterValid('', '')).toBe(true);
    expect(isDateRangeFilterValid('2026-05-01', '')).toBe(false);
    expect(isDateRangeFilterValid('', '2026-05-01')).toBe(false);
    expect(isDateRangeFilterValid('2026-05-01', '2026-05-01')).toBe(true);
    expect(isDateRangeFilterValid('2026-05-02', '2026-05-01')).toBe(false);
  });

  it('converts a captured system date and time to a UTC ISO datetime', () => {
    const date = new Date(2026, 3, 21);

    expect(toSystemUtcIsoDateTime(date, '09:30:00')).toBe(new Date(2026, 3, 21, 9, 30).toISOString());
  });

  it('reads the browser system timezone', () => {
    expect(getSystemTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });

  it('formats UTC booking instants in the system timezone', () => {
    const value = '2026-04-21T07:00:00Z';
    const expectedTime = new Intl.DateTimeFormat('en-ZA', {
      timeZone: getSystemTimeZone(),
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(new Date(value));

    expect(formatBookingDateTimeTime(value)).toBe(expectedTime);
  });

  it('builds every half-hour slot in a day', () => {
    const slots = buildHalfHourTimeSlots();

    expect(slots).toHaveLength(48);
    expect(slots[0]).toEqual({ value: '00:00', label: '12:00 AM' });
    expect(slots[47]).toEqual({ value: '23:30', label: '11:30 PM' });
  });

  it('groups half-hour slots by day period', () => {
    const groups = buildGroupedHalfHourTimeSlots();

    expect(groups.map((group) => group.label)).toEqual(['Early morning', 'Morning', 'Afternoon', 'Evening']);
    expect(groups.map((group) => group.timeOptions.length)).toEqual([12, 12, 12, 12]);
  });
});
