import { Link } from 'react-router-dom';

export function PaymentSuccessPage() {
  return (
    <div className="container page stack">
      <h1>Payment submitted</h1>
      <p className="muted">
        If Stripe webhooks are forwarding to the API, your booking will become CONFIRMED shortly.
      </p>
      <Link className="btn" to="/my-bookings">
        View my bookings
      </Link>
    </div>
  );
}

export function PaymentFailurePage() {
  return (
    <div className="container page stack">
      <h1>Payment cancelled</h1>
      <p className="muted">You can retry payment from the booking page while the hold is active.</p>
      <Link className="btn" to="/my-bookings">
        Back to bookings
      </Link>
    </div>
  );
}
