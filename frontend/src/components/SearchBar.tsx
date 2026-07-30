import { useNavigate } from 'react-router-dom';
import type { FormEvent } from 'react';
import { useState } from 'react';

interface Props {
  initial?: {
    city?: string;
    startDate?: string;
    endDate?: string;
    roomsCount?: number;
  };
  compact?: boolean;
}

/**
 * Formats a Date as YYYY-MM-DD in the user's own timezone.
 *
 * toISOString() converts to UTC first, so for any negative UTC offset a
 * local-midnight date rendered that way lands on the previous day — the guest
 * would search a different night than the form showed. The backend parses these
 * as bare LocalDate values, so they must be local calendar dates.
 */
function toLocalDateString(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Shifts a YYYY-MM-DD string by whole days, parsing the parts to stay local. */
function addDays(dateString: string, days: number): string {
  const [year, month, day] = dateString.split('-').map(Number);
  return toLocalDateString(new Date(year, month - 1, day + days));
}

function defaultDates() {
  const start = new Date();
  start.setDate(start.getDate() + 14);
  const end = new Date(start);
  end.setDate(end.getDate() + 2);
  return { startDate: toLocalDateString(start), endDate: toLocalDateString(end) };
}

export function SearchBar({ initial, compact }: Props) {
  const navigate = useNavigate();
  const defaults = defaultDates();
  const [city, setCity] = useState(initial?.city ?? 'New York');
  const [startDate, setStartDate] = useState(initial?.startDate ?? defaults.startDate);
  const [endDate, setEndDate] = useState(initial?.endDate ?? defaults.endDate);
  const [roomsCount, setRoomsCount] = useState(initial?.roomsCount ?? 1);

  function onStartDateChange(next: string) {
    setStartDate(next);
    // Keep the stay at least one night when check-in moves past check-out.
    if (next && endDate <= next) {
      setEndDate(addDays(next, 1));
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const params = new URLSearchParams({
      city,
      startDate,
      endDate,
      roomsCount: String(roomsCount),
    });
    navigate(`/search?${params.toString()}`);
  }

  return (
    <form className="search-panel" onSubmit={onSubmit}>
      <div className="field">
        <label htmlFor="city">City</label>
        <input id="city" value={city} onChange={(e) => setCity(e.target.value)} required />
      </div>
      <div className="field">
        <label htmlFor="start">Check-in</label>
        <input
          id="start"
          type="date"
          value={startDate}
          min={toLocalDateString(new Date())}
          onChange={(e) => onStartDateChange(e.target.value)}
          required
        />
      </div>
      <div className="field">
        <label htmlFor="end">Check-out</label>
        <input
          id="end"
          type="date"
          value={endDate}
          // Check-out is not a night, so a same-day range is zero nights and the
          // API rejects it. Enforce at least one night here rather than round-trip.
          min={addDays(startDate, 1)}
          onChange={(e) => setEndDate(e.target.value)}
          required
        />
      </div>
      {!compact && (
        <div className="field">
          <label htmlFor="rooms">Rooms</label>
          <input
            id="rooms"
            type="number"
            min={1}
            value={roomsCount}
            onChange={(e) => setRoomsCount(Number(e.target.value))}
            required
          />
        </div>
      )}
      <button className="btn" type="submit">
        Search
      </button>
    </form>
  );
}
