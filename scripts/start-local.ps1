param(
    [int]$Port = 8000,
    [string]$EnvFile = '',
    [switch]$NoJava
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$python = Join-Path $repo 'api/.venv/Scripts/python.exe'
[xml]$javafxPom = Get-Content -LiteralPath (Join-Path $repo 'gestorhogwartsjavafx/pom.xml') -Raw
$jar = Join-Path $repo ('gestorhogwartsjavafx/target/{0}-{1}.jar' -f $javafxPom.project.artifactId, $javafxPom.project.version)
$data = Join-Path $repo 'api/data'
$baseUrl = "http://127.0.0.1:$Port"
if (-not (Test-Path -LiteralPath $python)) { throw 'Falta el entorno Python de api/.venv.' }
if (-not $NoJava -and -not (Test-Path -LiteralPath $jar)) {
    throw "Compila JavaFX: mvn clean package '-Djavafx.platform=win'"
}
New-Item -ItemType Directory -Force $data | Out-Null
$mutex = New-Object System.Threading.Mutex($false, 'Local\Omni3-R01-start')
$owned = $false
Push-Location $repo
try {
    try { $owned = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $owned = $true }
    if (-not $owned) { throw 'Ya hay otro arranque R01 en curso. Espera a que termine.' }

    Write-Host '[1/5] Recuperando la configuracion local compartida...'
    $infoFile = Join-Path $data 'local-environment.json'
    if ($EnvFile) {
        if (-not [IO.Path]::IsPathRooted($EnvFile)) { $EnvFile = Join-Path $repo $EnvFile }
        if (-not (Test-Path -LiteralPath $EnvFile)) { throw "No existe la configuracion: $EnvFile" }
        & $python -u scripts/local-environment.py --legacy-env $EnvFile --output $infoFile
    } else {
        & $python -u scripts/local-environment.py --output $infoFile
    }
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo recuperar la instalacion local. Revisa el mensaje anterior.' }
    $local = Get-Content -LiteralPath $infoFile -Raw | ConvertFrom-Json
    $EnvFile = $local.env_file
    Write-Host "SQLite central: $($local.sqlite_path)"

    # Reutilizar solo una API que use la misma SQLite compartida.
    $health = $null
    try { $health = Invoke-RestMethod "$baseUrl/health" -TimeoutSec 15 } catch {}
    if ($health -and ($health.reto -ne 'R01' -or $health.instalacion -ne $local.installation_id)) {
        throw "La API del puerto $Port pertenece a otra instalacion o version. Detenla antes de continuar."
    }
    if (-not $health -and (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) {
        throw "El puerto $Port esta ocupado. Deten ese proceso o usa -Port con otro puerto."
    }

    Write-Host '[2/5] Arrancando bases y esperando a que esten listas...'
    docker compose --env-file $EnvFile -p $local.compose_project up -d --wait --wait-timeout 240
    if ($LASTEXITCODE -ne 0) { throw 'Las bases no estan listas; revisa Docker y los puertos.' }
    Write-Host '[3/5] Cargando los alumnos historicos del RAR...'
    $retiredFile = Join-Path $data 'seed-retired-ids.json'
    & $python -u scripts/seed-origins.py --env-file $EnvFile --retired-ids-file $retiredFile
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar las fuentes. Revisa el error anterior.' }

    Write-Host '[4/5] Comprobando o iniciando la API...'
    if (-not $health) {
        $arguments = @('-u', '-m', 'uvicorn', 'proyecto_reto1.main:app',
            '--env-file', ('"{0}"' -f $EnvFile), '--host', '127.0.0.1', '--port', "$Port")
        $backend = Start-Process -FilePath $python -ArgumentList $arguments -WorkingDirectory $repo `
            -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $data 'local-api.stdout.log') `
            -RedirectStandardError (Join-Path $data 'local-api.stderr.log')
        $backend.Id | Set-Content (Join-Path $data 'local-api.pid')
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            try { $health = Invoke-RestMethod "$baseUrl/health" -TimeoutSec 15; break } catch {}
            $backend.Refresh()
            if ($backend.HasExited) { throw 'La API se ha cerrado. Consulta api/data/local-api.stderr.log.' }
            Start-Sleep -Seconds 1
        }
        if (-not $health) { throw 'La API no responde. Consulta api/data/local-api.stderr.log.' }
    }
    if ($health.reto -ne 'R01' -or $health.instalacion -ne $local.installation_id) {
        throw 'La API no corresponde a la instalacion compartida preparada.'
    }
    Write-Host '[5/5] Importando los historicos y comprobando ambas centrales...'
    # Actualizar la copia SQLite del catálogo también al reutilizar una API abierta.
    $null = Invoke-RestMethod "$baseUrl/patronus" -TimeoutSec 30
    # Conservar inactivos los ejemplos y los UUID sustituidos por DNI ficticios.
    $legacyIds = Get-Content -LiteralPath $retiredFile -Raw | ConvertFrom-Json
    $retireIds = @('10000001A', '10000002B', '10000003C', '10000004D') + $legacyIds
    for ($offset = 0; $offset -lt $retireIds.Count; $offset += 500) {
        $last = [Math]::Min($offset + 499, $retireIds.Count - 1)
        $retire = @{ ids = @($retireIds[$offset..$last]) } | ConvertTo-Json -Compress
        $result = Invoke-RestMethod "$baseUrl/sync" -Method Post -ContentType 'application/json' -Body $retire -TimeoutSec 600
        foreach ($source in $result.fuentes.PSObject.Properties) {
            if ($source.Value.estado -ne 'ok' -or $source.Value.conflictos -gt 0) {
                throw "No se pudieron retirar los identificadores anteriores de $($source.Name)."
            }
        }
    }
    $sync = Invoke-RestMethod "$baseUrl/sync" -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 600
    foreach ($source in $sync.fuentes.PSObject.Properties) {
        if ($source.Value.estado -ne 'ok' -or $source.Value.conflictos -gt 0) {
            throw "No se pudo importar $($source.Name) sin conflictos."
        }
        Write-Host "$($source.Name): leidos=$($source.Value.leidos), importados=$($source.Value.importados)"
    }
    $limit = (Get-Date).AddMinutes(10)
    do {
        $health = Invoke-RestMethod "$baseUrl/health" -TimeoutSec 30
        if ($health.cola.fallidas -gt 0) { throw 'Hay refrescos FAILED. Consulta /admin/refresh-queue.' }
        if ($health.cola.activas -eq 0) { break }
        if ((Get-Date) -gt $limit) { throw 'La sincronizacion sigue pendiente. Consulta /admin/refresh-queue.' }
        Write-Host "Refrescos pendientes: $($health.cola.activas)..."
        Start-Sleep -Seconds 3
    } while ($true)
    $coherence = Invoke-RestMethod "$baseUrl/admin/coherence" -TimeoutSec 30
    if (-not $coherence.coinciden) { throw 'MariaDB y SQLite divergen. Consulta /admin/coherence.' }
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
    if ($owned) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
