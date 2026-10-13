"use client"

import React, { createContext, useContext, useState, useCallback } from "react";

const LoginModalContext = createContext<{
  loginOpen: boolean;
  setLoginOpen: (open: boolean) => void;
} | undefined>(undefined);

export function useLoginModal() {
  const context = useContext(LoginModalContext);
  if (context === undefined) {
    throw new Error('useLoginModal must be used within a LoginModalProvider');
  }
  return context;
}

export function LoginModalProvider({ children }: { children: React.ReactNode }) {
  const [loginOpen, setLoginOpen] = useState(false);

  const toggleLoginModal = useCallback((open: boolean) => {
    setLoginOpen(open);
  }, []);

  return (
    <LoginModalContext.Provider value={{ loginOpen, setLoginOpen: toggleLoginModal }}>
      {children}
    </LoginModalContext.Provider>
  );
}