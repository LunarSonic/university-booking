import React from 'react';
import { BookOpen, Presentation, Building, UserCheck, CalendarPlus } from 'lucide-react';

export function ServicesCatalog({ services, loading, onSelectService }) {
  const getIcon = (id) => {
    switch (id) {
      case 1: return <BookOpen size={22} />;
      case 2: return <Presentation size={22} />;
      case 3: return <Building size={22} />;
      case 4: return <UserCheck size={22} />;
      default: return <BookOpen size={22} />;
    }
  };

  return (
    <div>
      <div className="section-header">
        <h2 className="section-title">Доступные услуги</h2>
        <p className="section-subtitle">
          Бронирование аудиторий, оборудования и консультаций
        </p>
      </div>

      {loading && services.length === 0 && (
        <div className="empty-state">
          <h3>Загрузка услуг...</h3>
          <p>Пожалуйста, подождите</p>
        </div>
      )}

      <div className="services-grid">
        {services.map((service) => (
          <div key={service.id} className="card">
            <div>
              <div className="card-header">
                <div className="card-icon">{getIcon(service.id)}</div>
                <div>
                  <h3 className="card-title">{service.name}</h3>
                </div>
              </div>
              <p className="card-desc">{service.description}</p>
            </div>

            <div className="card-footer">
              <button
                className="btn btn-primary"
                style={{ width: '100%' }}
                onClick={() => onSelectService(service)}
              >
                <CalendarPlus size={15} />
                Забронировать
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
