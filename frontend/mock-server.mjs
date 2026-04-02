/**
 * mock-server.mjs
 *
 * Usage:
 *   node mock-server.mjs
 *
 */

import http from 'node:http'
import { randomUUID } from 'node:crypto'

const PORT = 8080

// ─── Mock knowledge base ─────────────────────────────────────────────────────

const FAQ = {
  wifi: {
    text: '以下是您的**WiFi登录信息**：',
    meta: {
      infoGrid: [
        { label: '网络名称',  value: 'Wifi_Guest' },
        { label: '密码',      value: 'Welcome2026!' },
        { label: '网速',      value: '500 Mbps' },
        { label: '覆盖范围',   value: '全区域覆盖' },
      ],
      chips: ['早餐时间？', '退房时间？', '客房送餐时间？'],
    },
  },
  breakfast: {
    text: '早餐在**花园露台**供应：',
    meta: {
      infoGrid: [
        { label: '工作日',    value: '07:00 – 10:30' },
        { label: '周末',      value: '07:30 – 11:30' },
        { label: '位置',      value: '二楼花园露台' },
        { label: '类型',      value: '自助餐' },
      ],
      chips: ['WiFi密码？', '泳池开放时间？', '健身房时间？'],
    },
  },
  checkout: {
    text: '您的退房信息：',
    meta: {
      infoGrid: [
        { label: '标准退房',   value: '中午12:00' },
        { label: '延迟退房',   value: '最晚14:00' },
        { label: '延迟费用',   value: '800元 / 2小时' },
        { label: '申请方式',   value: '前台 · App' },
      ],
      chips: ['申请延迟退房', '行李寄存？', '机场接送？'],
    },
  },
  facilities: {
    text: '酒店**设施概览**：',
    meta: {
      infoGrid: [
        { label: '屋顶泳池',   value: '06:00 – 22:00' },
        { label: '健身房',     value: '24小时 · 三楼' },
        { label: '水疗中心',   value: '09:00 – 21:00' },
        { label: '商务中心',   value: '24小时 · 二楼' },
      ],
      chips: ['预约水疗', '泳池政策？', '健身器材？'],
    },
  },

  restaurant: {
    text: '我找到了**3家很棒的浪漫晚餐选择**，都在10分钟路程内：',
    traceSteps: [
      '意图识别：浪漫餐厅',
      'Qdrant 语义搜索 · top-k=5',
      '筛选：氛围=浪漫，评分≥4.3',
      'PostgreSQL：营业时间及空位',
      '按匹配度重新排序',
    ],
    meta: {
      agentTrace: [],
      cards: [
        { emoji: '🕯️', name: '花园餐厅',  type: '法式 · 高级料理',     rating: '★ 4.8', hours: '18:00–23:00', tag: 'date',  tagText: '约会首选',   distance: '0.3 公里' },
        { emoji: '🐟', name: '日叶餐厅',   type: '日式 · 怀石料理',     rating: '★ 4.7', hours: '17:30–22:00', tag: 'date',  tagText: '需预约',     distance: '0.6 公里' },
        { emoji: '🌿', name: '大地餐厅',   type: '现代地中海',          rating: '★ 4.6', hours: '18:00–22:30', tag: 'open',  tagText: '欢迎散客',   distance: '0.9 公里' },
      ],
      chips: ['预订花园餐厅', '有素食选项吗？', '有着装要求吗？'],
    },
  },

  attractions: {
    text: '为您推荐**附近热门景点**：',
    traceSteps: [
      'RAG 检索 · Qdrant 搜索',
      '筛选：距离<2公里，评分≥4.0',
      '从 PostgreSQL 补充元数据',
      '按相关度重新排序',
    ],
    meta: {
      agentTrace: [],
      cards: [
        { emoji: '🏛️', name: '国立博物馆', type: '文化 · 0.4公里',  rating: '★ 4.9', hours: '09:00–17:00', tag: 'open',  tagText: '今日开放',   distance: '0.4 公里' },
        { emoji: '🌳', name: '河滨公园',   type: '自然 · 0.8公里',   rating: '★ 4.5', hours: '全天开放',    tag: 'open',  tagText: '免费入场',   distance: '0.8 公里' },
        { emoji: '🛍️', name: '古物市场',   type: '购物 · 1.2公里',   rating: '★ 4.3', hours: '10:00–21:00', tag: 'open',  tagText: '手工艺品',   distance: '1.2 公里' },
      ],
      chips: ['去博物馆的路线', '门票价格？', '有夜市吗？'],
    },
  },
}

// ─── Intent detection ────────────────────────────────────────────────────────

function detectIntent(msg) {
  const m = msg.toLowerCase()
  // WiFi / 网络
  if (m.includes('wifi') || m.includes('密码') || m.includes('网络') || 
      m.includes('联网') || m.includes('无线') || m.includes('上网')) 
    return 'wifi'
  
  // 早餐 / 餐饮
  if (m.includes('早餐') || m.includes('早饭') || m.includes('午餐') || 
      m.includes('晚饭') || m.includes('吃饭') || m.includes('早晨')) 
    return 'breakfast'
  
  // 退房
  if (m.includes('退房') || m.includes('checkout') || m.includes('check out') || 
      m.includes('离店') || m.includes('延迟退房')) 
    return 'checkout'
  
  // 餐厅 / 美食
  if (m.includes('餐厅') || m.includes('浪漫') || m.includes('美食') || 
      m.includes('晚餐') || m.includes('推荐餐厅') || m.includes('哪里吃')) 
    return 'restaurant'
  
  // 设施 / 服务
  if (m.includes('设施') || m.includes('泳池') || m.includes('游泳池') || 
      m.includes('健身房') || m.includes('健身') || m.includes('spa') || 
      m.includes('水疗') || m.includes('商务中心')) 
    return 'facilities'
  
  // 景点 / 游玩
  if (m.includes('景点') || m.includes('附近') || m.includes('游玩') || 
      m.includes('博物馆') || m.includes('公园') || m.includes('逛街') || 
      m.includes('购物') || m.includes('好玩') || m.includes('哪里去')) 
    return 'attractions'
  return null
}

// ─── SSE helpers ─────────────────────────────────────────────────────────────
function sendChunk(res, chunk) {
  res.write(`data: ${JSON.stringify(chunk)}\n\n`)
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms))
}

async function streamText(res, text) {
  const words = text.split(' ')
  for (const word of words) {
    sendChunk(res, { type: 'text_delta', delta: word + ' ' })
    await sleep(28 + Math.random() * 30)
  }
}

async function streamAgentTrace(res, meta, steps) {
  const trace = steps.map(label => ({ label, status: 'pending' }))
  sendChunk(res, { type: 'meta', meta: { ...meta, agentTrace: trace } })
  await sleep(200)

  for (let i = 0; i < trace.length; i++) {
    trace[i].status = 'running'
    sendChunk(res, { type: 'meta', meta: { ...meta, agentTrace: [...trace] } })
    await sleep(350 + Math.random() * 200)
    trace[i].status = 'done'
    sendChunk(res, { type: 'meta', meta: { ...meta, agentTrace: [...trace] } })
    await sleep(80)
  }
}

// ─── Fallback LLM response ───────────────────────────────────────────────────

const FALLBACK = `我很乐意为您提供帮助！我可以协助您了解以下信息：
- **无线网络和互联网接入**
- **早餐、午餐和晚餐**的供应时间和菜单
- **退房**时间和延迟退房申请
- **酒店设施**（游泳池、健身房、水疗中心、商务中心）
- **餐厅推荐**——浪漫餐厅、家庭餐厅或休闲餐厅
- **附近景点**和游玩项目
您想了解什么？`

// ─── Request handler ─────────────────────────────────────────────────────────

async function handleChat(req, res) {
  // Parse body
  let body = ''
  for await (const chunk of req) body += chunk
  const { message = '' } = JSON.parse(body)

  // SSE headers
  res.writeHead(200, {
    'Content-Type':  'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection':    'keep-alive',
    'Access-Control-Allow-Origin': '*',
  })

  const intent = detectIntent(message)
  const data   = intent ? FAQ[intent] : null

  if (!data) {
    await streamText(res, FALLBACK)
    sendChunk(res, { type: 'done' })
    res.end()
    return
  }

  // Stream text first
  await streamText(res, data.text)

  // If has agent trace, animate it
  if (data.traceSteps) {
    await streamAgentTrace(res, data.meta, data.traceSteps)
  } else {
    // Plain meta (info grids / chips)
    await sleep(150)
    sendChunk(res, { type: 'meta', meta: data.meta })
  }

  sendChunk(res, { type: 'done' })
  res.end()
}

// ─── HTTP server ─────────────────────────────────────────────────────────────

const server = http.createServer(async (req, res) => {
  // CORS pre-flight
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return }

  if (req.method === 'GET' && req.url === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ status: 'ok', ts: new Date().toISOString() }))
    return
  }

  // ─── Mock hotels ───────────────────────────────────────────────────────────
  const DEMO_HOTELS = [
    { name: '智宿酒店', code: 'NEXSTAY', room: '905',  color: '#6C8EF5', logo: '✦' },
    { name: '樱花旅馆', code: 'SAKURA',  room: '308',  color: '#D4849A', logo: '∞' },
  ]

  if (req.method === 'POST' && req.url === '/api/session/init') {
    let body = ''
    req.on('data', chunk => { body += chunk.toString() })
    req.on('end', () => {
      try {
        const { hotel_code, room_number, guest_name } = JSON.parse(body)
        const hotel = DEMO_HOTELS.find(h => h.code === hotel_code?.toUpperCase())

        if (!hotel) {
          res.writeHead(404, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify({ error: 'Hotel code not found' }))
          return
        }

        if (room_number !== hotel.room) {
          res.writeHead(401, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify({ error: 'Room number not found' }))
          return
        }

        if (!guest_name || guest_name.trim() === '') {
          res.writeHead(400, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify({ error: 'Invalid guest name' }))
          return
        }

        // 验证成功，返回会话信息
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({
          session_id: `sess_${Date.now()}_${Math.random().toString(36).substr(2, 8)}`,
          hotel: {
            hotel_id: hotel.code.toLowerCase(),
            logo: hotel.logo,
            name: hotel.name,
            tagline: 'Demo Hotel for testing',
            location: 'Shanghai, China',
            locale: 'zh-CN',
            theme: {
              accent: hotel.color,
              accent_light: '#D4B483',
              accent_dim: '#7A6038',
            },
            quick_actions: [
              { label: 'Wi-Fi密码', query: 'Wi-Fi密码是多少？' },
              { label: '早餐时间', query: '早餐几点开始？' },
              { label: '退房时间', query: '退房时间是几点？' }
            ],
            welcome_chips: ['推荐餐厅', '附近景点', '酒店设施']
          }
        }))

      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ error: 'Invalid request body' }))
      }
    })
    return
  }

  if (req.method === 'POST' && req.url === '/api/chat/stream') {
    try { 
      await handleChat(req, res) 
    } catch (err) {
        console.error('Stream error:', err)
        if (!res.headersSent) { 
          res.writeHead(500); res.end() 
        }
    }
    return
  }



  res.writeHead(404)
  res.end('Not found')
})

server.listen(PORT, () => {
  console.log(`\n🏨  Hotel AI mock server running at http://localhost:${PORT}`)
  console.log(`   POST /api/chat/stream  — SSE chat stream`)
  console.log(`   GET  /api/health       — health check\n`)
})
