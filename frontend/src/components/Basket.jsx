import React, { useState, useEffect } from 'react';
import { ShoppingCart, Clock, Send, Trash2 } from 'lucide-react';

export function Basket({ items, services, onCheckout, onDelete }) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    if (items.length === 0) return;
    const timer = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => clearInterval(timer);
  }, [items.length]);

  const getServiceName = (serviceId) => {
    if (!serviceId) return 'Университетский ресурс';
    const found = services.find((s) => s.id === serviceId);
    return found ? found.name : `Услуга #${serviceId}`;
  };

  const calculateTtl = (item) => {
    const startTime = item._localReceivedAt || (item.addedAt ? new Date(item.addedAt).getTime() : now);
    const elapsedSeconds = Math.floor((now - Math.min(startTime, now)) / 1000);
    const ttlTotal = 60;
    const remaining = Math.max(0, ttlTotal - elapsedSeconds);
    const percent = Math.min(100, Math.max(0, (remaining / ttlTotal) * 100));

    return {
      seconds: remaining,
      percent,
      expired: remaining === 0
    };
  };

  const getTtlBarColor = (percent, expired) => {
    if (expired || percent < 25) return 'var(--accent-danger)';
    if (percent < 50) return 'var(--accent-warning)';
    return 'var(--accent-success)';
  };

  return (
    <div>
      <div className="section-header">
        <h2 className="section-title">Корзина</h2>
        <p className="section-subtitle">
          Выбранные ресурсы закреплены на 1 минуту. Подайте заявку, пока действует резерв.
        </p>
      </div>

      {items.length === 0 ? (
        <div className="empty-state">
          <ShoppingCart size={36} color="var(--text-muted)" className="empty-state-icon" />
          <h3>Корзина пуста</h3>
          <p>Выберите услугу в каталоге, чтобы добавить её сюда.</p>
        </div>
      ) : (
        items.map((item) => {
          const { seconds, percent, expired } = calculateTtl(item);
          return (
            <div key={item.id} className={`basket-item-card ${expired ? 'basket-item-expired' : ''}`}>
              <div>
                <div className="basket-item-name">
                  {getServiceName(item.serviceId)}
                </div>
                <div className="basket-item-detail">
                  Аудитория №{item.room} · {new Date(item.bookingDate).toLocaleString('ru-RU')}
                </div>
              </div>

              <div className="basket-item-right">
                <div className="ttl-box">
                  <div className="ttl-text">
                    <span className={`ttl-label ${expired ? 'ttl-label-expired' : 'ttl-label-active'}`}>
                      <Clock size={13} />
                      {expired ? 'Истёк' : `${seconds} сек`}
                    </span>
                  </div>
                  <div className="ttl-bar-bg">
                    <div
                      className="ttl-bar-fill"
                      style={{
                        width: `${percent}%`,
                        background: getTtlBarColor(percent, expired)
                      }}
                    />
                  </div>
                </div>

                <div className="basket-btn-group">
                  <button
                    className={`btn ${expired ? 'btn-outline' : 'btn-primary'} btn-sm`}
                    onClick={() => onCheckout(item.id)}
                    disabled={expired}
                    title={expired ? 'Время вышло' : 'Отправить заявку'}
                  >
                    <Send size={13} />
                    Подать заявку
                  </button>

                  <button
                    className="btn btn-outline btn-sm"
                    onClick={() => onDelete(item.id)}
                    title="Удалить"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
