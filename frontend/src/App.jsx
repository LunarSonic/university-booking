import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { ServicesCatalog } from './components/ServicesCatalog';
import { Basket } from './components/Basket';
import { BookingsAdmin } from './components/BookingsAdmin';
import { BookingModal } from './components/BookingModal';
import { Toast } from './components/Toast';
import { EventsJournal } from './components/EventsJournal';
import {
  fetchServices,
  fetchBasket,
  addToBasket,
  deleteBasketItem,
  checkoutBasketItem,
  searchBookings,
  updateBookingStatus,
  deleteBooking,
  createDirectBooking
} from './services/api';
import { BookOpen, ShoppingCart, CalendarCheck, Shield, ScrollText } from 'lucide-react';

export default function App() {
  const [currentUser, setCurrentUser] = useState({
    id: 1,
    name: 'Студент 1',
    role: 'student'
  });

  const [activeTab, setActiveTab] = useState('catalog');

  // Data states
  const [services, setServices] = useState([]);
  const [basketItems, setBasketItems] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [statusFilter, setStatusFilter] = useState('NEW');
  const [direction, setDirection] = useState('DESC');
  const [needsProjector, setNeedsProjector] = useState(null);
  const [nextCursor, setNextCursor] = useState(null);
  const [page, setPage] = useState(1);
  const [cursorStack, setCursorStack] = useState([null]); // cursorStack[i] — курсор для страницы i+1

  // Separate loading states
  const [servicesLoading, setServicesLoading] = useState(false);
  const [bookingsLoading, setBookingsLoading] = useState(false);

  // UI states
  const [modalService, setModalService] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [toasts, setToasts] = useState([]);
  const toastTimersRef = useRef(new Map());

  // Toast helper with cleanup
  const addToast = useCallback((title, message, type = 'success') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, title, message, type }]);
    const timer = setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
      toastTimersRef.current.delete(id);
    }, 4500);
    toastTimersRef.current.set(id, timer);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    const timer = toastTimersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      toastTimersRef.current.delete(id);
    }
  }, []);

  // Cleanup all toast timers on unmount
  useEffect(() => {
    const timers = toastTimersRef.current;
    return () => {
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
    };
  }, []);

  // Load services once
  const loadServices = async () => {
    setServicesLoading(true);
    try {
      const data = await fetchServices();
      setServices(data);
    } catch (err) {
      addToast('Ошибка', err.message, 'error');
    } finally {
      setServicesLoading(false);
    }
  };

  // Load basket for current user
  const loadBasket = async (uid = currentUser.id) => {
    try {
      const items = await fetchBasket(uid);
      setBasketItems((prev) => {
        const existingTimestamps = new Map(prev.map((it) => [it.id, it._localReceivedAt]));
        return items.map((it) => ({
          ...it,
          _localReceivedAt: existingTimestamps.get(it.id) || Date.now()
        }));
      });
    } catch (err) {
      addToast('Ошибка корзины', err.message, 'error');
    }
  };

  // Load bookings with AbortController support
  const loadBookingsWithSignal = useCallback(async (status, userId, dir, projector, cursor, signal) => {
    setBookingsLoading(true);
    try {
      const data = await searchBookings({ status, userId, direction: dir, needsProjector: projector, cursor, signal });
      setBookings(data.items);
      setNextCursor(data.nextCursor || null);
    } catch (err) {
      if (err.name !== 'AbortError') {
        addToast('Ошибка заявок', err.message, 'error');
      }
    } finally {
      if (!signal || !signal.aborted) {
        setBookingsLoading(false);
      }
    }
  }, [addToast]);

  // Convenience wrapper for manual refresh (no abort needed)
  const loadBookings = useCallback(() => {
    const userId = currentUser.role !== 'admin' ? currentUser.id : null;
    setPage(1);
    setNextCursor(null);
    setCursorStack([null]);
    loadBookingsWithSignal(statusFilter, userId, direction, needsProjector, null);
  }, [currentUser, statusFilter, direction, needsProjector, loadBookingsWithSignal]);

  const handleNextPage = useCallback(() => {
    if (!nextCursor) return;
    const userId = currentUser.role !== 'admin' ? currentUser.id : null;
    setCursorStack((prev) => [...prev, nextCursor]);
    setPage((p) => p + 1);
    loadBookingsWithSignal(statusFilter, userId, direction, needsProjector, nextCursor);
  }, [nextCursor, currentUser, statusFilter, direction, needsProjector, loadBookingsWithSignal]);

  const handlePrevPage = useCallback(() => {
    if (page <= 1) return;
    const userId = currentUser.role !== 'admin' ? currentUser.id : null;
    const prevCursor = cursorStack[page - 2]; // курсор для предыдущей страницы
    setCursorStack((prev) => prev.slice(0, -1));
    setPage((p) => p - 1);
    loadBookingsWithSignal(statusFilter, userId, direction, needsProjector, prevCursor);
  }, [page, cursorStack, currentUser, statusFilter, direction, needsProjector, loadBookingsWithSignal]);

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

  // When switching tabs, status filter or direction — with AbortController
  useEffect(() => {
    if (activeTab === 'basket') {
      loadBasket(currentUser.id);
    } else if (activeTab === 'bookings') {
      const controller = new AbortController();
      const userId = currentUser.role !== 'admin' ? currentUser.id : null;
      setPage(1);
      setNextCursor(null);
      setCursorStack([null]);
      loadBookingsWithSignal(statusFilter, userId, direction, needsProjector, null, controller.signal);
      return () => controller.abort();
    }
  }, [activeTab, statusFilter, direction, needsProjector]);

  // Actions
  const handleAddToBasket = async (payload) => {
    try {
      const newItem = await addToBasket(payload);
      addToast('Корзина', 'Ресурс временно зарезервирован на 1 минуту', 'success');
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

            {currentUser.role === 'admin' && (
              <button
                className={`tab-btn ${activeTab === 'events' ? 'active' : ''}`}
                onClick={() => setActiveTab('events')}
              >
                <ScrollText size={18} />
                Журнал событий
              </button>
            )}
          </div>
        </div>

        {/* Tab contents */}
        {activeTab === 'catalog' && (
          <ServicesCatalog
            services={services}
            loading={servicesLoading}
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
            direction={direction}
            setDirection={setDirection}
            needsProjector={needsProjector}
            setNeedsProjector={setNeedsProjector}
            page={page}
            nextCursor={nextCursor}
            onNextPage={handleNextPage}
            onPrevPage={handlePrevPage}
            services={services}
            onUpdateStatus={handleUpdateStatus}
            onDeleteBooking={handleDeleteBooking}
            onOpenCreateModal={() => {
              setModalService(services[0] || null);
              setIsModalOpen(true);
            }}
            loading={bookingsLoading}
          />
        )}

        {activeTab === 'events' && currentUser.role === 'admin' && (
          <EventsJournal />
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
