<#
    CareerPilot 本地环境 —— 为 8 个 Python Agent 创建虚拟环境并安装依赖

    幂等：已装好依赖的 Agent 会跳过（依据 .venv\.installed 标记文件）。
    用法：
        powershell -File scripts/local/setup-python-envs.ps1
        powershell -File scripts/local/setup-python-envs.ps1 -Force   # 强制重装
#>

param(
    [switch]$Force
)

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'
$repo = Get-RepoRoot

$agents = @(
    'agent-orchestrator',
    'jd-parser-agent',
    'resume-analyzer-agent',
    'match-agent',
    'resume-optimizer-agent',
    'interview-agent',
    'cover-letter-agent',
    'career-advisor-agent'
)

Write-Step "准备 Python 虚拟环境（$($agents.Count) 个 Agent）"

$python = (Get-Command python -ErrorAction SilentlyContinue)
if (-not $python) { Write-Err "找不到 python，请先安装 Python 3.11+"; exit 1 }
Write-Ok "Python: $($python.Source)"

foreach ($agent in $agents) {
    $dir = Join-Path $repo "agents\$agent"
    if (-not (Test-Path $dir)) { Write-Err "找不到 Agent 目录：$dir"; continue }

    $venv = Join-Path $dir '.venv'
    $venvPy = Join-Path $venv 'Scripts\python.exe'
    $stamp = Join-Path $venv '.installed'

    if ((Test-Path $venvPy) -and (Test-Path $stamp) -and (-not $Force)) {
        Write-Ok "$agent 依赖已就绪，跳过"
        continue
    }

    Write-Info "$agent —— 创建虚拟环境并安装依赖"
    if (-not (Test-Path $venvPy)) {
        & $python.Source -m venv $venv
        if ($LASTEXITCODE -ne 0) { Write-Err "$agent venv 创建失败"; continue }
    }

    $req = Join-Path $dir 'requirements.txt'
    if (-not (Test-Path $req)) { Write-Err "$agent 缺少 requirements.txt"; continue }

    & $venvPy -m pip install --quiet --upgrade pip
    & $venvPy -m pip install --quiet -r $req
    if ($LASTEXITCODE -ne 0) {
        Write-Err "$agent 依赖安装失败"
        Get-Content (Join-Path (Get-LogDir) "pip-$agent.log") -Tail 10 -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Info $_ }
        continue
    }
    Set-Content -Path $stamp -Value (Get-Date -Format 's') -Encoding ascii
    Write-Ok "$agent 依赖安装完成"
}

Write-Step "Python 环境准备完成"
