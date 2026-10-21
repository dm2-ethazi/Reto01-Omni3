# verificar.ps1 - verificacion final: estructura .arquitecto + prototipo + workers en background
$ErrorActionPreference = 'Continue'
$root = 'C:\Users\dm2\Documents\Reto 1'
Set-Location $root

$fail = 0
function Check([string]$path, [string]$label) {
    $ok = Test-Path (Join-Path $script:root $path)
    if ($ok) { Write-Output ("[OK]   " + $label + " -> " + $path) }
    else { Write-Output ("[FAIL] " + $label + " -> " + $path); $script:fail++ }
    return $ok
}

Write-Output '=== 1. Estructura .arquitecto ==='
Check '.arquitecto\plan.md' 'plan'
Check '.arquitecto\prompts\wp1-infra-docker.md' 'prompt WP1'
Check '.arquitecto\prompts\wp2-backend-core.md' 'prompt WP2'
Check '.arquitecto\prompts\wp3-sync-service.md' 'prompt WP3'
Check '.arquitecto\scripts\lanzar_workers.ps1' 'script lanzamiento'
Check '.arquitecto\scripts\seguimiento.ps1' 'script seguimiento'
Check '.arquitecto\scripts\verificar.ps1' 'script verificacion'
Check '.arquitecto\progress' 'carpeta progreso'
Check '.arquitecto\workers' 'carpeta workers'

Write-Output ''
Write-Output '=== 2. Estructura prototipo ==='
foreach ($d in @('prototipo_r01\api','prototipo_r01\servicios','prototipo_r01\acceso_datos','prototipo_r01\persistencia','prototipo_r01\clientes_mock','prototipo_r01\infra','prototipo_r01\data')) {
    Check $d 'capa'
}
Check 'prototipo_r01\docker-compose.yml' 'compose (WP1)'
Check 'prototipo_r01\api\main.py' 'FastAPI /health (WP2)'
Check 'prototipo_r01\persistencia\__init__.py' 'persistencia dual (WP2)'
Check 'prototipo_r01\acceso_datos\fuentes.py' 'adaptador origenes (WP3)'
Check 'prototipo_r01\servicios\cocina.py' 'servicio cocina (WP3)'

Write-Output ''
Write-Output '=== 3. Workers en segundo plano ==='
foreach ($id in @('wp1','wp2','wp3')) {
    $pidFile = Join-Path $root ('.arquitecto\workers\' + $id + '.pid')
    if (-not (Test-Path $pidFile)) { Write-Output ("[FAIL] " + $id + " sin PID registrado"); $fail++; continue }
    $proc = Get-Process -Id ([int]((Get-Content $pidFile -Raw).Trim())) -ErrorAction SilentlyContinue
    if ($proc) { Write-Output ("[OK]   " + $id + " PID " + $proc.Id + " vivo") }
    else { Write-Output ("[WARN] " + $id + " proceso terminado (revisar log/informe)") }
}
$procs = @(Get-CimInstance Win32_Process -Filter "Name='opencode.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*WinGet*' })
Write-Output ("opencode.exe vivos: " + $procs.Count)

Write-Output ''
if ($fail -eq 0) { Write-Output 'VERIFICACION GLOBAL: PASS'; exit 0 }
else { Write-Output ("VERIFICACION GLOBAL: FAIL (" + $fail + " items)"); exit 1 }
