using System;

/// <summary>Student data returned by the Alumnos API.</summary>
[Serializable]
public sealed class Alumno
{
    public string id;
    public string nombre;
    public string apellido;
    public string sexo;
    public string patronus;
    public string patronus_url;
    public string imagen; // Student portrait, distinct from the Patronus image URL.
    public string casa;

    public string FullName => string.IsNullOrWhiteSpace(apellido)
        ? nombre ?? string.Empty
        : (nombre + " " + apellido).Trim();
}

// JsonUtility needs an object around the API's top-level array.
[Serializable]
public sealed class AlumnoListResponse
{
    public Alumno[] items;
}
