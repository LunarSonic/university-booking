import React from 'react';
import { Check, X, Trash2, Plus, Calendar, Shield, RefreshCw } from 'lucide-react';

export function BookingsAdmin({
  bookings,
  currentUser,
  statusFilter,
  setStatusFilter,
  services,
  onUpdateStatus,
  onDeleteBooking,
  onOpenCreateModal,
  onRefresh,
  loading
}) {
  const isAdmin = currentUser.role === 'admin';

  // If not admin, show only bookings belonging to the current user
  const visibleBookings = isAdmin
    ? bookings
    : bookings.filter((b) => b.userId === currentUser.id);

  const getServiceName = (serviceId) => {
    if (!serviceId) return 'Университетский ресурс';
    const found = services.find((s) => s.id === serviceId);
    return found ? found.name : `Услуга #${serviceId}`;
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'NEW': return 'На рассмотрении';
      case 'APPROVED': return 'Одобрено';
      case 'REJECTED': return 'Отклонено';
      default: return status;
    }
  };

  const getUserLabel = (uid) => {
    if (uid === 1) return 'Студент 1';
    if (uid === 2) return 'Студент 2';
    if (uid === 42) return 'Преподаватель';
    return `Пользователь #${uid}`;
  };

  return (
    <div>
      <div className="section-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700 }}>
              {isAdmin ? 'Панель администратора • Модерация заявок' : 'Мои заявки на бронирование'}
            </h2>
            {isAdmin && (
              <span className="badge-tag" style={{ background: 'rgba(59, 130, 246, 0.2)', color: '#60a5fa' }}>
                <Shield size={12} /> Админ-доступ
              </span>
            )}
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
            {isAdmin
              ? 'Просмотр, одобрение и отклонение заявок всех пользователей'
              : `Список заявок, поданных от лица: ${currentUser.name}`}
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn btn-outline btn-sm" onClick={onRefresh} disabled={loading} title="Обновить список">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            Обновить
          </button>

          {!isAdmin && (
            <button className="btn btn-primary btn-sm" onClick={onOpenCreateModal}>
              <Plus size={15} />
              Подать заявку
            </button>
          )}
        </div>
      </div>

      {/* Status Filter Tabs */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <button
          className={`tab-btn ${statusFilter === 'NEW' ? 'active' : ''}`}
          onClick={() => setStatusFilter('NEW')}
        >
          🟡 На рассмотрении
        </button>
        <button
          className={`tab-btn ${statusFilter === 'APPROVED' ? 'active' : ''}`}
          onClick={() => setStatusFilter('APPROVED')}
        >
          🟢 Одобренные
        </button>
        <button
          className={`tab-btn ${statusFilter === 'REJECTED' ? 'active' : ''}`}
          onClick={() => setStatusFilter('REJECTED')}
        >
          🔴 Отклонённые
        </button>
      </div>

      {visibleBookings.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem 1rem', background: 'var(--bg-card)', borderRadius: '0.85rem', border: '1px solid var(--border-color)' }}>
          <Calendar size={48} color="#6b7280" style={{ margin: '0 auto 1rem auto' }} />
          <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
            {isAdmin
              ? `Нет заявок со статусом «${getStatusLabel(statusFilter)}»`
              : `У вас нет заявок со статусом «${getStatusLabel(statusFilter)}»`}
          </h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.5rem' }}>
            {isAdmin
              ? 'Когда пользователи оформят заявки, они появятся в этом списке.'
              : 'Выберите услугу в каталоге и оформите бронирование.'}
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
          {visibleBookings.map((booking) => (
            <div key={booking.id} className="card" style={{ padding: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.75rem' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', marginBottom: '0.35rem' }}>
                    <span style={{ fontWeight: 700, fontSize: '1.15rem' }}>
                      Заявка #{booking.id}
                    </span>
                    <span className={`status-badge status-${booking.statusRequest ? booking.statusRequest.toLowerCase() : 'new'}`}>
                      {getStatusLabel(booking.statusRequest)}
                    </span>
                  </div>

                  <div style={{ fontSize: '0.95rem', color: 'var(--text-primary)', marginTop: '0.4rem' }}>
                    <b>Услуга:</b> {getServiceName(booking.serviceId)}
                  </div>

                  <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                    {isAdmin && (
                      <span style={{ marginRight: '0.75rem' }}>
                        Заявитель: <b>{getUserLabel(booking.userId)}</b>
                      </span>
                    )}
                    Аудитория: <b>№{booking.room}</b>
                  </div>

                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
                    Дата проведения: {new Date(booking.bookingDate).toLocaleString('ru-RU')} • Оформлена: {new Date(booking.createDate).toLocaleString('ru-RU')}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  {/* ADMIN ACTIONS: Approve / Reject */}
                  {isAdmin && booking.statusRequest === 'NEW' && (
                    <>
                      <button
                        className="btn btn-success btn-sm"
                        onClick={() => onUpdateStatus(booking.id, 'APPROVED')}
                        title="Одобрить заявку"
                      >
                        <Check size={14} />
                        Одобрить
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => onUpdateStatus(booking.id, 'REJECTED')}
                        title="Отклонить заявку"
                      >
                        <X size={14} />
                        Отклонить
                      </button>
                    </>
                  )}

                  {isAdmin && booking.statusRequest === 'APPROVED' && (
                    <button
                      className="btn btn-outline btn-sm"
                      style={{ borderColor: '#ef4444', color: '#ef4444' }}
                      onClick={() => onUpdateStatus(booking.id, 'REJECTED')}
                    >
                      <X size={14} />
                      Отклонить
                    </button>
                  )}

                  {isAdmin && booking.statusRequest === 'REJECTED' && (
                    <button
                      className="btn btn-outline btn-sm"
                      style={{ borderColor: '#10b981', color: '#10b981' }}
                      onClick={() => onUpdateStatus(booking.id, 'APPROVED')}
                    >
                      <Check size={14} />
                      Одобрить
                    </button>
                  )}

                  {/* Delete / Cancel button */}
                  {(isAdmin || booking.statusRequest === 'NEW') && (
                    <button
                      className="btn btn-outline btn-sm"
                      style={{ borderColor: 'var(--border-color)', color: 'var(--text-muted)' }}
                      onClick={() => onDeleteBooking(booking.id)}
                      title={isAdmin ? 'Удалить заявку' : 'Отозвать заявку'}
                    >
                      <Trash2 size={14} />
                      {!isAdmin && 'Отозвать'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
