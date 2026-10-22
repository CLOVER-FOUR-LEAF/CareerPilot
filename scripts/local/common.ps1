<#
    CareerPilot 本地运行脚本 —— 公共函数库

    所有脚本通过 dot-sourcing 引入：
        . "$PSScriptRoot/common.ps1"
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── 路径 ────────────────────────────────────────────────────

function Get-RepoRoot {
    <# 仓库根目录：scripts/local 的上两级 #>
    return (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Get-RuntimeDir {
    <# 运行时目录：日志、PID 文件等。不进版本库。 #>
    $dir = Join-Path (Get-RepoRoot) '.runtime'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    return $dir
}

function Get-LogDir {
    $dir = Join-Path (Get-RuntimeDir) 'logs'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    return $dir
}

# ── 配置读取 ────────────────────────────────────────────────

function Import-LocalEnv {
    <#
        读取 scripts/local/.env（不存在则回落到 .env.example），
        并把键值写入当前进程的 $env:，供 JVM / Python / Redis 直接继承。
    #>
    $envFile = Join-Path $PSScriptRoot '.env'
    $fallback = Join-Path $PSScriptRoot '.env.example'

    $source = if (Test-Path $envFile) { $envFile } else { $fallback }
    if (-not (Test-Path $source)) { throw "找不到配置文件：$envFile" }

    Get-Content $source | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        # 去掉成对引号
        if ($val.Length -ge 2 -and $val.StartsWith('"') -and $val.EndsWith('"')) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }

    if ($source -eq $fallback) {
        Write-Warn "未找到 scripts/local/.env，使用 .env.example 的默认值。复制一份再改更稳妥。"
    }
    return $source
}

function Get-Config {
    param([string]$Name, [string]$Default = '')
    $v = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($v)) { return $Default }
    return $v
}

function Get-DataDir {
    $dir = Get-Config 'CAREERPILOT_DATA_DIR' 'D:\CareerPilotData'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    return $dir
}

# ── 控制台输出 ──────────────────────────────────────────────

function Write-Step { param([string]$Msg) Write-Host "==> $Msg" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Msg) Write-Host "    [OK]   $Msg" -ForegroundColor Green }
function Write-Info { param([string]$Msg) Write-Host "    [INFO] $Msg" -ForegroundColor Gray }
function Write-Err  { param([string]$Msg) Write-Host "    [FAIL] $Msg" -ForegroundColor Red }
function Write-Warn { param([string]$Msg) Write-Host "    [WARN] $Msg" -ForegroundColor Yellow }

# ── 端口与进程 ──────────────────────────────────────────────

function Test-PortInUse {
    param([int]$Port)
    try {
        return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
    } catch {
        return $false
    }
}

function Wait-Port {
    <# 等待端口开始监听；返回 $true/$false #>
    param([int]$Port, [int]$TimeoutSec = 60)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if (Test-PortInUse -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-PortFree {
    param([int]$Port, [int]$TimeoutSec = 30)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if (-not (Test-PortInUse -Port $Port)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-PidFilePath {
    param([string]$Name)
    return (Join-Path (Get-RuntimeDir) "$Name.pid")
}

function Start-Background {
    <#
        以分离进程启动后台程序，日志写入 .runtime/logs/<name>.out.log，
        PID 写入 .runtime/<name>.pid。

        注意：这里刻意**不用** Start-Process 的 -RedirectStandardOutput。
        一旦用了它，Start-Process 会走 UseShellExecute=false，子进程会继承
        父进程（含调用方管道）的可继承句柄。长驻进程一直握着管道写端不退出，
        调用方的 `| Tee-Object`、`| Select-Object -Last N` 就永远等不到 EOF，
        表现为脚本"卡住不退出"。

        改成让 cmd.exe 用 shell 重定向，Start-Process 不带 Redirect 参数
        （UseShellExecute=true），子进程不继承任何父进程句柄。
        代价是 PID 文件里记的是 cmd.exe 的 PID，停止时要用 taskkill /T 按树杀。
    #>
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = '',
        [int]$Port = 0,
        [int]$WaitSec = 0
    )

    $logDir = Get-LogDir
    $outLog = Join-Path $logDir "$Name.out.log"
    $errLog = Join-Path $logDir "$Name.err.log"
    $pidFile = Get-PidFilePath $Name

    if (Test-Path $pidFile) {
        $oldPid = (Get-Content $pidFile -Raw).Trim()
        if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
            Write-Info "$Name 已在运行（PID $oldPid），跳过"
            return
        }
    }
    if ($Port -gt 0 -and (Test-PortInUse -Port $Port)) {
        Write-Warn "$Name 端口 $Port 已被占用，跳过启动（请先 stop-all.ps1 或改端口）"
        return
    }

    # 每次启动都重置日志，避免上一次的输出混进来
    foreach ($f in @($outLog, $errLog)) {
        if (Test-Path $f) { Clear-Content $f -ErrorAction SilentlyContinue }
    }

    $argLine = ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    }) -join ' '

    # cmd /c "..." 的引号规则：命令行首字符是引号时 cmd 会吃掉首尾引号，
    # 所以整条命令外面再套一层引号。
    $inner = ('"' + $FilePath + '" ' + $argLine + ' > "' + $outLog + '" 2> "' + $errLog + '"').Trim()
    $cmdLine = '"' + $inner + '"'

    $p = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList '/c', $cmdLine `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden -PassThru

    Set-Content -Path $pidFile -Value $p.Id -Encoding ascii
    Write-Info "$Name 已启动（cmd PID $($p.Id)）"

    if ($WaitSec -gt 0 -and $Port -gt 0) {
        if (Wait-Port -Port $Port -TimeoutSec $WaitSec) {
            Write-Ok "$Name 监听 $Port"
        } else {
            Write-Err "$Name 启动超时（$WaitSec s 内未监听 $Port），日志：$errLog"
        }
    }
}
function Stop-Background {
    param(
        [Parameter(Mandatory)][string]$Name,
        [int]$TimeoutSec = 15
    )
    $pidFile = Get-PidFilePath $Name
    if (-not (Test-Path $pidFile)) {
        Write-Info "$Name 未在运行（无 PID 文件）"
        return
    }
    $procId = (Get-Content $pidFile -Raw).Trim()
    if (-not $procId) { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue; return }

    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Info "停止 $Name（cmd PID $procId 及其子进程）"
        # 用 taskkill /T 按进程树杀，否则 cmd 挂了它拉起的 java / python 会变成孤儿进程
        & taskkill.exe /T /F /PID $procId 2>&1 | Out-Null
        Start-Sleep -Milliseconds 500
        if (Get-Process -Id $procId -ErrorAction SilentlyContinue) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    } else {
        Write-Info "$Name 进程不存在，清理 PID 文件"
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

function Get-ProcessAlive {
    param([string]$Name)
    $pidFile = Get-PidFilePath $Name
    if (-not (Test-Path $pidFile)) { return $false }
    $procId = (Get-Content $pidFile -Raw).Trim()
    if (-not $procId) { return $false }
    return $null -ne (Get-Process -Id $procId -ErrorAction SilentlyContinue)
}

# ── 外部工具定位 ────────────────────────────────────────────

function Find-MySqlBin {
    <# 定位 mysqld.exe / mysql.exe：先找 MySQL 官方安装目录，再找 PATH #>
    $candidates = @()
    foreach ($root in @('C:\ProgramData\MySQL', 'C:\Program Files\MySQL', 'C:\Program Files (x86)\MySQL')) {
        if (Test-Path $root) {
            $candidates += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { Join-Path $_.FullName 'bin' }
        }
    }
    $candidates += (Get-Command mysqld -ErrorAction SilentlyContinue | ForEach-Object { Split-Path $_.Source -Parent })
    $candidates += (Get-Command mysql -ErrorAction SilentlyContinue | ForEach-Object { Split-Path $_.Source -Parent })

    foreach ($dir in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        if ((Test-Path (Join-Path $dir 'mysqld.exe')) -and (Test-Path (Join-Path $dir 'mysql.exe'))) {
            return $dir
        }
    }
    return $null
}

function Find-RedisBin {
    <# 定位 redis-server.exe #>
    $candidates = @()
    foreach ($root in @('D:\Tools\redis', 'D:\Tools\redis-7.4.11', 'C:\Program Files\Redis', 'C:\redis')) {
        if (Test-Path $root) {
            $candidates += Get-ChildItem $root -Recurse -Filter 'redis-server.exe' -ErrorAction SilentlyContinue |
                ForEach-Object { $_.DirectoryName }
        }
    }
    $candidates += (Get-Command redis-server -ErrorAction SilentlyContinue | ForEach-Object { Split-Path $_.Source -Parent })
    foreach ($dir in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        if (Test-Path (Join-Path $dir 'redis-server.exe')) { return $dir }
    }
    return $null
}

function Get-JavaTool {
    <# 返回 @{ Java = 'java.exe 路径'; Maven = 'mvn.cmd 路径' } #>
    $java = $null
    $mvn = $null

    $jh = Get-Config 'JAVA_HOME' ''
    if (-not $jh) { $jh = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User') }
    if ($jh -and (Test-Path (Join-Path $jh 'bin\java.exe'))) {
        $java = Join-Path $jh 'bin\java.exe'
    }
    $c = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java -and $c) { $java = $c.Source }

    $mh = Get-Config 'MAVEN_HOME' ''
    if (-not $mh) { $mh = [Environment]::GetEnvironmentVariable('MAVEN_HOME', 'User') }
    if ($mh -and (Test-Path (Join-Path $mh 'bin\mvn.cmd'))) {
        $mvn = Join-Path $mh 'bin\mvn.cmd'
    }
    $c2 = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn -and $c2) { $mvn = $c2.Source }

    return @{ Java = $java; Maven = $mvn }
}
