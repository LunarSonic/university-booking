import React, { useState, useCallback } from 'react';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { fetchEventsByResult, fetchEventsByOperation, fetchEventsByBooking } from '../services/api';

const today = () => new Date().toISOString().slice(0, 10);

const OPERATION_OPTIONS = ['CREATE_BOOKING', 'UPDATE_STATUS', 'CHECKOUT'];
const RESULT_OPTIONS = ['SUCCESS', 'REJECTED'];

const OPERATION_LABELS = {
  CREATE_BOOKING: 'Создание заявки',
  UPDATE_STATUS: 'Смена статуса',
  CHECKOUT: 'Оформление из корзины',
};

const RESULT_LABELS = {
  SUCCESS: 'Успешно',
  REJECTED: 'Отклонено',
};

export function EventsJournal() {
  const [mode, setMode] = useState('by-operation');

  const [operation, setOperation] = useState('CREATE_BOOKING');
  const [result, setResult] = useState('SUCCESS');
  const [bookingId, setBookingId] = useState('');
  const [dateFrom, setDateFrom] = useState(today());
  const [dateTo, setDateTo] = useState(today());

  const [events, setEvents] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [cursorStack, setCursorStack] = useState([null]);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [searched, setSearched] = useState(false);

  const buildRange = () => ({
    from: `${dateFrom}T00:00:00Z`,
    to: `${dateTo}T23:59:59Z`,
  });

  const doSearch = useCallback(async (cursor = null) => {
    setLoading(true);
    setError(null);
    try {
      const { from, to } = buildRange();
      let data;
      if (mode === 'by-operation') {
        data = await fetchEventsByOperation({ operation, from, to, cursor });
      } else if (mode === 'by-result') {
        data = await fetchEventsByResult({ result, from, to, cursor });
      } else {
        if (!bookingId) { setError('Введите ID заявки'); setLoading(false); return; }
        data = await fetchEventsByBooking({ bookingId, from, to, cursor });
      }
      setEvents(data.items);
      setNextCursor(data.nextCursor || null);
      setSearched(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [mode, operation, result, bookingId, dateFrom, dateTo]);

  const handleSearch = () => {
    setPage(1);
    setCursorStack([null]);
    doSearch(null);
  };

  const handleNext = () => {
    if (!nextCursor) return;
    setCursorStack((s) => [...s, nextCursor]);
    setPage((p) => p + 1);
    doSearch(nextCursor);
  };

  const handlePrev = () => {
    if (page <= 1) return;
    const prev = cursorStack[page - 2];
    setCursorStack((s) => s.slice(0, -1));
    setPage((p) => p - 1);
    doSearch(prev);
  };

  return (
    <div>
      <div className="section-header">
        <h2 className="section-title">Журнал событий</h2>
        <p className="section-subtitle">Поиск по операции, результату или заявке</p>
      </div>

      {/* Filter card */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem' }}>
        {/* Mode tabs */}
        <div style={{ display: 'flex', gap: '0.25rem', marginBottom: '1.25rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0' }}>
          {[
            { key: 'by-operation', label: 'По операции' },
            { key: 'by-result', label: 'По результату' },
            { key: 'by-booking', label: 'По заявке' },
          ].map(({ key, label }) => (
            <button
              key={key}
              className={`tab-btn ${mode === key ? 'active' : ''}`}
              onClick={() => { setMode(key); setSearched(false); setEvents([]); }}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Filters row */}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          {mode === 'by-operation' && (
            <div className="form-group" style={{ marginBottom: 0, minWidth: '200px' }}>
              <label className="form-label">Операция</label>
              <select className="form-input" value={operation} onChange={(e) => setOperation(e.target.value)}>
                {OPERATION_OPTIONS.map((op) => (
                  <option key={op} value={op}>{OPERATION_LABELS[op]}</option>
                ))}
              </select>
            </div>
          )}

          {mode === 'by-result' && (
            <div className="form-group" style={{ marginBottom: 0, minWidth: '160px' }}>
              <label className="form-label">Результат</label>
              <select className="form-input" value={result} onChange={(e) => setResult(e.target.value)}>
                {RESULT_OPTIONS.map((r) => (
                  <option key={r} value={r}>{RESULT_LABELS[r]}</option>
                ))}
              </select>
            </div>
          )}

          {mode === 'by-booking' && (
            <div className="form-group" style={{ marginBottom: 0, minWidth: '160px' }}>
              <label className="form-label">ID заявки</label>
              <input
                className="form-input"
                type="number"
                min="1"
                placeholder="Например: 1"
                value={bookingId}
                onChange={(e) => setBookingId(e.target.value)}
              />
            </div>
          )}

          <div className="form-group" style={{ marginBottom: 0, minWidth: '150px' }}>
            <label className="form-label">Дата с</label>
            <input className="form-input" type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </div>

          <div className="form-group" style={{ marginBottom: 0, minWidth: '150px' }}>
            <label className="form-label">Дата по</label>
            <input className="form-input" type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
          </div>

          <button className="btn btn-primary" onClick={handleSearch} disabled={loading} style={{ alignSelf: 'flex-end' }}>
            <Search size={14} />
            Найти
          </button>
        </div>
      </div>

      {error && <div className="form-error">{error}</div>}

      {/* Results */}
      {loading ? (
        <div className="empty-state">
          <p>Загрузка...</p>
        </div>
      ) : searched && events.length === 0 ? (
        <div className="empty-state">
          <h3>Событий не найдено</h3>
          <p>Попробуйте изменить фильтры или диапазон дат.</p>
        </div>
      ) : events.length > 0 ? (
        <>
          <div className="bookings-list">
            {events.map((e) => (
              <div key={e.eventId} className="card booking-card">
                <div className="booking-card-layout">
                  <div>
                    <div className="booking-title-row">
                      <span className="booking-title">{OPERATION_LABELS[e.operation] ?? e.operation}</span>
                      <span className={`status-badge ${e.result === 'SUCCESS' ? 'status-approved' : 'status-rejected'}`}>
                        {RESULT_LABELS[e.result] ?? e.result}
                      </span>
                    </div>
                    <div className="booking-meta">
                      Заявка #{e.bookingId}
                      {e.errorCode && <span style={{ color: 'var(--accent-danger)', marginLeft: '0.5rem' }}>Ошибка: {e.errorCode}</span>}
                    </div>
                    <div className="booking-dates">
                      {new Date(e.occurredAt).toLocaleString('ru-RU')} · {e.eventId}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '16px' }}>
            {page > 1 ? (
              <button className="btn btn-outline btn-sm" onClick={handlePrev} disabled={loading}>
                <ChevronLeft size={14} />
                Назад
              </button>
            ) : <span />}
            <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Страница {page}</span>
            {nextCursor ? (
              <button className="btn btn-outline btn-sm" onClick={handleNext} disabled={loading}>
                Вперёд
                <ChevronRight size={14} />
              </button>
            ) : <span />}
          </div>
        </>
      ) : null}
    </div>
  );
}
