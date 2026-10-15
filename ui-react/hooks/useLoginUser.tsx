"use client"

import React, { createContext, useContext, useEffect, useState } from "react";
import { getUserDetail, execLogout } from "@/lib/api";

interface LoginUserInfo {
    userId: number;
    role: number;
    nickname?: string;
    avatar?: string;
    timestamp?: number;
}

function parseJwt(token: string) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch {
        return null;
    }
}
function isJwtValid(token: string): boolean {
    try {
        const payload = parseJwt(token);
        if (!payload) return false;
        if (payload.exp && typeof payload.exp === 'number') {
            return payload.exp > Math.floor(Date.now() / 1000);
        }
        return true;
    } catch {
        return false;
    }
}
function getCookie(name: string): string | null {
    if (typeof document === 'undefined') return null;
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
    return match ? decodeURIComponent(match[2]) : null;
}

const LoginUserContext = createContext<{
    userInfo: LoginUserInfo | null,
    setUserInfo: (u: LoginUserInfo | null) => void,
    logout: () => void,
    /** 首次恢复浏览器会话前，不渲染未登录门槛。 */
    authReady: boolean,
}>({ userInfo: null, setUserInfo: () => { }, logout: () => { }, authReady: false });

export function useLoginUser() {
    return useContext(LoginUserContext);
}

export function LoginUserProvider({ children }: { children: React.ReactNode }) {
    const [userInfo, setUserInfo] = useState<LoginUserInfo | null>(null);
    const [authReady, setAuthReady] = useState(false);

    useEffect(() => {
        let cancelled = false;

        const refreshUserDetail = async () => {
            try {
                const data = await getUserDetail();
                if (data && !cancelled) {
                    const updatedInfo = {
                        userId: data.userId,
                        role: data.role,
                        nickname: data.displayName,
                        avatar: data.avatar,
                        timestamp: Date.now()
                    };
                    setUserInfo(updatedInfo);
                    if (typeof window !== 'undefined') {
                        localStorage.setItem('oc-user', JSON.stringify(updatedInfo));
                    }
                }
            } catch (error) {
                // 网络暂时不可用时保留本地会话；明确认证失败才清理它。
                if (error instanceof Error && /未登录|登录态已失效|登录已过期/.test(error.message)) {
                    setUserInfo(null);
                    localStorage.removeItem('oc-user');
                    localStorage.removeItem('oc-token');
                    document.cookie = 'oc-session=;path=/;expires=Thu, 01 Jan 1970 00:00:00 GMT';
                }
            }
        };

        const hydrate = async () => {
            if (typeof window === 'undefined') {
                if (!cancelled) setAuthReady(true);
                return;
            }

            let hasLocalSession = false;
            const localUser = localStorage.getItem('oc-user');
            const token = localStorage.getItem('oc-token');
            if (localUser && token && isJwtValid(token)) {
                try {
                    const cachedUser = JSON.parse(localUser) as LoginUserInfo;
                    setUserInfo(cachedUser);
                    hasLocalSession = true;
                    // 会话恢复后校验一次；后端暂时不可用时不能把首屏卡在登录状态。
                    await Promise.race([
                        refreshUserDetail(),
                        new Promise<void>((resolve) => setTimeout(resolve, 3000)),
                    ]);
                } catch {
                    localStorage.removeItem('oc-user');
                    localStorage.removeItem('oc-token');
                }
            } else {
                localStorage.removeItem('oc-user');
                localStorage.removeItem('oc-token');
            }

            if (!hasLocalSession) {
                const cookieToken = getCookie('oc-session');
                if (cookieToken && isJwtValid(cookieToken)) {
                    const jwt = parseJwt(cookieToken);
                    if (jwt) {
                        const info = {
                            userId: jwt.uid,
                            role: jwt.r,
                            nickname: jwt.un,
                            avatar: jwt.av,
                            timestamp: Date.now()
                        };
                        setUserInfo(info);
                        localStorage.setItem('oc-user', JSON.stringify(info));
                        localStorage.setItem('oc-token', cookieToken);
                    }
                }
            }

            if (!cancelled) setAuthReady(true);
        };

        void hydrate();
        return () => { cancelled = true; };
    }, []);

    const logout = async () => {
        // 没有令牌时无需请求后端；失效令牌返回“未登录”也视为退出完成。
        const token = typeof window !== 'undefined' ? localStorage.getItem('oc-token') : null;
        if (token) {
            try {
                await execLogout();
            } catch (error) {
                if (!(error instanceof Error && /未登录|登录态已失效|登录已过期/.test(error.message))) {
                    console.warn('退出登录请求失败，将继续清理本地会话', error);
                }
            }
        }

        document.cookie = 'oc-session=;path=/;expires=Thu, 01 Jan 1970 00:00:00 GMT';
        setUserInfo(null);
        if (typeof window !== 'undefined') {
            localStorage.removeItem('oc-user');
            localStorage.removeItem('oc-token');
        }
        // 自动刷新当前页面
        window.location.reload();

    };

    return (
        <LoginUserContext.Provider value={{ userInfo, setUserInfo, logout, authReady }}>
            {children}
        </LoginUserContext.Provider>
    );
}
