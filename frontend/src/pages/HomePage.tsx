import { SearchBar } from '../components/SearchBar';

export function HomePage() {
  return (
    <section className="hero">
      <div className="hero__content">
        <h1>Stayline</h1>
        <p>Find a room that fits the trip — search live inventory from the booking API.</p>
        <SearchBar />
      </div>
    </section>
  );
}
