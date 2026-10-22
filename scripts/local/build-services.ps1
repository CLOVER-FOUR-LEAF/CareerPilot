<#
    CareerPilot 本地环境 —— 构建 6 个 Java 微服务

    用法：
        powershell -File scripts/local/build-services.ps1
        powershell -File scripts/local/build-services.ps1 -SkipTests
#>

param(
    [switch]$SkipTests
)

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'
$repo = Get-RepoRoot
$servicesDir = Join-Path $repo 'services'

Write-Step "构建 Java 微服务"

$tools = Get-JavaTool
if (-not $tools.Maven) {
    Write-Err "找不到 mvn。请安装 Maven 3.9+ 并设置 MAVEN_HOME，或把 mvn.cmd 加进 PATH。"
    exit 1
}
if (-not $tools.Java) {
    Write-Err "找不到 java。请安装 JDK 21+ 并设置 JAVA_HOME。"
    exit 1
}
Write-Ok "Maven : $($tools.Maven)"
Write-Ok "Java  : $($tools.Java)"

Push-Location $servicesDir
try {
    $args = @('clean', 'package')
    if ($SkipTests) { $args += '-DskipTests' }

    Write-Info "mvn $($args -join ' ')（首次会下载依赖，约 5–15 分钟）"
    & $tools.Maven @args
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -ne 0) {
    Write-Err "构建失败（exit $code）"
    exit 1
}

$targets = @(
    'gateway-service',
    'user-center',
    'resume-service',
    'jd-service',
    'match-service',
    'ai-task-scheduler'
)
$missing = @()
foreach ($t in $targets) {
    $jar = Get-ChildItem (Join-Path $servicesDir "$t\target") -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if ($jar) {
        Write-Ok ("{0,-20} {1}" -f $t, $jar.Name)
    } else {
        $missing += $t
        Write-Err "$t 未产出 jar"
    }
}

if ($missing.Count -gt 0) {
    Write-Err "以下服务缺少构建产物：$($missing -join ', ')"
    exit 1
}

Write-Step "构建完成"
