/**
 * mock-server.mjs  — Multi-hotel mock backend
 *
 * Endpoints:
 *   POST /api/session/init   — validate hotel_code + room_number, return HotelConfig
 *   POST /api/chat/stream    — SSE chat (hotel-aware responses)
 *   GET  /api/health
 *
 * Demo codes:  NEXSTAY · SAKURAINN · MTNOCEAN
 *
 * Usage:  node mock-server.mjs
 */

import http from 'node:http'
import { randomUUID } from 'node:crypto'

const PORT = 8080


const HOTELS = {
  NEXSTAY: {
    hotel_id: 'nexstay',
    name: '智宿酒店',
    tagline: 'AI智能助手 · 随时为您服务',
    location: '中国·广州',
    locale: 'zh-CN',
    theme: { accent: '#B8965A', accent_light: '#D4B483', accent_dim: '#7A6038' },
    quick_actions: [
      { label: 'WiFi密码',    query: 'WiFi密码是多少？' },
      { label: '早餐时间',     query: '早餐几点供应？' },
      { label: '退房时间',     query: '退房时间是几点？' },
      { label: '浪漫晚餐推荐', query: '推荐一家浪漫的餐厅' },
      { label: '酒店设施',     query: '酒店有哪些设施？' },
      { label: '周边景点',     query: '附近有什么景点？' },
    ],
    welcome_chips: ['WiFi密码？', '早餐时间？', '推荐一家浪漫的餐厅', '酒店有哪些设施？'],
    rooms: ['1204', '1205', '808', '2001', '305'],
    faq: {
      wifi:       { text: '**智宿酒店** WiFi 信息：', grid: [{ label: '网络名称', value: 'Nexstay_Guest' }, { label: '密码', value: 'Welcome2026' }, { label: '网速', value: '500 Mbps' }, { label: '覆盖范围', value: '全区域（含泳池）' }] },
      breakfast:  { text: '早餐在 **花园露台餐厅**（2楼）：', grid: [{ label: '工作日', value: '07:00 – 10:30' }, { label: '周末', value: '07:30 – 11:30' }, { label: '类型', value: '国际自助餐' }, { label: '包含', value: '煎蛋台、点心、面包' }] },
      checkout:   { text: '**智宿酒店** 退房政策：', grid: [{ label: '标准退房', value: '中午12:00' }, { label: '延迟退房', value: '最晚14:00' }, { label: '延迟费用', value: '80元 / 小时' }, { label: '申请方式', value: '前台或App' }] },
      facilities: { text: '**智宿酒店** 设施：', grid: [{ label: '屋顶泳池', value: '06:00–22:00 · 25楼' }, { label: '健身中心', value: '24小时 · 3楼' }, { label: '水疗桑拿', value: '09:00–21:00 · 需预约' }, { label: '商务中心', value: '24小时 · 2楼' }] },
    },
    restaurants: [
      { emoji: '🕯️', name: '花园餐厅', type: '法式 · 高级料理', rating: '★ 4.8', hours: '18:00–23:00', tag: 'date', tagText: '约会首选', distance: '0.3公里' },
      { emoji: '🐟', name: '日叶屋', type: '日式 · 怀石料理', rating: '★ 4.7', hours: '17:30–22:00', tag: 'date', tagText: '需预约', distance: '0.6公里' },
      { emoji: '🌿', name: '大地餐厅', type: '现代地中海', rating: '★ 4.6', hours: '18:00–22:30', tag: 'open', tagText: '欢迎散客', distance: '0.9公里' },
      { emoji: '🥟', name: '炳胜品味', type: '粤菜 · 米其林', rating: '★ 4.8', hours: '11:00–22:00', tag: 'date', tagText: '广州老字号', distance: '0.5公里' },
    ],
    attractions: [
      { emoji: '🗼', name: '广州塔', type: '地标 · 0.4公里', rating: '★ 4.8', hours: '09:00–22:00', tag: 'open', tagText: '今日开放', distance: '0.4公里' },
      { emoji: '🌳', name: '珠江公园', type: '自然 · 0.8公里', rating: '★ 4.5', hours: '全天开放', tag: 'open', tagText: '免费入场', distance: '0.8公里' },
      { emoji: '🛍️', name: '北京路步行街', type: '购物 · 1.2公里', rating: '★ 4.7', hours: '10:00–22:00', tag: 'open', tagText: '必去', distance: '1.2公里' },
    ],
  },

  SAKURAINN: {
    hotel_id: 'sakurainn',
    name: '樱花旅馆',
    tagline: '传统待客之道，现代智能服务',
    location: '中国·上海',
    locale: 'zh-CN',
    theme: { accent: '#D4849A', accent_light: '#E0A0B0', accent_dim: '#A06070' },
    quick_actions: [
      { label: 'WiFi密码', query: 'WiFi密码是多少？' },
      { label: '早餐时间', query: '早餐几点供应？' },
      { label: '退房时间', query: '退房时间是几点？' },
      { label: '附近景点', query: '附近有什么好玩的？' },
      { label: '酒店设施', query: '酒店有哪些设施？' },
    ],
    welcome_chips: ['WiFi密码？', '早餐时间？', '附近景点？', '酒店设施？'],
    rooms: ['308', '309', '310', '311', '312'],
    faq: {
      wifi:       { text: '**樱花旅馆** WiFi 信息：', grid: [{ label: '网络名称', value: 'SakuraInn_Free' }, { label: '密码', value: 'Shanghai2024' }, { label: '网速', value: '200 Mbps' }, { label: '覆盖范围', value: '全区域覆盖' }] },
      breakfast:  { text: '早餐在 **樱花厅**（1楼）：', grid: [{ label: '每日', value: '07:00 – 10:00' }, { label: '位置', value: '樱花厅 · 1楼' }, { label: '类型', value: '中西式自助' }, { label: '特色', value: '现做小笼包' }] },
      checkout:   { text: '**樱花旅馆** 退房政策：', grid: [{ label: '标准退房', value: '中午12:00' }, { label: '延迟退房', value: '最晚14:00' }, { label: '延迟费用', value: '100元 / 小时' }, { label: '申请方式', value: '前台' }] },
      facilities: { text: '**樱花旅馆** 设施：', grid: [{ label: '健身房', value: '24小时 · B1' }, { label: '茶室', value: '10:00–22:00 · 1楼' }, { label: '洗衣房', value: '24小时 · B1' }, { label: '会议室', value: '09:00–18:00 · 2楼' }] },
    },
    restaurants: [
      { emoji: '🍜', name: '老吉士', type: '本帮菜', rating: '★ 4.7', hours: '11:00–22:00', tag: 'date', tagText: '红烧肉必点', distance: '0.3公里' },
      { emoji: '🥟', name: '南翔馒头店', type: '小笼包', rating: '★ 4.5', hours: '08:30–20:30', tag: 'open', tagText: '百年老店', distance: '0.6公里' },
      { emoji: '🍣', name: '福和慧', type: '素食 · 米其林', rating: '★ 4.9', hours: '17:30–22:30', tag: 'date', tagText: '需预约', distance: '0.5公里' },
    ],
    attractions: [
      { emoji: '🏙️', name: '外滩', type: '地标 · 0.5公里', rating: '★ 4.9', hours: '全天开放', tag: 'open', tagText: '万国建筑博览', distance: '0.5公里' },
      { emoji: '🗼', name: '东方明珠', type: '地标 · 0.8公里', rating: '★ 4.7', hours: '09:00–21:00', tag: 'open', tagText: '陆家嘴地标', distance: '0.8公里' },
      { emoji: '🏯', name: '豫园', type: '园林 · 1.2公里', rating: '★ 4.6', hours: '09:00–17:00', tag: 'open', tagText: '明代江南园林', distance: '1.2公里' },
    ],
  },

  MTNOCEAN: {
    hotel_id: 'mtnocean',
    name: '山海民宿',
    tagline: '山海之间，诗意栖居',
    location: '中国·厦门',
    locale: 'zh-CN',
    theme: { accent: '#7AB85A', accent_light: '#A0D080', accent_dim: '#5A8A3A' },
    quick_actions: [
      { label: 'WiFi密码', query: 'WiFi密码是多少？' },
      { label: '早餐时间', query: '早餐几点供应？' },
      { label: '退房时间', query: '退房时间是几点？' },
      { label: '海边活动', query: '有什么海边活动？' },
      { label: '周边景点', query: '附近有什么景点？' },
    ],
    welcome_chips: ['WiFi密码？', '早餐时间？', '海边活动？', '周边景点？'],
    rooms: ['101', '102', '103', '104', '105'],
    faq: {
      wifi:       { text: '**山海民宿** WiFi 信息：', grid: [{ label: '网络名称', value: 'MtnOcean_Guest' }, { label: '密码', value: 'Xiamen2024' }, { label: '网速', value: '200 Mbps' }, { label: '覆盖范围', value: '客房及公共区域' }] },
      breakfast:  { text: '早餐在 **山海厅**（1楼）：', grid: [{ label: '每日', value: '07:30 – 09:30' }, { label: '位置', value: '山海厅 · 1楼' }, { label: '类型', value: '中式早餐' }, { label: '特色', value: '海鲜粥、手工馒头' }] },
      checkout:   { text: '**山海民宿** 退房政策：', grid: [{ label: '标准退房', value: '中午12:00' }, { label: '延迟退房', value: '最晚14:00' }, { label: '延迟费用', value: '50元 / 小时' }, { label: '行李寄存', value: '免费' }] },
      facilities: { text: '**山海民宿** 设施：', grid: [{ label: '海景露台', value: '全天开放 · 3楼' }, { label: '茶室', value: '10:00–22:00 · 1楼' }, { label: '阅读角', value: '24小时 · 2楼' }, { label: '烧烤区', value: '17:00–22:00 · 需预约' }] },
    },
    restaurants: [
      { emoji: '🦐', name: '临海渔家', type: '海鲜 · 大排档', rating: '★ 4.6', hours: '11:00–22:00', tag: 'open', tagText: '现捞现做', distance: '0.2公里' },
      { emoji: '🍜', name: '沙茶面馆', type: '厦门小吃', rating: '★ 4.5', hours: '07:00–20:00', tag: 'open', tagText: '地道风味', distance: '0.4公里' },
      { emoji: '☕', name: '山海咖啡馆', type: '咖啡 · 简餐', rating: '★ 4.4', hours: '09:00–21:00', tag: 'open', tagText: '海景位', distance: '0.1公里' },
    ],
    attractions: [
      { emoji: '🏖️', name: '环岛路', type: '海滩 · 0.3公里', rating: '★ 4.8', hours: '全天开放', tag: 'open', tagText: '最美马拉松赛道', distance: '0.3公里' },
      { emoji: '🏛️', name: '鼓浪屿', type: '岛屿 · 5公里', rating: '★ 4.9', hours: '全天开放', tag: 'open', tagText: '世界文化遗产', distance: '5公里' },
      { emoji: '🏯', name: '南普陀寺', type: '寺庙 · 3公里', rating: '★ 4.7', hours: '08:00–18:00', tag: 'open', tagText: '千年古刹', distance: '3公里' },
    ],
  },
}


function detectIntent(msg) {
  const m = msg.toLowerCase()
  if (m.includes('wifi') || m.includes('密码') || m.includes('网络') || m.includes('联网')) return 'wifi'
  if (m.includes('早餐') || m.includes('早饭') || m.includes('早点')) return 'breakfast'
  if (m.includes('退房') || m.includes('checkout') || m.includes('check out') || m.includes('离店')) return 'checkout'
  if (m.includes('设施') || m.includes('泳池') || m.includes('健身房') || m.includes('spa') || m.includes('水疗') || m.includes('健身')) return 'facilities'
  if (m.includes('餐厅') || m.includes('晚饭') || m.includes('浪漫') || m.includes('吃饭') || m.includes('美食') || m.includes('晚餐') || m.includes('推荐餐厅')) return 'restaurant'
  if (m.includes('景点') || m.includes('附近') || m.includes('游玩') || m.includes('博物馆') || m.includes('公园') || m.includes('逛街') || m.includes('购物') || m.includes('好玩')) return 'attractions'
  return null
}


const sleep = ms => new Promise(r => setTimeout(r, ms))
const send  = (res, chunk) => res.write(`data: ${JSON.stringify(chunk)}\n\n`)

async function streamText(res, text) {
  for (const word of text.split('')) {
    send(res, { type: 'text_delta', delta: word })
    await sleep(30 + Math.random() * 30)
  }
}

async function streamTrace(res, baseMeta, steps) {
  const trace = steps.map(label => ({ label, status: 'pending' }))
  send(res, { type: 'meta', meta: { ...baseMeta, agentTrace: trace } })
  await sleep(180)
  for (let i = 0; i < trace.length; i++) {
    trace[i].status = 'running'
    send(res, { type: 'meta', meta: { ...baseMeta, agentTrace: [...trace] } })
    await sleep(280 + Math.random() * 180)
    trace[i].status = 'done'
    send(res, { type: 'meta', meta: { ...baseMeta, agentTrace: [...trace] } })
    await sleep(50)
  }
}


async function handleSessionInit(req, res) {
  let body = ''
  for await (const c of req) body += c
  const { hotel_code, room_number } = JSON.parse(body)

  const hotel = HOTELS[hotel_code?.trim().toUpperCase()]
  if (!hotel) {
    res.writeHead(404, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ error: '酒店代码不存在，请核对后重试' }))
    return
  }
  if (!hotel.rooms.includes(room_number?.trim())) {
    res.writeHead(401, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ error: '房间号不存在，请联系前台确认' }))
    return
  }

  await sleep(350 + Math.random() * 300)

  const { rooms, faq, restaurants, attractions, ...hotelConfig } = hotel
  res.writeHead(200, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ session_id: randomUUID(), hotel: hotelConfig }))
}

async function handleChatStream(req, res) {
  let body = ''
  for await (const c of req) body += c
  const { hotel_id, message = '' } = JSON.parse(body)

  const hotel = Object.values(HOTELS).find(h => h.hotel_id === hotel_id)

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  })

  const intent = hotel ? detectIntent(message) : null

  if (!hotel || !intent) {
    const name = hotel?.name ?? '本酒店'
    await streamText(res, `您好！我是 **${name}** 的 AI 助手。我可以帮您查询WiFi密码、早餐时间、退房政策、酒店设施、餐厅推荐和周边景点等信息。请问有什么可以帮您？`)
    send(res, { type: 'meta', meta: { chips: hotel?.welcome_chips?.slice(0, 4) ?? [] } })
    send(res, { type: 'done' })
    res.end()
    return
  }

  if (intent === 'restaurant') {
    await streamText(res, `以下是 **${hotel.name}** 附近的餐厅推荐：`)
    await streamTrace(res, { cards: hotel.restaurants }, [
      `意图识别：餐饮推荐`,
      `语义搜索 · hotel_id=${hotel.hotel_id}`,
      `筛选：评分≥4.3，距离相关`,
      `数据库查询：营业时间及空位`,
      `按匹配度重新排序`,
    ])
    send(res, { type: 'meta', meta: { cards: hotel.restaurants, chips: ['有素食选项吗？', '如何预订？', '有着装要求吗？'] } })
  } else if (intent === 'attractions') {
    await streamText(res, `**${hotel.location}** 的热门景点推荐：`)
    await streamTrace(res, { cards: hotel.attractions }, [
      `意图识别：景点推荐`,
      `语义搜索 · hotel_id=${hotel.hotel_id}`,
      `筛选：评分≥4.0，距离优先`,
      `补充元数据：门票及开放时间`,
      `按相关度重新排序`,
    ])
    send(res, { type: 'meta', meta: { cards: hotel.attractions, chips: ['怎么去？', '门票多少钱？', '开放时间？'] } })
  } else {
    const faq = hotel.faq[intent]
    await streamText(res, faq.text)
    await sleep(80)
    send(res, { type: 'meta', meta: { infoGrid: faq.grid, chips: hotel.welcome_chips.slice(0, 3) } })
  }

  send(res, { type: 'done' })
  res.end()
}


const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return }

  if (req.method === 'GET' && req.url === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ status: 'ok', hotels: Object.keys(HOTELS) }))
    return
  }
  if (req.method === 'POST' && req.url === '/api/session/init') {
    try { await handleSessionInit(req, res) } catch (e) { console.error(e); if (!res.headersSent) { res.writeHead(500); res.end() } }
    return
  }
  if (req.method === 'POST' && req.url === '/api/chat/stream') {
    try { await handleChatStream(req, res) } catch (e) { console.error(e); if (!res.headersSent) { res.writeHead(500); res.end() } }
    return
  }
  res.writeHead(404); res.end()
})


server.listen(PORT, () => {
  console.log(`\n🏨  Hotel AI mock server  →  http://localhost:${PORT}\n`)
  console.log('Demo hotel codes & valid rooms:')
  Object.entries(HOTELS).forEach(([code, h]) => {
    console.log(`  ${code.padEnd(12)}  ${h.name} (${h.location})`)
    console.log(`               Rooms: ${h.rooms.join(', ')}`)
  })
  console.log()
})
