import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { RequireAuth, RequireManager } from './components/RequireAuth';
import { BookingPage } from './pages/BookingPage';
import { HomePage } from './pages/HomePage';
import { HotelDetailPage } from './pages/HotelDetailPage';
import { LoginPage } from './pages/LoginPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { PaymentFailurePage, PaymentSuccessPage } from './pages/PaymentPages';
import { SearchPage } from './pages/SearchPage';
import { SignupPage } from './pages/SignupPage';
import { ManageHotelBookingsPage } from './pages/manage/ManageHotelBookingsPage';
import { ManageHotelDetailPage } from './pages/manage/ManageHotelDetailPage';
import { ManageHotelsPage } from './pages/manage/ManageHotelsPage';
import { NewHotelPage } from './pages/manage/NewHotelPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Layout>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/hotels/:id" element={<HotelDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route
              path="/my-bookings"
              element={
                <RequireAuth>
                  <MyBookingsPage />
                </RequireAuth>
              }
            />
            <Route
              path="/bookings/:id"
              element={
                <RequireAuth>
                  <BookingPage />
                </RequireAuth>
              }
            />
            <Route path="/payments/success" element={<PaymentSuccessPage />} />
            <Route path="/payments/failure" element={<PaymentFailurePage />} />
            <Route
              path="/manage"
              element={
                <RequireManager>
                  <ManageHotelsPage />
                </RequireManager>
              }
            />
            <Route
              path="/manage/hotels/new"
              element={
                <RequireManager>
                  <NewHotelPage />
                </RequireManager>
              }
            />
            <Route
              path="/manage/hotels/:id"
              element={
                <RequireManager>
                  <ManageHotelDetailPage />
                </RequireManager>
              }
            />
            <Route
              path="/manage/hotels/:id/bookings"
              element={
                <RequireManager>
                  <ManageHotelBookingsPage />
                </RequireManager>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Layout>
      </AuthProvider>
    </BrowserRouter>
  );
}
