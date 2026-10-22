<#
    CareerPilot 本地环境 —— 状态总览

    检查所有组件的进程与端口状态。
#>

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Continue'

# 名称 : 端口 : 进程名（PID 文件里的 key）
$components = @(
    @{ Group = '基础设施'; Name = 'MySQL';      Port = [int](Get-Config 'MYSQL_PORT' '3307'); Proc = 'mysql' },
    @{ Group = '基础设施'; Name = 'Redis';      Port = [int](Get-Config 'REDIS_PORT' '6379'); Proc = 'redis' },

    @{ Group = 'Java 微服务'; Name = 'gateway-service';   Port = 8080; Proc = 'svc-gateway-service' },
    @{ Group = 'Java 微服务'; Name = 'user-center';       Port = 8081; Proc = 'svc-user-center' },
    @{ Group = 'Java 微服务'; Name = 'resume-service';    Port = 8082; Proc = 'svc-resume-service' },
    @{ Group = 'Java 微服务'; Name = 'jd-service';        Port = 8083; Proc = 'svc-jd-service' },
    @{ Group = 'Java 微服务'; Name = 'match-service';     Port = 8084; Proc = 'svc-match-service' },
    @{ Group = 'Java 微服务'; Name = 'ai-task-scheduler'; Port = 8085; Proc = 'svc-ai-task-scheduler' },

    @{ Group = 'Python Agent'; Name = 'agent-orchestrator';     Port = 8001; Proc = 'agent-agent-orchestrator' },
    @{ Group = 'Python Agent'; Name = 'jd-parser-agent';        Port = 8002; Proc = 'agent-jd-parser-agent' },
    @{ Group = 'Python Agent'; Name = 'resume-analyzer-agent';  Port = 8003; Proc = 'agent-resume-analyzer-agent' },
    @{ Group = 'Python Agent'; Name = 'match-agent';            Port = 8004; Proc = 'agent-match-agent' },
    @{ Group = 'Python Agent'; Name = 'resume-optimizer-agent'; Port = 8005; Proc = 'agent-resume-optimizer-agent' },
    @{ Group = 'Python Agent'; Name = 'interview-agent';        Port = 8006; Proc = 'agent-interview-agent' },
    @{ Group = 'Python Agent'; Name = 'cover-letter-agent';     Port = 8007; Proc = 'agent-cover-letter-agent' },
    @{ Group = 'Python Agent'; Name = 'career-advisor-agent';   Port = 8008; Proc = 'agent-career-advisor-agent' },

    @{ Group = '前端'; Name = 'web (vite)'; Port = [int](Get-Config 'FRONTEND_PORT' '3000'); Proc = 'web' }
)

Write-Host ""
Write-Host "  CareerPilot · 本地运行状态" -ForegroundColor White
Write-Host "  ─────────────────────────────────────────────────────" -ForegroundColor DarkGray

$lastGroup = ''
$up = 0; $down = 0

foreach ($c in $components) {
    if ($c.Group -ne $lastGroup) {
        Write-Host ""
        Write-Host ("  [{0}]" -f $c.Group) -ForegroundColor Cyan
        $lastGroup = $c.Group
    }
    $listening = Test-PortInUse -Port $c.Port
    $alive = Get-ProcessAlive -Name $c.Proc
    $mark = if ($listening) { '●' } else { '○' }
    $color = if ($listening) { 'Green' } else { 'DarkGray' }
    $state = if ($listening) { '运行中' } else { '未启动' }
    Write-Host ("    {0} {1,-24} :{2,-6} {3}" -f $mark, $c.Name, $c.Port, $state) -ForegroundColor $color
    if ($listening) { $up++ } else { $down++ }
}

Write-Host ""
Write-Host "  ─────────────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ("  运行中 {0} / {1}" -f $up, $components.Count) -ForegroundColor $(if ($up -eq $components.Count) { 'Green' } else { 'Yellow' })
Write-Host ("  日志目录: {0}" -f (Get-LogDir)) -ForegroundColor DarkGray
Write-Host ""
