using System;
using System.Collections;
using UnityEngine;
using UnityEngine.Networking;

/// <summary>Reads students and their Patronus data from the Alumnos API.</summary>
public sealed class PatronusApiClient : MonoBehaviour
{
    [SerializeField] private string apiBaseUrl = "http://127.0.0.1:8000";

    public string ApiBaseUrl
    {
        get { return apiBaseUrl; }
        set { apiBaseUrl = value; }
    }

    public IEnumerator SearchAlumnos(string searchText, Action<Alumno[]> onSuccess,
        Action<string> onFailure)
    {
        string nombre = (searchText ?? string.Empty).Trim();
        if (nombre.Length > 100)
        {
            onFailure?.Invoke("The search name cannot exceed 100 characters.");
            yield break;
        }

        // The API rejects an empty nombre, so omit the query parameter when it is blank.
        string url = ApiUrl("/alumnos");
        if (nombre.Length > 0)
            url += "?nombre=" + Uri.EscapeDataString(nombre);

        using (UnityWebRequest request = UnityWebRequest.Get(url))
        {
            yield return request.SendWebRequest();
            if (request.result != UnityWebRequest.Result.Success)
            {
                onFailure?.Invoke(request.error);
                yield break;
            }

            AlumnoListResponse response;
            try
            {
                // JsonUtility cannot read a JSON array at the root of a response.
                string wrappedJson = "{\"items\":" + request.downloadHandler.text + "}";
                response = JsonUtility.FromJson<AlumnoListResponse>(wrappedJson);
            }
            catch (Exception error)
            {
                onFailure?.Invoke("Could not read the student list: " + error.Message);
                yield break;
            }

            if (response == null || response.items == null)
            {
                onFailure?.Invoke("The student list has an unexpected format.");
                yield break;
            }

            onSuccess?.Invoke(response.items);
        }
    }

    public IEnumerator GetAlumno(string dni, Action<Alumno> onSuccess, Action<string> onFailure)
    {
        using (UnityWebRequest request = UnityWebRequest.Get(ApiUrl("/alumnos/" + Uri.EscapeDataString(dni))))
        {
            yield return request.SendWebRequest();
            if (request.result != UnityWebRequest.Result.Success)
            {
                onFailure?.Invoke(request.error);
                yield break;
            }

            Alumno alumno;
            try
            {
                alumno = JsonUtility.FromJson<Alumno>(request.downloadHandler.text);
            }
            catch (Exception error)
            {
                onFailure?.Invoke("Could not read the student: " + error.Message);
                yield break;
            }

            if (alumno == null || string.IsNullOrEmpty(alumno.id))
            {
                onFailure?.Invoke("The student has an unexpected format.");
                yield break;
            }

            onSuccess?.Invoke(alumno);
        }
    }

    public IEnumerator DownloadImage(string imagePath, Action<Texture2D> onSuccess, Action<string> onFailure)
    {
        // Patronus URLs may be absolute URLs or paths served by the API itself.
        string imageUrl;
        try
        {
            Uri baseAddress = new Uri(apiBaseUrl.TrimEnd('/') + "/");
            imageUrl = new Uri(baseAddress, imagePath).AbsoluteUri;
        }
        catch (UriFormatException)
        {
            onFailure?.Invoke("The Patronus image URL is invalid.");
            yield break;
        }

        using (UnityWebRequest request = UnityWebRequestTexture.GetTexture(imageUrl))
        {
            yield return request.SendWebRequest();
            if (request.result != UnityWebRequest.Result.Success)
            {
                onFailure?.Invoke(request.error);
                yield break;
            }

            onSuccess?.Invoke(DownloadHandlerTexture.GetContent(request));
        }
    }

    private string ApiUrl(string path)
    {
        return apiBaseUrl.TrimEnd('/') + path;
    }
}
