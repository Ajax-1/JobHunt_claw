import type React from "react"
import type { Metadata } from "next"
import { Inter } from "next/font/google"
import "./globals.css"
import { LoginUserProvider } from "@/hooks/useLoginUser"
import { LoginModalProvider } from "@/hooks/useLoginModal"
import { Toaster } from "@/components/ui/toaster"
import AppLayout from "@/components/layout/AppLayout"

const inter = Inter({ subsets: ["latin"] })

export const metadata: Metadata = {
  title: "JobClaw - 职位招聘平台",
  description: "专业的职位招聘和求职平台",
  generator: 'JobClaw',
  icons: {
    icon: [
      { url: '/favicon.svg', type: 'image/svg+xml' },
      { url: '/favicon.ico' },
    ],
  }
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className={inter.className} suppressHydrationWarning>
        <LoginUserProvider>
          <LoginModalProvider>
            <AppLayout>
              {children}
            </AppLayout>
          </LoginModalProvider>
        </LoginUserProvider>
        <Toaster />
      </body>
    </html>
  )
}
