import React from 'react';
import { Check, X, Trash2, Plus } from 'lucide-react';
import { getUserLabel } from '../constants/users';

export function BookingsAdmin({
  bookings,
  currentUser,
  statusFilter,
  setStatusFilter,
  services,
  onUpdateStatus,
  onDeleteBooking,
  onOpenCreateModal,
  loading
}) {
  const isAdmin = currentUser.role === 'admin';

  const visibleBookings = bookings;

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

  const handleDelete = (id) => {
    onDeleteBooking(id);
  };

  const handleStatusChange = (id, newStatus) => {
    onUpdateStatus(id, newStatus);
  };

  return (
    <div>
      <div className="section-header bookings-header">
        <div>
          <h2 className="section-title">
            {isAdmin ? 'Модерация заявок' : 'Мои заявки'}
          </h2>
          <p className="section-subtitle">
            {isAdmin
              ? 'Просмотр, одобрение и отклонение заявок'
              : `Заявки от: ${currentUser.name}`}
          </p>
        </div>

        {!isAdmin && (
          <button className="btn btn-primary btn-sm" onClick={onOpenCreateModal}>
            <Plus size={14} />
            Подать заявку
          </button>
        )}
      </div>

      {/* Status Filter Tabs */}
      <div className="bookings-filter-tabs">
        <button
          className={`tab-btn ${statusFilter === 'NEW' ? 'active' : ''}`}
          onClick={() => setStatusFilter('NEW')}
        >
          <span className="status-dot status-dot-new" />
          На рассмотрении
        </button>
        <button
          className={`tab-btn ${statusFilter === 'APPROVED' ? 'active' : ''}`}
          onClick={() => setStatusFilter('APPROVED')}
        >
          <span className="status-dot status-dot-approved" />
          Одобренные
        </button>
        <button
          className={`tab-btn ${statusFilter === 'REJECTED' ? 'active' : ''}`}
          onClick={() => setStatusFilter('REJECTED')}
        >
          <span className="status-dot status-dot-rejected" />
          Отклонённые
        </button>
      </div>

      {visibleBookings.length === 0 ? (
        <div className="empty-state">
          <h3>
            {isAdmin
              ? `Нет заявок со статусом «${getStatusLabel(statusFilter)}»`
              : `У вас нет заявок «${getStatusLabel(statusFilter)}»`}
          </h3>
          <p>
            {isAdmin
              ? 'Когда пользователи оформят заявки, они появятся здесь.'
              : 'Выберите услугу в каталоге и оформите бронирование.'}
          </p>
        </div>
      ) : (
        <div className="bookings-list">
          {visibleBookings.map((booking) => (
            <div key={booking.id} className="card booking-card">
              <div className="booking-card-layout">
                <div>
                  <div className="booking-title-row">
                    <span className="booking-title">
                      Заявка #{booking.id}
                    </span>
                    <span className={`status-badge status-${booking.statusRequest ? booking.statusRequest.toLowerCase() : 'new'}`}>
                      {getStatusLabel(booking.statusRequest)}
                    </span>
                  </div>

                  <div className="booking-service">
                    {getServiceName(booking.serviceId)}
                  </div>

                  <div className="booking-meta">
                    {isAdmin && (
                      <span className="booking-meta-user">
                        {getUserLabel(booking.userId)}
                      </span>
                    )}
                    Аудитория №{booking.room}
                  </div>

                  <div className="booking-dates">
                    {new Date(booking.bookingDate).toLocaleString('ru-RU')} · создана {new Date(booking.createDate).toLocaleString('ru-RU')}
                  </div>
                </div>

                <div className="booking-actions">
                  {isAdmin && booking.statusRequest === 'NEW' && (
                    <>
                      <button
                        className="btn btn-success btn-sm"
                        onClick={() => handleStatusChange(booking.id, 'APPROVED')}
                        title="Одобрить"
                      >
                        <Check size={13} />
                        Одобрить
                      </button>
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleStatusChange(booking.id, 'REJECTED')}
                        title="Отклонить"
                      >
                        <X size={13} />
                        Отклонить
                      </button>
                    </>
                  )}

                  {isAdmin && booking.statusRequest === 'APPROVED' && (
                    <button
                      className="btn btn-outline btn-sm btn-reject-outline"
                      onClick={() => handleStatusChange(booking.id, 'REJECTED')}
                    >
                      <X size={13} />
                      Отклонить
                    </button>
                  )}

                  {isAdmin && booking.statusRequest === 'REJECTED' && (
                    <button
                      className="btn btn-outline btn-sm btn-approve-outline"
                      onClick={() => handleStatusChange(booking.id, 'APPROVED')}
                    >
                      <Check size={13} />
                      Одобрить
                    </button>
                  )}

                  {(isAdmin || booking.statusRequest === 'NEW') && (
                    <button
                      className="btn btn-outline btn-sm btn-muted"
                      onClick={() => handleDelete(booking.id)}
                      title={isAdmin ? 'Удалить' : 'Отозвать'}
                    >
                      <Trash2 size={13} />
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
