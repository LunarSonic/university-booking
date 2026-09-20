import React, { useState, useEffect, useRef } from 'react';
import { X, Calendar, MapPin, Send, ShoppingCart, Layers } from 'lucide-react';

export function BookingModal({ service, services = [], userId, onClose, onAddToBasket, onCreateDirectBooking }) {
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

  const [room, setRoom] = useState('305');

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
  const modalRef = useRef(null);

  const [minDateTime, setMinDateTime] = useState(getMinDateTime);

  useEffect(() => {
    const timer = setInterval(() => {
      setMinDateTime(getMinDateTime());
    }, 60000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    modalRef.current?.focus();
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const formatToIsoSeconds = (dtString) => {
    if (!dtString) return '';
    if (dtString.length === 19) return dtString;
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
      setValidationError('Выберите услугу');
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
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div className="modal-content" onClick={(e) => e.stopPropagation()} ref={modalRef} tabIndex={-1}>
        <div className="modal-header">
          <h3 id="modal-title">Оформление бронирования</h3>
          <button className="btn btn-outline btn-sm modal-close-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className="modal-body">
          {validationError && (
            <div className="form-error">
              {validationError}
            </div>
          )}

          <div className="form-group">
            <label htmlFor="field-service" className="form-label">
              <Layers size={13} className="form-label-icon" />
              Услуга
            </label>
            <select
              id="field-service"
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
            <label htmlFor="field-room" className="form-label">
              <MapPin size={13} className="form-label-icon" />
              Номер аудитории
            </label>
            <input
              id="field-room"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              className="form-input"
              value={room}
              onChange={(e) => setRoom(e.target.value.replace(/\D/g, ''))}
              placeholder="Например, 305"
            />
          </div>

          <div className="form-group">
            <label htmlFor="field-date" className="form-label">
              <Calendar size={13} className="form-label-icon" />
              Дата и время
            </label>
            <input
              id="field-date"
              type="datetime-local"
              className="form-input"
              min={minDateTime}
              value={bookingDate}
              onChange={(e) => setBookingDate(e.target.value)}
              required
            />
          </div>

          <div className="modal-actions">
            <button
              className="btn btn-primary"
              disabled={loading}
              onClick={() => handleSubmit('direct')}
            >
              <Send size={15} />
              {loading ? 'Создание...' : 'Забронировать сразу'}
            </button>

            <button
              className="btn btn-outline"
              disabled={loading}
              onClick={() => handleSubmit('basket')}
            >
              <ShoppingCart size={15} />
              В корзину (резерв 1 мин)
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
