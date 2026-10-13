"use client";

import { Suspense, useState, useCallback, useEffect } from "react";
import { useLoginModal } from "@/hooks/useLoginModal";
import { Bell, User, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PiLogo } from "@/components/brand/PiLogo";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { useRouter, usePathname, useSearchParams } from "next/navigation";
import { useLoginUser } from "@/hooks/useLoginUser";
import { useToast } from "@/hooks/use-toast";
import { getConfigValue } from "@/lib/config";
import { getWxSseUrl, postWxCallback, GlobalConfigItemValue, getUserDetail, devWxLogin } from "@/lib/api";
import { useSSE } from "@/hooks/useSSE";
import { FloatingChatButton } from "@/components/chat/FloatingChatButton";

const adminDomains = [
  { label: "Agent作业台", href: "/admin/progress", paths: ["/admin/progress"] },
  { label: "采集工作台", href: "/admin/entry", paths: ["/admin/entry", "/admin/drafts", "/admin/jobs"] },
  { label: "采集源管理", href: "/admin/sources", paths: ["/admin/sources"] },
  { label: "平台配置", href: "/admin/llm-providers", paths: ["/admin/llm-providers", "/admin/llm-monitor", "/admin/dict", "/admin/users", "/admin/coupon", "/admin/user-config"] },
];

function AdminDomainNav({ pathname }: { pathname: string | null }) {
  const searchParams = useSearchParams();
  const agentScoped =
    searchParams.get("runnerType") === "agent" ||
    pathname === "/admin/progress" ||
    Boolean(pathname?.startsWith("/admin/progress/"));
  const sourceScoped =
    !agentScoped &&
    Boolean(searchParams.get("sourceId") || searchParams.get("sourceTaskId")) &&
    ["/admin/entry", "/admin/drafts", "/admin/jobs"].some((path) => pathname === path || pathname?.startsWith(`${path}/`));

  return <AdminDomainNavContent pathname={pathname} agentScoped={agentScoped} sourceScoped={sourceScoped} />;
}

function AdminDomainNavFallback({ pathname }: { pathname: string | null }) {
  return <AdminDomainNavContent pathname={pathname} agentScoped={false} sourceScoped={false} />;
}

function AdminDomainNavContent({ pathname, agentScoped, sourceScoped }: { pathname: string | null; agentScoped: boolean; sourceScoped: boolean }) {
  return (
    <nav className="flex min-w-0 items-center gap-1 overflow-x-auto whitespace-nowrap rounded-xl bg-slate-100/70 p-1">
      {adminDomains.map((domain) => {
        const active = agentScoped
          ? domain.href === "/admin/progress"
          : sourceScoped
          ? domain.href === "/admin/sources"
          : domain.paths.some((path) => pathname === path || pathname?.startsWith(`${path}/`));
        return (
          <a
            key={domain.href}
            href={domain.href}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              active
                ? "bg-white text-blue-600 shadow-sm"
                : "text-gray-600 hover:bg-white hover:text-blue-600"
            }`}
          >
            {domain.label}
          </a>
        );
      })}
    </nav>
  );
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { toast } = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const { loginOpen, setLoginOpen } = useLoginModal();
  const [qr, setQr] = useState("");
  const [code, setCode] = useState("");
  const [session, setSession] = useState("");
  const [mounted, setMounted] = useState(false);
  const [localHost, setLocalHost] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [adminLoading, setAdminLoading] = useState(false);
  const { userInfo, setUserInfo, logout } = useLoginUser();
  const [sseUrl, setSseUrl] = useState("");
  const [env, setEnv] = useState<GlobalConfigItemValue[]>([]);

  useEffect(() => {
    setMounted(true);
    setLocalHost(
      ["localhost", "127.0.0.1", "::1"].includes(window.location.hostname)
    );
    getConfigValue("site", "env").then(setEnv);
  }, []);

  const isDevEnv = env.some(
    (item) => String(item.value).toLowerCase() === "dev"
  );
  const localLoginEnabled = mounted && (localHost || isDevEnv);

  useEffect(() => {
    if (loginOpen && !localLoginEnabled) {
      setSseUrl(getWxSseUrl());
    } else {
      setSseUrl("");
    }
  }, [loginOpen, localLoginEnabled]);

  const handleSSE = useCallback(
    (type: string, payload: string) => {
      if (type === "qr") setQr(payload);
      if (type === "init") setCode(payload);
      if (type === "login") {
        setSession(payload);
        setLoginOpen(false);
        if (payload) {
          document.cookie = payload;
          const token = payload.substring(
            payload.indexOf("=") + 1,
            payload.indexOf(";")
          );
          // 解析 jwt 并设置全局用户信息
          const jwt = (() => {
            try {
              const base64Url = token.split(".")[1];
              const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
              const jsonPayload = decodeURIComponent(
                atob(base64)
                  .split("")
                  .map(function (c) {
                    return (
                      "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2)
                    );
                  })
                  .join("")
              );
              return JSON.parse(jsonPayload);
            } catch {
              return null;
            }
          })();
          if (jwt) {
            const info = {
              userId: jwt.uid,
              role: jwt.r,
              nickname: jwt.un,
              avatar: jwt.av,
              timestamp: Date.now(),
            };
            setUserInfo(info);
            if (typeof window !== "undefined") {
              localStorage.setItem("oc-user", JSON.stringify(info));
              localStorage.setItem("oc-token", token);
            }

            // 自动刷新当前页面
            window.location.reload();
          }
        }
      }
    },
    [setUserInfo]
  );

  useSSE(sseUrl, handleSSE);

  const handleWxLogin = async (type: "user" | "admin", code: String) => {
    const content = type === "user" ? "login" : "admin";
    const xml = `<xml><URL><![CDATA[https://jobclaw.local]]></URL><ToUserName><![CDATA[JobClaw]]></ToUserName><FromUserName><![CDATA[demoUser-${content}]]></FromUserName><CreateTime>${Math.floor(
      Date.now() / 1000
    )}</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[${code}]]></Content><MsgId>${Date.now()}</MsgId></xml>`;
    try {
      if (type === "user") setLoginLoading(true);
      else setAdminLoading(true);
      await postWxCallback(xml);
    } catch (err) {
      toast({
        title: "登录提醒",
        description: "登录失败，请刷新页面再重新登录吧",
        variant: "destructive",
      });
    } finally {
      setLoginLoading(false);
      setAdminLoading(false);
    }
  };

  const handleDevLogin = async (type: "user" | "admin") => {
    try {
      if (type === "user") setLoginLoading(true);
      else setAdminLoading(true);

      const data = await devWxLogin(type);
      const info = {
        userId: data.user.userId,
        role: data.user.role,
        nickname: data.user.displayName,
        avatar: data.user.avatar,
        timestamp: Date.now(),
      };
      setUserInfo(info);
      localStorage.setItem("oc-user", JSON.stringify(info));
      localStorage.setItem("oc-token", data.token);
      setLoginOpen(false);
      window.location.reload();
    } catch (err) {
      toast({
        title: "登录提醒",
        description: err instanceof Error ? err.message : "本地登录失败，请检查后端服务和数据库初始化情况",
        variant: "destructive",
      });
    } finally {
      setLoginLoading(false);
      setAdminLoading(false);
    }
  };

  // Periodically check user info validity (5 minutes)
  useEffect(() => {
    const checkUserInfoValidity = async () => {
      if (userInfo && userInfo.timestamp) {
        const now = Date.now();
        const fiveMinutes = 5 * 60 * 1000; // 5分钟有效期
        if (now - userInfo.timestamp > fiveMinutes) {
          try {
            const updatedUser = await getUserDetail();
            if (updatedUser) {
              const info = {
                userId: updatedUser.userId,
                role: updatedUser.role,
                nickname: updatedUser.displayName,
                avatar: updatedUser.avatar,
                timestamp: now,
              };
              setUserInfo(info);
              if (typeof window !== "undefined") {
                localStorage.setItem("oc-user", JSON.stringify(info));
              }
            }
          } catch (error) {
            console.error("Failed to update user info:", error);
          }
        }
      }
    };

    // Check immediately on mount
    checkUserInfoValidity();

    // Then check every 30 seconds
    const interval = setInterval(checkUserInfoValidity, 30000);

    return () => clearInterval(interval);
  }, [userInfo, setUserInfo]);

  return (
    <div className="min-h-screen bg-[#f7f8fa] flex flex-col">
      {/* 顶部导航栏 */}
      <header className="bg-white/95 backdrop-blur-xl border-b border-slate-200/80 fixed top-0 left-0 right-0 z-30">
        <div className="px-4 sm:px-8">
          <div className="flex justify-between items-center h-16">
            <div className="flex min-w-0 items-center gap-4 sm:gap-8">
              <div className="flex shrink-0 items-center">
                {pathname?.startsWith("/admin") ? (
                  <div className="h-16 flex items-center justify-center font-bold text-xl tracking-wide mb-0 select-none">
                    <a
                      href="/"
                      className="inline-flex items-center rounded-md transition-opacity hover:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2"
                      title="返回用户端"
                    >
                      <PiLogo wordmarkClassName="hidden sm:inline whitespace-nowrap" />
                    </a>
                  </div>
                ) : (
                  <a href="/" className="transition-opacity hover:opacity-80">
                    <PiLogo />
                  </a>
                )}
              </div>
              {pathname?.startsWith("/admin") ? (
                <Suspense fallback={<AdminDomainNavFallback pathname={pathname} />}>
                  <AdminDomainNav pathname={pathname} />
                </Suspense>
              ) : (
                <nav className="flex items-center gap-4 text-sm sm:gap-7">
                  <a
                    href="/"
                    className={`${
                      pathname === "/"
                        ? "text-blue-600 font-medium"
                        : "text-gray-700"
                    } hover:text-blue-600`}
                  >
                    校招
                  </a>
                  <a
                    href="/internship"
                    className={`${
                      pathname === "/internship"
                        ? "text-blue-600 font-medium"
                        : "text-gray-700"
                    } hover:text-blue-600`}
                  >
                    实习
                  </a>
                  <a
                    href="/internal"
                    className={`${
                      pathname === "/internal"
                        ? "text-blue-600 font-medium"
                        : "text-gray-700"
                    } hover:text-blue-600`}
                  >
                    内推广场
                  </a>
                </nav>
              )}
            </div>
            <div className="ml-3 flex shrink-0 items-center gap-3">
              {!pathname?.startsWith("/admin") && <span className="hidden text-xs text-slate-400 lg:block">让每一步求职更清晰</span>}
              {userInfo ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <span className="flex items-center cursor-pointer rounded-full border border-slate-200 p-1">
                      <img
                        src={userInfo.avatar}
                        alt="avatar"
                        className="w-8 h-8 rounded-full cursor-pointer"
                        title={userInfo.nickname || `用户${userInfo.userId}`}
                      />
                      <ChevronDown className="w-4 h-4 ml-1 text-gray-500" />
                    </span>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <div className="px-3 py-2">
                      <div className="font-medium">
                        {userInfo.nickname || `用户${userInfo.userId}`}
                      </div>
                      <div className="text-xs text-gray-500">
                        {userInfo.role === 1
                          ? "普通用户"
                          : userInfo.role === 2
                          ? "VIP用户"
                          : userInfo.role === 3
                          ? "管理员"
                          : "未知"}
                      </div>
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={() => {
                        router.push("/user");
                      }}
                    >
                      个人信息
                    </DropdownMenuItem>
                    {userInfo.role === 3 && (
                      <DropdownMenuItem onClick={() => router.push("/admin")}>
                        管理后台
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={logout}>退出</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                mounted && (
                  <Dialog open={loginOpen} onOpenChange={setLoginOpen}>
                    <DialogTrigger asChild>
                      <Button variant="outline" size="sm">
                        <User className="h-4 w-4 mr-1" />
                        登录
                      </Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>{localLoginEnabled ? "本地登录" : "扫码登录"}</DialogTitle>
                      </DialogHeader>
                      {localLoginEnabled ? (
                        <div className="flex flex-col gap-4">
                          <div className="text-gray-600 text-sm">
                            当前为本地开发环境，可直接选择登录身份。
                          </div>
                          <div className="flex gap-4">
                            <Button
                              onClick={() => handleDevLogin("user")}
                              disabled={loginLoading || adminLoading}
                            >
                              {loginLoading ? "登录中..." : "普通用户登录"}
                            </Button>
                            <Button
                              onClick={() => handleDevLogin("admin")}
                              disabled={loginLoading || adminLoading}
                              variant="secondary"
                            >
                              {adminLoading ? "登录中..." : "管理员登录"}
                            </Button>
                          </div>
                        </div>
                      ) : qr ? (
                        <div className="flex flex-col items-center">
                          <div className="text-gray text-sm py-1">
                            关注下方二维码，在对话框中输入验证码，既可以实现自动登录哦~
                          </div>
                          <img
                            className="pt-2"
                            src={qr}
                            alt="登录二维码"
                            width={180}
                            height={180}
                          />
                          <div className="mt-2 text-lg font-bold">
                            验证码：{code}
                          </div>
                          {isDevEnv && !localHost && (
                            <div className="flex gap-4 mt-6">
                              <Button
                                onClick={() => handleWxLogin("user", code)}
                                disabled={loginLoading}
                              >
                                {loginLoading ? "登录中..." : "普通用户登录"}
                              </Button>
                              <Button
                                onClick={() => handleWxLogin("admin", code)}
                                disabled={adminLoading}
                                variant="secondary"
                              >
                                {adminLoading ? "登录中..." : "管理员登录"}
                              </Button>
                            </div>
                          )}
                        </div>
                      ) : (
                        <div>微信登录二维码渲染中，请稍后...</div>
                      )}
                    </DialogContent>
                  </Dialog>
                )
              )}
            </div>
          </div>
        </div>
      </header>

      {/* 正文内容 */}
      <main className="flex-grow pt-16">{children}</main>

      {!pathname?.startsWith("/admin") && (
        <footer className="border-t border-slate-200/70 py-7 mt-auto">
          <div className="px-4 sm:px-8">
            <div className="flex items-center justify-center text-center">
              <p className="text-sm text-gray-500">© {new Date().getFullYear()} JobClaw - 专注于校园招聘信息</p>
            </div>
          </div>
        </footer>
      )}

      {/* 悬浮对话按钮 */}
      <FloatingChatButton />
    </div>
  );
}
