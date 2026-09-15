import React from 'react';
import { GraduationCap, User, ShieldCheck } from 'lucide-react';
import { users } from '../constants/users';

export function Navbar({ currentUser, setCurrentUser }) {

  return (
    <header className="navbar">
      <div className="navbar-content">
        <div className="logo-group">
          <div className="logo-icon">
            <GraduationCap size={22} />
          </div>
          <div>
            <h1 className="logo-title">University Booking</h1>
            <p className="logo-subtitle">
              {currentUser.role === 'admin'
                ? 'Администратор'
                : currentUser.name}
            </p>
          </div>
        </div>

        <div className="nav-actions">
          <div className="user-selector" style={{ borderColor: currentUser.role === 'admin' ? 'var(--accent-primary)' : undefined }}>
            {currentUser.role === 'admin' ? (
              <ShieldCheck size={16} color="var(--accent-primary)" />
            ) : (
              <User size={15} color="var(--text-muted)" />
            )}
            <select
              value={currentUser.id}
              onChange={(e) => {
                const selected = users.find((u) => u.id === Number(e.target.value));
                if (selected) setCurrentUser(selected);
              }}
            >
              <optgroup label="Пользователи">
                <option value={1}>Студент 1</option>
                <option value={2}>Студент 2</option>
                <option value={42}>Преподаватель</option>
              </optgroup>
              <optgroup label="Управление">
                <option value={999}>Администратор</option>
              </optgroup>
            </select>
          </div>
        </div>
      </div>
    </header>
  );
}
