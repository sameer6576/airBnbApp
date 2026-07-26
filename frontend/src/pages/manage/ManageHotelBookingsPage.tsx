import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { adminApi } from '../../api';
import { ApiError } from '../../api/client';
import type { BookingDto } from '../../types';

export function ManageHotelBookingsPage() {
  const { id } = useParams();
  const hotelId = Number(id);
  const [bookings, setBookings] = useState<BookingDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminApi
      .hotelBookings(hotelId)
      .then(setBookings)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load'));
  }, [hotelId]);

  return (
    <div className="container page">
      <Link to={`/manage/hotels/${hotelId}`} className="muted">
        ← Hotel
      </Link>
      <h1 className="page-title">Hotel bookings</h1>
      {error && <div className="error-banner">{error}</div>}
      <div className="panel" style={{ overflowX: 'auto' }}>
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Dates</th>
              <th>Rooms</th>
              <th>Status</th>
              <th>Amount</th>
            </tr>
          </thead>
          <tbody>
            {bookings.map((b) => (
              <tr key={b.id}>
                <td>{b.id}</td>
                <td>
                  {b.checkInDate} → {b.checkOutDate}
                </td>
                <td>{b.roomsCount}</td>
                <td>{b.bookingStatus}</td>
                <td>${Number(b.amount).toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!error && bookings.length === 0 && <p className="muted">No bookings for this hotel yet.</p>}
      </div>
    </div>
  );
}
