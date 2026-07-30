export type Role = 'GUEST' | 'HOTEL_MANAGER' | 'ADMIN';

export type BookingStatus =
  | 'RESERVED'
  | 'GUEST_ADDED'
  | 'PAYMENT_PENDING'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REFUNDED';

export interface UserDto {
  id: number;
  email: string;
  name?: string;
  roles?: Role[];
}

export interface HotelContactInfo {
  address?: string;
  phoneNumber?: string;
  email?: string;
  location?: string;
}

export interface HotelDto {
  id?: number;
  name: string;
  city: string;
  photos?: string[];
  amenities?: string[];
  contactInfo?: HotelContactInfo;
  active?: boolean;
  averageRating?: number;
  reviewCount?: number;
}

export interface RoomDto {
  id?: number;
  type: string;
  basePrice: number;
  photos?: string[];
  amenities?: string[];
  totalCount: number;
  capacity: number;
}

export interface HotelPriceDto {
  hotel: HotelDto;
  price: number;
  averageRating?: number;
}

export interface HotelInfoDto {
  hotel: HotelDto;
  rooms: RoomDto[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface HotelSearchRequest {
  city: string;
  startDate: string;
  endDate: string;
  roomsCount: number;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  sortBy?: 'PRICE_ASC' | 'PRICE_DESC' | 'RATING_DESC';
  page?: number;
  size?: number;
}

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

export interface GuestDto {
  id?: number;
  name?: string;
  gender?: Gender;
  age?: number;
}

export interface BookingDto {
  id: number;
  roomsCount: number;
  checkInDate: string;
  checkOutDate: string;
  bookingStatus: BookingStatus;
  amount: number;
  guests?: GuestDto[];
  /** ISO timestamp when the soft hold lapses. Absent once the booking is settled. */
  holdExpiresAt?: string | null;
  refundAmount?: number | null;
}

export interface BookingRequest {
  hotelId: number;
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  roomsCount: number;
}

export interface CancellationQuoteDto {
  freeCancellation: boolean;
  daysUntilCheckIn: number;
  freeCancelDays: number;
  refundPercent: number;
  estimatedRefund: number;
}
