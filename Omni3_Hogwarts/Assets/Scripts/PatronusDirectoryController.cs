using System.Collections;
using UnityEngine;

/// <summary>Connects the existing Canvas controls to the Alumnos API.</summary>
public sealed class PatronusDirectoryController : MonoBehaviour
{
    [Header("Objects already placed in this scene")]
    [SerializeField] private PatronusApiClient apiClient;
    [SerializeField] private PatronusDirectoryView directoryView;
    [SerializeField] private Transform resultRowsContainer;
    [SerializeField] private PatronusResultRow resultRowTemplate;

    private const float SearchDelaySeconds = 0.25f;
    private const int MaximumVisibleResults = 8;
    private Coroutine delayedSearch;
    private Texture2D currentPatronusImage;
    private readonly System.Collections.Generic.List<PatronusResultRow> generatedRows =
        new System.Collections.Generic.List<PatronusResultRow>();
    private int newestSearchNumber;
    private int newestDetailNumber;

    private void Awake()
    {
        directoryView.RegisterSearchActions(QueueSearch);
        ClearGeneratedRows();
    }

    private void Start()
    {
        QueueSearch(directoryView.SearchText);
    }

    private void OnDestroy()
    {
        if (currentPatronusImage != null)
            Destroy(currentPatronusImage);
    }

    // Waiting briefly means we do not call the API after every single typed letter.
    private void QueueSearch(string searchText)
    {
        newestSearchNumber++;
        if (delayedSearch != null)
            StopCoroutine(delayedSearch);

        // The live API has many students, so wait for a name before creating rows.
        if (string.IsNullOrWhiteSpace(searchText))
        {
            ClearGeneratedRows();
            directoryView.ShowSearchStatus("Type a name to search.");
            return;
        }

        delayedSearch = StartCoroutine(SearchAfterShortDelay(searchText, newestSearchNumber));
    }

    private IEnumerator SearchAfterShortDelay(string searchText, int searchNumber)
    {
        yield return new WaitForSecondsRealtime(SearchDelaySeconds);
        yield return SearchAlumnos(searchText, searchNumber);
    }

    private IEnumerator SearchAlumnos(string searchText, int searchNumber)
    {
        directoryView.ShowSearchStatus("Searching...");
        yield return apiClient.SearchAlumnos(searchText,
            response => ShowSearchResults(response, searchNumber),
            error => ShowSearchError(searchNumber, error));
    }

    private void ShowSearchResults(Alumno[] alumnos, int searchNumber)
    {
        if (searchNumber != newestSearchNumber)
            return; // A newer search finished first, so this old answer is ignored.

        if (alumnos == null || alumnos.Length == 0)
        {
            ClearGeneratedRows();
            directoryView.ShowSearchStatus("No matching students.");
            return;
        }

        ClearGeneratedRows();
        int visibleCount = Mathf.Min(alumnos.Length, MaximumVisibleResults);
        for (int index = 0; index < visibleCount; index++)
        {
            string selectedDni = alumnos[index].id;
            PatronusResultRow newRow = Instantiate(resultRowTemplate, resultRowsContainer);
            generatedRows.Add(newRow);
            newRow.Display(alumnos[index], () => StartCoroutine(LoadAlumnoDetail(selectedDni)));
        }

        string status = alumnos.Length > visibleCount
            ? "Showing " + visibleCount + " of " + alumnos.Length + " students. Refine the name."
            : alumnos.Length == 1 ? "1 student found" : alumnos.Length + " students found";
        directoryView.ShowSearchStatus(status, isSuccess: true);
    }

    private void ShowSearchError(int searchNumber, string error)
    {
        if (searchNumber != newestSearchNumber)
            return;

        Debug.LogWarning("Alumnos search failed: " + error);
        ClearGeneratedRows();
        directoryView.ShowSearchStatus("Could not search students. Check the API.", isError: true);
    }

    // Search results are temporary. Remove them before displaying the next API answer.
    private void ClearGeneratedRows()
    {
        foreach (PatronusResultRow generatedRow in generatedRows)
            Destroy(generatedRow.gameObject);
        generatedRows.Clear();
    }

    private IEnumerator LoadAlumnoDetail(string dni)
    {
        int detailNumber = ++newestDetailNumber;

        if (currentPatronusImage != null)
        {
            Destroy(currentPatronusImage);
            currentPatronusImage = null;
        }

        directoryView.ShowLoadingDetail();
        Alumno loadedAlumno = null;
        bool requestFailed = false;
        string errorMessage = string.Empty;

        yield return apiClient.GetAlumno(dni, alumno => loadedAlumno = alumno,
            error => { requestFailed = true; errorMessage = error; });
        if (detailNumber != newestDetailNumber)
            yield break;
        if (requestFailed || loadedAlumno == null)
        {
            Debug.LogWarning("Could not load student " + dni + ": " + errorMessage);
            directoryView.ShowDetailError("Could not load this student.");
            yield break;
        }

        directoryView.ShowAlumnoDetail(loadedAlumno, null);
        if (string.IsNullOrWhiteSpace(loadedAlumno.patronus_url))
            yield break;

        directoryView.ShowImageLoading();
        Texture2D downloadedImage = null;
        requestFailed = false;
        yield return apiClient.DownloadImage(loadedAlumno.patronus_url, image => downloadedImage = image,
            error => { requestFailed = true; errorMessage = error; });
        if (detailNumber != newestDetailNumber)
        {
            if (downloadedImage != null)
                Destroy(downloadedImage);
            yield break;
        }
        if (requestFailed || downloadedImage == null)
        {
            Debug.LogWarning("Could not load Patronus image: " + errorMessage);
            directoryView.ShowImageError();
            yield break;
        }

        currentPatronusImage = downloadedImage;
        directoryView.ShowAlumnoDetail(loadedAlumno, currentPatronusImage);
    }
}
