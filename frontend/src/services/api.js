const API_BASE = import.meta.env.VITE_API_BASE || '';

export async function fetchServices() {
  const response = await fetch(`${API_BASE}/services`);

  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось загрузить каталог услуг');
  }

  return await response.json();
}

export async function fetchBasket(userId) {
  const response = await fetch(`${API_BASE}/basket/user/${userId}`);
  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось загрузить корзину');
  }
  return await response.json();
}

export async function addToBasket(data) {
  const response = await fetch(`${API_BASE}/basket`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!response.ok) {
    let errorMsg = 'Не удалось добавить в корзину';
    try {
      const err = await response.json();
      if (Array.isArray(err.error)) {
        errorMsg = err.error.join(', ');
      } else if (err.error) {
        errorMsg = err.error;
      }
    } catch (_) {}

    if (response.status === 429) {
      errorMsg = 'Слишком много действий. Пожалуйста, подождите минуту.';
    }
    throw new Error(errorMsg);
  }
  return await response.json();
}

export async function deleteBasketItem(id) {
  const response = await fetch(`${API_BASE}/basket/${id}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Позиция уже удалена или истекла.');
    }
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось удалить позицию из корзины');
  }
}

export async function checkoutBasketItem(id) {
  const response = await fetch(`${API_BASE}/basket/${id}/checkout`, {
    method: 'POST'
  });
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Время резерва истекло. Пожалуйста, добавьте услугу заново.');
    }
    if (response.status === 429) {
      throw new Error('Слишком много действий. Подождите минуту.');
    }
    let errorMsg = 'Не удалось оформить заявку';
    try {
      const err = await response.json();
      if (err.error) errorMsg = Array.isArray(err.error) ? err.error.join(', ') : err.error;
    } catch (_) {}
    throw new Error(errorMsg);
  }
  return await response.json();
}

export async function fetchBookings(status = 'NEW', userId = null, signal) {
  const params = new URLSearchParams({ status });
  if (userId) params.append('userId', userId);
  const response = await fetch(`${API_BASE}/bookings?${params}`, { signal });
  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось загрузить список заявок');
  }
  return await response.json();
}

export async function searchBookings({ status, userId, direction = 'DESC', needsProjector, cursor, signal }) {
  const params = new URLSearchParams({
    status,
    createdFrom: '2020-01-01T00:00:00Z',
    createdTo: '2030-01-01T00:00:00Z',
    limit: 5,
    direction,
  });
  if (userId) params.append('userId', userId);
  if (needsProjector != null) params.append('needsProjector', needsProjector);
  if (cursor) params.append('cursor', cursor);
  const response = await fetch(`${API_BASE}/bookings/search?${params}`, { signal });
  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось загрузить список заявок');
  }
  return await response.json();
}

export async function updateBookingStatus(id, newStatus) {
  const response = await fetch(`${API_BASE}/bookings/${id}?status=${newStatus}`, {
    method: 'PATCH'
  });
  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось изменить статус заявки');
  }
  return await response.json();
}

export async function deleteBooking(id) {
  const response = await fetch(`${API_BASE}/bookings/${id}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Заявка не найдена или уже удалена.');
    }
    if (response.status === 429) {
      throw new Error('Слишком много запросов. Пожалуйста, подождите минуту.');
    }
    throw new Error('Не удалось удалить заявку');
  }
}

export async function fetchEventsByResult({ result, from, to, limit = 20, cursor, signal }) {
  const params = new URLSearchParams({ result, from, to, limit });
  if (cursor) params.append('cursor', cursor);
  const response = await fetch(`${API_BASE}/events/by-result?${params}`, { signal });
  if (!response.ok) throw new Error('Не удалось загрузить события');
  return await response.json();
}

export async function fetchEventsByOperation({ operation, from, to, limit = 20, cursor, signal }) {
  const params = new URLSearchParams({ operation, from, to, limit });
  if (cursor) params.append('cursor', cursor);
  const response = await fetch(`${API_BASE}/events/by-operation?${params}`, { signal });
  if (!response.ok) throw new Error('Не удалось загрузить события');
  return await response.json();
}

export async function fetchEventsByBooking({ bookingId, from, to, limit = 20, cursor, signal }) {
  const params = new URLSearchParams({ bookingId, from, to, limit });
  if (cursor) params.append('cursor', cursor);
  const response = await fetch(`${API_BASE}/events/by-booking?${params}`, { signal });
  if (!response.ok) throw new Error('Не удалось загрузить события');
  return await response.json();
}

export async function createDirectBooking(data) {
  const response = await fetch(`${API_BASE}/bookings`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!response.ok) {
    let errorMsg = 'Не удалось создать заявку';
    try {
      const err = await response.json();
      if (Array.isArray(err.error)) {
        errorMsg = err.error.join(', ');
      } else if (err.error) {
        errorMsg = err.error;
      }
    } catch (_) {}

    if (response.status === 429) {
      errorMsg = 'Слишком много действий. Пожалуйста, подождите минуту.';
    }
    throw new Error(errorMsg);
  }
  return await response.json();
}
