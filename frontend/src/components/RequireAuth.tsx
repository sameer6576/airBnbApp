import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="container page">Loading…</div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

export function RequireManager({ children }: { children: React.ReactNode }) {
  const { isManager, loading, user } = useAuth();
  const location = useLocation();
  if (loading) return <div className="container page">Loading…</div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!isManager) return <Navigate to="/" replace />;
  return <>{children}</>;
}
