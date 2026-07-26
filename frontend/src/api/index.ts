import { apiFetch, setAccessToken } from './client';
import type {
  BookingDto,
  BookingRequest,
  CancellationQuoteDto,
  GuestDto,
  HotelDto,
  HotelInfoDto,
  HotelPriceDto,
  HotelSearchRequest,
  Page,
  RoomDto,
  UserDto,
} from '../types';

export const authApi = {
  signup: (email: string, password: string) =>
    apiFetch<UserDto>('/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  login: async (email: string, password: string) => {
    const data = await apiFetch<{ accessToken: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    setAccessToken(data.accessToken);
    return data;
  },
  logout: () => setAccessToken(null),
};

export const userApi = {
  profile: () => apiFetch<UserDto>('/users/getMyProfile'),
  myBookings: () => apiFetch<BookingDto[]>('/users/myBookings'),
};

export const hotelsApi = {
  search: (body: HotelSearchRequest) =>
    apiFetch<Page<HotelPriceDto>>('/hotels/search', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  info: (hotelId: number) => apiFetch<HotelInfoDto>(`/hotels/${hotelId}/info`),
};

export const bookingsApi = {
  init: (body: BookingRequest, idempotencyKey?: string) =>
    apiFetch<BookingDto>('/bookings/init', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    }),
  addGuests: (bookingId: number, guests: GuestDto[]) =>
    apiFetch<BookingDto>(`/bookings/${bookingId}/addGuests`, {
      method: 'POST',
      body: JSON.stringify(guests),
    }),
  pay: (bookingId: number) =>
    apiFetch<{ sessionUrl: string }>(`/bookings/${bookingId}/payments`, {
      method: 'POST',
    }),
  status: (bookingId: number) =>
    apiFetch<{ status: string }>(`/bookings/${bookingId}/status`),
  cancelQuote: (bookingId: number) =>
    apiFetch<CancellationQuoteDto>(`/bookings/${bookingId}/cancellation-quote`),
  cancel: (bookingId: number) =>
    apiFetch<void>(`/bookings/${bookingId}/cancel`, { method: 'POST' }),
};

export const adminApi = {
  listHotels: () => apiFetch<HotelDto[]>('/admin/hotels'),
  createHotel: (hotel: HotelDto) =>
    apiFetch<HotelDto>('/admin/hotels', {
      method: 'POST',
      body: JSON.stringify(hotel),
    }),
  getHotel: (id: number) => apiFetch<HotelDto>(`/admin/hotels/${id}`),
  activate: (id: number) =>
    apiFetch<void>(`/admin/hotels/${id}/activate`, { method: 'PATCH' }),
  listRooms: (hotelId: number) =>
    apiFetch<RoomDto[]>(`/admin/hotels/${hotelId}/rooms`),
  createRoom: (hotelId: number, room: RoomDto) =>
    apiFetch<RoomDto>(`/admin/hotels/${hotelId}/rooms`, {
      method: 'POST',
      body: JSON.stringify(room),
    }),
  hotelBookings: (hotelId: number) =>
    apiFetch<BookingDto[]>(`/admin/hotels/${hotelId}/bookings`),
};
