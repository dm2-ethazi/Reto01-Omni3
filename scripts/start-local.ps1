param(
    [int]$Port = 8000,
    [string]$EnvFile = 'api/data/validation.env',
    [switch]$NoJava
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$python = Join-Path $repo 'api/.venv/Scripts/python.exe'
$jar = Join-Path $repo 'gestorhogwartsjavafx/target/gestorhogwartsjavafx-1.0.0.jar'
$data = Join-Path $repo 'api/data'
$baseUrl = "http://127.0.0.1:$Port"
if (-not [IO.Path]::IsPathRooted($EnvFile)) { $EnvFile = Join-Path $repo $EnvFile }
if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Falta la configuracion local: $EnvFile" }
if (-not (Test-Path -LiteralPath $python)) { throw 'Falta el entorno Python de api/.venv.' }
if (-not $NoJava -and -not (Test-Path -LiteralPath $jar)) {
    throw 'Compila primero JavaFX para Windows: mvn clean package -Djavafx.platform=win'
}

Push-Location $repo
try {
    docker compose --env-file $EnvFile -p r01-adat-validation up -d
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron arrancar las bases Docker.' }
    & $python scripts/seed-origins.py --env-file $EnvFile
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar las cuatro fuentes.' }

    $health = $null
    try { $health = Invoke-RestMethod "$baseUrl/health" -TimeoutSec 3 } catch {}
    if (-not $health) {
        $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($listener) { throw "El puerto $Port esta ocupado por otro proceso." }
        $arguments = @('-u', '-m', 'uvicorn', 'proyecto_reto1.main:app',
            '--env-file', ('"{0}"' -f $EnvFile), '--host', '127.0.0.1', '--port', "$Port")
        $backend = Start-Process -FilePath $python -ArgumentList $arguments -WorkingDirectory $repo `
            -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $data 'local-api.stdout.log') `
            -RedirectStandardError (Join-Path $data 'local-api.stderr.log')
        $backend.Id | Set-Content (Join-Path $data 'local-api.pid')
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            try { $health = Invoke-RestMethod "$baseUrl/health" -TimeoutSec 3; break } catch {}
            $backend.Refresh()
            if ($backend.HasExited) { throw 'La API se ha cerrado. Consulta api/data/local-api.stderr.log.' }
            Start-Sleep -Seconds 1
        }
        if (-not $health) { throw 'La API no responde. Consulta api/data/local-api.stderr.log.' }
    }
    $sync = Invoke-RestMethod "$baseUrl/sync" -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 120
    Write-Host "Backend: $baseUrl | Swagger: $baseUrl/docs"

    if (-not $NoJava) {
        $javaw = (Get-Command javaw.exe -ErrorAction Stop).Source
        $arguments = @('--enable-native-access=ALL-UNNAMED', "-Dgestor.api.url=$baseUrl", '-jar', ('"{0}"' -f $jar))
        $client = Start-Process -FilePath $javaw -ArgumentList $arguments `
            -WorkingDirectory (Join-Path $repo 'gestorhogwartsjavafx') -WindowStyle Normal -PassThru `
            -RedirectStandardOutput (Join-Path $data 'local-java.stdout.log') `
            -RedirectStandardError (Join-Path $data 'local-java.stderr.log')
        $client.Id | Set-Content (Join-Path $data 'local-java.pid')
        Write-Host "JavaFX abierto con API local (PID $($client.Id))."
    }
} finally {
    Pop-Location
}
