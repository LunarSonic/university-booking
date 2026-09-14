import React from 'react';
import { CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';

export function Toast({ toasts, onDismiss }) {
  if (!toasts || toasts.length === 0) return null;

  return (
    <div className="toast-container">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`toast toast-${toast.type}`}
          onClick={() => onDismiss(toast.id)}
          style={{ cursor: 'pointer' }}
        >
          {toast.type === 'success' && <CheckCircle2 size={20} color="#10b981" />}
          {toast.type === 'warning' && <AlertTriangle size={20} color="#f59e0b" />}
          {toast.type === 'error' && <XCircle size={20} color="#ef4444" />}
          <div>
            <div style={{ fontWeight: 600 }}>{toast.title}</div>
            <div style={{ color: '#9ca3af', fontSize: '0.8rem' }}>{toast.message}</div>
          </div>
        </div>
      ))}
    </div>
  );
}
