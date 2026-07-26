import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { hotelsApi } from '../api';
import { ApiError } from '../api/client';
import { HotelCard } from '../components/HotelCard';
import { SearchBar } from '../components/SearchBar';
import type { HotelPriceDto } from '../types';

export function SearchPage() {
  const [params] = useSearchParams();
  const city = params.get('city') || 'New York';
  const startDate = params.get('startDate') || '';
  const endDate = params.get('endDate') || '';
  const roomsCount = Number(params.get('roomsCount') || 1);

  const [results, setResults] = useState<HotelPriceDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!startDate || !endDate) {
      setLoading(false);
      setError('Pick check-in and check-out dates.');
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    hotelsApi
      .search({ city, startDate, endDate, roomsCount, page: 0, size: 20, sortBy: 'PRICE_ASC' })
      .then((page) => {
        if (!cancelled) setResults(page.content || []);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : 'Search failed');
          setResults([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [city, startDate, endDate, roomsCount]);

  return (
    <div className="container page">
      <h1 className="page-title">Search stays</h1>
      <SearchBar initial={{ city, startDate, endDate, roomsCount }} />
      <div style={{ height: '1.5rem' }} />
      {error && <div className="error-banner">{error}</div>}
      {loading && <p className="muted">Searching…</p>}
      {!loading && !error && results.length === 0 && (
        <p className="muted">No hotels matched. Activate a hotel as manager, or loosen filters.</p>
      )}
      <div className="grid-cards">
        {results.map((item) => (
          <HotelCard
            key={item.hotel.id}
            item={item}
            startDate={startDate}
            endDate={endDate}
            roomsCount={roomsCount}
          />
        ))}
      </div>
    </div>
  );
}
