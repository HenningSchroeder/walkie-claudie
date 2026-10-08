#!/usr/bin/env bun
// Prints a QR code the Walkie-Claudie app scans to learn this PC's address and key.
// Usage: bun pair.ts [http://address:port]   (default: Tailscale address if present, else the first LAN address)
import { networkInterfaces } from 'os'
import { readFileSync } from 'fs'
import { join } from 'path'
import QRCode from 'qrcode'
import { isPrivate, STATE_DIR, PORT } from './server.ts'

const addrs = Object.values(networkInterfaces()).flat()
  .filter(a => a && a.family === 'IPv4' && !a.internal && isPrivate(a.address))
  .map(a => a!.address)
  .sort((a, b) => Number(b.startsWith('100.')) - Number(a.startsWith('100.')))
const url = process.argv[2] ?? (addrs[0] ? `http://${addrs[0]}:${PORT}` : undefined)
if (!url) {
  console.error('No private network address found. Pass one: bun pair.ts http://192.168.0.10:7700')
  process.exit(1)
}
const key = readFileSync(join(STATE_DIR, 'key'), 'utf8').trim()

console.log(await QRCode.toString(JSON.stringify({ url, key }), { type: 'terminal', small: true }))
console.log(`Scan with Walkie-Claudie › Scan QR.   ${url}`)
if (addrs.length > 1) console.log(`Other addresses: ${addrs.slice(1).join(', ')}  (bun pair.ts http://<address>:${PORT})`)
