# R01 - Descarga los drivers JDBC desde Maven Central a backend\drivers.
# Uso: powershell -ExecutionPolicy Bypass -File infra\scripts\fetch_drivers.ps1
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path (Split-Path -Parent (Split-Path -Parent $ScriptDir)) 'backend'
$Dest = Join-Path $BackendDir 'drivers'
$Base = 'https://repo1.maven.org/maven2'

$DerbyVersion = if ($env:DERBY_VERSION) { $env:DERBY_VERSION } else { '10.16.1.1' }
$HsqldbVersion = if ($env:HSQLDB_VERSION) { $env:HSQLDB_VERSION } else { '2.7.3' }
$H2Version = if ($env:H2_VERSION) { $env:H2_VERSION } else { '2.2.224' }
$OjdbcVersion = if ($env:OJDBC_VERSION) { $env:OJDBC_VERSION } else { '23.26.3.0.0' }

New-Item -ItemType Directory -Force -Path $Dest | Out-Null

$artifacts = @(
    @{ Path = "org/apache/derby/derbyclient/$DerbyVersion"; File = "derbyclient-$DerbyVersion.jar" },
    @{ Path = "org/apache/derby/derbyshared/$DerbyVersion"; File = "derbyshared-$DerbyVersion.jar" },
    @{ Path = "org/hsqldb/hsqldb/$HsqldbVersion"; File = "hsqldb-$HsqldbVersion.jar" },
    @{ Path = "com/h2database/h2/$H2Version"; File = "h2-$H2Version.jar" },
    @{ Path = "com/oracle/database/jdbc/ojdbc11/$OjdbcVersion"; File = "ojdbc11-$OjdbcVersion.jar" }
)

foreach ($a in $artifacts) {
    $target = Join-Path $Dest $a.File
    if (Test-Path $target) {
        Write-Host "ya existe: $($a.File)"
        continue
    }
    $url = "$Base/$($a.Path)/$($a.File)"
    Write-Host "descargando $($a.File) ..."
    Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing
    Write-Host ("  OK {0:N0} bytes" -f (Get-Item $target).Length)
}

Write-Host "Drivers disponibles en ${Dest}:"
Get-ChildItem $Dest -Filter *.jar | Select-Object -ExpandProperty Name
