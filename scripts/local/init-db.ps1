<#
    CareerPilot 本地环境 —— 初始化 MySQL（一次性）

    在 .runtime 之外建一个**项目专用**的 MySQL 实例：
      * 数据目录：CAREERPILOT_DATA_DIR\mysql\data
      * 端口：MYSQL_PORT（默认 3307，避开你机器上已有的 3306）
      * 建库 careerpilot，建账号 careerpilot/cp123456
      * 导入 db/migrations/V1__init_schema.sql

    幂等：重复执行不会重复建表；已初始化的数据目录会直接复用。
#>

. "$PSScriptRoot/common.ps1"
Import-LocalEnv | Out-Null

$ErrorActionPreference = 'Stop'

$port       = [int](Get-Config 'MYSQL_PORT' '3307')
$appUser    = Get-Config 'MYSQL_USER' 'careerpilot'
$appPass    = Get-Config 'MYSQL_PASSWORD' 'cp123456'
$rootUser   = Get-Config 'MYSQL_ROOT_USER' 'root'
$rootPass   = Get-Config 'MYSQL_ROOT_PASSWORD' 'root123456'
$dataRoot   = Get-DataDir
$mysqlHome  = Join-Path $dataRoot 'mysql'
$dataDir    = Join-Path $mysqlHome 'data'
$myIni      = Join-Path $mysqlHome 'my.ini'
$schemaFile = Join-Path (Get-RepoRoot) 'db\migrations\V1__init_schema.sql'

Write-Step "初始化项目专用 MySQL 实例"

# ── 1. 定位 mysqld ──────────────────────────────────────────
$binDir = Find-MySqlBin
if (-not $binDir) {
    Write-Err "找不到 mysqld.exe / mysql.exe"
    Write-Info "请安装 MySQL 8.x，或在 scripts/local/.env 里把 MySQL 相关配置指向你已有的实例"
    exit 1
}
$mysqld  = Join-Path $binDir 'mysqld.exe'
$mysql   = Join-Path $binDir 'mysql.exe'
$basedir = Split-Path $binDir -Parent
Write-Ok "mysqld : $mysqld"
Write-Ok "basedir: $basedir"

if (-not (Test-Path $schemaFile)) {
    Write-Err "找不到 schema 文件：$schemaFile"
    exit 1
}

# ── 2. 数据目录与配置 ──────────────────────────────────────
if (-not (Test-Path $mysqlHome)) { New-Item -ItemType Directory -Force -Path $mysqlHome | Out-Null }

$iniContent = @"
[client]
port = $port
default-character-set = utf8mb4

[mysqld]
basedir = $basedir
datadir = $dataDir
port = $port
bind-address = 127.0.0.1
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
default-time-zone = '+08:00'
max_connections = 300
innodb_buffer_pool_size = 256M
log-error = $(Join-Path $mysqlHome 'mysql-error.log')
pid-file = $(Join-Path $mysqlHome 'mysql.pid')

[mysql]
default-character-set = utf8mb4
"@
# MySQL 的 ini 解析器不认 UTF-8 BOM，会报 "Found option without preceding group"，
# 所以用 .NET 无 BOM 写入
[IO.File]::WriteAllText($myIni, $iniContent, (New-Object Text.UTF8Encoding($false)))
Write-Ok "my.ini 已生成：$myIni"

# ── 3. 首次初始化数据目录 ──────────────────────────────────
$needInit = $true
if (Test-Path $dataDir) {
    $entries = @(Get-ChildItem $dataDir -Force -ErrorAction SilentlyContinue)
    $needInit = ($entries.Count -eq 0)
}
if ($needInit) {
    Write-Step "初始化数据目录（首次，约 10–30 秒）"
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    $initLog = Join-Path (Get-LogDir) 'mysql-init.log'
    $p = Start-Process -FilePath $mysqld `
        -ArgumentList "--defaults-file=$myIni", '--initialize-insecure', '--console' `
        -RedirectStandardOutput $initLog -RedirectStandardError "$initLog.err" `
        -WindowStyle Hidden -Wait -PassThru
    if ($p.ExitCode -ne 0) {
        Write-Err "mysqld --initialize-insecure 失败（exit $($p.ExitCode)），日志：$initLog"
        Get-Content "$initLog.err" -Tail 15 -ErrorAction SilentlyContinue | ForEach-Object { Write-Info $_ }
        exit 1
    }
    Write-Ok "数据目录初始化完成"
} else {
    Write-Ok "数据目录已存在，跳过初始化"
}

# ── 4. 启动实例 ────────────────────────────────────────────
if (Test-PortInUse -Port $port) {
    Write-Ok "端口 $port 已在监听（实例可能已启动）"
} else {
    Write-Step "启动 mysqld"
    Start-Background -Name 'mysql' -FilePath $mysqld `
        -Arguments @("--defaults-file=$myIni") `
        -WorkingDirectory $mysqlHome
    if (-not (Wait-Port -Port $port -TimeoutSec 90)) {
        Write-Err "MySQL 启动超时，错误日志：$(Join-Path $mysqlHome 'mysql-error.log')"
        Get-Content (Join-Path $mysqlHome 'mysql-error.log') -Tail 20 -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Info $_ }
        exit 1
    }
    Write-Ok "MySQL 已监听 $port"
}

# ── 5. root 密码 / 建库 / 建账号 ───────────────────────────
function Invoke-Db {
    <# 执行 SQL；-Raw 时返回原始文本 #>
    param([string]$Sql, [string]$User, [string]$Password, [switch]$Raw)
    $args = @("-h", '127.0.0.1', "-P", $port, '-u', $User, '--default-character-set=utf8mb4', '--batch', '--skip-column-names')
    if ($Password) { $args += "-p$Password" }
    $args += @('-e', $Sql)
    # mysql 客户端会把 warning 写到 stderr，这里临时放宽 ErrorAction 以免误判为异常
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $mysql @args 2>&1 | Out-String
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($code -ne 0) { throw "SQL 执行失败：$out" }
    if ($Raw) { return $out }
    return $null
}

Write-Step "配置 root 密码与业务账号"

function Get-DbInt {
    <# 从 mysql 输出里取最后一个纯数字行（避开 warning 噪声） #>
    param([string]$Text)
    if (-not $Text) { return 0 }
    $lines = $Text -split "`r?`n" | Where-Object { $_.Trim() -match '^\d+$' }
    if ($lines) { return [int]($lines | Select-Object -Last 1) }
    return 0
}

# 第一次连接：root 空密码（刚初始化）或已有密码（复用已有数据目录）
$rootAuthed = $false
try {
    Invoke-Db -Sql 'SELECT 1;' -User $rootUser -Password '' -Raw | Out-Null
    $rootAuthed = $true
    Write-Ok "root 空密码可用（首次初始化）"
} catch {
    try {
        Invoke-Db -Sql 'SELECT 1;' -User $rootUser -Password $rootPass -Raw | Out-Null
        $rootAuthed = $true
        Write-Ok "root 密码可用（复用已有数据目录）"
    } catch {
        Write-Err "无法以 root 登录 MySQL：$($_.Exception.Message)"
        exit 1
    }
}

if ($rootAuthed) {
    # 设 root 密码（空密码时）
    try {
        Invoke-Db -Sql "ALTER USER '$rootUser'@'localhost' IDENTIFIED BY '$rootPass';" -User $rootUser -Password '' | Out-Null
        Write-Ok "root 密码已设置为 .env 中的值"
    } catch {
        Write-Info "root 密码已设置过，跳过"
    }
}

# 后续所有操作统一用 root + 密码
$rootArgs = @("-h", '127.0.0.1', "-P", $port, '-u', $rootUser, "-p$rootPass", '--default-character-set=utf8mb4')

Invoke-Db -Sql "CREATE DATABASE IF NOT EXISTS careerpilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" -User $rootUser -Password $rootPass | Out-Null
Write-Ok "数据库 careerpilot 就绪"

$grant = "CREATE USER IF NOT EXISTS '$appUser'@'%' IDENTIFIED BY '$appPass';"
Invoke-Db -Sql $grant -User $rootUser -Password $rootPass | Out-Null
Invoke-Db -Sql "ALTER USER '$appUser'@'%' IDENTIFIED BY '$appPass';" -User $rootUser -Password $rootPass | Out-Null
Invoke-Db -Sql "GRANT ALL PRIVILEGES ON careerpilot.* TO '$appUser'@'%'; FLUSH PRIVILEGES;" -User $rootUser -Password $rootPass | Out-Null
Write-Ok "账号 $appUser 已就绪"

# root 也允许从 127.0.0.1 直连（便于用 -h 127.0.0.1 排查问题）
try {
    Invoke-Db -Sql "CREATE USER IF NOT EXISTS '$rootUser'@'127.0.0.1' IDENTIFIED BY '$rootPass'; GRANT ALL PRIVILEGES ON *.* TO '$rootUser'@'127.0.0.1' WITH GRANT OPTION; FLUSH PRIVILEGES;" -User $rootUser -Password $rootPass | Out-Null
} catch {
    Write-Info "root@127.0.0.1 账号创建跳过：$($_.Exception.Message)"
}

# ── 6. 导入 schema ─────────────────────────────────────────
Write-Step "导入 db/migrations/V1__init_schema.sql"
$tableCount = Invoke-Db -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='careerpilot';" -User $appUser -Password $appPass -Raw
$existing = Get-DbInt $tableCount
Write-Info "现有表数量：$existing"

if ($existing -ge 20) {
    Write-Ok "已存在 $existing 张表，跳过导入（需要重置请先清空 careerpilot 库）"
} else {
    # 用 mysql 客户端的 source 命令导入文件（UTF-8，指定库名）
    $importLog = Join-Path (Get-LogDir) 'mysql-import.log'
    $sourcePath = $schemaFile -replace '\\', '/'
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $mysql -h 127.0.0.1 -P $port -u $appUser "-p$appPass" --default-character-set=utf8mb4 --batch careerpilot `
            -e "source $sourcePath" 2>&1 | Tee-Object -FilePath $importLog | Out-Null
        $importCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if ($importCode -ne 0) {
        Write-Err "schema 导入失败，错误日志：$importLog"
        Get-Content $importLog -Tail 15 -ErrorAction SilentlyContinue | ForEach-Object { Write-Info $_ }
        exit 1
    }
    $after = Invoke-Db -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='careerpilot';" -User $appUser -Password $appPass -Raw
    $n = Get-DbInt $after
    Write-Ok "schema 导入完成，现有表 $n 张"
}

Write-Step "MySQL 初始化完成"
Write-Info "地址     : 127.0.0.1:$port"
Write-Info "库名     : careerpilot"
Write-Info "业务账号 : $appUser / $appPass"
