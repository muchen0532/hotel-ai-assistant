// Central config — override via .env.local
export const API_BASE = import.meta.env.VITE_API_BASE ?? ''


// Quick-action suggestions shown in the input area
export const QUICK_ACTIONS = [
  { label: 'WiFi 密码', query: 'WiFi password?' },
  { label: '早餐时间', query: 'What are the breakfast hours?' },
  { label: '退房时间', query: 'What is the check-out time?' },
  { label: '浪漫晚餐推荐', query: 'Recommend a romantic restaurant for tonight' },
  { label: '酒店设施', query: 'What facilities does the hotel have?' },
  { label: '周边景点', query: 'What are the nearby attractions?' },
  { label: 'SPA 预约', query: 'How do I book the spa?' },
  { label: '机场接送', query: 'Can I arrange airport transfer?' },
]

// Welcome message chips shown on first load
export const WELCOME_CHIPS = [
  'WiFi 密码?',
  '早餐时间?',
  '退房时间?',
  '浪漫晚餐推荐',
]
