/** AI-GENERATED AIDEV-NOTE: 本地验收真实 HTTP/STOMP/MCP，输出不包含凭证。 */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../ui-react/package.json', import.meta.url));
const { Client } = require('@stomp/stompjs');
const base = process.env.JOBCLAW_TEST_URL || 'http://localhost:8087';
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '仅允许本地验收');
let checks = 0;
const pass = (label) => { checks++; console.log(`PASS ${label}`); };
const request = async (path, options = {}) => {
  const response = await fetch(base + path, { ...options, signal: AbortSignal.timeout(15000) });
  return { status: response.status, body: await response.json() };
};
async function login(type) {
  const result = await request(`/api/wx/dev/login?type=${type}`);
  assert.equal(result.body.code, 0);
  return result.body.data;
}
let rpcId = 0;
async function rpc(config, method, params = {}) {
  return request(new URL(config.url).pathname, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream',
      'MCP-Protocol-Version': '2025-11-25', ...config.headers },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++rpcId, method, params }),
  });
}
async function connect(token) {
  const messages = [];
  let notify;
  const client = new Client({
    brokerURL: base.replace(/^http/, 'ws') + '/ws', connectHeaders: { 'X-OC-TOKEN': token },
    webSocketFactory: () => new WebSocket(base.replace(/^http/, 'ws') + '/ws'),
    reconnectDelay: 0, connectionTimeout: 8000,
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { void client.deactivate(); reject(new Error('STOMP连接或订阅超时')); }, 10000);
    client.onStompError = () => { clearTimeout(timer); void client.deactivate(); reject(new Error('STOMP认证失败')); };
    client.onConnect = () => {
      for (const suffix of ['', '/stream', '/done', '/error']) {
        client.subscribe('/user/queue/rsp' + suffix, frame => {
          const message = JSON.parse(frame.body);
          messages.push(message);
          if (message.msgId === 'ready') { clearTimeout(timer); resolve(); }
          notify?.();
        });
      }
      // 用真实帮助回复确认订阅可用；Spring simple broker 不承诺 SEND receipt。
      client.publish({ destination: '/app/chat.send',
        body: JSON.stringify({ msgId: 'ready', message: '/help' }) });
    };
    client.activate();
  });
  return { client, messages, async send(message) {
    const msgId = crypto.randomUUID();
    const result = new Promise((resolve, reject) => {
      const timer = setTimeout(() => { notify = undefined; reject(new Error('消息回复超时: ' + message)); }, 10000);
      notify = () => {
        const found = messages.find(m => m.msgId === msgId && m.type === 'text');
        if (found) { clearTimeout(timer); notify = undefined; resolve(found); }
      };
    });
    client.publish({ destination: '/app/chat.send', body: JSON.stringify({ msgId, message }) });
    return result;
  } };
}
const connections = [];
try {
  assert.equal((await request('/actuator/health')).body.data.status, 'UP'); pass('后端健康');
  const normal = await login('user');
  const admin = await login('admin');
  const providerState = (await request('/api/admin/env-config/llm-providers', { headers: { 'X-OC-TOKEN': admin.token } })).body.data;
  assert.equal(providerState.defaults.text, 'deepseek#deepseek-flash');
  assert.equal(providerState.defaults.vision, 'deepseek#deepseek-flash'); pass('文本和视觉统一DeepSeek Flash');
  assert.equal(providerState.providers.deepseek.models.find(m => m.name === 'deepseek-flash').multimodal, true); pass('DeepSeek多模态能力已启用');
  const config = admin.user?.config ?? admin.userInfo?.config;
  // DevLoginVo 的用户信息字段由实际返回确定，禁止在输出中打印 token。
  assert(config, '登录返回应包含MCP配置');
  assert.equal(config.type, 'http'); assert.equal(new URL(config.url).pathname, '/api/mcp'); pass('个人中心MCP配置');
  for (const headers of [{}, { Authorization: 'Bearer invalid-local-test' }]) {
    const result = await rpc({ ...config, headers }, 'tools/list');
    assert.equal(result.status, 401); pass('MCP拒绝' + (headers.Authorization ? '无效凭证' : '无凭证'));
  }
  const init = await rpc(config, 'initialize', { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'JobClaw QA', version: '1.0' } });
  assert.equal(init.body.result.serverInfo.name, 'JobClaw'); pass('MCP初始化');
  const tools = (await rpc(config, 'tools/list')).body.result.tools;
  assert.equal(tools.length, 1); assert.equal(tools[0].name, 'queryRecommendOcListForUser');
  assert.equal(Object.keys(tools[0].inputSchema.properties.req.properties).length, 5); pass('工具发现和五个查询字段');
  async function query(req) {
    const { body } = await rpc(config, 'tools/call', { name: tools[0].name, arguments: { req } });
    assert.equal(body.result.isError, false);
    return JSON.parse(body.result.content.find(c => c.type === 'text').text);
  }
  const all = await query({}); assert.equal(all.length, 20); pass('空条件最多20条');
  const banks = await query({ companyType: '银行' });
  assert(banks.length > 0 && banks.every(j => j.companyType === '银行')); pass('公司类型筛选');
  assert.deepEqual(await query({ position: '   ' }), all); pass('空白条件按未指定处理');
  const nationwide = await query({ jobLocation: '全国' }); assert.deepEqual(nationwide, all); pass('全国不额外限制地点');
  const beijing = await query({ jobLocation: '北京', recruitmentTarget: '2026', position: '研发', recruitmentType: '秋招' });
  assert(beijing.length > 0); assert(beijing.every(j => j.jobLocation.includes('北京') && j.recruitmentTarget.includes('2026') && j.position.includes('研发') && j.recruitmentType.includes('秋招'))); pass('教程北京2026研发秋招示例');
  assert.equal((await query({ position: '__QA_NONEXISTENT_JOB__' })).length, 0); pass('无匹配返回空数组');
  const again = await query({ jobLocation: '北京', recruitmentTarget: '2026', position: '研发', recruitmentType: '秋招' });
  assert.deepEqual(again, beijing); pass('连续重复调用');
  const invalid = await rpc(config, 'tools/call', { name: 'nonexistent-tool', arguments: {} });
  assert(invalid.body.error || invalid.body.result?.isError); pass('未知工具返回错误');
  await assert.rejects(connect('invalid-local-test'), /STOMP认证失败/); pass('WebSocket拒绝无效凭证');
  const upload = async (token, userId, content) => {
    const form = new FormData(); form.append('file', new Blob([content], { type: 'text/plain' }), 'qa-demo.txt');
    if (userId !== undefined) form.append('userId', String(userId));
    return request('/api/ws/upload', { method: 'POST', headers: token ? { 'X-OC-TOKEN': token } : {}, body: form });
  };
  const anonymousUpload = await upload(null, normal.user.userId, 'local QA');
  assert(anonymousUpload.status === 401 || anonymousUpload.body.code !== 0); pass('未登录禁止上传');
  const wrongUpload = await upload(normal.token, admin.user.userId, 'local QA');
  assert.equal(wrongUpload.status, 403); pass('禁止替其他用户上传');
  const emptyUpload = await upload(normal.token, normal.user.userId, '');
  assert.equal(emptyUpload.status, 400); pass('拒绝空附件');
  const validUpload = await upload(normal.token, normal.user.userId, 'JobClaw local QA demo attachment.');
  assert.equal(validUpload.body.code, 0); assert(validUpload.body.data.fileId); pass('登录用户文本附件上传');
  const userWs = await connect(normal.token); connections.push(userWs.client);
  const adminWs = await connect(admin.token); connections.push(adminWs.client);
  assert((await userWs.send('/help')).content.includes('/agents')); pass('普通用户WebSocket命令收发');
  assert((await adminWs.send('/help')).content.includes('/agents')); pass('管理员WebSocket命令收发');
  const response = await userWs.send('/current');
  await adminWs.send('/current');
  assert(!adminWs.messages.some(m => m.msgId === response.msgId)); pass('不同用户回复隔离');
  assert((await userWs.send('/agent JobRecommendAgent')).content.includes('已为您切换到 JobRecommendAgent')); pass('岗位推荐Agent切换');
  assert((await userWs.send('/current')).content.includes('JobRecommendAgent')); pass('切换后状态一致');
  assert((await userWs.send('/agent default')).content.includes('已为您切换到 default')); pass('恢复通用问答');
  await userWs.client.deactivate();
  const reconnected = await connect(normal.token); connections.push(reconnected.client);
  assert((await reconnected.send('/help')).content.includes('/agents')); pass('断开后重新连接');
  console.log(`验收通过 ${checks} 项；教程查询命中 ${beijing.length} 条。`);
} finally {
  await Promise.all(connections.map(c => c.deactivate()));
}
