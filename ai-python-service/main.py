import json
import os

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
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
