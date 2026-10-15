#!/usr/bin/env node

import { access, constants } from "node:fs/promises"
import dns from "node:dns/promises"
import http from "node:http"
import path from "node:path"
import { fileURLToPath } from "node:url"

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(scriptDir, "..")
const nodeModulesDir = path.join(projectRoot, "node_modules")
const modulesMarker = path.join(nodeModulesDir, ".modules.yaml")
const nextBin = path.join(nodeModulesDir, ".bin", process.platform === "win32" ? "next.cmd" : "next")

const failures = []
const warnings = []

const [nodeMajor, nodeMinor] = process.versions.node.split(".").map((part) => Number.parseInt(part, 10))
if (nodeMajor < 18 || (nodeMajor === 18 && nodeMinor < 19)) {
    failures.push("Node.js 版本过低：前端需要 Node.js 18.19 或更高版本。")
}

try {
    await access(path.join(projectRoot, "pnpm-lock.yaml"), constants.R_OK)
} catch {
    failures.push("找不到 pnpm-lock.yaml，请确认命令是在 ui-react 目录执行。")
}

const hasNodeModules = await canAccess(nodeModulesDir)
const hasModulesMarker = await canAccess(modulesMarker)
const hasNextBinary = await canAccess(nextBin, constants.X_OK)

if (!hasNodeModules || !hasModulesMarker || !hasNextBinary) {
    failures.push(
        "前端依赖没有安装完成（缺少 node_modules/.modules.yaml 或 next 可执行文件）。\n" +
            "请先执行：pnpm install --frozen-lockfile\n" +
            "如果本机已有完整 pnpm 缓存但当前没有网络，可尝试：pnpm install --offline --frozen-lockfile",
    )
}

try {
    await dns.lookup("registry.npmjs.org")
} catch {
    warnings.push("当前无法解析 registry.npmjs.org；缺少依赖时，pnpm install 会因网络问题失败。")
}

const backendHealth = await checkBackendHealth()
if (!backendHealth.ok) {
    warnings.push("后端 http://localhost:8087/actuator/health 当前不可访问；前端可以启动，但岗位数据和写操作不可用。")
}

if (failures.length > 0) {
    console.error("前端启动检查未通过：")
    failures.forEach((message) => console.error(`- ${message}`))
    warnings.forEach((message) => console.error(`提示：${message}`))
    process.exitCode = 1
} else {
    console.log(`前端启动检查通过（Node.js ${process.versions.node}）。`)
    warnings.forEach((message) => console.warn(`提示：${message}`))
}

async function canAccess(target, mode = constants.R_OK) {
    try {
        await access(target, mode)
        return true
    } catch {
        return false
    }
}

function checkBackendHealth() {
    return new Promise((resolve) => {
        const request = http.get("http://127.0.0.1:8087/actuator/health", { timeout: 1200 }, (response) => {
            response.resume()
            resolve({ ok: Boolean(response.statusCode && response.statusCode >= 200 && response.statusCode < 300) })
        })

        request.on("error", () => resolve({ ok: false }))
        request.on("timeout", () => {
            request.destroy()
            resolve({ ok: false })
        })
    })
}
