import json
import os

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from openai import OpenAI
from pydantic import BaseModel

load_dotenv()

app = FastAPI()


class ResumeRequest(BaseModel):
    content: str


class JobMatchRequest(BaseModel):
    resumeContent: str
    jobDescription: str
    jobName: str | None = None
    companyName: str | None = None


def get_client() -> OpenAI:
    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        raise HTTPException(
            status_code=500,
            detail="未配置 DEEPSEEK_API_KEY，请在 ai-python-service/.env 中设置"
        )
    return OpenAI(
        api_key=api_key,
        base_url="https://api.deepseek.com",
    )


@app.post("/api/ai/resume/analyze")
def analyze_resume(req: ResumeRequest):

    text = (req.content or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="content 不能为空")

    prompt = f"""请分析下面这份技术简历文本，并严格返回一个 JSON 对象，字段如下：
{{
  "name": "姓名",
  "education": {{
    "school": "学校",
    "major": "专业",
    "degree": "学历"
  }},
  "skills": ["技能1", "技能2"],
  "projects": ["项目1"],
  "experiences": ["经历1"],
  "strengths": ["优势1"],
  "weaknesses": ["不足1"],
  "suggestions": ["建议1"],
  "score": 0
}}

要求：
1. 只返回合法 JSON，不要 markdown，不要额外说明
2. 尽量从文本提取真实信息；没有的字段用空字符串或空数组
3. score 为 0-100 的整数，综合技术栈与经历完整度评分

简历文本：
{text}
"""

    try:
        client = get_client()
        response = client.chat.completions.create(
            model=os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
            messages=[
                {
                    "role": "system",
                    "content": "你是一名专业的IT招聘顾问，负责分析技术岗位求职者简历。请始终以 JSON 对象格式回复。"
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            response_format={
                "type": "json_object"
            },
            temperature=0.2
        )

        content = response.choices[0].message.content
        return json.loads(content)

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"AI分析失败：{str(e)}"
        )


@app.post("/api/ai/job-match")
def match_job(req: JobMatchRequest):

    resume_content = (req.resumeContent or "").strip()
    job_description = (req.jobDescription or "").strip()

    if not resume_content:
        raise HTTPException(status_code=400, detail="resumeContent 不能为空")
    if not job_description:
        raise HTTPException(status_code=400, detail="jobDescription 不能为空")

    job_name = (req.jobName or "").strip()
    company_name = (req.companyName or "").strip()

    prompt = f"""你是一名专业的IT招聘顾问。

根据候选人的简历和岗位JD进行匹配分析。

重点分析：
1. 技能匹配
2. 项目经验
3. 技术栈匹配
4. 岗位要求覆盖程度
5. 缺失技能
6. 候选人的优势
7. 风险
8. 改进建议

可靠性约束（必须遵守）：
1. 只能根据候选人简历中明确存在的信息进行判断。
2. 不得虚构候选人的工作经历、项目经历、技能或证书。
3. 如果简历没有明确体现某项技能，应将其视为缺失或未知，放入 missingSkills，不得放入 matchedSkills。
4. advantages / risks / suggestions / summary 也必须基于简历与JD可观察的事实，禁止编造。

岗位名称：{job_name or "未提供"}
公司名称：{company_name or "未提供"}

岗位JD：
{job_description}

候选人简历：
{resume_content}

严格返回一个 JSON 对象，字段如下：
{{
  "matchScore": 86,
  "matchedSkills": ["技能1", "技能2"],
  "missingSkills": ["缺少技能1"],
  "advantages": ["优势1"],
  "risks": ["风险1"],
  "suggestions": ["建议1"],
  "summary": "总体评价"
}}

要求：
1. 只返回合法 JSON，不要 markdown，不要代码块，不要额外说明
2. matchScore 为 0-100 的整数
3. 数组字段没有内容时返回空数组
"""

    try:
        client = get_client()
        response = client.chat.completions.create(
            model=os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
            messages=[
                {
                    "role": "system",
                    "content": (
                        "你是一名专业的IT招聘顾问。"
                        "只能依据简历中明确写出的信息做判断，禁止虚构经历、技能或证书。"
                        "请始终只返回合法 JSON 对象，不要使用 markdown。"
                    )
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            response_format={
                "type": "json_object"
            },
            temperature=0.2
        )

        content = response.choices[0].message.content
        return json.loads(content)

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"AI岗位匹配失败：{str(e)}"
        )


class InterviewStartRequest(BaseModel):
    resumeContent: str
    position: str


class InterviewHistoryItem(BaseModel):
    role: str
    content: str


class InterviewAnswerRequest(BaseModel):
    resumeContent: str
    position: str
    history: list[InterviewHistoryItem] = []
    currentQuestion: str
    userAnswer: str


class InterviewReportRequest(BaseModel):
    resumeContent: str
    position: str
    history: list[InterviewHistoryItem] = []


def _chat_json(system: str, user: str) -> dict:
    try:
        client = get_client()
        response = client.chat.completions.create(
            model=os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            response_format={"type": "json_object"},
            temperature=0.3,
        )
        content = response.choices[0].message.content
        return json.loads(content)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI面试调用失败：{str(e)}")


def _format_history(history: list[InterviewHistoryItem]) -> str:
    if not history:
        return "（暂无历史对话）"
    lines = []
    for item in history:
        role = (item.role or "").strip().upper()
        label = "面试官" if role == "AI" else "候选人"
        lines.append(f"{label}：{(item.content or '').strip()}")
    return "\n".join(lines)


@app.post("/api/ai/interview/start")
def interview_start(req: InterviewStartRequest):
    resume_content = (req.resumeContent or "").strip()
    position = (req.position or "").strip()

    if not resume_content:
        raise HTTPException(status_code=400, detail="resumeContent 不能为空")
    if not position:
        raise HTTPException(status_code=400, detail="position 不能为空")

    prompt = f"""你是一名严格但友好的 IT 技术面试官，正在面试「{position}」岗位候选人。

根据候选人简历，生成第一道开场技术面试题。

约束：
1. 只能基于简历中明确出现的技术栈/项目出题，不得虚构简历没有的技能。
2. 第一题适合开场：可结合自我介绍或简历中的核心技术（如 Java / Spring Boot / MySQL）。
3. 只出一道题，问题清晰具体，便于口头回答。
4. 只返回合法 JSON，不要 markdown。

简历：
{resume_content}

返回：
{{
  "question": "第一道面试题"
}}
"""

    result = _chat_json(
        "你是专业的 IT 技术面试官。始终只返回合法 JSON，不要使用 markdown。",
        prompt,
    )
    question = str(result.get("question") or "").strip()
    if not question:
        raise HTTPException(status_code=500, detail="AI未返回有效面试题")
    return {"question": question}


@app.post("/api/ai/interview/answer")
def interview_answer(req: InterviewAnswerRequest):
    resume_content = (req.resumeContent or "").strip()
    position = (req.position or "").strip()
    current_question = (req.currentQuestion or "").strip()
    user_answer = (req.userAnswer or "").strip()

    if not resume_content:
        raise HTTPException(status_code=400, detail="resumeContent 不能为空")
    if not position:
        raise HTTPException(status_code=400, detail="position 不能为空")
    if not current_question:
        raise HTTPException(status_code=400, detail="currentQuestion 不能为空")
    if not user_answer:
        raise HTTPException(status_code=400, detail="userAnswer 不能为空")

    history_text = _format_history(req.history or [])
    user_rounds = sum(1 for h in (req.history or []) if (h.role or "").upper() == "USER")

    prompt = f"""你是一名严格但友好的 IT 技术面试官，岗位：{position}。

任务：
1. 简要点评候选人刚刚的回答（指出正确点与不足，不要人身攻击）。
2. 基于其回答进行有针对性的追问（追问要承接上一题，不要跳到无关话题）。
3. 决定是否继续面试。

规则：
1. 不得虚构候选人没有的经历或技能。
2. 追问应围绕 Java / Spring / MySQL / Redis 等与岗位相关的技术。
3. 若回答很空泛、答非所问，可追问更基础的概念。
4. 若已进行较多轮次（候选人已答约 5 轮及以上），或已覆盖足够维度，可将 shouldContinue 设为 false，nextQuestion 可为空字符串。
5. 当前候选人历史回答轮次约：{user_rounds}
6. 只返回合法 JSON，不要 markdown。

简历摘要（供参考）：
{resume_content[:3000]}

历史对话：
{history_text}

当前问题：
{current_question}

候选人回答：
{user_answer}

返回：
{{
  "evaluation": "对本次回答的简短点评",
  "nextQuestion": "下一道追问（若不继续可为空字符串）",
  "shouldContinue": true
}}
"""

    result = _chat_json(
        "你是专业的 IT 技术面试官，擅长根据回答追问。始终只返回合法 JSON。",
        prompt,
    )

    evaluation = str(result.get("evaluation") or "").strip()
    next_question = str(result.get("nextQuestion") or "").strip()
    should_continue = bool(result.get("shouldContinue", True))

    if user_rounds >= 5:
        should_continue = False

    if not should_continue:
        next_question = ""

    if should_continue and not next_question:
        raise HTTPException(status_code=500, detail="AI未返回下一题")

    return {
        "evaluation": evaluation or "已记录你的回答。",
        "nextQuestion": next_question,
        "shouldContinue": should_continue,
    }


def _sse_data(event: str, payload: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _parse_stream_answer_text(text: str, user_rounds: int) -> dict:
    """解析流式输出中的【点评】/【追问】/【继续】标记。"""
    raw = (text or "").strip()
    evaluation = ""
    next_question = ""
    should_continue = True

    if "【点评】" in raw:
        after = raw.split("【点评】", 1)[1]
        if "【追问】" in after:
            evaluation = after.split("【追问】", 1)[0].strip()
            rest = after.split("【追问】", 1)[1]
            if "【继续】" in rest:
                next_question = rest.split("【继续】", 1)[0].strip()
                flag = rest.split("【继续】", 1)[1].strip().lower()
                should_continue = flag.startswith("true") or flag.startswith("是")
            else:
                next_question = rest.strip()
        else:
            evaluation = after.strip()
    else:
        # 兜底：尝试按 JSON 解析
        try:
            cleaned = raw
            if cleaned.startswith("```"):
                cleaned = cleaned.strip("`")
                if cleaned.lower().startswith("json"):
                    cleaned = cleaned[4:].strip()
            obj = json.loads(cleaned)
            evaluation = str(obj.get("evaluation") or "").strip()
            next_question = str(obj.get("nextQuestion") or "").strip()
            should_continue = bool(obj.get("shouldContinue", True))
        except Exception:
            evaluation = raw or "已记录你的回答。"

    if user_rounds >= 5:
        should_continue = False

    if not should_continue:
        next_question = ""

    return {
        "evaluation": evaluation or "已记录你的回答。",
        "nextQuestion": next_question,
        "shouldContinue": should_continue,
    }


@app.post("/api/ai/interview/answer/stream")
def interview_answer_stream(req: InterviewAnswerRequest):
    """SSE 流式点评 + 追问。事件：delta / done / error"""
    resume_content = (req.resumeContent or "").strip()
    position = (req.position or "").strip()
    current_question = (req.currentQuestion or "").strip()
    user_answer = (req.userAnswer or "").strip()

    if not resume_content:
        raise HTTPException(status_code=400, detail="resumeContent 不能为空")
    if not position:
        raise HTTPException(status_code=400, detail="position 不能为空")
    if not current_question:
        raise HTTPException(status_code=400, detail="currentQuestion 不能为空")
    if not user_answer:
        raise HTTPException(status_code=400, detail="userAnswer 不能为空")

    history_text = _format_history(req.history or [])
    user_rounds = sum(1 for h in (req.history or []) if (h.role or "").upper() == "USER")

    prompt = f"""你是一名严格但友好的 IT 技术面试官，岗位：{position}。

任务：
1. 简要点评候选人刚刚的回答。
2. 基于回答给出下一道追问（若不继续可空）。
3. 决定是否继续面试。

规则：
1. 不得虚构候选人没有的经历或技能。
2. 追问围绕 Java / Spring / MySQL / Redis 等岗位相关技术。
3. 若已进行较多轮次（候选人已答约 5 轮及以上），【继续】写 false。
4. 当前候选人历史回答轮次约：{user_rounds}
5. 严格按照下面格式输出，不要 JSON，不要 markdown：

【点评】
（点评内容，可多行）
【追问】
（下一道追问；若不继续则留空）
【继续】
true

简历摘要（供参考）：
{resume_content[:3000]}

历史对话：
{history_text}

当前问题：
{current_question}

候选人回答：
{user_answer}
"""

    def event_generator():
        buffer = ""
        try:
            client = get_client()
            stream = client.chat.completions.create(
                model=os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
                messages=[
                    {
                        "role": "system",
                        "content": "你是专业的 IT 技术面试官。请严格按指定标记格式输出，不要使用 JSON。",
                    },
                    {"role": "user", "content": prompt},
                ],
                stream=True,
                temperature=0.3,
            )
            for chunk in stream:
                delta = ""
                if chunk.choices and chunk.choices[0].delta:
                    delta = chunk.choices[0].delta.content or ""
                if not delta:
                    continue
                buffer += delta
                yield _sse_data("delta", {"content": delta})

            result = _parse_stream_answer_text(buffer, user_rounds)
            if result["shouldContinue"] and not result["nextQuestion"]:
                yield _sse_data("error", {"message": "AI未返回下一题"})
                return
            yield _sse_data("done", result)
        except Exception as e:
            yield _sse_data("error", {"message": f"AI面试流式调用失败：{str(e)}"})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post("/api/ai/interview/report")
def interview_report(req: InterviewReportRequest):
    resume_content = (req.resumeContent or "").strip()
    position = (req.position or "").strip()

    if not resume_content:
        raise HTTPException(status_code=400, detail="resumeContent 不能为空")
    if not position:
        raise HTTPException(status_code=400, detail="position 不能为空")

    history_text = _format_history(req.history or [])

    prompt = f"""你是一名资深 IT 技术面试官，请根据完整面试记录为「{position}」岗位生成结构化面试报告。

约束：
1. 只能依据面试对话与简历中明确信息评分，不得虚构表现。
2. 分数均为 0-100 的整数。
3. weakPoints / suggestions 为字符串数组。
4. 只返回合法 JSON，不要 markdown。

简历：
{resume_content[:3000]}

完整面试记录：
{history_text}

返回：
{{
  "totalScore": 78,
  "javaScore": 80,
  "mysqlScore": 75,
  "redisScore": 70,
  "springScore": 82,
  "weakPoints": ["薄弱点1"],
  "suggestions": ["建议1"],
  "summary": "总体评价"
}}
"""

    result = _chat_json(
        "你是专业的 IT 面试官，负责生成结构化面试报告。始终只返回合法 JSON。",
        prompt,
    )

    def _score(key: str) -> int:
        try:
            value = int(result.get(key, 0))
        except (TypeError, ValueError):
            value = 0
        return max(0, min(100, value))

    def _list(key: str) -> list:
        value = result.get(key) or []
        if not isinstance(value, list):
            return []
        return [str(item).strip() for item in value if str(item).strip()]

    return {
        "totalScore": _score("totalScore"),
        "javaScore": _score("javaScore"),
        "mysqlScore": _score("mysqlScore"),
        "redisScore": _score("redisScore"),
        "springScore": _score("springScore"),
        "weakPoints": _list("weakPoints"),
        "suggestions": _list("suggestions"),
        "summary": str(result.get("summary") or "").strip(),
    }
