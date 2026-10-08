#!/usr/bin/env bun
// Claude Code statusLine command. Saves the status JSON (it carries the plan's usage limits)
// where the walkie channel can read it, and prints a short usage line.
// settings.json: "statusLine": {"type": "command", "command": "bun /path/to/plugin/statusline.ts"}
import { mkdirSync, renameSync, writeFileSync } from 'fs'
import { homedir } from 'os'
import { join } from 'path'

const dir = process.env.WALKIE_STATE_DIR
  ?? join(process.env.CLAUDE_CONFIG_DIR ?? join(homedir(), '.claude'), 'channels', 'walkie')
const raw = await Bun.stdin.text()
mkdirSync(dir, { recursive: true, mode: 0o700 })
writeFileSync(join(dir, 'status.json.tmp'), raw)
renameSync(join(dir, 'status.json.tmp'), join(dir, 'status.json'))

const limits = JSON.parse(raw).rate_limits ?? {}
const pct = (l?: { used_percentage: number }) => (l ? `${Math.round(l.used_percentage)}%` : '–')
console.log(`5h ${pct(limits.five_hour)} · 7d ${pct(limits.seven_day)}`)
