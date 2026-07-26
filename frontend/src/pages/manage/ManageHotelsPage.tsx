import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api';
import { ApiError } from '../../api/client';
import type { HotelDto } from '../../types';

export function ManageHotelsPage() {
  const [hotels, setHotels] = useState<HotelDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminApi
      .listHotels()
      .then(setHotels)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load hotels'));
  }, []);

  return (
    <div className="container page">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', alignItems: 'center' }}>
        <h1 className="page-title">Your hotels</h1>
        <Link className="btn" to="/manage/hotels/new">
          New hotel
        </Link>
      </div>
      {error && <div className="error-banner">{error}</div>}
      <div className="stack">
        {hotels.map((h) => (
          <Link key={h.id} to={`/manage/hotels/${h.id}`} className="panel">
            <strong>{h.name}</strong>
            <div className="muted">
              {h.city} · {h.active ? 'Active' : 'Inactive'}
            </div>
          </Link>
        ))}
        {!error && hotels.length === 0 && <p className="muted">No hotels yet — create one.</p>}
      </div>
    </div>
  );
}
