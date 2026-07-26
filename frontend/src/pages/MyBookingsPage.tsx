import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { userApi } from '../api';
import { ApiError } from '../api/client';
import type { BookingDto } from '../types';

export function MyBookingsPage() {
  const [bookings, setBookings] = useState<BookingDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    userApi
      .myBookings()
      .then(setBookings)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load'));
  }, []);

  return (
    <div className="container page">
      <h1 className="page-title">My bookings</h1>
      {error && <div className="error-banner">{error}</div>}
      {!error && bookings.length === 0 && <p className="muted">No bookings yet.</p>}
      <div className="stack">
        {bookings.map((b) => (
          <Link key={b.id} to={`/bookings/${b.id}`} className="panel">
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem' }}>
              <div>
                <strong>#{b.id}</strong>
                <div className="muted">
                  {b.checkInDate} → {b.checkOutDate}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span className="badge">{b.bookingStatus}</span>
                <div className="price">${Number(b.amount).toFixed(2)}</div>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
