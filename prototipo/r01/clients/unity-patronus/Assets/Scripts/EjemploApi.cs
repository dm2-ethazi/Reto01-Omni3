using R01.Patronus.Api;
using R01.Patronus.Modelos;
using UnityEngine;

namespace R01.Patronus
{
    /// <summary>
    /// Punto de entrada mínimo del cliente Unity: comprueba la salud de la
    /// API, lista los conectores y carga la primera página de la central.
    /// Adjuntar a un GameObject vacío de la escena inicial.
    /// </summary>
    public class EjemploApi : MonoBehaviour
    {
        [SerializeField] private string urlBase = ApiConfig.BaseUrlPorDefecto;
        [SerializeField] private string filtroNombre = "";
        [SerializeField] private string filtroDni = "";

        private void Start()
        {
            if (ApiClient.Instancia == null)
            {
                gameObject.AddComponent<ApiClient>();
            }
            ApiClient.Instancia.UrlBase = urlBase;

            ConsultarSalud();
            ListarConectores();
            ListarCentral();
        }

        [ContextMenu("Consultar salud")]
        public void ConsultarSalud()
        {
            ApiClient.Instancia.GetSalud(
                salud => Debug.Log($"[R01] salud={salud.status} caidos={string.Join(",", salud.caidos)}"),
                error => Debug.LogError($"[R01] salud: {error}"));
        }

        [ContextMenu("Listar conectores")]
        public void ListarConectores()
        {
            ApiClient.Instancia.GetConectores(
                json => Debug.Log($"[R01] conectores: {json}"),
                error => Debug.LogError($"[R01] conectores: {error}"));
        }

        [ContextMenu("Listar central")]
        public void ListarCentral()
        {
            ApiClient.Instancia.GetCentral(filtroNombre, filtroDni, 0, ApiConfig.LimitPorDefecto,
                pagina =>
                {
                    Debug.Log($"[R01] central: {pagina.items.Length} de {pagina.total} "
                        + $"(backend={pagina.backend})");
                    foreach (EstudianteCentral fila in pagina.items)
                    {
                        Debug.Log($"  {fila.dni} | {fila.nombre} | {fila.casa} | {fila.patronus}");
                    }
                },
                error => Debug.LogError($"[R01] central: {error}"));
        }
    }
}
