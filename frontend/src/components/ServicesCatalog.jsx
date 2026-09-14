import React from 'react';
import { BookOpen, Presentation, Building, UserCheck, CalendarPlus } from 'lucide-react';

export function ServicesCatalog({ services, loading, onSelectService }) {
  const getIcon = (id) => {
    switch (id) {
      case 1: return <BookOpen size={24} />;
      case 2: return <Presentation size={24} />;
      case 3: return <Building size={24} />;
      case 4: return <UserCheck size={24} />;
      default: return <BookOpen size={24} />;
    }
  };

  return (
    <div>
      <div className="section-header">
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700 }}>Доступные услуги и ресурсы</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
            Выберите нужный ресурс для бронирования аудитории, оборудования или консультации
          </p>
        </div>
      </div>

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
                <CalendarPlus size={16} />
                Забронировать
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
