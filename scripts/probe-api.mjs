// 一次性契约探测脚本：把后端真实返回的 JSON 打出来，用于核对 Android 端模型定义。
// 用法：node scripts/probe-api.mjs
const BASE = process.env.CP_BASE || 'http://127.0.0.1:8080/api/v1'
const email = `apiprobe${Date.now()}@test.com`
const password = 'Test123456'

let token = ''

async function api(path, { method = 'GET', body, raw = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body && !raw) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: raw ? body : body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = { _raw: text } }
  return { status: res.status, json }
}

function show(title, value) {
  console.log(`\n===== ${title} =====`)
  console.log(typeof value === 'string' ? value : JSON.stringify(value, null, 2))
}

async function waitTask(taskId, tries = 40) {
  for (let i = 0; i < tries; i++) {
    const { json } = await api(`/ai-tasks/${taskId}`)
    if (json.data && ['SUCCESS', 'FAILED', 'CANCELLED'].includes(json.data.status)) return json.data
    await new Promise(r => setTimeout(r, 1000))
  }
  return null
}

const run = async () => {
  // 1) 注册
  let r = await api('/auth/register', { method: 'POST', body: { email, password, nickname: 'Probe' } })
  token = r.json.data.accessToken
  show('LOGIN/REGISTER data', r.json.data)

  // 2) 建 JD
  const rawText = [
    '职位名称：高级后端工程师',
    '公司名称：示例科技',
    '工作地点：北京',
    '岗位职责：',
    '1. 负责高并发后端服务的设计与开发；',
    '2. 负责线上系统的稳定性与性能优化。',
    '任职要求：',
    '1. 本科及以上学历，计算机相关专业；',
    '2. 5 年以上后端开发经验；',
    '3. 熟悉 Java、Spring Boot、MySQL、Redis、Kafka。',
    '福利待遇：六险一金、弹性工作。',
  ].join('\n')

  r = await api('/jd/upload', {
    method: 'POST',
    body: { jobTitle: '高级后端工程师', companyName: '示例科技', rawText, sourceType: 'MANUAL', salaryRange: '30-50K' },
  })
  const jd = r.json.data
  show('JD upload data', jd)
  await new Promise(res => setTimeout(res, 4000))

  r = await api(`/jd/${jd.jdId}/parse/result`)
  show('JD parse result', r.json.data)
  const jdSkills = (r.json.data.skills || []).map(s => s.name)
  console.log('>>> jd_skills =', JSON.stringify(jdSkills))

  // 3) 简历优化
  r = await api('/ai-tasks', {
    method: 'POST',
    body: {
      taskType: 'resume_optimize',
      bizId: jd.jdBizId,
      input: {
        bullet: '负责用户增长相关工作，提升了留存率',
        tone: 'professional',
        jd_skills: jdSkills,
        jd_id: jd.jdBizId,
        jd_text: rawText,
      },
    },
  })
  show('TASK create (resume_optimize)', r.json.data)
  const opt = await waitTask(r.json.data.taskId)
  show('TASK output (resume_optimize)', opt && opt.output)

  // 4) 求职信
  r = await api('/ai-tasks', {
    method: 'POST',
    body: {
      taskType: 'cover_letter',
      bizId: jd.jdBizId,
      input: {
        job_title: '高级后端工程师',
        company_name: '示例科技',
        tone: 'professional',
        jd_skills: jdSkills,
        resume_highlights: ['后端工程师简历'],
        jd_id: jd.jdBizId,
        jd_text: rawText,
      },
    },
  })
  const cl = await waitTask(r.json.data.taskId)
  show('TASK output (cover_letter)', cl && cl.output)

  // 5) 职业规划
  r = await api('/ai-tasks', {
    method: 'POST',
    body: {
      taskType: 'career_plan',
      input: {
        current_title: '后端工程师',
        years_of_experience: 3,
        target_industry: '互联网',
        target_position: '高级后端工程师',
        skills: ['Java', 'MySQL'],
      },
    },
  })
  const cp = await waitTask(r.json.data.taskId)
  show('TASK output (career_plan)', cp && cp.output)

  // 6) 模拟面试 —— 出题
  r = await api('/ai-tasks', {
    method: 'POST',
    body: {
      taskType: 'interview',
      bizId: jd.jdBizId,
      input: {
        phase: 'question',
        round_no: 1,
        total_rounds: 3,
        jd_skills: jdSkills,
        resume_highlights: ['后端工程师简历'],
        job_title: '高级后端工程师',
        jd_id: jd.jdBizId,
        jd_text: rawText,
        resume_id: '',
        resume_text: '后端工程师简历',
      },
    },
  })
  const q = await waitTask(r.json.data.taskId)
  show('TASK output (interview/question)', q && q.output)

  // 7) 模拟面试 —— 评分
  const question = q && q.output && q.output.question
  r = await api('/ai-tasks', {
    method: 'POST',
    body: {
      taskType: 'interview',
      bizId: jd.jdBizId,
      input: {
        phase: 'evaluate',
        round_no: 1,
        question: question || '请介绍一个你做过的项目',
        answer: '在电商系统重构中，我负责订单履约链路。背景是订单积压，我接手后梳理并拆分了同步链路，落地了异步化和分库分表，三个月内订单处理延迟从 800ms 降到 120ms，积压订单清零。',
        jd_skills: jdSkills,
      },
    },
  })
  const ev = await waitTask(r.json.data.taskId)
  show('TASK output (interview/evaluate)', ev && ev.output)

  // 8) 上传简历 → 匹配
  const resumeText = '张三\n高级后端工程师\n5年经验\n熟悉 Java、Spring Boot、MySQL、Redis、Kafka\n负责高并发订单系统，延迟从 800ms 降到 120ms\n邮箱 zhangsan@test.com\n'
  const fd = new FormData()
  fd.append('file', new Blob([resumeText], { type: 'text/plain' }), 'resume.txt')
  fd.append('title', '后端工程师简历')
  const upRes = await fetch(`${BASE}/resume/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  })
  const upJson = await upRes.json()
  show('RESUME upload data', upJson.data)
  await new Promise(res => setTimeout(res, 5000))

  r = await api('/resume/list?page=1&size=5')
  show('RESUME list', r.json.data)
  const resume = (r.json.data.items || [])[0]

  if (resume) {
    r = await api(`/resume/${resume.resumeId}/ats-score`)
    show('RESUME ats-score', r.json.data)
    r = await api(`/resume/${resume.resumeId}/versions`)
    show('RESUME versions', r.json.data)
    r = await api(`/resume/${resume.resumeId}`)
    show('RESUME detail', { ...r.json.data, textContent: (r.json.data.textContent || '').slice(0, 120) })

    r = await api('/match/evaluate', { method: 'POST', body: { resumeId: resume.resumeId, jdId: jd.jdId } })
    show('MATCH evaluate', r.json.data)
    const recordId = r.json.data.recordId
    for (let i = 0; i < 40; i++) {
      const mr = await api(`/match/${recordId}`)
      if (mr.json.data && ['SUCCESS', 'FAILED', 'CANCELLED'].includes(mr.json.data.status)) break
      await new Promise(res => setTimeout(res, 1000))
    }
    r = await api(`/match/${recordId}`)
    show('MATCH record', r.json.data)
  }

  // 9) 资料 / 会员
  r = await api('/user/profile')
  show('USER profile', r.json.data)
  r = await api('/user/membership')
  show('USER membership', r.json.data)

  console.log('\nPROBE_DONE')
}

run().catch(e => { console.error('PROBE_FAILED', e); process.exit(1) })
