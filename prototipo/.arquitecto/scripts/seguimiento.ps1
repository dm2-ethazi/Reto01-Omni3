# seguimiento.ps1 - estado de los workers de Ola 1 -> escribe .arquitecto\progress\estado.md
$ErrorActionPreference = 'Continue'
$root = 'C:\Users\dm2\Documents\Reto 1'
Set-Location $root

$lineas = @()
$lineas += '# Estado Ola 1 - ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
$lineas += ''
$lineas += '| WP | PID | Vivo | Log bytes | Ultima linea log | Informe |'
$lineas += '|----|-----|------|-----------|------------------|---------|'

foreach ($id in @('wp1','wp2','wp3')) {
    $pidFile = Join-Path $root ('.arquitecto\workers\' + $id + '.pid')
    $pidVal = ''
    if (Test-Path $pidFile) { $pidVal = (Get-Content $pidFile -Raw).Trim() }
    $vivo = 'NO'
    if ($pidVal -ne '') {
        $proc = Get-Process -Id ([int]$pidVal) -ErrorAction SilentlyContinue
        if ($proc) { $vivo = 'SI' }
    }
    $log = Join-Path $root ('.arquitecto\progress\' + $id + '.out.log')
    $tam = 0; $ult = ''
    if (Test-Path $log) {
        $tam = (Get-Item $log).Length
        $ult = (Get-Content $log -Tail 1 -ErrorAction SilentlyContinue)
        if ($null -eq $ult) { $ult = '' }
        $ult = $ult -replace '\|', '/'
        if ($ult.Length -gt 80) { $ult = $ult.Substring(0, 80) }
    }
    $inf = 'NO'
    if (Test-Path (Join-Path $root ('.arquitecto\progress\' + $id + '_informe.md'))) { $inf = 'SI' }
    $lineas += ('| ' + $id.ToUpper() + ' | ' + $pidVal + ' | ' + $vivo + ' | ' + $tam + ' | ' + $ult + ' | ' + $inf + ' |')
}

$lineas += ''
$lineas += '## Procesos opencode.exe en ejecucion'
$procs = @(Get-CimInstance Win32_Process -Filter "Name='opencode.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*WinGet*' })
if ($procs.Count -eq 0) {
    $lineas += '- NINGUNO'
} else {
    foreach ($pr in $procs) {
        $cmd = $pr.CommandLine
        if ($cmd -and $cmd.Length -gt 100) { $cmd = $cmd.Substring(0, 100) }
        $lineas += ('- PID ' + $pr.ProcessId + ': ' + $cmd)
    }
}
$lineas += ''

$dest = Join-Path $root '.arquitecto\progress\estado.md'
$lineas | Set-Content -Encoding UTF8 $dest
$lineas | Write-Output
