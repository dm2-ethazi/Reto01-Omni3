using System;
using UnityEngine;

namespace R01.Patronus.Modelos
{
    /// <summary>Página de un origen: PaginaEstudiantes (items + conector).</summary>
    [Serializable]
    public class PaginaOrigen
    {
        public Estudiante[] items = Array.Empty<Estudiante>();
        public int total;
        public int offset;
        public int limit;
        public string conector;
    }

    /// <summary>Página de la central: PaginaCentral (items + backend + filtros).</summary>
    [Serializable]
    public class PaginaCentral
    {
        public EstudianteCentral[] items = Array.Empty<EstudianteCentral>();
        public int total;
        public int offset;
        public int limit;
        public string backend;
    }

    /// <summary>
    /// GET /health (siempre HTTP 200). Sólo se leen los campos útiles:
    /// central/conectores contienen diccionarios y JsonUtility los ignora.
    /// </summary>
    [Serializable]
    public class Salud
    {
        public string status;
        public string reto;
        public string[] caidos = Array.Empty<string>();
    }

    /// <summary>Resumen de POST /sync y POST /sync/{connector_id}.</summary>
    [Serializable]
    public class SyncResumen
    {
        public string conector;
        public int leidos;
        public int insertados;
        public int actualizados;
        public int errores;
    }

    /// <summary>Envelope de error del backend: {"error": {...}}.</summary>
    [Serializable]
    public class RespuestaError
    {
        public ErrorCuerpo error;
    }

    [Serializable]
    public class ErrorCuerpo
    {
        public string codigo;
        public string mensaje;
    }
}
