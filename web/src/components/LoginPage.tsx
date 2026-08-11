import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError } from '../api';

interface Props {
  onLogin: (token: string, user: { username: string; name: string; role: string }) => void;
}

export function LoginPage({ onLogin }: Props) {
  const [username, setUsername] = useState('operator');
  const [password, setPassword] = useState('password');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await api.login(username, password);
      onLogin(result.accessToken, {
        username: result.username,
        name: result.name,
        role: result.role,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>PixelFactory OEE</h1>
        <p className="hint">데모 계정: admin / inspector / operator (비밀번호 password)</p>
        <div className="field">
          <label htmlFor="username">아이디</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
        </div>
        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>
        {error && <span className="error-text">{error}</span>}
        <button className="btn primary" type="submit" disabled={busy}>
          {busy ? '로그인 중…' : '로그인'}
        </button>
      </form>
    </div>
  );
}
