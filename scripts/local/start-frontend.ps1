<#
    CareerPilot 本地环境 —— 启动前端

    默认：vite dev server（热更新），/api 自动代理到 http://localhost:8080
    -Build ：先构建静态产物再起 preview（静态产物模式）

    注意：这里直接用 node 跑 node_modules 里的 vite / vue-tsc 入口，
    不走 npm.cmd。npm.cmd 靠 %~dp0 定位自身脚本，被 cmd 启动器的工作目录一带，
    会去 web\node_modules\npm\bin\npm-cli.js 里找 —— 那个路径不存在。
#>

param(
    [switch]$Build
)

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'
$repo = Get-RepoRoot
$webDir = Join-Path $repo 'web'
$port = [int](Get-Config 'FRONTEND_PORT' '3000')

$nodeExe = (Get-Command node -ErrorAction SilentlyContinue)
if (-not $nodeExe) { Write-Err "找不到 node，请安装 Node.js 20+"; exit 1 }

$viteBin = Join-Path $webDir 'node_modules\vite\bin\vite.js'
$tscBin = Join-Path $webDir 'node_modules\vue-tsc\bin\vue-tsc.js'

Write-Step "启动前端"

if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
    Write-Warn "前端依赖未安装，正在执行 npm install（首次约 3–5 分钟）"
    Push-Location $webDir
    try { & npm install --no-audit --no-fund } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Err "npm install 失败"; exit 1 }
}

if (-not (Test-Path $viteBin)) {
    Write-Err "找不到 $viteBin，请先执行 npm install"
    exit 1
}

if ($Build) {
    Write-Info "构建静态产物（vue-tsc + vite build）…"
    Push-Location $webDir
    try {
        & $nodeExe.Source $tscBin
        if ($LASTEXITCODE -ne 0) { Write-Err "vue-tsc 类型检查失败"; exit 1 }
        & $nodeExe.Source $viteBin build
        if ($LASTEXITCODE -ne 0) { Write-Err "vite build 失败"; exit 1 }
    } finally { Pop-Location }

    Start-Background -Name 'web' -FilePath $nodeExe.Source `
        -Arguments @($viteBin, 'preview', '--port', "$port") `
        -WorkingDirectory $webDir -Port $port -WaitSec 60
    Write-Ok "前端（preview）: http://localhost:$port"
} else {
    Start-Background -Name 'web' -FilePath $nodeExe.Source `
        -Arguments @($viteBin, '--port', "$port", '--host', '127.0.0.1') `
        -WorkingDirectory $webDir -Port $port -WaitSec 90
    Write-Ok "前端（dev）    : http://localhost:$port"
}
Write-Info "接口代理        : /api → http://localhost:8080"
