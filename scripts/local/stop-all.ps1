<#
    CareerPilot 本地环境 —— 一键停止全栈

    按启动的逆序停：前端 → Java 微服务 → Agent → 基础设施
#>

param(
    [switch]$KeepInfra
)

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Continue'

Write-Step "停止 CareerPilot 本地环境"

# 前端
Stop-Background -Name 'web'

# Java 微服务（逆序停，先停网关避免继续进流量）
$svcOrder = @('svc-gateway-service', 'svc-ai-task-scheduler', 'svc-match-service',
    'svc-jd-service', 'svc-resume-service', 'svc-user-center')
foreach ($s in $svcOrder) { Stop-Background -Name $s }

# Agent 层
$agents = @('agent-agent-orchestrator', 'agent-jd-parser-agent', 'agent-resume-analyzer-agent',
    'agent-match-agent', 'agent-resume-optimizer-agent', 'agent-interview-agent',
    'agent-cover-letter-agent', 'agent-career-advisor-agent')
foreach ($a in $agents) { Stop-Background -Name $a }

# 基础设施
if (-not $KeepInfra) {
    Stop-Background -Name 'redis'
    Stop-Background -Name 'mysql'
} else {
    Write-Info "按要求保留 MySQL / Redis"
}

Write-Step "已停止"
