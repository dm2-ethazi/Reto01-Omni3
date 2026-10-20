$OpencodePath = "C:\Users\dm2\AppData\Local\Microsoft\WinGet\Packages\SST.opencode_Microsoft.Winget.Source_8wekyb3d8bbwe\opencode.exe"
$WorkingDir = "C:\Users\dm2\Documents\Reto 1"

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$WorkingDir'; & '$OpencodePath' exec --agent backend_dev 'Corrige path params con Path(...) en routers/connectors.py y central.py. Soluciona el AttributeError en repositories/base.py usando Session(self._engine).'"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$WorkingDir'; & '$OpencodePath' exec --agent frontend_dev 'Genera estructuras funcionales en clients/desktop-javafx/, clients/mobile-android/ y clients/unity-patronus/.'"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$WorkingDir'; & '$OpencodePath' exec --agent doc_expert 'Redacta los 5 manuales obligatorios .md en docs/ usando layout Smetana en .puml.'"
