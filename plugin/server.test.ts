// End-to-end check: spawns server.ts as Claude Code would, drives it over MCP and HTTP. Run: bun test
import { test, expect } from 'bun:test'
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { mkdtempSync, readFileSync } from 'fs'
import { tmpdir } from 'os'
import { join } from 'path'
import { isPrivate } from './server.ts'

test('isPrivate', () => {
  for (const ip of ['192.168.0.5', '10.1.2.3', '172.20.0.1', '100.101.102.103', '127.0.0.1', '::1', '::ffff:192.168.1.2', 'fd7a:115c:a1e0::1'])
    expect(isPrivate(ip)).toBe(true)
  for (const ip of ['8.8.8.8', '172.32.0.1', '100.128.0.1', '2001:db8::1', ''])
    expect(isPrivate(ip)).toBe(false)
})

test('say → channel notification, reply → poll', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'walkie-'))
  const port = 17700 + Math.floor(Math.random() * 1000)
  const client = new Client({ name: 'test', version: '0' })
  const inbound: any[] = []
  client.fallbackNotificationHandler = async n => { inbound.push(n) }
  await client.connect(new StdioClientTransport({
    command: 'bun', args: [join(import.meta.dir, 'server.ts')],
    env: { ...process.env, WALKIE_STATE_DIR: dir, WALKIE_PORT: String(port) } as Record<string, string>,
  }))
  const base = `http://127.0.0.1:${port}`
  const auth = { authorization: `Bearer ${readFileSync(join(dir, 'key'), 'utf8').trim()}` }
  try {
    expect((await fetch(`${base}/poll`, { headers: { authorization: 'Bearer nope' } })).status).toBe(401)

    const say = () => fetch(`${base}/say`, { method: 'POST', headers: auth, body: JSON.stringify({ id: 'm1', text: '안녕' }) })
    expect((await say()).status).toBe(200)
    expect((await say()).status).toBe(200) // retry: acked, not re-delivered
    await Bun.sleep(100)
    expect(inbound.length).toBe(1)
    expect(inbound[0].method).toBe('notifications/claude/channel')
    expect(inbound[0].params).toMatchObject({ content: '안녕', meta: { msg_id: 'm1' } })

    const pending = fetch(`${base}/poll?after=0`, { headers: auth }).then(r => r.json())
    await Bun.sleep(100)
    await client.callTool({ name: 'set_issues', arguments: { items: ['A 승인 대기'] } })
    expect((await pending).issues).toEqual(['A 승인 대기']) // long-poll woke on issues change

    await client.callTool({ name: 'reply', arguments: { text: '네, 받았어요', reply_to: 'm1' } })
    const got = await (await fetch(`${base}/poll?after=0`, { headers: auth })).json()
    expect(got.replies).toHaveLength(1)
    expect(got.replies[0]).toMatchObject({ text: '네, 받았어요', reply_to: 'm1' })
    const again = await (await fetch(`${base}/poll?after=${got.replies[0].seq}`, { headers: auth, signal: AbortSignal.timeout(300) }).catch(() => null))
    expect(again).toBeNull() // nothing new → still waiting
  } finally {
    await client.close()
  }
}, 15_000)
