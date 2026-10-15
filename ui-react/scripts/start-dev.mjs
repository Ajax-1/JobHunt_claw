#!/usr/bin/env node

import { spawn } from "node:child_process"
import path from "node:path"
import { fileURLToPath } from "node:url"

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(scriptDir, "..")

const doctor = spawn(process.execPath, [path.join(scriptDir, "frontend-doctor.mjs")], {
    cwd: projectRoot,
    stdio: "inherit",
})

doctor.on("error", (error) => {
    console.error(`无法运行前端启动检查：${error.message}`)
    process.exitCode = 1
})

doctor.on("exit", (code, signal) => {
    if (code !== 0) {
        process.exitCode = code ?? 1
        return
    }

    const nextBin = path.join(projectRoot, "node_modules", ".bin", process.platform === "win32" ? "next.cmd" : "next")
    const next = spawn(nextBin, ["dev", ...process.argv.slice(2)], {
        cwd: projectRoot,
        stdio: "inherit",
        env: process.env,
        shell: process.platform === "win32",
    })

    next.on("error", (error) => {
        console.error(`无法启动 Next.js：${error.message}`)
        process.exitCode = 1
    })

    next.on("exit", (nextCode, nextSignal) => {
        if (nextSignal) {
            console.error(`Next.js 被信号 ${nextSignal} 终止。`)
        }
        process.exitCode = nextCode ?? 1
    })
})
