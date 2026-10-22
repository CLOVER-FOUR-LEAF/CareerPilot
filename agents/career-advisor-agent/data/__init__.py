"""
CareerPilot 内置数据包。

放在 Agent 根目录下（而非 app/ 内），是因为 retriever / advisor 用
``from data.knowledge_base import ...`` 这种顶层包路径导入它，
``app/main.py`` 会把 Agent 根目录塞进 ``sys.path`` 以保证两种运行方式都能找到。
"""
