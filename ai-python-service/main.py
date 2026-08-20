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
