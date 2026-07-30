import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { userApi } from '../api';
import { ApiError } from '../api/client';
import type { BookingDto, BookingStatus } from '../types';

/** Statuses whose bare enum name would not tell a guest what happened. */
const STATUS_NOTES: Partial<Record<BookingStatus, string>> = {
  PAYMENT_PENDING: 'Awaiting payment',
  EXPIRED: 'Hold expired before payment — nothing was charged',
  REFUNDED: 'Rooms were unavailable — payment refunded in full',
};

export function MyBookingsPage() {
  const [bookings, setBookings] = useState<BookingDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    userApi
      .myBookings()
      .then((result) => {
        if (!cancelled) setBookings(result);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Failed to load');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="container page">
      <h1 className="page-title">My bookings</h1>
      {error && (
        <div className="error-banner" role="alert">
          {error}
        </div>
      )}
      {loading && <p className="muted">Loading your bookings…</p>}
      {!loading && !error && bookings.length === 0 && <p className="muted">No bookings yet.</p>}
      <div className="stack">
        {bookings.map((b) => (
          <Link key={b.id} to={`/bookings/${b.id}`} className="panel">
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem' }}>
              <div>
                <strong>#{b.id}</strong>
                <div className="muted">
                  {b.checkInDate} → {b.checkOutDate}
                </div>
                {STATUS_NOTES[b.bookingStatus] && (
                  <div className="muted">{STATUS_NOTES[b.bookingStatus]}</div>
                )}
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
