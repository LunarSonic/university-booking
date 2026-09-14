import React from 'react';
import { GraduationCap, User, ShieldCheck } from 'lucide-react';

export function Navbar({ currentUser, setCurrentUser }) {
  const users = [
    { id: 1, name: 'Студент 1', role: 'student' },
    { id: 2, name: 'Студент 2', role: 'student' },
    { id: 42, name: 'Преподаватель', role: 'teacher' },
    { id: 999, name: 'Администратор', role: 'admin' }
  ];

  return (
    <header className="navbar">
      <div className="navbar-content">
        <div className="logo-group">
          <div className="logo-icon">
            <GraduationCap size={28} />
          </div>
          <div>
            <h1 className="logo-title">Университетское бронирование</h1>
            <p className="logo-subtitle">
              {currentUser.role === 'admin'
                ? 'Режим администратора • Обработка заявок'
                : `Личный кабинет: ${currentUser.name}`}
            </p>
          </div>
        </div>

        <div className="nav-actions">
          <div className="user-selector" style={{ borderColor: currentUser.role === 'admin' ? 'var(--accent-primary)' : 'var(--border-color)' }}>
            {currentUser.role === 'admin' ? (
              <ShieldCheck size={18} color="#3b82f6" />
            ) : (
              <User size={16} color="#9ca3af" />
            )}
            <select
              value={currentUser.id}
              onChange={(e) => {
                const selected = users.find((u) => u.id === Number(e.target.value));
                if (selected) setCurrentUser(selected);
              }}
            >
              <optgroup label="Пользователи">
                <option value={1}>👨‍🎓 Студент 1</option>
                <option value={2}>👨‍🎓 Студент 2</option>
                <option value={42}>👩‍🏫 Преподаватель</option>
              </optgroup>
              <optgroup label="Управление">
                <option value={999}>🛡️ Администратор системы</option>
              </optgroup>
            </select>
          </div>
        </div>
      </div>
    </header>
  );
}
