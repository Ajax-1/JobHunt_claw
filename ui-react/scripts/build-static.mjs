#!/usr/bin/env node
import { constants } from "node:fs"
import { access, cp, mkdtemp, rm, symlink } from "node:fs/promises"
import os from "node:os"
import path from "node:path"
import { spawn } from "node:child_process"
import { fileURLToPath } from "node:url"

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(scriptDir, "..")
const nodeModulesDir = path.join(projectRoot, "node_modules")
const outputDir = path.join(projectRoot, ".next-build")
const excludedTopLevel = new Set(["node_modules", ".next", ".next-build", "out"])

await access(nodeModulesDir, constants.R_OK)

const workRoot = await mkdtemp(path.join(os.tmpdir(), "jobclaw-ui-build-"))

try {
    await cp(projectRoot, workRoot, {
        recursive: true,
        filter(source) {
            const relativePath = path.relative(projectRoot, source)
            if (!relativePath) {
                return true
            }

            const topLevel = relativePath.split(path.sep)[0]
            return !excludedTopLevel.has(topLevel)
        },
    })

    // AIDEV-NOTE: AI-GENERATED isolated Next build.
    await symlink(nodeModulesDir, path.join(workRoot, "node_modules"), "dir")

    await runNextBuild(workRoot)

    await rm(outputDir, { recursive: true, force: true })
    await cp(path.join(workRoot, ".next-build"), outputDir, { recursive: true })
} finally {
    await rm(workRoot, { recursive: true, force: true })
}

function runNextBuild(cwd) {
    const nextBin = path.join(cwd, "node_modules", ".bin", "next")

    return new Promise((resolve, reject) => {
        const child = spawn(nextBin, ["build"], {
            cwd,
            stdio: "inherit",
            env: {
                ...process.env,
                NEXT_DIST_DIR: ".next-build",
            },
        })

        child.on("error", reject)
        child.on("exit", (code, signal) => {
            if (code === 0) {
                resolve()
                return
            }

            reject(new Error(`next build failed with ${signal || code}`))
        })
    })
}
