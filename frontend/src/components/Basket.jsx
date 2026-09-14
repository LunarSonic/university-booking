import React, { useState, useEffect } from 'react';
import { ShoppingCart, Clock, Send, Trash2 } from 'lucide-react';

export function Basket({ items, services, onCheckout, onDelete }) {
  const [now, setNow] = useState(Date.now());

  // Timer ticker every second
  useEffect(() => {
    const timer = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const getServiceName = (serviceId) => {
    if (!serviceId) return 'Университетский ресурс';
    const found = services.find((s) => s.id === serviceId);
    return found ? found.name : `Услуга #${serviceId}`;
  };

  const calculateTtl = (item) => {
    // Use local received timestamp so clock desync between Mac and Docker never causes 0s
    const startTime = item._localReceivedAt || (item.addedAt ? new Date(item.addedAt).getTime() : now);
    
    // Check if startTime is in the future or corrupted
    const validStartTime = Math.min(startTime, now);
    const elapsedSeconds = Math.floor((now - validStartTime) / 1000);
    const ttlTotal = 60; // 60 seconds
    const remaining = Math.max(0, ttlTotal - elapsedSeconds);
    const percent = Math.min(100, Math.max(0, (remaining / ttlTotal) * 100));

    return {
      seconds: remaining,
      percent,
      expired: remaining === 0
    };
  };

  return (
    <div>
      <div className="section-header">
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700 }}>Корзина предварительного бронирования</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
            Выбранные ресурсы временно закреплены за вами на 1 минуту. Нажмите «Подать заявку», пока действует резерв.
          </p>
        </div>
      </div>

      {items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem 1rem', background: 'var(--bg-card)', borderRadius: '0.85rem', border: '1px solid var(--border-color)' }}>
          <ShoppingCart size={48} color="#6b7280" style={{ margin: '0 auto 1rem auto' }} />
          <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Ваша корзина пуста</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.5rem' }}>
            Перейдите во вкладку «Каталог услуг», чтобы выбрать аудиторию или услугу.
          </p>
        </div>
      ) : (
        items.map((item) => {
          const { seconds, percent, expired } = calculateTtl(item);
          return (
            <div key={item.id} className="basket-item-card" style={{ borderColor: expired ? 'rgba(239, 68, 68, 0.4)' : 'var(--border-color)' }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '1.1rem', color: 'var(--text-primary)', marginBottom: '0.35rem' }}>
                  {getServiceName(item.serviceId)}
                </div>

                <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                  Аудитория: <b>№{item.room}</b> • Дата брони: <b>{new Date(item.bookingDate).toLocaleString('ru-RU')}</b>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
                <div className="ttl-box">
                  <div className="ttl-text">
                    <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: expired ? '#ef4444' : '#f59e0b' }}>
                      <Clock size={14} />
                      {expired ? 'Резерв истёк' : `Резерв: ${seconds} сек`}
                    </span>
                  </div>
                  <div className="ttl-bar-bg">
                    <div
                      className="ttl-bar-fill"
                      style={{
                        width: `${percent}%`,
                        background: expired ? '#ef4444' : percent < 25 ? '#ef4444' : percent < 50 ? '#f59e0b' : '#10b981'
                      }}
                    />
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button
                    className={`btn ${expired ? 'btn-outline' : 'btn-primary'} btn-sm`}
                    onClick={() => onCheckout(item.id)}
                    disabled={expired}
                    title={expired ? 'Время вышло' : 'Отправить заявку на рассмотрение'}
                  >
                    <Send size={14} />
                    Подать заявку
                  </button>

                  <button
                    className="btn btn-outline btn-sm"
                    style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                    onClick={() => onDelete(item.id)}
                    title="Удалить из корзины"
                  >
                    <Trash2 size={14} />
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
