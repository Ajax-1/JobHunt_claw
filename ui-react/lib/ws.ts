import { Client, IMessage, StompSubscription } from "@stomp/stompjs";

const BASE_URL =
  process.env.NODE_ENV === "production"
    ? `${typeof window !== "undefined" ? window.location.protocol : "http:"}//${typeof window !== "undefined" ? window.location.host : "localhost"}`
    : "http://localhost:8087";

export interface WsChatMessage {
  msgId: string;
  message: string;
  files?: string[];
  medias?: string[];
}

export interface WsResponseMessage {
  msgId: string;
  type: "text" | "stream" | "stream_done" | "error";
  content?: string;
  thinking?: string;
  tool?: string;
  toolResult?: string;
}

export interface WsFileUploadResponse {
  code: number;
  msg: string;
  data?: {
    fileId: string;
    fileName: string;
    mimeType: string;
    size: number;
  };
}

type MessageHandler = (msg: WsResponseMessage) => void;
export type ConnectionState = "disconnected" | "connecting" | "connected" | "reconnecting" | "auth-error";
type ConnectionHandler = (state: ConnectionState) => void;

class WebSocketClient {
  private client: Client | null = null;
  private messageSubscription: StompSubscription | null = null;
  private streamSubscription: StompSubscription | null = null;
  private doneSubscription: StompSubscription | null = null;
  private errorSubscription: StompSubscription | null = null;
  private messageHandler: MessageHandler | null = null;
  private connectionHandler: ConnectionHandler | null = null;
  private userId: string | null = null;

  connect(token: string, userId: string) {
    if (this.client?.active && this.userId === userId) {
      this.connectionHandler?.(this.client.connected ? "connected" : "connecting");
      return;
    }
    this.disconnect();
    this.userId = userId;
    this.connectionHandler?.("connecting");
    // AI-GENERATED AIDEV-NOTE: 防止旧连接回调覆盖新会话，HTTPS 页面必须使用 WSS。
    const url = new URL(BASE_URL);
    let authenticationFailed = false;
    const client = new Client({
      brokerURL: `${url.protocol === "https:" ? "wss:" : "ws:"}//${url.host}/ws`,
      connectHeaders: {
        "X-OC-TOKEN": token,
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      connectionTimeout: 10000,
      onConnect: () => {
        if (this.client !== client) return;
        console.log("[WS] Connected");
        this.subscribeToTopics();
        this.connectionHandler?.("connected");
      },
      onDisconnect: () => {
        if (this.client === client) this.connectionHandler?.("disconnected");
      },
      onWebSocketClose: () => {
        if (this.client === client && !authenticationFailed) this.connectionHandler?.("reconnecting");
      },
      onStompError: (frame) => {
        if (this.client !== client) return;
        console.error("[WS] STOMP error:", frame.headers["message"]);
        authenticationFailed = true;
        this.connectionHandler?.("auth-error");
        void client.deactivate();
      },
    });
    this.client = client;
    client.activate();
  }

  disconnect() {
    this.unsubscribe();
    const client = this.client;
    this.client = null;
    this.userId = null;
    void client?.deactivate();
    this.connectionHandler?.("disconnected");
  }

  private subscribeToTopics() {
    if (!this.client?.connected || !this.userId) return;

    // 订阅普通响应
    this.messageSubscription = this.client.subscribe(
      "/user/queue/rsp",
      (message: IMessage) => {
        try {
          const msg: WsResponseMessage = JSON.parse(message.body);
          this.messageHandler?.(msg);
        } catch (e) {
          console.error("[WS] Failed to parse message:", e);
        }
      }
    );

    // 订阅流式响应
    this.streamSubscription = this.client.subscribe(
      "/user/queue/rsp/stream",
      (message: IMessage) => {
        try {
          const msg: WsResponseMessage = JSON.parse(message.body);
          this.messageHandler?.(msg);
        } catch (e) {
          console.error("[WS] Failed to parse stream:", e);
        }
      }
    );

    // 订阅流式完成事件
    this.doneSubscription = this.client.subscribe(
      "/user/queue/rsp/done",
      (message: IMessage) => {
        try {
          const msg: WsResponseMessage = JSON.parse(message.body);
          this.messageHandler?.(msg);
        } catch (e) {
          console.error("[WS] Failed to parse done:", e);
        }
      }
    );

    // 订阅错误事件
    this.errorSubscription = this.client.subscribe(
      "/user/queue/rsp/error",
      (message: IMessage) => {
        try {
          const msg: WsResponseMessage = JSON.parse(message.body);
          this.messageHandler?.(msg);
        } catch (e) {
          console.error("[WS] Failed to parse error:", e);
        }
      }
    );
  }

  private unsubscribe() {
    this.messageSubscription?.unsubscribe();
    this.streamSubscription?.unsubscribe();
    this.doneSubscription?.unsubscribe();
    this.errorSubscription?.unsubscribe();
  }

  sendMessage(msg: WsChatMessage) {
    if (!this.client?.connected) {
      throw new Error("连接已断开，请等待重新连接后再发送。");
    }
    this.client.publish({
      destination: "/app/chat.send",
      body: JSON.stringify(msg),
    });
  }

  onMessage(handler: MessageHandler) {
    this.messageHandler = handler;
  }

  onConnectionChange(handler: ConnectionHandler) {
    this.connectionHandler = handler;
  }

  get connected(): boolean {
    return this.client?.connected ?? false;
  }
}

// 上传文件
export async function uploadFile(
  file: File,
  userId: string
): Promise<WsFileUploadResponse["data"]> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("userId", userId);

  const token = localStorage.getItem("oc-token");
  const headers: Record<string, string> = {};
  if (token) {
    headers["X-OC-TOKEN"] = token;
  }

  const response = await fetch(`${BASE_URL}/api/ws/upload`, {
    method: "POST",
    headers,
    body: formData,
  });

  const result: WsFileUploadResponse = await response.json();
  if (result.code === 0 && result.data) {
    return result.data;
  }
  throw new Error(result.msg || "文件上传失败");
}

// 单例
export const wsClient = new WebSocketClient();
