using System;
using UnityEngine;
using UnityEngine.UI;

/// <summary>Changes the API address from the scene while Play mode is running.</summary>
public sealed class ApiUrlField : MonoBehaviour
{
    [SerializeField] private InputField urlInput;
    [SerializeField] private PatronusApiClient apiClient;

    private void Start()
    {
        urlInput.SetTextWithoutNotify(apiClient.ApiBaseUrl);
        urlInput.onEndEdit.AddListener(ChangeUrl);
    }

    private void OnDestroy()
    {
        urlInput.onEndEdit.RemoveListener(ChangeUrl);
    }

    private void ChangeUrl(string text)
    {
        string url = text.Trim().TrimEnd('/');
        if (url.Length == 0)
        {
            // Keep the local URL hint visible without changing the active server.
            return;
        }
        Uri address;
        if (Uri.TryCreate(url, UriKind.Absolute, out address) &&
            (address.Scheme == Uri.UriSchemeHttp || address.Scheme == Uri.UriSchemeHttps))
        {
            apiClient.ApiBaseUrl = url;
            urlInput.SetTextWithoutNotify(url);
        }
        else
        {
            urlInput.SetTextWithoutNotify(apiClient.ApiBaseUrl);
            Debug.LogWarning("Enter a valid HTTP or HTTPS API URL.");
        }
    }
}
