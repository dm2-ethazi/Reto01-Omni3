using UnityEngine;
using UnityEngine.Events;
using UnityEngine.UI;

/// <summary>Displays one student in the search results.</summary>
public sealed class PatronusResultRow : MonoBehaviour
{
    [SerializeField] private Button selectButton;
    [SerializeField] private Text initialText;
    [SerializeField] private Text nameText;
    [SerializeField] private Text houseText;

    public void Display(Alumno alumno, UnityAction whenSelected)
    {
        string fullName = alumno.FullName;
        nameText.text = fullName;
        initialText.text = fullName.Length == 0 ? "?" : fullName.Substring(0, 1).ToUpperInvariant();
        houseText.text = alumno.casa ?? string.Empty;
        selectButton.onClick.RemoveAllListeners();
        selectButton.onClick.AddListener(whenSelected);
        gameObject.SetActive(true);
    }

    public void Hide()
    {
        selectButton.onClick.RemoveAllListeners();
        gameObject.SetActive(false);
    }
}
