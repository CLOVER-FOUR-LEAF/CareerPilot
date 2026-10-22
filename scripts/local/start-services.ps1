<#
    CareerPilot 本地环境 —— 启动 6 个 Java 微服务

    日志：.runtime/logs/<service>.out.log
    工作目录：services/<service>/（STORAGE_ROOT 等相对路径以此为基准）
#>

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'
$repo = Get-RepoRoot
$servicesDir = Join-Path $repo 'services'

# 服务名 : 端口
$services = [ordered]@{
    'user-center'      = 8081
    'resume-service'   = 8082
    'jd-service'       = 8083
    'match-service'    = 8084
    'ai-task-scheduler' = 8085
    'gateway-service'  = 8080
}

Write-Step "启动 Java 微服务"

$tools = Get-JavaTool
if (-not $tools.Java) { Write-Err "找不到 java，请安装 JDK 21+ 并设置 JAVA_HOME"; exit 1 }

# 简历文件统一存到数据目录，避免散落在各服务的工作目录里
$storageRoot = Join-Path (Get-DataDir) 'uploads'
if (-not (Test-Path $storageRoot)) { New-Item -ItemType Directory -Force -Path $storageRoot | Out-Null }
[Environment]::SetEnvironmentVariable('STORAGE_ROOT', $storageRoot, 'Process')

$ok = 0
foreach ($svc in $services.Keys) {
    $port = $services[$svc]
    $dir = Join-Path $servicesDir $svc

    $jar = Get-ChildItem (Join-Path $dir 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if (-not $jar) {
        Write-Warn "$svc 尚未构建（无 jar），跳过。先执行 build-services.ps1"
        continue
    }

    Start-Background -Name "svc-$svc" `
        -FilePath $tools.Java `
        -Arguments @('-jar', $jar.FullName) `
        -WorkingDirectory $dir `
        -Port $port -WaitSec 90

    if (Test-PortInUse -Port $port) { $ok++ }
}

Write-Step "Java 微服务启动完成（$ok/$($services.Count) 已监听）"
Write-Info "网关     : http://localhost:8080"
Write-Info "健康检查 : http://localhost:8080/actuator/health"
