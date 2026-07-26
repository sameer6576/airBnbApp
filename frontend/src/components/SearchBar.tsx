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

function defaultDates() {
  const start = new Date();
  start.setDate(start.getDate() + 14);
  const end = new Date(start);
  end.setDate(end.getDate() + 2);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return { startDate: iso(start), endDate: iso(end) };
}

export function SearchBar({ initial, compact }: Props) {
  const navigate = useNavigate();
  const defaults = defaultDates();
  const [city, setCity] = useState(initial?.city ?? 'New York');
  const [startDate, setStartDate] = useState(initial?.startDate ?? defaults.startDate);
  const [endDate, setEndDate] = useState(initial?.endDate ?? defaults.endDate);
  const [roomsCount, setRoomsCount] = useState(initial?.roomsCount ?? 1);

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
          onChange={(e) => setStartDate(e.target.value)}
          required
        />
      </div>
      <div className="field">
        <label htmlFor="end">Check-out</label>
        <input
          id="end"
          type="date"
          value={endDate}
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
