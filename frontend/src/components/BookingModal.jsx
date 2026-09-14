import React, { useState, useEffect } from 'react';
import { X, Calendar, MapPin, Send, ShoppingCart, Layers } from 'lucide-react';

export function BookingModal({ service, services = [], userId, onClose, onAddToBasket, onCreateDirectBooking }) {
  // Select service
  const [selectedServiceId, setSelectedServiceId] = useState(
    service?.id || (services.length > 0 ? services[0].id : 1)
  );

  useEffect(() => {
    if (service?.id) {
      setSelectedServiceId(service.id);
    } else if (services.length > 0 && !selectedServiceId) {
      setSelectedServiceId(services[0].id);
    }
  }, [service, services]);

  const [room, setRoom] = useState(305);

  // Default to tomorrow 10:00 in local time
  const getInitialDateTime = () => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    d.setHours(10, 0, 0, 0);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };

  const getMinDateTime = () => {
    const d = new Date();
    d.setMinutes(d.getMinutes() + 5);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };

  const [bookingDate, setBookingDate] = useState(getInitialDateTime());
  const [loading, setLoading] = useState(false);
  const [validationError, setValidationError] = useState('');

  const formatToIsoSeconds = (dtString) => {
    if (!dtString) return '';
    // If it has seconds already (length 19), return as is
    if (dtString.length === 19) return dtString;
    // If format is YYYY-MM-DDTHH:mm (length 16), append :00
    if (dtString.length === 16) return `${dtString}:00`;
    return dtString;
  };

  const handleSubmit = async (mode) => {
    setValidationError('');
    const roomNum = Number(room);
    if (!roomNum || roomNum <= 0) {
      setValidationError('Номер аудитории должен быть положительным числом');
      return;
    }

    if (!selectedServiceId) {
      setValidationError('Пожалуйста, выберите услугу');
      return;
    }

    const selectedTime = new Date(bookingDate).getTime();
    if (isNaN(selectedTime) || selectedTime <= Date.now()) {
      setValidationError('Дата бронирования должна быть в будущем');
      return;
    }

    setLoading(true);
    const payload = {
      userId: Number(userId),
      serviceId: Number(selectedServiceId),
      room: roomNum,
      bookingDate: formatToIsoSeconds(bookingDate)
    };

    try {
      if (mode === 'basket') {
        await onAddToBasket(payload);
      } else {
        await onCreateDirectBooking(payload);
      }
      onClose();
    } catch (e) {
      setValidationError(e.message || 'Ошибка бронирования');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>Оформление бронирования</h3>
          <button className="btn btn-outline btn-sm" onClick={onClose}>
            <X size={16} />
          </button>
        </div>

        {validationError && (
          <div style={{ background: 'rgba(239, 68, 68, 0.15)', border: '1px solid rgba(239, 68, 68, 0.4)', color: '#f87171', padding: '0.65rem 0.85rem', borderRadius: '0.5rem', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
            {validationError}
          </div>
        )}

        <div className="form-group">
          <label className="form-label">
            <Layers size={14} style={{ display: 'inline', marginRight: '4px' }} />
            Выберите услугу или ресурс
          </label>
          <select
            className="form-input"
            value={selectedServiceId}
            onChange={(e) => setSelectedServiceId(Number(e.target.value))}
          >
            {services.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label className="form-label">
            <MapPin size={14} style={{ display: 'inline', marginRight: '4px' }} />
            Номер аудитории / помещения
          </label>
          <input
            type="number"
            className="form-input"
            value={room}
            min={1}
            max={999}
            onChange={(e) => setRoom(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label className="form-label">
            <Calendar size={14} style={{ display: 'inline', marginRight: '4px' }} />
            Дата и время бронирования
          </label>
          <input
            type="datetime-local"
            className="form-input"
            min={getMinDateTime()}
            value={bookingDate}
            onChange={(e) => setBookingDate(e.target.value)}
            required
          />
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.75rem', flexDirection: 'column' }}>
          <button
            className="btn btn-primary"
            disabled={loading}
            onClick={() => handleSubmit('direct')}
          >
            <Send size={16} />
            {loading ? 'Создание...' : 'Забронировать сразу'}
          </button>

          <button
            className="btn btn-outline"
            disabled={loading}
            onClick={() => handleSubmit('basket')}
          >
            <ShoppingCart size={16} />
            Отложить в корзину (резерв на 1 мин)
          </button>
        </div>
      </div>
    </div>
  );
}
