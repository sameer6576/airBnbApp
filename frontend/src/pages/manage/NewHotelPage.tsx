import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminApi } from '../../api';
import { ApiError } from '../../api/client';

export function NewHotelPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [city, setCity] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const hotel = await adminApi.createHotel({
        name,
        city,
        photos: [
          'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80',
        ],
        amenities: ['wifi', 'parking'],
        contactInfo: {
          address: '1 Main St',
          phoneNumber: '+1-555-0100',
          email: 'stay@example.com',
          location: city,
        },
      });
      navigate(`/manage/hotels/${hotel.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Create failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container page">
      <h1 className="page-title">New hotel</h1>
      <form className="panel stack" onSubmit={onSubmit} style={{ maxWidth: 480 }}>
        {error && <div className="error-banner">{error}</div>}
        <div className="field">
          <label htmlFor="name">Name</label>
          <input id="name" value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="city">City</label>
          <input id="city" value={city} onChange={(e) => setCity(e.target.value)} required />
        </div>
        <button className="btn" type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Create'}
        </button>
      </form>
    </div>
  );
}
