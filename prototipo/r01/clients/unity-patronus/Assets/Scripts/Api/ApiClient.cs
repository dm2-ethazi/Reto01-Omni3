using System;
using System.Text;
using R01.Patronus.Modelos;
using UnityEngine;
using UnityEngine.Networking;

namespace R01.Patronus.Api
{
    /// <summary>
    /// Cliente HTTP de la API de R01 sobre UnityWebRequest (corutinas).
    ///
    /// Contrato cubierto: /health, /connectors, /central/estudiantes,
    /// /central/estudiantes/{dni}, /central/consistencia,
    /// CRUD de /connectors/{id}/estudiantes y /sync.
    /// Los errores del backend llegan como {"error": {codigo, mensaje}}.
    /// </summary>
    public class ApiClient : MonoBehaviour
    {
        /// <summary>Instancia única para llamadas puntuales desde cualquier script.</summary>
        public static ApiClient Instancia { get; private set; }

        [SerializeField] private string urlBase = ApiConfig.BaseUrlPorDefecto;

        private static readonly Encoding Utf8 = new UTF8Encoding(false);

        public string UrlBase
        {
            get => urlBase;
            set => urlBase = ApiConfig.Normalizar(value);
        }

        private void Awake()
        {
            if (Instancia != null && Instancia != this)
            {
                Destroy(gameObject);
                return;
            }
            Instancia = this;
            DontDestroyOnLoad(gameObject);
            urlBase = ApiConfig.Normalizar(urlBase);
        }

        // --- Lecturas -------------------------------------------------------

        /// <summary>GET /health - status ok|degraded, siempre HTTP 200.</summary>
        public void GetSalud(Action<Salud> ok, Action<string> fallo)
        {
            ConJson("/health", ok, fallo);
        }

        /// <summary>
        /// GET /connectors - el cuerpo es un diccionario por id y JsonUtility
        /// no admite claves dinámicas, así que se devuelve el JSON crudo.
        /// </summary>
        public void GetConectores(Action<string> jsonCrudo, Action<string> fallo)
        {
            StartCoroutine(Enviar("GET", "/connectors", null, (status, cuerpo) =>
            {
                if (EsExito(status)) jsonCrudo?.Invoke(cuerpo);
                else fallo?.Invoke(MensajeError(cuerpo));
            }, fallo));
        }

        /// <summary>GET /central/estudiantes con filtros y paginación.</summary>
        public void GetCentral(string nombre, string dni, int offset, int limit,
            Action<PaginaCentral> ok, Action<string> fallo)
        {
            string consulta = Consulta(offset, limit, nombre, dni);
            ConJson("/central/estudiantes" + consulta, ok, fallo);
        }

        /// <summary>GET /central/estudiantes/{dni} - ok(null) cuando responde 404.</summary>
        public void GetPorDni(string dni, Action<EstudianteCentral> ok, Action<string> fallo)
        {
            StartCoroutine(Enviar("GET",
                "/central/estudiantes/" + Uri.EscapeDataString((dni ?? "").Trim()), null,
                (status, cuerpo) =>
                {
                    if (status == 404) { ok?.Invoke(null); return; }
                    if (!EsExito(status)) { fallo?.Invoke(MensajeError(cuerpo)); return; }
                    ok?.Invoke(JsonUtility.FromJson<EstudianteCentral>(cuerpo));
                }, fallo));
        }

        /// <summary>GET /central/consistencia - conteos y huellas de la central doble.</summary>
        public void GetConsistencia(Action<string> jsonCrudo, Action<string> fallo)
        {
            StartCoroutine(Enviar("GET", "/central/consistencia", null, (status, cuerpo) =>
            {
                if (EsExito(status)) jsonCrudo?.Invoke(cuerpo);
                else fallo?.Invoke(MensajeError(cuerpo));
            }, fallo));
        }

        /// <summary>GET /connectors/{id}/estudiantes con filtros y paginación.</summary>
        public void GetOrigen(string connectorId, string nombre, string dni,
            int offset, int limit, Action<PaginaOrigen> ok, Action<string> fallo)
        {
            string consulta = Consulta(offset, limit, nombre, dni);
            ConJson("/connectors/" + connectorId + "/estudiantes" + consulta, ok, fallo);
        }

        // --- CRUD de un origen ---------------------------------------------

        /// <summary>POST /connectors/{id}/estudiantes - responde 201.</summary>
        public void PostCrear(string connectorId, EstudiantePayload datos,
            Action<Estudiante> ok, Action<string> fallo)
        {
            ConJson("/connectors/" + connectorId + "/estudiantes",
                datos == null ? "{}" : datos.AJson(), ok, fallo, "POST");
        }

        /// <summary>GET /connectors/{id}/estudiantes/{rowId}.</summary>
        public void GetObtener(string connectorId, int rowId,
            Action<Estudiante> ok, Action<string> fallo)
        {
            ConJson("/connectors/" + connectorId + "/estudiantes/" + rowId, ok, fallo);
        }

        /// <summary>PUT /connectors/{id}/estudiantes/{rowId} - actualización parcial.</summary>
        public void PutActualizar(string connectorId, int rowId, EstudianteUpdate cambios,
            Action<Estudiante> ok, Action<string> fallo)
        {
            ConJson("/connectors/" + connectorId + "/estudiantes/" + rowId,
                cambios == null ? "{}" : cambios.AJson(), ok, fallo, "PUT");
        }

        /// <summary>DELETE /connectors/{id}/estudiantes/{rowId} - responde 204 sin cuerpo.</summary>
        public void DeleteBorrar(string connectorId, int rowId,
            Action ok, Action<string> fallo)
        {
            StartCoroutine(Enviar("DELETE",
                "/connectors/" + connectorId + "/estudiantes/" + rowId, null,
                (status, cuerpo) =>
                {
                    if (EsExito(status)) ok?.Invoke();
                    else fallo?.Invoke(MensajeError(cuerpo));
                }, fallo));
        }

        // --- Sincronización -------------------------------------------------

        /// <summary>POST /sync - todos los conectores; /sync/{id} - uno solo.</summary>
        public void PostSync(string connectorId, Action<SyncResumen> ok, Action<string> fallo)
        {
            string ruta = string.IsNullOrEmpty(connectorId) ? "/sync" : "/sync/" + connectorId;
            ConJson(ruta, null, ok, fallo, "POST", 120);
        }

        // --- Internos -------------------------------------------------------

        private void ConJson<T>(string ruta, Action<T> ok, Action<string> fallo)
        {
            StartCoroutine(Enviar("GET", ruta, null, (status, cuerpo) =>
            {
                if (!EsExito(status)) { fallo?.Invoke(MensajeError(cuerpo)); return; }
                ok?.Invoke(JsonUtility.FromJson<T>(cuerpo));
            }, fallo));
        }

        private void ConJson<T>(string ruta, string cuerpoJson, Action<T> ok,
            Action<string> fallo, string metodo, int timeout = 30)
        {
            StartCoroutine(Enviar(metodo, ruta, cuerpoJson, (status, cuerpo) =>
            {
                if (!EsExito(status)) { fallo?.Invoke(MensajeError(cuerpo)); return; }
                ok?.Invoke(string.IsNullOrEmpty(cuerpo)
                    ? default
                    : JsonUtility.FromJson<T>(cuerpo));
            }, fallo, timeout));
        }

        /// <summary>Única salida a red: devuelve (status HTTP, cuerpo) o error.</summary>
        private IEnumerator Enviar(string metodo, string ruta, string cuerpo,
            Action<long, string> alRecibir, Action<string> alError, int timeout = 30)
        {
            using (var peticion = new UnityWebRequest(urlBase + ruta, metodo))
            {
                if (cuerpo != null)
                {
                    peticion.uploadHandler = new UploadHandlerRaw(Utf8.GetBytes(cuerpo));
                    peticion.SetRequestHeader("Content-Type", "application/json");
                }
                else if (metodo != UnityWebRequest.kGetVerb && metodo != UnityWebRequest.kHeadVerb)
                {
                    // POST/DELETE sin cuerpo: handler vacío evita el aviso de Unity.
                    peticion.uploadHandler = new UploadHandlerRaw(Array.Empty<byte>());
                }
                peticion.downloadHandler = new DownloadHandlerBuffer();
                peticion.timeout = timeout;
                yield return peticion.SendWebRequest();

                if (peticion.result == UnityWebRequest.Result.ConnectionError ||
                    peticion.result == UnityWebRequest.Result.DataProcessingError)
                {
                    alError?.Invoke(peticion.error ?? "error de red");
                    yield break;
                }
                alRecibir?.Invoke(peticion.responseCode, peticion.downloadHandler.text);
            }
        }

        /// <summary>offset/limit obligatorios; nombre y dni sólo si traen valor.</summary>
        private static string Consulta(int offset, int limit, string nombre, string dni)
        {
            var sb = new StringBuilder();
            Añadir(sb, "offset", offset.ToString());
            Añadir(sb, "limit", Mathf.Clamp(limit, 1, ApiConfig.LimitMaximo).ToString());
            Añadir(sb, "nombre", nombre);
            Añadir(sb, "dni", dni);
            return sb.ToString();
        }

        private static void Añadir(StringBuilder sb, string campo, string valor)
        {
            if (string.IsNullOrEmpty(valor))
            {
                return;
            }
            sb.Append(sb.Length == 0 ? '?' : '&')
              .Append(campo).Append('=')
              .Append(Uri.EscapeDataString(valor.Trim()));
        }

        private static bool EsExito(long status) => status >= 200 && status < 300;

        private static string MensajeError(string cuerpo)
        {
            if (string.IsNullOrEmpty(cuerpo))
            {
                return "respuesta vacía";
            }
            try
            {
                RespuestaError envoltorio = JsonUtility.FromJson<RespuestaError>(cuerpo);
                if (envoltorio != null && envoltorio.error != null)
                {
                    return envoltorio.error.codigo + ": " + envoltorio.error.mensaje;
                }
            }
            catch (Exception)
            {
                // El cuerpo no es el envelope estándar: se devuelve tal cual.
            }
            return cuerpo;
        }
    }
}
