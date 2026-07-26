import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout, isManager, loading } = useAuth();

  return (
    <>
      <header className="site-header">
        <div className="container site-header__inner">
          <Link to="/" className="brand">
            Stay<span>line</span>
          </Link>
          <nav className="nav">
            <NavLink to="/search">Search</NavLink>
            {!loading && user && <NavLink to="/my-bookings">My bookings</NavLink>}
            {!loading && isManager && <NavLink to="/manage">Manage</NavLink>}
            {!loading && !user && (
              <>
                <NavLink to="/login">Log in</NavLink>
                <NavLink to="/signup">Sign up</NavLink>
              </>
            )}
            {!loading && user && (
              <>
                <span className="muted">{user.email}</span>
                <button type="button" className="linkish" onClick={logout}>
                  Log out
                </button>
              </>
            )}
          </nav>
        </div>
      </header>
      <main>{children}</main>
      <footer className="site-footer">
        <div className="container">Stayline — demo UI for the airBnbApp API</div>
      </footer>
    </>
  );
}
