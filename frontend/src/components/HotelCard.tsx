import { Link } from 'react-router-dom';
import type { HotelPriceDto } from '../types';

const FALLBACK =
  'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=800&q=80';

export function HotelCard({
  item,
  startDate,
  endDate,
  roomsCount,
}: {
  item: HotelPriceDto;
  startDate: string;
  endDate: string;
  roomsCount: number;
}) {
  const photo = item.hotel.photos?.[0] || FALLBACK;
  const qs = new URLSearchParams({ startDate, endDate, roomsCount: String(roomsCount) });

  return (
    <Link to={`/hotels/${item.hotel.id}?${qs}`} className="hotel-card">
      <div className="hotel-card__media" style={{ backgroundImage: `url(${photo})` }} />
      <div className="hotel-card__body">
        <h3>{item.hotel.name}</h3>
        <div className="hotel-card__meta">
          {item.hotel.city}
          {item.averageRating != null && item.averageRating > 0
            ? ` · ${item.averageRating.toFixed(1)}★`
            : ''}
        </div>
        <div className="price">from ${Number(item.price).toFixed(0)} / night</div>
      </div>
    </Link>
  );
}
