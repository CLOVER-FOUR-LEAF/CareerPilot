<#
    CareerPilot 本地环境 —— 启动基础设施（MySQL + Redis）

    幂等：已在监听的组件会被跳过。
#>

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'

$mysqlPort = [int](Get-Config 'MYSQL_PORT' '3307')
$redisPort = [int](Get-Config 'REDIS_PORT' '6379')
$redisPass = Get-Config 'REDIS_PASSWORD' ''
$dataRoot  = Get-DataDir
$redisHome = Join-Path $dataRoot 'redis'
$redisConf = Join-Path $redisHome 'redis.conf'

Write-Step "启动基础设施"

# ── MySQL ──────────────────────────────────────────────────
if (Test-PortInUse -Port $mysqlPort) {
    Write-Ok "MySQL 已在监听 $mysqlPort"
} else {
    $binDir = Find-MySqlBin
    if (-not $binDir) { Write-Err "找不到 mysqld.exe，请先执行 init-db.ps1 或安装 MySQL"; exit 1 }
    $myIni = Join-Path $dataRoot 'mysql\my.ini'
    if (-not (Test-Path $myIni)) {
        Write-Err "缺少 $myIni，请先执行：powershell -File scripts/local/init-db.ps1"
        exit 1
    }
    Start-Background -Name 'mysql' `
        -FilePath (Join-Path $binDir 'mysqld.exe') `
        -Arguments @("--defaults-file=$myIni") `
        -WorkingDirectory (Join-Path $dataRoot 'mysql')
    if (Wait-Port -Port $mysqlPort -TimeoutSec 90) {
        Write-Ok "MySQL 已监听 $mysqlPort"
    } else {
        Write-Err "MySQL 启动超时，错误日志：$(Join-Path $dataRoot 'mysql\mysql-error.log')"
        exit 1
    }
}

# ── Redis ──────────────────────────────────────────────────
if (Test-PortInUse -Port $redisPort) {
    Write-Ok "Redis 已在监听 $redisPort"
} else {
    $redisBin = Find-RedisBin
    if (-not $redisBin) {
        Write-Err "找不到 redis-server.exe"
        Write-Info "下载地址：https://github.com/redis-windows/redis-windows/releases"
        Write-Info "装好后把目录放进 PATH，或放到 D:\Tools\redis 下"
        exit 1
    }
    if (-not (Test-Path $redisHome)) { New-Item -ItemType Directory -Force -Path $redisHome | Out-Null }

    # Windows 版 Redis（msys2 构建）会把路径当成 POSIX 路径处理，
    # 所以配置里的路径一律用正斜杠，且启动时用「工作目录 + 相对配置名」的方式。
    $posixHome = $redisHome -replace '\\', '/'
    $conf = @"
port $redisPort
bind 127.0.0.1
protected-mode yes
daemonize no
dir $posixHome/data
logfile $posixHome/redis.log
databases 16
save 900 1
save 300 10
save 60 10000
maxmemory 512mb
maxmemory-policy noeviction
"@
    if ($redisPass) { $conf += "`nrequirepass $redisPass" }
    # Redis 的配置文件同样不能带 BOM
    [IO.File]::WriteAllText($redisConf, $conf, (New-Object Text.UTF8Encoding($false)))

    $rdata = Join-Path $redisHome 'data'
    if (-not (Test-Path $rdata)) { New-Item -ItemType Directory -Force -Path $rdata | Out-Null }

    Start-Background -Name 'redis' `
        -FilePath (Join-Path $redisBin 'redis-server.exe') `
        -Arguments @('redis.conf') `
        -WorkingDirectory $redisHome
    if (Wait-Port -Port $redisPort -TimeoutSec 30) {
        Write-Ok "Redis 已监听 $redisPort"
    } else {
        Write-Err "Redis 启动超时，日志：$(Join-Path $redisHome 'redis.log')"
        exit 1
    }
}

Write-Step "基础设施就绪"
Write-Info "MySQL : 127.0.0.1:$mysqlPort"
Write-Info "Redis : 127.0.0.1:$redisPort"
