import React, { useState, useEffect, useRef } from 'react';
import { Navbar } from './components/Navbar';
import { ServicesCatalog } from './components/ServicesCatalog';
import { Basket } from './components/Basket';
import { BookingsAdmin } from './components/BookingsAdmin';
import { BookingModal } from './components/BookingModal';
import { Toast } from './components/Toast';
import {
  fetchServices,
  fetchBasket,
  addToBasket,
  deleteBasketItem,
  checkoutBasketItem,
  fetchBookings,
  updateBookingStatus,
  deleteBooking,
  createDirectBooking
} from './services/api';
import { BookOpen, ShoppingCart, CalendarCheck, Shield } from 'lucide-react';

export default function App() {
  const [currentUser, setCurrentUser] = useState({
    id: 1,
    name: 'Студент 1',
    role: 'student'
  });

  const [activeTab, setActiveTab] = useState('catalog'); // 'catalog' | 'basket' | 'bookings'

  // Data states
  const [services, setServices] = useState([]);
  const [basketItems, setBasketItems] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [statusFilter, setStatusFilter] = useState('NEW');

  // UI states
  const [loading, setLoading] = useState(false);
  const [modalService, setModalService] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [toasts, setToasts] = useState([]);

  // Toast helper
  const addToast = (title, message, type = 'success') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, title, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Load services once
  const loadServices = async () => {
    setLoading(true);
    try {
      const data = await fetchServices();
      setServices(data);
    } catch (err) {
      addToast('Ошибка', err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  // Load basket for current user
  const loadBasket = async (uid = currentUser.id) => {
    try {
      const items = await fetchBasket(uid);
      // Attach local received timestamp if missing so the 60s timer never starts at 0
      const enriched = items.map((it) => ({
        ...it,
        _localReceivedAt: it._localReceivedAt || Date.now()
      }));
      setBasketItems(enriched);
    } catch (err) {
      addToast('Ошибка корзины', err.message, 'error');
    }
  };

  // Load bookings
  const loadBookings = async () => {
    setLoading(true);
    try {
      const data = await fetchBookings(statusFilter);
      setBookings(data);
    } catch (err) {
      addToast('Ошибка заявок', err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  // Initial load
  useEffect(() => {
    loadServices();
  }, []);

  // When active user changes: reload their basket and bookings
  useEffect(() => {
    loadBasket(currentUser.id);
    if (activeTab === 'bookings') {
      loadBookings();
    }
  }, [currentUser.id]);

  // When switching tabs or status filter
  useEffect(() => {
    if (activeTab === 'basket') {
      loadBasket(currentUser.id);
    } else if (activeTab === 'bookings') {
      loadBookings();
    }
  }, [activeTab, statusFilter]);

  // Actions
  const handleAddToBasket = async (payload) => {
    try {
      const newItem = await addToBasket(payload);
      addToast('Корзина', 'Ресурс временно зарезервирован на 1 минуту', 'success');
      // Append directly with local timer to avoid delay
      const tagged = {
        ...newItem,
        _localReceivedAt: Date.now()
      };
      setBasketItems((prev) => [tagged, ...prev]);
    } catch (err) {
      addToast('Внимание', err.message, 'warning');
      throw err;
    }
  };

  const handleCreateDirectBooking = async (payload) => {
    try {
      const created = await createDirectBooking(payload);
      addToast('Успешно', `Заявка #${created.id} оформлена!`, 'success');
      if (activeTab === 'bookings') loadBookings();
    } catch (err) {
      addToast('Внимание', err.message, 'warning');
      throw err;
    }
  };

  const handleCheckout = async (itemId) => {
    try {
      const created = await checkoutBasketItem(itemId);
      addToast('Успешно', `Заявка #${created.id} отправлена на рассмотрение!`, 'success');
      // Remove from cart locally
      setBasketItems((prev) => prev.filter((it) => it.id !== itemId));
    } catch (err) {
      addToast('Внимание', err.message, 'warning');
    }
  };

  const handleDeleteBasketItem = async (itemId) => {
    try {
      await deleteBasketItem(itemId);
      addToast('Удалено', 'Позиция удалена из корзины', 'success');
      setBasketItems((prev) => prev.filter((it) => it.id !== itemId));
    } catch (err) {
      addToast('Ошибка', err.message, 'error');
    }
  };

  const handleUpdateStatus = async (id, newStatus) => {
    try {
      await updateBookingStatus(id, newStatus);
      const label = newStatus === 'APPROVED' ? 'одобрена' : 'отклонена';
      addToast('Статус обновлён', `Заявка #${id} ${label}`, 'success');
      loadBookings();
    } catch (err) {
      addToast('Ошибка', err.message, 'error');
    }
  };

  const handleDeleteBooking = async (id) => {
    try {
      await deleteBooking(id);
      addToast('Удалено', `Заявка #${id} отозвана/удалена`, 'success');
      loadBookings();
    } catch (err) {
      addToast('Ошибка', err.message, 'error');
    }
  };

  return (
    <div>
      <Navbar currentUser={currentUser} setCurrentUser={setCurrentUser} />

      <main className="app-container">
        {/* Navigation Tabs */}
        <div className="tabs-container">
          <div className="nav-tabs">
            <button
              className={`tab-btn ${activeTab === 'catalog' ? 'active' : ''}`}
              onClick={() => setActiveTab('catalog')}
            >
              <BookOpen size={18} />
              Каталог услуг
            </button>

            <button
              className={`tab-btn ${activeTab === 'basket' ? 'active' : ''}`}
              onClick={() => setActiveTab('basket')}
            >
              <ShoppingCart size={18} />
              Корзина
              {basketItems.length > 0 && (
                <span className="tab-badge">{basketItems.length}</span>
              )}
            </button>

            <button
              className={`tab-btn ${activeTab === 'bookings' ? 'active' : ''}`}
              onClick={() => setActiveTab('bookings')}
            >
              {currentUser.role === 'admin' ? (
                <>
                  <Shield size={18} />
                  Панель администратора
                </>
              ) : (
                <>
                  <CalendarCheck size={18} />
                  Мои заявки
                </>
              )}
            </button>
          </div>
        </div>

        {/* Tab contents */}
        {activeTab === 'catalog' && (
          <ServicesCatalog
            services={services}
            loading={loading}
            onSelectService={(service) => {
              setModalService(service);
              setIsModalOpen(true);
            }}
          />
        )}

        {activeTab === 'basket' && (
          <Basket
            items={basketItems}
            services={services}
            onCheckout={handleCheckout}
            onDelete={handleDeleteBasketItem}
          />
        )}

        {activeTab === 'bookings' && (
          <BookingsAdmin
            bookings={bookings}
            currentUser={currentUser}
            statusFilter={statusFilter}
            setStatusFilter={setStatusFilter}
            services={services}
            onUpdateStatus={handleUpdateStatus}
            onDeleteBooking={handleDeleteBooking}
            onRefresh={loadBookings}
            onOpenCreateModal={() => {
              setModalService(services[0] || null);
              setIsModalOpen(true);
            }}
            loading={loading}
          />
        )}
      </main>

      {/* Booking Modal */}
      {isModalOpen && (
        <BookingModal
          service={modalService}
          services={services}
          userId={currentUser.id}
          onClose={() => setIsModalOpen(false)}
          onAddToBasket={handleAddToBasket}
          onCreateDirectBooking={handleCreateDirectBooking}
        />
      )}

      {/* Toast Notifications */}
      <Toast toasts={toasts} onDismiss={removeToast} />
    </div>
  );
}
