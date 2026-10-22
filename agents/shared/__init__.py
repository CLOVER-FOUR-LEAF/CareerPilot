"""
CareerPilot Agent 共享层。

这里放**跨 Agent 复用**的代码（目前是 LLM 客户端）。
刻意不放业务逻辑 —— 业务逻辑属于各自的 Agent 包。

各 Agent 的虚拟环境是独立的，所以引入方式是把仓库的 ``agents/`` 目录
加进 ``sys.path`` 后按包导入，见各 Agent 的 ``app/llm.py``。
"""
