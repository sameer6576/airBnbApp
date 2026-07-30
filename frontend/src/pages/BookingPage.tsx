import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { bookingsApi, userApi } from '../api';
import { ApiError } from '../api/client';
import type { BookingDto, CancellationQuoteDto, Gender } from '../types';

const UNPAID_STATUSES = ['RESERVED', 'GUEST_ADDED', 'PAYMENT_PENDING'];

/** Warn the guest once the hold is this close to lapsing. */
const WARN_BELOW_SECONDS = 120;

function secondsUntil(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const remaining = Math.floor((new Date(iso).getTime() - Date.now()) / 1000);
  return Number.isNaN(remaining) ? null : remaining;
}

function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/**
 * The backend releases held inventory once holdExpiresAt passes. Without showing
 * it, a guest filling in details has no idea their rooms are about to be released
 * and just hits an opaque "Booking has already expired".
 */
function HoldCountdown({ expiresAt }: { expiresAt: string }) {
  const [remaining, setRemaining] = useState(() => secondsUntil(expiresAt));

  useEffect(() => {
    setRemaining(secondsUntil(expiresAt));
    const timer = setInterval(() => setRemaining(secondsUntil(expiresAt)), 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);

  if (remaining === null) return null;

  if (remaining <= 0) {
    return (
      <p className="error-banner" role="alert">
        This hold has expired and the rooms have been released. Start a new booking to try again.
      </p>
    );
  }

  const urgent = remaining <= WARN_BELOW_SECONDS;
  return (
    <p
      className={urgent ? 'error-banner' : 'muted'}
      role={urgent ? 'alert' : undefined}
      aria-live="polite"
    >
      Rooms held for <strong>{formatCountdown(remaining)}</strong>
      {urgent ? ' — complete payment now or the rooms will be released.' : '.'}
    </p>
  );
}

export function BookingPage() {
  const { id } = useParams();
  const bookingId = Number(id);
  const [booking, setBooking] = useState<BookingDto | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [status, setStatus] = useState<string>('');
  const [quote, setQuote] = useState<CancellationQuoteDto | null>(null);
  const [guestName, setGuestName] = useState('');
  const [guestAge, setGuestAge] = useState('');
  const [guestGender, setGuestGender] = useState<Gender>('MALE');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const list = await userApi.myBookings();
    const found = list.find((b) => b.id === bookingId) || null;
    setBooking(found);
    setNotFound(found === null);
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
      await bookingsApi.addGuests(bookingId, [
        { name: guestName, age: Number(guestAge), gender: guestGender },
      ]);
      setGuestName('');
      setGuestAge('');
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
    setMessage(null);
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

  if (notFound) {
    return (
      <div className="container page stack">
        <h1 className="page-title">Booking not found</h1>
        <p className="muted">
          We couldn&rsquo;t find booking #{bookingId} on your account.{' '}
          <Link to="/my-bookings">Back to my bookings</Link>
        </p>
      </div>
    );
  }

  if (!booking && !error) return <div className="container page">Loading booking…</div>;

  const effectiveStatus = status || booking?.bookingStatus || '';
  const unpaid = UNPAID_STATUSES.includes(effectiveStatus);

  return (
    <div className="container page stack">
      <div>
        <Link to="/my-bookings" className="muted">
          ← My bookings
        </Link>
        <h1 className="page-title">Booking #{bookingId}</h1>
      </div>
      {error && (
        <div className="error-banner" role="alert">
          {error}
        </div>
      )}
      {message && <p className="muted">{message}</p>}
      {booking && (
        <div className="panel stack">
          <div>
            <span className="badge">{effectiveStatus}</span>
            <p style={{ marginTop: '0.75rem' }}>
              {booking.checkInDate} → {booking.checkOutDate} · {booking.roomsCount} room(s)
            </p>
            <p className="price">${Number(booking.amount).toFixed(2)}</p>
          </div>

          {unpaid && booking.holdExpiresAt && <HoldCountdown expiresAt={booking.holdExpiresAt} />}

          {effectiveStatus === 'REFUNDED' && (
            <p className="error-banner" role="alert">
              Your payment went through but the rooms were no longer available, so it has been
              refunded in full
              {booking.refundAmount != null ? ` ($${Number(booking.refundAmount).toFixed(2)})` : ''}.
              Refunds usually reach your card within a few business days.
            </p>
          )}

          {effectiveStatus === 'EXPIRED' && (
            <p className="muted">
              This booking expired before payment completed, so the rooms were released. Nothing was
              charged.
            </p>
          )}

          {unpaid && (
            <form className="stack" onSubmit={addGuest}>
              <h3>Add a guest (optional)</h3>
              <div className="field">
                <label htmlFor="gname">Name</label>
                <input id="gname" value={guestName} onChange={(e) => setGuestName(e.target.value)} required />
              </div>
              <div className="field">
                <label htmlFor="gage">Age</label>
                <input
                  id="gage"
                  type="number"
                  min={1}
                  max={120}
                  value={guestAge}
                  onChange={(e) => setGuestAge(e.target.value)}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="ggender">Gender</label>
                <select
                  id="ggender"
                  value={guestGender}
                  onChange={(e) => setGuestGender(e.target.value as Gender)}
                >
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
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
            {(unpaid || effectiveStatus === 'CONFIRMED') && (
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
        </div>
      )}
    </div>
  );
}
