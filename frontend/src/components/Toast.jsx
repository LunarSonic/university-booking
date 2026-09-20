import React from 'react';
import { CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';

export function Toast({ toasts, onDismiss }) {
  if (!toasts || toasts.length === 0) return null;

  return (
    <div className="toast-container" aria-live="polite" role="region" aria-label="Уведомления">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`toast toast-${toast.type}`}
        >
          {toast.type === 'success' && <CheckCircle2 size={18} color="var(--accent-success)" />}
          {toast.type === 'warning' && <AlertTriangle size={18} color="var(--accent-warning)" />}
          {toast.type === 'error' && <XCircle size={18} color="var(--accent-danger)" />}
          <div className="toast-content">
            <div className="toast-title">{toast.title}</div>
            <div className="toast-message">{toast.message}</div>
          </div>
          <button
            onClick={() => onDismiss(toast.id)}
            className="toast-close"
            aria-label="Закрыть уведомление"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
