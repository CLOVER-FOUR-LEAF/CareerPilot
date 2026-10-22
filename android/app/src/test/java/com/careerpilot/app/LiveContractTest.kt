package com.careerpilot.app

import com.careerpilot.app.data.api.ApiClient
import com.careerpilot.app.data.model.AiTask
import com.careerpilot.app.data.model.AtsScoreResult
import com.careerpilot.app.data.model.AuthUser
import com.careerpilot.app.data.model.CareerPlanResult
import com.careerpilot.app.data.model.CoverLetterResult
import com.careerpilot.app.data.model.InterviewEvaluation
import com.careerpilot.app.data.model.InterviewQuestion
import com.careerpilot.app.data.model.JdParseResult
import com.careerpilot.app.data.model.LoginResponse
import com.careerpilot.app.data.model.MatchRecord
import com.careerpilot.app.data.model.Membership
import com.careerpilot.app.data.model.PageResult
import com.careerpilot.app.data.model.ResumeDetail
import com.careerpilot.app.data.model.ResumeListItem
import com.careerpilot.app.data.model.ResumeOptimizeResult
import com.careerpilot.app.data.model.ResumeUploadResult
import com.careerpilot.app.data.model.ResumeVersion
import com.careerpilot.app.data.model.UserProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约解码测试。
 *
 * 这里的 JSON 全部是从**真实运行中的后端**（`http://127.0.0.1:8080/api/v1`）抓下来的响应体，
 * 用生产环境同款的 `ApiClient.json` 配置解码，断言字段能落到模型上。
 *
 * 目的：防止「后端改了字段 / 类型悄悄变了」这种只有装到手机上才发现的问题。
 */
class LiveContractTest {

    private val json = ApiClient.json

    // ─── ATS 评分（Agent AnalysisResult 原结构）────────────────────

    @Test
    fun `ats score decodes`() {
        val raw = """
        {
          "skills": [
            {"name": "mysql", "category": "technical", "proficiency": "unknown"},
            {"name": "java", "category": "technical", "proficiency": "unknown"}
          ],
          "has_photo": false,
          "resume_id": "res_8e7d3b56d7a84e0a",
          "dimensions": [
            {"name": "structure", "score": 0, "weight": 1.5, "feedback": "简历结构不完整"},
            {"name": "keywords", "score": 60, "weight": 1.3, "feedback": "关键词覆盖一般"},
            {"name": "typos", "score": 100, "weight": 1, "feedback": "格式规范"}
          ],
          "word_count": 103,
          "has_contact": false,
          "suggestions": [
            {"title": "完善简历结构", "detail": "建议按标准顺序组织", "category": "structure", "priority": "high"}
          ],
          "overall_score": 46.2,
          "section_count": 0,
          "updatedAt": "2026-10-22T15:23:53"
        }
        """.trimIndent()

        val r = json.decodeFromString(AtsScoreResult.serializer(), raw)

        assertEquals("res_8e7d3b56d7a84e0a", r.resumeId)
        assertEquals(46.2, r.overallScore, 0.0001)
        assertEquals(3, r.dimensions?.size)
        // 关键：后端给的是整数 JSON 字面量，必须能进 Double 字段
        assertEquals(0.0, r.dimensions!![0].score, 0.0001)
        assertEquals(60.0, r.dimensions!![1].score, 0.0001)
        assertEquals(1.5, r.dimensions!![0].weight, 0.0001)
        assertEquals(1.0, r.dimensions!![2].weight, 0.0001)
        assertEquals(2, r.skills?.size)
        assertEquals("完善简历结构", r.suggestions?.first()?.title)
        assertEquals(103, r.wordCount)
        assertEquals(false, r.hasContact)
    }

    // ─── JD 解析（Agent JdChunk 原结构）──────────────────────────

    @Test
    fun `jd parse result decodes`() {
        val raw = """
        {
          "skills": [
            {"name": "spring", "category": "technical", "importance": "required", "years_required": null}
          ],
          "benefits": [],
          "location": "北京",
          "raw_text": "职位名称：高级后端工程师",
          "job_title": "高级后端工程师",
          "department": "",
          "salary_max": null,
          "salary_min": null,
          "parse_score": 0.9,
          "company_name": "",
          "parse_method": "regex+classify",
          "requirements": ["本科及以上学历"],
          "employment_type": "",
          "salary_currency": "CNY",
          "responsibilities": ["负责高并发后端服务"],
          "education_required": "本科",
          "experience_max_years": null,
          "experience_min_years": null
        }
        """.trimIndent()

        val r = json.decodeFromString(JdParseResult.serializer(), raw)

        assertEquals("高级后端工程师", r.jobTitle)
        assertEquals("北京", r.location)
        assertEquals(0.9, r.parseScore, 0.0001)
        assertEquals("spring", r.skills?.first()?.name)
        assertNull(r.salaryMin)
        assertNull(r.seniority)          // 字段缺失 → 默认 null
        assertNull(r.industry)           // 字段缺失 → 默认 null
        assertNull(r.createdAt)          // 字段缺失 → 默认 null
        assertEquals(1, r.requirements?.size)
    }

    // ─── 匹配记录（业务 ID + 整数分数）────────────────────────────

    @Test
    fun `match record decodes with business ids and integer scores`() {
        val raw = """
        {
          "finalScore": 54,
          "gapList": [
            {
              "severity": 5,
              "suggestion": "该学历要求属于一票否决项",
              "gapType": "weak_metric",
              "gapContent": "学历不满足硬性要求"
            },
            {
              "severity": 4,
              "suggestion": "在项目经历中加入实际使用案例",
              "gapType": "missing_skill",
              "gapContent": "SQL"
            }
          ],
          "completedAt": "2026-10-22T15:24:00",
          "jdId": "jd_2629730de92e43e9",
          "keywordScore": 80,
          "errorMessage": null,
          "matchedSkills": ["Spring", "Redis", "Java", "MySQL"],
          "businessScore": 60,
          "recordId": "mch_76e35eae58a14c95",
          "createdAt": "2026-10-22T15:23:59",
          "resumeId": "res_8e7d3b56d7a84e0a",
          "semanticScore": 28,
          "taskId": "task_7e73271d0327468a",
          "status": "SUCCESS"
        }
        """.trimIndent()

        val r = json.decodeFromString(MatchRecord.serializer(), raw)

        // 记录里是业务 ID 字符串，不是数字
        assertEquals("res_8e7d3b56d7a84e0a", r.resumeId)
        assertEquals("jd_2629730de92e43e9", r.jdId)
        // 分数是整数 JSON 字面量
        assertEquals(54.0, r.finalScore!!, 0.0001)
        assertEquals(80.0, r.keywordScore!!, 0.0001)
        assertEquals(60.0, r.businessScore!!, 0.0001)
        assertEquals(28.0, r.semanticScore!!, 0.0001)
        assertEquals(2, r.gapList?.size)
        assertEquals(5, r.gapList!![0].severity)
        assertEquals("missing_skill", r.gapList!![1].gapType)
        assertEquals(4, r.matchedSkills?.size)
        assertNull(r.errorMessage)
    }

    // ─── AI 任务输出 ────────────────────────────────────────────

    @Test
    fun `resume optimize output decodes`() {
        val raw = """
        {
          "original": "负责用户增长相关工作，提升了留存率",
          "rewritten": "主导用户增长相关工作，引入spring、redis、java……",
          "change_note": "将弱动词「负责」替换为强动词",
          "star_breakdown": {
            "task": "需要独立承担用户增长相关工作",
            "action": "负责用户增长相关工作，引入spring、redis、java",
            "result": "请补齐具体数字",
            "situation": "在用户增长相关场景下"
          },
          "injected_skills": ["spring", "redis", "java"]
        }
        """.trimIndent()

        val r = json.decodeFromString(ResumeOptimizeResult.serializer(), raw)

        assertEquals("主导用户增长相关工作，引入spring、redis、java……", r.rewritten)
        assertEquals(3, r.injectedSkills?.size)
        assertEquals("在用户增长相关场景下", r.starBreakdown?.situation)
        assertEquals("请补齐具体数字", r.starBreakdown?.result)
        assertEquals("将弱动词「负责」替换为强动词", r.changeNote)
    }

    @Test
    fun `cover letter output decodes`() {
        val raw = """
        {
          "tone": "professional",
          "content": "尊敬的示例科技招聘负责人：\n您好……",
          "word_count": 321,
          "highlighted_skills": ["spring", "redis", "java", "sql"]
        }
        """.trimIndent()

        val r = json.decodeFromString(CoverLetterResult.serializer(), raw)

        assertEquals("professional", r.tone)
        assertEquals(321, r.wordCount)
        assertEquals(4, r.highlightedSkills?.size)
        assertTrue(r.content.contains("招聘负责人"))
    }

    @Test
    fun `career plan output decodes`() {
        val raw = """
        {
          "roadmap": [
            {
              "goal": "定位能力差距并补齐硬性门槛",
              "phase": "第 1-3 个月",
              "actions": ["逐条拆解硬性要求", "做一次能力盘点"],
              "doc_ids": ["kb_int_001", "kb_int_003"],
              "milestone": "产出目标岗位能力清单"
            }
          ],
          "summary": "你目前是后端工程师（3 年经验）……",
          "citations": [
            {"doc_id": "kb_int_001", "source": "互联网岗位能力模型", "version": "2026.01"}
          ],
          "skill_gap": ["Spring Boot", "Redis"]
        }
        """.trimIndent()

        val r = json.decodeFromString(CareerPlanResult.serializer(), raw)

        assertEquals(1, r.roadmap?.size)
        assertEquals("第 1-3 个月", r.roadmap!![0].phase)
        assertEquals(2, r.roadmap!![0].actions?.size)
        assertEquals(2, r.roadmap!![0].docIds?.size)
        assertEquals("kb_int_001", r.citations?.first()?.docId)
        assertEquals("2026.01", r.citations?.first()?.version)
        assertEquals(2, r.skillGap?.size)
    }

    @Test
    fun `interview question and evaluation decode`() {
        val questionRaw = """
        {
          "focus": "考察经历真实性、STAR 完整度（聚焦：spring）",
          "question": "请描述一次你用 spring 改变了原有做法的项目。",
          "round_no": 1,
          "question_type": "behavioral"
        }
        """.trimIndent()

        val q = json.decodeFromString(InterviewQuestion.serializer(), questionRaw)
        assertEquals(1, q.roundNo)
        assertEquals("behavioral", q.questionType)
        assertTrue(q.focus.contains("spring"))

        val evalRaw = """
        {
          "score": 7.4,
          "feedback": "回答缺少：行动（Action）。",
          "followup": "能具体讲一下方案吗？",
          "star_coverage": {"task": true, "action": false, "result": true, "situation": true}
        }
        """.trimIndent()

        val e = json.decodeFromString(InterviewEvaluation.serializer(), evalRaw)
        assertEquals(7.4, e.score, 0.0001)
        assertEquals(false, e.starCoverage?.action)
        assertEquals(true, e.starCoverage?.situation)
        assertEquals("能具体讲一下方案吗？", e.followup)
    }

    @Test
    fun `ai task snapshot decodes with polymorphic output`() {
        val raw = """
        {
          "taskId": "task_5a7d2d674c214314",
          "userId": "usr_110522160b394704",
          "taskType": "resume_optimize",
          "bizId": "jd_2629730de92e43e9",
          "status": "SUCCESS",
          "progress": 100,
          "currentAgent": "resume-optimizer-agent",
          "input": {"bullet": "负责用户增长相关工作", "tone": "professional"},
          "output": {"original": "a", "rewritten": "b"},
          "errorMessage": null,
          "retryCount": 0,
          "durationMs": 812,
          "startedAt": "2026-10-22T15:20:11",
          "completedAt": "2026-10-22T15:20:12",
          "createdAt": "2026-10-22T15:20:11",
          "updatedAt": "2026-10-22T15:20:12"
        }
        """.trimIndent()

        val t = json.decodeFromString(AiTask.serializer(), raw)
        assertEquals("task_5a7d2d674c214314", t.taskId)
        assertEquals("SUCCESS", t.status)
        assertEquals(100, t.progress)
        assertNotNull(t.output)

        // output 是 JsonElement，页面按需再反序列化成具体类型
        val typed = json.decodeFromJsonElement(
            ResumeOptimizeResult.serializer(),
            t.output!!,
        )
        assertEquals("b", typed.rewritten)
    }

    // ─── 用户 / 简历 ───────────────────────────────────────────

    @Test
    fun `login response decodes`() {
        val raw = """
        {
          "accessToken": "eyJhbGciOiJIUzUxMiJ9.aaa.bbb",
          "refreshToken": "eyJhbGciOiJIUzUxMiJ9.ccc.ddd",
          "tokenType": "Bearer",
          "expiresIn": 7200,
          "user": {
            "userId": 10,
            "userIdBiz": "usr_110522160b394704",
            "email": "apiprobe@test.com",
            "nickname": "Probe",
            "avatar": null,
            "membershipLevel": "FREE"
          }
        }
        """.trimIndent()

        val r = json.decodeFromString(LoginResponse.serializer(), raw)
        assertEquals("Bearer", r.tokenType)
        assertEquals(7200, r.expiresIn)
        assertEquals(10L, r.user?.userId)
        assertNull(r.user?.avatar)
    }

    @Test
    fun `user profile decodes with nulls and empty lists`() {
        val raw = """
        {
          "userId": 10, "userIdBiz": "usr_110522160b394704",
          "email": "apiprobe@test.com", "phone": null, "nickname": "Probe", "avatar": null,
          "gender": null, "birthYear": null, "city": null, "currentTitle": null,
          "yearsOfExperience": null, "educationLevel": null, "school": null, "major": null,
          "graduationYear": null, "targetIndustry": null, "targetPosition": null,
          "targetSeniority": null, "targetCity": [], "targetSalaryMin": null,
          "targetSalaryMax": null, "skills": [],
          "createdAt": "2026-10-22T15:20:05", "updatedAt": "2026-10-22T15:20:05"
        }
        """.trimIndent()

        val r = json.decodeFromString(UserProfile.serializer(), raw)
        assertEquals(10L, r.userId)
        assertEquals(0, r.targetCity?.size)
        assertEquals(0, r.skills?.size)
        assertNull(r.phone)
    }

    @Test
    fun `membership decodes`() {
        val raw = """
        {"level": "FREE", "autoRenew": false, "levelName": "免费版",
         "aiQuotaTotal": 3, "expireAt": null, "aiQuotaRemaining": 3}
        """.trimIndent()

        val r = json.decodeFromString(Membership.serializer(), raw)
        assertEquals("FREE", r.level)
        assertEquals("免费版", r.levelName)
        assertEquals(3, r.aiQuotaRemaining)
        assertEquals(3, r.aiQuotaTotal)
        assertNull(r.expireAt)
    }

    @Test
    fun `resume list page decodes`() {
        val raw = """
        {
          "total": 1, "size": 5, "page": 1,
          "items": [
            {
              "resumeId": 6, "resumeBizId": "res_8e7d3b56d7a84e0a",
              "title": "后端工程师简历", "atsScore": 46, "parseStatus": "SUCCESS",
              "isPrimary": false, "updatedAt": "2026-10-22T15:23:54"
            }
          ]
        }
        """.trimIndent()

        val r = json.decodeFromString(
            PageResult.serializer(ResumeListItem.serializer()),
            raw,
        )
        assertEquals(1, r.total)
        assertEquals(1, r.page)
        assertEquals(46, r.items.first().atsScore)
        assertEquals("res_8e7d3b56d7a84e0a", r.items.first().resumeBizId)
    }

    @Test
    fun `resume upload result decodes with extra fields`() {
        val raw = """
        {
          "resumeId": 6, "resumeBizId": "res_8e7d3b56d7a84e0a",
          "versionId": "ver_0f1601ae83cc4935", "title": "后端工程师简历",
          "parseStatus": "PROCESSING", "taskId": "task_804fc41fd13f4f0c",
          "sectionCount": 1, "wordCount": 103, "createdAt": "2026-10-22T15:23:53.1366258"
        }
        """.trimIndent()

        val r = json.decodeFromString(ResumeUploadResult.serializer(), raw)
        assertEquals(6L, r.resumeId)
        assertEquals("PROCESSING", r.parseStatus)
        // 后端多返回的字段必须被忽略而不是报错
        assertEquals("ver_0f1601ae83cc4935", r.versionId)
    }

    @Test
    fun `resume detail and versions decode`() {
        val detailRaw = """
        {
          "resumeId": 6, "resumeBizId": "res_8e7d3b56d7a84e0a",
          "title": "后端工程师简历", "atsScore": 46, "parseStatus": "SUCCESS",
          "textContent": "张三\n高级后端工程师",
          "createdAt": "2026-10-22T15:23:53", "updatedAt": "2026-10-22T15:23:54"
        }
        """.trimIndent()

        val d = json.decodeFromString(ResumeDetail.serializer(), detailRaw)
        assertEquals(46, d.atsScore)
        assertTrue(d.textContent.contains("张三"))

        val versionsRaw = """
        [{"versionId": "ver_0f1601ae83cc4935", "versionNo": 1, "atsScore": 46,
          "changeNote": "初始上传", "createdAt": "2026-10-22T15:23:53"}]
        """.trimIndent()

        val v = json.decodeFromString(ListSerializer(ResumeVersion.serializer()), versionsRaw)
        assertEquals(1, v.size)
        assertEquals(1, v.first().versionNo)
        assertEquals("初始上传", v.first().changeNote)
    }

    @Test
    fun `auth user decodes standalone`() {
        val raw = """
        {"userId": 10, "userIdBiz": "usr_110522160b394704", "email": "a@test.com",
         "nickname": "Probe", "avatar": null, "membershipLevel": "FREE"}
        """.trimIndent()

        val u = json.decodeFromString(AuthUser.serializer(), raw)
        assertEquals("usr_110522160b394704", u.userIdBiz)
        assertEquals("FREE", u.membershipLevel)
    }
}
