using System;
using UnityEngine;

namespace R01.Patronus.Modelos
{
    /// <summary>Fila de un origen: EstudianteOut del backend.</summary>
    [Serializable]
    public class Estudiante
    {
        public int id;
        public string dni;
        public string nombre;
        public string casa;
        public string patronus;
    }

    /// <summary>Fila de la central con trazabilidad: EstudianteCentralOut.</summary>
    [Serializable]
    public class EstudianteCentral
    {
        public int id;
        public string origen;
        public int id_origen;
        public string dni;
        public string nombre;
        public string casa;
        public string patronus;
        public string actualizado;
        public string backend;
    }

    /// <summary>
    /// Alta en un origen (POST). JsonUtility omite los campos null, así que
    /// id sólo viaja si se informa; nunca se envían campos extra (extra=forbid).
    /// </summary>
    [Serializable]
    public class EstudiantePayload
    {
        public int id;
        public bool idInformado;
        public string dni;
        public string nombre;
        public string casa;
        public string patronus;

        public string AJson()
        {
            return "{\"dni\":" + Json(dni)
                 + ",\"nombre\":" + Json(nombre)
                 + ",\"casa\":" + Json(casa)
                 + ",\"patronus\":" + Json(patronus)
                 + (idInformado ? ",\"id\":" + id : "")
                 + "}";
        }

        private static string Json(string valor) =>
            "\"" + (valor ?? "").Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";
    }

    /// <summary>
    /// Actualización parcial (PUT): sólo se emiten los campos informados.
    /// El backend aplica únicamente los que llegan.
    /// </summary>
    [Serializable]
    public class EstudianteUpdate
    {
        public string dni;
        public string nombre;
        public string casa;
        public string patronus;

        public string AJson()
        {
            var partes = new System.Collections.Generic.List<string>(4);
            Añadir(partes, "dni", dni);
            Añadir(partes, "nombre", nombre);
            Añadir(partes, "casa", casa);
            Añadir(partes, "patronus", patronus);
            return "{" + string.Join(",", partes) + "}";
        }

        private static void Añadir(System.Collections.Generic.List<string> partes,
            string campo, string valor)
        {
            if (string.IsNullOrEmpty(valor))
            {
                return;
            }
            partes.Add("\"" + campo + "\":\""
                + valor.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"");
        }
    }
}
