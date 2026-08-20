from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()


class ResumeRequest(BaseModel):
    content: str


@app.post("/api/ai/resume/analyze")
def analyze_resume(req: ResumeRequest):

    text = req.content

    skills = []

    skill_list = [
        "Java",
        "Spring",
        "Spring Boot",
        "MySQL",
        "Redis",
        "Vue",
        "Python",
        "Docker",
        "Git"
    ]

    for skill in skill_list:
        if skill.lower() in text.lower():
            skills.append(skill)

    return {
        "name": "待识别",
        "skills": skills,
        "score": len(skills) * 10,
        "suggestions": [
            "建议增加项目经验描述",
            "建议增加Redis技能"
        ]
    }
