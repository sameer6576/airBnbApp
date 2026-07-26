import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { adminApi } from '../../api';
import { ApiError } from '../../api/client';
import type { HotelDto, RoomDto } from '../../types';

export function ManageHotelDetailPage() {
  const { id } = useParams();
  const hotelId = Number(id);
  const [hotel, setHotel] = useState<HotelDto | null>(null);
  const [rooms, setRooms] = useState<RoomDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [type, setType] = useState('Deluxe King');
  const [basePrice, setBasePrice] = useState(150);
  const [totalCount, setTotalCount] = useState(5);
  const [capacity, setCapacity] = useState(2);

  const reload = useCallback(async () => {
    const [h, r] = await Promise.all([adminApi.getHotel(hotelId), adminApi.listRooms(hotelId)]);
    setHotel(h);
    setRooms(r);
  }, [hotelId]);

  useEffect(() => {
    reload().catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load'));
  }, [reload]);

  async function activate() {
    setBusy(true);
    setError(null);
    try {
      await adminApi.activate(hotelId);
      setMessage('Hotel activated — inventory generated for existing rooms.');
      await reload();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Activate failed');
    } finally {
      setBusy(false);
    }
  }

  async function addRoom(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminApi.createRoom(hotelId, {
        type,
        basePrice,
        totalCount,
        capacity,
        photos: [],
        amenities: ['wifi'],
      });
      setMessage(
        hotel?.active
          ? 'Room added with inventory (hotel already active).'
          : 'Room added. Activate the hotel to generate inventory.',
      );
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add room');
    } finally {
      setBusy(false);
    }
  }

  if (!hotel && !error) return <div className="container page">Loading…</div>;

  return (
    <div className="container page stack">
      <div>
        <Link to="/manage" className="muted">
          ← Hotels
        </Link>
        <h1 className="page-title">{hotel?.name}</h1>
        <p className="muted">
          {hotel?.city} · {hotel?.active ? 'Active' : 'Inactive'}
        </p>
      </div>
      {error && <div className="error-banner">{error}</div>}
      {message && <p className="muted">{message}</p>}

      <div className="form-actions">
        {!hotel?.active && (
          <button className="btn" type="button" onClick={activate} disabled={busy || rooms.length === 0}>
            Activate hotel
          </button>
        )}
        <Link className="btn btn--ghost" to={`/manage/hotels/${hotelId}/bookings`}>
          View bookings
        </Link>
      </div>

      <div className="panel">
        <h2>Rooms</h2>
        {rooms.length === 0 && <p className="muted">Add at least one room before activating.</p>}
        {rooms.map((r) => (
          <div className="room-row" key={r.id}>
            <div>
              <strong>{r.type}</strong>
              <div className="muted">
                ${Number(r.basePrice).toFixed(0)} · {r.totalCount} units · capacity {r.capacity}
              </div>
            </div>
          </div>
        ))}
      </div>

      <form className="panel stack" onSubmit={addRoom}>
        <h2>Add room</h2>
        <div className="field">
          <label htmlFor="type">Type</label>
          <input id="type" value={type} onChange={(e) => setType(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="price">Base price</label>
          <input
            id="price"
            type="number"
            min={1}
            step="0.01"
            value={basePrice}
            onChange={(e) => setBasePrice(Number(e.target.value))}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="count">Total count</label>
          <input
            id="count"
            type="number"
            min={1}
            value={totalCount}
            onChange={(e) => setTotalCount(Number(e.target.value))}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="cap">Capacity</label>
          <input
            id="cap"
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(Number(e.target.value))}
            required
          />
        </div>
        <button className="btn" type="submit" disabled={busy}>
          Add room
        </button>
      </form>
    </div>
  );
}
