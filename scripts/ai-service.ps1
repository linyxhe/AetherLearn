# Shared helpers for running the Python AI service directly on the host.
# The script is ASCII-only so Windows PowerShell 5.1 can parse it safely.

$script:AiServiceDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'aetherlearn-ai-service'
$script:AiEnvDir = if ($env:AI_ENV_DIR) { $env:AI_ENV_DIR } else { 'D:\Program Files\AetherLearn\ai-env' }
$script:AiPython = Join-Path $script:AiEnvDir 'python.exe'

function ConvertTo-PSSingleQuoted {
    param([string]$Value)
    return ($Value -replace "'", "''")
}

function Get-EnvFileValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$EnvFile
    )

    if (-not (Test-Path -LiteralPath $EnvFile)) {
        return ''
    }

    $pattern = "^\s*(?:export\s+)?$([regex]::Escape($Name))\s*="
    # 必须按 UTF-8 读取 .env（见 start-dev.ps1 说明），否则中文注释会吃掉换行导致读不到密钥。
    $lines = (Get-Content -LiteralPath $EnvFile -Raw -Encoding UTF8) -split "\r\n|\r|\n"
    $line = $lines | Where-Object { $_ -match $pattern } | Select-Object -First 1
    if (-not $line) {
        return ''
    }

    $value = (($line -split '=', 2)[1]).Trim()
    if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    return $value
}

function Resolve-AiPython {
    # Return a Python 3.11+ interpreter that can host the AI service.

    if ($env:AI_PYTHON) {
        if (-not (Test-Path -LiteralPath $env:AI_PYTHON)) {
            throw "AI_PYTHON points to a missing file: $env:AI_PYTHON"
        }
        return $env:AI_PYTHON
    }

    if (Test-Path -LiteralPath $script:AiPython) {
        return $script:AiPython
    }

    if (-not (Get-Command conda -ErrorAction SilentlyContinue)) {
        throw 'conda was not found. Install Anaconda/Miniconda or set AI_PYTHON to an existing Python 3.11 interpreter.'
    }

    $envParent = Split-Path -Parent $script:AiEnvDir
    New-Item -ItemType Directory -Force -Path $envParent | Out-Null

    Write-Host "Creating Python environment for the AI service: $script:AiEnvDir"
    conda create -p $script:AiEnvDir python=3.11 -y | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'conda create failed while preparing the AI environment.'
    }

    return $script:AiPython
}

function Install-AiDependencies {
    param([Parameter(Mandatory = $true)][string]$Python)

    $requirements = Join-Path $script:AiServiceDir 'requirements.txt'
    if (-not (Test-Path -LiteralPath $requirements)) {
        throw "AI requirements file not found: $requirements"
    }

    Write-Host 'Installing Python dependencies for the AI service (first run can take a while)...'
    & $Python -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to upgrade pip in the AI environment.'
    }

    # Windows 默认安装 CPU 版 torch，避免从 PyPI 拉取数 GB 的 CUDA wheel。
    # 可用 AI_TORCH_INDEX_URL 覆盖为国内镜像。
    $torchIndex = if ($env:AI_TORCH_INDEX_URL) { $env:AI_TORCH_INDEX_URL } else { 'https://download.pytorch.org/whl/cpu' }
    & $Python -m pip install --no-input --timeout 120 --retries 5 torch==2.3.0 --index-url $torchIndex
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to install CPU torch for the AI environment.'
    }

    # Default to a China-friendly mirror; override with AI_PIP_INDEX_URL when needed.
    $indexUrl = if ($env:AI_PIP_INDEX_URL) { $env:AI_PIP_INDEX_URL } else { 'https://pypi.tuna.tsinghua.edu.cn/simple' }
    $pipArgs = @('install', '--prefer-binary', '--no-input', '--timeout', '60', '--retries', '5', '-i', $indexUrl, '-r', $requirements)

    & $Python -m pip @pipArgs
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to install AI service dependencies.'
    }
}

function Test-AiDependencies {
    param([Parameter(Mandatory = $true)][string]$Python)

    $probe = "import importlib.util as u, sys; sys.exit(0 if all(u.find_spec(m) for m in ['fastapi','uvicorn','pymilvus','pymysql','torch','transformers','onnxruntime','httpx']) else 1)"
    & $Python -c $probe
    return ($LASTEXITCODE -eq 0)
}

function Start-AiService {
    param(
        [Parameter(Mandatory = $true)][string]$LogDir,
        [string]$ServiceApiKey = '',
        [string]$LlmApiKey = ''
    )

    $python = Resolve-AiPython
    if (-not (Test-AiDependencies -Python $python)) {
        Install-AiDependencies -Python $python
    }

    $hfHome = Join-Path $script:AiServiceDir 'models\huggingface'
    New-Item -ItemType Directory -Force -Path $hfHome | Out-Null

    $command = @(
        "Set-Location -LiteralPath '$(ConvertTo-PSSingleQuoted $script:AiServiceDir)'",
        "if (-not `$env:MILVUS_HOST) { `$env:MILVUS_HOST='127.0.0.1' }",
        "if (-not `$env:MILVUS_PORT) { `$env:MILVUS_PORT='19530' }",
        "if (-not `$env:COLLECTION_NAME) { `$env:COLLECTION_NAME='knowledge_chunks_v2' }",
        "if (-not `$env:LEGACY_COLLECTION_NAME) { `$env:LEGACY_COLLECTION_NAME='knowledge_chunks' }",
        "if (-not `$env:DB_HOST) { `$env:DB_HOST='127.0.0.1' }",
        "if (-not `$env:DB_PORT) { `$env:DB_PORT='3306' }",
        "if (-not `$env:DB_NAME) { `$env:DB_NAME='aetherlearn' }",
        "if (-not `$env:DB_USER) { `$env:DB_USER='root' }",
        "if (-not `$env:EMBEDDING_DEVICE) { `$env:EMBEDDING_DEVICE='cpu' }",
        "if (-not `$env:RERANKER_DEVICE) { `$env:RERANKER_DEVICE='cpu' }",
        "if (-not `$env:HF_ENDPOINT) { `$env:HF_ENDPOINT='https://hf-mirror.com' }",
        "`$env:HF_HOME='$(ConvertTo-PSSingleQuoted $hfHome)'"
    )

    if ($ServiceApiKey) {
        $command += "`$env:SERVICE_API_KEY='$(ConvertTo-PSSingleQuoted $ServiceApiKey)'"
    }
    if ($LlmApiKey) {
        $command += "`$env:LLM_API_KEY='$(ConvertTo-PSSingleQuoted $LlmApiKey)'"
    }

    $command += "& '$(ConvertTo-PSSingleQuoted $python)' -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1"
    $commandText = $command -join '; '

    Write-Host 'Starting Python AI service on http://127.0.0.1:8000 ...'
    Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-NoExit', '-Command', $commandText -RedirectStandardOutput (Join-Path $LogDir 'ai-service.log') -RedirectStandardError (Join-Path $LogDir 'ai-service.err')
}
