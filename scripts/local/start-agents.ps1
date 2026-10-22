<#
    CareerPilot 本地环境 —— 启动 8 个 Python Agent

    每个 Agent 一个 uvicorn 进程，监听 8001–8008。
    日志：.runtime/logs/<agent>.out.log
#>

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'
$repo = Get-RepoRoot

# 目录名 : 端口
$agents = [ordered]@{
    'agent-orchestrator'    = 8001
    'jd-parser-agent'       = 8002
    'resume-analyzer-agent' = 8003
    'match-agent'           = 8004
    'resume-optimizer-agent' = 8005
    'interview-agent'       = 8006
    'cover-letter-agent'    = 8007
    'career-advisor-agent'  = 8008
}

Write-Step "启动 Python Agent 层"

$ok = 0
foreach ($agent in $agents.Keys) {
    $port = $agents[$agent]
    $dir = Join-Path $repo "agents\$agent"
    $venvPy = Join-Path $dir '.venv\Scripts\python.exe'

    if (-not (Test-Path $venvPy)) {
        Write-Warn "$agent 缺少虚拟环境，跳过（先跑 setup-python-envs.ps1）"
        continue
    }

    Start-Background -Name "agent-$agent" `
        -FilePath $venvPy `
        -Arguments @('-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', "$port") `
        -WorkingDirectory $dir `
        -Port $port -WaitSec 45

    if (Test-PortInUse -Port $port) { $ok++ }
}

Write-Step "Agent 层启动完成（$ok/$($agents.Count) 已监听）"
Write-Info "编排服务 OpenAPI : http://localhost:8001/docs"
Write-Info "匹配 Agent OpenAPI: http://localhost:8004/docs"
