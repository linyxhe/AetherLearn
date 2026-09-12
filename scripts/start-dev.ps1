#requires -Version 5.1
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot 'AetherLearn_backend'
$frontendDir = Join-Path $projectRoot 'AetherLearn_front'
$aiServiceDir = Join-Path $projectRoot 'aetherlearn-ai-service'
$composeFile = Join-Path $aiServiceDir 'docker-compose.yml'
$aiEnvFile = Join-Path $aiServiceDir '.env'
$logDir = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Assert-ExitCode {
    param(
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][string]$Command
    )

    if ($ExitCode -ne 0) {
        throw "$Command failed with exit code $ExitCode."
    }
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$EnvFile
    )

    $envItem = Get-Item "Env:$Name" -ErrorAction SilentlyContinue
    if ($envItem -and -not [string]::IsNullOrWhiteSpace($envItem.Value)) {
        return $envItem.Value
    }

    if (Test-Path -LiteralPath $EnvFile) {
        $pattern = "^\s*(?:export\s+)?$([regex]::Escape($Name))\s*="
        # 必须按 UTF-8 读取：PowerShell 5.1 默认按系统 ANSI 解码，.env 里的中文注释会被误解码，
        # 某些字节对会把后面的换行吃掉，导致 SERVICE_API_KEY 被并进上一行注释、读不到密钥，
        # 最终 AI 服务返回 401、问答被降级为 BM25。同时兼容 \n、\r\n、单独 \r。
        $lines = (Get-Content -LiteralPath $EnvFile -Raw -Encoding UTF8) -split "\r\n|\r|\n"
        $line = $lines | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($line) {
            $value = (($line -split '=', 2)[1]).Trim()
            if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            return $value
        }
    }

    return ''
}

function ConvertTo-PSSingleQuoted {
    param([string]$Value)
    return ($Value -replace "'", "''")
}

function Wait-For-Url {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][uri]$Url,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    Write-Host "Waiting for ${Name}: ${Url}"
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -MaximumRedirection 0 -TimeoutSec 3
            if ($response.StatusCode -lt 500) {
                Write-Host "$Name is ready."
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$Name did not become ready within $TimeoutSeconds seconds: $Url"
}

function Wait-For-Port {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    Write-Host "Waiting for $Name to listen on $Port"
    while ((Get-Date) -lt $deadline) {
        try {
            if (Test-NetConnection -ComputerName '127.0.0.1' -Port $Port -InformationLevel Quiet -ErrorAction SilentlyContinue) {
                Write-Host "$Name is listening on $Port."
                return
            }
        }
        catch {
            # The port is not open yet.
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not listen on $Port within $TimeoutSeconds seconds."
}

function Start-LocalMySQL {
    $mysqlServices = Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue
    if ($mysqlServices) {
        foreach ($service in $mysqlServices) {
            if ($service.Status -ne 'Running') {
                Write-Host "Starting MySQL service: $($service.Name)"
                Start-Service -Name $service.Name
            }
        }
    }
    else {
        throw 'No MySQL Windows service was found. Start MySQL before running the project.'
    }
}

function Ensure-DockerRunning {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    docker info | Out-Null
    $dockerReady = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $previous

    if ($dockerReady) {
        return
    }

    $candidates = @(
        'D:\Docker\Docker\Docker Desktop.exe',
        'D:\Docker\docker\Docker Desktop.exe',
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe')
    )
    $desktop = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
    if (-not $desktop) {
        throw 'Docker daemon is not running and Docker Desktop was not found. Start Docker Desktop manually.'
    }

    Write-Host "Starting Docker Desktop: $desktop"
    Start-Process -FilePath $desktop | Out-Null

    $deadline = (Get-Date).AddSeconds(240)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $ErrorActionPreference = 'SilentlyContinue'
        docker info | Out-Null
        $dockerReady = ($LASTEXITCODE -eq 0)
        $ErrorActionPreference = $previous
        if ($dockerReady) {
            Write-Host 'Docker is ready.'
            return
        }
    }
    throw 'Docker did not become ready within 240 seconds.'
}

foreach ($command in @('docker', 'npm', 'mvn')) {
    Require-Command -Name $command
}

Ensure-DockerRunning

if (-not (Test-Path -LiteralPath $backendDir)) {
    throw "Backend directory not found: $backendDir"
}
if (-not (Test-Path -LiteralPath $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}
if (-not (Test-Path -LiteralPath $aiServiceDir)) {
    throw "AI service directory not found: $aiServiceDir"
}

$aiHelper = Join-Path $PSScriptRoot 'ai-service.ps1'
if (-not (Test-Path -LiteralPath $aiHelper)) {
    throw "AI service helper not found: $aiHelper"
}
. $aiHelper

$uploadDir = if ($env:FILE_UPLOAD_DIR) {
    $env:FILE_UPLOAD_DIR
}
else {
    Join-Path $projectRoot 'uploads'
}
New-Item -ItemType Directory -Force -Path $uploadDir | Out-Null

# The backend and the Python AI service share the same internal service key.
$serviceApiKey = Get-EnvValue -Name 'SERVICE_API_KEY' -EnvFile $aiEnvFile
$llmApiKey = Get-EnvValue -Name 'LLM_API_KEY' -EnvFile $aiEnvFile

$backendEnv = @(
    "Set-Location -LiteralPath '$(ConvertTo-PSSingleQuoted $backendDir)'",
    "`$env:SPRING_PROFILES_ACTIVE='dev'",
    "`$env:FILE_UPLOAD_DIR='$(ConvertTo-PSSingleQuoted $uploadDir)'",
    "if ('$(ConvertTo-PSSingleQuoted $serviceApiKey)') { `$env:AI_SERVICE_API_KEY='$(ConvertTo-PSSingleQuoted $serviceApiKey)' }",
    'mvn spring-boot:run'
) -join '; '

Write-Host '========================================'
Write-Host 'AetherLearn development startup'
Write-Host 'Spring Boot: http://127.0.0.1:8080'
Write-Host 'Vue:         http://127.0.0.1:5173'
Write-Host 'AI service:  http://127.0.0.1:8000 (host Python)'
Write-Host '========================================'

Write-Host 'Starting Docker services: Milvus and MinIO...'
docker compose -f $composeFile up -d milvus minio
Assert-ExitCode -ExitCode $LASTEXITCODE -Command 'docker compose up -d milvus minio'

Wait-For-Url -Name 'Milvus' -Url 'http://127.0.0.1:9091/healthz' -TimeoutSeconds 240

Start-AiService -LogDir $logDir -ServiceApiKey $serviceApiKey -LlmApiKey $llmApiKey
Wait-For-Url -Name 'AI service' -Url 'http://127.0.0.1:8000/health/live' -TimeoutSeconds 240
try {
    Wait-For-Url -Name 'AI service readiness' -Url 'http://127.0.0.1:8000/health/ready' -TimeoutSeconds 120
}
catch {
    Write-Warning "AI readiness check failed; backend will fall back to MySQL FULLTEXT + BM25: $($_.Exception.Message)"
}

Write-Host 'Checking local MySQL...'
Start-LocalMySQL
Wait-For-Port -Name 'MySQL' -Port 3306 -TimeoutSeconds 60

Write-Host 'Starting Spring Boot backend...'
Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-NoExit', '-Command', $backendEnv -RedirectStandardOutput (Join-Path $logDir 'backend.log') -RedirectStandardError (Join-Path $logDir 'backend.err')

Wait-For-Port -Name 'Spring Boot' -Port 8080 -TimeoutSeconds 120

Write-Host 'Starting Vue frontend...'
Set-Location -LiteralPath $frontendDir
npm run dev
