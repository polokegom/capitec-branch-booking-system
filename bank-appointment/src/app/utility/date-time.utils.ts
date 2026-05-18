export interface TimeSlotOption {
  value: string;
  label: string;
}

export interface TimeSlotGroup {
  label: string;
  timeOptions: TimeSlotOption[];
}

export function addDays(date: Date, days: number): Date {
  const adjustedDate = new Date(date);
  adjustedDate.setDate(adjustedDate.getDate() + days);
  return adjustedDate;
}

export function startOfDay(date: Date): Date {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function fromIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function isDateRangeFilterValid(startDate: string, endDate: string): boolean {
  if (!startDate && !endDate) return true;
  if (!startDate || !endDate) return false;
  return startDate <= endDate;
}

export function currentHourMinute(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

export function roundDownToHalfHour(value: string): string {
  const { hour, minute: minuteValue } = parseTimeValue(value);
  const roundedMinute = minuteValue < 30 ? 0 : 30;
  return `${String(hour).padStart(2, '0')}:${String(roundedMinute).padStart(2, '0')}`;
}

export function toApiTime(value: string): string {
  return value.length === 5 ? `${value}:00` : value;
}

export function toHourMinuteTime(value: string): string {
  return value.slice(0, 5);
}

export function formatBookingDateTimeDate(value: string, locale = 'en-ZA'): string {
  return formatBookingDateTime(value, locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
}

export function formatBookingDateTimeTime(value: string, locale = 'en-ZA'): string {
  return formatBookingDateTime(value, locale, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

export function buildHalfHourTimeSlots(): TimeSlotOption[] {
  const timeOptions: TimeSlotOption[] = [];
  for (let hour = 0; hour < 24; hour++) {
    for (const minute of [0, 30]) {
      timeOptions.push({
        value: hourMinuteValue(hour, minute),
        label: displayTime(hour, minute)
      });
    }
  }
  return timeOptions;
}

export function buildGroupedHalfHourTimeSlots(): TimeSlotGroup[] {
  const groups: TimeSlotGroup[] = [
    { label: 'Early morning', timeOptions: [] },
    { label: 'Morning', timeOptions: [] },
    { label: 'Afternoon', timeOptions: [] },
    { label: 'Evening', timeOptions: [] }
  ];

  for (const timeOption of buildHalfHourTimeSlots()) {
    const hour = parseInt(timeOption.value.slice(0, 2), 10);
    if (hour < 6) groups[0].timeOptions.push(timeOption);
    else if (hour < 12) groups[1].timeOptions.push(timeOption);
    else if (hour < 18) groups[2].timeOptions.push(timeOption);
    else groups[3].timeOptions.push(timeOption);
  }

  return groups;
}

function hourMinuteValue(hour: number, minute: number): string {
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

function parseTimeValue(value: string): { hour: number; minute: number; second: number } {
  const [hour, minute, second = 0] = value
    .split(':')
    .map((timePart) => parseInt(timePart, 10));

  return { hour, minute, second };
}

function formatBookingDateTime(
  value: string,
  locale: string,
  options: Intl.DateTimeFormatOptions
): string {
  return new Intl.DateTimeFormat(locale, options).format(new Date(value));
}

function displayTime(hour: number, minute: number): string {
  const period = hour < 12 ? 'AM' : 'PM';
  const twelveHour = ((hour + 11) % 12) + 1;
  return `${twelveHour}:${String(minute).padStart(2, '0')} ${period}`;
}
