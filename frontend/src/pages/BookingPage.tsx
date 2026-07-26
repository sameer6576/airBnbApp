import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { bookingsApi, userApi } from '../api';
import { ApiError } from '../api/client';
import type { BookingDto, CancellationQuoteDto } from '../types';

export function BookingPage() {
  const { id } = useParams();
  const bookingId = Number(id);
  const [booking, setBooking] = useState<BookingDto | null>(null);
  const [status, setStatus] = useState<string>('');
  const [quote, setQuote] = useState<CancellationQuoteDto | null>(null);
  const [guestName, setGuestName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const list = await userApi.myBookings();
    const found = list.find((b) => b.id === bookingId) || null;
    setBooking(found);
    const st = await bookingsApi.status(bookingId);
    setStatus(st.status);
    try {
      setQuote(await bookingsApi.cancelQuote(bookingId));
    } catch {
      setQuote(null);
    }
  }, [bookingId]);

  useEffect(() => {
    load().catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load booking'));
  }, [load]);

  async function addGuest(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await bookingsApi.addGuests(bookingId, [{ name: guestName, age: 30, gender: 'OTHER' }]);
      setGuestName('');
      setMessage('Guest added');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add guest');
    } finally {
      setBusy(false);
    }
  }

  async function pay() {
    setBusy(true);
    setError(null);
    try {
      const { sessionUrl } = await bookingsApi.pay(bookingId);
      window.location.href = sessionUrl;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Payment start failed');
      setBusy(false);
    }
  }

  async function cancel() {
    if (!confirm('Cancel this booking?')) return;
    setBusy(true);
    setError(null);
    try {
      await bookingsApi.cancel(bookingId);
      setMessage('Cancelled');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Cancel failed');
    } finally {
      setBusy(false);
    }
  }

  if (!booking && !error) return <div className="container page">Loading booking…</div>;

  const unpaid =
    status === 'RESERVED' || status === 'GUEST_ADDED' || status === 'PAYMENT_PENDING';

  return (
    <div className="container page stack">
      <div>
        <Link to="/my-bookings" className="muted">
          ← My bookings
        </Link>
        <h1 className="page-title">Booking #{bookingId}</h1>
      </div>
      {error && <div className="error-banner">{error}</div>}
      {message && <p className="muted">{message}</p>}
      {booking && (
        <div className="panel stack">
          <div>
            <span className="badge">{status || booking.bookingStatus}</span>
            <p style={{ marginTop: '0.75rem' }}>
              {booking.checkInDate} → {booking.checkOutDate} · {booking.roomsCount} room(s)
            </p>
            <p className="price">${Number(booking.amount).toFixed(2)}</p>
          </div>

          {unpaid && (
            <form className="stack" onSubmit={addGuest}>
              <h3>Add a guest (optional)</h3>
              <div className="field">
                <label htmlFor="gname">Name</label>
                <input id="gname" value={guestName} onChange={(e) => setGuestName(e.target.value)} required />
              </div>
              <button className="btn btn--ghost" type="submit" disabled={busy}>
                Add guest
              </button>
            </form>
          )}

          <div className="form-actions">
            {unpaid && (
              <button className="btn" type="button" onClick={pay} disabled={busy}>
                Pay with Stripe
              </button>
            )}
            {(unpaid || status === 'CONFIRMED') && (
              <button className="btn btn--danger" type="button" onClick={cancel} disabled={busy}>
                Cancel booking
              </button>
            )}
          </div>

          {quote && (
            <p className="muted">
              Cancel quote: ${Number(quote.estimatedRefund).toFixed(2)}
              {quote.freeCancellation ? ' (free cancellation window)' : ''}
            </p>
          )}
          <p className="muted">
            Confirm needs Stripe CLI forwarding webhooks locally. Without it, status stays PAYMENT_PENDING.
          </p>
        </div>
      )}
    </div>
  );
}
