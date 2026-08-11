import { Client } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';
import type { FactoryEvent, OeeSummary } from './types';

function resolveWsUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined;
  if (configured) {
    return configured;
  }
  return `${window.location.origin.replace(/^http/, 'ws')}/ws`;
}

/**
 * /topic/oee(요약 스냅샷), /topic/events(이벤트 스트림) 구독.
 * 반환값은 브로커 연결 상태 — 헤더의 라이브 인디케이터에 쓴다.
 */
export function useRealtime(
  onOeeSummary: (summary: OeeSummary) => void,
  onFactoryEvent: (event: FactoryEvent) => void,
): boolean {
  const [connected, setConnected] = useState(false);

  // Keep latest callbacks without resubscribing on every render.
  const onOeeRef = useRef(onOeeSummary);
  const onEventRef = useRef(onFactoryEvent);
  onOeeRef.current = onOeeSummary;
  onEventRef.current = onFactoryEvent;

  useEffect(() => {
    const client = new Client({
      brokerURL: resolveWsUrl(),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/oee', (message) => {
          onOeeRef.current(JSON.parse(message.body) as OeeSummary);
        });
        client.subscribe('/topic/events', (message) => {
          onEventRef.current(JSON.parse(message.body) as FactoryEvent);
        });
      },
      onWebSocketClose: () => setConnected(false),
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, []);

  return connected;
}
