<#
    CareerPilot 本地环境 —— 一键启动全栈

    顺序：基础设施 → Agent 层 → Java 微服务 → 前端
    每个步骤都可单独执行，本脚本只是把它们串起来。
#>

param(
    [switch]$SkipPythonSetup,
    [switch]$SkipBuild,
    [switch]$SkipFrontend
)

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'
$repo = Get-RepoRoot
$sw = [Diagnostics.Stopwatch]::StartNew()

Write-Host ""
Write-Host "  CareerPilot · 本地一键启动" -ForegroundColor White
Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""

# ── 0. Python 环境（首次会装依赖）────────────────────────────
if (-not $SkipPythonSetup) {
    $setup = Join-Path $PSScriptRoot 'setup-python-envs.ps1'
    & $setup
}

# ── 1. 基础设施 ─────────────────────────────────────────────
# 注意：这里不能用 $LASTEXITCODE —— 它只有执行**外部程序**后才有值，
# 调用 .ps1 脚本不会设置它，在 StrictMode 下还会直接抛错。
# 判断脚本是否成功用 $?（上一条命令是否成功）。
& (Join-Path $PSScriptRoot 'start-infra.ps1')
if (-not $?) { Write-Err "基础设施启动失败，终止"; exit 1 }

# ── 2. Agent 层 ─────────────────────────────────────────────
& (Join-Path $PSScriptRoot 'start-agents.ps1')

# ── 3. Java 微服务（需要先构建）──────────────────────────────
if (-not $SkipBuild) {
    $jar = Get-ChildItem (Join-Path $repo 'services\user-center\target') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if (-not $jar) {
        Write-Step "未发现构建产物，先构建 Java 服务"
        & (Join-Path $PSScriptRoot 'build-services.ps1') -SkipTests
    }
}
& (Join-Path $PSScriptRoot 'start-services.ps1')

# ── 4. 前端 ─────────────────────────────────────────────────
if (-not $SkipFrontend) {
    & (Join-Path $PSScriptRoot 'start-frontend.ps1')
}

Write-Host ""
Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ("  启动完成，用时 {0} 秒" -f [math]::Round($sw.Elapsed.TotalSeconds, 1)) -ForegroundColor Green
Write-Host ""
& (Join-Path $PSScriptRoot 'status.ps1')
