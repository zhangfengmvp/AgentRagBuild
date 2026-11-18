#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M5 离线权威评估脚本（双 RAGAS 之「Python 真 RAGAS」）。

流程：
  1. 读取问题集 JSON（默认 eval/questions.json，结构见 questions.sample.json）；
  2. 逐个问题调用本工程 M5 的 Agent 端点 POST /api/agent（非流式），拿到
     finalAnswer 与 OBSERVATION 步骤里的检索原文作为上下文（contexts）；
  3. 用真 ·RAGAS 库计算三项核心指标：faithfulness / answer_relevancy / context_relevancy；
  4. 输出评估报告 JSON（默认 eval/report.json）并打印汇总。

与 Java 侧 LLMJudgeService（/api/eval，轻量即时代理分）互补：
  - Java 侧：每次问答后即时、低成本打分，做实时反馈；
  - Python 侧：离线、接入真 RAGAS，做权威评估与回归对比。

依赖（可选，缺则脚本仅导出采集数据并给出安装提示）：
  pip install ragas langchain-openai datasets openai

环境变量（也可用命令行参数覆盖）：
  DASHSCOPE_API_KEY      百炼 API Key（必填，用于 RAGAS 调用的 LLM / Embedding）
  RAGAS_BASE_URL         OpenAI 兼容 chat 端点（默认本工作区 /compatible-mode/v1）
  RAGAS_EMBED_BASE_URL   原生 embedding 端点（默认网关根地址 /v1）
  RAGAS_CHAT_MODEL       chat 模型（默认 qwen3.8-27b）
  RAGAS_EMBED_MODEL      embedding 模型（默认 qwen3.7-text-embedding-flash）
  AGENT_API_URL         Spring Agent 端点（默认 http://localhost:8083/api/agent）
"""
import argparse
import json
import os
import sys
import time

try:
    import requests
except ImportError:
    sys.exit("请先安装 requests：pip install requests")


def load_questions(path):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    # 支持 [ {question}, ... ] 或 { questions: [ ... ] }
    if isinstance(data, dict):
        data = data.get("questions", [])
    return data


def call_agent(api_url, question, timeout=120):
    """调用 M5 Agent 非流式端点，返回 (finalAnswer, contexts)。"""
    resp = requests.post(api_url, json={"question": question}, timeout=timeout)
    resp.raise_for_status()
    body = resp.json()
    final_answer = body.get("finalAnswer", "")
    contexts = []
    for step in body.get("steps", []):
        if step.get("phase") == "OBSERVATION" and step.get("output"):
            contexts.append(step["output"])
    # 若没有 OBSERVATION，退而用 citations 文本兜底（citations 仅有来源，无正文）
    return final_answer, contexts


def build_dataset(items):
    """构造 ragas 需要的 dataset 字典。"""
    questions, answers, contexts = [], [], []
    for it in items:
        questions.append(it["question"])
        answers.append(it["answer"])
        contexts.append(it["contexts"])
    return {"question": questions, "answer": answers, "contexts": contexts}


def run_ragas(dataset, args):
    """调用真 RAGAS 评估，返回 {metric: [scores]} 或抛异常。"""
    # 延迟导入，缺失时由调用方捕获并友好提示
    from datasets import Dataset
    from langchain_openai import ChatOpenAI, OpenAIEmbeddings

    chat = ChatOpenAI(
        model=args.chat_model,
        api_key=os.environ["DASHSCOPE_API_KEY"],
        base_url=args.base_url,
        temperature=0,
    )
    embeddings = OpenAIEmbeddings(
        model=args.embed_model,
        api_key=os.environ["DASHSCOPE_API_KEY"],
        base_url=args.embed_base_url,
    )

    # 兼容 ragas 0.1 / 0.2 的指标导入
    metrics = []
    try:
        from ragas.metrics import faithfulness, answer_relevancy, context_relevancy
        metrics = [faithfulness, answer_relevancy, context_relevancy]
    except ImportError:
        from ragas.metrics import Faithfulness, AnswerRelevancy, ContextRelevancy
        metrics = [Faithfulness(), AnswerRelevancy(), ContextRelevancy()]

    from ragas import evaluate
    ds = Dataset.from_dict(dataset)
    result = evaluate(ds, metrics=metrics, llm=chat, embeddings=embeddings)
    return result


def main():
    p = argparse.ArgumentParser(description="M5 RAGAS 离线评估")
    p.add_argument("--questions", default=os.path.join(os.path.dirname(__file__), "questions.json"))
    p.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "report.json"))
    p.add_argument("--api-url", default=os.environ.get("AGENT_API_URL", "http://localhost:8083/api/agent"))
    p.add_argument("--base-url", default=os.environ.get(
        "RAGAS_BASE_URL",
        "https://ws-garuvggx9tyw15t4.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"))
    p.add_argument("--embed-base-url", default=os.environ.get(
        "RAGAS_EMBED_BASE_URL",
        "https://ws-garuvggx9tyw15t4.cn-beijing.maas.aliyuncs.com/v1"))
    p.add_argument("--chat-model", default=os.environ.get("RAGAS_CHAT_MODEL", "qwen3.8-27b"))
    p.add_argument("--embed-model", default=os.environ.get("RAGAS_EMBED_MODEL", "qwen3.7-text-embedding-flash"))
    p.add_argument("--no-ragas", action="store_true", help="只采集数据，不跑 RAGAS（ragas 未安装时用）")
    args = p.parse_args()

    questions = load_questions(args.questions)
    if not questions:
        sys.exit(f"问题集为空：{args.questions}")

    print(f"[1/3] 调用 Agent 端点采集答案与上下文：{args.api_url}")
    items = []
    for q in questions:
        text = q if isinstance(q, str) else q.get("question", "")
        if not text:
            continue
        try:
            answer, contexts = call_agent(args.api_url, text)
        except Exception as e:
            print(f"  ! 调用失败：{text[:30]}… 原因：{e}")
            answer, contexts = "", []
        items.append({"question": text, "answer": answer, "contexts": contexts})
        print(f"  ✓ {text[:40]}{'…' if len(text) > 40 else ''}  上下文数={len(contexts)} 答案长={len(answer)}")

    dataset = build_dataset(items)

    if args.no_ragas or "DASHSCOPE_API_KEY" not in os.environ:
        print("[2/3] 跳过 RAGAS（缺 DASHSCOPE_API_KEY 或指定 --no-ragas），仅导出采集数据。")
        out = {"items": items, "note": "未运行 RAGAS，设置 DASHSCOPE_API_KEY 并去掉 --no-ragas 以计算指标"}
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f"[3/3] 已写出采集数据：{args.out}")
        return

    print("[2/3] 调用真 RAGAS 计算指标…")
    try:
        result = run_ragas(dataset, args)
    except ImportError as e:
        print(f"  ! 未安装 RAGAS 相关依赖（{e}）。")
        print("    安装：pip install ragas langchain-openai datasets openai")
        out = {"items": items, "note": "ragas 未安装，仅导出采集数据"}
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f"[3/3] 已写出采集数据：{args.out}")
        return
    except Exception as e:
        print(f"  ! RAGAS 执行失败：{e}")
        out = {"items": items, "error": str(e)}
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        return

    scores = result.scores if hasattr(result, "scores") else result
    print("[3/3] RAGAS 评估报告：")
    for k, v in (scores.items() if isinstance(scores, dict) else {}):
        print(f"  {k}: {v}")
    out = {
        "summary": {k: (v if not isinstance(v, list) else {
            "mean": round(sum(v) / len(v), 4) if v else None,
            "values": [round(x, 4) for x in v],
        }) for k, v in (scores.items() if isinstance(scores, dict) else {})},
        "items": items,
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"已写出报告：{args.out}")


if __name__ == "__main__":
    main()
