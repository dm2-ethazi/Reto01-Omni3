# R01 · Cliente móvil (Android / Kotlin)

> **Reto R01** · Cliente Android con MVVM ligero (sin frameworks) · `es.r01.mobile`

![Android](https://img.shields.io/badge/Android-Kotlin-3DDC84?style=flat-square&logo=kotlin&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.3-02303A?style=flat-square&logo=gradle&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-24-A4C639?style=flat-square)
![Estado](https://img.shields.io/badge/assembleDebug-EXIT%200-10B981?style=flat-square)

## 🎨 Diseño

**Mock de referencia** (360 × 640): [`docs/diseno-activity-main.svg`](docs/diseno-activity-main.svg)

![Mock de la pantalla principal](docs/diseno-activity-main.svg)

| Decisión | Valor |
|----------|-------|
| **Paleta** | Primario `#1E3A8A` · Oscuro `#172554` · Acento `#06B6D4` · Fondo `#F1F5F9` · Superficie `#FFFFFF` · Borde `#E2E8F0` |
| **Tipografía** | Sans-serif del sistema (Roboto) · 22sp título / 14sp cuerpo / 13sp botones / 11–12sp etiquetas |
| **Espaciado** | Rejilla de 4dp: 6 / 8 / 10 / 12 / 14 / 16dp |
| **Radios** | Tarjeta 16dp · Campo y botón 12dp · Chip 20dp |
| **Estados** | `pressed` más oscuro (primario) o relleno azul claro (secundario); `disabled` en `#94A3B8` |

**Arreglo de overflow:** el bloque de acciones va en **dos filas con
`layout_weight`** (3 + 2 botones) en lugar de una sola fila horizontal, de
modo que `Sincronizar` ya no se aplasta contra el borde derecho en
pantallas de 360dp.

## 🗂️ Arquitectura (MVVM)

| Archivo | Papel |
|---------|-------|
| `MainActivity.kt` | Vista: sólo pinta estado y reenvía eventos |
| `EstudianteViewModel.kt` | Estado observable + acciones (búsqueda y CRUD) |
| `ApiClient.kt` | Cliente HTTP: OkHttp + Gson |
| `Modelos.kt` | `Estudiante`, `EstudianteCentral`, `Pagina`, payloads, `Salud`, `SyncResumen` |

Permisos: `INTERNET` (ya en el manifiesto). Tema propio `@style/TemaR01`
(`values/themes.xml`), sin AppCompat.

### Recursos de diseño

| Ruta | Contenido |
|------|-----------|
| `res/values/colors.xml` | Paleta R01 |
| `res/values/strings.xml` | Textos de la UI |
| `res/values/themes.xml` | Tema `TemaR01` (barra de estado oscura) |
| `res/drawable/*.xml` | Cabecera en degradado, tarjeta, campos, botones con estados, chip y fila |
| `res/layout/activity_main.xml` | Pantalla principal |
| `res/layout/item_fila.xml` | Fila de dos líneas de la lista |

> Los ids `campoUrl`, `campoNombre`, `campoDni`, `textoEstado`, `lista`,
> `botonConectar`, `botonBuscar`, `botonSalud`, `botonSincronizar` y
> `botonDni` son **contrato con `MainActivity.kt`** y no deben cambiarse.

## ▶️ Ejecución

Abrir el proyecto en Android Studio y lanzar la app.
URL por defecto: `http://10.0.2.2:8000` (el emulador alcanza al host por
`10.0.2.2`; en dispositivo físico hay que usar la IP de la máquina).

```powershell
.\gradlew.bat :app:assembleDebug --console=plain   # EXIT=0
```

## 🔌 Operaciones cubiertas (`ApiClient`)

- `GET /health`, `GET /connectors`, `GET /central/consistencia`
- `GET /central/estudiantes` (nombre, dni, offset, limit)
- `GET /central/estudiantes/{dni}` (devuelve `null` en 404)
- `GET /connectors/{id}/estudiantes`
- `POST /connectors/{id}/estudiantes` (201) · `GET`/`PUT`/`DELETE` `.../estudiantes/{row_id}`
- `POST /sync` y `POST /sync/{connector_id}`

## ⚙️ Requisitos del backend detectados

- `limit` entre 1 y 500 (por defecto 50) y `offset >= 0`.
- `dni` máx. 9 caracteres; en la ruta central exige exactamente 9.
- Payloads sin campos extra (`extra="forbid"`); `EstudianteUpdate` sólo
  envía los campos informados (Gson omite los nulos).
- `DELETE` responde 204 sin cuerpo; `POST` responde 201.
- Errores con envelope `{"error": {"codigo", "mensaje", "detalle"}}`.

La alta/edición/borrado sólo existen sobre orígenes (`/connectors/...`);
la central es de sólo lectura.
