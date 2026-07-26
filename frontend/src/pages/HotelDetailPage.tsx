import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { bookingsApi, hotelsApi } from '../api';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { HotelInfoDto, RoomDto } from '../types';

const FALLBACK =
  'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1400&q=80';

export function HotelDetailPage() {
  const { id } = useParams();
  const hotelId = Number(id);
  const [params] = useSearchParams();
  const startDate = params.get('startDate') || '';
  const endDate = params.get('endDate') || '';
  const roomsCount = Number(params.get('roomsCount') || 1);
  const { user } = useAuth();
  const navigate = useNavigate();

  const [info, setInfo] = useState<HotelInfoDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyRoom, setBusyRoom] = useState<number | null>(null);

  useEffect(() => {
    hotelsApi
      .info(hotelId)
      .then(setInfo)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load hotel'));
  }, [hotelId]);

  async function book(room: RoomDto) {
    if (!user) {
      navigate('/login', { state: { from: `/hotels/${hotelId}?${params}` } });
      return;
    }
    if (!startDate || !endDate || !room.id) {
      setError('Missing dates or room id');
      return;
    }
    setBusyRoom(room.id);
    setError(null);
    try {
      const key = crypto.randomUUID();
      const booking = await bookingsApi.init(
        {
          hotelId,
          roomId: room.id,
          checkInDate: startDate,
          checkOutDate: endDate,
          roomsCount,
        },
        key,
      );
      navigate(`/bookings/${booking.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not start booking');
    } finally {
      setBusyRoom(null);
    }
  }

  if (!info && !error) return <div className="container page">Loading hotel…</div>;

  const photo = info?.hotel.photos?.[0] || FALLBACK;

  return (
    <div className="page">
      <div
        style={{
          minHeight: 280,
          background: `linear-gradient(180deg, transparent, rgba(15,42,36,0.55)), url(${photo}) center/cover`,
        }}
      />
      <div className="container" style={{ marginTop: '-3rem' }}>
        <div className="panel stack">
          {error && <div className="error-banner">{error}</div>}
          {info && (
            <>
              <div>
                <h1>{info.hotel.name}</h1>
                <p className="muted">
                  {info.hotel.city}
                  {info.hotel.averageRating
                    ? ` · ${info.hotel.averageRating.toFixed(1)}★ (${info.hotel.reviewCount || 0})`
                    : ''}
                </p>
                <p className="muted">
                  {startDate && endDate
                    ? `${startDate} → ${endDate} · ${roomsCount} room(s)`
                    : 'Open from search with dates to book'}
                </p>
              </div>
              <div>
                <h2>Rooms</h2>
                {info.rooms?.length ? (
                  info.rooms.map((room) => (
                    <div className="room-row" key={room.id}>
                      <div>
                        <strong>{room.type}</strong>
                        <div className="muted">
                          ${Number(room.basePrice).toFixed(0)} base · sleeps {room.capacity} ·{' '}
                          {room.totalCount} units
                        </div>
                      </div>
                      <button
                        className="btn"
                        type="button"
                        disabled={!startDate || !endDate || busyRoom === room.id}
                        onClick={() => book(room)}
                      >
                        {busyRoom === room.id ? 'Reserving…' : 'Book'}
                      </button>
                    </div>
                  ))
                ) : (
                  <p className="muted">No rooms yet.</p>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
