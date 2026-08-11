import { useState } from 'react';
import { getToken, setToken } from './api';
import { Dashboard } from './components/Dashboard';
import { LoginPage } from './components/LoginPage';

interface SessionUser {
  username: string;
  name: string;
  role: string;
}

const USER_KEY = 'pixelfactory.user';

function loadUser(): SessionUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as SessionUser;
  } catch {
    return null;
  }
}

export function App() {
  const [user, setUser] = useState<SessionUser | null>(() => (getToken() ? loadUser() : null));

  const handleLogin = (token: string, sessionUser: SessionUser) => {
    setToken(token);
    localStorage.setItem(USER_KEY, JSON.stringify(sessionUser));
    setUser(sessionUser);
  };

  const handleLogout = () => {
    setToken(null);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  };

  if (!user) {
    return <LoginPage onLogin={handleLogin} />;
  }

  return <Dashboard user={user} onLogout={handleLogout} />;
}
