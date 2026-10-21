# lanzar_workers.ps1 - Ola 1 (Reto R01) - lanza los 3 workers EN SEGUNDO PLANO (no bloquea)
# Uso: powershell -ExecutionPolicy Bypass -File .arquitecto\scripts\lanzar_workers.ps1
$ErrorActionPreference = 'Continue'
$exe  = 'C:\Users\dm2\AppData\Local\Microsoft\WinGet\Packages\SST.opencode_Microsoft.Winget.Source_8wekyb3d8bbwe\opencode.exe'
$root = 'C:\Users\dm2\Documents\Reto 1'

if (-not (Test-Path $exe)) { Write-Output "FATAL: no se encuentra opencode.exe"; exit 1 }
Set-Location $root

$workers = @(
    @{ id='wp1'; nombre='infra-docker'; prompt='.arquitecto\prompts\wp1-infra-docker.md' },
    @{ id='wp2'; nombre='backend-core'; prompt='.arquitecto\prompts\wp2-backend-core.md' },
    @{ id='wp3'; nombre='sync-service'; prompt='.arquitecto\prompts\wp3-sync-service.md' }
)

$registro = Join-Path $root '.arquitecto\workers\lanzamientos.csv'
if (-not (Test-Path $registro)) { 'fecha_hora,wp,nombre,pid' | Set-Content -Encoding UTF8 $registro }

foreach ($w in $workers) {
    $out = Join-Path $root (".arquitecto\progress\" + $w.id + ".out.log")
    $err = Join-Path $root (".arquitecto\progress\" + $w.id + ".err.log")
    $msgTexto = "Ejecuta el plan descrito en $($w.prompt) siguiendolo al pie de la letra (rutas relativas al directorio de trabajo). Termina escribiendo tu informe en .arquitecto/progress/$($w.id)_informe.md"
    $msg = '"' + $msgTexto + '"'
    $argList = @('run', $msg, '--agent', 'build', '-m', 'opencode/mimo-v2.6-flash-free', '-f', $w.prompt, '--auto', '--title', ('R01-' + $w.id))

    $p = Start-Process -FilePath $exe -ArgumentList $argList -WorkingDirectory $root `
        -RedirectStandardOutput $out -RedirectStandardError $err `
        -WindowStyle Hidden -PassThru

    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    "$stamp,$($w.id),$($w.nombre),$($p.Id)" | Add-Content -Encoding UTF8 $registro
    Set-Content -Path (Join-Path $root (".arquitecto\workers\" + $w.id + ".pid")) -Value $p.Id
    Write-Output "LANZADO $($w.id) [$($w.nombre)] PID=$($p.Id) log=$out"
}
Write-Output "OK: 3 workers en segundo plano. Seguimiento: .arquitecto\scripts\seguimiento.ps1"
exit 0
