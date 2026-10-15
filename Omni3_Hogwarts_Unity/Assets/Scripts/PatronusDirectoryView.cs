using System.Collections;
using UnityEngine;
using UnityEngine.UI;

/// <summary>
/// Holds scene UI references, displays student information,
/// and animates Patronus images when loaded.
/// </summary>
public sealed class PatronusDirectoryView : MonoBehaviour
{
    [Header("Existing controls in the Canvas")]
    [SerializeField] private InputField searchInput;

    [SerializeField] private Text resultsStatusText;
    [SerializeField] private Text detailNameText;
    [SerializeField] private Text detailPatronusText;
    [SerializeField] private Text detailHouseText;
    [SerializeField] private Text imagePlaceholderText;
    [SerializeField] private RawImage patronusImage;

    [Header("Patronus Image Animation")]
    [SerializeField] private float animationDuration = 0.4f;

    [Range(0.1f, 1f)]
    [SerializeField] private float initialScale = 0.85f;

    private Coroutine imageAnimation;
    private Vector3 originalScale;
    private Color originalColor;

    private static readonly Color SecondaryColor =
        new Color32(0xA8, 0x9C, 0x85, 0xFF);

    private static readonly Color PrimaryTextColor =
        new Color32(0xF2, 0xE8, 0xD5, 0xFF);

    private static readonly Color SuccessColor =
        new Color32(0x7F, 0xB8, 0x8A, 0xFF);

    private static readonly Color ErrorColor =
        new Color32(0xC0, 0x50, 0x4D, 0xFF);

    public string SearchText => searchInput.text;

    private void Awake()
    {
        originalScale = patronusImage.rectTransform.localScale;
        originalColor = patronusImage.color;
    }

    private void OnDisable()
    {
        StopImageAnimation();
    }

    /// <summary>
    /// Registers an action to execute whenever the search input changes.
    /// </summary>
    public void RegisterSearchActions(
        UnityEngine.Events.UnityAction<string> whenTextChanges)
    {
        searchInput.onValueChanged.AddListener(whenTextChanges);
    }

    /// <summary>
    /// Displays the current search status.
    /// </summary>
    public void ShowSearchStatus(
        string message,
        bool isError = false,
        bool isSuccess = false)
    {
        resultsStatusText.text = message;

        resultsStatusText.color = isError
            ? ErrorColor
            : isSuccess
                ? SuccessColor
                : SecondaryColor;
    }

    /// <summary>
    /// Displays a loading state while student details are requested.
    /// </summary>
    public void ShowLoadingDetail()
    {
        StopImageAnimation();

        detailNameText.text = "Loading...";
        detailNameText.color = PrimaryTextColor;

        detailPatronusText.text = string.Empty;
        detailHouseText.text = string.Empty;

        patronusImage.gameObject.SetActive(false);

        imagePlaceholderText.gameObject.SetActive(true);
        imagePlaceholderText.text = "Looking up Patronus...";
        imagePlaceholderText.color = SecondaryColor;
    }

    /// <summary>
    /// Displays student information and animates the Patronus image.
    /// </summary>
    public void ShowAlumnoDetail(Alumno alumno, Texture2D image)
    {
        StopImageAnimation();

        detailNameText.text = alumno.FullName;
        detailNameText.color = PrimaryTextColor;

        detailPatronusText.text = "PATRONUS  /  " +
            (string.IsNullOrWhiteSpace(alumno.patronus)
                ? "NOT RECORDED"
                : alumno.patronus.ToUpperInvariant());

        detailHouseText.text = "HOUSE  /  " +
            (alumno.casa ?? string.Empty).ToUpperInvariant();

        // Handle students without an available Patronus image.
        if (image == null)
        {
            patronusImage.texture = null;
            patronusImage.gameObject.SetActive(false);

            imagePlaceholderText.gameObject.SetActive(true);

            imagePlaceholderText.text =
                string.IsNullOrWhiteSpace(alumno.patronus)
                    ? "No Patronus recorded."
                    : "No Patronus image available.";

            imagePlaceholderText.color = SecondaryColor;
            return;
        }

        // Assign the downloaded image.
        patronusImage.texture = image;

        // Prepare the animation's initial state.
        RectTransform rect = patronusImage.rectTransform;
        rect.localScale = originalScale * initialScale;

        Color startingColor = originalColor;
        startingColor.a = 0f;
        patronusImage.color = startingColor;

        // Display the image and hide its placeholder.
        patronusImage.gameObject.SetActive(true);
        imagePlaceholderText.gameObject.SetActive(false);

        // Start the fade-in and zoom animation.
        if (isActiveAndEnabled)
        {
            imageAnimation = StartCoroutine(AnimatePatronusImage());
        }
        else
        {
            // Display normally if the component is disabled.
            rect.localScale = originalScale;
            patronusImage.color = originalColor;
        }
    }

    /// <summary>
    /// Shows a message while the Patronus image is downloading.
    /// </summary>
    public void ShowImageLoading()
    {
        StopImageAnimation();

        patronusImage.gameObject.SetActive(false);
        imagePlaceholderText.gameObject.SetActive(true);

        imagePlaceholderText.text = "Summoning Patronus image...";
        imagePlaceholderText.color = SecondaryColor;
    }

    /// <summary>
    /// Displays an error if the Patronus image cannot be loaded.
    /// </summary>
    public void ShowImageError()
    {
        StopImageAnimation();

        patronusImage.gameObject.SetActive(false);
        imagePlaceholderText.gameObject.SetActive(true);

        imagePlaceholderText.text = "Patronus image unavailable.";
        imagePlaceholderText.color = ErrorColor;
    }

    /// <summary>
    /// Displays an error if student information cannot be loaded.
    /// </summary>
    public void ShowDetailError(string message)
    {
        StopImageAnimation();

        detailNameText.text = message;
        detailNameText.color = ErrorColor;

        detailPatronusText.text = string.Empty;
        detailHouseText.text = string.Empty;

        patronusImage.gameObject.SetActive(false);
        imagePlaceholderText.gameObject.SetActive(true);

        imagePlaceholderText.text = "Image unavailable";
        imagePlaceholderText.color = ErrorColor;
    }

    /// <summary>
    /// Gradually increases the Patronus image's opacity and scale.
    /// </summary>
    private IEnumerator AnimatePatronusImage()
    {
        RectTransform rect = patronusImage.rectTransform;

        float elapsed = 0f;
        float duration = Mathf.Max(0.01f, animationDuration);

        while (elapsed < duration)
        {
            elapsed += Time.unscaledDeltaTime;

            float progress = Mathf.Clamp01(elapsed / duration);

            // Smooth interpolation for a natural animation.
            float eased = Mathf.SmoothStep(0f, 1f, progress);

            // Fade in from transparent to fully visible.
            Color currentColor = originalColor;
            currentColor.a = originalColor.a * eased;

            patronusImage.color = currentColor;

            // Zoom from initialScale to the original image size.
            float scale = Mathf.Lerp(initialScale, 1f, eased);

            rect.localScale = originalScale * scale;

            yield return null;
        }

        // Ensure the final values are exact.
        patronusImage.color = originalColor;
        rect.localScale = originalScale;

        imageAnimation = null;
    }

    /// <summary>
    /// Stops any running image animation and resets its properties.
    /// </summary>
    private void StopImageAnimation()
    {
        if (imageAnimation != null)
        {
            StopCoroutine(imageAnimation);
            imageAnimation = null;
        }

        if (patronusImage == null)
            return;

        patronusImage.color = originalColor;
        patronusImage.rectTransform.localScale = originalScale;
    }
}