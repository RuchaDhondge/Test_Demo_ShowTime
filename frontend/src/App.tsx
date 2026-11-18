import { useEffect, useMemo, useState } from 'react'
import './App.css'

type EventItem = {
  id: number
  name: string
  category: string
  city: string
  venue: string
  startsAt: string
}

type Seat = {
  id: number
  label: string
  status: 'AVAILABLE' | 'HELD' | 'BOOKED'
  holdExpiresAt?: string
}

function App() {
  const [city, setCity] = useState('Pune')
  const [events, setEvents] = useState<EventItem[]>([])
  const [selectedEvent, setSelectedEvent] = useState<EventItem>()
  const [seats, setSeats] = useState<Seat[]>([])
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([])
  const [userId, setUserId] = useState('colleague@example.com')
  const [message, setMessage] = useState('Choose an event and seats.')
  const [busy, setBusy] = useState(false)
  const [imageVersion, setImageVersion] = useState(0)

  useEffect(() => {
    fetch(`/api/events?city=${encodeURIComponent(city)}`)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then((items: EventItem[]) => {
        setEvents(items)
        setSelectedEvent(items[0])
      })
      .catch(() => setMessage('Could not load events. Check the backend health.'))
  }, [city])

  useEffect(() => {
    if (!selectedEvent) return
    loadSeats(selectedEvent.id)
  }, [selectedEvent])

  const selectedLabels = useMemo(
    () => seats.filter(seat => selectedSeatIds.includes(seat.id)).map(seat => seat.label),
    [seats, selectedSeatIds],
  )

  function loadSeats(eventId: number) {
    fetch(`/api/events/${eventId}/seats`)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then((items: Seat[]) => {
        setSeats(items)
        setSelectedSeatIds([])
      })
      .catch(() => setMessage('Could not load seats.'))
  }

  function toggleSeat(seat: Seat) {
    if (seat.status !== 'AVAILABLE') return
    setSelectedSeatIds(current =>
      current.includes(seat.id)
        ? current.filter(id => id !== seat.id)
        : [...current, seat.id],
    )
  }

  async function createHold() {
    if (!selectedEvent || selectedSeatIds.length === 0) return
    setBusy(true)
    try {
      const response = await fetch(`/api/events/${selectedEvent.id}/holds`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, seatIds: selectedSeatIds }),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message ?? 'Seat hold failed')
      setMessage(`Held ${selectedLabels.join(', ')} until ${new Date(body.expiresAt).toLocaleTimeString()}.`)
      loadSeats(selectedEvent.id)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Seat hold failed')
      loadSeats(selectedEvent.id)
    } finally {
      setBusy(false)
    }
  }

  async function uploadImage(file?: File) {
    if (!selectedEvent || !file) return
    const form = new FormData()
    form.append('file', file)
    setBusy(true)
    try {
      const response = await fetch(`/api/events/${selectedEvent.id}/image`, {
        method: 'POST',
        body: form,
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message ?? 'Image upload failed')
      setImageVersion(current => current + 1)
      setMessage('Event image stored in emulated S3.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Image upload failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <header>
        <p className="eyebrow">Local network demonstration</p>
        <h1>Ticket Booking</h1>
        <p>PostgreSQL-authoritative seat holds with Redis acceleration.</p>
      </header>

      <section className="toolbar">
        <label>
          City
          <select value={city} onChange={event => setCity(event.target.value)}>
            <option>Pune</option>
            <option>Mumbai</option>
          </select>
        </label>
        <label>
          Demo user
          <input value={userId} onChange={event => setUserId(event.target.value)} />
        </label>
      </section>

      <section>
        <h2>Events</h2>
        <div className="events">
          {events.map(item => (
            <button
              className={selectedEvent?.id === item.id ? 'event selected' : 'event'}
              key={item.id}
              onClick={() => setSelectedEvent(item)}
            >
              <strong>{item.name}</strong>
              <span>{item.category} · {item.venue}</span>
              <span>{new Date(item.startsAt).toLocaleString()}</span>
            </button>
          ))}
        </div>
      </section>

      <section>
        <div className="event-image-row">
          <div>
            <h2>AWS Emulator S3</h2>
            <p>Upload a JPEG, PNG, or WebP image up to 2 MB for the selected event.</p>
            <label className="file-input">
              Event image
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                disabled={!selectedEvent || busy}
                onChange={event => uploadImage(event.target.files?.[0])}
              />
            </label>
          </div>
          {selectedEvent && imageVersion > 0 && (
            <img
              className="event-image"
              src={`/api/events/${selectedEvent.id}/image?v=${imageVersion}`}
              alt={`${selectedEvent.name} cover`}
            />
          )}
        </div>
      </section>

      <section>
        <div className="section-heading">
          <div>
            <h2>Seats</h2>
            <p>Held seats automatically become available after two minutes.</p>
          </div>
          <button className="refresh" onClick={() => selectedEvent && loadSeats(selectedEvent.id)}>
            Refresh
          </button>
        </div>
        <div className="legend">
          <span><i className="available" /> Available</span>
          <span><i className="chosen" /> Selected</span>
          <span><i className="held" /> Held</span>
        </div>
        <div className="seats">
          {seats.map(seat => (
            <button
              className={`seat ${seat.status.toLowerCase()} ${selectedSeatIds.includes(seat.id) ? 'chosen' : ''}`}
              disabled={seat.status !== 'AVAILABLE'}
              key={seat.id}
              onClick={() => toggleSeat(seat)}
            >
              {seat.label}
            </button>
          ))}
        </div>
        <button
          className="primary"
          disabled={busy || selectedSeatIds.length === 0 || !userId.trim()}
          onClick={createHold}
        >
          {busy ? 'Holding…' : `Hold ${selectedSeatIds.length || ''} seat${selectedSeatIds.length === 1 ? '' : 's'}`}
        </button>
        <p className="message" role="status">{message}</p>
      </section>
    </main>
  )
}

export default App
