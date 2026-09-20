export const users = [
  { id: 1, name: 'Студент 1', role: 'student' },
  { id: 2, name: 'Студент 2', role: 'student' },
  { id: 42, name: 'Преподаватель', role: 'teacher' },
  { id: 999, name: 'Администратор', role: 'admin' }
];

export function getUserLabel(uid) {
  const user = users.find((u) => u.id === uid);
  return user ? user.name : `Пользователь #${uid}`;
}
