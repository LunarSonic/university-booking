const API_BASE = '';

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

export async function fetchBookings(status = 'NEW') {
  const response = await fetch(`${API_BASE}/bookings?status=${status}`);
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
    throw new Error('Не удалось удалить заявку');
  }
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
